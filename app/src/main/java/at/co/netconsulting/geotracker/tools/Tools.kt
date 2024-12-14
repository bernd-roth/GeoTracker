package at.co.netconsulting.geotracker.tools

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class Tools {
    private val EARTH_RADIUS = 6371008.8

    fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60)) % 24
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    fun provideDateTimeFormat() : String {
        val zonedDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val epochMilli = zonedDateTime.toInstant().toEpochMilli()
        val startDateTimeFormatted = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMilli),
            ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return startDateTimeFormatted
    }
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rad = Math.PI / 180
        val φ1 = lat1 * rad
        val φ2 = lat2 * rad

        // Using exact same formula as GPX Studio
        val a = kotlin.math.sin(φ1) * kotlin.math.sin(φ2) +
                kotlin.math.cos(φ1) * kotlin.math.cos(φ2) *
                kotlin.math.cos((lon2 - lon1) * rad)

        // Important: use coerceAtMost(1.0) just like GPX Studio's Math.min(a, 1)
        return EARTH_RADIUS * kotlin.math.acos(a.coerceAtMost(1.0))
    }
    // Overload that includes elevation
    fun calculateDistance(
        lat1: Double, lon1: Double, ele1: Double,
        lat2: Double, lon2: Double, ele2: Double
    ): Double {
        // First calculate horizontal distance
        val horizontalDistance = calculateDistance(lat1, lon1, lat2, lon2)

        // Calculate elevation difference
        val verticalDistance = ele2 - ele1

        // Use Pythagorean theorem to get true 3D distance
        return kotlin.math.sqrt(horizontalDistance * horizontalDistance +
                verticalDistance * verticalDistance)
    }
    fun formatCurrentTimestamp(): String {
        // Get the current date and time
        var now = LocalDateTime.now()
        // Define the desired format
        var formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
        // Format the timestamp
        var formattedTimestamp = now.format(formatter)
        return formattedTimestamp
    }

    fun createUUID(): String {
        val uuid = UUID.randomUUID()
        val uuidFromString = UUID.fromString("123e4567-b12b-123a-a123-817714174999")
        val uuidString = UUID.randomUUID().toString()
        return uuidString
    }
}