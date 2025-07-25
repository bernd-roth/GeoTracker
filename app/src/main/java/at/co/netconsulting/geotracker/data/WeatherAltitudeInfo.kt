package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class WeatherAltitudeInfo(
    val temperature: Float,
    val elevation: Float,
    val position: Offset
)