package at.co.netconsulting.geotracker.data

import androidx.compose.runtime.Immutable

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
    val windDirection: Double? = null
)