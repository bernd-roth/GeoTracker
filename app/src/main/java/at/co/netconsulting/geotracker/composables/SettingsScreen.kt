package at.co.netconsulting.geotracker.composables

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions as FoundationKeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.core.content.ContextCompat
import at.co.netconsulting.geotracker.receiver.AutoBackupReceiver
import at.co.netconsulting.geotracker.service.AutoBackupService
import at.co.netconsulting.geotracker.service.CleanupResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import at.co.netconsulting.geotracker.tools.DatabaseImporter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    onNavigateToExportSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
    }
    val backupPrefs = remember {
        context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
    }

    val savedState = sharedPreferences.getBoolean("batteryOptimizationState", true)
    var isBatteryOptimizationIgnoredState by remember { mutableStateOf(savedState) }

    var firstName by remember {
        mutableStateOf(sharedPreferences.getString("firstname", "") ?: "")
    }
    var lastName by remember {
        mutableStateOf(sharedPreferences.getString("lastname", "") ?: "")
    }
    var birthDate by remember {
        mutableStateOf(sharedPreferences.getString("birthdate", "") ?: "")
    }
    var height by remember { mutableStateOf(sharedPreferences.getFloat("height", 0f)) }
    var weight by remember { mutableStateOf(sharedPreferences.getFloat("weight", 0f)) }
    var maxHeartRate by remember { 
        mutableStateOf(sharedPreferences.getInt("maxHeartRate", 180))
    }
    var websocketserver by remember {
        mutableStateOf(sharedPreferences.getString("websocketserver", "") ?: "")
    }

    var darkModeEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("darkModeEnabled", false))
    }

    // Voice announcement interval
    var voiceAnnouncementInterval by remember {
        mutableIntStateOf(sharedPreferences.getInt("voiceAnnouncementInterval", 1))
    }

    // Auto backup settings
    var autoBackupEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("autoBackupEnabled", false))
    }
    var backupHour by remember {
        mutableStateOf(sharedPreferences.getInt("backupHour", 2)) // Default to 2:00 AM
    }
    var backupMinute by remember {
        mutableStateOf(sharedPreferences.getInt("backupMinute", 0))
    }
    var autoExportType by remember {
        mutableStateOf(sharedPreferences.getString("autoExportType", "both") ?: "both")
    }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showBirthDatePickerDialog by remember { mutableStateOf(false) }

    // Get last backup info
    val nextBackupTime = remember { mutableStateOf(backupPrefs.getString("nextBackupTime", null)) }
    val lastBackupTime = remember { mutableStateOf<String?>(null) }

    // Database cleanup state
    var showCleanupDialog by remember { mutableStateOf(false) }
    var cleanupResult by remember { mutableStateOf<CleanupResult?>(null) }
    var isCleanupInProgress by remember { mutableStateOf(false) }
    var selectedEventIds by remember { mutableStateOf(setOf<Int>()) }
    val coroutineScope = rememberCoroutineScope()

    // Database import state
    var showImportConfirmation by remember { mutableStateOf(false) }
    var selectedDbFileUri by remember { mutableStateOf<Uri?>(null) }
    var isImportInProgress by remember { mutableStateOf(false) }

    // Update last backup time display
    LaunchedEffect(Unit) {
        val lastBackupTimeMs = backupPrefs.getLong("lastBackupTime", 0L)
        if (lastBackupTimeMs > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            lastBackupTime.value = dateFormat.format(Date(lastBackupTimeMs))
        }
    }

    val isBatteryOptimizationIgnored = isBatteryOptimizationIgnored(context)

    // Function to format time for display
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }

    // Auto-save function
    fun autoSaveSettings() {
        saveAllSettings(
            sharedPreferences,
            firstName.trim(),
            lastName.trim(),
            birthDate,
            height,
            weight,
            maxHeartRate,
            websocketserver.trim(),
            autoBackupEnabled,
            backupHour,
            backupMinute,
            autoExportType,
            voiceAnnouncementInterval,
            darkModeEnabled
        )
    }

    // Birth date picker dialog
    val birthDateCalendar = Calendar.getInstance()
    val birthDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            birthDateCalendar.set(year, month, dayOfMonth)
            birthDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(birthDateCalendar.time)
            autoSaveSettings()
        },
        birthDateCalendar.get(Calendar.YEAR),
        birthDateCalendar.get(Calendar.MONTH),
        birthDateCalendar.get(Calendar.DAY_OF_MONTH)
    )

    // Database file picker launcher
    val dbFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                selectedDbFileUri = uri
                showImportConfirmation = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .background(Color.LightGray)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // User profile settings
        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("Firstname") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        autoSaveSettings()
                    }
                },
            singleLine = true,
            keyboardOptions = FoundationKeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { autoSaveSettings() }
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Lastname") },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        autoSaveSettings()
                    }
                },
            singleLine = true,
            keyboardOptions = FoundationKeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { autoSaveSettings() }
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Birth date field with date picker
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = birthDate,
                onValueChange = { birthDate = it },
                label = { Text("Birthdate") },
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { birthDatePickerDialog.show() }) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Pick Birth Date"
                )
            }
        }

        OutlinedTextField(
            value = height.toString(),
            onValueChange = { input ->
                height = input.toFloatOrNull() ?: height
            },
            label = { Text("Height (cm)") },
            keyboardOptions = FoundationKeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { autoSaveSettings() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        autoSaveSettings()
                    }
                }
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = weight.toString(),
            onValueChange = { input ->
                weight = input.toFloatOrNull() ?: weight
            },
            label = { Text("Weight (kg)") },
            keyboardOptions = FoundationKeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { autoSaveSettings() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        autoSaveSettings()
                    }
                }
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = maxHeartRate.toString(),
            onValueChange = { input ->
                val value = input.toIntOrNull()
                if (value != null && value in 10..300 && input.length <= 3) {
                    maxHeartRate = value
                }
            },
            label = { Text("Max Heart Rate (bpm)") },
            keyboardOptions = FoundationKeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { autoSaveSettings() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        autoSaveSettings()
                    }
                }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = websocketserver,
            onValueChange = { websocketserver = it },
            label = { Text("Websocket ip address") },
            keyboardOptions = FoundationKeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { autoSaveSettings() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        autoSaveSettings()
                    }
                },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Voice announcement interval
        VoiceAnnouncementDropdown(
            value = voiceAnnouncementInterval,
            onValueChange = { voiceAnnouncementInterval = it },
            modifier = Modifier.fillMaxWidth(),
            onSave = { autoSaveSettings() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(
                    color = Color.Transparent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Map Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dark Mode Map",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = darkModeEnabled,
                        onCheckedChange = { isEnabled ->
                            darkModeEnabled = isEnabled
                            // Save immediately when toggled
                            sharedPreferences.edit()
                                .putBoolean("darkModeEnabled", isEnabled)
                                .apply()
                        }
                    )
                }

                Text(
                    text = "Enable dark map tiles for better viewing at night. App interface remains in light mode.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Export & Sync section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(
                    color = Color.Transparent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Export & Sync",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Connect to Strava, Garmin Connect, or TrainingPeaks to automatically sync your activities.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedButton(
                    onClick = onNavigateToExportSync,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Configure Export & Sync")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(
                    color = Color.Transparent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Label positioned like OutlinedTextField label
                Text(
                    text = "Automatic Backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable daily backup",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoBackupEnabled,
                        onCheckedChange = { isEnabled ->
                            autoBackupEnabled = isEnabled

                            // Save immediately to SharedPreferences
                            sharedPreferences.edit()
                                .putBoolean("autoBackupEnabled", isEnabled)
                                .apply()

                            // Schedule or cancel based on toggle
                            if (isEnabled) {
                                // Schedule using our receiver's companion function
                                AutoBackupReceiver.scheduleBackup(context, backupHour, backupMinute)

                                // Refresh next backup time
                                nextBackupTime.value = context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                                    .getString("nextBackupTime", null)
                            } else {
                                // Cancel scheduled backup
                                cancelScheduledBackup(context)
                                nextBackupTime.value = null
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Export type selection
                Text(
                    text = "Export type:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Radio button: Both database and files
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = autoExportType == "both",
                        onClick = {
                            autoExportType = "both"
                            sharedPreferences.edit()
                                .putString("autoExportType", "both")
                                .apply()
                        },
                        enabled = autoBackupEnabled
                    )
                    Text(
                        text = "Database and GPX files",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Radio button: Database only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = autoExportType == "database",
                        onClick = {
                            autoExportType = "database"
                            sharedPreferences.edit()
                                .putString("autoExportType", "database")
                                .apply()
                        },
                        enabled = autoBackupEnabled
                    )
                    Text(
                        text = "Database only",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Radio button: Files only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = autoExportType == "files",
                        onClick = {
                            autoExportType = "files"
                            sharedPreferences.edit()
                                .putString("autoExportType", "files")
                                .apply()
                        },
                        enabled = autoBackupEnabled
                    )
                    Text(
                        text = "GPX files only",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Backup time:", modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { showTimePickerDialog = true },
                        enabled = autoBackupEnabled,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(formatTime(backupHour, backupMinute))
                    }
                }

                Text(
                    text = when (autoExportType) {
                        "both" -> "Database and GPX files will be backed up daily to Downloads/GeoTracker"
                        "database" -> "Database will be backed up daily to Downloads/GeoTracker/DatabaseBackups"
                        "files" -> "GPX files will be exported daily to Downloads/GeoTracker"
                        else -> "Backup will be performed daily to Downloads/GeoTracker"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Display backup status information
                if (nextBackupTime.value != null || lastBackupTime.value != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (autoBackupEnabled && nextBackupTime.value != null) {
                        Text(
                            text = "Next backup: ${nextBackupTime.value}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (lastBackupTime.value != null) {
                        Text(
                            text = "Last backup: ${lastBackupTime.value}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Manual backup button
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val backupIntent = Intent(context, AutoBackupService::class.java)
                            ContextCompat.startForegroundService(context, backupIntent)
                        } else {
                            context.startService(Intent(context, AutoBackupService::class.java))
                        }
                        Toast.makeText(context, "Manual backup started", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run Backup Now")
                }

                // Database cleanup button
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        Log.d("CleanupButton", "Cleanup button clicked")
                        isCleanupInProgress = true
                        coroutineScope.launch {
                            try {
                                Log.d("CleanupButton", "Starting cleanup preview coroutine")
                                cleanupResult = previewDatabaseCleanup(context)
                                Log.d("CleanupButton", "Preview completed, showing dialog: ${cleanupResult != null}")
                                // Select all events by default
                                selectedEventIds = cleanupResult?.events?.map { it.eventId }?.toSet() ?: emptySet()
                                showCleanupDialog = true
                            } catch (e: Exception) {
                                Log.e("CleanupButton", "Error in cleanup preview", e)
                                Toast.makeText(context, "Error previewing cleanup: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                Log.d("CleanupButton", "Setting cleanup progress to false")
                                isCleanupInProgress = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCleanupInProgress
                ) {
                    Text(if (isCleanupInProgress) "Checking..." else "Clean Up Invalid Events")
                }

                // Database import button
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                "application/octet-stream",
                                "application/x-sqlite3",
                                "application/zip"
                            ))
                        }
                        dbFileLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isImportInProgress
                ) {
                    Text(if (isImportInProgress) "Importing..." else "Import Database Backup")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(
                    color = Color.Transparent,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Label positioned like OutlinedTextField label
                Text(
                    text = "Battery Optimization",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ignore battery optimization",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isBatteryOptimizationIgnoredState,
                        onCheckedChange = { isChecked ->
                            isBatteryOptimizationIgnoredState = isChecked
                            sharedPreferences.edit()
                                .putBoolean("batteryOptimizationState", isChecked)
                                .apply()

                            if (isChecked) {
                                requestIgnoreBatteryOptimizations(context)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Background usage might still be enabled. Please disable manually.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }

                Text(
                    text = "Allow the app to run in the background without battery restrictions for accurate location tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    // Show time picker dialog
    if (showTimePickerDialog) {
        TimePickerDialog(
            onDismissRequest = { /* Do nothing - prevent closing when clicking outside */ },
            onTimeSelected = { hour, minute ->
                backupHour = hour
                backupMinute = minute
                showTimePickerDialog = false
                // Update schedule if auto backup is enabled
                if (autoBackupEnabled) {
                    AutoBackupReceiver.scheduleBackup(context, hour, minute)

                    // Refresh next backup time
                    nextBackupTime.value = context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                        .getString("nextBackupTime", null)
                }
                // Auto-save settings
                autoSaveSettings()
            },
            onCancel = { showTimePickerDialog = false },
            initialHour = backupHour,
            initialMinute = backupMinute
        )
    }

    // Enhanced database cleanup dialog with individual selection
    if (showCleanupDialog && cleanupResult != null) {
        AlertDialog(
            onDismissRequest = {
                showCleanupDialog = false
                cleanupResult = null
                selectedEventIds = emptySet()
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Database Cleanup", modifier = Modifier.weight(1f))
                    Text(
                        "${selectedEventIds.size}/${cleanupResult!!.events.size} selected",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    Text(cleanupResult!!.message)

                    if (cleanupResult!!.events.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Select/Deselect all buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedEventIds = cleanupResult!!.events.map { it.eventId }.toSet()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Select All")
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedEventIds = emptySet()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Deselect All")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Select events to delete:",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Scrollable list of events with checkboxes
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            cleanupResult!!.events.forEach { event ->
                                val reason = cleanupResult!!.eventCategories[event.eventId] ?: "Unknown issue"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = selectedEventIds.contains(event.eventId),
                                        onCheckedChange = { isChecked ->
                                            selectedEventIds = if (isChecked) {
                                                selectedEventIds + event.eventId
                                            } else {
                                                selectedEventIds - event.eventId
                                            }
                                        }
                                    )

                                    Column(
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text(
                                            text = event.eventName,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${event.eventDate} • $reason",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedEventIds.isNotEmpty()) {
                    Button(
                        onClick = {
                            isCleanupInProgress = true
                            coroutineScope.launch {
                                try {
                                    val result = performSelectedEventsCleanup(context, selectedEventIds.toList())

                                    Toast.makeText(
                                        context,
                                        result.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error during cleanup: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isCleanupInProgress = false
                                    showCleanupDialog = false
                                    cleanupResult = null
                                    selectedEventIds = emptySet()
                                }
                            }
                        },
                        enabled = !isCleanupInProgress
                    ) {
                        Text(if (isCleanupInProgress) "Deleting..." else "Delete Selected (${selectedEventIds.size})")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showCleanupDialog = false
                        cleanupResult = null
                        selectedEventIds = emptySet()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Database import confirmation dialog
    if (showImportConfirmation && selectedDbFileUri != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmation = false
                selectedDbFileUri = null
            },
            title = { Text("Import Database Backup?") },
            text = {
                Text(
                    "This will replace all current data with the backup file.\n\n" +
                    "• A safety backup will be created first\n" +
                    "• The app will restart after import\n\n" +
                    "Continue?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showImportConfirmation = false
                    isImportInProgress = true
                    coroutineScope.launch {
                        try {
                            val result = DatabaseImporter.importDatabase(context, selectedDbFileUri!!)
                            if (result.isSuccess) {
                                Toast.makeText(context, "Import successful. Restarting...", Toast.LENGTH_SHORT).show()
                                delay(1500)
                                DatabaseImporter.restartApp(context)
                            } else {
                                Toast.makeText(context, "Import failed: ${result.error}", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isImportInProgress = false
                            selectedDbFileUri = null
                        }
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showImportConfirmation = false
                    selectedDbFileUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun saveAllSettings(
    sharedPreferences: SharedPreferences,
    firstName: String,
    lastName: String,
    birthDate: String,
    height: Float,
    weight: Float,
    maxHeartRate: Int,
    websocketserver: String,
    autoBackupEnabled: Boolean,
    backupHour: Int,
    backupMinute: Int,
    autoExportType: String,
//    minDistanceMeters: Int,
//    minTimeSeconds: Int,
    voiceAnnouncementInterval: Int,
    darkModeEnabled: Boolean
) {
    sharedPreferences.edit().apply {
        putString("firstname", firstName)
        putString("lastname", lastName)
        putString("birthdate", birthDate)
        putFloat("height", height)
        putFloat("weight", weight)
        putInt("maxHeartRate", maxHeartRate)
        putString("websocketserver", websocketserver)
        putBoolean("autoBackupEnabled", autoBackupEnabled)
        putInt("backupHour", backupHour)
        putInt("backupMinute", backupMinute)
        putString("autoExportType", autoExportType)
//        putInt("minDistanceMeters", minDistanceMeters)
//        putInt("minTimeSeconds", minTimeSeconds)
        putInt("voiceAnnouncementInterval", voiceAnnouncementInterval)
        putBoolean("darkModeEnabled", darkModeEnabled)
        apply()
    }
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit,
    onCancel: () -> Unit = onDismissRequest,
    initialHour: Int,
    initialMinute: Int
) {
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Backup Time") },
        text = {
            Column {
                // Hour picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hour:", modifier = Modifier.width(80.dp))
                    Slider(
                        value = selectedHour.toFloat(),
                        onValueChange = { selectedHour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 23,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%02d", selectedHour),
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Minute picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Minute:", modifier = Modifier.width(80.dp))
                    Slider(
                        value = selectedMinute.toFloat(),
                        onValueChange = { selectedMinute = it.toInt() },
                        valueRange = 0f..59f,
                        steps = 59,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%02d", selectedMinute),
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "Selected time: ${String.format("%02d:%02d", selectedHour, selectedMinute)}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = { onTimeSelected(selectedHour, selectedMinute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

// Database cleanup helper functions
private suspend fun previewDatabaseCleanup(context: Context): CleanupResult {
    return try {
        Log.d("CleanupPreview", "Starting cleanup preview")
        withContext(Dispatchers.IO) {
            val database = FitnessTrackerDatabase.getInstance(context)
            Log.d("CleanupPreview", "Database instance obtained")

            // Get all types of invalid events
            Log.d("CleanupPreview", "Querying events with no valid metrics...")
            val noMetricsEvents = database.eventDao().getEventsWithLocationsButNoValidMetrics()
            Log.d("CleanupPreview", "Found ${noMetricsEvents.size} events with no valid metrics")

            Log.d("CleanupPreview", "Querying events with invalid timestamps...")
            val futureThreshold = System.currentTimeMillis() + 86400000L // 24 hours from now
            val invalidTimestampEvents = database.eventDao().getEventsWithInvalidTimestamps(futureThreshold)
            Log.d("CleanupPreview", "Found ${invalidTimestampEvents.size} events with invalid timestamps")

            Log.d("CleanupPreview", "Querying all invalid events...")
            val allInvalidEvents = database.eventDao().getAllInvalidEvents(futureThreshold)
            Log.d("CleanupPreview", "Found ${allInvalidEvents.size} total invalid events")

            val eventNames = mutableListOf<String>()
            val categories = mutableListOf<String>()
            val eventCategories = mutableMapOf<Int, String>()
            val allEvents = mutableListOf<at.co.netconsulting.geotracker.domain.Event>()

            if (noMetricsEvents.isNotEmpty()) {
                categories.add("${noMetricsEvents.size} events with no valid metrics")
                noMetricsEvents.forEach { event ->
                    eventNames.add("• ${event.eventName} (${event.eventDate}) - No metrics")
                    eventCategories[event.eventId] = "No valid metrics"
                    allEvents.add(event)
                    Log.d("CleanupPreview", "No metrics: ${event.eventName} (${event.eventDate})")
                }
            }

            if (invalidTimestampEvents.isNotEmpty()) {
                categories.add("${invalidTimestampEvents.size} events with invalid timestamps")
                invalidTimestampEvents.forEach { event ->
                    if (!eventCategories.containsKey(event.eventId)) { // Avoid duplicates
                        eventNames.add("• ${event.eventName} (${event.eventDate}) - Invalid timestamps")
                        eventCategories[event.eventId] = "Invalid timestamps"
                        allEvents.add(event)
                        Log.d("CleanupPreview", "Invalid timestamps: ${event.eventName} (${event.eventDate})")
                    }
                }
            }

            val result = CleanupResult(
                allInvalidEvents.size,
                eventNames,
                if (allInvalidEvents.isEmpty()) {
                    "No invalid events found."
                } else {
                    "Found ${allInvalidEvents.size} invalid events:\n${categories.joinToString("\n")}"
                },
                events = allEvents,
                eventCategories = eventCategories
            )

            Log.d("CleanupPreview", "Cleanup preview completed: ${result.message}")
            result
        }
    } catch (e: Exception) {
        Log.e("CleanupPreview", "Error during preview", e)
        CleanupResult(0, emptyList(), "Error during preview: ${e.message}")
    }
}

private suspend fun performDatabaseCleanup(context: Context): CleanupResult {
    return try {
        withContext(Dispatchers.IO) {
            val database = FitnessTrackerDatabase.getInstance(context)

            // First, get the events that would be deleted for reporting
            val futureThreshold = System.currentTimeMillis() + 86400000L // 24 hours from now
            val eventsToDelete = database.eventDao().getAllInvalidEvents(futureThreshold)

            if (eventsToDelete.isEmpty()) {
                CleanupResult(0, emptyList(), "No invalid events found to clean up.")
            } else {
                // Delete all invalid events (this will cascade delete related data due to foreign keys)
                val deletedCount = database.eventDao().deleteAllInvalidEvents(futureThreshold)

                val eventNames = eventsToDelete.map { "${it.eventName} (${it.eventDate})" }
                CleanupResult(
                    deletedCount,
                    eventNames,
                    "Successfully cleaned up $deletedCount invalid events."
                )
            }
        }
    } catch (e: Exception) {
        CleanupResult(0, emptyList(), "Error during cleanup: ${e.message}")
    }
}

private suspend fun performSelectedEventsCleanup(context: Context, selectedEventIds: List<Int>): CleanupResult {
    return try {
        withContext(Dispatchers.IO) {
            val database = FitnessTrackerDatabase.getInstance(context)

            if (selectedEventIds.isEmpty()) {
                CleanupResult(0, emptyList(), "No events selected for cleanup.")
            } else {
                // Get the events that will be deleted for reporting
                val eventsToDelete = selectedEventIds.mapNotNull { eventId ->
                    database.eventDao().getEventById(eventId)
                }

                // Delete the selected events (this will cascade delete related data due to foreign keys)
                val deletedCount = database.eventDao().deleteEventsByIds(selectedEventIds)

                val eventNames = eventsToDelete.map { "${it.eventName} (${it.eventDate})" }
                CleanupResult(
                    deletedCount,
                    eventNames,
                    "Successfully deleted $deletedCount selected events."
                )
            }
        }
    } catch (e: Exception) {
        CleanupResult(0, emptyList(), "Error during cleanup: ${e.message}")
    }
}

// Helper functions
private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = context.packageName
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

private fun cancelScheduledBackup(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AutoBackupReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        1234, // AUTO_BACKUP_REQUEST_CODE
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Cancel the alarm
    alarmManager.cancel(pendingIntent)
    pendingIntent.cancel()

    // Clear stored next backup time
    context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE).edit()
        .remove("nextBackupTime")
        .apply()
}