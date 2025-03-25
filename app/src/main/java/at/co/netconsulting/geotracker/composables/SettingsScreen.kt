package at.co.netconsulting.geotracker.composables

import android.app.AlarmManager
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import at.co.netconsulting.geotracker.receiver.AutoBackupReceiver
import at.co.netconsulting.geotracker.service.AutoBackupService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen() {
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
    var websocketserver by remember {
        mutableStateOf(sharedPreferences.getString("websocketserver", "") ?: "")
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
    var showTimePickerDialog by remember { mutableStateOf(false) }

    // Get last backup info
    val nextBackupTime = remember { mutableStateOf(backupPrefs.getString("nextBackupTime", null)) }
    val lastBackupTime = remember { mutableStateOf<String?>(null) }

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
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Lastname") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = birthDate,
            onValueChange = { birthDate = it },
            label = { Text("Birthdate (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = height.toString(),
            onValueChange = { input ->
                height = input.toFloatOrNull() ?: height
            },
            label = { Text("Height (cm)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = weight.toString(),
            onValueChange = { input ->
                weight = input.toFloatOrNull() ?: weight
            },
            label = { Text("Weight (kg)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = websocketserver,
            onValueChange = { websocketserver = it },
            label = { Text("Websocket ip address") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Automatic Backup Settings Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Automatic Backup",
                    style = MaterialTheme.typography.titleMedium,
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

                Spacer(modifier = Modifier.height(8.dp))

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
                    text = "Database and GPX files will be backed up daily to Downloads/GeoTracker",
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
            }
        }

        Text(
            text = "Battery Optimization",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
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
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                saveAllSettings(
                    sharedPreferences,
                    firstName,
                    lastName,
                    birthDate,
                    height,
                    weight,
                    websocketserver,
                    autoBackupEnabled,
                    backupHour,
                    backupMinute
                )

                // If auto backup is enabled, reschedule it with the new time
                if (autoBackupEnabled) {
                    AutoBackupReceiver.scheduleBackup(context, backupHour, backupMinute)

                    // Refresh next backup time
                    nextBackupTime.value = context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE)
                        .getString("nextBackupTime", null)
                }

                Toast.makeText(context, "All settings saved", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }

    // Show time picker dialog
    if (showTimePickerDialog) {
        TimePickerDialog(
            onDismissRequest = { showTimePickerDialog = false },
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
            },
            initialHour = backupHour,
            initialMinute = backupMinute
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
    websocketserver: String,
    autoBackupEnabled: Boolean,
    backupHour: Int,
    backupMinute: Int
) {
    sharedPreferences.edit().apply {
        putString("firstname", firstName)
        putString("lastname", lastName)
        putString("birthdate", birthDate)
        putFloat("height", height)
        putFloat("weight", weight)
        putString("websocketserver", websocketserver)
        putBoolean("autoBackupEnabled", autoBackupEnabled)
        putInt("backupHour", backupHour)
        putInt("backupMinute", backupMinute)
        apply()
    }
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit,
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
            Button(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
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