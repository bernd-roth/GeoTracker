package at.co.netconsulting.geotracker.data

import androidx.compose.runtime.Immutable

/**
 * Represents an active user currently recording their event
 */
@Immutable
data class ActiveUser(
    val sessionId: String,
    val person: String,
    val eventName: String = "",
    val lastUpdate: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isSelected: Boolean = false
)