package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class WeatherDistanceInfo(
    val temperature: Float,
    val distance: Double,
    val position: Offset
)