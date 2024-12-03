package at.co.netconsulting.geotracker.data

import at.co.netconsulting.geotracker.location.CustomLocationListener
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
    val averageSpeed: Double,
    val locationChangeEventList: CustomLocationListener.LocationChangeEvent
)