package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class AltitudeSpeedInfo(
    val elevation: Float,
    val speed: Float,
    val position: Offset

)