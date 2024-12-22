package at.co.netconsulting.geotracker.data

data class SatelliteInfo(
    val visibleSatellites: Int = 0,
    val totalSatellites: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)