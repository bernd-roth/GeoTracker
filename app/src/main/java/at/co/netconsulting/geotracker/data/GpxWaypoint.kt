package at.co.netconsulting.geotracker.data

/**
 * Waypoint data from imported GPX files (not persisted to database)
 */
data class GpxWaypoint(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String? = null,
    val elevation: Double? = null
)
