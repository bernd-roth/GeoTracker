package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class BarometerDistanceInfo(
    val pressure: Float,
    val distance: Double,
    val position: Offset
)