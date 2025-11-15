package at.co.netconsulting.geotracker.composables

import at.co.netconsulting.geotracker.data.EventWithDetails
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    // Use filteredEvents which supports search across all loaded events
    val events by eventsViewModel.filteredEventsWithDetails.collectAsState(initial = emptyList())
    val searchQuery by eventsViewModel.searchQuery.collectAsState()

    // Load events with larger page size for better search coverage
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
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { eventsViewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { Text("Search by name, date, or sport...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { eventsViewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true
                )

                // Content
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
                    // MEMORY FIX: Use locationPointCount instead of checking locationPoints list
                    // ViewModel already handles search filtering via setSearchQuery()
                    val eventsWithTracks = events.filter { it.locationPointCount > 0 }

                    if (eventsWithTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "No events with track data found"
                                } else {
                                    "No tracks match your search"
                                },
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
    var isExpanded by remember { mutableStateOf(false) }

    val selectTrack = {
        // Load waypoints and create ImportedGpxTrack asynchronously
        coroutineScope.launch {
            // MEMORY FIX: Load location points from database (not preloaded in memory)
            val dbLocations = database.locationDao().getLocationsForEvent(eventWithDetails.event.eventId)
            val locationPoints = dbLocations.map { GeoPoint(it.latitude, it.longitude) }

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
            val hasRealTimestamps = eventWithDetails.metrics.isNotEmpty()
            val absoluteTimestamps = if (hasRealTimestamps) {
                eventWithDetails.metrics.map { it.timeInMilliseconds }
            } else {
                // If no metrics, generate synthetic timestamps based on distance and user-specified speed
                generateSyntheticTimestampsFromDistance(locationPoints, assumedSpeedKmh)
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
                points = locationPoints,
                timestamps = relativeTimestamps,
                eventId = eventWithDetails.event.eventId,
                waypoints = gpxWaypoints,
                hasSyntheticTimestamps = !hasRealTimestamps // Mark as synthetic if we generated them
            )
            onSelected(importedTrack)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Always visible: Header with event name, track indicator, and expand/collapse button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event name
                Text(
                    text = eventWithDetails.event.eventName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Track indicator
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
                        text = "${eventWithDetails.locationPointCount} pts",
                        fontSize = 12.sp,
                        color = Color.Green,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Expand/Collapse icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Expanded details - only shown when expanded
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
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

                    // Select button - only visible when expanded
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Button(
                        onClick = { selectTrack() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select This Track")
                    }
                }
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