package at.co.netconsulting.geotracker.service

import android.content.Context
import android.util.Log
import at.co.netconsulting.geotracker.data.HeartRateData
import at.co.netconsulting.geotracker.data.CurrentWeather
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.LapTime
import at.co.netconsulting.geotracker.domain.Metric
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
import java.time.Instant
import java.time.ZoneId

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

    // Speed history tracking for chart (distance in km, speed in km/h)
    private val _speedHistory = MutableStateFlow<List<Pair<Double, Float>>>(emptyList())
    val speedHistory: StateFlow<List<Pair<Double, Float>>> = _speedHistory.asStateFlow()

    // Altitude history tracking for chart (distance in km, altitude in m)
    private val _altitudeHistory = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val altitudeHistory: StateFlow<List<Pair<Double, Double>>> = _altitudeHistory.asStateFlow()

    // Barometric pressure history tracking for chart (distance in km, pressure in hPa)
    private val _pressureHistory = MutableStateFlow<List<Pair<Double, Float>>>(emptyList())
    val pressureHistory: StateFlow<List<Pair<Double, Float>>> = _pressureHistory.asStateFlow()

    // Barometric altitude history tracking for chart (distance in km, altitude in m)
    private val _barometerAltitudeHistory = MutableStateFlow<List<Pair<Double, Float>>>(emptyList())
    val barometerAltitudeHistory: StateFlow<List<Pair<Double, Float>>> = _barometerAltitudeHistory.asStateFlow()

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
    private var currentEventId = -1

    // Lap tracking variables (keeping for backward compatibility)
    private var lastLapDistance = 0.0
    private var lapStartTime = 0L
    private var sessionStartTime = 0L
    private var isTrackingLaps = false

    companion object {
        private const val TAG = "WeatherEventBusHandler"
        private const val LAP_DISTANCE_KM = 1.0 // 1 km per lap
        private const val LAP_DISTANCE_METERS = LAP_DISTANCE_KM * 1000
        private const val CHART_DISTANCE_STEP_KM = 0.01 // Keep chart points every 10 meters.

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

        if (currentSessionId != metrics.sessionId && metrics.sessionId.isNotEmpty()) {
            startNewSession(metrics.sessionId, currentTime)
            Log.d(TAG, "Session changed to: $currentSessionId, loading stored statistics")
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

        // Override distance for Backyard Ultra mode
        val isBackyardUltra = metrics.sportType.equals("Backyard Ultra", ignoreCase = true)
        val adjustedMetrics = if (isBackyardUltra) {
            val prefs = context?.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
            val lapNumber = prefs?.getInt("backyard_lap_number", 0) ?: 0
            val lapActive = prefs?.getBoolean("backyard_lap_active", false) ?: false
            val completedLaps = if (lapActive) (lapNumber - 1).coerceAtLeast(0) else lapNumber
            val backyardDistance = completedLaps * 6706.06 // meters
            metrics.copy(coveredDistance = backyardDistance, lap = lapNumber)
        } else {
            metrics
        }

        if (hasAbnormalSpeedJump) {
            Log.d(TAG, "Detected abnormal speed jump, applying smoothing")
            // Create a smoothed version of the metrics
            val smoothedMetrics = adjustedMetrics.copy(
                speed = calculateSmoothedSpeed(adjustedMetrics.speed)
            )
            _metrics.value = smoothedMetrics
        } else {
            // Update the speed buffer
            updateSpeedBuffer(adjustedMetrics.speed)

            // Use the original metrics
            _metrics.value = adjustedMetrics
        }

        // Update heart rate history if we have data
        if (metrics.heartRate > 0 && metrics.coveredDistance > 0) {
            val distanceKm = metrics.coveredDistance / 1000.0
            val currentHistory = _heartRateHistory.value.toMutableList()

            if (shouldAppendDistancePoint(currentHistory, distanceKm)) {
                currentHistory.add(Pair(distanceKm, metrics.heartRate))
                _heartRateHistory.value = currentHistory
            }
        }

        // Update speed history for chart
        if (metrics.coveredDistance > 0) {
            val distanceKm = metrics.coveredDistance / 1000.0
            val currentSpeedHistory = _speedHistory.value.toMutableList()

            // Add new data point (only if distance has progressed significantly)
            val lastDistance = currentSpeedHistory.lastOrNull()?.first ?: 0.0
            val shouldAddPoint = currentSpeedHistory.isEmpty() || 
                                distanceKm - lastDistance >= 0.01 // Every 10 meters

            if (shouldAddPoint) {
                currentSpeedHistory.add(Pair(distanceKm, metrics.speed))
                _speedHistory.value = currentSpeedHistory
            }
        }

        // Update altitude history for chart
        if (metrics.coveredDistance > 0) {
            val distanceKm = metrics.coveredDistance / 1000.0
            val currentAltitudeHistory = _altitudeHistory.value.toMutableList()

            // Add new data point (only if distance has progressed significantly)
            val lastDistance = currentAltitudeHistory.lastOrNull()?.first ?: 0.0
            val shouldAddPoint = currentAltitudeHistory.isEmpty() ||
                                distanceKm - lastDistance >= 0.01 // Every 10 meters

            if (shouldAddPoint) {
                currentAltitudeHistory.add(Pair(distanceKm, metrics.altitude))
                _altitudeHistory.value = currentAltitudeHistory
            }
        }

        // Update pressure history for chart
        if (metrics.coveredDistance > 0 && metrics.pressure > 0) {
            val distanceKm = metrics.coveredDistance / 1000.0
            val currentPressureHistory = _pressureHistory.value.toMutableList()

            // Add new data point (only if distance has progressed significantly)
            val lastDistance = currentPressureHistory.lastOrNull()?.first ?: 0.0
            val shouldAddPoint = currentPressureHistory.isEmpty() ||
                                distanceKm - lastDistance >= 0.01 // Every 10 meters

            if (shouldAddPoint) {
                currentPressureHistory.add(Pair(distanceKm, metrics.pressure))
                _pressureHistory.value = currentPressureHistory
            }
        }

        // Update barometric altitude history for chart
        if (metrics.coveredDistance > 0 && metrics.altitudeFromPressure != 0f) {
            val distanceKm = metrics.coveredDistance / 1000.0
            val currentBarometerHistory = _barometerAltitudeHistory.value.toMutableList()

            // Add new data point (only if distance has progressed significantly)
            val lastDistance = currentBarometerHistory.lastOrNull()?.first ?: 0.0
            val shouldAddPoint = currentBarometerHistory.isEmpty() ||
                                distanceKm - lastDistance >= 0.01 // Every 10 meters

            if (shouldAddPoint) {
                currentBarometerHistory.add(Pair(distanceKm, metrics.altitudeFromPressure))
                _barometerAltitudeHistory.value = currentBarometerHistory
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

        // Clear existing lap times, speed history, heart rate history, altitude history, and barometric histories, then load from database
        _lapTimes.value = emptyList()
        _speedHistory.value = emptyList()
        _heartRateHistory.value = emptyList()
        _altitudeHistory.value = emptyList()
        _pressureHistory.value = emptyList()
        _barometerAltitudeHistory.value = emptyList()
        loadLapTimesFromDatabase()
        loadCurrentEventDataFromDatabase()

        Log.d(TAG, "Started new session: $sessionId")
    }

    private fun loadCurrentEventDataFromDatabase() {
        if (currentSessionId.isEmpty()) return

        scope.launch {
            try {
                val eventId = resolveCurrentEventId()
                if (eventId <= 0) {
                    Log.d(TAG, "No active event found for session $currentSessionId; skipping statistics hydration")
                    return@launch
                }

                val storedMetrics = database.metricDao()
                    .getMetricsForEvent(eventId)
                    .sortedBy { it.timeInMilliseconds }

                if (storedMetrics.isEmpty()) {
                    Log.d(TAG, "No stored metrics found for event $eventId; skipping statistics hydration")
                    return@launch
                }

                val event = database.eventDao().getEventById(eventId)

                currentEventId = eventId
                _speedHistory.value = buildDistanceHistory(
                    metrics = storedMetrics,
                    valueSelector = { it.speed },
                    isValid = { it >= 0f }
                )
                _altitudeHistory.value = buildDistanceHistory(
                    metrics = storedMetrics,
                    valueSelector = { it.elevation.toDouble() },
                    isValid = { true }
                )
                _heartRateHistory.value = buildDistanceHistory(
                    metrics = storedMetrics,
                    valueSelector = { it.heartRate },
                    isValid = { it > 0 }
                )
                _pressureHistory.value = buildDistanceHistory(
                    metrics = storedMetrics,
                    valueSelector = { it.pressure },
                    isValid = { it > 0f }
                )
                _barometerAltitudeHistory.value = buildDistanceHistory(
                    metrics = storedMetrics,
                    valueSelector = { it.altitudeFromPressure },
                    isValid = { it != 0f }
                )
                _metrics.value = buildMetricsSnapshot(event, storedMetrics)

                storedMetrics.lastOrNull { it.heartRate > 0 }?.let { latestHeartRateMetric ->
                    if (_heartRate.value == null) {
                        _heartRate.value = HeartRateData(
                            deviceName = latestHeartRateMetric.heartRateDevice
                                .takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }
                                ?: "Recorded sensor",
                            deviceAddress = "",
                            heartRate = latestHeartRateMetric.heartRate,
                            isConnected = false,
                            isScanning = false
                        )
                    }
                }

                Log.d(
                    TAG,
                    "Hydrated statistics for event $eventId: " +
                        "speed=${_speedHistory.value.size}, " +
                        "altitude=${_altitudeHistory.value.size}, " +
                        "heartRate=${_heartRateHistory.value.size}, " +
                        "pressure=${_pressureHistory.value.size}, " +
                        "barometer=${_barometerAltitudeHistory.value.size}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error hydrating statistics from database", e)
            }
        }
    }

    private suspend fun resolveCurrentEventId(): Int {
        val activeEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
            .getInt("active_event_id", -1)
        if (activeEventId > 0) return activeEventId

        return database.eventDao().getEventBySessionId(currentSessionId)?.eventId ?: -1
    }

    private fun buildMetricsSnapshot(event: Event?, storedMetrics: List<Metric>): Metrics? {
        val firstMetric = storedMetrics.firstOrNull() ?: return null
        val latestMetric = storedMetrics.last()
        val totalDistance = storedMetrics.maxOfOrNull { it.distance } ?: latestMetric.distance
        val durationSeconds = ((latestMetric.timeInMilliseconds - firstMetric.timeInMilliseconds) / 1000.0)
            .coerceAtLeast(0.0)
        val speeds = storedMetrics.map { it.speed.toDouble() }
        val recentSpeeds = speeds.takeLast(5)
        val slopes = storedMetrics.map { it.slope }
        val nonZeroSlopes = slopes.filter { it != 0.0 }

        return Metrics(
            latitude = 0.0,
            longitude = 0.0,
            speed = latestMetric.speed,
            altitude = latestMetric.elevation.toDouble(),
            slope = latestMetric.slope,
            averageSlope = nonZeroSlopes.averageOrZero(),
            maxUphillSlope = slopes.filter { it > 0.0 }.maxOrNull() ?: 0.0,
            maxDownhillSlope = abs(slopes.filter { it < 0.0 }.minOrNull() ?: 0.0),
            coveredDistance = totalDistance,
            lap = latestMetric.lap,
            startDateTime = firstMetric.timeInMilliseconds.toLocalDateTime(),
            currentDateTime = latestMetric.timeInMilliseconds.toLocalDateTime(),
            averageSpeed = if (durationSeconds > 0.0) {
                (totalDistance / durationSeconds) * 3.6
            } else {
                0.0
            },
            maxSpeed = speeds.maxOrNull() ?: 0.0,
            cumulativeElevationGain = storedMetrics.sumOf { it.elevationGain.toDouble().coerceAtLeast(0.0) },
            sessionId = currentSessionId,
            eventName = event?.eventName.orEmpty(),
            sportType = event?.artOfSport.orEmpty(),
            comment = event?.comment.orEmpty(),
            heartRate = latestMetric.heartRate,
            heartRateDevice = latestMetric.heartRateDevice,
            pressure = latestMetric.pressure ?: 0f,
            pressureAccuracy = latestMetric.pressureAccuracy ?: 0,
            altitudeFromPressure = latestMetric.altitudeFromPressure ?: 0f,
            seaLevelPressure = latestMetric.seaLevelPressure ?: 1013.25f,
            movingAverageSpeed = recentSpeeds.averageOrZero()
        )
    }

    private fun <T> buildDistanceHistory(
        metrics: List<Metric>,
        valueSelector: (Metric) -> T?,
        isValid: (T) -> Boolean
    ): List<Pair<Double, T>> {
        val history = mutableListOf<Pair<Double, T>>()

        metrics.forEach { metric ->
            val distanceKm = metric.distance / 1000.0
            val value = valueSelector(metric)
            if (distanceKm > 0.0 && value != null && isValid(value) && shouldAppendDistancePoint(history, distanceKm)) {
                history.add(Pair(distanceKm, value))
            }
        }

        return history
    }

    private fun <T> shouldAppendDistancePoint(history: List<Pair<Double, T>>, distanceKm: Double): Boolean {
        val lastDistance = history.lastOrNull()?.first ?: return true
        return distanceKm - lastDistance >= CHART_DISTANCE_STEP_KM
    }

    private fun List<Double>.averageOrZero(): Double {
        return if (isNotEmpty()) average() else 0.0
    }

    private fun Long.toLocalDateTime() =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

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
        _speedHistory.value = emptyList() // Clear speed history as well
        _heartRateHistory.value = emptyList() // Clear heart rate history for consistency
        _altitudeHistory.value = emptyList() // Clear altitude history for consistency
        _pressureHistory.value = emptyList() // Clear pressure history for consistency
        _barometerAltitudeHistory.value = emptyList() // Clear barometric altitude history for consistency
        currentSessionId = ""
        Log.d(TAG, "Cleared all lap times, speed history, heart rate history, altitude history, and barometric histories")
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
        if (sessionId.isEmpty()) return

        if (sessionId != currentSessionId) {
            currentSessionId = sessionId
            currentEventId = -1
        }

        loadLapTimesFromDatabase()
        loadCurrentEventDataFromDatabase()
        Log.d(TAG, "Initialized with session: $sessionId")
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
