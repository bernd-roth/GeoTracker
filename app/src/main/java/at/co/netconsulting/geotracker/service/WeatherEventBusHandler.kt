package at.co.netconsulting.geotracker.service

import android.util.Log
import at.co.netconsulting.geotracker.data.HeartRateData
import at.co.netconsulting.geotracker.data.CurrentWeather
import at.co.netconsulting.geotracker.data.Metrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.abs

/**
 * Singleton handler for weather and metrics events from EventBus
 * Fixes issue with fluctuating speed values in StatisticsScreen
 */
class WeatherEventBusHandler private constructor() {

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

    // Speed smoothing values
    private val speedBuffer = mutableListOf<Float>()
    private val speedBufferSize = 5
    private var lastMetricsTimestamp = 0L

    // Last metrics value for comparison
    private var lastMetricsValue: Metrics? = null

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

    companion object {
        private const val TAG = "WeatherEventBusHandler"

        @Volatile
        private var INSTANCE: WeatherEventBusHandler? = null

        fun getInstance(): WeatherEventBusHandler {
            return INSTANCE ?: synchronized(this) {
                val instance = WeatherEventBusHandler()
                INSTANCE = instance
                instance
            }
        }
    }
}