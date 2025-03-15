package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.data.LocationEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LocationEventPanel(event: LocationEvent) {
    val formattedTime = if (event.startDateTime == null) {
        "N/A"
    } else {
        val zonedDateTime = event.startDateTime.atZone(ZoneId.systemDefault())
        val epochMilli = zonedDateTime.toInstant().toEpochMilli()
        val eventStartDateTimeFormatted = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMilli),
            ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
        eventStartDateTimeFormatted
    }

    Column(modifier = Modifier.padding(8.dp)) {
        Text("Date and time: $formattedTime", style = MaterialTheme.typography.bodyLarge)
        Text("Speed: ${"%.2f".format(event.speed)} Km/h", style = MaterialTheme.typography.bodyLarge)
        Text("Ø speed: ${"%.2f".format(event.averageSpeed)} Km/h", style = MaterialTheme.typography.bodyLarge)
        Text("Covered distance: ${"%.3f".format(event.coveredDistance/1000)} Km", style = MaterialTheme.typography.bodyLarge)
        Text("Total ascent: ${"%.3f".format(event.totalAscent)} meter", style = MaterialTheme.typography.bodyLarge)
        Text("Total descent: ${"%.3f".format(event.totalDescent)} meter", style = MaterialTheme.typography.bodyLarge)
    }
}