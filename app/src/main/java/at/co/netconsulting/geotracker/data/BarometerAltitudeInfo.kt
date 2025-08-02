package at.co.netconsulting.geotracker.data

import androidx.compose.ui.geometry.Offset

data class BarometerAltitudeInfo(
    val pressure: Float,
    val elevation: Float,
    val position: Offset
)