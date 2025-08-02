package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class BarometerTimeInfo(
    val pressure: Float,
    val timeInMilliseconds: Long,
    val position: Offset
)