package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class SpeedInfo(
    val heartRate: Int,
    val speed: Float,
    val position: Offset
)
