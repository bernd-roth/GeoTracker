package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

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

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var selectedEventId by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<EventWithDetails?>(null) }

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
            text = { Text("Are you sure you want to delete '${eventToDelete?.event?.eventName}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        eventToDelete?.let { event ->
                            coroutineScope.launch {
                                eventsViewModel.deleteEvent(event.event.eventId)
                                showDeleteDialog = false
                                eventToDelete = null
                            }
                        }
                    }
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
        Text(
            text = "Events",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

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
                    EventCard(
                        event = eventWithDetails,
                        selected = selectedEventId == eventWithDetails.event.eventId,
                        onClick = {
                            selectedEventId = if (selectedEventId == eventWithDetails.event.eventId) null else eventWithDetails.event.eventId
                        },
                        onEdit = { onEditEvent(eventWithDetails.event.eventId) },
                        onDelete = {
                            eventToDelete = eventWithDetails
                            showDeleteDialog = true
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
                        }
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
fun EventCard(
    event: EventWithDetails,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
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

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Event",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Event",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
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
                                InfoRow("Distance:", String.format("%.2f km", event.totalDistance / 1000))
                                InfoRow("Avg. Speed:", String.format("%.1f km/h", event.averageSpeed * 3.6))
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

                // Lap information
                Text(
                    text = "Lap Information",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (event.laps.isNotEmpty()) {
                    event.laps.forEachIndexed { index, lapTime ->
                        InfoRow("Lap ${index + 1} (1km):", Tools().formatDuration(lapTime))
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

                if (event.locationPoints.isNotEmpty()) {
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