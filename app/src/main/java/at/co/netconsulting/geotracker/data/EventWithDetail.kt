package at.co.netconsulting.geotracker.data

import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.Weather
import org.osmdroid.util.GeoPoint

data class EventWithDetails(
    val event: Event,
    val totalDistance: Double = 0.0,
    val averageSpeed: Double = 0.0,
    val maxElevation: Double = 0.0,
    val minElevation: Double = 0.0,
    val maxElevationGain: Double = 0.0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val weather: Weather? = null,
    val laps: List<Long> = emptyList(),
    val locationPoints: List<GeoPoint> = emptyList(),
    val satellites: Int = 0,

    // Heart rate related fields
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgHeartRate: Int = 0,
    val heartRateDevice: String = ""
)