package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class DistanceInfo(
    val heartRate: Int,
    val distance: Double,
    val position: Offset
)