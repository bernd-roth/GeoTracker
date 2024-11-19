package at.co.netconsulting.geotracker.data

data class LocationEvent(
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val speedAccuracyMetersPerSecond: Float,
    val altitude: Double,
    val horizontalAccuracy: Float,
    val verticalAccuracyMeters: Float,
    val coveredDistance: Double,
    val lap: Int
)