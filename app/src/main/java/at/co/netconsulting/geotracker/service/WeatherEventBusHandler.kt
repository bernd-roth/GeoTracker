package at.co.netconsulting.geotracker.service

import android.content.Context
import android.util.Log
import at.co.netconsulting.geotracker.data.HeartRateData
import at.co.netconsulting.geotracker.data.CurrentWeather
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.domain.LapTime
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.abs

/**
 * Singleton handler for weather and metrics events from EventBus
 * Fixes issue with fluctuating speed values in StatisticsScreen
 * Now includes database-driven lap time tracking
 */
class WeatherEventBusHandler private constructor(private val context: Context) {

    // StateFlow for weather data
    private val _weather = MutableStateFlow<CurrentWeather?>(null)
    val weather: StateFlow<CurrentWeather?> = _weather.asStateFlow()

    // StateFlow for metrics data with speed smoothing
    private val _metrics = MutableStateFlow<Metrics?>(null)
    val metrics: StateFlow<Metrics?> = _metrics.asStateFlow()

    // StateFlow for heart rate data
    private val _heartRate = MutableStateFlow<HeartRateData?>(null)
    val heartRate: StateFlow<HeartRateData?> = _heartRate.asStateFlow()

    // Heart rate history tracking for chart
    private val _heartRateHistory = MutableStateFlow<List<Pair<Double, Int>>>(emptyList())
    val heartRateHistory: StateFlow<List<Pair<Double, Int>>> = _heartRateHistory.asStateFlow()

    // StateFlow for lap times from database
    private val _lapTimes = MutableStateFlow<List<LapTime>>(emptyList())
    val lapTimes: StateFlow<List<LapTime>> = _lapTimes.asStateFlow()

    // Speed smoothing values
    private val speedBuffer = mutableListOf<Float>()
    private val speedBufferSize = 5
    private var lastMetricsTimestamp = 0L

    // Last metrics value for comparison
    private var lastMetricsValue: Metrics? = null

