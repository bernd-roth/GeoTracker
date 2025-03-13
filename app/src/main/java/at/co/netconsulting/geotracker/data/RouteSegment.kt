package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint

data class RouteSegment(
    val startPoint: GeoPoint,
    val endPoint: GeoPoint,
    val speed: Float
)
