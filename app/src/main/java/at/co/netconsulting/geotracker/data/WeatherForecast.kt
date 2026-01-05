package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint

/**
 * Weather forecast data for a specific location and time
 */
data class WeatherForecast(
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,        // °C
    val windSpeed: Double,          // km/h
    val windDirection: Double,      // degrees
    val relativeHumidity: Int,      // %
    val precipitation: Double,      // mm
    val snowfall: Double,          // cm
    val weatherCode: Int,          // WMO weather code
    val forecastTime: String       // ISO timestamp
)

/**
 * Complete weather data for a route
 */
data class RouteWeatherData(
    val routePoints: List<GeoPoint>,           // Original route points
    val weatherForecasts: List<WeatherForecast>, // Weather at sampled points
    val fetchTime: Long,                       // When data was fetched (millis)
    val targetTime: String?                    // Scheduled forecast time (ISO), null = now
)

/**
 * Weather condition marker (rain, snow, wind, humidity)
 */
data class WeatherCondition(
    val position: GeoPoint,
    val type: WeatherType,
    val value: String              // Display value (e.g., "5mm", "25km/h")
)

/**
 * Types of weather conditions to display as icons
 */
enum class WeatherType {
    RAIN,
    SNOW,
    WIND,
    HIGH_HUMIDITY
}
