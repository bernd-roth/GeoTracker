package at.co.netconsulting.geotracker.composables

import android.util.Log
import at.co.netconsulting.geotracker.data.CurrentWeather
import at.co.netconsulting.geotracker.data.Metrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class WeatherEventBusHandler private constructor() {
    // State holders
    private val _weather = MutableStateFlow<CurrentWeather?>(null)
    val weather: StateFlow<CurrentWeather?> = _weather.asStateFlow()

    private val _metrics = MutableStateFlow<Metrics?>(null)
    val metrics: StateFlow<Metrics?> = _metrics.asStateFlow()

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onWeatherReceived(currentWeather: CurrentWeather) {
        Log.e("WeatherEventBusHandler", "Weather received: temp=${currentWeather.temperature}, " +
                "wind=${currentWeather.windspeed}, direction=${currentWeather.winddirection}, " +
                "code=${currentWeather.weathercode}, time=${currentWeather.time}")
        _weather.value = currentWeather
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMetricsReceived(metrics: Metrics) {
        Log.d("WeatherEventBusHandler", "Metrics received: speed=${metrics.speed}, " +
                "distance=${metrics.coveredDistance}, alt=${metrics.altitude}")
        _metrics.value = metrics
    }

    companion object {
        @Volatile
        private var instance: WeatherEventBusHandler? = null

        fun getInstance(): WeatherEventBusHandler {
            return instance ?: synchronized(this) {
                instance ?: WeatherEventBusHandler().also { instance = it }
            }
        }
    }
}