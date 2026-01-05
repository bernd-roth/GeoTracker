package at.co.netconsulting.geotracker.location

import android.graphics.Color
import at.co.netconsulting.geotracker.data.RouteWeatherData
import at.co.netconsulting.geotracker.data.WeatherForecast
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.*

/**
 * Creates weather overlay visualizations for routes
 * Temperature is displayed as colored polyline segments
 */
class WeatherOverlay(
    private val routeWeatherData: RouteWeatherData
) {

    companion object {
        private const val POLYLINE_WIDTH = 10f
        private const val INTERPOLATION_INTERVAL_METERS = 100.0 // Interpolate every 100m
    }

    /**
     * Create temperature-based colored polyline overlays
     */
    fun createTemperatureOverlay(mapView: MapView): List<Polyline> {
        if (routeWeatherData.weatherForecasts.isEmpty()) {
            return emptyList()
        }

        // Interpolate weather data along the entire route for smooth gradients
        val interpolatedForecasts = interpolateWeatherAlongRoute()

        // Create temperature segments
        val temperatureSegments = createTemperatureSegments(interpolatedForecasts)

        // Convert segments to polylines
        return temperatureSegments.map { segment ->
            Polyline().apply {
                color = getTemperatureColor(segment.temperature)
                outlinePaint.strokeWidth = POLYLINE_WIDTH
                setPoints(segment.points)
                infoWindow = null // Don't show info window on click
            }
        }
    }

    /**
     * Interpolate weather data along the entire route
     */
    private fun interpolateWeatherAlongRoute(): List<WeatherPoint> {
        val weatherPoints = mutableListOf<WeatherPoint>()
        val routePoints = routeWeatherData.routePoints
        val forecasts = routeWeatherData.weatherForecasts

        if (forecasts.isEmpty() || routePoints.isEmpty()) {
            return emptyList()
        }

        // Map forecasts to route indices (find closest route point for each forecast)
        val forecastIndices = forecasts.map { forecast ->
            val closestIndex = routePoints.indices.minByOrNull { i ->
                calculateDistance(
                    routePoints[i],
                    GeoPoint(forecast.latitude, forecast.longitude)
                )
            } ?: 0
            closestIndex to forecast
        }

        // Sort by index
        val sortedForecasts = forecastIndices.sortedBy { it.first }

        // Interpolate between forecast points
        for (i in 0 until sortedForecasts.size - 1) {
            val (startIndex, startForecast) = sortedForecasts[i]
            val (endIndex, endForecast) = sortedForecasts[i + 1]

            // Add start point
            weatherPoints.add(
                WeatherPoint(
                    routePoints[startIndex],
                    startForecast.temperature
                )
            )

            // Interpolate between start and end
            val segmentRoutePoints = routePoints.subList(startIndex, endIndex + 1)
            var accumulatedDistance = 0.0
            val totalDistance = calculateSegmentDistance(segmentRoutePoints)

            for (j in 1 until segmentRoutePoints.size) {
                val pointDistance = calculateDistance(
                    segmentRoutePoints[j - 1],
                    segmentRoutePoints[j]
                )
                accumulatedDistance += pointDistance

                // Linear interpolation of temperature based on distance
                val ratio = if (totalDistance > 0) accumulatedDistance / totalDistance else 0.0
                val interpolatedTemp = startForecast.temperature +
                        (endForecast.temperature - startForecast.temperature) * ratio

                weatherPoints.add(
                    WeatherPoint(
                        segmentRoutePoints[j],
                        interpolatedTemp
                    )
                )
            }
        }

        // Add last forecast point
        if (sortedForecasts.isNotEmpty()) {
            val (lastIndex, lastForecast) = sortedForecasts.last()
            if (lastIndex < routePoints.size) {
                weatherPoints.add(
                    WeatherPoint(
                        routePoints[lastIndex],
                        lastForecast.temperature
                    )
                )
            }
        }

        return weatherPoints
    }

    /**
     * Group weather points into temperature range segments
     */
    private fun createTemperatureSegments(weatherPoints: List<WeatherPoint>): List<TemperatureSegment> {
        if (weatherPoints.size < 2) return emptyList()

        val segments = mutableListOf<TemperatureSegment>()
        var currentSegmentPoints = mutableListOf<GeoPoint>()
        var currentTemperatureRange = getTemperatureRange(weatherPoints[0].temperature)

        currentSegmentPoints.add(weatherPoints[0].point)

        for (i in 1 until weatherPoints.size) {
            val temperatureRange = getTemperatureRange(weatherPoints[i].temperature)

            if (temperatureRange == currentTemperatureRange) {
                // Same range, add to current segment
                currentSegmentPoints.add(weatherPoints[i].point)
            } else {
                // Different range, finish current segment and start new one
                if (currentSegmentPoints.size >= 2) {
                    val avgTemp = weatherPoints.subList(
                        i - currentSegmentPoints.size,
                        i
                    ).map { it.temperature }.average()

                    segments.add(
                        TemperatureSegment(
                            points = currentSegmentPoints.toList(),
                            temperature = avgTemp
                        )
                    )
                }

                currentSegmentPoints = mutableListOf(weatherPoints[i - 1].point, weatherPoints[i].point)
                currentTemperatureRange = temperatureRange
            }
        }

        // Add the last segment
        if (currentSegmentPoints.size >= 2) {
            val avgTemp = weatherPoints.subList(
                weatherPoints.size - currentSegmentPoints.size,
                weatherPoints.size
            ).map { it.temperature }.average()

            segments.add(
                TemperatureSegment(
                    points = currentSegmentPoints,
                    temperature = avgTemp
                )
            )
        }

        return segments
    }

    /**
     * Get temperature range category
     */
    private fun getTemperatureRange(temp: Double): Int {
        return when {
            temp < -10 -> 0
            temp < 0 -> 1
            temp < 10 -> 2
            temp < 20 -> 3
            temp < 30 -> 4
            else -> 5
        }
    }

    /**
     * Get color for temperature value
     */
    private fun getTemperatureColor(tempCelsius: Double): Int {
        return when {
            tempCelsius < -10 -> Color.rgb(0, 0, 255)      // Deep blue
            tempCelsius < 0 -> Color.rgb(0, 191, 255)      // Light blue
            tempCelsius < 10 -> Color.rgb(0, 255, 0)       // Green
            tempCelsius < 20 -> Color.rgb(255, 255, 0)     // Yellow
            tempCelsius < 30 -> Color.rgb(255, 165, 0)     // Orange
            else -> Color.rgb(255, 0, 0)                    // Red
        }
    }

    /**
     * Calculate distance between two GeoPoints in meters
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
     * Calculate total distance of a segment
     */
    private fun calculateSegmentDistance(points: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 1 until points.size) {
            totalDistance += calculateDistance(points[i - 1], points[i])
        }
        return totalDistance
    }
}

/**
 * Weather point with temperature
 */
private data class WeatherPoint(
    val point: GeoPoint,
    val temperature: Double
)

/**
 * Temperature segment with average temperature
 */
private data class TemperatureSegment(
    val points: List<GeoPoint>,
    val temperature: Double
)
