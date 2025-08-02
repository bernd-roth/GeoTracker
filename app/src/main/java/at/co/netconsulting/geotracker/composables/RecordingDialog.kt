package at.co.netconsulting.geotracker.composables

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.GpsStatus
import at.co.netconsulting.geotracker.data.HeartRateSensorDevice
import at.co.netconsulting.geotracker.enums.GpsFixStatus
import at.co.netconsulting.geotracker.tools.GpsStatusEvaluator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDialog(
    gpsStatus: GpsStatus,
    onSave: (String, String, String, String, String, Boolean, HeartRateSensorDevice?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var eventName by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(getCurrentFormattedDate()) }
    var artOfSport by remember { mutableStateOf("Running") }
    var comment by remember { mutableStateOf("") }
    var clothing by remember { mutableStateOf("") }
    var showPath by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    var showHeartRateSensorDialog by remember { mutableStateOf(false) }
    var selectedHeartRateSensor by remember { mutableStateOf<HeartRateSensorDevice?>(null) }

    // WebSocket transfer setting - load from SharedPreferences with default true
    var enableWebSocketTransfer by remember {
        mutableStateOf(
            context.getSharedPreferences("UserSettings", android.content.Context.MODE_PRIVATE)
                .getBoolean("enable_websocket_transfer", true)
        )
    }

    // Sport types list
    val sportTypes = listOf("Running", "Cycling", "Swimming", "Walking", "Hiking", "Other")

    // Date picker dialog
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            eventDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    if (showHeartRateSensorDialog) {
        HeartRateSensorDialog(
            onSelectDevice = { device ->
                selectedHeartRateSensor = device
            },
            onDismiss = {
                showHeartRateSensorDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Activity") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(750.dp) // Increased height to accommodate GPS status
                    .verticalScroll(rememberScrollState())
            ) {
                // GPS Status Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (gpsStatus.status) {
                            GpsFixStatus.GOOD_FIX -> Color(0xFFE8F5E8)
                            GpsFixStatus.NO_LOCATION -> Color(0xFFFFF3E0)
                            GpsFixStatus.INSUFFICIENT_SATELLITES -> Color(0xFFFFF3E0)
                            GpsFixStatus.POOR_ACCURACY -> Color(0xFFFFEBEE)
                            else -> Color(0xFFF5F5F5) // Default light gray
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (gpsStatus.isReadyToRecord) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            contentDescription = "GPS Status",
                            tint = when (gpsStatus.status) {
                                GpsFixStatus.GOOD_FIX -> Color(0xFF4CAF50)
                                GpsFixStatus.NO_LOCATION -> Color(0xFFFF9800)
                                GpsFixStatus.INSUFFICIENT_SATELLITES -> Color(0xFFFF9800)
                                GpsFixStatus.POOR_ACCURACY -> Color(0xFFE91E63)
                                else -> Color.Gray // Default gray
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = GpsStatusEvaluator.getDetailedStatusMessage(gpsStatus),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (gpsStatus.status) {
                                    GpsFixStatus.GOOD_FIX -> Color(0xFF2E7D32)
                                    GpsFixStatus.NO_LOCATION -> Color(0xFFE65100)
                                    GpsFixStatus.INSUFFICIENT_SATELLITES -> Color(0xFFE65100)
                                    GpsFixStatus.POOR_ACCURACY -> Color(0xFFC62828)
                                    else -> Color.DarkGray // Default dark gray
                                }
                            )
                            if (!gpsStatus.isReadyToRecord) {
                                Text(
                                    text = "Recording will start when GPS fix is acquired",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Event name field
                OutlinedTextField(
                    value = eventName,
                    onValueChange = { eventName = it },
                    label = { Text("Event Name *") },
                    placeholder = { Text("Required field") },
                    isError = eventName.trim().isEmpty(),
                    supportingText = {
                        if (eventName.trim().isEmpty()) {
                            Text("Event name is required (current date will be used if empty)")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // Event date field with date picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = eventDate,
                        onValueChange = { eventDate = it },
                        label = { Text("Event Date") },
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Pick Date"
                        )
                    }
                }

                // Sport type dropdown
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = artOfSport,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Sport Type") },
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown Arrow"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        sportTypes.forEach { sport ->
                            DropdownMenuItem(
                                text = { Text(sport) },
                                onClick = {
                                    artOfSport = sport
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Comment field
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // Clothing field
                OutlinedTextField(
                    value = clothing,
                    onValueChange = { clothing = it },
                    label = { Text("Clothing") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // Heart Rate Sensor Selection
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart Rate Icon",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Heart Rate Sensor",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = selectedHeartRateSensor?.name ?: "No sensor selected",
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Button(onClick = { showHeartRateSensorDialog = true }) {
                        Text(if (selectedHeartRateSensor == null) "Select" else "Change")
                    }
                }

                // WebSocket Transfer Setting
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "WebSocket Transfer Icon",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Send data to server",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (enableWebSocketTransfer) "Send data to server" else "Data will be stored locally",
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                    Switch(
                        checked = enableWebSocketTransfer,
                        onCheckedChange = { enableWebSocketTransfer = it }
                    )
                }

                // Show Path option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Show Path on Map")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showPath,
                        onCheckedChange = { showPath = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = gpsStatus.isReadyToRecord, // Enable only when GPS is ready
                onClick = {
                    // Use current date as event name if eventName is empty or just whitespace
                    val finalEventName = if (eventName.trim().isEmpty()) {
                        getCurrentFormattedDate()
                    } else {
                        eventName.trim()
                    }

                    // Save WebSocket transfer setting to SharedPreferences
                    context.getSharedPreferences("UserSettings", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("enable_websocket_transfer", enableWebSocketTransfer)
                        .apply()

                    onSave(finalEventName, eventDate, artOfSport, comment, clothing, showPath, selectedHeartRateSensor, enableWebSocketTransfer)
                }
            ) {
                Text(
                    text = if (gpsStatus.isReadyToRecord) "Start Recording" else "Waiting for GPS...",
                    color = if (gpsStatus.isReadyToRecord) Color.White else Color.Gray
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun getCurrentFormattedDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Calendar.getInstance().time)
}