package at.co.netconsulting.geotracker.composables

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FileOpen
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import at.co.netconsulting.geotracker.data.GpxImportProgress
import at.co.netconsulting.geotracker.data.HeartRateSensorDevice
import at.co.netconsulting.geotracker.data.ImportedGpxTrack
import at.co.netconsulting.geotracker.enums.GpsFixStatus
import at.co.netconsulting.geotracker.tools.GpsStatusEvaluator
import at.co.netconsulting.geotracker.tools.GpxPersistenceUtil
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.BufferedInputStream

private suspend fun parseGpxFileStreaming(
    inputStream: InputStream,
    filename: String,
    onProgress: (GpxImportProgress) -> Unit
): ImportedGpxTrack = withContext(Dispatchers.IO) {

    val points = mutableListOf<GeoPoint>()
    var pointsProcessed = 0
    val updateInterval = 1000 // Update progress every 1000 points

    try {
        onProgress(GpxImportProgress(
            isImporting = true,
            status = "Initializing parser..."
        ))

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()

        // Use BufferedInputStream for better performance
        val bufferedStream = BufferedInputStream(inputStream, 8192)
        parser.setInput(bufferedStream, null)

        var eventType = parser.eventType
        var currentLat: String? = null
        var currentLon: String? = null

        onProgress(GpxImportProgress(
            isImporting = true,
            status = "Parsing GPX data..."
        ))

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "trkpt", "wpt" -> {
                            currentLat = parser.getAttributeValue(null, "lat")
                            currentLon = parser.getAttributeValue(null, "lon")

                            if (currentLat != null && currentLon != null) {
                                try {
                                    val lat = currentLat.toDouble()
                                    val lon = currentLon.toDouble()

                                    // Validate coordinates
                                    if (lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0) {
                                        points.add(GeoPoint(lat, lon))
                                        pointsProcessed++

                                        // Update progress periodically
                                        if (pointsProcessed % updateInterval == 0) {
                                            onProgress(GpxImportProgress(
                                                isImporting = true,
                                                pointsProcessed = pointsProcessed,
                                                status = "Processed $pointsProcessed points..."
                                            ))

                                            // Allow other coroutines to run
                                            yield()
                                        }
                                    }
                                } catch (e: NumberFormatException) {
                                    // Skip invalid coordinates
                                    android.util.Log.w("GPX", "Invalid coordinates: lat=$currentLat, lon=$currentLon")
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        bufferedStream.close()

    } catch (e: Exception) {
        onProgress(GpxImportProgress(
            isImporting = false,
            status = "Error: ${e.message}"
        ))
        throw Exception("Failed to parse GPX file: ${e.message}")
    }

    if (points.isEmpty()) {
        throw Exception("No valid GPS coordinates found in GPX file")
    }

    onProgress(GpxImportProgress(
        isImporting = false,
        pointsProcessed = points.size,
        status = "Import completed"
    ))

    ImportedGpxTrack(filename, points)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDialog(
    gpsStatus: GpsStatus,
    onSave: (String, String, String, String, String, Boolean, HeartRateSensorDevice?, Boolean, ImportedGpxTrack?) -> Unit,
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
    var importedGpxTrack by remember { mutableStateOf<ImportedGpxTrack?>(null) }

    // GPX import progress state
    var gpxImportProgress by remember { mutableStateOf(GpxImportProgress()) }
    var showGpxImportDialog by remember { mutableStateOf(false) }

    // WebSocket transfer setting - load from SharedPreferences with default true
    var enableWebSocketTransfer by remember {
        mutableStateOf(
            context.getSharedPreferences("UserSettings", android.content.Context.MODE_PRIVATE)
                .getBoolean("enable_websocket_transfer", true)
        )
    }

    // GPX file picker launcher
    val gpxFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Show import dialog and start processing
            showGpxImportDialog = true
            gpxImportProgress = GpxImportProgress(isImporting = true)

            // Launch coroutine for background processing
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(it)
                    inputStream?.let { stream ->
                        val filename = getFileNameFromUri(context, uri)

                        val parsedTrack = parseGpxFileStreaming(
                            inputStream = stream,
                            filename = filename,
                            onProgress = { progress ->
                                gpxImportProgress = progress
                            }
                        )

                        importedGpxTrack = parsedTrack
                        showGpxImportDialog = false

                        android.widget.Toast.makeText(
                            context,
                            "GPX imported: ${parsedTrack.points.size} points",
                            android.widget.Toast.LENGTH_LONG
                        ).show()

                        stream.close()
                    }
                } catch (e: Exception) {
                    showGpxImportDialog = false
                    gpxImportProgress = GpxImportProgress()

                    android.widget.Toast.makeText(
                        context,
                        "Error importing GPX: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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

    // GPX Import Progress Dialog
    if (showGpxImportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!gpxImportProgress.isImporting) {
                    showGpxImportDialog = false
                }
            },
            title = { Text("Importing GPX File") },
            text = {
                Column {
                    if (gpxImportProgress.isImporting) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = if (gpxImportProgress.totalEstimated > 0) {
                                gpxImportProgress.pointsProcessed.toFloat() / gpxImportProgress.totalEstimated
                            } else {
                                0f
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(gpxImportProgress.status)

                    if (gpxImportProgress.pointsProcessed > 0) {
                        Text("Points processed: ${gpxImportProgress.pointsProcessed}")
                    }
                }
            },
            confirmButton = {
                if (!gpxImportProgress.isImporting) {
                    Button(
                        onClick = { showGpxImportDialog = false }
                    ) {
                        Text("OK")
                    }
                }
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
                    .height(800.dp) // Increased height to accommodate GPX import
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

                // GPX Import Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (importedGpxTrack != null) Color(0xFFE8F5E8) else Color(0xFFF5F5F5)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = "GPX Import",
                                tint = if (importedGpxTrack != null) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Import GPX Track",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = importedGpxTrack?.let {
                                        "${it.filename} (${it.points.size} points)"
                                    } ?: "Supports large files",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            if (importedGpxTrack != null) {
                                OutlinedButton(
                                    onClick = { importedGpxTrack = null },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Remove", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Button(
                                onClick = {
                                    gpxFilePicker.launch("application/gpx+xml")
                                },
                                enabled = !gpxImportProgress.isImporting,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (importedGpxTrack == null) "Choose File" else "Replace",
                                    fontSize = 12.sp
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
                enabled = gpsStatus.isReadyToRecord && !gpxImportProgress.isImporting,
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

                    // Save imported GPX track to persistent storage before starting recording
                    if (importedGpxTrack != null) {
                        GpxPersistenceUtil.saveImportedGpxTrack(context, importedGpxTrack)
                        android.util.Log.d("RecordingDialog", "Saved GPX track to persistence: ${importedGpxTrack!!.filename}")
                    }

                    onSave(
                        finalEventName,
                        eventDate,
                        artOfSport,
                        comment,
                        clothing,
                        showPath,
                        selectedHeartRateSensor,
                        enableWebSocketTransfer,
                        importedGpxTrack
                    )
                }
            ) {
                Text(
                    text = when {
                        gpxImportProgress.isImporting -> "Importing GPX..."
                        !gpsStatus.isReadyToRecord -> "Waiting for GPS..."
                        else -> "Start Recording"
                    },
                    color = when {
                        gpxImportProgress.isImporting -> Color.Gray
                        gpsStatus.isReadyToRecord -> Color.White
                        else -> Color.Gray
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !gpxImportProgress.isImporting
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

// Helper function to get filename from URI
private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    var filename = "imported_track.gpx"

    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                filename = cursor.getString(nameIndex) ?: filename
            }
        }
    }
    return filename
}