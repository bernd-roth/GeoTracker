package at.co.netconsulting.geotracker.data

import java.time.LocalDateTime

data class LocationEvent(
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val speedAccuracyMetersPerSecond: Float,
    val altitude: Double,
    val horizontalAccuracy: Float,
    val verticalAccuracyMeters: Float,
    val coveredDistance: Double,
    val lap: Int,
    val startDateTime: LocalDateTime,
    val averageSpeed: Double
)