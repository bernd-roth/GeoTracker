package at.co.netconsulting.geotracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomSheetContent(
    latitude: Double,
    longitude: Double,
    speed: Float,
    speedAccuracyInMeters: Float,
    altitude: Double,
    horizontalAccuracyInMeters: Float,
    verticalAccuracyInMeters: Float,
    numberOfSatellites: Int,
    usedNumberOfSatellites: Int,
    coveredDistance: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Latitude: $latitude Longitude: $longitude",
            fontSize = 14.sp,
            color = Color.Black
        )
        Text(
            text = "Speed ${"%.2f".format(speed)} km/h",
            fontSize = 14.sp,
            color = Color.Black
        )
        Text(
            text = "Speed accuracy: ±${"%.2f".format(speedAccuracyInMeters)} km/h",
            fontSize = 14.sp,
            color = Color.Black
        )
        Text(
            text = "Altitude: ${"%.2f".format(altitude)} meter",
            fontSize = 14.sp,
            color = Color.Black
        )
        Text(
            text = "Horizontal accuracy: ±${"%.2f".format(horizontalAccuracyInMeters)} meter",
            fontSize = 14.sp,
            color = Color.Black
        )
        Text(
            text = "Vertical accuracy: ±${"%.2f".format(verticalAccuracyInMeters)} meter",
            fontSize = 14.sp,
            color = Color.Black
        )
        Text(
            text = "Satellites: $usedNumberOfSatellites/$numberOfSatellites ",
            fontSize = 14.sp,
            color = Color.Black
        )
        Text(
            text = "Covered distance: ${"%.3f".format(coveredDistance / 1000)} Km",
            fontSize = 14.sp,
            color = Color.Black
        )
    }
}