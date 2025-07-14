package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class TimeInfo(
    val heartRate: Int,
    val timeInMilliseconds: Long,
    val position: Offset
)
