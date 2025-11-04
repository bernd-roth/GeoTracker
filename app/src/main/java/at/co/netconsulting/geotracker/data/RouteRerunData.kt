package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint
import at.co.netconsulting.geotracker.domain.Metric

data class RouteRerunData(
    val points: List<GeoPoint>,
    val isRerun: Boolean = true,
    val eventId: Int? = null,
    val totalDistance: Double? = null
)

data class RouteDisplayData(
    val points: List<GeoPoint>,
    val eventId: Int? = null,
    val showSlopeColors: Boolean = false,
    val metrics: List<Metric>? = null
)