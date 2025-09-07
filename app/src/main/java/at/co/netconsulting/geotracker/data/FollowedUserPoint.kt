package at.co.netconsulting.geotracker.data

import androidx.compose.runtime.Immutable

/**
 * Data class for followed user lap time information
 */
@Immutable
data class FollowedUserLapTime(
    val lapNumber: Int,
    val duration: Long, // Duration in milliseconds
    val distance: Double
)

/**
 * Represents a tracking point from a followed user
 */
@Immutable
data class FollowedUserPoint(
    val sessionId: String,
    val person: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val currentSpeed: Float = 0.0f,
    val distance: Double = 0.0,
    val heartRate: Int? = null,
    val timestamp: String = "",
    // Weather data
    val temperature: Double? = null,
    val weatherCode: Int? = null,
    val pressure: Double? = null,
    val relativeHumidity: Int? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    // Lap times data
    val lapTimes: List<FollowedUserLapTime>? = null
)