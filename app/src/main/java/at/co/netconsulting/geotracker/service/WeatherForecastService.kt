package at.co.netconsulting.geotracker.service

import android.content.Context
import android.util.Log
import at.co.netconsulting.geotracker.data.RouteWeatherData
import at.co.netconsulting.geotracker.data.WeatherCondition
import at.co.netconsulting.geotracker.data.WeatherForecast
import at.co.netconsulting.geotracker.data.WeatherType
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.util.GeoPoint
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Service for fetching weather forecasts for routes using Open-Meteo API
 */
class WeatherForecastService(private val context: Context) {

    companion object {
        private const val TAG = "WeatherForecastService"
        private const val API_BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val MAX_SAMPLE_POINTS = 50
        private const val SECONDS_PER_QUARTER_HOUR = 15 * 60L

        // Weather condition thresholds
        private const val RAIN_THRESHOLD_MM = 1.0
        private const val SNOW_THRESHOLD_CM = 0.5
        private const val WIND_THRESHOLD_KMH = 25.0
        private const val HUMIDITY_THRESHOLD_PERCENT = 85
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Fetch weather forecast for a route
     * @param routePoints List of GeoPoints representing the route
     * @param targetTime ISO local date and time in the device's time zone (null = current time)
     * @return RouteWeatherData or null if fetch fails
     */
    suspend fun fetchRouteWeatherForecast(
        routePoints: List<GeoPoint>,
        targetTime: String? = null
    ): RouteWeatherData? = withContext(Dispatchers.IO) {
        try {
            if (routePoints.isEmpty()) {
                Log.e(TAG, "Route points list is empty")
                return@withContext null
            }

            val forecastInstant = targetTime?.let {
                LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant()
            } ?: Instant.now()

            // Sample route points to limit API calls
            val sampledPoints = sampleRoutePoints(routePoints)
            Log.d(TAG, "Sampled ${sampledPoints.size} points from ${routePoints.size} total points")

            // Build API URL
            val url = buildWeatherForecastUrl(sampledPoints)
            Log.d(TAG, "Fetching weather from: $url")

            // Make API request
            val request = Request.Builder()
                .url(url)
                .build()

            val responseBody = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request failed with code: ${response.code}")
                    return@withContext null
                }
                response.body?.string()
            }
            if (responseBody == null) {
                Log.e(TAG, "Response body is null")
                return@withContext null
            }

            // Parse response - Open-Meteo returns array when multiple coordinates are requested
            val apiResponses = try {
                gson.fromJson(responseBody, Array<WeatherApiResponse>::class.java).toList()
            } catch (e: Exception) {
                // Try parsing as single object (fallback for single coordinate)
                Log.d(TAG, "Trying single response format")
                listOf(gson.fromJson(responseBody, WeatherApiResponse::class.java))
            }

            if (apiResponses.isEmpty()) {
                Log.e(TAG, "No API responses parsed")
                return@withContext null
            }

            // Convert to WeatherForecast objects
            val weatherForecasts = parseWeatherForecastsFromMultipleResponses(
                sampledPoints,
                apiResponses,
                forecastInstant
            )
            if (weatherForecasts.isEmpty()) return@withContext null

            RouteWeatherData(
                routePoints = routePoints,
                weatherForecasts = weatherForecasts,
                fetchTime = System.currentTimeMillis(),
                targetTime = targetTime
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching weather forecast", e)
            null
        }
    }

    /**
     * Sample route points at regular intervals based on route length
     */
    private fun sampleRoutePoints(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size <= MAX_SAMPLE_POINTS) {
            return points
        }

        // Calculate total distance
        val totalDistance = calculateTotalDistance(points)
        Log.d(TAG, "Total route distance: ${totalDistance / 1000} km")

        // Determine sampling interval based on distance
        val samplingInterval = when {
            totalDistance < 10000 -> 1000.0  // 1km for routes < 10km
            totalDistance < 50000 -> 2000.0  // 2km for routes 10-50km
            else -> 5000.0                    // 5km for routes > 50km
        }

        val sampledPoints = mutableListOf<GeoPoint>()
        sampledPoints.add(points.first()) // Always include start point

        var accumulatedDistance = 0.0
        var nextSampleDistance = samplingInterval

        for (i in 1 until points.size) {
            val distance = calculateDistance(points[i - 1], points[i])
            accumulatedDistance += distance

            if (accumulatedDistance >= nextSampleDistance) {
                sampledPoints.add(points[i])
                nextSampleDistance += samplingInterval

                if (sampledPoints.size >= MAX_SAMPLE_POINTS - 1) {
                    break // Leave room for end point
                }
            }
        }

        // Always include end point
        if (sampledPoints.last() != points.last()) {
            sampledPoints.add(points.last())
        }

