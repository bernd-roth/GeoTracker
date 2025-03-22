package at.co.netconsulting.geotracker.data

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val speedAccuracyMetersPerSecond: Float = 0f,
    val altitude: Double,
    val horizontalAccuracy: Float = 0f,
    val verticalAccuracy: Float = 0f,
    val coveredDistance: Double = 0.0,
    val numberOfSatellites: Int = 0,
    val usedNumberOfSatellites: Int = 0
)
