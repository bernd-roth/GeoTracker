package at.co.netconsulting.geotracker.composables

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.data.EventWithDetails
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.tools.Tools
import at.co.netconsulting.geotracker.viewmodel.EventsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onEditEvent: (Int) -> Unit = {} // Navigation callback for edit screen
) {
    val context = LocalContext.current
    val eventsViewModel = remember { EventsViewModel(FitnessTrackerDatabase.getInstance(context)) }
    val events by eventsViewModel.filteredEventsWithDetails.collectAsState(initial = emptyList())
    val allEvents by eventsViewModel.eventsWithDetails.collectAsState(initial = emptyList())
    val isLoading by eventsViewModel.isLoading.collectAsState()
    val searchQuery by eventsViewModel.searchQuery.collectAsState()

    // Track both the active event ID and recording state
    var activeEventId by remember { mutableStateOf(-1) }
    var isRecording by remember { mutableStateOf(false) }

    // Add a state to trigger stats refresh
    var statsRefreshTrigger by remember { mutableStateOf(0) }

    // Refresh the state on recomposition
    LaunchedEffect(Unit) {
        // Launch effect to fetch shared preferences values
        activeEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
            .getInt("active_event_id", -1)

        isRecording = context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
            .getBoolean("is_recording", false)

        // Log initial state for debugging
        Log.d("EventsScreen", "Initial state: activeEventId=$activeEventId, isRecording=$isRecording")
    }

    // Create a periodic check to catch changes in SharedPreferences
    LaunchedEffect(Unit) {
        // Check every 1 second for changes in SharedPreferences
        while (true) {
            val newActiveEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                .getInt("active_event_id", -1)

            val newIsRecording = context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_recording", false)

            // Log any changes
            if (newActiveEventId != activeEventId || newIsRecording != isRecording) {
                Log.d("EventsScreen", "State changed: activeEventId=$newActiveEventId (was $activeEventId), isRecording=$newIsRecording (was $isRecording)")
            }

            // Update regardless of change to ensure UI is consistent
            activeEventId = newActiveEventId
            isRecording = newIsRecording

            delay(1000) // 1-second interval
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var selectedEventId by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<EventWithDetails?>(null) }
    var showYearlyStats by remember { mutableStateOf(true) } // State to toggle stats visibility

    // Load initial events
    LaunchedEffect(Unit) {
        eventsViewModel.loadEvents()
    }

    // Load more events when scrolling to the bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collectLatest { lastIndex ->
                if (lastIndex != null && allEvents.isNotEmpty() && lastIndex >= events.size - 5) {
                    eventsViewModel.loadMoreEvents()
                }
            }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && eventToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Event") },
            text = {
                if (eventToDelete?.event?.eventId == activeEventId && isRecording) {
                    Text("Cannot delete an event that is currently being recorded.")
                } else {
                    Text("Are you sure you want to delete '${eventToDelete?.event?.eventName}'?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        eventToDelete?.let { event ->
                            // Only allow deletion if not currently recording
                            if (event.event.eventId != activeEventId || !isRecording) {
                                coroutineScope.launch {
                                    eventsViewModel.deleteEvent(event.event.eventId)

                                    // Increment the refresh trigger to force stats update
                                    statsRefreshTrigger++

                                    // Show feedback to the user
                                    Toast.makeText(
                                        context,
                                        "Event deleted successfully",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    showDeleteDialog = false
                                    eventToDelete = null
                                }
                            } else {
                                showDeleteDialog = false
                                eventToDelete = null
                            }
                        }
                    },
                    enabled = eventToDelete?.event?.eventId != activeEventId || !isRecording
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        eventToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Events",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            // Toggle button for stats
            TextButton(
                onClick = { showYearlyStats = !showYearlyStats },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (showYearlyStats) "Hide Stats" else "Show Stats")
                Icon(
                    imageVector = if (showYearlyStats) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle stats",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { eventsViewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search events...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { eventsViewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        // Add the Yearly Stats Overview between search and event list with animation
        // Pass the refresh trigger to force recomposition when an event is deleted
        AnimatedVisibility(
            visible = showYearlyStats,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            YearlyStatsOverview(
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                eventsViewModel = eventsViewModel,
                refreshTrigger = statsRefreshTrigger, // Pass the refresh trigger
                onWeekSelected = { year, week ->
                    // Filter events for the selected week
                    coroutineScope.launch {
                        // Calculate date range for the selected week
                        val calendar = Calendar.getInstance().apply {
                            firstDayOfWeek = Calendar.MONDAY
                            minimalDaysInFirstWeek = 4
                            set(Calendar.YEAR, year)
                            set(Calendar.WEEK_OF_YEAR, week)
                            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                        }

                        // Format start date (Monday)
                        val startDateStr = "${calendar.get(Calendar.YEAR)}-" +
                                String.format("%02d", calendar.get(Calendar.MONTH) + 1) + "-" +
                                String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))

                        // Move to end of week (Sunday)
                        calendar.add(Calendar.DAY_OF_MONTH, 6)

                        // Format end date (Sunday)
                        val endDateStr = "${calendar.get(Calendar.YEAR)}-" +
                                String.format("%02d", calendar.get(Calendar.MONTH) + 1) + "-" +
                                String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))

                        // Set filter in the ViewModel
                        eventsViewModel.filterByDateRange(startDate = startDateStr, endDate = endDateStr)
                    }
                }
            )
        }

        if (events.isEmpty() && isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "No events found" else "No events match your search",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(events) { eventWithDetails ->
                    // Double-check recording status from both values to be extra safe
                    val isRecordingThisEvent = eventWithDetails.event.eventId == activeEventId &&
                            (isRecording || context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                                .getBoolean("is_recording", false))

                    // Optional debugging log for this specific event
                    if (eventWithDetails.event.eventId == activeEventId) {
                        Log.d("EventsScreen", "Event ${eventWithDetails.event.eventName} (ID: ${eventWithDetails.event.eventId}): isRecordingThisEvent=$isRecordingThisEvent, activeEventId=$activeEventId, isRecording=$isRecording")
                    }

                    EventCard(
                        event = eventWithDetails,
                        selected = selectedEventId == eventWithDetails.event.eventId,
                        onClick = {
                            selectedEventId = if (selectedEventId == eventWithDetails.event.eventId) null else eventWithDetails.event.eventId
                        },
                        onEdit = {
                            // Only allow edit if not currently recording
                            if (!isRecordingThisEvent) {
                                onEditEvent(eventWithDetails.event.eventId)
                            } else {
                                // Show a toast message
                                Toast.makeText(
                                    context,
                                    "Cannot edit an event that is currently being recorded",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDelete = {
                            // Similar logic for deletion
                            if (!isRecordingThisEvent) {
                                eventToDelete = eventWithDetails
                                showDeleteDialog = true
                            } else {
                                Toast.makeText(
                                    context,
                                    "Cannot delete an event that is currently being recorded",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onExport = {
                            // Launch the export function in a coroutine
                            coroutineScope.launch {
                                // Call the export function properly with the correct parameters
                                at.co.netconsulting.geotracker.gpx.export(
                                    eventId = eventWithDetails.event.eventId,
                                    contextActivity = context
                                )
                            }
                        },
                        isCurrentlyRecording = isRecordingThisEvent
                    )
                }

                item {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            fontSize = 14.sp,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun InfoRowWithColor(label: String, value: String, textColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            fontSize = 14.sp,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
fun EventCard(
    event: EventWithDetails,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    isCurrentlyRecording: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrentlyRecording) {
                    Modifier.border(2.dp, Color.Red.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with title and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Event title
                Text(
                    text = event.event.eventName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Action buttons
                Row {
                    // Export GPX button with a more appropriate icon that's already available
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Export GPX",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Edit button - disabled if currently recording
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp),
                        enabled = !isCurrentlyRecording
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Event",
                            tint = if (isCurrentlyRecording) Color.Gray
                            else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Delete button - also disabled if currently recording
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp),
                        enabled = !isCurrentlyRecording
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Event",
                            tint = if (isCurrentlyRecording) Color.Gray
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // If currently recording, show a recording indicator with more prominent styling
            if (isCurrentlyRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp)
                        .background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsating animation for the recording dot
                    val infiniteTransition = rememberInfiniteTransition()
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(scale)
                            .background(Color.Red, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Currently recording",
                        fontSize = 14.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            // Clickable area for expanding/collapsing details
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Basic event info (always shown)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            InfoRow("Date:", event.event.eventDate)
                            InfoRow("Sport:", event.event.artOfSport)
                        }
                    }

                    // Stats section - only show if there's actual data
                    if (event.totalDistance > 10 || event.averageSpeed > 0.1) { // Only show if meaningful data exists
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                // Keep existing stats
                                InfoRow("Distance:", String.format("%.2f km", event.totalDistance / 1000))
                                InfoRow("Avg. Speed:", String.format("%.1f km/h", event.averageSpeed * 3.6))
                                // Elevation info
                                InfoRow("Elevation Gain:", String.format("%.0f m", event.maxElevationGain))
                                InfoRow("Max. Elevation:", String.format("%.0f m", event.maxElevation))
                                InfoRow("Min. Elevation:", String.format("%.0f m", event.minElevation))
                            }
                        }
                    } else {
                        // Show a compact message for new events with no metrics yet
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No activity data recorded yet",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // If event is selected, show additional details
            if (selected) {
                Spacer(modifier = Modifier.height(16.dp))

                Divider()

                Spacer(modifier = Modifier.height(16.dp))

                // Event Times
                Text(
                    text = "Event Times",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (event.startTime > 0 && event.endTime > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoRow("Start:", Tools().formatTimestamp(event.startTime))
                            InfoRow("Duration:", Tools().formatDuration(event.endTime - event.startTime))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            InfoRow("End:", Tools().formatTimestamp(event.endTime))
                            InfoRow("Satellites:", "${event.satellites}")
                        }
                    }
                } else {
                    Text(
                        text = "No time data available",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Weather information
                Text(
                    text = "Weather Conditions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (event.weather != null && event.weather.temperature > 0f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoRow("Temperature:", "${event.weather.temperature}°C")
                            InfoRow("Wind Speed:", "${event.weather.windSpeed} Km/h")
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            InfoRow("Wind Direction:", event.weather.windDirection)
                            InfoRow("Humidity:", "${event.weather.relativeHumidity}%")
                        }
                    }
                } else {
                    Text(
                        text = "No weather data available",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lap information with highlighting
                Text(
                    text = "Lap Information",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (event.laps.isNotEmpty()) {
                    // Find the fastest and slowest complete laps
                    // We'll consider the last lap as possibly incomplete
                    val completeLaps = if (event.laps.size > 1) event.laps.dropLast(1) else event.laps
                    val lastLap = if (event.laps.size > 1) event.laps.last() else null

                    // Find fastest and slowest times from complete laps
                    val fastestLapTime = completeLaps.minOrNull() ?: Long.MAX_VALUE
                    val slowestLapTime = completeLaps.maxOrNull() ?: 0L

                    event.laps.forEachIndexed { index, lapTime ->
                        val isLast = index == event.laps.size - 1
                        val isLastAndIncomplete = isLast && lastLap != null && event.endTime > 0 &&
                                (event.endTime - event.startTime) % lapTime != 0L

                        val textColor = when {
                            isLastAndIncomplete -> Color.Gray // Last incomplete lap in gray
                            lapTime == fastestLapTime && !isLastAndIncomplete -> Color.Green // Fastest lap in green
                            lapTime == slowestLapTime -> Color.Red // Slowest lap in red
                            else -> Color.Unspecified // Normal laps in default color
                        }

                        val lapLabel = "Lap ${index + 1} (1km):"
                        val lapValueText = Tools().formatDuration(lapTime)
                        val lapStatus = when {
                            isLastAndIncomplete -> " (incomplete)"
                            lapTime == fastestLapTime && !isLastAndIncomplete -> " (fastest)"
                            lapTime == slowestLapTime -> " (slowest)"
                            else -> ""
                        }

                        InfoRowWithColor(
                            label = lapLabel,
                            value = lapValueText + lapStatus,
                            textColor = textColor
                        )
                    }
                } else {
                    Text(
                        text = "No lap data available",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Map preview
                Text(
                    text = "Route Preview",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

// Map preview section with fixed update function
                if (event.locationPoints.isNotEmpty()) {
                    // The height needs to be explicitly defined to make the map visible
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.LightGray)
                    ) {
                        AndroidView(
                            factory = { context ->
                                MapView(context).apply {
                                    setMultiTouchControls(false)
                                    controller.setZoom(14.0)
                                    isClickable = false

                                    val routePath = Polyline().apply {
                                        setPoints(event.locationPoints)
                                        color = android.graphics.Color.BLUE
                                        width = 5f
                                    }

                                    overlays.add(routePath)

                                    if (event.locationPoints.isNotEmpty()) {
                                        controller.setCenter(event.locationPoints[0])
                                    }
                                }
                            },
                            update = { mapView ->
                                // Simply invalidate the map to force a redraw
                                mapView.invalidate()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text(
                        text = "No route data available for preview",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                if (event.event.comment.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Comments",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = event.event.comment,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}