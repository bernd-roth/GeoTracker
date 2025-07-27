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
import at.co.netconsulting.geotracker.data.HeartRateData
import at.co.netconsulting.geotracker.data.BarometerData
import at.co.netconsulting.geotracker.domain.CurrentRecording
import at.co.netconsulting.geotracker.domain.DeviceStatus
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.LapTime
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.User
import at.co.netconsulting.geotracker.domain.Weather
import at.co.netconsulting.geotracker.location.CustomLocationListener
import at.co.netconsulting.geotracker.sensor.BarometerSensorService
import at.co.netconsulting.geotracker.tools.Tools
import at.co.netconsulting.geotracker.tools.BarometerUtils
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
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
    private var lapCounter: Double = 0.0 // Added to track partial lap progress
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

    // Heart rate monitoring
    private var heartRateSensorService: HeartRateSensorService? = null
    private var heartRateDeviceAddress: String? = null
    private var heartRateDeviceName: String? = null
    private var currentHeartRate: Int = 0
    private var isHrRegistered = false

    // NEW: Barometer sensor monitoring
    private var barometerSensorService: BarometerSensorService? = null
    private var currentPressure: Float = 0f
    private var currentPressureAccuracy: Int = 0
    private var currentAltitudeFromPressure: Float = 0f
    private var currentSeaLevelPressure: Float = 1013.25f

    //reconnection logic
    private var connectionMonitorJob: Job? = null
    private var isWebSocketConnected = false
    //Weather
    private var weatherJob: Job? = null
    private val weatherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentTemperature: Double = 0.0
    private var currentWindSpeed: Double = 0.0
    private var currentWindDirection: Double = 0.0
    private var currentRelativeHumidity: Int = 0
    private var currentWeatherCode: Int = 0
    private var currentWeatherTime: String = ""
    //sessionId
    private var sessionId: String = ""

    private var isServiceStarted = false

    //restoring destroyed session
    private var isRestoredSession = false
    private var lastKnownPosition: Pair<Double, Double>? = null
    private var startDateTime: LocalDateTime = LocalDateTime.now()

    // State saving variables
    private var currentStateJob: Job? = null
    private var lastLapCompletionTime: Long = 0
    private var lapStartTime: Long = System.currentTimeMillis()

    // Max. elevation gain calculation
    private var previousElevation: Float = 0f
    private var hasSetInitialElevation: Boolean = false
    private var currentElevationGain: Float = 0f

    // recovery after crash
    private var recoveryManager: SessionRecoveryManager? = null
    private var stateConsistencyCheckerJob: Job? = null
    private var isInitialStateRestored = false

    // WebSocket transfer setting
    private var enableWebSocketTransfer: Boolean = true

    // Session data management methods
    private fun saveSessionDataToPreferences(
        eventName: String,
        eventDate: String,
        sportType: String,
        comment: String,
        clothing: String,
        sessionId: String,
        enableWebSocketTransfer: Boolean
    ) {
        try {
            // Save to SessionPrefs for CustomLocationListener to access
            val sessionPrefs = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            sessionPrefs.edit().apply {
                putString("current_event_name", eventName)
                putString("current_event_date", eventDate)
                putString("current_sport_type", sportType)
                putString("current_comment", comment)
                putString("current_clothing", clothing)
                putString("current_session_id", sessionId)
                apply()
            }

            // Save WebSocket setting to UserSettings
            val userSettings = getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            userSettings.edit().apply {
                putBoolean("enable_websocket_transfer", enableWebSocketTransfer)
                apply()
            }

            Log.d(TAG, "Session data saved to SessionPrefs: Event='$eventName', Sport='$sportType', Comment='$comment', Clothing='$clothing'")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving session data to SharedPreferences", e)
        }
    }

    private fun clearSessionDataFromPreferences() {
        try {
            val sessionPrefs = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            sessionPrefs.edit().apply {
                remove("current_event_name")
                remove("current_event_date")
                remove("current_sport_type")
                remove("current_comment")
                remove("current_clothing")
                // Keep session_id as it might be needed for final data processing
                apply()
            }

            Log.d(TAG, "Session data cleared from SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing session data from SharedPreferences", e)
        }
    }

    // Add debugging method to verify session data is saved correctly
    private fun logSessionDataForDebugging() {
        val sessionPrefs = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)

        Log.d(TAG, "=== FOREGROUND SERVICE SESSION DATA ===")
        Log.d(TAG, "Event Name: ${sessionPrefs.getString("current_event_name", "NOT SET")}")
        Log.d(TAG, "Event Date: ${sessionPrefs.getString("current_event_date", "NOT SET")}")
        Log.d(TAG, "Sport Type: ${sessionPrefs.getString("current_sport_type", "NOT SET")}")
        Log.d(TAG, "Comment: ${sessionPrefs.getString("current_comment", "NOT SET")}")
        Log.d(TAG, "Clothing: ${sessionPrefs.getString("current_clothing", "NOT SET")}")
        Log.d(TAG, "Session ID: ${sessionPrefs.getString("current_session_id", "NOT SET")}")
        Log.d(TAG, "=========================================")
    }

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
            coroutineContext.ensureActive()

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
                coroutineContext.ensureActive()

                client.newCall(request).execute().use { response ->
                    coroutineContext.ensureActive()

                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }

                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        throw IOException("Empty response body")
                    }

                    Log.d(TAG, "Weather API response: ${responseBody.take(500)}...")

                    try {
                        val gson = GsonBuilder()
                            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                            .create()

                        val weatherResponse = gson.fromJson(responseBody, WeatherResponse::class.java)

                        if (weatherResponse != null) {
                            Log.d(TAG, "Weather response parsed successfully")

                            if (weatherResponse.currentWeather != null) {
                                Log.d(TAG, "Current weather data: ${weatherResponse.currentWeather}")

                                val currentWeather = weatherResponse.currentWeather

                                // ✅ STORE WEATHER DATA IN MEMBER VARIABLES
                                currentTemperature = currentWeather.temperature
                                currentWindSpeed = currentWeather.windspeed
                                currentWindDirection = currentWeather.winddirection
                                currentWeatherCode = currentWeather.weathercode
                                currentWeatherTime = currentWeather.time

                                // Directly publish CurrentWeather to EventBus
                                EventBus.getDefault().post(weatherResponse.currentWeather)
                                Log.d(TAG, "Weather data published to EventBus: ${weatherResponse.currentWeather}")

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
                                                val datePart = currentTime.substringBefore("T")
                                                val hourPart = currentTime.substringAfter("T").substringBefore(":")

                                                val closestIndex = hourlyData.time.indexOfFirst { hourlyTime ->
                                                    hourlyTime.contains(datePart) && hourlyTime.contains("T$hourPart")
                                                }

                                                if (closestIndex != -1 && closestIndex < hourlyData.relativeHumidity.size) {
                                                    currentHumidity = hourlyData.relativeHumidity[closestIndex]
                                                    Log.d(TAG, "Found closest time match, humidity: $currentHumidity")
                                                } else {
                                                    currentHumidity = hourlyData.relativeHumidity.first()
                                                    Log.d(TAG, "Using fallback humidity data: $currentHumidity")
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error processing hourly humidity data", e)
                                                currentHumidity = 70  // typical humidity value
                                            }
                                        }
                                    } else {
                                        Log.d(TAG, "Hourly data is incomplete or null")
                                    }

                                    // ✅ STORE HUMIDITY IN MEMBER VARIABLE TOO
                                    currentRelativeHumidity = currentHumidity

                                    // Update CustomLocationListener with humidity data
                                    customLocationListener?.updateWeatherHumidity(currentHumidity)
                                    Log.d(TAG, "Updated CustomLocationListener with humidity: $currentHumidity%")

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
                                }

                                return@withContext true
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

            return false
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

        // Initialize the recovery manager
        recoveryManager = SessionRecoveryManager.getInstance(this)

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

        // Don't create a new session ID if we're restoring
        if (!isRestoredSession) {
            createSessionID()
        } else {
            // For restored sessions, load the existing session ID
            sessionId = getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
                .getString("current_session_id", "") ?: ""
            Log.d(TAG, "Restored existing session ID: $sessionId")
        }

        // Initialize heart rate sensor service
        heartRateSensorService = HeartRateSensorService.getInstance(this)

        // NEW: Initialize barometer sensor service
        barometerSensorService = BarometerSensorService.getInstance(this)

        // Start connection monitoring
        startConnectionMonitoring()

        // Start the state consistency checker
        startStateConsistencyChecker()
    }

    private fun startStateConsistencyChecker() {
        stateConsistencyCheckerJob?.cancel()
        stateConsistencyCheckerJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Check if we need to synchronize service state with database
                    if (isServiceStarted && sessionId.isNotEmpty()) {
                        saveCurrentState()
                    }

                    // Check every 30 seconds
                    delay(STATE_CONSISTENCY_CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in state consistency checker", e)
                }
            }
        }
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

        // Added to save current state
        fun getSerializableState(): String {
            val gson = Gson()
            return gson.toJson(segments)
        }

        // Added to restore from state
        fun restoreFromState(serializedState: String) {
            try {
                val gson = Gson()
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java,
                    TimeSegment::class.java
                ).type
                val restoredSegments: List<TimeSegment> = gson.fromJson(serializedState, type)
                segments.clear()
                segments.addAll(restoredSegments)
            } catch (e: Exception) {
                Log.e("StopwatchState", "Error restoring state: ${e.message}")
            }
        }
    }

    private fun createBackgroundCoroutine(eventIdDeferred: CompletableDeferred<Int>) {
        serviceScope.launch {
            try {
                isServiceStarted = true

                // If we're restoring, first try to get the latest state from the database
                if (isRestoredSession) {
                    restoreFromDatabase()
                }

                // Create the location listener with restored session context
                customLocationListener = CustomLocationListener(applicationContext).also {
                    // Use the restored start time if this is a restored session
                    if (isRestoredSession) {
                        it.startDateTime = startDateTime
                        Log.d(TAG, "Restored session startDateTime: $startDateTime")
                    } else {
                        startDateTime = LocalDateTime.now()
                        it.startDateTime = startDateTime
                    }

                    // Make sure we wait until sessionId is created and saved
                    delay(100) // Short delay to ensure sessionId is saved to SharedPreferences
                    it.startListener()

                    // If we have restored position and distance, initialize the location listener with them
                    if (isRestoredSession && distance > 0) {
                        it.resumeFromSavedState(distance, lastKnownPosition, lap, lapCounter)
                        Log.d(TAG, "Resumed from saved state: distance=$distance, position=$lastKnownPosition, lap=$lap, lapCounter=$lapCounter")
                    }
                }

                val userId = database.userDao().insertUser(User(0, firstname, lastname, birthdate, weight, height))

                // Only create a new event if this is not a restored session with valid eventId
                if (isRestoredSession && eventId > 0) {
                    eventIdDeferred.complete(eventId)
                } else {
                    eventId = createNewEvent(database, userId)
                    eventIdDeferred.complete(eventId)
                }

                // Start weather updates right away with aggressive polling
                startWeatherUpdates()

                // Start periodic state saving
                startPeriodicStateSaving()

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

    private fun startPeriodicStateSaving() {
        currentStateJob?.cancel()
        currentStateJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Save current state every 5 seconds
                    saveCurrentState()
                    delay(STATE_SAVE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in state saving job", e)
                }
            }
        }
    }

    private suspend fun saveCurrentState(forceSave: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                // Only save if we have valid data and active tracking
                // Force save can be triggered on service stop or crash recovery
                if ((eventId > 0 && sessionId.isNotEmpty() && checkLatitudeLongitude()) || forceSave) {
                    val currentRecord = CurrentRecording(
                        sessionId = sessionId,
                        eventId = eventId,
                        timestamp = System.currentTimeMillis(),
                        latitude = latitude,
                        longitude = longitude,
                        altitude = altitude,
                        speed = speed,
                        distance = distance,
                        lap = lap,
                        currentLapDistance = lapCounter,
                        movementDuration = movementFormattedTime,
                        inactivityDuration = lazyFormattedTime,
                        movementStateJson = movementState.getSerializableState(),
                        lazyStateJson = lazyState.getSerializableState(),
                        startDateTimeEpoch = startDateTime.toEpochSecond(java.time.ZoneOffset.UTC)
                    )

                    database.currentRecordingDao().insertCurrentRecord(currentRecord)

                    // Update service state in shared preferences for additional redundancy
                    updateServiceStatePreferences()

                    if (forceSave) {
                        Log.d(TAG, "Forced save of current state to database completed")
                    } else {
                        Log.d(TAG, "Saved current state to database")
                    }
                } else {
                    Log.d(TAG, "Skipping state save - invalid data or coordinates")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving current state to database", e)
            }
        }
    }

    // Helper method to update service state shared preferences
    private fun updateServiceStatePreferences() {
        try {
            val prefs = getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("was_running", isServiceStarted)
                .putInt("event_id", eventId)
                .putString("session_id", sessionId)
                .putFloat("last_latitude", latitude.toFloat())
                .putFloat("last_longitude", longitude.toFloat())
                .putFloat("covered_distance", distance.toFloat())
                .putLong("last_timestamp", lastUpdateTimestamp)
                .putInt("lap", lap)
                .putFloat("lap_counter", lapCounter.toFloat())
                .putLong("service_start_time", startDateTime.toEpochSecond(java.time.ZoneOffset.UTC))
                .putLong("last_lap_completion_time", lastLapCompletionTime)
                .putLong("lap_start_time", lapStartTime)
                // Save heart rate info
                .putString("heart_rate_device_address", heartRateDeviceAddress)
                .putString("heart_rate_device_name", heartRateDeviceName)
                // Save event details for recovery
                .putString("eventName", eventname)
                .putString("eventDate", eventdate)
                .putString("artOfSport", artofsport)
                .putString("comment", comment)
                .putString("clothing", clothing)
                .putBoolean("enable_websocket_transfer", enableWebSocketTransfer)
                .apply() // Fixed: This should be chained to the editor, not standalone
        } catch (e: Exception) {
            Log.e(TAG, "Error updating service state preferences", e)
        }
    }

    private suspend fun restoreFromDatabase() {
        withContext(Dispatchers.IO) {
            try {
                // Get latest record for this session
                val lastRecord = database.currentRecordingDao().getLatestRecordForSession(sessionId)

                lastRecord?.let {
                    // Restore basic tracking data
                    distance = it.distance
                    lap = it.lap
                    lapCounter = it.currentLapDistance
                    movementFormattedTime = it.movementDuration
                    lazyFormattedTime = it.inactivityDuration

                    // Restore start time
                    startDateTime = LocalDateTime.ofEpochSecond(
                        it.startDateTimeEpoch, 0, java.time.ZoneOffset.UTC
                    )

                    // Restore stopwatch states if available
                    if (it.movementStateJson.isNotEmpty()) {
                        movementState.restoreFromState(it.movementStateJson)
                    }

                    if (it.lazyStateJson.isNotEmpty()) {
                        lazyState.restoreFromState(it.lazyStateJson)
                    }

                    // Also restore lap times
                    val lapTimes = database.lapTimeDao().getLapTimesForSession(sessionId)
                    if (lapTimes.isNotEmpty()) {
                        // Update last lap completion time
                        val lastLapTime = lapTimes.maxByOrNull { it.endTime }
                        if (lastLapTime != null) {
                            lastLapCompletionTime = lastLapTime.endTime
                            // If we've completed laps, the next lap start time is the last lap end time
                            lapStartTime = lastLapTime.endTime
                        }
                    }

                    Log.d(TAG, "Restored state from database: distance=$distance, lap=$lap, lapCounter=$lapCounter")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring state from database", e)
            }
        }
    }

    // This function now tracks lap progress and saves lap times to the database
    private fun checkLapCompletionAndSave(newDistance: Double) {
        val prevLap = lap
        val prevLapCounter = lapCounter

        // Update lap counter with new distance
        lapCounter += newDistance

        // Check if we've completed a new lap (1000 meters)
        if (lapCounter >= 1000) {
            val completedLaps = (lapCounter / 1000).toInt()
            lap += completedLaps
            lapCounter -= completedLaps * 1000

            // If we've completed a new lap, save the lap time
            if (lap > prevLap) {
                val currentTime = System.currentTimeMillis()

                // Save lap time to database
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        for (i in 1..completedLaps) {
                            val newLapNumber = prevLap + i
                            val lapTime = LapTime(
                                sessionId = sessionId,
                                eventId = eventId,
                                lapNumber = newLapNumber,
                                startTime = lapStartTime,
                                endTime = currentTime,
                                distance = 1000.0 // Each lap is 1000 meters
                            )

                            database.lapTimeDao().insertLapTime(lapTime)
                            Log.d(TAG, "Saved lap time for lap $newLapNumber: ${currentTime - lapStartTime}ms")
                        }

                        // Update for next lap
                        lastLapCompletionTime = currentTime
                        lapStartTime = currentTime
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving lap time: ${e.message}")
                    }
                }
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
        val heartRateText = if (currentHeartRate > 0) {
            "\nHeart Rate: $currentHeartRate bpm"
        } else {
            ""
        }

        // Add barometer text
        val barometerText = if (currentPressure > 0) {
            val pressureInHg = BarometerUtils.hPaToInHg(currentPressure)
            "\nPressure: ${String.format("%.1f", currentPressure)} hPa (${String.format("%.2f", pressureInHg)} inHg)" +
                    "\nBarometric Altitude: ${String.format("%.1f", currentAltitudeFromPressure)} m"
        } else {
            ""
        }

        updateNotification(
            "Activity: " + movementFormattedTime +
                    "\nCovered Distance: " + String.format("%.2f", distance / 1000) + " Km" +
                    "\nSpeed: " + String.format("%.2f", speed) + " km/h" +
                    "\nGPS Altitude: " + String.format("%.2f", altitude) + " m" +
                    "\nLap: " + String.format("%2d", lap) +
                    heartRateText +
                    barometerText +
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
                // Calculate elevation gain/loss
                val currentElevation = altitude.toFloat()
                var elevGain = 0f
                var elevLoss = 0f

                if (hasSetInitialElevation) {
                    val elevationDiff = currentElevation - previousElevation
                    if (elevationDiff > 0) {
                        // We're going uphill, add to elevation gain
                        elevGain = elevationDiff
                        currentElevationGain += elevGain
                    } else if (elevationDiff < 0) {
                        // We're going downhill
                        elevLoss = -elevationDiff // Convert to positive value
                    }
                } else {
                    hasSetInitialElevation = true
                }
                previousElevation = currentElevation

                // Always save metrics data with barometer data included
                val metric = Metric(
                    eventId = eventId,
                    heartRate = currentHeartRate,
                    heartRateDevice = heartRateDeviceName ?: "None",
                    speed = speed,
                    distance = distance,
                    cadence = 0,
                    lap = lap,
                    timeInMilliseconds = currentTimeMillis(),
                    unity = "metric",
                    elevation = altitude.toFloat(),
                    elevationGain = elevGain,
                    elevationLoss = elevLoss,
                    temperature = if (currentTemperature > 0) currentTemperature.toFloat() else null,
                    accuracy = null,

                    // NEW: Include barometer data
                    pressure = if (currentPressure > 0) currentPressure else null,
                    pressureAccuracy = if (currentPressureAccuracy > 0) currentPressureAccuracy else null,
                    altitudeFromPressure = if (currentAltitudeFromPressure != 0f) currentAltitudeFromPressure else null,
                    seaLevelPressure = if (currentSeaLevelPressure > 0) currentSeaLevelPressure else null
                )
                database.metricDao().insertMetric(metric)
                Log.d(TAG, "Metric saved with barometer data: pressure=$currentPressure hPa, barometric altitude=$currentAltitudeFromPressure m")

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

            // Get previous distance value to calculate increment
            val previousDistance = distance

            // Update values from location update
            latitude = metrics.latitude
            longitude = metrics.longitude
            speed = metrics.speed
            distance = metrics.coveredDistance
            altitude = metrics.altitude
            lap = metrics.lap
            satellites = (metrics.satellites ?: 0).toString()

            // Calculate distance increment for lap tracking
            val distanceIncrement = distance - previousDistance
            if (distanceIncrement > 0) {
                // Update lap tracking with the new distance increment
                checkLapCompletionAndSave(distanceIncrement)
            }

            Log.d(TAG, "Location update received: lat=$latitude, lon=$longitude, speed=$speed, satellites=$satellites")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing location update", e)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onHeartRateData(data: HeartRateData) {
        if (data.isConnected && data.heartRate > 0) {
            currentHeartRate = data.heartRate
            heartRateDeviceName = data.deviceName
            Log.d(TAG, "Heart rate updated: ${data.heartRate} bpm")

            // Update CustomLocationListener with the latest heart rate (without triggering immediate updates)
            customLocationListener?.updateHeartRateOnly(data.heartRate, data.deviceName)  // Use data.* instead of current*
        }
    }

    // NEW: Subscribe to barometer data from EventBus
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBarometerData(data: BarometerData) {
        if (data.isAvailable) {
            currentPressure = data.pressure
            currentPressureAccuracy = data.accuracy
            currentAltitudeFromPressure = data.altitudeFromPressure
            currentSeaLevelPressure = data.seaLevelPressure

            Log.d(TAG, "Barometer data updated: ${data.pressure} hPa, altitude: ${data.altitudeFromPressure}m, accuracy: ${data.accuracy}")

            // Update CustomLocationListener with the latest barometer data
            customLocationListener?.updateBarometerData(data.pressure, data.accuracy, data.altitudeFromPressure, data.seaLevelPressure)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Check if we're stopping intentionally (flag set by stop button)
            if (intent?.getBooleanExtra("stopping_intentionally", false) == true) {
                isStoppingIntentionally = true
                Log.d(TAG, "Service is being stopped intentionally")
            }

            // Check if this is a restored session
            isRestoredSession = intent?.getBooleanExtra("is_restored_session", false) ?: false

            // Check if we're restarting after crash
            val prefs = getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
            val wasRunning = prefs.getBoolean("was_running", false)

            if (wasRunning || isRestoredSession) {
                Log.d(TAG, "Restoring from previous session (wasRunning=$wasRunning, isRestoredSession=$isRestoredSession)")

                // Restore state
                eventId = prefs.getInt("event_id", -1)
                sessionId = prefs.getString("session_id", "") ?: ""

                enableWebSocketTransfer = prefs.getBoolean("enable_websocket_transfer", true)

                // Restore position and distance data
                val lastLat = prefs.getFloat("last_latitude", -999f).toDouble()
                val lastLng = prefs.getFloat("last_longitude", -999f).toDouble()
                if (lastLat != -999.0 && lastLng != -999.0) {
                    lastKnownPosition = Pair(lastLat, lastLng)
                }
                distance = prefs.getFloat("covered_distance", 0f).toDouble()
                lastUpdateTimestamp = prefs.getLong("last_timestamp", currentTimeMillis())

                // Restore lap data
                lap = prefs.getInt("lap", 0)
                lapCounter = prefs.getFloat("lap_counter", 0f).toDouble()

                // Restore start time if available
                val storedStartTime = prefs.getLong("service_start_time", 0)
                if (storedStartTime > 0) {
                    startDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(storedStartTime),
                        ZoneOffset.UTC
                    )
                }

                Log.d(TAG, "Restored service state: eventId=$eventId, sessionId=$sessionId, " +
                        "distance=$distance, lap=$lap, startTime=$startDateTime")

                // Extract heart rate information from previous state
                heartRateDeviceAddress = prefs.getString("heart_rate_device_address", null)
                heartRateDeviceName = prefs.getString("heart_rate_device_name", null)

                // Extract event information from previous state or intent
                eventname = intent?.getStringExtra("eventName")
                    ?: prefs.getString("eventName", "Recovered Event")
                            ?: "Recovered Event"

                eventdate = intent?.getStringExtra("eventDate")
                    ?: prefs.getString("eventDate", "")
                            ?: ""

                artofsport = intent?.getStringExtra("artOfSport")
                    ?: prefs.getString("artOfSport", "Running")
                            ?: "Running"

                comment = intent?.getStringExtra("comment")
                    ?: prefs.getString("comment", "Recovered after crash")
                            ?: "Recovered after crash"

                clothing = intent?.getStringExtra("clothing")
                    ?: prefs.getString("clothing", "")
                            ?: ""

                // Save restored session data to SessionPrefs
                saveSessionDataToPreferences(
                    eventName = eventname,
                    eventDate = eventdate,
                    sportType = artofsport,
                    comment = comment,
                    clothing = clothing,
                    sessionId = sessionId,
                    enableWebSocketTransfer = enableWebSocketTransfer
                )

                // Clear restart flag
                prefs.edit().putBoolean("was_running", false).apply()

                // Set flag that we've done initial state restoration
                isInitialStateRestored = true
            } else {
                // Normal service start - extract extras from intent
                eventname = intent?.getStringExtra("eventName") ?: "Unknown Event"
                eventdate = intent?.getStringExtra("eventDate") ?: "Unknown Date"
                artofsport = intent?.getStringExtra("artOfSport") ?: "Unknown Sport"
                comment = intent?.getStringExtra("comment") ?: "No Comment"
                clothing = intent?.getStringExtra("clothing") ?: "No Clothing Info"
                enableWebSocketTransfer = intent?.getBooleanExtra("enableWebSocketTransfer", true) ?: true

                // Extract heart rate sensor info from intent
                heartRateDeviceAddress = intent?.getStringExtra("heartRateDeviceAddress")
                heartRateDeviceName = intent?.getStringExtra("heartRateDeviceName")

                // Generate session ID if not already created
                if (sessionId.isEmpty()) {
                    createSessionID()
                }

                // Save new session data to SessionPrefs
                saveSessionDataToPreferences(
                    eventName = eventname,
                    eventDate = eventdate,
                    sportType = artofsport,
                    comment = comment,
                    clothing = clothing,
                    sessionId = sessionId,
                    enableWebSocketTransfer = enableWebSocketTransfer
                )
            }

            // Connect to heart rate sensor if address is available
            if (!heartRateDeviceAddress.isNullOrEmpty()) {
                Log.d(TAG, "Connecting to heart rate sensor: $heartRateDeviceName ($heartRateDeviceAddress)")

                // Register for HeartRateData events if not already registered
                if (!isHrRegistered && !EventBus.getDefault().isRegistered(this)) {
                    EventBus.getDefault().register(this)
                    isHrRegistered = true
                }

                // Connect to the device
                serviceScope.launch {
                    delay(1000) // Short delay to ensure service is fully started
                    heartRateSensorService?.connectToDevice(heartRateDeviceAddress!!)
                }
            }

            // Start barometer sensor if available
            barometerSensorService?.let { service ->
                if (service.isAvailable()) {
                    val started = service.startListening()
                    if (started) {
                        Log.d(TAG, "Barometer sensor started successfully")

                        // Try to calibrate with GPS altitude when we have a good fix
                        if (altitude > 0 && altitude != -999.0) {
                            val calibrated = service.calibrateWithGpsAltitude(altitude.toFloat())
                            if (calibrated) {
                                Log.d(TAG, "Barometer sensor calibrated with GPS altitude: ${altitude}m")
                            } else {
                                Log.w(TAG, "Failed to calibrate barometer sensor with GPS altitude")
                            }
                        } else {
                            Log.d(TAG, "No valid GPS altitude for barometer calibration yet")
                        }
                    } else {
                        Log.w(TAG, "Failed to start barometer sensor")
                    }
                } else {
                    Log.w(TAG, "Barometer sensor not available on this device")
                }
            }

            Log.d(TAG, "Starting service with event: $eventname, sport: $artofsport, comment: $comment, clothing: $clothing, websocketTransfer: $enableWebSocketTransfer")

            // Debug log to verify session data is saved
            logSessionDataForDebugging()

            val eventIdDeferred = CompletableDeferred<Int>()

            // Only create a new event if this is not a restored session with a valid eventId
            if (isRestoredSession && eventId > 0) {
                // Use existing event ID
                eventIdDeferred.complete(eventId)
                Log.d(TAG, "Using existing event ID for restored session: $eventId")

                // If this is a recovered session, set flag to check more thoroughly
                if (wasRunning && !isInitialStateRestored) {
                    serviceScope.launch {
                        try {
                            // Verify session consistency - make sure data exists in the database
                            if (recoveryManager?.verifySessionConsistency(sessionId, eventId) == true) {
                                Log.d(TAG, "Session data verified from database for recovery")

                                // Try to load more accurate state from the database
                                recoveryManager?.getLatestSessionState(sessionId)?.let { state ->
                                    // Update our state with database values for better accuracy
                                    distance = state.distance
                                    lap = state.lap
                                    lapCounter = state.currentLapDistance

                                    // Use coordinates from database
                                    if (lastKnownPosition == null) {
                                        lastKnownPosition = Pair(state.latitude, state.longitude)
                                    }

                                    Log.d(TAG, "Updated state from database: distance=${state.distance}, lap=${state.lap}")
                                }
                            } else {
                                Log.w(TAG, "Session data couldn't be verified - using SharedPreferences values only")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during database consistency check", e)
                        }
                    }
                }

                // Start background coroutine specifically for restoration
                createBackgroundCoroutine(eventIdDeferred)
            } else {
                // Create new event
                createBackgroundCoroutine(eventIdDeferred)
            }

            serviceScope.launch {
                try {
                    currentEventId = eventIdDeferred.await()
                    eventId = currentEventId

                    // Save to SharedPreferences
                    getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("active_event_id", currentEventId)
                        .apply()

                    // Also ensure we set the recording state to true
                    getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_recording", true)
                        .apply()

                    Log.d(TAG, "Event ID saved: $currentEventId, recording state set to true")
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

        // Disconnect from heart rate sensor
        try {
            heartRateSensorService?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from heart rate sensor", e)
        }

        // NEW: Stop barometer sensor
        try {
            barometerSensorService?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping barometer sensor", e)
        }

        if (isStoppingIntentionally) {
            try {
                // Clear session data when stopping intentionally
                clearSessionDataFromPreferences()

                // Add this block to explicitly clear the recovery state immediately
                getSharedPreferences(SessionRecoveryManager.PREF_SERVICE_STATE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(SessionRecoveryManager.PREF_WAS_RUNNING, false)
                    .apply()

                Log.d(TAG, "Cleared recovery state and session data in SharedPreferences")

                // When stopping intentionally, finalize the recording by transferring data
                serviceScope.launch {
                    finalizeRecording()
                }

                // Reset the flag
                isStoppingIntentionally = false
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing session data and event ID from preferences", e)
            }
        } else {
            // Not stopping intentionally - might be a crash or kill
            // Don't clear the session data - keep it for recovery
            Log.d(TAG, "Not clearing session data - might be a crash")
        }

        try {
            customLocationListener?.cleanup()
            customLocationListener = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up location listener", e)
        }

        // Cancel state saving job
        currentStateJob?.cancel()
        currentStateJob = null

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

        connectionMonitorJob?.cancel()
        connectionMonitorJob = null
    }

    private suspend fun finalizeRecording() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Get all records from current_recording for this session
                val records = database.currentRecordingDao().getAllRecordsForSession(sessionId)
                Log.d(TAG, "Finalizing recording, found ${records.size} records")

                // 2. Get all lap times for this session
                val lapTimes = database.lapTimeDao().getLapTimesForSession(sessionId)
                Log.d(TAG, "Found ${lapTimes.size} lap records")

                // 3. We don't need to duplicate data that's already in the permanent tables
                // because we've been saving to both throughout the session.
                // We just need to ensure the lap times are properly stored

                // 4. Clear temporary tables
                if (records.isNotEmpty()) {
                    database.currentRecordingDao().clearSessionRecords(sessionId)
                    Log.d(TAG, "Cleared temporary recording records")
                }

                Log.d(TAG, "Recording finalized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing recording", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved called, attempting to schedule restart")

        try {
            // Save current state to survive restart - more comprehensive
            val prefs = getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("was_running", isServiceStarted)
                .putInt("event_id", eventId)
                .putString("session_id", sessionId)
                .putFloat("last_latitude", latitude.toFloat())
                .putFloat("last_longitude", longitude.toFloat())
                .putFloat("covered_distance", distance.toFloat())
                .putLong("last_timestamp", lastUpdateTimestamp)
                .putInt("lap", lap)
                .putFloat("lap_counter", lapCounter.toFloat())
                .putLong("service_start_time", startDateTime.toEpochSecond(java.time.ZoneOffset.UTC))
                .putLong("last_lap_completion_time", lastLapCompletionTime)
                .putLong("lap_start_time", lapStartTime)
                // Also save heart rate sensor information for restarts
                .putString("heart_rate_device_address", heartRateDeviceAddress)
                .putString("heart_rate_device_name", heartRateDeviceName)
                // Save event details for better restoration
                .putString("eventName", eventname)
                .putString("eventDate", eventdate)
                .putString("artOfSport", artofsport)
                .putString("comment", comment)
                .putString("clothing", clothing)
                .putBoolean("enable_websocket_transfer", enableWebSocketTransfer)
                .apply()

            // Force a final state save to the database
            serviceScope.launch {
                try {
                    saveCurrentState(forceSave = true)
                    Log.d(TAG, "Successfully saved final state before task removal")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving final state before task removal", e)
                }
            }

            // Create intent with all necessary extras from the original intent
            val restartIntent = Intent(applicationContext, ForegroundService::class.java).apply {
                putExtra("eventName", eventname)
                putExtra("eventDate", eventdate)
                putExtra("artOfSport", artofsport)
                putExtra("comment", comment)
                putExtra("clothing", clothing)
                putExtra("is_restored_session", true)
                putExtra("enableWebSocketTransfer", enableWebSocketTransfer)

                // Restore heart rate sensor information
                heartRateDeviceAddress?.let { putExtra("heartRateDeviceAddress", it) }
                heartRateDeviceName?.let { putExtra("heartRateDeviceName", it) }
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
            Log.d(TAG, "Service restart scheduled with full state preservation")
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

    private fun startConnectionMonitoring() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    delay(CONNECTION_CHECK_INTERVAL)

                    // Check if location listener exists and has a valid session ID
                    val isSessionValid = customLocationListener?.hasValidSession() ?: false
                    val isLocationTracking = customLocationListener != null

                    Log.d(TAG, "Connection status check: session valid=$isSessionValid, location tracking=$isLocationTracking")

                    if (!isLocationTracking || !isSessionValid) {
                        Log.w(TAG, "Reconnecting location tracking and WebSocket...")

                        // Recreate the location listener if needed
                        customLocationListener?.cleanup()
                        customLocationListener = CustomLocationListener(applicationContext).also {
                            it.startDateTime = startDateTime
                            delay(100) // Short delay to ensure sessionId is saved
                            it.startListener()

                            // Restore tracked values if we have them
                            if (distance > 0) {
                                it.resumeFromSavedState(distance, lastKnownPosition, lap, lapCounter)
                            }
                        }
                    }

                    // Also check heart rate sensor connection if needed
                    if (!heartRateDeviceAddress.isNullOrEmpty() && heartRateSensorService?.isConnected() != true) {
                        Log.d(TAG, "Reconnecting to heart rate sensor: $heartRateDeviceName")
                        heartRateSensorService?.connectToDevice(heartRateDeviceAddress!!)
                    }

                    // NEW: Check barometer sensor connection if needed
                    if (barometerSensorService?.isAvailable() == true && !barometerSensorService?.isListening()!!) {
                        Log.d(TAG, "Restarting barometer sensor")
                        barometerSensorService?.startListening()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in connection monitoring", e)
                }
            }
        }
    }

    private fun updateLocationListenerHeartRate() {
        // Make sure to update CustomLocationListener with the latest heart rate data
        customLocationListener?.updateHeartRate(currentHeartRate, heartRateDeviceName ?: "")

        Log.d(TAG, "Updated CustomLocationListener with heart rate: $currentHeartRate from device: $heartRateDeviceName")
    }

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val MIN_SPEED_THRESHOLD = 2.5f
        private const val EVENT_TIMEOUT_MS = 2000
        private var isStoppingIntentionally = false
        private const val TAG = "ForegroundService"

        // State saving constants
        private const val STATE_SAVE_INTERVAL = 5000L // 5 seconds

        // Weather
        private const val WEATHER_UPDATE_INTERVAL = 1800000L // 30 minutes in milliseconds
        private const val ERROR_RETRY_INTERVAL = 300000L // 5 minutes in milliseconds
        private const val WEATHER_FAST_POLL_INTERVAL = 10000L // 10 seconds for initial polling

        // reconnection logic
        private const val CONNECTION_CHECK_INTERVAL = 60_000L // 1 minute

        // restoring logic
        private const val STATE_CONSISTENCY_CHECK_INTERVAL = 30_000L // 30 seconds
    }
}