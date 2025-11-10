package at.co.netconsulting.geotracker.composables

import EventWithDetails
import androidx.compose.foundation.clickable
import org.osmdroid.util.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.GpxWaypoint
import at.co.netconsulting.geotracker.data.ImportedGpxTrack
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.viewmodel.EventsViewModel
import at.co.netconsulting.geotracker.viewmodel.EventsViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionDialog(
    assumedSpeedKmh: Double = 9.0,
    onTrackSelected: (ImportedGpxTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { FitnessTrackerDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val eventsViewModel: EventsViewModel = viewModel(
        factory = EventsViewModelFactory(database, context)
    )
    val events by eventsViewModel.eventsWithDetails.collectAsState(initial = emptyList())

    // Load events when dialog opens
    LaunchedEffect(Unit) {
        eventsViewModel.loadEvents()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Existing Track")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading events...")
                    }
                }
            } else {
                val eventsWithTracks = events.filter { it.locationPoints.isNotEmpty() }

                if (eventsWithTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No events with track data found",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(eventsWithTracks) { eventWithDetails ->
                            TrackSelectionItem(
                                eventWithDetails = eventWithDetails,
                                database = database,
                                coroutineScope = coroutineScope,
                                assumedSpeedKmh = assumedSpeedKmh,
                                onSelected = { importedTrack ->
                                    onTrackSelected(importedTrack)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun TrackSelectionItem(
    eventWithDetails: EventWithDetails,
    database: FitnessTrackerDatabase,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    assumedSpeedKmh: Double,
    onSelected: (ImportedGpxTrack) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Load waypoints and create ImportedGpxTrack asynchronously
                coroutineScope.launch {
                    // Load waypoints from database
                    val dbWaypoints = database.waypointDao().getWaypointsForEvent(eventWithDetails.event.eventId)

                    // Convert database Waypoint to GpxWaypoint
                    val gpxWaypoints = dbWaypoints.map { waypoint ->
                        GpxWaypoint(
                            latitude = waypoint.latitude,
                            longitude = waypoint.longitude,
                            name = waypoint.name,
                            description = waypoint.description,
                            elevation = waypoint.elevation
                        )
                    }

                    // Convert event data to ImportedGpxTrack format with timestamps
                    // Extract timestamps from metrics (assuming metrics and locations are in same order)
                    val absoluteTimestamps = if (eventWithDetails.metrics.isNotEmpty()) {
                        eventWithDetails.metrics.map { it.timeInMilliseconds }
                    } else {
                        // If no metrics, generate synthetic timestamps based on distance and user-specified speed
                        generateSyntheticTimestampsFromDistance(eventWithDetails.locationPoints, assumedSpeedKmh)
                    }

                    // Normalize timestamps to be relative elapsed times (start from 0)
                    val relativeTimestamps = if (absoluteTimestamps.isNotEmpty()) {
                        val startTime = absoluteTimestamps.first()
                        absoluteTimestamps.map { it - startTime }
                    } else {
                        emptyList()
                    }

                    val importedTrack = ImportedGpxTrack(
                        filename = "${eventWithDetails.event.eventName} (${eventWithDetails.event.eventDate})",
                        points = eventWithDetails.locationPoints,
                        timestamps = relativeTimestamps,
                        eventId = eventWithDetails.event.eventId,
                        waypoints = gpxWaypoints
                    )
                    onSelected(importedTrack)
                }
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with event name and track indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = eventWithDetails.event.eventName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Track available",
                        tint = Color.Green,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${eventWithDetails.locationPoints.size} pts",
                        fontSize = 12.sp,
                        color = Color.Green,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Event details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Date: ${eventWithDetails.event.eventDate}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Sport: ${eventWithDetails.event.artOfSport}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    if (eventWithDetails.totalDistance > 10) {
                        Text(
                            text = String.format("%.2f km", eventWithDetails.totalDistance / 1000),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (eventWithDetails.averageSpeed > 0.1) {
                        Text(
                            text = String.format("%.1f km/h", eventWithDetails.averageSpeed),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Duration if available
            if (eventWithDetails.startTime > 0 && eventWithDetails.endTime > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duration: ${formatDuration(eventWithDetails.endTime - eventWithDetails.startTime)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// Helper function to format duration
private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%02d:%02d", minutes, secs)
    }
}

/**
 * Generate synthetic timestamps for a track based on distance and assumed average speed.
 * @param points List of GPS points
 * @param speedKmh Assumed average speed in km/h (default: 9 km/h)
 * @return List of absolute timestamps in milliseconds (starting from 0)
 */
private fun generateSyntheticTimestampsFromDistance(points: List<GeoPoint>, speedKmh: Double = 9.0): List<Long> {
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) return listOf(0L)

    val timestamps = mutableListOf<Long>()
    var cumulativeTime = 0L
    timestamps.add(0L) // First point at time 0

    // Speed in meters per second
    val speedMs = (speedKmh * 1000.0) / 3600.0 // 9 km/h = 2.5 m/s

    for (i in 1 until points.size) {
        val prevPoint = points[i - 1]
        val currentPoint = points[i]

        // Calculate distance in meters using Haversine formula
        val distance = calculateDistanceBetweenPoints(
            prevPoint.latitude, prevPoint.longitude,
            currentPoint.latitude, currentPoint.longitude
        )

        // Calculate time in milliseconds for this segment
        val segmentTime = ((distance / speedMs) * 1000).toLong()
        cumulativeTime += segmentTime

        timestamps.add(cumulativeTime)
    }

    return timestamps
}

/**
 * Calculate distance between two GPS points using Haversine formula.
 * @return Distance in meters
 */
private fun calculateDistanceBetweenPoints(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // Earth radius in meters

    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return earthRadius * c
}