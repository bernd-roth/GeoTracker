package at.co.netconsulting.geotracker.service

import android.app.ActivityManager
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
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.data.LapTimeData
import at.co.netconsulting.geotracker.data.WeatherResponse
import at.co.netconsulting.geotracker.data.HeartRateData
import at.co.netconsulting.geotracker.data.BarometerData
import at.co.netconsulting.geotracker.data.DisciplineTransitionData
import at.co.netconsulting.geotracker.data.LtPhaseChanged
import at.co.netconsulting.geotracker.data.LtTestResult
import at.co.netconsulting.geotracker.data.WebSocketMessage
import at.co.netconsulting.geotracker.data.WingsForLifeUpdate
import at.co.netconsulting.geotracker.domain.CurrentRecording
import at.co.netconsulting.geotracker.domain.DeviceStatus
import at.co.netconsulting.geotracker.domain.DisciplineTransition
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.LapTime
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.User
import at.co.netconsulting.geotracker.domain.Weather
import at.co.netconsulting.geotracker.location.CustomLocationListener
import at.co.netconsulting.geotracker.sensor.BarometerSensorService
import at.co.netconsulting.geotracker.sensor.RunningCadenceTracker
import at.co.netconsulting.geotracker.service.WeatherEventBusHandler
import at.co.netconsulting.geotracker.tools.Tools
import at.co.netconsulting.geotracker.tools.BarometerUtils
import at.co.netconsulting.geotracker.tools.GeocodingHelper
import at.co.netconsulting.geotracker.widget.GeoTrackerWidget
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
import kotlinx.coroutines.runBlocking
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
import java.time.ZoneId
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
    private var bmi: Float = 0f
    private lateinit var eventname: String
    private lateinit var eventdate: String
    private lateinit var artofsport: String
    private lateinit var comment: String
    private lateinit var clothing: String
    @Volatile private var speed: Float = 0.0f
    private var eventId: Int = 0
    @Volatile private var distance: Double = 0.0
    @Volatile private var altitude: Double = 0.0
    @Volatile private var bearing: Float = 0.0f
    @Volatile private var latitude: Double = -999.0
    @Volatile private var longitude: Double = -999.0
    private var oldLatitude: Double = -999.0
    private var oldLongitude: Double = -999.0
    private var lap: Int = 0
    private var lapCounter: Double = 0.0 // Added to track partial lap progress
    private var lazyFormattedTime: String = "00:00:00"
    private var movementFormattedTime: String = "00:00:00"
    private val serviceJob = SupervisorJob() // Changed to SupervisorJob for better error handling
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null // Made nullable for safer handling
    @Volatile private var lastUpdateTimestamp: Long = currentTimeMillis()
    private var isCurrentlyMoving = false
    private val movementState = StopwatchState()
    private val lazyState = StopwatchState()
    private var customLocationListener: CustomLocationListener? = null
    private var currentEventId: Int = -1
    @Volatile private var satellites: String = "0"
    private var hasReceivedValidWeather = false

    // Heart rate monitoring
    private var heartRateSensorService: HeartRateSensorService? = null
    private var heartRateDeviceAddress: String? = null
    private var heartRateDeviceName: String? = null
    @Volatile private var currentHeartRate: Int = 0
    private var isHrRegistered = false

    // Barometer sensor monitoring
    private var barometerSensorService: BarometerSensorService? = null
    private var currentPressure: Float = 0f
    private var currentPressureAccuracy: Int = 0
    private var currentAltitudeFromPressure: Float = 0f
    private var currentSeaLevelPressure: Float = 1013.25f

    // Running cadence from the device step detector
    private var runningCadenceTracker: RunningCadenceTracker? = null

    //reconnection logic
    private var connectionMonitorJob: Job? = null
    private var isWebSocketConnected = false
    //Weather
    private var weatherJob: Job? = null
    private val weatherScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentTemperature: Double? = null
    private var currentWindSpeed: Double = 0.0
    private var currentWindDirection: Double = 0.0
    private var currentRelativeHumidity: Int = 0
    private var currentWeatherCode: Int = 0
    private var currentWeatherTime: String = ""
    //sessionId
    private var sessionId: String = ""

    @Volatile private var isServiceStarted = false
    @Volatile private var isAcceptingLocationUpdates = false

    //restoring destroyed session
    private var isRestoredSession = false
    private var lastKnownPosition: Pair<Double, Double>? = null
    private var startDateTime: LocalDateTime = LocalDateTime.now()
    @Volatile private var recordingStartTimestampMs: Long = 0L
    @Volatile private var latestRecordingTimestampMs: Long = 0L

    // State saving variables
    private var currentStateJob: Job? = null
    private var lastLapCompletionTime: Long = 0
    private var lapStartTime: Long = System.currentTimeMillis()

    // Max. elevation gain calculation
    private var previousElevation: Float = 0f
    private var hasSetInitialElevation: Boolean = false
    private var currentElevationGain: Float = 0f

    // Real-time slope calculation
    private var previousDistance: Double = 0.0
    private var previousAltitude: Double = 0.0
    private var currentSlope: Double = 0.0
    private var slopeHistory = mutableListOf<Double>()
    private val maxSlopeHistorySize = 5  // For smoothing GPS noise

    // Slope statistics tracking
    private var allSlopeMeasurements = mutableListOf<Double>()
    private var maxUphillSlope: Double = 0.0
    private var maxDownhillSlope: Double = 0.0

    // recovery after crash
    private var recoveryManager: SessionRecoveryManager? = null
    private var stateConsistencyCheckerJob: Job? = null
    private var isInitialStateRestored = false

    // WebSocket transfer setting
    private var enableWebSocketTransfer: Boolean = true

    // Pause/Resume functionality
    @Volatile private var isPaused = false
    private var pauseStartTime: Long = 0
    private var totalPausedDurationMs: Long = 0

    // Backyard Ultra mode
    private var isBackyardUltraMode = false
    private var backyardLapNumber: Int = 0
    private var backyardLapStartTime: Long = 0L

    // Wings for Life Run mode (virtual catcher car)
    private var isWingsForLifeMode = false
    @Volatile private var wflWasCaught = false
    @Volatile private var wflCaughtAtDistanceMeters: Double = 0.0
    @Volatile private var wflCaughtAtElapsedMs: Long = 0L
    @Volatile private var wflLastAnnouncedHeadwayKm: Int = -1
    private var wflTts: TextToSpeech? = null
    private var isWflTtsInitialized = false

    // Lactate Threshold mode
    private var isLactateThresholdMode = false
    private var ltTestStartTime: Long = 0L
    private var ltMeasurementPhaseStartTime: Long = 0L
    private var ltHeartRateSamples = mutableListOf<Int>()
    private var ltSpeedSamples = mutableListOf<Double>()
    private var ltCountdownJob: Job? = null
    private val LT_TOTAL_DURATION_MS = 30 * 60 * 1000L     // 30 minutes
    private val LT_SETTLE_DURATION_MS = 10 * 60 * 1000L    // 10 minutes settle-in

    // Geocoding flag to track if start location has been geocoded
    private var hasGeocodedStartLocation = false

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
                remove("current_session_id") // Clear the session ID to prevent old lap data from loading
                apply()
            }

            // Clear lap times from the WeatherEventBusHandler
            try {
                WeatherEventBusHandler.getInstance(this@ForegroundService).clearLapTimes()
                Log.d(TAG, "Cleared lap times from WeatherEventBusHandler")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing lap times from handler", e)
            }

            Log.d(TAG, "Session data and lap times cleared from SharedPreferences")
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

            return withContext(Dispatchers.IO) {
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

                false
            }
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
            .setUsesChronometer(true)
            .setChronometerCountDown(false)

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

        // Initialize barometer sensor service
        barometerSensorService = BarometerSensorService.getInstance(this)

        // Initialize running cadence sensor (started after the sport type is known)
        runningCadenceTracker = RunningCadenceTracker(this)

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
        bmi = sharedPreferences.getFloat("bmi", 0f)
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
                it.totalDuration.toMillis()
            }

            val currentDuration = currentSegment?.let {
                Duration.between(it.startTime, LocalDateTime.now()).toMillis()
            } ?: 0L

            return Duration.ofMillis(completedDuration + currentDuration)
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
                // Restore currentSegment from the last open (unfinished) segment
                // so getTotalDuration() continues advancing instead of appearing frozen
                currentSegment = segments.lastOrNull { it.endTime == null }
            } catch (e: Exception) {
                Log.e("StopwatchState", "Error restoring state: ${e.message}")
            }
        }
    }

    private fun createBackgroundCoroutine(eventIdDeferred: CompletableDeferred<Int>) {
        serviceScope.launch {
            try {
                isServiceStarted = true
                isAcceptingLocationUpdates = false
                hasFinalized = false // Reset static flag for new recording

                // Use a broader flag that covers both explicit restoration (isRestoredSession)
                // and system auto-restart restoration (isInitialStateRestored via wasRunning).
                val isRestoring = isRestoredSession || isInitialStateRestored

                // If we're restoring, first try to get the latest state from the database
                if (isRestoring) {
                    restoreFromDatabase()
                }

                // Create the location listener with restored session context
                Log.d(TAG, "DIAG FG createBackgroundCoroutine CREATING CLL isRestoring=$isRestoring distance=$distance lastKnownPosition=$lastKnownPosition lap=$lap lapCounter=$lapCounter")
                customLocationListener = CustomLocationListener(applicationContext).also {
                    // Use the restored start time if this is a restored session
                    if (isRestoring) {
                        it.startDateTime = startDateTime
                        Log.d(TAG, "Restored session startDateTime: $startDateTime")
                    } else {
                        startDateTime = LocalDateTime.now()
                        it.startDateTime = startDateTime
                        // Initialize lap tracking for new session
                        initializeLapTracking()
                    }

                    // Make sure we wait until sessionId is created and saved.
                    // Also wait for BackgroundLocationService to fully stop before
                    // we requestLocationUpdates: both share the system LocationManager,
                    // and on several OEM ROMs a near-simultaneous removeUpdates from
                    // the dying BG service makes GPS_PROVIDER drop into an idle state
                    // that delivers exactly one cached fix and then nothing more —
                    // which is what made the timer/distance freeze at ~1 second.
                    delay(100) // Ensure sessionId is saved to SharedPreferences
                    waitForBackgroundLocationServiceStopped(timeoutMs = 2000L)

                    if (isStoppingIntentionally) {
                        Log.d(TAG, "DIAG FG createBackgroundCoroutine skipped CLL start because service is stopping")
                        return@also
                    }

                    isAcceptingLocationUpdates = true
                    it.startListener()

                    // If we have restored position and distance, initialize the location listener with them
                    if (isRestoring && distance > 0) {
                        it.resumeFromSavedState(distance, lastKnownPosition, lap, lapCounter)
                        Log.d(TAG, "Resumed from saved state: distance=$distance, position=$lastKnownPosition, lap=$lap, lapCounter=$lapCounter")
                    }
                }

                val userId = database.userDao().insertUser(User(0, firstname, lastname, birthdate, weight, height, bmi))

                // Only create a new event if this is not a restored session with valid eventId
                if (isRestoring && eventId > 0) {
                    eventIdDeferred.complete(eventId)
                } else {
                    eventId = createNewEvent(database, userId)
                    eventIdDeferred.complete(eventId)

                    // Create initial discipline transition for multisport races
                    val initialDiscipline = when {
                        artofsport.equals("Duathlon", ignoreCase = true) -> "Run"
                        artofsport.equals("Triathlon", ignoreCase = true) ||
                        artofsport.equals("Ultratriathlon", ignoreCase = true) -> "Swim"
                        else -> null
                    }
                    if (initialDiscipline != null) {
                        try {
                            database.disciplineTransitionDao().insertTransition(
                                DisciplineTransition(
                                    eventId = eventId,
                                    sessionId = sessionId,
                                    disciplineName = initialDiscipline,
                                    transitionNumber = 1,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                                .edit()
                                .putString("current_discipline", initialDiscipline)
                                .putInt("discipline_transition_count", 1)
                                .apply()
                            // Send initial discipline transition to WebSocket server via EventBus
                            EventBus.getDefault().post(
                                WebSocketMessage.DisciplineTransitionMessage(
                                    sessionId = sessionId,
                                    eventName = eventname,
                                    transition = DisciplineTransitionData(
                                        disciplineName = initialDiscipline,
                                        transitionNumber = 1,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            )
                            Log.d(TAG, "Initial $artofsport discipline transition recorded: $initialDiscipline #1")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error recording initial discipline transition", e)
                        }
                    }
                }

                // Start weather updates right away with aggressive polling
                startWeatherUpdates()

                // Start periodic state saving
                startPeriodicStateSaving()

                var loopIter = 0L
                Log.d(TAG, "DIAG FG mainLoop STARTED")
                while (isActive) {
                    loopIter++
                    val currentTime = currentTimeMillis()
                    val sinceLastUpdate = currentTime - lastUpdateTimestamp
                    val timedOut = sinceLastUpdate > EVENT_TIMEOUT_MS
                    val coordsValid = checkLatitudeLongitude()
                    Log.d(TAG,
                        "DIAG FG mainLoop tick #$loopIter " +
                                "isPaused=$isPaused speed=$speed distance=$distance " +
                                "lat=$latitude lon=$longitude coordsValid=$coordsValid " +
                                "sinceLastMetrics=${sinceLastUpdate}ms timedOut=$timedOut " +
                                "cllInstance=${customLocationListener?.let { System.identityHashCode(it) }}")

                    if (timedOut) {
                        Log.w(TAG, "DIAG FG mainLoop EVENT_TIMEOUT firing resetValues (no Metrics for ${sinceLastUpdate}ms > $EVENT_TIMEOUT_MS) preTimeoutSpeed=$speed preTimeoutDistance=$distance")
                        resetValues()
                    }

                    // Only update stopwatches if not paused
                    if (!isPaused) {
                        // Use movement-based tracking
                        if (speed >= MIN_SPEED_THRESHOLD) {
                            showStopWatch()
                        } else {
                            showLazyStopWatch()
                        }
                    }

                    // Tick the Wings for Life catcher car before painting the
                    // notification so its line reflects the latest comparison.
                    if (!isPaused) {
                        tickWingsForLifeCatcher()
                    }

                    showNotification()

                    // Save data to database when we have valid coordinates
                    if (!isPaused) {
                        if (coordsValid) {
                            Log.d(TAG, "DIAG FG mainLoop calling insertDatabase distance=$distance lat=$latitude lon=$longitude")
                            insertDatabase(database)
                        } else {
                            Log.d(TAG, "DIAG FG mainLoop SKIP insertDatabase (invalid coords) lat=$latitude lon=$longitude")
                        }
                    } else {
                        Log.d(TAG, "DIAG FG mainLoop SKIP insertDatabase (isPaused=true)")
                    }
                    delay(1000)
                }
                Log.d(TAG, "DIAG FG mainLoop EXITED after $loopIter iterations")
            } catch (e: Exception) {
                eventIdDeferred.completeExceptionally(e)
                Log.e(TAG, "Error in background coroutine", e)
            }
        }
    }

    // Initialize lap tracking properly for new sessions
    private fun initializeLapTracking() {
        // Make sure lap starts at 0 so first completed km becomes lap 1
        lap = 0
        lapCounter = 0.0
        lapStartTime = System.currentTimeMillis()

        Log.d(TAG, "Lap tracking initialized: lap=$lap, lapCounter=$lapCounter, startTime=$lapStartTime")
    }

    private fun startPeriodicStateSaving() {
        currentStateJob?.cancel()
        currentStateJob = serviceScope.launch {
            var iter = 0L
            Log.d(TAG, "DIAG FG periodicStateSave STARTED")
            while (isActive) {
                try {
                    iter++
                    Log.d(TAG, "DIAG FG periodicStateSave tick #$iter distance=$distance sessionId='$sessionId' eventId=$eventId lat=$latitude lon=$longitude")
                    // Save current state every 5 seconds
                    saveCurrentState()
                    delay(STATE_SAVE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "DIAG FG periodicStateSave caught Exception isActive=$isActive", e)
                }
            }
            Log.d(TAG, "DIAG FG periodicStateSave EXITED after $iter iterations")
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
                .putLong("recording_start_time_ms", recordingStartTimestampMs)
                .putLong("latest_recording_time_ms", latestRecordingTimestampMs)
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
                // Save pause state
                .putBoolean("is_paused", isPaused)
                .putLong("total_paused_duration_ms", totalPausedDurationMs)
                .putLong("pause_start_time_saved", pauseStartTime)
                .apply()
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

                if (eventId > 0) {
                    database.metricDao().getEventTimeRange(eventId)?.let { timeRange ->
                        if (timeRange.minTime > 0L && timeRange.maxTime >= timeRange.minTime) {
                            recordingStartTimestampMs = timeRange.minTime
                            latestRecordingTimestampMs = timeRange.maxTime
                            startDateTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(timeRange.minTime),
                                ZoneId.systemDefault()
                            )
                            Log.d(
                                TAG,
                                "Restored recording clock from metrics: start=${timeRange.minTime}, latest=${timeRange.maxTime}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring state from database", e)
            }
        }
    }

    /**
     * Detect new lap completions by comparing the authoritative lap count from
     * CustomLocationListener (metrics.lap) with laps already saved in the database.
     *
     * Previous approach compared metrics.lap with an in-memory FS.lap variable,
     * but FS.lap could silently match CLL.lap after state restoration (SharedPrefs,
     * database recovery, connection-monitor listener recreation), causing
     * syncLapFromMetrics to believe all laps were already saved.
     *
     * Now we query the actual database for the highest saved lap number.  This is
     * authoritative — no matter how FS.lap drifts, we always detect truly missing
     * laps.  The query runs on IO and is guarded by an AtomicBoolean to prevent
     * overlapping coroutines.
     */
    private val isSyncingLaps = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun syncLapFromMetrics(metricsLap: Int) {
        // Quick check: if metrics reports 0, nothing to do
        if (metricsLap <= 0) return

        // Keep FS.lap in sync for display / state-save purposes
        if (metricsLap > lap) {
            lap = metricsLap
        }

        // Prevent overlapping sync coroutines
        if (!isSyncingLaps.compareAndSet(false, true)) return

        val currentTime = System.currentTimeMillis()

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Query the database for the highest lap number already saved for this session.
                // Using MAX(lapNumber) rather than COUNT(*) so gaps or duplicates don't mislead.
                val maxSavedLap = database.lapTimeDao().getMaxLapNumberBySession(sessionId)

                if (metricsLap <= maxSavedLap) {
                    Log.d(TAG, "LAP SYNC: metricsLap=$metricsLap, maxSavedLap=$maxSavedLap — already up to date")
                    return@launch
                }

                Log.d(TAG, "LAP SYNC: metricsLap=$metricsLap, maxSavedLap=$maxSavedLap — saving ${metricsLap - maxSavedLap} new lap(s)")

                for (newLapNumber in (maxSavedLap + 1)..metricsLap) {
                    val lapTime = LapTime(
                        sessionId = sessionId,
                        eventId = eventId,
                        lapNumber = newLapNumber,
                        startTime = lapStartTime,
                        endTime = currentTime,
                        distance = 1.0 // 1 km per lap
                    )

                    database.lapTimeDao().insertLapTime(lapTime)
                    Log.d(TAG, "Saved lap time for lap $newLapNumber: ${currentTime - lapStartTime}ms")
                    Log.d(TAG, "LAP DEBUG: maxSavedLap=$maxSavedLap, newLapNumber=$newLapNumber, total distance=${distance/1000}km")
                }

                // Update for next lap
                lastLapCompletionTime = currentTime
                lapStartTime = currentTime

                // Notify WeatherEventBusHandler to refresh lap times
                try {
                    val weatherHandler = WeatherEventBusHandler.getInstance()
                    weatherHandler.refreshLapTimes()
                    Log.d(TAG, "Notified WeatherEventBusHandler to refresh lap times")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not notify WeatherEventBusHandler to refresh lap times: ${e.message}")
                }

                // Transmit updated lap times via WebSocket if enabled
                if (enableWebSocketTransfer) {
                    try {
                        transmitLapTimesToWebSocket()
                        Log.d(TAG, "Transmitted lap times to WebSocket server")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not transmit lap times to WebSocket: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving lap time: ${e.message}")
            } finally {
                isSyncingLaps.set(false)
            }
        }
    }

    // ── Lactate Threshold 30-min Time Trial ──────────────────────────

    private fun startLtCountdownTimer() {
        ltCountdownJob?.cancel()
        ltCountdownJob = serviceScope.launch {
            var lastPhase = "settle"
            while (isActive) {
                val elapsed = System.currentTimeMillis() - ltTestStartTime
                val remainingMs = (LT_TOTAL_DURATION_MS - elapsed).coerceAtLeast(0)
                val remainingSeconds = remainingMs / 1000

                val phase = if (elapsed < LT_SETTLE_DURATION_MS) "settle" else "measurement"
                if (phase != lastPhase) {
                    lastPhase = phase
                    Log.d(TAG, "LT test phase changed to: $phase")
                }

                EventBus.getDefault().post(
                    LtPhaseChanged(
                        phase = phase,
                        remainingSeconds = remainingSeconds,
                        totalSeconds = LT_TOTAL_DURATION_MS / 1000
                    )
                )

                if (remainingMs <= 0) {
                    // 30 minutes elapsed — auto-stop
                    Log.d(TAG, "LT test completed (30 min)")
                    finalizeLtTest()

                    // Send stop_recording action to self
                    val stopIntent = Intent(this@ForegroundService, ForegroundService::class.java).apply {
                        putExtra("action", "stop_recording")
                    }
                    startService(stopIntent)
                    break
                }

                delay(1000)
            }
        }
    }

    private fun finalizeLtTest() {
        val avgHr = if (ltHeartRateSamples.isNotEmpty())
            ltHeartRateSamples.average().toInt() else 0
        val avgSpeedMs = if (ltSpeedSamples.isNotEmpty())
            ltSpeedSamples.average() else 0.0
        // pace = minutes per km; speed is m/s
        val avgPaceMinPerKm = if (avgSpeedMs > 0) (1000.0 / avgSpeedMs) / 60.0 else 0.0

        Log.d(TAG, "LT test finalized: avgHR=$avgHr, avgPace=${"%.2f".format(avgPaceMinPerKm)} min/km, " +
                "samples=${ltHeartRateSamples.size}")

        // Persist results to UserSettings
        getSharedPreferences("UserSettings", MODE_PRIVATE).edit()
            .putInt("lactate_threshold_hr", avgHr)
            .putFloat("lactate_threshold_pace", avgPaceMinPerKm.toFloat())
            .putString("lactate_threshold_date", java.time.LocalDate.now().toString())
            .apply()

        // Post result to UI via EventBus
        EventBus.getDefault().post(
            LtTestResult(
                avgHeartRate = avgHr,
                avgPaceMinPerKm = avgPaceMinPerKm,
                totalSamples = ltHeartRateSamples.size,
                testDate = java.time.LocalDate.now().toString()
            )
        )

        // Mark mode as done so we don't finalize twice
        isLactateThresholdMode = false
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
        movementFormattedTime = formatDurationHms(duration.toMillis())
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
        lazyFormattedTime = formatDurationHms(duration.toMillis())
    }

    private fun showContinuousStopWatch() {
        // For stationary activities, always count as movement time
        if (!isCurrentlyMoving) {
            movementState.start()
            isCurrentlyMoving = true
        }

        val duration = movementState.getTotalDuration()
        movementFormattedTime = formatDurationHms(duration.toMillis())

        // Keep lazy time at zero for stationary activities
        lazyFormattedTime = "00:00:00"
    }

    private fun formatDurationHms(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L)) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun refreshRecordingDurationLabels(totalRecordingMs: Long) {
        val movementMs = movementState.getTotalDuration()
            .toMillis()
            .coerceAtLeast(0L)
            .coerceAtMost(totalRecordingMs.coerceAtLeast(0L))
        val inactivityMs = (totalRecordingMs - movementMs).coerceAtLeast(0L)

        movementFormattedTime = formatDurationHms(movementMs)
        lazyFormattedTime = formatDurationHms(inactivityMs)
    }

    private fun resetValues() {
        Log.d(TAG, "DIAG FG resetValues ENTER preSpeed=$speed (only speed is reset; distance=$distance is preserved)")
        speed = 0.0F
        showNotification()
    }

    /**
     * Effective recording duration in milliseconds — wall-clock time since
     * startDateTime minus all paused intervals (including any current pause).
     * This is what the Wings for Life catcher car sees and what we display
     * as "Total Recording" in the notification.
     */
    private fun getEffectiveRecordingMs(): Long {
        val totalMs = Duration.between(startDateTime, LocalDateTime.now()).toMillis()
        val pausedMs = totalPausedDurationMs + if (isPaused && pauseStartTime > 0) {
            System.currentTimeMillis() - pauseStartTime
        } else 0L
        return (totalMs - pausedMs).coerceAtLeast(0L)
    }

    private fun updateRecordingClockFromMetric(timestampMs: Long) {
        if (timestampMs <= 0L) return

        if (recordingStartTimestampMs == 0L || timestampMs < recordingStartTimestampMs) {
            recordingStartTimestampMs = timestampMs
            startDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestampMs),
                ZoneId.systemDefault()
            )
            customLocationListener?.startDateTime = startDateTime

            Log.d(TAG, "Recording clock anchored to first metric at $startDateTime ($timestampMs)")
        }

        if (timestampMs > latestRecordingTimestampMs) {
            latestRecordingTimestampMs = timestampMs
        }
    }

    private fun refreshForegroundServiceTypes(includeCadence: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (includeCadence && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceTypes = serviceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            }
            startForeground(1, notificationBuilder.build(), serviceTypes)
        } else {
            startForeground(1, notificationBuilder.build())
        }
    }

    private fun supportsStepCadence(sportType: String): Boolean {
        val normalized = sportType.lowercase(Locale.ROOT)
        return listOf(
            "running",
            "marathon",
            "walking",
            "hiking",
            "backyard ultra",
            "wings for life",
            "lactate threshold"
        ).any(normalized::contains)
    }

    private fun startCadenceTrackingIfSupported(sportType: String = artofsport) {
        val tracker = runningCadenceTracker ?: return
        if (!supportsStepCadence(sportType)) {
            tracker.pause()
            return
        }

        if (tracker.start()) {
            refreshForegroundServiceTypes(includeCadence = true)
            Log.d(TAG, "Running cadence tracking started for '$sportType'")
        } else {
            Log.i(
                TAG,
                "Running cadence unavailable for '$sportType' " +
                    "(step detector missing or activity-recognition permission denied)"
            )
        }
    }

    private fun getRecordedDurationMs(): Long {
        val startMs = recordingStartTimestampMs
        if (startMs <= 0L) return getEffectiveRecordingMs()

        val endMs = when {
            isPaused && pauseStartTime > 0 -> currentTimeMillis()
            latestRecordingTimestampMs > 0L -> latestRecordingTimestampMs
            else -> currentTimeMillis()
        }

        val pausedMs = totalPausedDurationMs + if (isPaused && pauseStartTime > 0) {
            currentTimeMillis() - pauseStartTime
        } else {
            0L
        }

        return (endMs - startMs - pausedMs).coerceAtLeast(0L)
    }

    private fun configureNotificationChronometer(displayedDurationMs: Long) {
        if (!::notificationBuilder.isInitialized) return

        val chronometerBaseMs = currentTimeMillis() - displayedDurationMs.coerceAtLeast(0L)
        notificationBuilder
            .setWhen(chronometerBaseMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
    }

    /**
     * Wings for Life Run catcher car schedule (since 2021). Time is the
     * elapsed run time excluding pauses. Returns the cumulative distance
     * the catcher car has covered, in meters.
     *
     *   00:00 - 00:30  →  0 km/h   (runner head start)
     *   00:30 - 01:00  → 14 km/h
     *   01:00 - 01:30  → 15 km/h
     *   01:30 - 02:00  → 16 km/h
     *   02:00 - 02:30  → 17 km/h
     *   02:30 - 03:00  → 18 km/h
     *   03:00 - 03:30  → 22 km/h
     *   03:30 - 04:00  → 26 km/h
     *   04:00 - 04:30  → 30 km/h
     *   04:30+         → 34 km/h
     */
    private fun wingsForLifeCatcherDistanceMeters(elapsedMs: Long): Double {
        if (elapsedMs <= 0L) return 0.0
        // (boundary in minutes, speed in km/h active up to that boundary)
        val schedule = intArrayOf(30, 60, 90, 120, 150, 180, 210, 240, 270)
        val speeds = doubleArrayOf(0.0, 14.0, 15.0, 16.0, 17.0, 18.0, 22.0, 26.0, 30.0)
        val finalSpeedKmh = 34.0
        var meters = 0.0
        var prevBoundaryMs = 0L
        for (i in schedule.indices) {
            val boundaryMs = schedule[i] * 60_000L
            val mPerMs = speeds[i] * 1000.0 / 3_600_000.0
            if (elapsedMs <= boundaryMs) {
                return meters + mPerMs * (elapsedMs - prevBoundaryMs)
            }
            meters += mPerMs * (boundaryMs - prevBoundaryMs)
            prevBoundaryMs = boundaryMs
        }
        val mPerMs = finalSpeedKmh * 1000.0 / 3_600_000.0
        return meters + mPerMs * (elapsedMs - prevBoundaryMs)
    }

    /**
     * Tick the Wings for Life catcher car: compare its position with the
     * runner's covered distance and announce the catch (once). Recording
     * continues regardless — only the catch point is marked.
     */
    private fun tickWingsForLifeCatcher() {
        if (!isWingsForLifeMode) return
        val elapsedMs = getEffectiveRecordingMs()
        val catcherMeters = wingsForLifeCatcherDistanceMeters(elapsedMs)

        // Always broadcast — MapScreen needs the catcher position even after the catch.
        EventBus.getDefault().post(
            WingsForLifeUpdate(
                catcherDistanceMeters = catcherMeters,
                runnerDistanceMeters = distance,
                wasCaught = wflWasCaught,
                caughtAtDistanceMeters = wflCaughtAtDistanceMeters,
                caughtAtElapsedMs = wflCaughtAtElapsedMs,
                elapsedMs = elapsedMs
            )
        )

        if (wflWasCaught) return
        if (catcherMeters >= distance && distance > 0.0) {
            wflWasCaught = true
            wflCaughtAtDistanceMeters = distance
            wflCaughtAtElapsedMs = elapsedMs
            persistWingsForLifeState()
            val km = String.format(Locale.US, "%.2f", distance / 1000.0)
            val mins = elapsedMs / 60_000
            announceWingsForLife(
                "Catcher car has caught you. Distance $km kilometers, time $mins minutes."
            )
            Log.d(TAG, "WFL caught: distance=${distance}m, elapsedMs=$elapsedMs")
            return
        }
        // Pre-catch headway announcement at 1, 0.5 and 0.1 km thresholds.
        val headwayKm = (distance - catcherMeters) / 1000.0
        val bucket = when {
            headwayKm > 1.0 -> 1000   // sentinel — no announcement
            headwayKm > 0.5 -> 1
            headwayKm > 0.1 -> 2
            else            -> 3
        }
        if (bucket != 1000 && bucket != wflLastAnnouncedHeadwayKm) {
            wflLastAnnouncedHeadwayKm = bucket
            val msg = when (bucket) {
                1 -> "Catcher car is one kilometer behind."
                2 -> "Catcher car is five hundred meters behind."
                else -> "Catcher car is one hundred meters behind."
            }
            announceWingsForLife(msg)
        }
    }

    private fun initWingsForLifeTts() {
        if (wflTts != null) return
        wflTts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                wflTts?.setLanguage(Locale.getDefault())
                isWflTtsInitialized = true
                Log.d(TAG, "WFL TTS initialized")
            } else {
                Log.e(TAG, "WFL TTS init failed: $status")
            }
        }
    }

    private fun announceWingsForLife(message: String) {
        if (!isWflTtsInitialized) {
            Log.w(TAG, "WFL TTS not ready, dropping message: $message")
            return
        }
        wflTts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "wfl_${System.currentTimeMillis()}")
    }

    private fun persistWingsForLifeState() {
        try {
            getSharedPreferences("RecordingState", Context.MODE_PRIVATE).edit()
                .putBoolean("wfl_was_caught", wflWasCaught)
                .putFloat("wfl_caught_at_distance_m", wflCaughtAtDistanceMeters.toFloat())
                .putLong("wfl_caught_at_elapsed_ms", wflCaughtAtElapsedMs)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting WFL state", e)
        }
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

        // Match Event Times by using the metric timestamp range that is stored in the DB.
        val effectiveMs = getRecordedDurationMs()
        refreshRecordingDurationLabels(effectiveMs)
        configureNotificationChronometer(effectiveMs)
        val totalRecordingDuration = Duration.ofMillis(effectiveMs)

        val totalHours = totalRecordingDuration.toHours()
        val totalMinutes = (totalRecordingDuration.toMinutes() % 60)
        val totalSeconds = (totalRecordingDuration.seconds % 60)
        val totalRecordingFormattedTime = String.format("%02d:%02d:%02d", totalHours, totalMinutes, totalSeconds)

        // Wings for Life Run: catcher car status
        val catcherText = if (isWingsForLifeMode) {
            if (wflWasCaught) {
                val caughtKm = String.format(Locale.US, "%.2f", wflCaughtAtDistanceMeters / 1000.0)
                val caughtMin = wflCaughtAtElapsedMs / 60_000
                val caughtSec = (wflCaughtAtElapsedMs / 1000) % 60
                "\nCatcher: CAUGHT at $caughtKm km / ${String.format("%02d:%02d", caughtMin, caughtSec)}"
            } else {
                val catcherKm = wingsForLifeCatcherDistanceMeters(effectiveMs) / 1000.0
                val headwayKm = (distance / 1000.0) - catcherKm
                "\nCatcher: ${String.format(Locale.US, "%.2f", catcherKm)} km " +
                        "(${String.format(Locale.US, "%+.2f", headwayKm)} km headway)"
            }
        } else ""

        // For Backyard Ultra: display distance based on completed laps × 6.7606 km
        val displayDistanceKm = if (isBackyardUltraMode) {
            val completedLaps = if (isPaused) backyardLapNumber else (backyardLapNumber - 1).coerceAtLeast(0)
            completedLaps * 6.7606
        } else {
            distance / 1000
        }
        val lapDisplay = if (isBackyardUltraMode) {
            "\nBackyard Lap: $backyardLapNumber" + if (isPaused) " (resting)" else " (running)"
        } else {
            "\nLap: " + String.format("%2d", lap)
        }

        val notificationTitle = "${String.format("%.2f", displayDistanceKm)} km • $totalRecordingFormattedTime"

        updateNotification(
            notificationTitle,
            "Total Recording: " + totalRecordingFormattedTime +
                    "\nActivity: " + movementFormattedTime +
                    "\nInactivity: " + lazyFormattedTime +
                    "\nCovered Distance: " + String.format("%.2f", displayDistanceKm) + " Km" +
                    "\nSpeed: " + String.format("%.2f", speed) + " km/h" +
                    "\nGPS Altitude: " + String.format("%.2f", altitude) + " m" +
                    lapDisplay +
                    catcherText +
                    heartRateText +
                    barometerText
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

    private fun updateNotification(newTitle: String, newContent: String) {
        runOnUiThread {
            notificationBuilder
                .setContentTitle(newTitle)
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

                // Calculate real-time slope
                currentSlope = calculateRealTimeSlope(distance, altitude)

                // Calculate average slope
                val avgSlope = calculateAverageSlope()

                // Update CustomLocationListener with all slope statistics
                customLocationListener?.updateSlopeData(currentSlope, avgSlope, maxUphillSlope, maxDownhillSlope)

                val metricTimestampMs = currentTimeMillis()
                updateRecordingClockFromMetric(metricTimestampMs)

                // Always save metrics data with barometer data included
                val metric = Metric(
                    eventId = eventId,
                    heartRate = currentHeartRate,
                    heartRateDevice = heartRateDeviceName ?: "None",
                    speed = speed,
                    distance = distance,
                    cadence = runningCadenceTracker?.currentCadence(),
                    lap = lap,
                    timeInMilliseconds = metricTimestampMs,
                    unity = "metric",
                    elevation = altitude.toFloat(),
                    elevationGain = elevGain,
                    elevationLoss = elevLoss,
                    slope = currentSlope,                      // Real-time slope percentage
                    temperature = currentTemperature?.toFloat(),
                    accuracy = null,

                    // Include barometer data — use QNH-calibrated values from CustomLocationListener
                    // (ForegroundService.currentAltitudeFromPressure comes from BarometerSensorService
                    // which always uses ICAO standard 1013.25 hPa; CustomLocationListener applies the
                    // GPS-derived QNH calibration so its value matches what is sent via WebSocket)
                    pressure = if (currentPressure > 0) currentPressure else null,
                    pressureAccuracy = if (currentPressureAccuracy > 0) currentPressureAccuracy else null,
                    altitudeFromPressure = customLocationListener?.getCalibratedAltitudeFromPressure()
                        ?.takeIf { it != 0f }
                        ?: if (currentAltitudeFromPressure != 0f) currentAltitudeFromPressure else null,
                    seaLevelPressure = customLocationListener?.getCalibratedSeaLevelPressure()
                        ?.takeIf { it > 0f }
                        ?: if (currentSeaLevelPressure > 0) currentSeaLevelPressure else null
                )
                database.metricDao().insertMetric(metric)
                Log.d(TAG, "Metric saved with barometer data: pressure=$currentPressure hPa, barometric altitude=$currentAltitudeFromPressure m")

                // Always save location data regardless of speed
                val location = Location(
                    eventId = eventId,
                    latitude = latitude,
                    longitude = longitude,
                    altitude = altitude,
                    backyardLap = if (isBackyardUltraMode) backyardLapNumber else 0
                )
                database.locationDao().insertLocation(location)
                Log.d("ForegroundService: ", "Location saved: lat=$latitude, lon=$longitude")

                // Geocode start location on first valid coordinates
                if (!hasGeocodedStartLocation && eventId > 0) {
                    hasGeocodedStartLocation = true
                    try {
                        val locationInfo = GeocodingHelper.getLocationInfo(applicationContext, latitude, longitude)
                        if (locationInfo.city != null || locationInfo.country != null || locationInfo.address != null) {
                            database.eventDao().updateEventStartLocation(eventId, locationInfo.city, locationInfo.country, locationInfo.address)
                            // Update CustomLocationListener for WebSocket transfer
                            customLocationListener?.updateStartLocationData(locationInfo.city, locationInfo.country, locationInfo.address)
                            Log.d(TAG, "Start location geocoded: ${locationInfo.address ?: "${locationInfo.city}, ${locationInfo.country}"}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error geocoding start location", e)
                    }
                }

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
            if (isStoppingIntentionally || !isAcceptingLocationUpdates) {
                Log.d(
                    TAG,
                    "DIAG FG onLocationUpdate ignored while stopping/closed " +
                            "isStoppingIntentionally=$isStoppingIntentionally " +
                            "isAcceptingLocationUpdates=$isAcceptingLocationUpdates " +
                            "incomingDistance=${metrics.coveredDistance} " +
                            "incomingLat=${metrics.latitude} incomingLon=${metrics.longitude}"
                )
                return
            }

            Log.d(TAG, "DIAG FG onLocationUpdate ENTER preDistance=$distance incomingMetrics.coveredDistance=${metrics.coveredDistance} incomingLat=${metrics.latitude} incomingLon=${metrics.longitude} cllInstance=${customLocationListener?.let { System.identityHashCode(it) }} thread=${Thread.currentThread().name}")
            // Update timestamp to prevent timeout
            lastUpdateTimestamp = currentTimeMillis()

            // Update values from location update
            latitude = metrics.latitude
            longitude = metrics.longitude
            speed = metrics.speed
            distance = metrics.coveredDistance
            altitude = metrics.altitude
            bearing = metrics.bearing
            satellites = (metrics.satellites ?: 0).toString()
            Log.d(TAG, "DIAG FG onLocationUpdate wrote distance=$distance lat=$latitude lon=$longitude speed=$speed")

            // Keep lastKnownPosition current so connection-monitor listener
            // recreation uses a fresh position instead of a stale startup value.
            if (latitude != -999.0 && longitude != -999.0) {
                lastKnownPosition = Pair(latitude, longitude)
            }

            // Sync lap count from CustomLocationListener (the single source of truth).
            // CLL's calculateLap() processes every GPS increment directly without guards,
            // so it never loses distance. ForegroundService just detects when the lap
            // number increases and saves the corresponding lap time to the database.
            if (!isBackyardUltraMode) {
                syncLapFromMetrics(metrics.lap)
            }

            // Derive partial-lap progress from distance so state saves stay consistent
            lapCounter = distance % 1000.0

            // Collect HR and speed samples during LT measurement phase
            if (isLactateThresholdMode && ltTestStartTime > 0) {
                val elapsed = System.currentTimeMillis() - ltTestStartTime
                if (elapsed >= LT_SETTLE_DURATION_MS && currentHeartRate > 0) {
                    ltHeartRateSamples.add(currentHeartRate)
                    if (speed > 0) ltSpeedSamples.add(speed.toDouble())
                }
            }

            Log.d(TAG, "Location update received: lat=$latitude, lon=$longitude, speed=$speed, satellites=$satellites")

            // Update home screen widget with current metrics
            val recordedDurationMs = getRecordedDurationMs()
            refreshRecordingDurationLabels(recordedDurationMs)
            val totalRecordingDuration = Duration.ofMillis(recordedDurationMs)
            val totalRecordingFormattedTime = String.format(
                "%02d:%02d:%02d",
                totalRecordingDuration.toHours(),
                totalRecordingDuration.toMinutes() % 60,
                totalRecordingDuration.seconds % 60
            )

            GeoTrackerWidget.updateWidget(
                this,
                movementFormattedTime,
                totalRecordingFormattedTime,
                lazyFormattedTime,
                distance,
                speed,
                altitude,
                currentTemperature?.toFloat(),
                currentPressure,
                currentHeartRate,
                true
            )
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

    // Subscribe to barometer data from EventBus
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBarometerData(data: BarometerData) {
        if (data.isAvailable) {
            currentPressure = data.pressure
            currentPressureAccuracy = data.accuracy
            currentAltitudeFromPressure = data.altitudeFromPressure
            currentSeaLevelPressure = data.seaLevelPressure

            Log.d(TAG, "Barometer data updated: ${data.pressure} hPa, altitude: ${data.altitudeFromPressure}m, accuracy: ${data.accuracy}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Check if we're stopping intentionally (flag set by stop button)
            if (intent?.getBooleanExtra("stopping_intentionally", false) == true) {
                isStoppingIntentionally = true
                Log.d(TAG, "Service is being stopped intentionally")
            }

            // Handle pause/resume/stop actions
            val action = intent?.getStringExtra("action")
            when (action) {
                "stop_recording" -> {
                    Log.d(TAG, "Stop recording action received")
                    isStoppingIntentionally = true
                    isAcceptingLocationUpdates = false
                    customLocationListener?.stopLocationCallbacks("stop_recording")
                    runningCadenceTracker?.stop()

                    // Finalize Lactate Threshold test if active
                    if (isLactateThresholdMode) {
                        ltCountdownJob?.cancel()
                        finalizeLtTest()
                    }

                    // Finalize recording synchronously before stopping
                    runBlocking {
                        finalizeRecording()
                    }

                    // Clear session data
                    clearSessionDataFromPreferences()
                    getSharedPreferences(SessionRecoveryManager.PREF_SERVICE_STATE, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(SessionRecoveryManager.PREF_WAS_RUNNING, false)
                        .apply()

                    Log.d(TAG, "Recording stopped intentionally, calling stopSelf()")
                    stopSelf()
                    return START_NOT_STICKY
                }
                "pause_recording" -> {
                    customLocationListener?.pauseTracking()
                    runningCadenceTracker?.pause()
                    isPaused = true
                    pauseStartTime = System.currentTimeMillis()

                    // Stop the current stopwatch segments to freeze the time
                    if (isCurrentlyMoving) {
                        movementState.stop()
                    } else {
                        lazyState.stop()
                    }

                    getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_paused", true)
                        .putLong("pause_start_time", pauseStartTime)
                        .apply()
                    Log.d(TAG, "Recording paused - stopwatches frozen")
                    return START_STICKY
                }
                "resume_recording" -> {
                    customLocationListener?.resumeTracking()
                    startCadenceTrackingIfSupported()

                    // Calculate how long we were paused and add to total
                    if (pauseStartTime > 0) {
                        val pauseDuration = System.currentTimeMillis() - pauseStartTime
                        totalPausedDurationMs += pauseDuration
                        Log.d(TAG, "Pause duration: ${pauseDuration}ms, Total paused: ${totalPausedDurationMs}ms")
                    }

                    isPaused = false
                    pauseStartTime = 0

                    // Resume the appropriate stopwatch segment
                    if (isCurrentlyMoving) {
                        movementState.start()
                    } else {
                        lazyState.start()
                    }

                    getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_paused", false)
                        .remove("pause_start_time")
                        .apply()
                    Log.d(TAG, "Recording resumed - stopwatches restarted")
                    return START_STICKY
                }
                "transition_discipline" -> {
                    val newDiscipline = intent?.getStringExtra("discipline") ?: return START_STICKY
                    val prefs = getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                    val transitionCount = prefs.getInt("discipline_transition_count", 1)
                    val nextTransitionNumber = transitionCount + 1

                    // Insert discipline transition record
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val database = FitnessTrackerDatabase.getInstance(this@ForegroundService)
                            database.disciplineTransitionDao().insertTransition(
                                DisciplineTransition(
                                    eventId = eventId,
                                    sessionId = sessionId,
                                    disciplineName = newDiscipline,
                                    transitionNumber = nextTransitionNumber,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            Log.d(TAG, "Discipline transition #$nextTransitionNumber to $newDiscipline recorded")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error recording discipline transition", e)
                        }
                    }

                    // Update SharedPreferences with current discipline and counter
                    prefs.edit()
                        .putString("current_discipline", newDiscipline)
                        .putInt("discipline_transition_count", nextTransitionNumber)
                        .apply()

                    startCadenceTrackingIfSupported(newDiscipline)

                    // Send discipline transition to WebSocket server via EventBus
                    EventBus.getDefault().post(
                        WebSocketMessage.DisciplineTransitionMessage(
                            sessionId = sessionId,
                            eventName = eventname,
                            transition = DisciplineTransitionData(
                                disciplineName = newDiscipline,
                                transitionNumber = nextTransitionNumber,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    )

                    Log.d(TAG, "Discipline transitioned to $newDiscipline (#$nextTransitionNumber)")
                    return START_STICKY
                }
                "backyard_start_lap" -> {
                    // Resume GPS tracking
                    customLocationListener?.resumeTracking()
                    startCadenceTrackingIfSupported()
                    isPaused = false

                    // Increment lap counter
                    backyardLapNumber++
                    backyardLapStartTime = System.currentTimeMillis()

                    // Calculate paused duration (rest time)
                    if (pauseStartTime > 0) {
                        val pauseDuration = System.currentTimeMillis() - pauseStartTime
                        totalPausedDurationMs += pauseDuration
                        Log.d(TAG, "Backyard rest duration: ${pauseDuration}ms")
                    }
                    pauseStartTime = 0

                    // Resume stopwatches
                    if (isCurrentlyMoving) movementState.start() else lazyState.start()

                    // Update SharedPreferences
                    getSharedPreferences("RecordingState", MODE_PRIVATE).edit()
                        .putBoolean("is_paused", false)
                        .putInt("backyard_lap_number", backyardLapNumber)
                        .putLong("backyard_lap_start_time", backyardLapStartTime)
                        .putBoolean("backyard_lap_active", true)
                        .apply()

                    Log.d(TAG, "Backyard Ultra lap $backyardLapNumber started")
                    return START_STICKY
                }
                "backyard_complete_lap" -> {
                    val currentTime = System.currentTimeMillis()

                    // Save LapTime record with fixed distance
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val lapTime = LapTime(
                                sessionId = sessionId,
                                eventId = eventId,
                                lapNumber = backyardLapNumber,
                                startTime = backyardLapStartTime,
                                endTime = currentTime,
                                distance = 6.7606
                            )
                            database.lapTimeDao().insertLapTime(lapTime)
                            Log.d(TAG, "Backyard Ultra lap $backyardLapNumber saved: ${(currentTime - backyardLapStartTime) / 1000}s")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving backyard lap time", e)
                        }
                    }

                    // Pause GPS tracking
                    customLocationListener?.pauseTracking()
                    runningCadenceTracker?.pause()
                    isPaused = true
                    pauseStartTime = currentTime

                    // Stop stopwatches
                    if (isCurrentlyMoving) movementState.stop() else lazyState.stop()

                    // Update SharedPreferences
                    getSharedPreferences("RecordingState", MODE_PRIVATE).edit()
                        .putBoolean("is_paused", true)
                        .putLong("pause_start_time", currentTime)
                        .putBoolean("backyard_lap_active", false)
                        .putInt("backyard_completed_laps", backyardLapNumber)
                        .apply()

                    // Notify WeatherEventBusHandler to refresh lap times
                    try {
                        WeatherEventBusHandler.getInstance().refreshLapTimes()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not refresh lap times", e)
                    }

                    Log.d(TAG, "Backyard Ultra lap $backyardLapNumber completed")
                    return START_STICKY
                }
            }

            // Guard against duplicate initialization: if the service is already running
            // (e.g., AlarmManager restart succeeded, then CrashRecoveryActivity also sends
            // a start intent), skip re-initialization to avoid duplicate coroutines,
            // overwriting customLocationListener, and potential new-event creation.
            if (isServiceStarted) {
                Log.w(TAG, "Service already initialized, skipping duplicate onStartCommand")
                return START_STICKY
            }

            // Check if this is a restored session
            isRestoredSession = intent?.getBooleanExtra("is_restored_session", false) ?: false

            // Check if we're restarting after crash
            val prefs = getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
            val wasRunning = prefs.getBoolean("was_running", false)

            // Detect stale recovery: if wasRunning is true but the intent has explicit start extras
            // (eventName), this is a genuinely new recording — the stale was_running flag was left
            // over from a previous session (race condition in onDestroy). In that case, skip recovery
            // and treat this as a fresh start.
            val isGenuineNewStart = !isRestoredSession && intent?.hasExtra("eventName") == true
            val shouldRestore = (wasRunning || isRestoredSession) && !isGenuineNewStart

            if (isGenuineNewStart && wasRunning) {
                Log.w(TAG, "Stale was_running=true detected for new recording start — clearing recovery state")
                prefs.edit()
                    .putBoolean("was_running", false)
                    .putBoolean("is_paused", false)
                    .putLong("pause_start_time_saved", 0)
                    .putLong("total_paused_duration_ms", 0)
                    .apply()
            }

            if (shouldRestore) {
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
                recordingStartTimestampMs = prefs.getLong("recording_start_time_ms", 0)
                latestRecordingTimestampMs = prefs.getLong("latest_recording_time_ms", 0)
                val storedStartTime = prefs.getLong("service_start_time", 0)
                if (recordingStartTimestampMs > 0) {
                    startDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(recordingStartTimestampMs),
                        ZoneId.systemDefault()
                    )
                } else if (storedStartTime > 0) {
                    startDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(storedStartTime),
                        ZoneOffset.UTC
                    )
                }

                // Restore pause state
                isPaused = prefs.getBoolean("is_paused", false)
                totalPausedDurationMs = prefs.getLong("total_paused_duration_ms", 0)
                pauseStartTime = prefs.getLong("pause_start_time_saved", 0)

                Log.d(TAG, "Restored service state: eventId=$eventId, sessionId=$sessionId, " +
                        "distance=$distance, lap=$lap, startTime=$startDateTime, isPaused=$isPaused, " +
                        "totalPausedDuration=${totalPausedDurationMs}ms")

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

                // Keep was_running=true so that a quick second crash still triggers
                // recovery. The flag will be cleared properly by onDestroy when
                // isStoppingIntentionally=true. The isServiceStarted guard above
                // prevents the CrashRecoveryActivity from re-initialising an
                // already-running service.

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

                recordingStartTimestampMs = 0L
                latestRecordingTimestampMs = 0L

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

            if (!isPaused) {
                startCadenceTrackingIfSupported()
            }

            // Initialize Wings for Life Run mode (virtual catcher car)
            isWingsForLifeMode = artofsport.equals("Wings for Life Run", ignoreCase = true)
            if (isWingsForLifeMode) {
                val recordingPrefs = getSharedPreferences("RecordingState", MODE_PRIVATE)
                wflWasCaught = recordingPrefs.getBoolean("wfl_was_caught", false)
                wflCaughtAtDistanceMeters = recordingPrefs.getFloat("wfl_caught_at_distance_m", 0f).toDouble()
                wflCaughtAtElapsedMs = recordingPrefs.getLong("wfl_caught_at_elapsed_ms", 0L)
                wflLastAnnouncedHeadwayKm = -1
                initWingsForLifeTts()
                Log.d(TAG, "Wings for Life Run mode initialized: wasCaught=$wflWasCaught, " +
                        "caughtAt=${wflCaughtAtDistanceMeters}m / ${wflCaughtAtElapsedMs}ms")
            }

            // Initialize Backyard Ultra mode
            isBackyardUltraMode = artofsport.equals("Backyard Ultra", ignoreCase = true)
            if (isBackyardUltraMode) {
                val recordingPrefs = getSharedPreferences("RecordingState", MODE_PRIVATE)
                backyardLapNumber = recordingPrefs.getInt("backyard_lap_number", 1)
                backyardLapStartTime = recordingPrefs.getLong("backyard_lap_start_time", System.currentTimeMillis())
                Log.d(TAG, "Backyard Ultra mode initialized: lap=$backyardLapNumber")
            }

            // Initialize Lactate Threshold mode
            isLactateThresholdMode = artofsport.equals("Lactate Threshold (30min TT)", ignoreCase = true)
            if (isLactateThresholdMode) {
                val recordingPrefs = getSharedPreferences("RecordingState", MODE_PRIVATE)
                ltTestStartTime = recordingPrefs.getLong("lt_test_start_time", System.currentTimeMillis())
                ltMeasurementPhaseStartTime = ltTestStartTime + LT_SETTLE_DURATION_MS
                // Clear any previous samples
                ltHeartRateSamples.clear()
                ltSpeedSamples.clear()
                // Persist start time
                recordingPrefs.edit()
                    .putLong("lt_test_start_time", ltTestStartTime)
                    .apply()
                startLtCountdownTimer()
                Log.d(TAG, "Lactate Threshold mode initialized: startTime=$ltTestStartTime")
            }

            Log.d(TAG, "Starting service with event: $eventname, sport: $artofsport, comment: $comment, clothing: $clothing, websocketTransfer: $enableWebSocketTransfer")

            // Debug log to verify session data is saved
            logSessionDataForDebugging()

            val eventIdDeferred = CompletableDeferred<Int>()

            // Only create a new event if this is not a restored session with a valid eventId.
            // Use isInitialStateRestored (covers wasRunning path too) — not just isRestoredSession —
            // so that system auto-restarts (START_STICKY with null intent) also reuse the event.
            if ((isRestoredSession || isInitialStateRestored) && eventId > 0) {
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
            refreshForegroundServiceTypes(includeCadence = runningCadenceTracker?.isListening == true)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called, isStoppingIntentionally: $isStoppingIntentionally")

        // CRITICAL: Set isServiceStarted to false FIRST, before cancelling jobs.
        // This prevents the periodic state saver from writing was_running=true
        // after we clear it below (race condition that caused stale recovery on next start).
        isServiceStarted = false
        isAcceptingLocationUpdates = false

        // Cancel all background jobs IMMEDIATELY to prevent them from overwriting
        // SharedPreferences state (was_running, is_paused) after we clean up below.
        try {
            currentStateJob?.cancel()
            currentStateJob = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling state saving job", e)
        }

        try {
            stateConsistencyCheckerJob?.cancel()
            stateConsistencyCheckerJob = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling state consistency checker", e)
        }

        try {
            connectionMonitorJob?.cancel()
            connectionMonitorJob = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling connection monitor", e)
        }

        try {
            serviceJob.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling service job", e)
        }

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

        // Stop barometer sensor
        try {
            barometerSensorService?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping barometer sensor", e)
        }

        // Stop running cadence sensor
        try {
            runningCadenceTracker?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping running cadence sensor", e)
        }

        if (isStoppingIntentionally) {
            try {
                // Clear session data when stopping intentionally
                clearSessionDataFromPreferences()

                // Clear the recovery state — safe now because all background jobs are cancelled
                getSharedPreferences(SessionRecoveryManager.PREF_SERVICE_STATE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(SessionRecoveryManager.PREF_WAS_RUNNING, false)
                    .putBoolean("is_paused", false)
                    .putLong("pause_start_time_saved", 0)
                    .putLong("total_paused_duration_ms", 0)
                    .apply()

                // Clear Wings for Life Run catch state so the next session starts fresh
                getSharedPreferences("RecordingState", Context.MODE_PRIVATE).edit()
                    .remove("wfl_was_caught")
                    .remove("wfl_caught_at_distance_m")
                    .remove("wfl_caught_at_elapsed_ms")
                    .apply()

                Log.d(TAG, "Cleared recovery state and session data in SharedPreferences")

                // When stopping intentionally, finalize the recording by transferring data
                // Use runBlocking to ensure geocoding completes before service cleanup
                runBlocking {
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

        // Reset widget - restart following service if a followed runner is configured, else reset to not tracking
        try {
            val widgetPrefs = getSharedPreferences(GeoTrackerWidget.WIDGET_CONFIG_PREFS, MODE_PRIVATE)
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(this, GeoTrackerWidget::class.java)
            )
            val hasFollowingConfig = widgetIds.any { id ->
                widgetPrefs.getString("widget_${id}_session_id", null) != null
            }
            if (hasFollowingConfig) {
                Log.d(TAG, "Recording stopped, restarting WidgetFollowingService")
                startForegroundService(Intent(this, WidgetFollowingService::class.java))
            } else {
                GeoTrackerWidget.updateWidgetNotTracking(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting widget", e)
        }

        try {
            customLocationListener?.cleanup()
            customLocationListener = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up location listener", e)
        }

        try {
            wflTts?.stop()
            wflTts?.shutdown()
            wflTts = null
            isWflTtsInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down WFL TTS", e)
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
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground service", e)
        }
    }

    private suspend fun finalizeRecording() {
        if (hasFinalized) {
            Log.d(TAG, "Recording already finalized, skipping duplicate call")
            return
        }
        hasFinalized = true

        withContext(Dispatchers.IO) {
            try {
                // 1. Get all records from current_recording for this session
                val records = database.currentRecordingDao().getAllRecordsForSession(sessionId)
                Log.d(TAG, "Finalizing recording, found ${records.size} records")

                // 2. Get all lap times for this session
                val lapTimes = database.lapTimeDao().getLapTimesForSession(sessionId)
                Log.d(TAG, "Found ${lapTimes.size} lap records")

                // 3. Geocode end location using current coordinates
                if (eventId > 0 && latitude != -999.0 && longitude != -999.0) {
                    try {
                        val locationInfo = GeocodingHelper.getLocationInfo(applicationContext, latitude, longitude)
                        if (locationInfo.city != null || locationInfo.country != null || locationInfo.address != null) {
                            database.eventDao().updateEventEndLocation(eventId, locationInfo.city, locationInfo.country, locationInfo.address)
                            // Update CustomLocationListener for WebSocket transfer
                            customLocationListener?.updateEndLocationData(locationInfo.city, locationInfo.country, locationInfo.address)
                            Log.d(TAG, "End location geocoded: ${locationInfo.address ?: "${locationInfo.city}, ${locationInfo.country}"}")

                            // Send final metrics with end location to WebSocket server
                            customLocationListener?.sendFinalMetrics()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error geocoding end location", e)
                    }
                }

                // 4. We don't need to duplicate data that's already in the permanent tables
                // because we've been saving to both throughout the session.
                // We just need to ensure the lap times are properly stored

                // 5. Clear temporary tables
                if (records.isNotEmpty()) {
                    database.currentRecordingDao().clearSessionRecords(sessionId)
                    Log.d(TAG, "Cleared temporary recording records")
                }

                // 6. If event was live-streamed via WebSocket, mark it as uploaded
                if (enableWebSocketTransfer && eventId > 0 && sessionId.isNotEmpty()) {
                    database.eventDao().updateEventUploadStatus(
                        eventId,
                        sessionId,
                        true,
                        System.currentTimeMillis()
                    )
                    Log.d(TAG, "Event marked as uploaded (live WebSocket session: $sessionId)")
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
                .putLong("recording_start_time_ms", recordingStartTimestampMs)
                .putLong("latest_recording_time_ms", latestRecordingTimestampMs)
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
                // Save pause state
                .putBoolean("is_paused", isPaused)
                .putLong("total_paused_duration_ms", totalPausedDurationMs)
                .putLong("pause_start_time_saved", pauseStartTime)
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

    private fun isBackgroundLocationServiceRunning(): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val target = "at.co.netconsulting.geotracker.service.BackgroundLocationService"
            @Suppress("DEPRECATION")
            manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className == target }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query running services", e)
            false
        }
    }

    private suspend fun waitForBackgroundLocationServiceStopped(timeoutMs: Long) {
        val startMs = currentTimeMillis()
        val deadline = startMs + timeoutMs
        var polls = 0
        Log.d(TAG, "DIAG waitForBackgroundLocationServiceStopped ENTER timeout=${timeoutMs}ms initialCheck=${isBackgroundLocationServiceRunning()}")
        while (isBackgroundLocationServiceRunning() && currentTimeMillis() < deadline) {
            polls++
            delay(50)
        }
        val stillRunning = isBackgroundLocationServiceRunning()
        val elapsed = currentTimeMillis() - startMs
        Log.d(TAG, "DIAG waitForBackgroundLocationServiceStopped EXIT elapsed=${elapsed}ms polls=$polls stillRunning=$stillRunning")
        if (stillRunning) {
            Log.w(TAG, "BackgroundLocationService still running after ${timeoutMs}ms — proceeding anyway")
        }
    }

    private fun startConnectionMonitoring() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = serviceScope.launch {
            var iter = 0L
            Log.d(TAG, "DIAG FG connectionMonitor STARTED")
            while (isActive) {
                try {
                    iter++
                    delay(CONNECTION_CHECK_INTERVAL)

                    // Check if location listener exists and has a valid session ID
                    val isSessionValid = customLocationListener?.hasValidSession() ?: false
                    val isLocationTracking = customLocationListener != null
                    val lastLocationAgeMs = customLocationListener?.getMillisSinceLastLocationUpdate()
                    val isLocationStale = isLocationTracking &&
                            !isPaused &&
                            lastLocationAgeMs != null &&
                            lastLocationAgeMs > LOCATION_STALL_TIMEOUT_MS

                    Log.d(TAG, "DIAG FG connectionMonitor tick #$iter isSessionValid=$isSessionValid isLocationTracking=$isLocationTracking cllInstance=${customLocationListener?.let { System.identityHashCode(it) }} distance=$distance lastKnownPosition=$lastKnownPosition lastLocationAgeMs=$lastLocationAgeMs isLocationStale=$isLocationStale")
                    Log.d(TAG, "Connection status check: session valid=$isSessionValid, location tracking=$isLocationTracking")

                    if (!isLocationTracking || !isSessionValid) {
                        Log.w(TAG, "DIAG ConnectionMonitor RECREATING CLL — isLocationTracking=$isLocationTracking isSessionValid=$isSessionValid distance=$distance lastKnownPosition=$lastKnownPosition lap=$lap lapCounter=$lapCounter")
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
                            } else {
                                Log.w(TAG, "DIAG ConnectionMonitor SKIPPED resumeFromSavedState because distance=$distance — fresh CLL starts at coveredDistance=0")
                            }
                        }
                    } else if (isLocationStale) {
                        val staleMs = lastLocationAgeMs ?: -1L
                        Log.w(
                            TAG,
                            "DIAG ConnectionMonitor RESTARTING location updates only - " +
                                    "no CLL callback for ${staleMs}ms, distance=$distance, " +
                                    "lastKnownPosition=$lastKnownPosition"
                        )
                        customLocationListener?.restartLocationUpdates(
                            "ForegroundService monitor detected stale GPS callbacks after ${staleMs}ms"
                        )
                    }

                    // Also check heart rate sensor connection if needed
                    if (!heartRateDeviceAddress.isNullOrEmpty() && heartRateSensorService?.isConnected() != true) {
                        Log.d(TAG, "Reconnecting to heart rate sensor: $heartRateDeviceName")
                        heartRateSensorService?.connectToDevice(heartRateDeviceAddress!!)
                    }

                    // Check barometer sensor connection if needed
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

    /**
     * Transmit current lap times to WebSocket server
     */
    private suspend fun transmitLapTimesToWebSocket() {
        try {
            val database = FitnessTrackerDatabase.getInstance(applicationContext)
            val lapTimes = database.lapTimeDao().getLapTimesBySession(sessionId)
            
            if (lapTimes.isNotEmpty()) {
                val lapTimeData = lapTimes.map { lapTime: LapTime ->
                    LapTimeData(
                        lapNumber = lapTime.lapNumber,
                        startTime = lapTime.startTime,
                        endTime = lapTime.endTime,
                        distance = lapTime.distance
                    )
                }
                
                // Create metrics object with lap times for transmission
                val metricsWithLapTimes = Metrics(
                    latitude = latitude,
                    longitude = longitude,
                    speed = speed,
                    altitude = altitude,
                    bearing = bearing,
                    slope = currentSlope,                      // Real-time slope percentage
                    coveredDistance = distance,
                    lap = lap,
                    startDateTime = startDateTime,
                    currentDateTime = LocalDateTime.now(),
                    averageSpeed = 0.0, // Will be calculated by CustomLocationListener
                    maxSpeed = 0.0, // Will be calculated by CustomLocationListener
                    cumulativeElevationGain = 0.0, // Will be calculated by CustomLocationListener
                    sessionId = sessionId,
                    firstname = firstname,
                    lastname = lastname,
                    birthdate = birthdate,
                    height = height,
                    weight = weight,
                    bmi = bmi,
                    eventName = eventname,
                    sportType = artofsport,
                    comment = comment,
                    clothing = clothing,
                    movingAverageSpeed = 0.0, // Will be calculated by CustomLocationListener
                    lapTimes = lapTimeData
                )
                
                // Send to CustomLocationListener for WebSocket transmission
                customLocationListener?.transmitLapTimes(metricsWithLapTimes)
                Log.d(TAG, "Transmitted ${lapTimeData.size} lap times to WebSocket")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitting lap times to WebSocket: ${e.message}")
            throw e
        }
    }

    /**
     * Calculate real-time slope based on distance and elevation changes
     */
    private fun calculateRealTimeSlope(currentDistance: Double, currentAltitude: Double): Double {
        if (previousDistance == 0.0) {
            // First measurement - initialize
            previousDistance = currentDistance
            previousAltitude = currentAltitude
            return 0.0
        }

        val distanceDiff = currentDistance - previousDistance
        val elevationDiff = currentAltitude - previousAltitude

        // Only calculate slope if there's meaningful distance covered (> 5 meters)
        if (distanceDiff > 5.0) {
            // Slope = (elevation change / distance change) * 100 to get percentage
            val slope = (elevationDiff / distanceDiff) * 100.0

            // Filter out extreme values that are likely GPS errors
            if (slope >= -50.0 && slope <= 50.0) {
                // Add to history for smoothing
                slopeHistory.add(slope)
                if (slopeHistory.size > maxSlopeHistorySize) {
                    slopeHistory.removeAt(0)
                }

                // Calculate smoothed slope using moving average
                val smoothedSlope = slopeHistory.average()

                // Track slope statistics
                allSlopeMeasurements.add(smoothedSlope)

                // Update max uphill slope (positive slopes)
                if (smoothedSlope > 0 && smoothedSlope > maxUphillSlope) {
                    maxUphillSlope = smoothedSlope
                }

                // Update max downhill slope (negative slopes, store as positive value)
                if (smoothedSlope < 0 && kotlin.math.abs(smoothedSlope) > maxDownhillSlope) {
                    maxDownhillSlope = kotlin.math.abs(smoothedSlope)
                }

                // Update previous values for next calculation
                previousDistance = currentDistance
                previousAltitude = currentAltitude

                Log.d(TAG, "Slope calculated: ${String.format("%.2f", smoothedSlope)}% (raw: ${String.format("%.2f", slope)}%, distance: ${String.format("%.1f", distanceDiff)}m, elevation: ${String.format("%.1f", elevationDiff)}m)")

                return smoothedSlope
            } else {
                Log.w(TAG, "Extreme slope value filtered out: ${String.format("%.1f", slope)}%")
            }
        }

        // Return current slope if no meaningful distance change
        return currentSlope
    }

    /**
     * Calculate average slope over the entire route
     */
    private fun calculateAverageSlope(): Double {
        return if (allSlopeMeasurements.isNotEmpty()) {
            allSlopeMeasurements.average()
        } else {
            0.0
        }
    }

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val MIN_SPEED_THRESHOLD = 2.5f
        private const val EVENT_TIMEOUT_MS = 2000
        private var isStoppingIntentionally = false
        private var hasFinalized = false
        private const val TAG = "ForegroundService"

        // State saving constants
        private const val STATE_SAVE_INTERVAL = 5000L // 5 seconds

        // Weather
        private const val WEATHER_UPDATE_INTERVAL = 1800000L // 30 minutes in milliseconds
        private const val ERROR_RETRY_INTERVAL = 300000L // 5 minutes in milliseconds
        private const val WEATHER_FAST_POLL_INTERVAL = 10000L // 10 seconds for initial polling

        // reconnection logic
        private const val CONNECTION_CHECK_INTERVAL = 15_000L
        private const val LOCATION_STALL_TIMEOUT_MS = 30_000L

        // restoring logic
        private const val STATE_CONSISTENCY_CHECK_INTERVAL = 30_000L // 30 seconds
    }
}
