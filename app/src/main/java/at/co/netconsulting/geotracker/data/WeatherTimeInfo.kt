package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class WeatherTimeInfo(
    val temperature: Float,
    val timeInMilliseconds: Long,
    val position: Offset
)