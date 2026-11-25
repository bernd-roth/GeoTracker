package at.co.netconsulting.geotracker.location

import at.co.netconsulting.geotracker.data.RouteWeatherData
import at.co.netconsulting.geotracker.data.WeatherForecast
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherIconsOverlayTest {
    @Test
    fun clearForecastUsesDaylightDataForMarkerTitle() {
        val context = RuntimeEnvironment.getApplication()
        val map = MapView(context)
        map.setUseDataConnection(false)
        try {
            val point = GeoPoint(48.2, 16.4)
            val forecast = WeatherForecast(
                point.latitude, point.longitude, 6.0, 0.0, 0.0, 50, 0.0, 0.0, 0,
                "2026-11-24T16:00:00Z"
            )
            val data = RouteWeatherData(listOf(point), listOf(forecast), 0L, null)
            for ((isDay, title) in listOf(true to "Sunny", false to "Clear night", null to "Clear sky")) {
                val weather = data.copy(weatherForecasts = listOf(forecast.copy(isDay = isDay)))
                assertEquals(title, WeatherIconsOverlay(context, weather).createWeatherMarkers(map).single().title)
            }
            val unknown = data.copy(weatherForecasts = listOf(forecast.copy(weatherCode = null)))
            assertEquals(0, WeatherIconsOverlay(context, unknown).createWeatherMarkers(map).size)
        } finally {
            map.onDetach()
        }
    }
}