    // Database and coroutine scope for lap time queries
    private val database: FitnessTrackerDatabase by lazy {
        FitnessTrackerDatabase.getInstance(context)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Current session tracking
    private var currentSessionId = ""

    // Lap tracking variables (keeping for backward compatibility)
    private var lastLapDistance = 0.0
    private var lapStartTime = 0L
    private var sessionStartTime = 0L
    private var isTrackingLaps = false

    companion object {
        private const val TAG = "WeatherEventBusHandler"
        private const val LAP_DISTANCE_KM = 1.0 // 1 km per lap
        private const val LAP_DISTANCE_METERS = LAP_DISTANCE_KM * 1000

        @Volatile
        private var INSTANCE: WeatherEventBusHandler? = null

        fun getInstance(context: Context): WeatherEventBusHandler {
            return INSTANCE ?: synchronized(this) {
                val instance = WeatherEventBusHandler(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // For backward compatibility - use application context
        fun getInstance(): WeatherEventBusHandler {
            return INSTANCE ?: throw IllegalStateException(
                "WeatherEventBusHandler must be initialized with context first"
            )
        }
    }

    init {
        // Register with EventBus if not already registered
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
            Log.d(TAG, "WeatherEventBusHandler initialized and registered")
        }
    }

    /**
     * Receive weather updates from EventBus
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWeatherUpdate(weather: CurrentWeather) {
        _weather.value = weather
        Log.d(TAG, "Weather update received: temp=${weather.temperature}")
    }

    /**
     * Receive metrics updates from EventBus with speed smoothing
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMetricsUpdate(metrics: Metrics) {
        val currentTime = System.currentTimeMillis()

        // Check if session changed and update lap times accordingly
        if (currentSessionId != metrics.sessionId && metrics.sessionId.isNotEmpty()) {
            currentSessionId = metrics.sessionId
            loadLapTimesFromDatabase()
            Log.d(TAG, "Session changed to: $currentSessionId, loading lap times")
        }

        // Initialize lap tracking for new session (backward compatibility)
        if (currentSessionId != metrics.sessionId && metrics.sessionId.isNotEmpty()) {
            startNewSession(metrics.sessionId, currentTime)
        }

        // Check for too frequent updates (less than 250ms apart)
        if (lastMetricsTimestamp > 0 && currentTime - lastMetricsTimestamp < 250) {
            // Skip this update to prevent UI jitter
            Log.d(TAG, "Skipping too frequent metrics update")
            return
        }

        // Check for abnormal jumps in speed
        val hasAbnormalSpeedJump = lastMetricsValue?.let { last ->
            val speedDiff = abs(metrics.speed - last.speed)
            // If speed changed by more than 5 km/h in less than 0.5 seconds, consider it abnormal
            speedDiff > 5.0f && currentTime - lastMetricsTimestamp < 500
        } ?: false

        if (hasAbnormalSpeedJump) {
            Log.d(TAG, "Detected abnormal speed jump, applying smoothing")
            // Create a smoothed version of the metrics
            val smoothedMetrics = metrics.copy(
                speed = calculateSmoothedSpeed(metrics.speed)
            )
            _metrics.value = smoothedMetrics
        } else {
            // Update the speed buffer
            updateSpeedBuffer(metrics.speed)

            // Use the original metrics
            _metrics.value = metrics
        }

        // Update heart rate history if we have data
        if (metrics.heartRate > 0 && metrics.coveredDistance > 0) {
            val distanceKm = metrics.coveredDistance / 1000.0
            val currentHistory = _heartRateHistory.value.toMutableList()

            // Add new data point
            currentHistory.add(Pair(distanceKm, metrics.heartRate))

            // Keep only the last 100 points to prevent memory issues
            if (currentHistory.size > 100) {
                val trimmedHistory = currentHistory.takeLast(100)
                _heartRateHistory.value = trimmedHistory
            } else {
                _heartRateHistory.value = currentHistory
            }
        }

        // Check if we should refresh lap times (when lap number increases)
        lastMetricsValue?.let { lastMetrics ->
            if (metrics.lap > lastMetrics.lap) {
                // New lap completed, refresh from database
                loadLapTimesFromDatabase()
                Log.d(TAG, "New lap detected (${metrics.lap}), refreshing lap times from database")
            }
        }

        // Update tracking values
        lastMetricsTimestamp = currentTime
        lastMetricsValue = metrics
    }

    /**
     * Receive heart rate updates from EventBus
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onHeartRateUpdate(heartRateData: HeartRateData) {
        _heartRate.value = heartRateData
        Log.d(TAG, "Heart rate update received: ${heartRateData.heartRate}")
    }

    /**
     * Load lap times from database for current session
     */
    private fun loadLapTimesFromDatabase() {
        if (currentSessionId.isEmpty()) return

        scope.launch {
            try {
                val lapTimes = database.lapTimeDao().getLapTimesForSession(currentSessionId)
                _lapTimes.value = lapTimes.sortedBy { it.lapNumber }
                Log.d(TAG, "Loaded ${lapTimes.size} lap times from database for session $currentSessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading lap times from database", e)
            }
        }
    }

    /**
     * Start a new session and initialize lap tracking (backward compatibility)
     */
    private fun startNewSession(sessionId: String, currentTime: Long) {
        currentSessionId = sessionId
        sessionStartTime = currentTime
        lapStartTime = currentTime
        lastLapDistance = 0.0
        isTrackingLaps = true

        // Clear existing lap times and load from database
        _lapTimes.value = emptyList()
        loadLapTimesFromDatabase()

        Log.d(TAG, "Started new session: $sessionId")
    }

    /**
     * Update the speed buffer for smoothing calculations
     */
    private fun updateSpeedBuffer(speed: Float) {
        speedBuffer.add(speed)
        if (speedBuffer.size > speedBufferSize) {
            speedBuffer.removeAt(0)
        }
    }

    /**
     * Calculate a smoothed speed value based on recent values
     */
    private fun calculateSmoothedSpeed(currentSpeed: Float): Float {
        // Update buffer with current speed
        updateSpeedBuffer(currentSpeed)

        // Calculate average from buffer
        return if (speedBuffer.isNotEmpty()) {
            speedBuffer.sum() / speedBuffer.size
        } else {
            currentSpeed
        }
    }

    // Public methods for lap time management

    /**
     * Manually add a completed lap time (backward compatibility)
     */
    fun addCompletedLap(lapTime: LapTime) {
        scope.launch {
            try {
                database.lapTimeDao().insertLapTime(lapTime)
                loadLapTimesFromDatabase() // Refresh from database
                Log.d(TAG, "Manually added lap: ${lapTime.lapNumber}")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding lap time to database", e)
            }
        }
    }

    /**
     * Manually refresh lap times from database
     * Can be called when you know new lap data has been saved
     */
    fun refreshLapTimes() {
        loadLapTimesFromDatabase()
    }

    /**
     * Clear lap times (for new sessions)
     */
    fun clearLapTimes() {
        _lapTimes.value = emptyList()
        currentSessionId = ""
        Log.d(TAG, "Cleared all lap times")
    }

    /**
     * Get current lap times list
     */
    fun getCurrentLapTimes(): List<LapTime> {
        return _lapTimes.value
    }

    /**
     * Start lap tracking manually (backward compatibility)
     */
    fun startLapTracking(sessionId: String) {
        val currentTime = System.currentTimeMillis()
        startNewSession(sessionId, currentTime)
    }

    /**
     * Stop lap tracking (backward compatibility)
     */
    fun stopLapTracking() {
        isTrackingLaps = false
        Log.d(TAG, "Stopped lap tracking")
    }

    /**
     * Get the current lap number based on distance
     */
    fun getCurrentLap(distance: Double): Int {
        return ((distance / LAP_DISTANCE_METERS).toInt()) + 1
    }

    /**
     * Get progress in current lap (0.0 to 1.0)
     */
    fun getCurrentLapProgress(distance: Double): Double {
        val currentLapDistance = distance % LAP_DISTANCE_METERS
        return currentLapDistance / LAP_DISTANCE_METERS
    }

    /**
     * Initialize with session ID to start tracking lap times
     */
    fun initializeWithSession(sessionId: String) {
        if (sessionId != currentSessionId) {
            currentSessionId = sessionId
            loadLapTimesFromDatabase()
            Log.d(TAG, "Initialized with session: $sessionId")
        }
    }

    /**
     * Check if currently tracking laps (backward compatibility)
     */
    fun isTrackingLaps(): Boolean {
        return isTrackingLaps
    }

    /**
     * Get current session ID
     */
    fun getCurrentSessionId(): String {
        return currentSessionId
    }

    /**
     * Get lap start time (backward compatibility)
     */
    fun getLapStartTime(): Long {
        return lapStartTime
    }

    /**
     * Set lap start time (backward compatibility)
     */
    fun setLapStartTime(time: Long) {
        lapStartTime = time
    }
}