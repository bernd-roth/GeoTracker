package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class AltitudeChangeRateInfo(
    val changeRate: Double, // meters per minute
    val timeInMilliseconds: Long,
    val position: Offset
)