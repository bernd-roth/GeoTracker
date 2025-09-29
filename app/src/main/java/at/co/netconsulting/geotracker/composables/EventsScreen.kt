package at.co.netconsulting.geotracker.composables

import EventWithDetails
import HeartRateGraph
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import at.co.netconsulting.geotracker.YearlyStatisticsActivity
import at.co.netconsulting.geotracker.LapAnalysisActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import at.co.netconsulting.geotracker.data.AltitudeSpeedInfo
import at.co.netconsulting.geotracker.data.RouteDisplayData
import at.co.netconsulting.geotracker.data.RouteRerunData
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.tools.GpxImporter
import at.co.netconsulting.geotracker.tools.Tools
import at.co.netconsulting.geotracker.viewmodel.EventsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onEditEvent: (Int) -> Unit,
    onNavigateToHeartRateDetail: (String, List<Metric>) -> Unit,
    onNavigateToWeatherDetail: (String, List<Metric>) -> Unit,
    onNavigateToBarometerDetail: (String, List<Metric>) -> Unit,
    onNavigateToAltitudeDetail: (String, List<Metric>) -> Unit,
    onNavigateToMapWithRoute: (RouteDisplayData) -> Unit,
    onNavigateToMapWithRouteRerun: (RouteRerunData) -> Unit
) {
    val context = LocalContext.current
    val eventsViewModel = remember { EventsViewModel(FitnessTrackerDatabase.getInstance(context)) }
    val events by eventsViewModel.filteredEventsWithDetails.collectAsState(initial = emptyList())
    val allEvents by eventsViewModel.eventsWithDetails.collectAsState(initial = emptyList())
    val isLoading by eventsViewModel.isLoading.collectAsState()
    val searchQuery by eventsViewModel.searchQuery.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Track both the active event ID and recording state
    var activeEventId by remember { mutableStateOf(-1) }
    var isRecording by remember { mutableStateOf(false) }

    // State for importing GPX
    var isImporting by remember { mutableStateOf(false) }
    var showImportingDialog by remember { mutableStateOf(false) }


    // Result launcher for file picking
    val gpxFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                showImportingDialog = true
                isImporting = true

                coroutineScope.launch {
                    try {
                        val gpxImporter = GpxImporter(context)
                        Log.d("GPX_Import", "Starting import for URI: $uri")

                        val newEventId = gpxImporter.importGpx(uri)
                        Log.d("GPX_Import", "Import completed with result: $newEventId")

                        when {
                            newEventId > 0 -> {
                                // Success
                                eventsViewModel.loadEvents()
                                Toast.makeText(
                                    context,
                                    "GPX file imported successfully! Event ID: $newEventId",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            newEventId == 0 -> {
                                Toast.makeText(
                                    context,
                                    "No valid track data found in GPX file. Please check the file contains GPS coordinates.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            newEventId == -1 -> {
                                Toast.makeText(
                                    context,
                                    "Error processing GPX file. Please check the file format and try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            newEventId == -2 -> {
                                Toast.makeText(
                                    context,
                                    "Invalid GPX file format. Please select a valid GPX file.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            newEventId == -3 -> {
                                Toast.makeText(
                                    context,
                                    "Permission denied. Please grant storage access and try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            else -> {
                                Toast.makeText(
                                    context,
                                    "Unknown error occurred during import (code: $newEventId)",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e("GPX_Import", "Permission denied: ${e.message}")
                        Toast.makeText(context, "Permission denied reading file", Toast.LENGTH_LONG).show()
                    } catch (e: java.io.FileNotFoundException) {
                        Log.e("GPX_Import", "File not found: ${e.message}")
                        Toast.makeText(context, "File not found or cannot be accessed", Toast.LENGTH_LONG).show()
                    } catch (e: org.xmlpull.v1.XmlPullParserException) {
                        Log.e("GPX_Import", "XML parsing error: ${e.message}")
                        Toast.makeText(context, "Invalid GPX file format - XML parsing failed", Toast.LENGTH_LONG).show()
                    } catch (e: OutOfMemoryError) {
                        Log.e("GPX_Import", "Out of memory: ${e.message}")
                        Toast.makeText(context, "GPX file too large. Please try with a smaller file.", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Log.e("GPX_Import", "Unexpected error: ${e.message}", e)
                        Toast.makeText(context, "Unexpected error: ${e.message?.take(100) ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    } finally {
                        isImporting = false
                        showImportingDialog = false
                    }
                }
            } else {
                Toast.makeText(context, "No file selected", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d("GPX_Import", "File selection cancelled or failed")
        }
    }

    // Function to launch file picker
    val launchGpxFilePicker = {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/gpx+xml", "text/xml", "*/*"))
        }
        gpxFileLauncher.launch(intent)
    }

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
    var selectedEventId by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<EventWithDetails?>(null) }
    var showYearlyStats by remember { mutableStateOf(false) } // State to toggle stats visibility

    // Import progress dialog
    if (showImportingDialog) {
        AlertDialog(
            onDismissRequest = { /* Dialog cannot be dismissed while importing */ },
            title = { Text("Importing GPX") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Importing GPX file, please wait...")
                }
            },
            confirmButton = { /* No buttons while importing */ }
        )
    }

    // Parallel loading for better performance
    LaunchedEffect(Unit) {
        // Launch events loading and stats loading in parallel
        coroutineScope.launch {
            // Load events immediately for quick UI response
            eventsViewModel.loadEvents()
        }
        // Stats will be loaded separately when shown
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


    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = launchGpxFilePicker,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Import GPX",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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

                Row {
                    // Detailed Statistics button
                    TextButton(
                        onClick = {
                            val intent = Intent(context, YearlyStatisticsActivity::class.java)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Detailed Stats")
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Detailed Statistics",
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    // Toggle button for quick stats
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No activities found" else "No events match your search",
                            fontSize = 18.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        if (searchQuery.isEmpty()) {
                            // Show different messages based on whether this might be after a database upgrade
                            Text(
                                text = "This could be because:",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Text(
                                text = "• No activities have been recorded yet\n" +
                                      "• Database upgrade may have affected data\n" +
                                      "• Try importing a GPX file to get started",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            Text(
                                text = "Tip: Use the '+' button to import a GPX file",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
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
                                // Export handled directly in EventCard dropdown
                            },
                            onImport = {
                                // Launch the GPX file picker
                                launchGpxFilePicker()
                            },
                            isCurrentlyRecording = isRecordingThisEvent,
                            onHeartRateClick = {
                                onNavigateToHeartRateDetail(eventWithDetails.event.eventName, eventWithDetails.metrics)
                            },
                            onWeatherClick = {
                                onNavigateToWeatherDetail(eventWithDetails.event.eventName, eventWithDetails.metrics)
                            },
                            onBarometerClick = {
                                onNavigateToBarometerDetail(eventWithDetails.event.eventName, eventWithDetails.metrics)
                            },
                            onAltitudeClick = {
                                onNavigateToAltitudeDetail(eventWithDetails.event.eventName, eventWithDetails.metrics)
                            },
                            // Pass the map navigation callback and recording state
                            onViewOnMap = { locationPoints ->
                                onNavigateToMapWithRoute(RouteDisplayData(locationPoints, eventWithDetails.event.eventId))
                            },
                            onViewOnMapRerun = { locationPoints ->
                                onNavigateToMapWithRouteRerun(RouteRerunData(locationPoints, true, eventWithDetails.event.eventId))
                            },
                            onViewSlopeOnMap = { locationPoints ->
                                onNavigateToMapWithRoute(RouteDisplayData(locationPoints, eventWithDetails.event.eventId, showSlopeColors = true, metrics = eventWithDetails.metrics))
                            },
                            canViewOnMap = !isRecordingThisEvent // Only allow when not recording this event
                        )
                    }

                    // Show loading indicator at bottom when loading more
                    if (isLoading && events.isNotEmpty()) {
                        item {
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
    onImport: () -> Unit,
    isCurrentlyRecording: Boolean = false,
    onHeartRateClick: () -> Unit = {},
    onWeatherClick: () -> Unit = {},
    onBarometerClick: () -> Unit = {},
    onAltitudeClick: () -> Unit = {},
    onViewOnMap: (List<GeoPoint>) -> Unit = {},
    onViewOnMapRerun: (List<GeoPoint>) -> Unit = {},
    onViewSlopeOnMap: (List<GeoPoint>) -> Unit = {},
    canViewOnMap: Boolean = true
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showExportDropdown by remember { mutableStateOf(false) }
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
                    // Export button with dropdown
                    Box {
                        IconButton(
                            onClick = { showExportDropdown = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }

                        DropdownMenu(
                            expanded = showExportDropdown,
                            onDismissRequest = { showExportDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = "GPX",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Export as GPX",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                onClick = {
                                    showExportDropdown = false
                                    coroutineScope.launch {
                                        at.co.netconsulting.geotracker.gpx.export(
                                            eventId = event.event.eventId,
                                            contextActivity = context
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MyLocation,
                                            contentDescription = "KML",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Export as KML",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                onClick = {
                                    showExportDropdown = false
                                    coroutineScope.launch {
                                        at.co.netconsulting.geotracker.kml.export(
                                            eventId = event.event.eventId,
                                            contextActivity = context
                                        )
                                    }
                                }
                            )
                        }
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

                        // First row: Distance and Heart Rate info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Left column with distance and speed
                                InfoRow("Distance:", String.format("%.2f km", event.totalDistance / 1000))
                                InfoRow("Avg. Speed:", String.format("%.1f km/h", event.averageSpeed))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                // Right column with heart rate info
                                InfoRowWithColor(
                                    label = "Min HR:",
                                    value = if (event.minHeartRate > 0) "${event.minHeartRate} bpm" else "N/A",
                                    textColor = if (event.minHeartRate > 0) Color.Blue else Color.Gray
                                )
                                InfoRowWithColor(
                                    label = "Max HR:",
                                    value = if (event.maxHeartRate > 0) "${event.maxHeartRate} bpm" else "N/A",
                                    textColor = if (event.maxHeartRate > 0) Color.Red else Color.Gray
                                )
                            }
                        }

                        // Second row: Elevation and Avg Heart Rate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Only show elevation gain if it's greater than 0
                                val elevationGain = event.maxElevationGain.toInt()
                                Log.d("TAG_EVENT_ID", "Event id: ${event.event.eventId}")
                                Log.d("TAG_MAX_ELEVATION_GAIN", "Max. Elevation Gain: $elevationGain")
                                if (elevationGain > 0) {
                                    InfoRow("Max. Elevation Gain:", "$elevationGain m")
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                InfoRowWithColor(
                                    label = "Avg HR:",
                                    value = if (event.avgHeartRate > 0) "${event.avgHeartRate} bpm" else "N/A",
                                    textColor = if (event.avgHeartRate > 0) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }

                        // Third row: Slope information
                        if (event.averageSlope != 0.0 || event.maxSlope != 0.0 || event.minSlope != 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    InfoRowWithColor(
                                        label = "Avg Slope:",
                                        value = String.format("%.1f%%", event.averageSlope),
                                        textColor = when {
                                            event.averageSlope > 5 -> Color.Red
                                            event.averageSlope > 2 -> Color(0xFFFF9800) // Orange
                                            event.averageSlope > -2 -> MaterialTheme.colorScheme.primary
                                            event.averageSlope > -5 -> Color(0xFFFF9800) // Orange
                                            else -> Color.Red
                                        }
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    InfoRowWithColor(
                                        label = "Max Slope:",
                                        value = String.format("%.1f%%", event.maxSlope),
                                        textColor = when {
                                            event.maxSlope > 15 -> Color.Red
                                            event.maxSlope > 8 -> Color(0xFFFF9800) // Orange
                                            else -> Color(0,139,0)
                                        }
                                    )
                                }
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
                // Minimized space here - changed to 4.dp
                Spacer(modifier = Modifier.height(4.dp))

                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                // Minimized space here - changed to 4.dp
                Spacer(modifier = Modifier.height(4.dp))

                // Event Times
                Text(
                    text = "Event Times",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

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
                        }
                    }
                } else {
                    Text(
                        text = "No time data available",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                // GPS Signal Quality and Satellite info
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "GPS Signal Quality",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (event.maxSatellites > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoRowWithColor(
                                label = "Min Satellites:",
                                value = "${event.minSatellites}",
                                textColor = when {
                                    event.minSatellites >= 8 -> Color(0,139,0)
                                    event.minSatellites >= 4 -> MaterialTheme.colorScheme.primary
                                    else -> Color.Red
                                }
                            )
                            InfoRowWithColor(
                                label = "Avg Satellites:",
                                value = "${event.avgSatellites}",
                                textColor = when {
                                    event.avgSatellites >= 8 -> Color(0,139,0)
                                    event.avgSatellites >= 4 -> MaterialTheme.colorScheme.primary
                                    else -> Color.Red
                                }
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            InfoRowWithColor(
                                label = "Max Satellites:",
                                value = "${event.maxSatellites}",
                                textColor = when {
                                    event.maxSatellites >= 8 -> Color(0,139,0)
                                    event.maxSatellites >= 4 -> MaterialTheme.colorScheme.primary
                                    else -> Color.Red
                                }
                            )

                            // Signal quality indicator
                            val signalQuality = when {
                                event.avgSatellites >= 8 -> "Excellent"
                                event.avgSatellites >= 6 -> "Good"
                                event.avgSatellites >= 4 -> "Fair"
                                else -> "Poor"
                            }

                            InfoRowWithColor(
                                label = "Signal Quality:",
                                value = signalQuality,
                                textColor = when {
                                    event.avgSatellites >= 8 -> Color(0,139,0)
                                    event.avgSatellites >= 4 -> MaterialTheme.colorScheme.primary
                                    else -> Color.Red
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No GPS signal data available",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                // Heart Rate Details Section - Made clickable
                Spacer(modifier = Modifier.height(4.dp))

                // Make the heart rate section clickable
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (event.metrics.any { it.heartRate > 0 }) {
                                Modifier.clickable { onHeartRateClick() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Heart Rate Details",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Add a visual indicator that this section is clickable
                        if (event.metrics.any { it.heartRate > 0 }) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "View detailed heart rate analysis",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (event.minHeartRate > 0 || event.maxHeartRate > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                InfoRowWithColor(
                                    label = "Min Heart Rate:",
                                    value = if (event.minHeartRate > 0) "${event.minHeartRate} bpm" else "N/A",
                                    textColor = if (event.minHeartRate > 0) Color.Blue else Color.Gray
                                )
                                InfoRowWithColor(
                                    label = "Avg Heart Rate:",
                                    value = if (event.avgHeartRate > 0) "${event.avgHeartRate} bpm" else "N/A",
                                    textColor = if (event.avgHeartRate > 0) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                InfoRowWithColor(
                                    label = "Max Heart Rate:",
                                    value = if (event.maxHeartRate > 0) "${event.maxHeartRate} bpm" else "N/A",
                                    textColor = if (event.maxHeartRate > 0) Color.Red else Color.Gray
                                )

                                // Show heart rate device if available
                                if (event.heartRateDevice.isNotEmpty() && event.heartRateDevice != "None") {
                                    InfoRow("Device:", event.heartRateDevice)
                                }
                            }
                        }
                        //HeartRateGraph
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF5F5F5))
                                .padding(8.dp)
                        ) {
                            HeartRateGraph(
                                metrics = event.metrics,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Add a hint text to indicate it's clickable
                        if (event.metrics.any { it.heartRate > 0 }) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap for detailed heart rate analysis",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "No heart rate data available",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Weather information - Make this section clickable
                Spacer(modifier = Modifier.height(4.dp))

                // Check if we have temperature data in metrics for consistent behavior
                val hasTemperatureData = event.metrics.any { it.temperature != null && it.temperature!! > 0f }

                // Make the weather section clickable similar to heart rate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (hasTemperatureData) {
                                Modifier.clickable { onWeatherClick() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weather Conditions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Add a visual indicator that this section is clickable
                        if (hasTemperatureData) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "View detailed weather analysis",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (event.weather != null && event.weather.temperature > 0f) {
                        // Calculate temperature statistics from metrics if available
                        val temperatures = event.metrics.mapNotNull { it.temperature }.filter { it > 0f }

                        if (temperatures.isNotEmpty() && temperatures.size > 1) {
                            // Show min/max/avg only if we have multiple temperature readings
                            val maxTemp = temperatures.maxOrNull() ?: event.weather.temperature
                            val minTemp = temperatures.minOrNull() ?: event.weather.temperature
                            val avgTemp = temperatures.average().toFloat()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    InfoRowWithColor(
                                        label = "↑ Max. Temperature:",
                                        value = String.format("%.1f°C", maxTemp),
                                        textColor = Color.Red
                                    )
                                    InfoRowWithColor(
                                        label = "⌀ Avg. Temperature:",
                                        value = String.format("%.1f°C", avgTemp),
                                        textColor = MaterialTheme.colorScheme.primary
                                    )
                                    InfoRowWithColor(
                                        label = "↓ Min. Temperature:",
                                        value = String.format("%.1f°C", minTemp),
                                        textColor = Color.Blue
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    InfoRow("Wind Speed:", "${event.weather.windSpeed} Km/h")
                                    InfoRow("Wind Direction:", event.weather.windDirection)
                                    InfoRow("Humidity:", "${event.weather.relativeHumidity}%")
                                }
                            }
                        } else {
                            // Show single temperature reading if we only have one value
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    InfoRow("Temperature:", String.format("%.1f°C", event.weather.temperature))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    InfoRow("Wind Speed:", "${event.weather.windSpeed} Km/h")
                                    InfoRow("Wind Direction:", event.weather.windDirection)
                                    InfoRow("Humidity:", "${event.weather.relativeHumidity}%")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No weather data available",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }

                    // Add a hint text to indicate it's clickable - moved outside the weather check
                    // and use the same condition as the clickable modifier
                    if (hasTemperatureData) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap for detailed weather conditions analysis",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Barometer Data section - Make this section clickable
                Spacer(modifier = Modifier.height(4.dp))

                // Check if we have pressure data in metrics for consistent behavior
                val hasPressureData = event.metrics.any { it.pressure != null && it.pressure!! > 0f }

                // Make the barometer section clickable similar to heart rate and weather
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (hasPressureData) {
                                Modifier.clickable { onBarometerClick() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Barometer Data",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Add a visual indicator that this section is clickable
                        if (hasPressureData) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "View detailed barometer analysis",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Calculate pressure statistics from metrics
                    val pressureReadings = event.metrics.mapNotNull { it.pressure }.filter { it > 0f }
                    val pressureAccuracyReadings = event.metrics.mapNotNull { it.pressureAccuracy }
                    val altitudeFromPressureReadings = event.metrics.mapNotNull { it.altitudeFromPressure }.filter { it != 0f }
                    val seaLevelPressureReadings = event.metrics.mapNotNull { it.seaLevelPressure }.filter { it > 0f }

                    if (pressureReadings.isNotEmpty()) {
                        val minPressure = pressureReadings.minOrNull() ?: 0f
                        val maxPressure = pressureReadings.maxOrNull() ?: 0f
                        val avgPressure = pressureReadings.average().toFloat()

                        // Get the most common accuracy level
                        val avgAccuracy = if (pressureAccuracyReadings.isNotEmpty()) {
                            pressureAccuracyReadings.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
                        } else 0

                        val avgSeaLevelPressure = if (seaLevelPressureReadings.isNotEmpty()) {
                            seaLevelPressureReadings.average().toFloat()
                        } else 1013.25f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                InfoRowWithColor(
                                    label = "↑ Max Pressure:",
                                    value = String.format("%.1f hPa", maxPressure),
                                    textColor = Color.Red
                                )
                                InfoRowWithColor(
                                    label = "⌀ Avg Pressure:",
                                    value = String.format("%.1f hPa", avgPressure),
                                    textColor = MaterialTheme.colorScheme.primary
                                )
                                InfoRowWithColor(
                                    label = "↓ Min Pressure:",
                                    value = String.format("%.1f hPa", minPressure),
                                    textColor = Color.Blue
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                // Pressure accuracy with color coding
                                InfoRowWithColor(
                                    label = "Accuracy:",
                                    value = when (avgAccuracy) {
                                        3 -> "High"
                                        2 -> "Medium"
                                        1 -> "Low"
                                        0 -> "Unreliable"
                                        else -> "Unknown"
                                    },
                                    textColor = when (avgAccuracy) {
                                        3 -> Color(0,139,0)
                                        2 -> MaterialTheme.colorScheme.primary
                                        1 -> Color(0xFFFF9800) // Orange
                                        else -> Color.Red
                                    }
                                )

                                InfoRow(
                                    label = "Sea Level Ref:",
                                    value = String.format("%.1f hPa", avgSeaLevelPressure)
                                )

                                // Show altitude comparison if we have both GPS and pressure altitude
                                if (altitudeFromPressureReadings.isNotEmpty() && event.maxElevation > 0) {
                                    val avgPressureAltitude = altitudeFromPressureReadings.average().toFloat()
                                    val avgGpsAltitude = (event.maxElevation + event.minElevation) / 2
                                    val altitudeDifference = avgPressureAltitude - avgGpsAltitude

                                    InfoRowWithColor(
                                        label = "Alt. Difference:",
                                        value = String.format("%.1f m", altitudeDifference),
                                        textColor = when {
                                            kotlin.math.abs(altitudeDifference) < 5 -> Color(0,139,0)
                                            kotlin.math.abs(altitudeDifference) < 15 -> MaterialTheme.colorScheme.primary
                                            else -> Color.Red
                                        }
                                    )
                                }
                            }
                        }

                        // Add pressure trend information if we have enough data points
                        if (pressureReadings.size > 10) {
                            Spacer(modifier = Modifier.height(4.dp))

                            // Calculate pressure trend (rising/falling/stable)
                            val firstHalfAvg = pressureReadings.take(pressureReadings.size / 2).average()
                            val secondHalfAvg = pressureReadings.drop(pressureReadings.size / 2).average()
                            val pressureChange = secondHalfAvg - firstHalfAvg

                            val trendText = when {
                                pressureChange > 1.0 -> "Rising (${String.format("%.1f", pressureChange)} hPa)"
                                pressureChange < -1.0 -> "Falling (${String.format("%.1f", pressureChange)} hPa)"
                                else -> "Stable (${String.format("%.1f", pressureChange)} hPa)"
                            }

                            val trendColor = when {
                                pressureChange > 1.0 -> Color(0,139,0)
                                pressureChange < -1.0 -> Color.Red
                                else -> MaterialTheme.colorScheme.primary
                            }

                            InfoRowWithColor(
                                label = "Pressure Trend:",
                                value = trendText,
                                textColor = trendColor
                            )
                        }

                        // Barometric altitude graph (if we have pressure altitude data)
                        if (altitudeFromPressureReadings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Barometric Altitude Profile",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF5F5F5))
                                    .padding(8.dp)
                            ) {
                                BarometricAltitudeGraph(
                                    metrics = event.metrics,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No barometer data available",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }

                    if (hasPressureData) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap for detailed barometer analysis",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Altitude information
                Spacer(modifier = Modifier.height(4.dp))

                // Check if we have elevation data
                val hasElevationData = event.metrics.any { it.elevation > 0 }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (hasElevationData) {
                                Modifier.clickable { onAltitudeClick() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Altitude",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Add a visual indicator that this section is clickable
                        if (hasElevationData) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "View detailed altitude analysis",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (event.maxElevation > 0 || event.minElevation > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                InfoRow("Max. Altitude:", String.format("%.1f m", event.maxElevation))
                                InfoRow("Min. Altitude:", String.format("%.1f m", event.minElevation))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                InfoRow("Acc. Elevation Gain:", String.format("%.1f m", event.elevationGain))
                                InfoRow("Acc. Elevation Loss:", String.format("%.1f m", event.elevationLoss))
                            }
                        }

                        // Altitude graph
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF5F5F5))
                                .padding(8.dp)
                        ) {
                            AltitudeGraph(
                                metrics = event.metrics,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Add a hint text to indicate it's clickable
                        if (hasElevationData) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap for detailed altitude analysis",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "No altitude data available",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Slope information section
                if (event.averageSlope != 0.0 || event.maxSlope != 0.0 || event.minSlope != 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Enhanced Slope Analysis header with map navigation option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Slope Analysis",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // "View on Map" button for slope analysis - only show when not recording and has location data
                        if (canViewOnMap && event.locationPoints.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .clickable {
                                        onViewSlopeOnMap(event.locationPoints)
                                    }
                                    .padding(4.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "View Slope on Map",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "View on Map",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoRowWithColor(
                                label = "Average Slope:",
                                value = String.format("%.2f%%", event.averageSlope),
                                textColor = when {
                                    event.averageSlope > 5 -> Color.Red
                                    event.averageSlope > 2 -> Color(0xFFFF9800) // Orange
                                    event.averageSlope > -2 -> MaterialTheme.colorScheme.primary
                                    event.averageSlope > -5 -> Color(0xFFFF9800) // Orange
                                    else -> Color.Red
                                }
                            )
                            InfoRowWithColor(
                                label = "Min Slope:",
                                value = String.format("%.2f%%", event.minSlope),
                                textColor = when {
                                    event.minSlope < -15 -> Color.Red
                                    event.minSlope < -8 -> Color(0xFFFF9800) // Orange
                                    else -> Color.Blue
                                }
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            InfoRowWithColor(
                                label = "Max Slope:",
                                value = String.format("%.2f%%", event.maxSlope),
                                textColor = when {
                                    event.maxSlope > 15 -> Color.Red
                                    event.maxSlope > 8 -> Color(0xFFFF9800) // Orange
                                    else -> Color(0,139,0)
                                }
                            )

                            // Add slope category
                            val slopeCategory = when {
                                event.averageSlope > 10 -> "Very Steep"
                                event.averageSlope > 5 -> "Steep"
                                event.averageSlope > 2 -> "Moderate"
                                event.averageSlope > -2 -> "Flat"
                                event.averageSlope > -5 -> "Gentle Decline"
                                else -> "Steep Decline"
                            }
                            InfoRow("Terrain:", slopeCategory)
                        }
                    }

                    // Slope visualization map
                    if (event.locationPoints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Slope Profile Map",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.LightGray)
                        ) {
                            SlopeColoredMap(
                                metrics = event.metrics,
                                locationPoints = event.locationPoints,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add slope color legend
                        SlopeColorLegend()
                    }
                }

                // Lap information with highlighting
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lap Information",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (event.laps.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val intent = Intent(context, LapAnalysisActivity::class.java).apply {
                                    putExtra("EVENT_ID", event.event.eventId)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text(
                                text = "Detailed Analysis",
                                fontSize = 12.sp,
                                color = Color(0xFF6650a4)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

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
                            lapTime == fastestLapTime && !isLastAndIncomplete -> Color(0,139,0) // Fastest lap in green
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

                // Map preview section with enhanced header
                Spacer(modifier = Modifier.height(4.dp))

                // Enhanced Route Preview header with map navigation option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Route Preview",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // "View on Map" button - only show when not recording and has location data
                    if (canViewOnMap && event.locationPoints.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    onViewOnMap(event.locationPoints)
                                }
                                .padding(4.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "View on Map",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "View on Map",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (event.locationPoints.isNotEmpty()) {
                    // The height needs to be explicitly defined to make the map visible
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp) // slightly reduced height
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.LightGray)
                    ) {
                        SlopeColoredMap(
                            metrics = event.metrics,
                            locationPoints = event.locationPoints,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Add a hint for the "View on Map" feature
                    if (canViewOnMap) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'View on Map' to see the full route",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(
                        text = "No route data available for preview",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                // Route rerun section
                if (canViewOnMap && event.locationPoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Route Rerun",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onViewOnMapRerun(event.locationPoints)
                            }
                            .padding(4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Rerun Route",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Rerun Route on Map",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Animated playback: 1 second per kilometer",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                if (event.event.comment.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Comments",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = event.event.comment,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactLegendItem(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp, 3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}