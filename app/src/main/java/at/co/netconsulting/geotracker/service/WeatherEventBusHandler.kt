package at.co.netconsulting.geotracker.composables

import android.util.Log
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.data.CurrentWeather
import at.co.netconsulting.geotracker.data.HeartRateData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Singleton handler for collecting weather, metrics and heart rate data from EventBus
 */
class WeatherEventBusHandler private constructor() {
    // Flows for UI to collect
    private val _weather = MutableStateFlow<CurrentWeather?>(null)
    val weather: StateFlow<CurrentWeather?> = _weather

    private val _metrics = MutableStateFlow<Metrics?>(null)
    val metrics: StateFlow<Metrics?> = _metrics

    // New flow for heart rate data
    private val _heartRate = MutableStateFlow<HeartRateData?>(null)
    val heartRate: StateFlow<HeartRateData?> = _heartRate

    // Keep track of recent heart rate values for graphing
    private val _heartRateHistory = MutableStateFlow<List<Pair<Double, Int>>>(emptyList())
    val heartRateHistory: StateFlow<List<Pair<Double, Int>>> = _heartRateHistory

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWeatherReceived(weather: CurrentWeather) {
        Log.d(TAG, "Weather received: $weather")
        _weather.value = weather
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMetricsReceived(metrics: Metrics) {
        Log.d(TAG, "Metrics received: $metrics")
        _metrics.value = metrics

        // When we receive a metrics update with a distance, update our heart rate history
        // This ensures we're tracking heart rate along with distance for our chart
        val currentHR = _heartRate.value?.heartRate ?: 0
        if (currentHR > 0 && metrics.coveredDistance > 0) {
            val distanceInKm = metrics.coveredDistance / 1000
            val currentHistory = _heartRateHistory.value.toMutableList()

            // Add the new entry, but avoid duplicate distance points
            // If we have a previous entry with same distance (rounded to 0.1km), update it instead
            val existingIndex = currentHistory.indexOfFirst {
                Math.abs(it.first - distanceInKm) < 0.1
            }

            if (existingIndex >= 0) {
                currentHistory[existingIndex] = Pair(distanceInKm, currentHR)
            } else {
                currentHistory.add(Pair(distanceInKm, currentHR))
            }

            // Keep only the most recent 50 entries to prevent memory issues
            if (currentHistory.size > 50) {
                _heartRateHistory.value = currentHistory.takeLast(50)
            } else {
                _heartRateHistory.value = currentHistory
            }

            Log.d(TAG, "Updated HR history: ${_heartRateHistory.value.size} points")
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onHeartRateReceived(heartRateData: HeartRateData) {
        if (heartRateData.isConnected && heartRateData.heartRate > 0) {
            Log.d(TAG, "Heart rate received: ${heartRateData.heartRate} bpm from ${heartRateData.deviceName}")
            _heartRate.value = heartRateData

            // If we have current metrics, update heart rate history as well
            val currentMetrics = _metrics.value
            if (currentMetrics != null && currentMetrics.coveredDistance > 0) {
                val distanceInKm = currentMetrics.coveredDistance / 1000
                val currentHistory = _heartRateHistory.value.toMutableList()

                // Same logic as in onMetricsReceived
                val existingIndex = currentHistory.indexOfFirst {
                    Math.abs(it.first - distanceInKm) < 0.1
                }

                if (existingIndex >= 0) {
                    currentHistory[existingIndex] = Pair(distanceInKm, heartRateData.heartRate)
                } else {
                    currentHistory.add(Pair(distanceInKm, heartRateData.heartRate))
                }

                // Keep only the most recent 50 entries
                if (currentHistory.size > 50) {
                    _heartRateHistory.value = currentHistory.takeLast(50)
                } else {
                    _heartRateHistory.value = currentHistory
                }
            }
        } else {
            // Still update even when not connected or no heart rate
            // This allows UI to show connection status
            _heartRate.value = heartRateData
        }
    }

    companion object {
        private const val TAG = "WeatherEventBusHandler"

        // Singleton instance
        @Volatile
        private var instance: WeatherEventBusHandler? = null

        fun getInstance(): WeatherEventBusHandler {
            return instance ?: synchronized(this) {
                instance ?: WeatherEventBusHandler().also { instance = it }
            }
        }
    }
}