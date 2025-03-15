package at.co.netconsulting.geotracker.composables

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.data.EditState
import at.co.netconsulting.geotracker.data.LapTimeInfo
import at.co.netconsulting.geotracker.data.SingleEventWithMetric
import at.co.netconsulting.geotracker.data.TotalStatistics
import at.co.netconsulting.geotracker.tools.Tools
import kotlinx.coroutines.delay

@Composable
fun TotalStatisticsPanel(totalStatistics: TotalStatistics) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text("Total distance covered: ${"%.3f".format(totalStatistics.totalDistanceKm)} Km",
            style = MaterialTheme.typography.bodyLarge)

        // Show distance by year if available
        if (totalStatistics.distanceByYear.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Distance by year:", style = MaterialTheme.typography.bodyMedium)

            totalStatistics.distanceByYear.forEach { (year, distance) ->
                Text("$year: ${"%.3f".format(distance)} Km",
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun EventSelectionDialog(
    records: List<SingleEventWithMetric>,
    selectedRecords: List<SingleEventWithMetric>,
    editState: EditState,
    lapTimesMap: Map<Int, List<LapTimeInfo>>,
    onRecordSelected: (SingleEventWithMetric) -> Unit,
    onDelete: (Int) -> Unit,
    onExport: (Int) -> Unit,
    onEdit: (Int, String, String) -> Unit,
    onDeleteAllContent: () -> Unit,
    onExportGPX: () -> Unit,
    onImportGPX: () -> Unit,
    onBackupDatabase: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .width(400.dp)
            .heightIn(max = 600.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query -> searchQuery = query },
                label = { Text("Search event") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val filteredRecords = records.filter {
                val eventNameMatch = it.eventName.contains(searchQuery, ignoreCase = true)
                val eventDateMatch = it.eventDate?.contains(searchQuery, ignoreCase = true) ?: false
                val distanceMatch = it.distance?.let { distance ->
                    "%.3f".format(distance / 1000).contains(searchQuery, ignoreCase = true)
                } ?: false
                eventNameMatch || eventDateMatch || distanceMatch
            }

            // Replace scrolling Column with LazyColumn for better performance
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = filteredRecords,
                    key = { it.eventId }
                ) { record ->
                    EventItemCard(
                        record = record,
                        isSelected = selectedRecords.contains(record),
                        editState = editState,
                        lapTimes = lapTimesMap[record.eventId] ?: emptyList(),
                        onRecordSelected = onRecordSelected,
                        onDelete = onDelete,
                        onExport = onExport,
                        onEdit = onEdit,
                        context = context
                    )
                }
            }

            // Footer actions
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Button(
                    onClick = { onDeleteAllContent() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Delete content of all tables")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onExportGPX() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Export GPX files")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onImportGPX() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Import GPX files")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onBackupDatabase() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Backup database")
                }
            }
        }
    }
}

