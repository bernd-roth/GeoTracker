package at.co.netconsulting.geotracker.composables

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ElectricBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Waves
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.osmdroid.util.GeoPoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private suspend fun parseGpxFileStreamingWithSpeed(
    inputStream: InputStream,
    filename: String,
    speedKmh: Double,
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
                        "trkpt" -> {  // Only parse track points, not waypoints
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

    // Generate synthetic timestamps based on distance and user-specified speed
    val syntheticTimestamps = generateSyntheticTimestamps(points, speedKmh)

    ImportedGpxTrack(filename, points, syntheticTimestamps)
}

/**
 * Generate synthetic timestamps for a track based on distance and assumed average speed.
 * @param points List of GPS points
 * @param speedKmh Assumed average speed in km/h (default: 9 km/h)
 * @return List of relative timestamps in milliseconds (starting from 0)
 */
private fun generateSyntheticTimestamps(points: List<GeoPoint>, speedKmh: Double = 9.0): List<Long> {
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) return listOf(0L)

    val timestamps = mutableListOf<Long>()
    var cumulativeTime = 0L
    timestamps.add(0L) // First point at time 0

    // Speed in meters per second
    val speedMs = (speedKmh * 1000.0) / 3600.0 // 9 km/h = 2.5 m/s

    for (i in 1 until points.size) {
        val prevPoint = points[i - 1]
        val currentPoint = points[i]

        // Calculate distance in meters using Haversine formula
        val distance = calculateDistance(
            prevPoint.latitude, prevPoint.longitude,
            currentPoint.latitude, currentPoint.longitude
        )

        // Calculate time in milliseconds for this segment
        val segmentTime = ((distance / speedMs) * 1000).toLong()
        cumulativeTime += segmentTime

        timestamps.add(cumulativeTime)
    }

    return timestamps
}

