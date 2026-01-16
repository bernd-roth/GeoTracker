package at.co.netconsulting.geotracker.location

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import at.co.netconsulting.geotracker.data.RouteWeatherData
import at.co.netconsulting.geotracker.data.WeatherCondition
import at.co.netconsulting.geotracker.data.WeatherType
import at.co.netconsulting.geotracker.service.WeatherForecastService
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/**
 * Creates weather condition icon markers (rain, snow, wind, humidity)
 */
class WeatherIconsOverlay(
    private val context: Context,
    private val routeWeatherData: RouteWeatherData
) {

    companion object {
        private const val MIN_CLUSTER_DISTANCE_METERS = 500.0 // Cluster conditions within 500m
    }

    /**
     * Create weather condition markers based on weather codes and conditions
     */
    fun createWeatherMarkers(mapView: MapView): List<Marker> {
        val markers = mutableListOf<Marker>()

        // Create markers based on WMO weather codes for more comprehensive display
        val weatherCodeMarkers = createWeatherCodeMarkers(mapView)
        markers.addAll(weatherCodeMarkers)

        // Also add threshold-based markers (wind, humidity)
        val weatherService = WeatherForecastService(context)
        val conditions = weatherService.extractWeatherConditions(routeWeatherData.weatherForecasts)
        val clusteredConditions = clusterConditions(conditions)

        markers.addAll(clusteredConditions.map { condition ->
            createWeatherMarker(mapView, condition)
        })

        return markers
    }

    /**
     * Create markers based on WMO weather codes
     */
    private fun createWeatherCodeMarkers(mapView: MapView): List<Marker> {
        val markers = mutableListOf<Marker>()
        val processedIndices = mutableSetOf<Int>()

        for (i in routeWeatherData.weatherForecasts.indices) {
            if (i in processedIndices) continue

            val forecast = routeWeatherData.weatherForecasts[i]
            val weatherType = getWeatherTypeFromCode(forecast.weatherCode)

            if (weatherType != null) {
                // Check if there's a nearby marker of same type to cluster
                val nearbyIndices = mutableListOf(i)
                for (j in i + 1 until routeWeatherData.weatherForecasts.size) {
                    if (j in processedIndices) continue

                    val otherForecast = routeWeatherData.weatherForecasts[j]
                    if (getWeatherTypeFromCode(otherForecast.weatherCode) == weatherType) {
                        val distance = calculateDistance(
                            GeoPoint(forecast.latitude, forecast.longitude),
                            GeoPoint(otherForecast.latitude, otherForecast.longitude)
                        )
                        if (distance < MIN_CLUSTER_DISTANCE_METERS) {
                            nearbyIndices.add(j)
                            processedIndices.add(j)
                        }
                    }
                }

                processedIndices.add(i)

                val marker = Marker(mapView)
                marker.position = GeoPoint(forecast.latitude, forecast.longitude)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.icon = createWeatherCodeIcon(weatherType, forecast.temperature)
                marker.title = getWeatherCodeTitle(weatherType)
                marker.snippet = "${String.format("%.1f", forecast.temperature)}°C"

                markers.add(marker)
            }
        }

        return markers
    }

    /**
     * Get weather type from WMO code
     */
    private fun getWeatherTypeFromCode(code: Int): WeatherCodeType? {
        return when (code) {
            0, 1 -> WeatherCodeType.SUNNY // Clear sky, mainly clear
            2, 3 -> WeatherCodeType.CLOUDY // Partly cloudy, overcast
            45, 48 -> WeatherCodeType.CLOUDY // Fog
            51, 53, 55, 56, 57 -> WeatherCodeType.RAIN // Drizzle
            61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCodeType.RAIN // Rain, rain showers
            71, 73, 75, 77, 85, 86 -> WeatherCodeType.SNOW // Snow, snow showers
            95, 96, 99 -> WeatherCodeType.STORM // Thunderstorm
            else -> null
        }
    }

    /**
     * Cluster conditions that are close together
     */
    private fun clusterConditions(conditions: List<WeatherCondition>): List<WeatherCondition> {
        if (conditions.isEmpty()) return emptyList()

        val clustered = mutableListOf<WeatherCondition>()
        val processed = mutableSetOf<Int>()

        for (i in conditions.indices) {
            if (i in processed) continue

            val current = conditions[i]
            val nearbyConditions = mutableListOf(current)

            // Find nearby conditions of the same type
            for (j in i + 1 until conditions.size) {
                if (j in processed) continue

                val other = conditions[j]
                if (current.type == other.type) {
                    val distance = calculateDistance(current.position, other.position)
                    if (distance < MIN_CLUSTER_DISTANCE_METERS) {
                        nearbyConditions.add(other)
                        processed.add(j)
                    }
                }
            }

            // Use the first condition as representative
            clustered.add(current)
            processed.add(i)
        }

        return clustered
    }

    /**
     * Create a marker for a weather condition
     */
    private fun createWeatherMarker(mapView: MapView, condition: WeatherCondition): Marker {
        val marker = Marker(mapView)
        marker.position = condition.position
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        // Set icon based on weather type
        marker.icon = createWeatherIcon(condition.type)

        // Set info window title and snippet with temperature
        marker.title = getConditionTitle(condition.type)
        marker.snippet = "${condition.value} | ${String.format("%.1f", condition.temperature)}°C"

        return marker
    }

    /**
     * Create a simple weather icon drawable
     * Note: In a real implementation, you would use actual icon resources
     */
    private fun createWeatherIcon(type: WeatherType): android.graphics.drawable.Drawable {
        val size = 48
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background circle
        val bgPaint = Paint().apply {
            color = getBackgroundColor(type)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, bgPaint)

        // Border
        val borderPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, borderPaint)

        // Icon symbol (simplified text representation)
        val textPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 28f
            isFakeBoldText = true
        }

        val symbol = getWeatherSymbol(type)
        val textBounds = Rect()
        textPaint.getTextBounds(symbol, 0, symbol.length, textBounds)
        val textY = size / 2f - textBounds.exactCenterY()

        canvas.drawText(symbol, size / 2f, textY, textPaint)

        return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Get background color for weather type
     */
    private fun getBackgroundColor(type: WeatherType): Int {
        return when (type) {
            WeatherType.RAIN -> Color.rgb(33, 150, 243)      // Blue
            WeatherType.SNOW -> Color.rgb(158, 158, 158)     // Gray/White
            WeatherType.WIND -> Color.rgb(255, 152, 0)       // Orange
            WeatherType.HIGH_HUMIDITY -> Color.rgb(0, 188, 212) // Teal
        }
    }

    /**
     * Get text symbol for weather type
     */
    private fun getWeatherSymbol(type: WeatherType): String {
        return when (type) {
            WeatherType.RAIN -> "💧"
            WeatherType.SNOW -> "❄"
            WeatherType.WIND -> "💨"
            WeatherType.HIGH_HUMIDITY -> "💦"
        }
    }

    /**
     * Get title for weather condition
     */
    private fun getConditionTitle(type: WeatherType): String {
        return when (type) {
            WeatherType.RAIN -> "Rain"
            WeatherType.SNOW -> "Snow"
            WeatherType.WIND -> "High Wind"
            WeatherType.HIGH_HUMIDITY -> "High Humidity"
        }
    }

    /**
     * Create icon for weather code type
     */
    private fun createWeatherCodeIcon(type: WeatherCodeType, temperature: Double): android.graphics.drawable.Drawable {
        val size = 56
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background circle with temperature-based color
        val bgPaint = Paint().apply {
            color = getWeatherCodeBackgroundColor(type, temperature)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, bgPaint)

        // Border
        val borderPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2.5f, borderPaint)

        // Weather symbol
        val textPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 32f
            isFakeBoldText = true
        }

        val symbol = getWeatherCodeSymbol(type)
        val textBounds = Rect()
        textPaint.getTextBounds(symbol, 0, symbol.length, textBounds)
        val textY = size / 2f - textBounds.exactCenterY()

        canvas.drawText(symbol, size / 2f, textY, textPaint)

        return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Get background color for weather code type
     */
    private fun getWeatherCodeBackgroundColor(type: WeatherCodeType, temperature: Double): Int {
        return when (type) {
            WeatherCodeType.SUNNY -> if (temperature > 25) Color.rgb(255, 152, 0) else Color.rgb(255, 193, 7) // Orange/Amber
            WeatherCodeType.CLOUDY -> Color.rgb(158, 158, 158) // Gray
            WeatherCodeType.RAIN -> Color.rgb(33, 150, 243) // Blue
            WeatherCodeType.SNOW -> Color.rgb(176, 190, 197) // Blue Gray
            WeatherCodeType.STORM -> Color.rgb(63, 81, 181) // Indigo
        }
    }

    /**
     * Get symbol for weather code type
     */
    private fun getWeatherCodeSymbol(type: WeatherCodeType): String {
        return when (type) {
            WeatherCodeType.SUNNY -> "☀"
            WeatherCodeType.CLOUDY -> "☁"
            WeatherCodeType.RAIN -> "🌧"
            WeatherCodeType.SNOW -> "❄"
            WeatherCodeType.STORM -> "⛈"
        }
    }

    /**
     * Get title for weather code type
     */
    private fun getWeatherCodeTitle(type: WeatherCodeType): String {
        return when (type) {
            WeatherCodeType.SUNNY -> "Sunny"
            WeatherCodeType.CLOUDY -> "Cloudy"
            WeatherCodeType.RAIN -> "Rain"
            WeatherCodeType.SNOW -> "Snow"
            WeatherCodeType.STORM -> "Storm"
        }
    }

    /**
     * Calculate distance between two points in meters
     */
    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(point2.latitude - point1.latitude)
        val dLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(point1.latitude)) * kotlin.math.cos(Math.toRadians(point2.latitude)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return earthRadius * c
    }
}

/**
 * Weather condition types based on WMO codes
 */
enum class WeatherCodeType {
    SUNNY,      // Clear sky, sunny
    CLOUDY,     // Cloudy, overcast, fog
    RAIN,       // Rain, drizzle, showers
    SNOW,       // Snow, snow showers
    STORM       // Thunderstorm
}