@Composable
fun EventItemCard(
    record: SingleEventWithMetric,
    isSelected: Boolean,
    editState: EditState,
    lapTimes: List<LapTimeInfo>,
    onRecordSelected: (SingleEventWithMetric) -> Unit,
    onDelete: (Int) -> Unit,
    onExport: (Int) -> Unit,
    onEdit: (Int, String, String) -> Unit,
    context: Context
) {
    // Check if currently recording
    val isCurrentlyRecording = record.eventId == getCurrentlyRecordingEventId(context) &&
            isServiceRunning(context, "at.co.netconsulting.geotracker.service.ForegroundService")

    // Create completely separate state variables with simpler remember dependency
    var editedName by remember { mutableStateOf("") }
    var editedDate by remember { mutableStateOf("") }

    // Local focus manager for handling keyboard focus
    val focusManager = LocalFocusManager.current

    // Focus requesters for the text fields
    val nameFieldFocus = remember { FocusRequester() }
    val dateFieldFocus = remember { FocusRequester() }

    // Initialize edit fields when edit mode is activated
    LaunchedEffect(editState.isEditing, editState.eventId) {
        if (editState.isEditing && editState.eventId == record.eventId) {
            Log.d("EventItemCard", "Edit mode activated for: ${record.eventId}, name: ${editState.currentEventName}")

            // Set the values on our local state
            editedName = editState.currentEventName
            editedDate = editState.currentEventDate

            // Request focus after a short delay to allow the UI to update
            delay(100)
            try {
                nameFieldFocus.requestFocus()
            } catch (e: Exception) {
                Log.e("EventItemCard", "Failed to request focus: ${e.message}")
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(
                enabled = !isCurrentlyRecording && !editState.isEditing,
                onClick = {
                    onRecordSelected(record)
                }
            ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            if (editState.isEditing && editState.eventId == record.eventId) {
                // Edit mode fields
                Text(
                    "Edit Event Details",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // First input field with explicit focus handling
                BasicTextField(
                    value = editedName,
                    onValueChange = {
                        Log.d("EventItemCard", "Name changed to: $it")
                        editedName = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min) // Allow height to expand as needed
                        .focusRequester(nameFieldFocus)
                        .focusable()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { dateFieldFocus.requestFocus() }
                    ),
                    // Add these decorations for better overflow handling
                    visualTransformation = VisualTransformation.None,
                    singleLine = false, // Allow wrapping for long names
                    maxLines = 3, // Allow up to 3 lines for overflow
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Event Name",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Second input field with explicit focus handling
                BasicTextField(
                    value = editedDate,
                    onValueChange = {
                        Log.d("EventItemCard", "Date changed to: $it")
                        editedDate = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(dateFieldFocus)
                        .focusable()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text, // Changed from Number to Text to allow hyphens
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    // Allow wrapping and scrolling for long text
                    visualTransformation = VisualTransformation.None,
                    singleLine = false,
                    maxLines = 1, // Keep to one line for dates
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Event Date (YYYY-MM-DD)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            Log.d("EventItemCard", "Edit cancelled")
                            focusManager.clearFocus()
                            onEdit(-1, "", "")
                        }
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            Log.d("EventItemCard", "Saving with Name: $editedName, Date: $editedDate")
                            focusManager.clearFocus()
                            onEdit(record.eventId, editedName, editedDate)
                        }
                    ) {
                        Text("Save")
                    }
                }
            } else {
                // Normal display mode
                Text(
                    text = "Event date: ${record.eventDate?.takeIf { it.isNotEmpty() } ?: "No date provided"}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Event name: ${record.eventName}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Covered distance: ${"%.3f".format(record.distance?.div(1000) ?: 0.0)} Km",
                    style = MaterialTheme.typography.bodyLarge
                )

                if (isCurrentlyRecording) {
                    Text(
                        text = "⚫ Ongoing Recording",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Use a separate collapsible section for lap times to reduce initial render cost
                if (lapTimes.isNotEmpty()) {
                    var showLapTimes by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { showLapTimes = !showLapTimes },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lap Times",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showLapTimes)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle lap times"
                        )
                    }

                    if (showLapTimes) {
                        LapTimesSection(lapTimes)
                    }
                }

                // Action buttons
                if (!isCurrentlyRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(onClick = { onDelete(record.eventId) }) {
                            Text("Delete")
                        }
                        Button(onClick = { onExport(record.eventId) }) {
                            Text("Export")
                        }
                        Button(
                            onClick = {
                                Log.d("EventItemCard", "Edit button clicked for event ${record.eventId}")
                                onEdit(
                                    record.eventId,
                                    record.eventName,
                                    record.eventDate ?: ""
                                )
                            }
                        ) {
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }
}

// Helper function that takes context as parameter instead of using activity methods directly
fun isServiceRunning(context: Context, serviceName: String): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Integer.MAX_VALUE)
        .any { service ->
            serviceName == service.service.className && service.foreground
        }
}

// Helper function that takes context as parameter instead of using activity methods directly
fun getCurrentlyRecordingEventId(context: Context): Int {
    val sharedPreferences = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
    return if (isServiceRunning(context, "at.co.netconsulting.geotracker.service.ForegroundService")) {
        sharedPreferences.getInt("active_event_id", -1)
    } else {
        // Return the last recorded event ID when not recording
        sharedPreferences.getInt("last_event_id", -1)
    }
}

@Composable
fun LapTimesSection(lapTimes: List<LapTimeInfo>) {
    // Find fastest and slowest completed laps
    val lastLapNumber = lapTimes.maxOfOrNull { it.lapNumber } ?: 0

    val completedLaps = lapTimes.filter { lapTime ->
        lapTime.lapNumber > 0 &&
                lapTime.lapNumber < lastLapNumber &&
                lapTime.timeInMillis > 0 &&
                lapTime.timeInMillis < Long.MAX_VALUE
    }

    val fastestLap = completedLaps.minByOrNull { it.timeInMillis }
    val slowestLap = completedLaps.maxByOrNull { it.timeInMillis }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            Text(
                text = "Lap",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "Time",
                modifier = Modifier.weight(2f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // Use LazyColumn for lap times if there are many
        if (lapTimes.size > 20) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(lapTimes) { lapTime ->
                    LapTimeRow(lapTime, fastestLap, slowestLap, lastLapNumber)
                }
            }
        } else {
            // Use Column for fewer lap times
            lapTimes.forEach { lapTime ->
                LapTimeRow(lapTime, fastestLap, slowestLap, lastLapNumber)
            }
        }
    }
}

@Composable
fun LapTimeRow(
    lapTime: LapTimeInfo,
    fastestLap: LapTimeInfo?,
    slowestLap: LapTimeInfo?,
    lastLapNumber: Int
) {
    val backgroundColor = when {
        lapTime.lapNumber == lastLapNumber -> Color.Transparent // Current lap
        lapTime.timeInMillis <= 0 || lapTime.timeInMillis == Long.MAX_VALUE -> Color.Transparent // Invalid lap
        lapTime == fastestLap -> Color(0xFF90EE90) // Fastest lap
        lapTime == slowestLap -> Color(0xFFF44336) // Slowest lap
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Text(
            text = lapTime.lapNumber.toString(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = Tools().formatTime(lapTime.timeInMillis),
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}