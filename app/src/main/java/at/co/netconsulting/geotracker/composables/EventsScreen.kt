package at.co.netconsulting.geotracker.composables

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.DisposableEffect
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
import at.co.netconsulting.geotracker.data.EventWithDetails
import at.co.netconsulting.geotracker.data.RouteDisplayData
import at.co.netconsulting.geotracker.data.RouteRerunData
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.tools.GpxImporter
import at.co.netconsulting.geotracker.tools.Tools
import at.co.netconsulting.geotracker.viewmodel.EventsViewModel
import at.co.netconsulting.geotracker.viewmodel.EventsViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
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
    onNavigateToMapWithRouteRerun: (RouteRerunData) -> Unit,
    onNavigateToRouteComparison: (Int, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val eventsViewModel: EventsViewModel = viewModel(
        factory = EventsViewModelFactory(
            FitnessTrackerDatabase.getInstance(context),
            context
        )
    )
    val events by eventsViewModel.filteredEventsWithDetails.collectAsState(initial = emptyList())
    val allEvents by eventsViewModel.eventsWithDetails.collectAsState(initial = emptyList())
    val isLoading by eventsViewModel.isLoading.collectAsState()
    val searchQuery by eventsViewModel.searchQuery.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Observe recording state from ViewModel (managed in viewModelScope to prevent memory leaks)
    val activeEventId by eventsViewModel.activeEventId.collectAsState()
    val isRecording by eventsViewModel.isRecording.collectAsState()

    // State for importing GPX
    var isImporting by remember { mutableStateOf(false) }
    var showImportingDialog by remember { mutableStateOf(false) }

    // State for upload dialog
    var showUploadDialog by remember { mutableStateOf(false) }


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

    // Recording state is now monitored in the ViewModel to prevent memory leaks
    // The ViewModel's viewModelScope ensures the monitoring coroutine is properly cancelled

    // Clear search query when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            eventsViewModel.setSearchQuery("")
        }
    }

    val listState = rememberLazyListState()
    var selectedEventId by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<EventWithDetails?>(null) }

    var showSyncDialog by remember { mutableStateOf(false) }
    var eventToSync by remember { mutableStateOf<EventWithDetails?>(null) }
    var showYearlyStats by remember { mutableStateOf(false) } // State to toggle stats visibility
    var isDateFilterActive by remember { mutableStateOf(false) } // Track if date filter is active

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

    // Sync dialog
    if (showSyncDialog && eventToSync != null) {
        SyncEventDialog(
            event = eventToSync!!,
            onDismiss = {
                showSyncDialog = false
                eventToSync = null
            },
            context = context
        )
    }

    // Upload dialog
    if (showUploadDialog) {
        UploadEventsDialog(
            onDismiss = { showUploadDialog = false }
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with Events title and buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
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

                        // Upload events button
                        IconButton(
                            onClick = { showUploadDialog = true },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload Events",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { eventsViewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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
            }

            // Yearly Stats Overview (conditionally shown)
            if (showYearlyStats) {
                item {
                    YearlyStatsOverview(
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
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

                                Log.d("EventsScreen", "Week selected: Year=$year, Week=$week, " +
                                        "DateRange: $startDateStr to $endDateStr")

                                // Set filter in the ViewModel
                                eventsViewModel.filterByDateRange(startDate = startDateStr, endDate = endDateStr)

                                // Mark filter as active
                                isDateFilterActive = true
                            }
                        }
                    )
                }
            }

            // Show "Clear Filter" button when date filter is active
            if (isDateFilterActive) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Filter Active",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Showing filtered events by week",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        // Clear the date filter
                                        eventsViewModel.filterByDateRange(null, null)
                                        isDateFilterActive = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Show All Events",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Loading state
            if (events.isEmpty() && isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            // Empty state
            else if (events.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
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
                }
            }
            // Event items
            else {
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
                                val isCurrentlySelected = selectedEventId == eventWithDetails.event.eventId
                                if (isCurrentlySelected) {
                                    // Collapsing - just unselect
                                    selectedEventId = null
                                } else {
                                    // Expanding - select and load full details if needed
                                    selectedEventId = eventWithDetails.event.eventId

                                    // Load full details on-demand if not already loaded
                                    if (!eventWithDetails.hasFullDetails) {
                                        coroutineScope.launch {
                                            eventsViewModel.loadFullDetailsForEvent(eventWithDetails.event.eventId)
                                        }
                                    }
                                }
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
                                onNavigateToMapWithRouteRerun(RouteRerunData(locationPoints, true, eventWithDetails.event.eventId, eventWithDetails.totalDistance))
                            },
                            onViewSlopeOnMap = { locationPoints ->
                                onNavigateToMapWithRoute(RouteDisplayData(locationPoints, eventWithDetails.event.eventId, showSlopeColors = true, metrics = eventWithDetails.metrics))
                            },
                            onCompareRoutes = {
                                Log.d("EventsScreen", "Compare Routes clicked for event: ${eventWithDetails.event.eventName} (ID: ${eventWithDetails.event.eventId})")
                                Toast.makeText(context, "Loading route comparison...", Toast.LENGTH_SHORT).show()
                                onNavigateToRouteComparison(eventWithDetails.event.eventId, eventWithDetails.event.eventName)
                            },
                            onSync = {
                                eventToSync = eventWithDetails
                                showSyncDialog = true
                            },
                            canViewOnMap = !isRecordingThisEvent, // Only allow when not recording this event
                            database = FitnessTrackerDatabase.getInstance(context)
                        )
                    }
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
    onCompareRoutes: () -> Unit = {},
    onSync: () -> Unit = {},
    canViewOnMap: Boolean = true,
    database: FitnessTrackerDatabase? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showExportDropdown by remember { mutableStateOf(false) }

    // Helper function to load location points on-demand
    fun loadAndExecute(callback: (List<GeoPoint>) -> Unit) {
        if (database != null && event.locationPointCount > 0) {
            coroutineScope.launch {
                try {
                    val locations = database.locationDao().getLocationsForEvent(event.event.eventId)
                    val geoPoints = locations.map { GeoPoint(it.latitude, it.longitude) }
                    callback(geoPoints)
                } catch (e: Exception) {
                    Log.e("EventCard", "Error loading location points: ${e.message}")
                    Toast.makeText(context, "Error loading route data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
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
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = "FIT",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Export as FIT",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                onClick = {
                                    showExportDropdown = false
                                    coroutineScope.launch {
                                        at.co.netconsulting.geotracker.fit.export(
                                            eventId = event.event.eventId,
                                            contextActivity = context
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // Compare Routes button - only show if event has location data and is GPS-based
                    if (event.locationPointCount > 0 && at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport)) {
                        IconButton(
                            onClick = onCompareRoutes,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompareArrows,
                                contentDescription = "Compare Routes",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    // Sync button - show for all activities (GPS and stationary)
                    // Strava supports weight training and other indoor activities without GPS data
                    IconButton(
                        onClick = onSync,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Sync to Cloud",
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Basic info when collapsed: Date and Sport only
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.event.eventDate,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = event.event.artOfSport,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Expand/Collapse icon
                Icon(
                    imageVector = if (selected) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (selected) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // If event is selected, show all details
            if (selected) {
                Column {
                    // Show loading indicator if full details haven't been loaded yet
                    if (!event.hasFullDetails) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Loading event details...",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                    // Stats section - only show if full details are loaded
                    else if (event.totalDistance > 10 || event.averageSpeed > 0.1) { // Only show if meaningful data exists
                        Spacer(modifier = Modifier.height(8.dp))

                        // Check if this is a stationary activity (no GPS tracking)
                        val isStationaryActivity = !at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport)

                        // First row: Distance and Heart Rate info (or just Heart Rate for stationary activities)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (!isStationaryActivity) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // Left column with distance and speed (only for GPS-based activities)
                                    InfoRow("Distance:", String.format("%.2f km", event.totalDistance / 1000))
                                    InfoRow("", String.format(""))
                                    InfoRow("Avg. Speed:", String.format("%.1f km/h", event.averageSpeed))
                                    InfoRow("Max. Speed:", String.format("%.1f km/h", event.maxSpeed))
                                    InfoRow("", String.format(""))
                                }
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
                                InfoRowWithColor(
                                    label = "Avg HR:",
                                    value = if (event.avgHeartRate > 0) "${event.avgHeartRate} bpm" else "N/A",
                                    textColor = if (event.avgHeartRate > 0) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }

                        // Second row: Elevation (only for GPS-based activities)
                        if (!isStationaryActivity) {
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
                            }
                        }

                        // Third row: Slope information (only for GPS-based activities)
                        if (!isStationaryActivity && (event.averageSlope != 0.0 || event.maxSlope != 0.0 || event.minSlope != 0.0)) {
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
                    } else if (event.hasFullDetails) {
                        // Show a compact message for new events with no metrics yet (only if details are loaded)
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

            // If event is selected and has full details, show additional details beyond stats
            if (selected && event.hasFullDetails) {
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

                // GPS Signal Quality and Satellite info (only for GPS-based activities)
                val isStationaryActivity = !at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport)

                if (!isStationaryActivity) {
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

                // Weather information - Make this section clickable (only for GPS-based activities)
                if (at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport)) {
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

                    if (event.weather != null && !event.weather.temperature.isNaN()) {
                        // Calculate temperature statistics from metrics if available
                        val temperatures = event.metrics.mapNotNull { it.temperature }.filter { !it.isNaN() }

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
                }

                // Barometer Data section - Make this section clickable (only for GPS-based activities)
                if (at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport)) {
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
                }

                // Altitude information (only for GPS-based activities)
                if (at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport)) {
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
                }

                // Slope information section (only for GPS-based activities)
                if (at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport) &&
                    (event.averageSlope != 0.0 || event.maxSlope != 0.0 || event.minSlope != 0.0)) {
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
                        if (canViewOnMap && event.locationPointCount > 0) {
                            Surface(
                                modifier = Modifier
                                    .clickable {
                                        loadAndExecute(onViewSlopeOnMap)
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
                    if (event.locationPointCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Slope Profile Map (tap 'View on Map' to see full)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Map preview not available in list view to save memory",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add slope color legend
                        SlopeColorLegend()
                    }
                }

                // Lap information with highlighting (only for GPS-based activities)
                if (at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport)) {
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
                }

                // Map preview section with enhanced header (only for GPS-based activities)
                if (at.co.netconsulting.geotracker.utils.ActivityTypeUtils.requiresGpsTracking(event.event.artOfSport)) {
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
                        if (canViewOnMap && event.locationPointCount > 0) {
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    loadAndExecute(onViewOnMap)
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

                if (event.locationPointCount > 0) {
                    // MEMORY FIX: Don't render map preview in list view
                    Text(
                        text = "Route with ${event.locationPointCount} GPS points available",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    // Add a hint for the "View on Map" feature
                    if (canViewOnMap) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'View on Map' button above to see the full route",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    } else {
                        Text(
                            text = "No route data available",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }

                    // Route rerun section
                    if (canViewOnMap && event.locationPointCount > 0) {
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
                                    loadAndExecute(onViewOnMapRerun)
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

@Composable
private fun SyncEventDialog(
    event: EventWithDetails,
    onDismiss: () -> Unit,
    context: Context
) {
    val syncManager = remember { at.co.netconsulting.geotracker.sync.ExportSyncManager(context) }
    val authStatus = remember { syncManager.getAuthenticationStatus() }
    val coroutineScope = rememberCoroutineScope()

    // Selected platforms
    var selectedStrava by remember { mutableStateOf(authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.STRAVA] == true) }
    var selectedGarmin by remember { mutableStateOf(authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.GARMIN] == true) }
    var selectedTrainingPeaks by remember { mutableStateOf(authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.TRAINING_PEAKS] == true) }

    // Sync state
    var isSyncing by remember { mutableStateOf(false) }
    var syncResults by remember { mutableStateOf<Map<at.co.netconsulting.geotracker.sync.SyncPlatform, at.co.netconsulting.geotracker.sync.SyncResult>>(emptyMap()) }

    AlertDialog(
        onDismissRequest = { if (!isSyncing) onDismiss() },
        title = { Text("Sync to Cloud") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isSyncing) {
                    // Show sync progress
                    Text("Syncing activity...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (syncResults.isNotEmpty()) {
                    // Show sync results
                    Text("Sync Results:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    syncResults.forEach { (platform, result) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (result is at.co.netconsulting.geotracker.sync.SyncResult.Success)
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.Error,
                                contentDescription = null,
                                tint = if (result is at.co.netconsulting.geotracker.sync.SyncResult.Success)
                                    Color.Green
                                else
                                    Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = platform.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                when (result) {
                                    is at.co.netconsulting.geotracker.sync.SyncResult.Success ->
                                        Text("Success!", style = MaterialTheme.typography.bodySmall, color = Color.Green)
                                    is at.co.netconsulting.geotracker.sync.SyncResult.Failure -> {
                                        // Clean up HTML from error message and make it more readable
                                        val cleanError = result.error
                                            .replace(Regex("<a href='[^']*'>"), "")
                                            .replace("</a>", "")
                                            .replace(Regex("\\.gpx duplicate of"), " is a duplicate of")
                                        Text(cleanError, style = MaterialTheme.typography.bodySmall, color = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Show platform selection
                    Text(
                        text = "Select platforms to sync '${event.event.eventName}':",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Strava checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedStrava,
                            onCheckedChange = { selectedStrava = it },
                            enabled = authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.STRAVA] == true
                        )
                        Text(
                            text = "Strava",
                            modifier = Modifier.weight(1f)
                        )
                        if (authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.STRAVA] == false) {
                            Text(
                                text = "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Garmin checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedGarmin,
                            onCheckedChange = { selectedGarmin = it },
                            enabled = authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.GARMIN] == true
                        )
                        Text(
                            text = "Garmin Connect",
                            modifier = Modifier.weight(1f)
                        )
                        if (authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.GARMIN] == false) {
                            Text(
                                text = "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // TrainingPeaks checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedTrainingPeaks,
                            onCheckedChange = { selectedTrainingPeaks = it },
                            enabled = authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.TRAINING_PEAKS] == true
                        )
                        Text(
                            text = "TrainingPeaks",
                            modifier = Modifier.weight(1f)
                        )
                        if (authStatus[at.co.netconsulting.geotracker.sync.SyncPlatform.TRAINING_PEAKS] == false) {
                            Text(
                                text = "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect accounts in Settings > Export & Sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            if (syncResults.isEmpty()) {
                Button(
                    onClick = {
                        val platforms = mutableListOf<at.co.netconsulting.geotracker.sync.SyncPlatform>()
                        if (selectedStrava) platforms.add(at.co.netconsulting.geotracker.sync.SyncPlatform.STRAVA)
                        if (selectedGarmin) platforms.add(at.co.netconsulting.geotracker.sync.SyncPlatform.GARMIN)
                        if (selectedTrainingPeaks) platforms.add(at.co.netconsulting.geotracker.sync.SyncPlatform.TRAINING_PEAKS)

                        if (platforms.isNotEmpty()) {
                            isSyncing = true
                            coroutineScope.launch {
                                val results = syncManager.syncEvent(event.event.eventId, platforms)
                                syncResults = results
                                isSyncing = false
                            }
                        } else {
                            Toast.makeText(context, "Please select at least one platform", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isSyncing && (selectedStrava || selectedGarmin || selectedTrainingPeaks)
                ) {
                    Text("Sync")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (syncResults.isEmpty()) {
                TextButton(onClick = onDismiss, enabled = !isSyncing) {
                    Text("Cancel")
                }
            }
        }
    )
}