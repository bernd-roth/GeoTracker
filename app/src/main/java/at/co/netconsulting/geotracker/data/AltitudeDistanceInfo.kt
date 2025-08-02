package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class AltitudeDistanceInfo(
    val elevation: Float,
    val distance: Double,
    val position: Offset
)