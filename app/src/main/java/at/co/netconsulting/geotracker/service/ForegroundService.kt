package at.co.netconsulting.geotracker.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.data.WeatherResponse
import at.co.netconsulting.geotracker.domain.DeviceStatus
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.User
import at.co.netconsulting.geotracker.domain.Weather
import at.co.netconsulting.geotracker.location.CustomLocationListener
import at.co.netconsulting.geotracker.tools.Tools
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.IOException
import java.lang.System.currentTimeMillis
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class ForegroundService : Service() {
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private var height: Float = 0f
    private var weight: Float = 0f
    private lateinit var eventname: String
    private lateinit var eventdate: String
    private lateinit var artofsport: String
    private lateinit var comment: String
    private lateinit var clothing: String
    private var speed: Float = 0.0f
    private var eventId: Int = 0
    private var distance: Double = 0.0
    private var altitude: Double = 0.0
    private var latitude: Double = -999.0
    private var longitude: Double = -999.0
    private var oldLatitude: Double = -999.0
    private var oldLongitude: Double = -999.0
    private var lap: Int = 0
    private var lazyFormattedTime: String = "00:00:00"
    private var movementFormattedTime: String = "00:00:00"
    private val serviceJob = SupervisorJob() // Changed to SupervisorJob for better error handling
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null // Made nullable for safer handling
    private var lastUpdateTimestamp: Long = currentTimeMillis()
    private var isCurrentlyMoving = false
    private val movementState = StopwatchState()
    private val lazyState = StopwatchState()
    private var customLocationListener: CustomLocationListener? = null
    private var currentEventId: Int = -1
    private var satellites: String = "0"
    private var hasReceivedValidWeather = false

    //Weather
    private var weatherJob: Job? = null
    private val weatherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    //sessionId
    private var sessionId: String = ""

    private var isServiceStarted = false

    private fun startWeatherUpdates() {
        stopWeatherUpdates()

        Log.d(TAG, "Starting weather updates with coordinates: lat=$latitude, lon=$longitude")
        Log.d(TAG, "Initial polling interval: ${if (hasReceivedValidWeather) "NORMAL" else "AGGRESSIVE"}")

        weatherJob = weatherScope.launch {
            // Initially use aggressive polling until we get valid data
            var pollInterval = if (hasReceivedValidWeather) WEATHER_UPDATE_INTERVAL else WEATHER_FAST_POLL_INTERVAL

            while (isActive) {
                try {
                    // Only fetch weather if we have valid coordinates
                    if (latitude != -999.0 && longitude != -999.0) {
                        Log.d(TAG, "Attempting to fetch weather with interval: $pollInterval ms")
                        val weatherSuccess = fetchWeatherData()

                        // If we got valid weather, switch to normal interval
                        if (weatherSuccess) {
                            hasReceivedValidWeather = true
                            pollInterval = WEATHER_UPDATE_INTERVAL
                            Log.d(TAG, "Successfully received weather data, switching to normal polling interval ($pollInterval ms)")
                        } else {
                            Log.d(TAG, "Weather fetch unsuccessful, continuing with ${if (hasReceivedValidWeather) "normal" else "aggressive"} polling")
                        }
                    } else {
                        Log.d(TAG, "Skipping weather update - invalid coordinates")
                    }
                    delay(pollInterval)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Weather updates cancelled")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching weather data", e)
                    try {
                        delay(ERROR_RETRY_INTERVAL)
                    } catch (ce: CancellationException) {
                        Log.d(TAG, "Weather updates cancelled during error delay")
                        break
                    }
                }
            }
        }
    }

    private suspend fun fetchWeatherData(): Boolean {
        try {
            coroutineContext.ensureActive() // Check if coroutine is still active

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val url = buildWeatherUrl(latitude, longitude)
            Log.d(TAG, "Fetching weather from URL: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            withContext(Dispatchers.IO) {
                coroutineContext.ensureActive() // Check again before network call

                client.newCall(request).execute().use { response ->
                    coroutineContext.ensureActive() // Check before processing response

                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        throw IOException("Empty response body")
                    }

                    Log.d(TAG, "Weather API response: ${responseBody.take(500)}...") // Log the start of the response

                    try {
                        // Create a custom Gson instance with specific field naming policy
                        val gson = GsonBuilder()
                            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                            .create()

                        val weatherResponse = gson.fromJson(responseBody, WeatherResponse::class.java)

                        if (weatherResponse != null) {
                            Log.d(TAG, "Weather response parsed successfully")

                            // Check if currentWeather is available
                            if (weatherResponse.currentWeather != null) {
                                Log.d(TAG, "Current weather data: ${weatherResponse.currentWeather}")

                                // Directly publish CurrentWeather to EventBus
                                EventBus.getDefault().post(weatherResponse.currentWeather)
                                Log.d(TAG, "Weather data published to EventBus: ${weatherResponse.currentWeather}")

                                // Only try to save to DB if we have the required data
                                val currentWeather = weatherResponse.currentWeather
                                try {
                                    // Get humidity data if available
                                    var currentHumidity = 0
                                    val hourlyData = weatherResponse.hourly

                                    if (hourlyData != null &&
                                        hourlyData.time != null &&
                                        hourlyData.relativeHumidity != null &&
                                        hourlyData.relativeHumidity.isNotEmpty()) {

                                        val currentTime = currentWeather.time

                                        Log.d(TAG, "Current weather time: $currentTime")
                                        Log.d(TAG, "First hourly time: ${hourlyData.time.firstOrNull()}")

                                        // Try to find exact match first
                                        val hourlyIndex = hourlyData.time.indexOfFirst { it == currentTime }

                                        if (hourlyIndex != -1 && hourlyIndex < hourlyData.relativeHumidity.size) {
                                            currentHumidity = hourlyData.relativeHumidity[hourlyIndex]
                                            Log.d(TAG, "Found exact time match, humidity: $currentHumidity")
                                        } else {
                                            // Try to find closest time by parsing date/hour parts
                                            try {
                                                // Extract date and hour from current time (e.g., "2025-03-26T21:00")
                                                val datePart = currentTime.substringBefore("T")
                                                val hourPart = currentTime.substringAfter("T").substringBefore(":")

                                                // Find closest matching hour
                                                val closestIndex = hourlyData.time.indexOfFirst { hourlyTime ->
                                                    hourlyTime.contains(datePart) && hourlyTime.contains("T$hourPart")
                                                }

                                                if (closestIndex != -1 && closestIndex < hourlyData.relativeHumidity.size) {
                                                    currentHumidity = hourlyData.relativeHumidity[closestIndex]
                                                    Log.d(TAG, "Found closest time match, humidity: $currentHumidity")
                                                } else {
                                                    // Fallback to first available humidity data
                                                    currentHumidity = hourlyData.relativeHumidity.first()
                                                    Log.d(TAG, "Using fallback humidity data: $currentHumidity")
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error processing hourly humidity data", e)
                                                // Last resort fallback - use a typical value
                                                currentHumidity = 70  // typical humidity value
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "Hourly data is incomplete or null")
                                    }

                                    // Create and save weather object
                                    val weather = Weather(
                                        eventId = eventId,
                                        weatherRestApi = "OpenMeteo",
                                        temperature = currentWeather.temperature.toFloat(),
                                        windSpeed = currentWeather.windspeed.toFloat(),
                                        windDirection = getWindDirection(currentWeather.winddirection),
                                        relativeHumidity = currentHumidity
                                    )

                                    database.weatherDao().insertWeather(weather)
                                    Log.d(TAG, "Weather data saved to database: $weather")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error saving weather to database", e)
                                    // Continue anyway, we already published to EventBus
                                }

                                return@withContext true  // Successfully got and processed weather data
                            } else {
                                Log.e(TAG, "Current weather data is null after parsing")
                            }
                        } else {
                            Log.e(TAG, "Weather response is null after parsing")
                        }
                    } catch (e: JsonSyntaxException) {
                        Log.e(TAG, "Failed to parse weather response, syntax error: ${e.message}")
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing weather data: ${e.message}")
                        throw e
                    }
                }
            }

            return false  // No valid weather data was obtained
        } catch (e: CancellationException) {
            Log.d(TAG, "Weather fetch cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather data", e)
            return false
        }
    }

    private fun buildWeatherUrl(lat: Double, lon: Double): String {
        return "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat" +
                "&longitude=$lon" +
                "&current_weather=true" +
                "&hourly=relativehumidity_2m"
    }

    private fun getWindDirection(degrees: Double): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((degrees + 22.5) % 360 / 45).toInt()
        return directions[index]
    }

    private fun stopWeatherUpdates() {
        weatherJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                Log.d(TAG, "Weather updates stopped")
            }
        }
        weatherJob = null
    }
    //Weather

    override fun onCreate() {
        super.onCreate()
        EventBus.getDefault().register(this)
        currentEventId =
            getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE).getInt("active_event_id", -1)
        loadSharedPreferences()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTracker")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        requestHighPriority()
        createSessionID()
    }

    private fun createSessionID() {
        sessionId = Tools().generateSessionId(firstname, this)
        getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            .edit()
            .putString("current_session_id", sessionId)
            .apply()
        Log.d(TAG, "Created and saved session ID: $sessionId")
    }

    private fun requestHighPriority() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Request maximum priority
            startForeground(
                1,
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )

            // Request ignore battery optimizations
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }

    private fun loadSharedPreferences() {
        val sharedPreferences = this.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        firstname = sharedPreferences.getString("firstname", "") ?: ""
        lastname = sharedPreferences.getString("lastname", "") ?: ""
        birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        height = sharedPreferences.getFloat("height", 0f)
        weight = sharedPreferences.getFloat("weight", 0f)
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null || (wakeLock != null && !wakeLock!!.isHeld)) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GeoTracker:ForegroundService:WakeLock"
                )
                // Set a timeout to ensure wake lock is eventually released even if something goes wrong
                wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours max
                Log.d(TAG, "Wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock!!.isHeld) {
                wakeLock!!.release()
                wakeLock = null
                Log.d(TAG, "Wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }

    private val database: FitnessTrackerDatabase by lazy {
        FitnessTrackerDatabase.getInstance(applicationContext)
    }

    private class TimeSegment(
        var startTime: LocalDateTime = LocalDateTime.now(),
        var endTime: LocalDateTime? = null,
        var totalDuration: Duration = Duration.ZERO
    )

    private class StopwatchState {
        private val segments = mutableListOf<TimeSegment>()
        private var currentSegment: TimeSegment? = null

        fun start() {
            currentSegment = TimeSegment()
            segments.add(currentSegment!!)
        }

        fun stop() {
            currentSegment?.let {
                it.endTime = LocalDateTime.now()
                it.totalDuration = Duration.between(it.startTime, it.endTime)
                currentSegment = null
            }
        }

        fun getTotalDuration(): Duration {
            val completedDuration = segments.sumOf {
                it.totalDuration.seconds
            }

            val currentDuration = currentSegment?.let {
                Duration.between(it.startTime, LocalDateTime.now()).seconds
            } ?: 0

            return Duration.ofSeconds(completedDuration + currentDuration)
        }

        fun getCurrentSegment(): TimeSegment? = currentSegment
    }

    private fun createBackgroundCoroutine(eventIdDeferred: CompletableDeferred<Int>) {
        serviceScope.launch {
            try {
                isServiceStarted = true

                // Create the location listener
                customLocationListener = CustomLocationListener(applicationContext).also {
                    it.startDateTime = LocalDateTime.now()
                    // Make sure we wait until sessionId is created and saved
                    delay(100) // Short delay to ensure sessionId is saved to SharedPreferences
                    it.startListener()
                }

                val userId = database.userDao().insertUser(User(0, firstname, lastname, birthdate, weight, height))
                eventId = createNewEvent(database, userId)

                // Complete the deferred with the new event ID
                eventIdDeferred.complete(eventId)

                // Start weather updates right away with aggressive polling
                startWeatherUpdates()

                while (isActive) {
                    val currentTime = currentTimeMillis()
                    if (currentTime - lastUpdateTimestamp > EVENT_TIMEOUT_MS) {
                        resetValues()
                    }
                    if (speed >= MIN_SPEED_THRESHOLD) {
                        showStopWatch()
                    } else {
                        showLazyStopWatch()
                    }

                    showNotification()

                    // save data to database as long as we have valid coordinates,
                    // regardless of speed or duplicate coordinates
                    if(checkLatitudeLongitude()) {
                        insertDatabase(database)
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                eventIdDeferred.completeExceptionally(e)
                Log.e(TAG, "Error in background coroutine", e)
            }
        }
    }

    private fun checkLatitudeLongitudeDuplicates(): Boolean {
        val isLatitudeSame = latitude.compareTo(oldLatitude) == 0
        val isLongitudeSame = longitude.compareTo(oldLongitude) == 0

        oldLatitude = latitude
        oldLongitude = longitude

        return !isLatitudeSame || !isLongitudeSame
    }

    private fun checkLatitudeLongitude(): Boolean {
        return latitude != -999.0 && longitude != -999.0
    }

    private fun showStopWatch() {
        if (speed >= MIN_SPEED_THRESHOLD) {
            if (!isCurrentlyMoving) {
                lazyState.stop()
                movementState.start()
                isCurrentlyMoving = true
            }
        } else {
            if (isCurrentlyMoving) {
                movementState.stop()
                isCurrentlyMoving = false
            }
        }
        val duration = movementState.getTotalDuration()
        val movementHours = duration.toHours()
        val movementMinutes = (duration.toMinutes() % 60)
        val movementSeconds = (duration.seconds % 60)
        movementFormattedTime = String.format("%02d:%02d:%02d", movementHours, movementMinutes, movementSeconds)
    }

    private fun showLazyStopWatch() {
        if (speed < MIN_SPEED_THRESHOLD) {
            Log.d("StopWatch", "Speed below threshold")
            if (isCurrentlyMoving) {
                Log.d("StopWatch", "Transitioning from moving to lazy")
                movementState.stop()
                lazyState.start()
                isCurrentlyMoving = false
            } else if (lazyState.getCurrentSegment() == null) {
                Log.d("StopWatch", "Starting new lazy segment")
                lazyState.start()
            }
        } else {
            if (!isCurrentlyMoving) {
                Log.d("StopWatch", "Stopping lazy state")
                lazyState.stop()
            }
        }

        val duration = lazyState.getTotalDuration()
        val lazyHours = duration.toHours()
        val lazyMinutes = (duration.toMinutes() % 60)
        val lazySeconds = (duration.seconds % 60)
        lazyFormattedTime = String.format("%02d:%02d:%02d", lazyHours, lazyMinutes, lazySeconds)
    }

    private fun resetValues() {
        speed = 0.0F
        showNotification()
    }

    private fun showNotification() {
        updateNotification(
            "Activity: " + movementFormattedTime +
                    "\nCovered Distance: " + String.format("%.2f", distance / 1000) + " Km" +
                    "\nSpeed: " + String.format("%.2f", speed) + " km/h" +
                    "\nAltitude: " + String.format("%.2f", altitude) + " meter" +
                    "\nLap: " + String.format("%2d", lap) +
                    "\nInactivity: " + lazyFormattedTime
        )
    }

    private suspend fun createNewEvent(database: FitnessTrackerDatabase, userId: Long): Int {
        val newEvent = Event(
            userId = userId,
            eventName = eventname,
            eventDate = Tools().provideDateTimeFormat(),
            artOfSport = artofsport,
            comment = comment
        )

        val eventId = database.eventDao().insertEvent(newEvent).toInt()
        println("New Event ID: $eventId")
        return eventId
    }

    private fun updateNotification(newContent: String) {
        runOnUiThread {
            notificationBuilder
                .setStyle(NotificationCompat.BigTextStyle().bigText(newContent))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setAutoCancel(false)
            notificationManager.notify(1, notificationBuilder.build())
        }
    }

    private suspend fun insertDatabase(database: FitnessTrackerDatabase) {
        withContext(Dispatchers.IO) {
            try {
                // Always save metrics data regardless of speed
                val metric = Metric(
                    eventId = eventId,
                    heartRate = 0,
                    heartRateDevice = "Chest Strap",
                    speed = speed,
                    distance = distance, // This is only updated when speed > threshold from CustomLocationListener
                    cadence = 0,
                    lap = lap,
                    timeInMilliseconds = currentTimeMillis(),
                    unity = "metric",
                    elevation = altitude.toFloat(),
                    elevationGain = 0f,
                    elevationLoss = 0f
                )
                database.metricDao().insertMetric(metric)
                Log.d("ForegroundService: ", "Metric saved at ${metric.timeInMilliseconds}")

                // Always save location data regardless of speed
                val location = Location(
                    eventId = eventId,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude
                )
                database.locationDao().insertLocation(location)
                Log.d("ForegroundService: ", "Location saved: lat=$latitude, lon=$longitude")

                // Always save device status regardless of speed
                val deviceStatus = DeviceStatus(
                    eventId = eventId,
                    numberOfSatellites = satellites,
                    sensorAccuracy = "High",
                    signalStrength = "Strong",
                    batteryLevel = "80%",
                    connectionStatus = "Connected",
                    sessionId = sessionId
                )
                database.deviceStatusDao().insertDeviceStatus(deviceStatus)
                Log.d("ForegroundService: ", "Device status saved with sessionId: $sessionId and satellites: $satellites")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting data to database", e)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLocationUpdate(metrics: Metrics) {
        try {
            // Update timestamp to prevent timeout
            lastUpdateTimestamp = currentTimeMillis()

            // Update values from location update
            latitude = metrics.latitude
            longitude = metrics.longitude
            speed = metrics.speed
            distance = metrics.coveredDistance
            altitude = metrics.altitude
            lap = metrics.lap
            satellites = (metrics.satellites ?: 0).toString()

            Log.d(TAG, "Location update received: lat=$latitude, lon=$longitude, speed=$speed, satellites=$satellites")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing location update", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Check if we're stopping intentionally (flag set by stop button)
            if (intent?.getBooleanExtra("stopping_intentionally", false) == true) {
                isStoppingIntentionally = true
                Log.d(TAG, "Service is being stopped intentionally")
            }

            // Check if we're restarting after crash
            val prefs = getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
            val wasRunning = prefs.getBoolean("was_running", false)

            if (wasRunning) {
                // Restore state
                eventId = prefs.getInt("event_id", -1)
                sessionId = prefs.getString("session_id", "") ?: ""
                Log.d(TAG, "Restoring service state: eventId=$eventId, sessionId=$sessionId")

                // Clear restart flag
                prefs.edit().putBoolean("was_running", false).apply()
            }

            // Extract extras from intent
            eventname = intent?.getStringExtra("eventName") ?: "Unknown Event"
            eventdate = intent?.getStringExtra("eventDate") ?: "Unknown Date"
            artofsport = intent?.getStringExtra("artOfSport") ?: "Unknown Sport"
            comment = intent?.getStringExtra("comment") ?: "No Comment"
            clothing = intent?.getStringExtra("clothing") ?: "No Clothing Info"

            Log.d(TAG, "Starting service with event: $eventname, sport: $artofsport")

            val eventIdDeferred = CompletableDeferred<Int>()
            createBackgroundCoroutine(eventIdDeferred)

            serviceScope.launch {
                try {
                    currentEventId = eventIdDeferred.await()
                    eventId = currentEventId

                    // Save to SharedPreferences
                    getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("active_event_id", currentEventId)
                        .apply()

                    Log.d(TAG, "Event ID created and saved: $currentEventId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create event ID", e)
                }
            }

            acquireWakeLock()
            startForeground(1, notificationBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called, isStoppingIntentionally: $isStoppingIntentionally")

        try {
            stopWeatherUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping weather updates", e)
        }

        try {
            weatherScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling weather scope", e)
        }

        if (isStoppingIntentionally) {
            try {
                // Clear active_event_id when stopping intentionally
                getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                    .edit()
                    .remove("active_event_id")
                    .apply()

                Log.d(TAG, "Removed active_event_id from SharedPreferences")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing event ID from preferences", e)
            }

            // Reset the flag
            isStoppingIntentionally = false
        }

        try {
            customLocationListener?.cleanup()
            customLocationListener = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up location listener", e)
        }

        releaseWakeLock()

        try {
            if (EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().unregister(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering from EventBus", e)
        }

        try {
            serviceJob.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling service job", e)
        }

        isServiceStarted = false

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved called, attempting to schedule restart")

        try {
            // Save current state to survive restart
            val prefs = getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("was_running", isServiceStarted)
                .putInt("event_id", eventId)
                .putString("session_id", sessionId)
                .apply()

            // Create intent with all necessary extras from the original intent
            val restartIntent = Intent(applicationContext, ForegroundService::class.java).apply {
                putExtra("eventName", eventname)
                putExtra("eventDate", eventdate)
                putExtra("artOfSport", artofsport)
                putExtra("comment", comment)
                putExtra("clothing", clothing)
            }

            // Create pending intent with FLAG_IMMUTABLE for security
            val flags = PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getService(
                applicationContext,
                1,
                restartIntent,
                flags
            )

            // Use AlarmManager to schedule restart
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle for more reliable execution
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    currentTimeMillis() + 1000, // 1 second delay
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    currentTimeMillis() + 1000,
                    pendingIntent
                )
            }

            Log.d(TAG, "Service restart scheduled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule service restart", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GeoTracker",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val MIN_SPEED_THRESHOLD = 2.5f
        private const val EVENT_TIMEOUT_MS = 2000
        private var isStoppingIntentionally = false
        private const val TAG = "ForegroundService"
        //Weather
        private const val WEATHER_UPDATE_INTERVAL = 3600000L // 1 hour in milliseconds
        private const val ERROR_RETRY_INTERVAL = 300000L // 5 minutes in milliseconds
        private const val WEATHER_FAST_POLL_INTERVAL = 10000L // 10 seconds for initial polling
    }
}