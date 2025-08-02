package at.co.netconsulting.geotracker.data

import at.co.netconsulting.geotracker.enums.GpsFixStatus

data class GpsStatus(
    val status: GpsFixStatus,
    val message: String,
    val isReadyToRecord: Boolean,
    val horizontalAccuracy: Float = 0f,
    val verticalAccuracy: Float = 0f,
    val speedAccuracy: Float = 0f,
    val satelliteCount: Int = 0,
    val usedSatelliteCount: Int = 0
)
