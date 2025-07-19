package at.co.netconsulting.geotracker.composables

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.data.HeartRateSensorDevice
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDialog(
    onSave: (String, String, String, String, String, Boolean, HeartRateSensorDevice?) -> Unit,
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
                    .height(600.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                onClick = {
                    // Use current date as event name if eventName is empty or just whitespace
                    val finalEventName = if (eventName.trim().isEmpty()) {
                        getCurrentFormattedDate()
                    } else {
                        eventName.trim()
                    }

                    onSave(finalEventName, eventDate, artOfSport, comment, clothing, showPath, selectedHeartRateSensor)
                }
            ) {
                Text("Start Recording")
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