package at.co.netconsulting.geotracker.data

import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.Weather
import org.osmdroid.util.GeoPoint

/**
 * Data class that combines an Event with all its related details for the Events screen
 */
data class EventWithDetails(
    val event: Event,
    val weather: Weather?,
    val locationPoints: List<GeoPoint>,
    val laps: List<Long>,
    val totalDistance: Double,
    val averageSpeed: Double,
    val startTime: Long,
    val endTime: Long,
    val satellites: Int
)