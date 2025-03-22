package at.co.netconsulting.geotracker.data

data class MetricsUiState(
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val speedAccuracyInMeters: Float,
    val altitude: Double,
    val verticalAccuracyInMeters: Float,
    val horizontalAccuracyInMeters: Float,
    val numberOfSatellites: Int,
    val usedNumberOfSatellites: Int,
    val coveredDistance: Double
)