/**
 * Calculate distance between two GPS points using Haversine formula.
 * @return Distance in meters
 */
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // Earth radius in meters

    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    return earthRadius * c
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDialog(
    gpsStatus: GpsStatus,
    onSave: (String, String, String, String, String, Boolean, HeartRateSensorDevice?, Boolean, ImportedGpxTrack?, Boolean) -> Unit,
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
    var enableGhostRacer by remember { mutableStateOf(false) }
    var assumedSpeedKmh by remember { mutableStateOf("9.0") } // Default speed for tracks without timestamps

    // GPX import progress state
    var gpxImportProgress by remember { mutableStateOf(GpxImportProgress()) }
    var showGpxImportDialog by remember { mutableStateOf(false) }

    // Track selection dialog state
    var showTrackSelectionDialog by remember { mutableStateOf(false) }

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

                        // Parse with user-specified speed
                        val speed = assumedSpeedKmh.toDoubleOrNull() ?: 9.0
                        val parsedTrack = parseGpxFileStreamingWithSpeed(
                            inputStream = stream,
                            filename = filename,
                            speedKmh = speed,
                            onProgress = { progress ->
                                gpxImportProgress = progress
                            }
                        )

                        importedGpxTrack = parsedTrack
                        showGpxImportDialog = false

                        // Save to persistence immediately so MapScreen can display it
                        GpxPersistenceUtil.saveImportedGpxTrack(context, parsedTrack)
                        android.util.Log.d("RecordingDialog", "Saved imported track to persistence: ${parsedTrack.filename}")

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

    // Hierarchical sport types structure
    data class SportType(val name: String, val subcategories: List<String> = emptyList())
    
    val sportTypes = listOf(
        SportType("Training"),
        SportType("Running", listOf("Trail Running", "Ultramarathon", "Marathon", "Road Running", "Orienteering")),
        SportType("Cycling", listOf("Gravel Bike", "E-Bike", "Racing Bicycle", "Mountain Bike")),
        SportType("Water Sports", listOf("Swimming - Open Water", "Swimming - Pool", "Kayaking", "Canoeing", "Stand Up Paddleboarding")),
        SportType("Ball Sports", listOf("Soccer", "American Football", "Fistball", "Squash", "Tennis", "Basketball", "Volleyball", "Baseball", "Badminton", "Table Tennis")),
        SportType("Winter Sport", listOf("Ski", "Snowboard", "Cross Country Skiing", "Ski Touring", "Ice Skating", "Ice Hockey", "Biathlon", "Sledding", "Snowshoeing")),
        SportType("Walking", listOf("Nordic Walking", "Urban Walking")),
        SportType("Hiking", listOf("Mountain Hiking", "Forest Hiking")),
        SportType("Motorsport", listOf("Car", "Motorcycle"))
    )

    // Function to get appropriate icon for sport type with specific subcategory icons
    fun getSportIcon(sportName: String) = when (sportName) {
        // Training
        "Training" -> Icons.Default.Timer

        // Running subcategories
        "Trail Running" -> Icons.Default.Terrain
        "Ultramarathon" -> Icons.Default.Timer
        "Marathon" -> Icons.Default.Speed
        "Road Running" -> Icons.Default.Route
        "Orienteering" -> Icons.Default.MyLocation
        "Running" -> Icons.Default.DirectionsRun
        
        // Cycling subcategories
        "Gravel Bike" -> Icons.Default.Terrain
        "E-Bike" -> Icons.Default.ElectricBike
        "Racing Bicycle" -> Icons.Default.Speed
        "Mountain Bike" -> Icons.Default.Landscape
        "Cycling" -> Icons.Default.DirectionsBike
        
        // Water Sports subcategories
        "Swimming - Open Water" -> Icons.Default.Waves
        "Swimming - Pool" -> Icons.Default.Pool
        "Kayaking" -> Icons.Default.Sailing
        "Canoeing" -> Icons.Default.Sailing
        "Stand Up Paddleboarding" -> Icons.Default.Sailing
        "Water Sports" -> Icons.Default.Pool
        
        // Ball Sports subcategories
        "Soccer" -> Icons.Default.SportsSoccer
        "American Football" -> Icons.Default.SportsFootball
        "Fistball" -> Icons.Default.SportsHandball
        "Squash" -> Icons.Default.SportsTennis
        "Tennis" -> Icons.Default.SportsTennis
        "Basketball" -> Icons.Default.SportsBasketball
        "Volleyball" -> Icons.Default.SportsVolleyball
        "Baseball" -> Icons.Default.SportsBaseball
        "Badminton" -> Icons.Default.SportsTennis
        "Table Tennis" -> Icons.Default.SportsTennis
        "Ball Sports" -> Icons.Default.SportsFootball
        
        // Walking subcategories
        "Nordic Walking" -> Icons.Default.Hiking
        "Urban Walking" -> Icons.Default.LocationCity
        "Walking" -> Icons.Default.DirectionsWalk
        
        // Hiking subcategories
        "Mountain Hiking" -> Icons.Default.Landscape
        "Forest Hiking" -> Icons.Default.Forest
        "Hiking" -> Icons.Default.Hiking
        
        // Motorsport subcategories
        "Car" -> Icons.Default.DirectionsCar
        "Motorcycle" -> Icons.Default.Motorcycle
        "Motorsport" -> Icons.Default.SportsMotorsports

        // Winter Sport subcategories
        "Ski" -> Icons.Default.Terrain  // Mountain/downhill terrain
        "Snowboard" -> Icons.Default.Landscape  // Mountain landscape
        "Cross Country Skiing" -> Icons.Default.DirectionsRun  // Similar movement to running
        "Ski Touring" -> Icons.Default.Terrain  // Mountain terrain
        "Ice Skating" -> Icons.Default.Speed  // Speed/gliding motion
        "Ice Hockey" -> Icons.Default.SportsFootball  // Team sport icon
        "Biathlon" -> Icons.Default.GpsFixed  // Target/precision sport
        "Sledding" -> Icons.Default.Speed  // Speed/downhill motion
        "Snowshoeing" -> Icons.Default.Hiking  // Similar to hiking
        "Winter Sport" -> Icons.Default.Landscape  // Winter mountain landscape

        // Default fallback
        else -> Icons.Default.DirectionsRun
    }
    
    // Track expanded categories and selected sport
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedSport by remember { mutableStateOf(artOfSport) }

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

    // Track Selection Dialog
    if (showTrackSelectionDialog) {
        TrackSelectionDialog(
            assumedSpeedKmh = assumedSpeedKmh.toDoubleOrNull() ?: 9.0,
            onTrackSelected = { track ->
                importedGpxTrack = track
                showTrackSelectionDialog = false

                // Save to persistence immediately so MapScreen can display it
                GpxPersistenceUtil.saveImportedGpxTrack(context, track)
                android.util.Log.d("RecordingDialog", "Saved selected track to persistence: ${track.filename}")

                android.widget.Toast.makeText(
                    context,
                    "Track selected: ${track.filename}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                showTrackSelectionDialog = false
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
                    .height(800.dp) // Increased height to accommodate track selection
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

                // Track Import/Selection Card - UPDATED
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (importedGpxTrack != null) Color(0xFFE8F5E8) else Color(0xFFF5F5F5)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (importedGpxTrack != null) Icons.Default.MyLocation else Icons.Default.FileOpen,
                                contentDescription = "Track Import",
                                tint = if (importedGpxTrack != null) Color(0xFF4CAF50) else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Track Overlay",
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = importedGpxTrack?.let {
                                        "${it.filename} (${it.points.size} points)"
                                    } ?: "Import GPX file or select existing track",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        if (importedGpxTrack != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    importedGpxTrack = null
                                    // Clear from persistence so MapScreen removes it
                                    GpxPersistenceUtil.clearImportedGpxTrack(context)
                                    android.util.Log.d("RecordingDialog", "Cleared track from persistence")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Remove Track", fontSize = 14.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Two buttons for import options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        gpxFilePicker.launch("application/gpx+xml")
                                    },
                                    enabled = !gpxImportProgress.isImporting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileOpen,
                                        contentDescription = "Import GPX",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import GPX", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        showTrackSelectionDialog = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "Select Track",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Select Track", fontSize = 12.sp)
                                }
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
                        .padding(vertical = 8.dp),
                    singleLine = true
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

                // Hierarchical Sport Type Selector (styled to match OutlinedTextField)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF79747E))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Sport Type",
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = getSportIcon(selectedSport),
                                contentDescription = "$selectedSport icon",
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF1976D2)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedSport,
                                fontSize = 16.sp,
                                color = Color(0xFF1C1B1F),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        sportTypes.forEach { sportType ->
                            // Main category
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        expandedCategories = if (expandedCategories.contains(sportType.name)) {
                                            expandedCategories - sportType.name
                                        } else {
                                            expandedCategories + sportType.name
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (expandedCategories.contains(sportType.name)) 
                                        Icons.Default.KeyboardArrowDown 
                                    else 
                                        Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Expand ${sportType.name}",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = getSportIcon(sportType.name),
                                    contentDescription = "${sportType.name} icon",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (selectedSport == sportType.name) Color(0xFF1976D2) else Color(0xFF616161)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sportType.name,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedSport == sportType.name) Color(0xFF1976D2) else Color.Black,
                                    modifier = Modifier
                                        .clickable { 
                                            selectedSport = sportType.name
                                            artOfSport = sportType.name
                                        }
                                        .weight(1f)
                                )
                            }
                            
                            // Subcategories (show when expanded)
                            if (expandedCategories.contains(sportType.name)) {
                                sportType.subcategories.forEach { subcat ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                selectedSport = subcat
                                                artOfSport = subcat
                                            }
                                            .padding(top = 2.dp, bottom = 2.dp)
                                            .padding(start = 28.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = getSportIcon(subcat),
                                            contentDescription = "$subcat icon",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (selectedSport == subcat) Color(0xFF1976D2) else Color(0xFF9E9E9E)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = subcat,
                                            fontSize = 14.sp,
                                            color = if (selectedSport == subcat) Color(0xFF1976D2) else Color.Gray,
                                            fontWeight = if (selectedSport == subcat) FontWeight.Medium else FontWeight.Normal
                                        )
                                    }
                                }
                            }
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
                        .padding(vertical = 8.dp),
                    singleLine = true
                )

                // Clothing field
                OutlinedTextField(
                    value = clothing,
                    onValueChange = { clothing = it },
                    label = { Text("Clothing") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true
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

                // Assumed Speed for tracks without timing data
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Assumed Speed",
                                tint = Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Assumed Pace (for tracks without time data)",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Used to calculate ghost position timing",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Speed input field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedTextField(
                                value = assumedSpeedKmh,
                                onValueChange = {
                                    // Only allow valid decimal numbers
                                    if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        assumedSpeedKmh = it
                                    }
                                },
                                label = { Text("Speed (km/h)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Speed presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { assumedSpeedKmh = "5.0" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Walk", fontSize = 11.sp)
                                    Text("5 km/h", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                            OutlinedButton(
                                onClick = { assumedSpeedKmh = "9.0" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Jog", fontSize = 11.sp)
                                    Text("9 km/h", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                            OutlinedButton(
                                onClick = { assumedSpeedKmh = "12.0" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Run", fontSize = 11.sp)
                                    Text("12 km/h", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                            OutlinedButton(
                                onClick = { assumedSpeedKmh = "20.0" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Bike", fontSize = 11.sp)
                                    Text("20 km/h", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                // Ghost Racer option (only show if track is imported with timestamps)
                if (importedGpxTrack != null && importedGpxTrack!!.timestamps.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Run Against Opponent", fontWeight = FontWeight.Medium)
                            Text(
                                "Show ghost position from reference track",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = enableGhostRacer,
                            onCheckedChange = { enableGhostRacer = it }
                        )
                    }
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
                        android.util.Log.d("RecordingDialog", "Saved track to persistence: ${importedGpxTrack!!.filename}")
                    }

                    onSave(
                        finalEventName,
                        eventDate,
                        artOfSport,
                        comment.trim(),
                        clothing.trim(),
                        showPath,
                        selectedHeartRateSensor,
                        enableWebSocketTransfer,
                        importedGpxTrack,
                        enableGhostRacer
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