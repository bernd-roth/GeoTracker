package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint

data class RouteRerunData(
    val points: List<GeoPoint>,
    val isRerun: Boolean = true,
    val eventId: Int? = null
)

data class RouteDisplayData(
    val points: List<GeoPoint>,
    val eventId: Int? = null
)