        return sampledPoints
    }

    /**
     * Calculate total distance of route in meters
     */
    private fun calculateTotalDistance(points: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 1 until points.size) {
            totalDistance += calculateDistance(points[i - 1], points[i])
        }
        return totalDistance
    }

    /**
     * Calculate distance between two points using Haversine formula
     */
    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(point2.latitude - point1.latitude)
        val dLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(point1.latitude)) * cos(Math.toRadians(point2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Build weather forecast API URL
     */
    private fun buildWeatherForecastUrl(points: List<GeoPoint>): String {
        val latitudes = points.joinToString(",") { String.format(Locale.US, "%.4f", it.latitude) }
        val longitudes = points.joinToString(",") { String.format(Locale.US, "%.4f", it.longitude) }

        return "$API_BASE_URL" +
                "?latitude=$latitudes" +
                "&longitude=$longitudes" +
                "&hourly=temperature_2m,wind_speed_10m,wind_direction_10m,relative_humidity_2m,precipitation,snowfall,weather_code,is_day" +
                "&minutely_15=is_day&past_minutely_15=96&forecast_minutely_15=96" +
                "&forecast_days=7&past_days=1&timeformat=unixtime"
    }

    /**
     * Parse API responses (array of responses, one per coordinate) into WeatherForecast objects
     */
    private fun parseWeatherForecastsFromMultipleResponses(
        points: List<GeoPoint>,
        apiResponses: List<WeatherApiResponse>,
        forecastInstant: Instant
    ): List<WeatherForecast> {
        val forecasts = mutableListOf<WeatherForecast>()

        // Each API response corresponds to one coordinate point
        for (i in points.indices) {
            if (i >= apiResponses.size) {
                Log.w(TAG, "Not enough API responses for all points (have ${apiResponses.size}, need ${points.size})")
                break
            }

            val apiResponse = apiResponses[i]
            val hourly = apiResponse.hourly

            if (hourly == null) {
                Log.w(TAG, "No hourly data for point $i")
                continue
            }

            val timeIndex = routeForecastHourIndex(hourly.time, forecastInstant)

            if (timeIndex == -1) {
                Log.w(TAG, "Could not find suitable time index for point $i")
                continue
            }

            try {
                // Use the finer daylight reading near sunrise and sunset when it is available.
                val daylight = apiResponse.minutely15?.let { minutely ->
                    val epoch = forecastInstant.epochSecond
                    val quarterHour = epoch - Math.floorMod(epoch, SECONDS_PER_QUARTER_HOUR)
                    minutely.isDay?.getOrNull(minutely.time.indexOf(quarterHour))
                } ?: hourly.isDay?.getOrNull(timeIndex)
                forecasts.add(
                    WeatherForecast(
                        latitude = points[i].latitude,
                        longitude = points[i].longitude,
                        temperature = hourly.temperature2m?.getOrNull(timeIndex) ?: 0.0,
                        windSpeed = hourly.windspeed10m?.getOrNull(timeIndex) ?: 0.0,
                        windDirection = hourly.winddirection10m?.getOrNull(timeIndex) ?: 0.0,
                        relativeHumidity = hourly.relativehumidity2m?.getOrNull(timeIndex) ?: 0,
                        precipitation = hourly.precipitation?.getOrNull(timeIndex) ?: 0.0,
                        snowfall = hourly.snowfall?.getOrNull(timeIndex) ?: 0.0,
                        weatherCode = hourly.weatherCode?.getOrNull(timeIndex),
                        forecastTime = Instant.ofEpochSecond(hourly.time[timeIndex]).toString(),
                        isDay = when (daylight) {
                            1 -> true
                            0 -> false
                            else -> null
                        }
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing forecast for point $i", e)
            }
        }

        Log.d(TAG, "Parsed ${forecasts.size} weather forecasts from ${apiResponses.size} API responses")
        return forecasts
    }

    /**
     * Extract notable weather conditions from forecasts
     */
    fun extractWeatherConditions(forecasts: List<WeatherForecast>): List<WeatherCondition> {
        val conditions = mutableListOf<WeatherCondition>()

        forecasts.forEach { forecast ->
            val position = GeoPoint(forecast.latitude, forecast.longitude)

            // Check for rain
            if (forecast.precipitation > RAIN_THRESHOLD_MM) {
                conditions.add(
                    WeatherCondition(
                        position = position,
                        type = WeatherType.RAIN,
                        value = "${String.format("%.1f", forecast.precipitation)}mm",
                        temperature = forecast.temperature
                    )
                )
            }

            // Check for snow
            if (forecast.snowfall > SNOW_THRESHOLD_CM) {
                conditions.add(
                    WeatherCondition(
                        position = position,
                        type = WeatherType.SNOW,
                        value = "${String.format("%.1f", forecast.snowfall)}cm",
                        temperature = forecast.temperature
                    )
                )
            }

            // Check for high wind
            if (forecast.windSpeed > WIND_THRESHOLD_KMH) {
                conditions.add(
                    WeatherCondition(
                        position = position,
                        type = WeatherType.WIND,
                        value = "${String.format("%.0f", forecast.windSpeed)} km/h",
                        temperature = forecast.temperature
                    )
                )
            }

            // Check for high humidity
            if (forecast.relativeHumidity > HUMIDITY_THRESHOLD_PERCENT) {
                conditions.add(
                    WeatherCondition(
                        position = position,
                        type = WeatherType.HIGH_HUMIDITY,
                        value = "${forecast.relativeHumidity}%",
                        temperature = forecast.temperature
                    )
                )
            }
        }

        return conditions
    }
}

internal fun routeForecastHourIndex(times: List<Long>, target: Instant): Int =
    times.indexOf(target.truncatedTo(ChronoUnit.HOURS).epochSecond)

/**
 * API response models for Open-Meteo
 */
private data class WeatherApiResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hourly: HourlyWeatherData?,
    @SerializedName("minutely_15")
    val minutely15: MinutelyDaylight?
)

private data class MinutelyDaylight(
    val time: List<Long>,
    @SerializedName("is_day")
    val isDay: List<Int?>?
)

private data class HourlyWeatherData(
    val time: List<Long>,
    @SerializedName("temperature_2m")
    val temperature2m: List<Double>?,
    @SerializedName("wind_speed_10m")
    val windspeed10m: List<Double>?,
    @SerializedName("wind_direction_10m")
    val winddirection10m: List<Double>?,
    @SerializedName("relative_humidity_2m")
    val relativehumidity2m: List<Int>?,
    val precipitation: List<Double>?,
    val snowfall: List<Double>?,
    @SerializedName("weather_code")
    val weatherCode: List<Int?>?,
    @SerializedName("is_day")
    val isDay: List<Int?>?
)
