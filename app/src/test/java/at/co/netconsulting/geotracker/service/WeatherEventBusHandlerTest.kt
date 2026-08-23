package at.co.netconsulting.geotracker.service

import android.os.Looper
import at.co.netconsulting.geotracker.data.CurrentWeather
import at.co.netconsulting.geotracker.data.Metrics
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class WeatherEventBusHandlerTest {

    @After
    fun tearDown() {
        WeatherEventBusHandler.getInstance(RuntimeEnvironment.getApplication()).clearSessionStatistics()
        EventBus.getDefault().removeAllStickyEvents()
    }

    @Test
    fun weatherFetchedBeforeStatisticsOpensIsRetainedUntilReplacementContent() {
        val weather = CurrentWeather(
            temperature = 24.5,
            windspeed = 12.0,
            winddirection = 135.0,
            weathercode = 2,
            time = "2026-08-15T15:15"
        )

        EventBus.getDefault().postSticky(weather)

        val handler = WeatherEventBusHandler.getInstance(RuntimeEnvironment.getApplication())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(weather, handler.weather.value)

        handler.clearSessionStatistics()

        assertNull(handler.weather.value)
        assertNull(EventBus.getDefault().getStickyEvent(CurrentWeather::class.java))
    }

    @Test
    fun stoppingRetainsStatisticsUntilReplacementContentClearsThem() {
        val handler = WeatherEventBusHandler.getInstance(RuntimeEnvironment.getApplication())
        val weather = CurrentWeather(
            temperature = 18.0,
            windspeed = 5.0,
            winddirection = 90.0,
            weathercode = 1,
            time = "2026-08-22T10:00"
        )
        val metrics = Metrics(
            latitude = 48.2,
            longitude = 16.3,
            speed = 12f,
            altitude = 180.0,
            coveredDistance = 1_250.0,
            startDateTime = LocalDateTime.now().minusMinutes(10),
            movingAverageSpeed = 11.5,
            heartRate = 145
        )

        handler.onWeatherUpdate(weather)
        handler.onMetricsUpdate(metrics)
        handler.stopLapTracking()

        assertEquals(weather, handler.weather.value)
        assertEquals(1_250.0, handler.metrics.value?.coveredDistance ?: 0.0, 0.0)
        assertFalse(handler.speedHistory.value.isEmpty())
        assertFalse(handler.heartRateHistory.value.isEmpty())

        handler.clearSessionStatistics()

        assertNull(handler.weather.value)
        assertNull(handler.metrics.value)
        assertNull(handler.heartRate.value)
        assertEquals(emptyList<Pair<Double, Float>>(), handler.speedHistory.value)
        assertEquals(emptyList<Pair<Double, Int>>(), handler.heartRateHistory.value)
    }
}
