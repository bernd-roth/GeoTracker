package at.co.netconsulting.geotracker.service

import android.os.Looper
import at.co.netconsulting.geotracker.data.CurrentWeather
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WeatherEventBusHandlerTest {

    @After
    fun tearDown() {
        WeatherEventBusHandler.getInstance(RuntimeEnvironment.getApplication()).clearLapTimes()
        EventBus.getDefault().removeAllStickyEvents()
    }

    @Test
    fun weatherFetchedBeforeStatisticsOpensIsRetainedUntilRecordingCleanup() {
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

        handler.clearLapTimes()

        assertNull(handler.weather.value)
        assertNull(EventBus.getDefault().getStickyEvent(CurrentWeather::class.java))
    }
}
