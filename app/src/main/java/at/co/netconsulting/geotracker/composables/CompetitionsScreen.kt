package at.co.netconsulting.geotracker.composables

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.PlannedEvent
import at.co.netconsulting.geotracker.reminder.ReminderManager
import at.co.netconsulting.geotracker.service.PlannedEventsNetworkManager
import at.co.netconsulting.geotracker.tools.AlarmPermissionHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { FitnessTrackerDatabase.getInstance(context) }
    val reminderManager = remember { ReminderManager(context) }
    val networkManager = remember { PlannedEventsNetworkManager(context) }

    // Get current user ID
    val currentUserId = remember {
        context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            .getInt("current_user_id", 1)
    }

    // State variables
    var showAddForm by remember { mutableStateOf(false) }
    var showCompetitionsList by remember { mutableStateOf(false) } // ✅ Starts collapsed
    var searchQuery by remember { mutableStateOf("") }
    var competitions by remember { mutableStateOf<List<PlannedEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var editingCompetition by remember { mutableStateOf<PlannedEvent?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Sync state variables
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Form state variables
    var competitionName by remember { mutableStateOf("") }
    var competitionDate by remember { mutableStateOf("") }
    var competitionCountry by remember { mutableStateOf("") }
    var competitionCity by remember { mutableStateOf("") }
    var competitionType by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var isEnteredAndFinished by remember { mutableStateOf(false) }
    var reminderDateTime by remember { mutableStateOf("") }
    var isReminderActive by remember { mutableStateOf(false) }
    var isRecurring by remember { mutableStateOf(false) }
    var recurringType by remember { mutableStateOf("daily") }
    var recurringInterval by remember { mutableStateOf(1) }
    var recurringEndDate by remember { mutableStateOf("") }
    var recurringDaysOfWeek by remember { mutableStateOf("") }

    // UI state for date/time pickers
    var reminderDateFormatted by remember { mutableStateOf("") }
    var reminderTimeFormatted by remember { mutableStateOf("") }
    var recurringEndDateFormatted by remember { mutableStateOf("") }

    // Days of week selection (for weekly recurring)
    var selectedDaysOfWeek by remember { mutableStateOf(setOf<Int>()) }

    // ✅ Load competitions when screen appears or when list is shown - WITH ERROR HANDLING
    LaunchedEffect(showCompetitionsList, currentUserId) {
        if (showCompetitionsList) {
            try {
                Log.d("CompetitionsScreen", "Loading competitions for user: $currentUserId")

                // Test database schema validation before proceeding
                try {
                    // This will trigger Room's schema validation
                    val testQuery = database.plannedEventDao().getAllPlannedEventsForUser(currentUserId)
                    Log.d("CompetitionsScreen", "Database schema validation passed")
                } catch (dbError: Exception) {
                    Log.e("CompetitionsScreen", "Database schema validation failed", dbError)

                    when {
                        dbError.message?.contains("Migration didn't properly handle") == true -> {
                            errorMessage = "Database schema mismatch detected. Please restart the app to apply updates."
                            return@LaunchedEffect
                        }
                        dbError.message?.contains("no such table") == true -> {
                            errorMessage = "Database table missing. Please restart the app to recreate tables."
                            return@LaunchedEffect
                        }
                        dbError.message?.contains("lap_times") == true -> {
                            errorMessage = "Lap times table schema issue detected. App update required - please restart the app."
                            return@LaunchedEffect
                        }
                        dbError is SQLiteException -> {
                            errorMessage = "Database error: ${dbError.message}. Please restart the app."
                            return@LaunchedEffect
                        }
                        else -> {
                            Log.e("CompetitionsScreen", "Unknown database error", dbError)
                            errorMessage = "Database initialization failed. Please restart the app."
                            return@LaunchedEffect
                        }
                    }
                }

                // If we get here, database is working properly
                loadCompetitions(database, currentUserId, searchQuery) { loadedCompetitions ->
                    competitions = loadedCompetitions
                    Log.d("CompetitionsScreen", "Loaded ${loadedCompetitions.size} competitions")
                }

            } catch (e: Exception) {
                Log.e("CompetitionsScreen", "Error in LaunchedEffect loading competitions", e)
                errorMessage = when {
                    e.message?.contains("Migration") == true ->
                        "Database migration error. Please restart the app to complete the update."
                    e.message?.contains("no such table") == true ->
                        "Database table error. Please restart the app to recreate the database."
                    e.message?.contains("FOREIGN KEY constraint failed") == true ->
                        "Database constraint error. Some data may be corrupted."
                    else -> "Failed to load competitions: ${e.message}"
                }
                competitions = emptyList()
            }
        }
    }

    // ✅ Search effect - WITH ERROR HANDLING
    LaunchedEffect(searchQuery) {
        if (showCompetitionsList) {
            try {
                if (searchQuery.isNotEmpty()) {
                    Log.d("CompetitionsScreen", "Searching for: '$searchQuery'")
                    loadCompetitions(database, currentUserId, searchQuery) { loadedCompetitions ->
                        competitions = loadedCompetitions
                        Log.d("CompetitionsScreen", "Search found ${loadedCompetitions.size} competitions")
                    }
                } else {
                    Log.d("CompetitionsScreen", "Loading all competitions")
                    loadAllCompetitions(database, currentUserId) { loadedCompetitions ->
                        competitions = loadedCompetitions
                        Log.d("CompetitionsScreen", "Loaded all ${loadedCompetitions.size} competitions")
                    }
                }
            } catch (e: Exception) {
                Log.e("CompetitionsScreen", "Error in search LaunchedEffect", e)
                when {
                    e.message?.contains("Migration") == true -> {
                        errorMessage = "Database needs updating. Please restart the app."
                    }
                    e.message?.contains("lap_times") == true -> {
                        errorMessage = "Database schema issue detected. Please restart the app to fix."
                    }
                    else -> {
                        errorMessage = "Search error: ${e.message}"
                    }
                }
                competitions = emptyList()
            }
        }
    }

    // Clear form function
    fun clearForm() {
        competitionName = ""
        competitionDate = ""
        competitionCountry = ""
        competitionCity = ""
        competitionType = ""
        website = ""
        comment = ""
        isEnteredAndFinished = false
        reminderDateTime = ""
        isReminderActive = false
        isRecurring = false
        recurringType = "daily"
        recurringInterval = 1
        recurringEndDate = ""
        recurringDaysOfWeek = ""
        reminderDateFormatted = ""
        reminderTimeFormatted = ""
        recurringEndDateFormatted = ""
        selectedDaysOfWeek = setOf()
        editingCompetition = null
        errorMessage = null
    }

    // ✅ Save competition function - WITH ENHANCED ERROR HANDLING
    fun saveCompetition() {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                Log.d("CompetitionsScreen", "Saving competition: $competitionName")

                val plannedEvent = if (editingCompetition != null) {
                    editingCompetition!!.copy(
                        plannedEventName = competitionName,
                        plannedEventDate = competitionDate,
                        plannedEventCountry = competitionCountry,
                        plannedEventCity = competitionCity,
                        plannedEventType = competitionType,
                        website = website,
                        comment = comment,
                        isEnteredAndFinished = isEnteredAndFinished,
                        reminderDateTime = reminderDateTime,
                        isReminderActive = isReminderActive,
                        isRecurring = isRecurring,
                        recurringType = recurringType,
                        recurringInterval = recurringInterval,
                        recurringEndDate = recurringEndDate,
                        recurringDaysOfWeek = if (recurringType == "weekly") {
                            selectedDaysOfWeek.joinToString(",")
                        } else ""
                    )
                } else {
                    PlannedEvent(
                        userId = currentUserId,
                        plannedEventName = competitionName,
                        plannedEventDate = competitionDate,
                        plannedEventCountry = competitionCountry,
                        plannedEventCity = competitionCity,
                        plannedEventType = competitionType,
                        website = website,
                        comment = comment,
                        isEnteredAndFinished = isEnteredAndFinished,
                        reminderDateTime = reminderDateTime,
                        isReminderActive = isReminderActive,
                        isRecurring = isRecurring,
                        recurringType = recurringType,
                        recurringInterval = recurringInterval,
                        recurringEndDate = recurringEndDate,
                        recurringDaysOfWeek = if (recurringType == "weekly") {
                            selectedDaysOfWeek.joinToString(",")
                        } else "",
                        plannedLatitude = null,
                        plannedLongitude = null
                    )
                }

                val savedEventId = try {
                    if (editingCompetition != null) {
                        database.plannedEventDao().updatePlannedEvent(plannedEvent)
                        plannedEvent.plannedEventId
                    } else {
                        database.plannedEventDao().insertPlannedEvent(plannedEvent).toInt()
                    }
                } catch (e: Exception) {
                    Log.e("CompetitionsScreen", "Error saving competition to database", e)
                    errorMessage = "Failed to save competition: ${e.message}"
                    isLoading = false
                    return@launch
                }

                Log.d("CompetitionsScreen", "Competition saved with ID: $savedEventId")

                // Handle reminder scheduling
                if (isReminderActive && reminderDateTime.isNotEmpty()) {
                    try {
                        if (AlarmPermissionHelper.checkExactAlarmPermission(context)) {
                            val savedEvent = plannedEvent.copy(plannedEventId = savedEventId)
                            reminderManager.updateReminder(savedEvent)
                            Log.d("CompetitionsScreen", "Reminder set for competition: $competitionName")
                        } else {
                            AlarmPermissionHelper.requestExactAlarmPermission(context as ComponentActivity) { hasPermission ->
                                if (hasPermission) {
                                    val savedEvent = plannedEvent.copy(plannedEventId = savedEventId)
                                    reminderManager.updateReminder(savedEvent)
                                } else {
                                    Log.w("CompetitionsScreen", "Exact alarm permission denied, cannot set reminder")
                                    errorMessage = "Unable to set reminder - permission denied"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CompetitionsScreen", "Error setting up reminder", e)
                        errorMessage = "Competition saved but reminder setup failed: ${e.message}"
                    }
                } else if (editingCompetition != null) {
                    try {
                        reminderManager.cancelReminder(savedEventId)
                        Log.d("CompetitionsScreen", "Reminder cancelled for competition: $competitionName")
                    } catch (e: Exception) {
                        Log.e("CompetitionsScreen", "Error cancelling reminder", e)
                    }
                }

                clearForm()

                // ✅ Refresh the list if it's visible - WITH ERROR HANDLING
                if (showCompetitionsList) {
                    try {
                        if (searchQuery.isNotEmpty()) {
                            loadCompetitions(database, currentUserId, searchQuery) { loadedCompetitions ->
                                competitions = loadedCompetitions
                            }
                        } else {
                            loadAllCompetitions(database, currentUserId) { loadedCompetitions ->
                                competitions = loadedCompetitions
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CompetitionsScreen", "Error refreshing competitions list after save", e)
                        // Don't set errorMessage here as the save was successful
                    }
                }

            } catch (e: Exception) {
                Log.e("CompetitionsScreen", "Error in saveCompetition", e)
                errorMessage = "Failed to save competition: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Upload events function
    fun uploadEvents() {
        scope.launch {
            isSyncing = true
            syncMessage = "Uploading events to server..."
            syncSuccess = null

            try {
                val result = networkManager.uploadPlannedEvents()

                if (result.success) {
                    syncSuccess = true
                    syncMessage = buildString {
                        append("Upload successful! ")
                        if (result.uploadedCount > 0) append("${result.uploadedCount} uploaded, ")
                        if (result.duplicateCount > 0) append("${result.duplicateCount} duplicates skipped, ")
                        if (result.errorCount > 0) append("${result.errorCount} errors")
                    }
                } else {
                    syncSuccess = false
                    syncMessage = "Upload failed: ${result.message}"
                }
            } catch (e: Exception) {
                Log.e("CompetitionsScreen", "Error in uploadEvents", e)
                syncSuccess = false
                syncMessage = "Upload error: ${e.message}"
            } finally {
                isSyncing = false
            }
        }
    }

    // ✅ Download events function - WITH ENHANCED ERROR HANDLING
    fun downloadEvents() {
        scope.launch {
            isSyncing = true
            syncMessage = "Downloading events from server..."
            syncSuccess = null

            try {
                val result = networkManager.downloadPlannedEvents()

                if (result.success) {
                    syncSuccess = true
                    syncMessage = buildString {
                        append("Download successful! ")
                        if (result.downloadedCount > 0) append("${result.downloadedCount} new events added, ")
                        if (result.duplicateCount > 0) append("${result.duplicateCount} duplicates skipped")
                    }

                    // ✅ Refresh the competitions list if it's visible - WITH ERROR HANDLING
                    if (showCompetitionsList) {
                        try {
                            Log.d("CompetitionsScreen", "Refreshing competitions list after download")
                            if (searchQuery.isNotEmpty()) {
                                loadCompetitions(database, currentUserId, searchQuery) { loadedCompetitions ->
                                    competitions = loadedCompetitions
                                    Log.d("CompetitionsScreen", "Refreshed search results: ${loadedCompetitions.size} competitions")
                                }
                            } else {
                                loadAllCompetitions(database, currentUserId) { loadedCompetitions ->
                                    competitions = loadedCompetitions
                                    Log.d("CompetitionsScreen", "Refreshed all competitions: ${loadedCompetitions.size} competitions")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("CompetitionsScreen", "Error refreshing competitions list after download", e)
                            syncMessage += " (Note: List refresh failed, please manually refresh)"
                        }
                    }
                } else {
                    syncSuccess = false
                    syncMessage = "Download failed: ${result.message}"
                }
            } catch (e: Exception) {
                Log.e("CompetitionsScreen", "Error in downloadEvents", e)
                syncSuccess = false
                syncMessage = "Download error: ${e.message}"
            } finally {
                isSyncing = false
            }
        }
    }

    // ✅ Use LazyColumn with items instead of nested scrolling
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ✅ Error message display
        errorMessage?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(
                            onClick = { errorMessage = null }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // ✅ Add Competition Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddForm = !showAddForm }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (editingCompetition != null) "Edit Competition" else "Add New Competition",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = if (showAddForm) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAddForm) "Collapse" else "Expand"
                        )
                    }

                    // Form Content
                    AnimatedVisibility(
                        visible = showAddForm,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = competitionName,
                                onValueChange = { competitionName = it },
                                label = { Text("Competition Name *") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = competitionDate,
                                onValueChange = { competitionDate = it },
                                label = { Text("Date (YYYY-MM-DD) *") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                singleLine = true,
                                placeholder = { Text("2024-12-25") }
                            )

                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = competitionCountry,
                                    onValueChange = { competitionCountry = it },
                                    label = { Text("Country *") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp, bottom = 8.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = competitionCity,
                                    onValueChange = { competitionCity = it },
                                    label = { Text("City *") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp, bottom = 8.dp),
                                    singleLine = true
                                )
                            }

                            OutlinedTextField(
                                value = competitionType,
                                onValueChange = { competitionType = it },
                                label = { Text("Competition Type") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                singleLine = true,
                                placeholder = { Text("Marathon, Triathlon, etc.") }
                            )

                            OutlinedTextField(
                                value = website,
                                onValueChange = { website = it },
                                label = { Text("Website") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                singleLine = true,
                                placeholder = { Text("https://...") }
                            )

                            OutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text("Comment") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                maxLines = 3
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isEnteredAndFinished,
                                    onCheckedChange = { isEnteredAndFinished = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Competition entered and finished")
                            }

                            // Reminder Section
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isReminderActive,
                                            onCheckedChange = { isReminderActive = it }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Set reminder for this competition",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    AnimatedVisibility(visible = isReminderActive) {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                // Date picker
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 4.dp)
                                                        .clickable {
                                                            val calendar = Calendar.getInstance()
                                                            if (reminderDateTime.isNotEmpty()) {
                                                                reminderManager.parseReminderDateTimeToCalendar(reminderDateTime)?.let {
                                                                    calendar.time = it.time
                                                                }
                                                            }

                                                            DatePickerDialog(
                                                                context,
                                                                { _, year, month, dayOfMonth ->
                                                                    val newCalendar = Calendar.getInstance().apply {
                                                                        set(year, month, dayOfMonth)
                                                                        if (reminderDateTime.isNotEmpty()) {
                                                                            reminderManager.parseReminderDateTimeToCalendar(reminderDateTime)?.let { existing ->
                                                                                set(Calendar.HOUR_OF_DAY, existing.get(Calendar.HOUR_OF_DAY))
                                                                                set(Calendar.MINUTE, existing.get(Calendar.MINUTE))
                                                                            }
                                                                        }
                                                                    }
                                                                    reminderDateTime = reminderManager.formatReminderDateTime(newCalendar)
                                                                    reminderDateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(newCalendar.time)
                                                                },
                                                                calendar.get(Calendar.YEAR),
                                                                calendar.get(Calendar.MONTH),
                                                                calendar.get(Calendar.DAY_OF_MONTH)
                                                            ).show()
                                                        }
                                                ) {
                                                    OutlinedTextField(
                                                        value = reminderDateFormatted,
                                                        onValueChange = { },
                                                        label = { Text("Reminder Date") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        readOnly = true,
                                                        enabled = false,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        trailingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Default.DateRange,
                                                                contentDescription = "Select Date"
                                                            )
                                                        }
                                                    )
                                                }

                                                // Time picker
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(start = 4.dp)
                                                        .clickable {
                                                            val calendar = Calendar.getInstance()
                                                            if (reminderDateTime.isNotEmpty()) {
                                                                reminderManager.parseReminderDateTimeToCalendar(reminderDateTime)?.let {
                                                                    calendar.time = it.time
                                                                }
                                                            }

                                                            TimePickerDialog(
                                                                context,
                                                                { _, hourOfDay, minute ->
                                                                    val newCalendar = Calendar.getInstance().apply {
                                                                        if (reminderDateTime.isNotEmpty()) {
                                                                            reminderManager.parseReminderDateTimeToCalendar(reminderDateTime)?.let { existing ->
                                                                                time = existing.time
                                                                            }
                                                                        }
                                                                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                                        set(Calendar.MINUTE, minute)
                                                                        set(Calendar.SECOND, 0)
                                                                    }
                                                                    reminderDateTime = reminderManager.formatReminderDateTime(newCalendar)
                                                                    reminderTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(newCalendar.time)
                                                                },
                                                                calendar.get(Calendar.HOUR_OF_DAY),
                                                                calendar.get(Calendar.MINUTE),
                                                                true
                                                            ).show()
                                                        }
                                                ) {
                                                    OutlinedTextField(
                                                        value = reminderTimeFormatted,
                                                        onValueChange = { },
                                                        label = { Text("Reminder Time") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        readOnly = true,
                                                        enabled = false,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        trailingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Default.Schedule,
                                                                contentDescription = "Select Time"
                                                            )
                                                        }
                                                    )
                                                }
                                            }

                                            if (reminderDateTime.isNotEmpty()) {
                                                Text(
                                                    text = "You will be reminded on ${reminderDateFormatted} at ${reminderTimeFormatted}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(top = 8.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            // Recurring options
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isRecurring,
                                                    onCheckedChange = { isRecurring = it }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Make this a recurring reminder",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            AnimatedVisibility(visible = isRecurring) {
                                                Column {
                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    // Recurring type dropdown
                                                    var recurringTypeExpanded by remember { mutableStateOf(false) }
                                                    val recurringTypes = mapOf(
                                                        "daily" to "Daily",
                                                        "weekly" to "Weekly",
                                                        "monthly" to "Monthly",
                                                        "yearly" to "Yearly"
                                                    )

                                                    ExposedDropdownMenuBox(
                                                        expanded = recurringTypeExpanded,
                                                        onExpandedChange = { recurringTypeExpanded = !recurringTypeExpanded }
                                                    ) {
                                                        OutlinedTextField(
                                                            value = recurringTypes[recurringType] ?: "Daily",
                                                            onValueChange = { },
                                                            readOnly = true,
                                                            label = { Text("Repeat") },
                                                            trailingIcon = {
                                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurringTypeExpanded)
                                                            },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .menuAnchor()
                                                                .padding(bottom = 8.dp)
                                                        )

                                                        ExposedDropdownMenu(
                                                            expanded = recurringTypeExpanded,
                                                            onDismissRequest = { recurringTypeExpanded = false }
                                                        ) {
                                                            recurringTypes.forEach { (key, value) ->
                                                                DropdownMenuItem(
                                                                    text = { Text(value) },
                                                                    onClick = {
                                                                        recurringType = key
                                                                        recurringTypeExpanded = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // Interval selection
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            "Every",
                                                            modifier = Modifier.padding(end = 8.dp)
                                                        )

                                                        OutlinedTextField(
                                                            value = recurringInterval.toString(),
                                                            onValueChange = { value ->
                                                                value.toIntOrNull()?.let {
                                                                    if (it > 0) recurringInterval = it
                                                                }
                                                            },
                                                            modifier = Modifier.width(80.dp),
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            singleLine = true
                                                        )

                                                        Text(
                                                            when (recurringType) {
                                                                "daily" -> if (recurringInterval == 1) "day" else "days"
                                                                "weekly" -> if (recurringInterval == 1) "week" else "weeks"
                                                                "monthly" -> if (recurringInterval == 1) "month" else "months"
                                                                "yearly" -> if (recurringInterval == 1) "year" else "years"
                                                                else -> "days"
                                                            },
                                                            modifier = Modifier.padding(start = 8.dp)
                                                        )
                                                    }

                                                    // Days of week selection for weekly recurring
                                                    AnimatedVisibility(visible = recurringType == "weekly") {
                                                        DaysOfWeekSelector(
                                                            selectedDaysOfWeek = selectedDaysOfWeek,
                                                            onDaysChanged = { selectedDaysOfWeek = it }
                                                        )
                                                    }

                                                    // End date selection
                                                    Text(
                                                        "End date (optional):",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )

                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                val calendar = Calendar.getInstance()

                                                                DatePickerDialog(
                                                                    context,
                                                                    { _, year, month, dayOfMonth ->
                                                                        val selectedCalendar = Calendar.getInstance().apply {
                                                                            set(year, month, dayOfMonth)
                                                                        }
                                                                        recurringEndDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCalendar.time)
                                                                        recurringEndDateFormatted = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedCalendar.time)
                                                                    },
                                                                    calendar.get(Calendar.YEAR),
                                                                    calendar.get(Calendar.MONTH),
                                                                    calendar.get(Calendar.DAY_OF_MONTH)
                                                                ).show()
                                                            }
                                                            .padding(bottom = 8.dp)
                                                    ) {
                                                        OutlinedTextField(
                                                            value = recurringEndDateFormatted,
                                                            onValueChange = { },
                                                            label = { Text("End Date") },
                                                            placeholder = { Text("Never") },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            readOnly = true,
                                                            enabled = false,
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                            ),
                                                            trailingIcon = {
                                                                Row {
                                                                    if (recurringEndDate.isNotEmpty()) {
                                                                        IconButton(
                                                                            onClick = {
                                                                                recurringEndDate = ""
                                                                                recurringEndDateFormatted = ""
                                                                            }
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Close,
                                                                                contentDescription = "Clear end date"
                                                                            )
                                                                        }
                                                                    }
                                                                    Icon(
                                                                        imageVector = Icons.Default.DateRange,
                                                                        contentDescription = "Select end date"
                                                                    )
                                                                }
                                                            }
                                                        )
                                                    }

                                                    // Recurring summary
                                                    if (isRecurring) {
                                                        val summaryText = buildRecurringSummary(
                                                            recurringType,
                                                            recurringInterval,
                                                            selectedDaysOfWeek,
                                                            recurringEndDateFormatted
                                                        )

                                                        Card(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(top = 8.dp),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                                            )
                                                        ) {
                                                            Text(
                                                                text = summaryText,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.padding(12.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (editingCompetition != null) {
                                    OutlinedButton(
                                        onClick = { clearForm() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel")
                                    }
                                }

                                Button(
                                    onClick = { saveCompetition() },
                                    enabled = competitionName.isNotBlank() &&
                                            competitionDate.isNotBlank() &&
                                            competitionCountry.isNotBlank() &&
                                            competitionCity.isNotBlank() &&
                                            !isLoading,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(if (editingCompetition != null) "Update" else "Save")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // ✅ View Competitions Section (2nd position)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCompetitionsList = !showCompetitionsList
                                // Clear any previous errors when opening the list
                                if (showCompetitionsList) {
                                    errorMessage = null
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "View All Competitions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = if (showCompetitionsList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showCompetitionsList) "Collapse" else "Expand"
                        )
                    }

                    // Competitions List Content
                    AnimatedVisibility(
                        visible = showCompetitionsList,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Search Field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    // Clear any search-related errors
                                    if (errorMessage?.contains("Search") == true) {
                                        errorMessage = null
                                    }
                                },
                                label = { Text("Search competitions...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search"
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                singleLine = true
                            )

                            // ✅ Competitions List - Using Column instead of LazyColumn to avoid nesting
                            if (competitions.isEmpty() && searchQuery.isNotEmpty()) {
                                Text(
                                    text = "No competitions found matching your search.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else if (competitions.isEmpty()) {
                                Text(
                                    text = "No competitions added yet. Add your first competition above!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    competitions.forEach { competition ->
                                        CompetitionItem(
                                            competition = competition,
                                            reminderManager = reminderManager,
                                            onEdit = {
                                                try {
                                                    editingCompetition = competition
                                                    competitionName = competition.plannedEventName
                                                    competitionDate = competition.plannedEventDate
                                                    competitionCountry = competition.plannedEventCountry
                                                    competitionCity = competition.plannedEventCity
                                                    competitionType = competition.plannedEventType
                                                    website = competition.website
                                                    comment = competition.comment
                                                    isEnteredAndFinished = competition.isEnteredAndFinished
                                                    reminderDateTime = competition.reminderDateTime
                                                    isReminderActive = competition.isReminderActive
                                                    isRecurring = competition.isRecurring
                                                    recurringType = competition.recurringType
                                                    recurringInterval = competition.recurringInterval
                                                    recurringEndDate = competition.recurringEndDate

                                                    // Parse days of week for weekly recurring
                                                    if (competition.recurringType == "weekly" && competition.recurringDaysOfWeek.isNotEmpty()) {
                                                        selectedDaysOfWeek = competition.recurringDaysOfWeek
                                                            .split(",")
                                                            .mapNotNull { it.trim().toIntOrNull() }
                                                            .toSet()
                                                    }

                                                    // Update UI fields for reminder
                                                    if (competition.reminderDateTime.isNotEmpty()) {
                                                        reminderManager.parseReminderDateTimeToCalendar(competition.reminderDateTime)?.let { calendar ->
                                                            reminderDateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                                                            reminderTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
                                                        }
                                                    }

                                                    // Update end date formatting
                                                    if (competition.recurringEndDate.isNotEmpty()) {
                                                        try {
                                                            val endDateCal = Calendar.getInstance().apply {
                                                                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                                time = format.parse(competition.recurringEndDate) ?: Date()
                                                            }
                                                            recurringEndDateFormatted = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(endDateCal.time)
                                                        } catch (e: Exception) {
                                                            recurringEndDateFormatted = ""
                                                        }
                                                    }

                                                    showAddForm = true
                                                    errorMessage = null // Clear any errors when editing
                                                } catch (e: Exception) {
                                                    Log.e("CompetitionsScreen", "Error setting up edit form", e)
                                                    errorMessage = "Error loading competition for editing: ${e.message}"
                                                }
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    try {
                                                        Log.d("CompetitionsScreen", "Deleting competition: ${competition.plannedEventName}")

                                                        // Cancel reminder before deleting
                                                        reminderManager.cancelReminder(competition.plannedEventId)

                                                        database.plannedEventDao().deletePlannedEvent(competition)

                                                        Log.d("CompetitionsScreen", "Competition deleted successfully")

                                                        // Refresh list
                                                        if (searchQuery.isNotEmpty()) {
                                                            loadCompetitions(database, currentUserId, searchQuery) { loadedCompetitions ->
                                                                competitions = loadedCompetitions
                                                            }
                                                        } else {
                                                            loadAllCompetitions(database, currentUserId) { loadedCompetitions ->
                                                                competitions = loadedCompetitions
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("CompetitionsScreen", "Error deleting competition", e)
                                                        errorMessage = "Failed to delete competition: ${e.message}"
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ✅ Sync Section (3rd position - at the bottom!)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sync with Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Share your events with others and discover new competitions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Status message
                    syncMessage?.let { message ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when (syncSuccess) {
                                    true -> MaterialTheme.colorScheme.primaryContainer
                                    false -> MaterialTheme.colorScheme.errorContainer
                                    null -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(12.dp),
                                color = when (syncSuccess) {
                                    true -> MaterialTheme.colorScheme.onPrimaryContainer
                                    false -> MaterialTheme.colorScheme.onErrorContainer
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    // Sync buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { uploadEvents() },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload")
                            }
                        }

                        OutlinedButton(
                            onClick = { downloadEvents() },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download")
                            }
                        }
                    }

                    // Test connection button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isSyncing = true
                                syncMessage = "Testing connection..."
                                syncSuccess = null

                                try {
                                    val result = networkManager.testPlannedEventsConnection()

                                    syncSuccess = result.success
                                    syncMessage = if (result.success) {
                                        "Connection test successful!"
                                    } else {
                                        "Connection test failed: ${result.message}"
                                    }
                                } catch (e: Exception) {
                                    Log.e("CompetitionsScreen", "Error in connection test", e)
                                    syncSuccess = false
                                    syncMessage = "Connection test error: ${e.message}"
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Connection")
                    }

                    // Help text
                    Text(
                        text = "• Upload: Share your events with the community\n• Download: Get new events from other users\n• Test: Verify server connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CompetitionItem(
    competition: PlannedEvent,
    reminderManager: ReminderManager,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = competition.plannedEventName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${competition.plannedEventDate} • ${competition.plannedEventCity}, ${competition.plannedEventCountry}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (competition.plannedEventType.isNotBlank()) {
                        Text(
                            text = competition.plannedEventType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (competition.isEnteredAndFinished) {
                        Text(
                            text = "✓ Entered and Finished",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (competition.isReminderActive && competition.reminderDateTime.isNotEmpty()) {
                        val reminderText = if (competition.isRecurring) {
                            val nextTime = reminderManager.calculateNextReminderTime(competition)
                            if (nextTime != null) {
                                val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                                "🔔 Next reminder: ${dateFormat.format(Date(nextTime))} (Recurring)"
                            } else {
                                "🔔 Recurring reminder (ended)"
                            }
                        } else {
                            reminderManager.parseReminderDateTimeToCalendar(competition.reminderDateTime)?.let { cal ->
                                val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                                "🔔 Reminder: ${dateFormat.format(cal.time)}"
                            } ?: "🔔 Reminder set"
                        }

                        Text(
                            text = reminderText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        // Show recurring summary
                        if (competition.isRecurring) {
                            val dayNames = if (competition.recurringType == "weekly" && competition.recurringDaysOfWeek.isNotEmpty()) {
                                competition.recurringDaysOfWeek.split(",")
                                    .mapNotNull { it.trim().toIntOrNull() }
                                    .sorted()
                                    .map { dayConstant ->
                                        when (dayConstant) {
                                            Calendar.MONDAY -> "Mon"
                                            Calendar.TUESDAY -> "Tue"
                                            Calendar.WEDNESDAY -> "Wed"
                                            Calendar.THURSDAY -> "Thu"
                                            Calendar.FRIDAY -> "Fri"
                                            Calendar.SATURDAY -> "Sat"
                                            Calendar.SUNDAY -> "Sun"
                                            else -> ""
                                        }
                                    }.filter { it.isNotEmpty() }
                            } else emptyList()

                            val recurringText = when (competition.recurringType) {
                                "daily" -> if (competition.recurringInterval == 1) "Daily" else "Every ${competition.recurringInterval} days"
                                "weekly" -> {
                                    if (dayNames.isNotEmpty()) {
                                        if (competition.recurringInterval == 1) {
                                            "Weekly: ${dayNames.joinToString(", ")}"
                                        } else {
                                            "Every ${competition.recurringInterval} weeks: ${dayNames.joinToString(", ")}"
                                        }
                                    } else {
                                        if (competition.recurringInterval == 1) "Weekly" else "Every ${competition.recurringInterval} weeks"
                                    }
                                }
                                "monthly" -> if (competition.recurringInterval == 1) "Monthly" else "Every ${competition.recurringInterval} months"
                                "yearly" -> if (competition.recurringInterval == 1) "Yearly" else "Every ${competition.recurringInterval} years"
                                else -> "Recurring"
                            }

                            Text(
                                text = "📅 $recurringText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    if (competition.website.isNotBlank()) {
                        Text(
                            text = "Website: ${competition.website}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (competition.comment.isNotBlank()) {
                        Text(
                            text = competition.comment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// ✅ Enhanced helper functions with proper error handling
private suspend fun loadCompetitions(
    database: FitnessTrackerDatabase,
    userId: Int,
    searchQuery: String,
    onResult: (List<PlannedEvent>) -> Unit
) {
    try {
        Log.d("CompetitionsScreen", "Loading competitions for user $userId with query: '$searchQuery'")
        val result = database.plannedEventDao().searchPlannedEvents(userId, searchQuery)
        Log.d("CompetitionsScreen", "Found ${result.size} competitions matching search")
        onResult(result)
    } catch (e: Exception) {
        Log.e("CompetitionsScreen", "Error loading competitions with search query", e)
        onResult(emptyList())
        throw e // Re-throw to let caller handle the error message
    }
}

private suspend fun loadAllCompetitions(
    database: FitnessTrackerDatabase,
    userId: Int,
    onResult: (List<PlannedEvent>) -> Unit
) {
    try {
        Log.d("CompetitionsScreen", "Loading all competitions for user $userId")
        val result = database.plannedEventDao().getAllPlannedEventsForUser(userId)
        Log.d("CompetitionsScreen", "Found ${result.size} total competitions")
        onResult(result)
    } catch (e: Exception) {
        Log.e("CompetitionsScreen", "Error loading all competitions", e)
        onResult(emptyList())
        throw e // Re-throw to let caller handle the error message
    }
}

@Composable
private fun DaysOfWeekSelector(
    selectedDaysOfWeek: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit
) {
    Column {
        Text(
            "Select days:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val daysOfWeek = listOf(
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat",
            Calendar.SUNDAY to "Sun"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(daysOfWeek) { (dayConstant, dayName) ->
                FilterChip(
                    onClick = {
                        onDaysChanged(
                            if (selectedDaysOfWeek.contains(dayConstant)) {
                                selectedDaysOfWeek - dayConstant
                            } else {
                                selectedDaysOfWeek + dayConstant
                            }
                        )
                    },
                    label = { Text(dayName) },
                    selected = selectedDaysOfWeek.contains(dayConstant)
                )
            }
        }
    }
}

// Helper function to build recurring summary text
private fun buildRecurringSummary(
    recurringType: String,
    recurringInterval: Int,
    selectedDaysOfWeek: Set<Int>,
    recurringEndDateFormatted: String
): String {
    val intervalText = when (recurringType) {
        "daily" -> if (recurringInterval == 1) "every day" else "every $recurringInterval days"
        "weekly" -> {
            if (selectedDaysOfWeek.isNotEmpty()) {
                val dayNames = selectedDaysOfWeek.sorted().map { dayConstant ->
                    when (dayConstant) {
                        Calendar.MONDAY -> "Mon"
                        Calendar.TUESDAY -> "Tue"
                        Calendar.WEDNESDAY -> "Wed"
                        Calendar.THURSDAY -> "Thu"
                        Calendar.FRIDAY -> "Fri"
                        Calendar.SATURDAY -> "Sat"
                        Calendar.SUNDAY -> "Sun"
                        else -> ""
                    }
                }.filter { it.isNotEmpty() }

                if (recurringInterval == 1) {
                    "every ${dayNames.joinToString(", ")}"
                } else {
                    "every $recurringInterval weeks on ${dayNames.joinToString(", ")}"
                }
            } else {
                if (recurringInterval == 1) "every week" else "every $recurringInterval weeks"
            }
        }
        "monthly" -> if (recurringInterval == 1) "every month" else "every $recurringInterval months"
        "yearly" -> if (recurringInterval == 1) "every year" else "every $recurringInterval years"
        else -> "Unknown"
    }

    val endText = if (recurringEndDateFormatted.isNotEmpty()) {
        " until $recurringEndDateFormatted"
    } else {
        " (no end date)"
    }
    return "📅 Recurring reminder: $intervalText$endText"
}