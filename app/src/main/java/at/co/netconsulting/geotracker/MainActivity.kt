package at.co.netconsulting.geotracker

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import at.co.netconsulting.geotracker.data.LocationEvent
import at.co.netconsulting.geotracker.data.SingleEventWithMetric
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.User
import at.co.netconsulting.geotracker.location.CustomLocationListener
import at.co.netconsulting.geotracker.service.BackgroundLocationService
import at.co.netconsulting.geotracker.service.ForegroundService
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.time.Instant
import java.time.ZoneId
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.FileOutputStream
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private var latitudeState = mutableDoubleStateOf(0.0)
    private var longitudeState = mutableDoubleStateOf(0.0)
    private var horizontalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var altitudeState = mutableDoubleStateOf(0.0)
    private var speedState = mutableFloatStateOf(0.0f)
    private var speedAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var verticalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var coveredDistanceState = mutableDoubleStateOf(0.0)
    private val locationEventState = mutableStateOf<LocationEvent?>(null)
    private val startDateTimeState = mutableStateOf<LocalDateTime>(LocalDateTime.now())
    private val locationChangeEventState = mutableStateOf(CustomLocationListener.LocationChangeEvent(emptyList()))
    private lateinit var mapView: MapView
    private lateinit var polyline: Polyline
    private var usedInFixCount = 0
    private var satelliteCount = 0
    private lateinit var marker: Marker
    private val PERMISSION_REQUEST_CODE = 1001
    private val permissions = listOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.WAKE_LOCK
    )
    private var savedLocationData = SavedLocationData(emptyList(), false)
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private var height: Float = 0f
    private var weight: Float = 0f
    private val EARTH_RADIUS = 6371008.8

    private fun loadSharedPreferences() {
        val sharedPreferences = this.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        firstname = sharedPreferences.getString("firstname", "") ?: ""
        lastname = sharedPreferences.getString("lastname", "") ?: ""
        birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        height = sharedPreferences.getFloat("height", 0f)
        weight = sharedPreferences.getFloat("weight", 0f)
    }

    private fun arePermissionsGranted(): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLocationEvent(event: LocationEvent) {
        Log.d(
            "MainActivity",
            "Latitude: ${event.latitude}," +
                    " Longitude: ${event.longitude}," +
                    " Speed: ${event.speed}," +
                    " SpeedAccuracyInMeters: ${event.speedAccuracyMetersPerSecond}," +
                    " Altitude: ${event.altitude}," +
                    " HorizontalAccuracyInMeters: ${event.horizontalAccuracy}," +
                    " VerticalAccuracyInMeters: ${event.verticalAccuracyMeters}" +
                    " CoveredDistance: ${event.coveredDistance}"
        )

        locationEventState.value = event
        latitudeState.value = event.latitude
        longitudeState.value = event.longitude
        speedState.value = event.speed
        speedAccuracyInMetersState.value = event.speedAccuracyMetersPerSecond
        altitudeState.value = event.altitude
        verticalAccuracyInMetersState.value = event.verticalAccuracyMeters
        coveredDistanceState.value = event.coveredDistance
        startDateTimeState.value = event.startDateTime
        locationChangeEventState.value = event.locationChangeEventList

        if (isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")) {
            Log.d("MainActivity", "Drawing polyline")
            val newPoints = locationChangeEventState.value.latLngs.map {
                GeoPoint(it.latitude, it.longitude)
            }
            savedLocationData = SavedLocationData(newPoints, true)
            drawPolyline(locationChangeEventState.value)
        } else {
            Log.d("MainActivity", "Service not running, centering map")
            savedLocationData = SavedLocationData(emptyList(), false)
            mapView.controller.setCenter(GeoPoint(latitudeState.value, longitudeState.value))
            //mapView.controller.setZoom(15.0)
            mapView.invalidate()
        }
    }

    private fun isServiceRunning(serviceName: String): Boolean {
        var serviceRunning = false
        val am = this.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val l = am.getRunningServices(50)
        val i: Iterator<ActivityManager.RunningServiceInfo> = l.iterator()
        while (i.hasNext()) {
            val runningServiceInfo = i
                .next()

            if (runningServiceInfo.service.className == serviceName) {
                serviceRunning = true

                if (runningServiceInfo.foreground) {}
            }
        }
        return serviceRunning
    }

    private fun drawPolyline(
        locationChangeEventList: CustomLocationListener.LocationChangeEvent
    ) {
        val latLngs = locationChangeEventList.latLngs

        val oldPolyline = mapView.overlays.find { it is Polyline } as? Polyline
        if (oldPolyline != null) {
            mapView.overlays.remove(oldPolyline)
        }

        val newPolyline = Polyline().apply {
            outlinePaint.strokeWidth = 10f
            outlinePaint.color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_purple)
            latLngs.forEach { latLng ->
                addPoint(GeoPoint(latLng.latitude, latLng.longitude))
            }
        }

        if (latLngs.isNotEmpty()) {
            createMarker(latLngs[0])
        }

        polyline = newPolyline
        mapView.overlays.add(newPolyline)
        //mapView.controller.setZoom(17.0)
        mapView.invalidate()
    }

    private fun createMarker(firstLatLng: LatLng) {
        if (!::marker.isInitialized) {
            marker = Marker(mapView)
        }

        val firstPoint = GeoPoint(firstLatLng.latitude, firstLatLng.longitude)

        // Remove existing marker if it exists
        val existingMarker = mapView.overlays.find { it is Marker } as? Marker
        if (existingMarker != null) {
            mapView.overlays.remove(existingMarker)
        }

        if(!(firstPoint.latitude==0.0 && firstPoint.longitude==0.0)) {
            marker.position = firstPoint
            marker.title = getString(R.string.marker_title)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_start_marker)
            mapView.overlays.add(marker)
            mapView.controller.setCenter(GeoPoint(firstPoint.latitude,firstPoint.longitude))
        } else {
            mapView.controller.setCenter(GeoPoint(0.0,0.0))
        }
        mapView.controller.setZoom(5.0)
        mapView.invalidate()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val mapCenter = remember { mutableStateOf<GeoPoint?>(null) }
        val mapZoom = remember { mutableStateOf(17.0) }
        val lastKnownLocation = remember { mutableStateOf<GeoPoint?>(null) }

        // Coroutine scope to auto-hide the top bar after a delay
        val coroutineScope = rememberCoroutineScope()

        // Create a scaffold state for controlling the bottom sheet
        val scaffoldState = rememberBottomSheetScaffoldState()

        // Remember the selected tab index
        var selectedTabIndex by remember { mutableStateOf(0) }

        // List of tab titles
        val tabs = listOf(
            getString(R.string.map),
            getString(R.string.statistics),
            getString(R.string.settings)
        )

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                BottomSheetContent(
                    latitude = latitudeState.value,
                    longitude = longitudeState.value,
                    speed = speedState.value,
                    speedAccuracyInMeters = speedState.value,
                    altitude = altitudeState.value,
                    verticalAccuracyInMeters = verticalAccuracyInMetersState.value,
                    horizontalAccuracyInMeters = horizontalAccuracyInMetersState.value,
                    numberOfSatellites = satelliteCount,
                    usedNumberOfSatellites = usedInFixCount,
                    coveredDistance = coveredDistanceState.value
                )
            },
            sheetPeekHeight = 20.dp,
            sheetContentColor = Color.Transparent,
            sheetContainerColor = Color.Transparent
        ) {
            Scaffold(
                topBar = {
                    // TabRow for navigation
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title) }
                            )
                        }
                    }
                },
                content = { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (selectedTabIndex) {
                            0 -> MapScreen(mapCenter, mapZoom, lastKnownLocation)
                            1 -> StatisticsScreenPreview(context = applicationContext, locationEventState = locationEventState)
                            2 -> SettingsScreen()
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun MapScreen(
        mapCenter: MutableState<GeoPoint?>,
        mapZoom: MutableState<Double>,
        lastKnownLocation: MutableState<GeoPoint?>
    ) {
        Column {
            OpenStreetMapView(mapCenter, mapZoom, lastKnownLocation)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun StatisticsScreenPreview(context: Context, locationEventState: MutableState<LocationEvent?>, latLngs: List<LatLng> = emptyList()) {
        val scrollState = rememberScrollState()
        var showGPXDialog by remember { mutableStateOf(false) }
        val actualStatistics = locationEventState.value ?: LocationEvent(
            latitude = 0.0,
            longitude = 0.0,
            speed = 0.0f,
            speedAccuracyMetersPerSecond = 0.0f,
            altitude = 0.0,
            horizontalAccuracy = 0.0f,
            verticalAccuracyMeters = 0.0f,
            coveredDistance = 0.0,
            lap = 0,
            startDateTime = startDateTimeState.value,
            averageSpeed = 0.0,
            locationChangeEventList = CustomLocationListener.LocationChangeEvent(latLngs)
        )
        var users by remember { mutableStateOf<List<User>>(emptyList()) }
        var events by remember { mutableStateOf<List<Event>>(emptyList()) }
        var records by remember { mutableStateOf<List<SingleEventWithMetric>>(emptyList()) }
        var expanded by remember { mutableStateOf(false) }
        var selectedRecords by remember { mutableStateOf<List<SingleEventWithMetric>>(emptyList()) }

        // Search state
        var searchQuery by remember { mutableStateOf("") }

        val coroutineScope = rememberCoroutineScope()

        suspend fun loadData() {
            try {
                users = database.userDao().getAllUsers()
                Log.d("Loaded", "${users.size} users") // Debug log

                events = database.eventDao().getEventDateEventNameGroupByEventDate()
                Log.d("Loaded", "${events.size} events") // Debug log

                records = database.eventDao().getDetailsFromEventJoinedOnMetricsWithRecordingData()
                Log.d("Loaded", "${records.size} records") // Debug log

            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("Error", "loading data: ${e.message}") // Debug log
            }
        }

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                loadData()
            }
        }

        // Handle expanded state changes
        LaunchedEffect(expanded) {
            if (expanded) {
                coroutineScope.launch {
                    loadData()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
                .background(Color.LightGray)
        ) {
            // Display actual statistics
            Text("Actual Statistics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LocationEventPanel(actualStatistics)
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Dropdown menu for selecting events
            Text(text = "Select an event", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { isExpanded ->
                    expanded = isExpanded
                }
            ) {
                OutlinedTextField(
                    value = selectedRecords.joinToString(", ") { it.eventName },
                    onValueChange = { /* No-op */ },
                    readOnly = true,
                    modifier = Modifier.menuAnchor(),
                    trailingIcon = {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                )

                // DropdownMenu with search filter
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    // Search field for filtering
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query -> searchQuery = query },
                        label = { Text("Search event") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )

                    // Filter the records based on the search query, checking all relevant fields
                    val filteredRecords = records.filter {
                        val eventNameMatch = it.eventName.contains(searchQuery, ignoreCase = true)
                        val eventDateMatch = it.eventDate.contains(searchQuery, ignoreCase = true)
                        // Convert distance to Km, handle null distance
                        val distanceMatch = it.distance?.let { distance ->
                            "%.3f".format(distance / 1000).contains(searchQuery, ignoreCase = true)
                        } ?: false
                        eventNameMatch || eventDateMatch || distanceMatch
                    }

                    filteredRecords.forEach { record ->
                        DropdownMenuItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    // Highlight selected rows
                                    if (selectedRecords.contains(record)) {
                                        // background color for highlighting
                                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    } else {
                                        Modifier
                                    }
                                ),
                            onClick = {
                                selectedRecords = if (selectedRecords.contains(record)) {
                                    selectedRecords.filter { it != record } // Remove if already selected
                                } else {
                                    selectedRecords + record // Add if not selected
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Date: ${record.eventDate}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Event name: ${record.eventName}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Covered distance: ${"%.3f".format(record.distance?.div(
                                            1000
                                        ) ?: 0.0)} Km",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Log.d("Raw distance from record: ", "${record.distance}")

                                    if (selectedRecords.contains(record)) {
                                        Text(
                                            text = "Selected",
                                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    delete(record.eventId)
                                                    records = records.filter { it.eventId != record.eventId }
                                                }
                                            }
                                        ) {
                                            Text(text = "Delete")
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    export(record.eventId)
                                                }
                                            }
                                        ) {
                                            Text(text = "Export")
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    edit(record.eventId)
                                                }
                                            }
                                        ) {
                                            Text(text = "Edit")
                                        }
                                    }
                                }
                            }
                        )
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            coroutineScope.launch {
                                deleteContentAllTables()
                                records = emptyList()
                            }
                        },
                        text = {
                            Text(
                                text = "Delete content of all tables",
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            coroutineScope.launch {
                                exportGPX()
                            }
                        },
                        text = {
                            Text(
                                text = "Export GPX files",
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showGPXDialog = true
                            expanded = false  // Close the dropdown when opening dialog
                        },
                        text = {
                            Text(
                                text = "Import GPX files",
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display statistics of all selected events
            selectedRecords.forEach { record ->
                SelectedEventPanel(record)
            }
        }

        if (showGPXDialog) {
            GPXFileSelectionDialog(
                context = context,
                onDismissRequest = { showGPXDialog = false },
                onFileSelected = { file ->
                    coroutineScope.launch {
                        importGPXFile(
                            file = file,
                            database = database,
                            onComplete = { success ->
                                if (success) {
                                    coroutineScope.launch {
                                        loadData()
                                    }
                                }
                                showGPXDialog = false
                            }
                        )
                    }
                }
            )
        }
    }

    @Composable
    fun GPXFileSelectionDialog(
        context: Context, // Add context parameter
        onDismissRequest: () -> Unit,
        onFileSelected: (File) -> Unit
    ) {
        var showError by remember { mutableStateOf(false) }

        // Function to convert Uri to File
        fun uriToFile(uri: Uri): File? {
            return try {
                // Create a temporary file
                val tempFile = File(context.cacheDir, "temp_gpx_file.gpx")

                // Copy the URI content to the temporary file
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { selectedUri ->
                // Convert URI to File and handle the selection
                uriToFile(selectedUri)?.let { file ->
                    onFileSelected(file)
                } ?: run {
                    showError = true
                }
            }
        }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Select GPX File") },
            text = {
                if (showError) {
                    Text("Error importing GPX file. Please try again.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        launcher.launch("*/*") // Changed to accept all file types since GPX mime type might not be recognized
                    }
                ) {
                    Text("Choose File")
                }
            },
            dismissButton = {
                Button(onClick = onDismissRequest) {
                    Text("Cancel")
                }
            }
        )
    }

    private fun exportGPX() {
        Log.d("exportGPX", "Export whole table done")
        Toast.makeText(applicationContext, "Multiple export not implemented yet", Toast.LENGTH_LONG).show()
    }

    private suspend fun deleteContentAllTables() {
        database.eventDao().deleteAllContent()
    }

    private fun edit(eventId: Int) {
        Toast.makeText(applicationContext, "Edit not implemented yet", Toast.LENGTH_LONG).show()
    }

    private fun export(eventId: Int) {
        Toast.makeText(applicationContext, "Single export not implemented yet", Toast.LENGTH_LONG).show()
    }

    private suspend fun delete(eventId: Int) {
        database.eventDao().delete(eventId)
    }

    @Composable
    fun LocationEventPanel(event: LocationEvent) {
        val formattedTime = if (event.startDateTime == null) {
            "N/A"
        } else {
            val zonedDateTime = event.startDateTime.atZone(ZoneId.systemDefault())
            val epochMilli = zonedDateTime.toInstant().toEpochMilli()
            val eventStartDateTimeFormatted = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMilli),
                ZoneId.systemDefault()
            ).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            eventStartDateTimeFormatted
        }

        Column(modifier = Modifier.padding(8.dp)) {
            Text("Date and time: $formattedTime", style = MaterialTheme.typography.bodyLarge)
            Text("Speed: ${"%.2f".format(event.speed)} Km/h", style = MaterialTheme.typography.bodyLarge)
            Text("Ø speed: ${"%.2f".format(event.averageSpeed)} Km/h", style = MaterialTheme.typography.bodyLarge)
            Text("Covered distance: ${"%.3f".format(event.coveredDistance/1000)} Km", style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    fun SelectedEventPanel(record: SingleEventWithMetric) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Event date: ${record.eventDate}", style = MaterialTheme.typography.bodyLarge)
            Text("Event name: ${record.eventName}", style = MaterialTheme.typography.bodyLarge)
            Text("Covered distance: ${"%.3f".format(record.distance?.div(1000) ?: 0.0)} Km", style = MaterialTheme.typography.bodyLarge)
            EventMapView(record = record)
        }
    }

    @Composable
    fun SettingsScreen() {
        val context = LocalContext.current
        val sharedPreferences = remember {
            context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        }

        val savedState = sharedPreferences.getBoolean("batteryOptimizationState", true)
        var isBatteryOptimizationIgnoredState by remember { mutableStateOf(savedState) }

        var firstName by remember {
            mutableStateOf(
                sharedPreferences.getString("firstname", "") ?: ""
            )
        }
        var lastName by remember {
            mutableStateOf(
                sharedPreferences.getString("lastname", "") ?: ""
            )
        }
        var birthDate by remember {
            mutableStateOf(
                sharedPreferences.getString("birthdate", "") ?: ""
            )
        }
        var height by remember { mutableStateOf(sharedPreferences.getFloat("height", 0f)) }
        var weight by remember { mutableStateOf(sharedPreferences.getFloat("weight", 0f)) }
        var websocketserver by remember { mutableStateOf(sharedPreferences.getString("websocketserver", "") ?: "") }

        // Check if the app is ignored by battery optimization
        val isBatteryOptimized = isBatteryOptimizationIgnored(context)

        // State for the switch (shows if app is excluded from battery optimizations)
        var isOptimized by remember { mutableStateOf(isBatteryOptimized) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(Color.LightGray)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Display other settings like firstname, last name, etc.
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
                value = height.toString(), // Convert Float to String
                onValueChange = { input ->
                    height = input.toFloatOrNull() ?: height // Safely convert input to Float
                },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = weight.toString(), // Convert Float to String
                onValueChange = { input ->
                    weight = input.toFloatOrNull() ?: weight // Safely convert input to Float
                },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = websocketserver,
                onValueChange = { websocketserver = it },
                label = { Text("Websocket ip adress") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Battery Optimization Toggle
            Text(
                text = "Battery Optimization",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            Switch(
                checked = isBatteryOptimizationIgnoredState,
                onCheckedChange = { isChecked ->
                    isBatteryOptimizationIgnoredState = isChecked
                    // Save the switch state to SharedPreferences
                    sharedPreferences.edit().putBoolean("batteryOptimizationState", isChecked).apply()

                    // If the user switches the setting, update accordingly
                    if (isChecked) {
                        requestIgnoreBatteryOptimizations(context)
                    } else {
                        // When the user wants to disable background optimization,
                        // the system might not immediately reflect it
                        Toast.makeText(context, "Background usage might still be enabled. Please disable manually.", Toast.LENGTH_LONG).show()
                    }
                }
            )
            // Save Button
            Button(
                onClick = {
                    saveToSharedPreferences(
                        sharedPreferences,
                        firstName,
                        lastName,
                        birthDate,
                        height,
                        weight,
                        websocketserver
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    private fun saveToSharedPreferences(
        sharedPreferences: SharedPreferences,
        firstName: String,
        lastName: String,
        birthDate: String,
        height: Float,
        weight: Float,
        websocketserver: String
    ) {
        val editor = sharedPreferences.edit()
        editor.putString("firstname", firstName)
        editor.putString("lastname", lastName)
        editor.putString("birthdate", birthDate)
        editor.putFloat("height", height)
        editor.putFloat("weight", weight)
        editor.putString("websocketserver", websocketserver)
        editor.apply()
    }

    @Composable
    fun BottomSheetContent(
        latitude: Double,
        longitude: Double,
        speed: Float,
        speedAccuracyInMeters: Float,
        altitude: Double,
        horizontalAccuracyInMeters: Float,
        verticalAccuracyInMeters: Float,
        numberOfSatellites: Int,
        usedNumberOfSatellites: Int,
        coveredDistance: Double
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Latitude: $latitude Longitude: $longitude",
                fontSize = 10.sp,
                color = Color.Black
            )
            Text("Speed ${"%.2f".format(speed)} km/h",
                fontSize = 10.sp,
                color = Color.Black)
            Text(
                "Speed accuracy: ±${"%.2f".format(speedAccuracyInMeters)} km/h",
                fontSize = 10.sp,
                color = Color.Black
            )
            Text(
                "Altitude: ${"%.2f".format(altitude)} meter",
                fontSize = 10.sp,
                color = Color.Black
            )
            Text(
                "Horizontal accuracy: ±${"%.2f".format(horizontalAccuracyInMeters)} meter",
                fontSize = 10.sp,
                color = Color.Black
            )
            Text(
                "Vertical accuracy: ±${"%.2f".format(verticalAccuracyInMeters)} meter",
                fontSize = 10.sp,
                color = Color.Black
            )
            Text(
                "Satellites: $usedNumberOfSatellites/$numberOfSatellites ",
                fontSize = 10.sp,
                color = Color.Black
            )
            Text(
                "Covered distance: ${"%.3f".format(coveredDistance / 1000)} Km",
                fontSize = 10.sp,
                color = Color.Black
            )
        }
    }

    @Composable
    fun OpenStreetMapView(
        mapCenter: MutableState<GeoPoint?>,
        mapZoom: MutableState<Double>,
        lastKnownLocation: MutableState<GeoPoint?>
    ) {
        val context = LocalContext.current

        var showDialog by remember { mutableStateOf(false) }
        var isRecording by remember {
            mutableStateOf(
                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                    .getBoolean("is_recording", false)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    mapView = MapView(context).apply {
                        setTileSource(customTileSource)
                        setMultiTouchControls(true)

                        if (savedLocationData.points.isNotEmpty()) {
                            polyline = Polyline().apply {
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.color = ContextCompat.getColor(context, android.R.color.holo_purple)
                                setPoints(savedLocationData.points)
                            }
                            overlays.add(polyline)
                        }

                        polyline = Polyline().apply {
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.color = ContextCompat.getColor(context, android.R.color.holo_purple)
                        }
                        overlays.add(polyline)

                        val dm: DisplayMetrics = context.resources.displayMetrics
                        val scaleBarOverlay = ScaleBarOverlay(this).apply {
                            setCentred(true)
                            setScaleBarOffset(dm.widthPixels / 2, 2000)
                        }
                        overlays.add(scaleBarOverlay)

                        val mLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
                            enableMyLocation()
                        }
                        overlays.add(mLocationOverlay)
                    }
                    marker = Marker(mapView)

                    if (savedLocationData.points.isNotEmpty()) {
                        createMarker(LatLng(
                            savedLocationData.points[0].latitude,
                            savedLocationData.points[0].longitude
                        ))
                    } else {
                        createMarker(LatLng(0.0, 0.0))
                    }

                    mapView
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    // Update map when switching back to tab
                    if (savedLocationData.points.isNotEmpty()) {
                        val oldPolyline = mapView.overlays.find { it is Polyline } as? Polyline
                        if (oldPolyline != null) {
                            mapView.overlays.remove(oldPolyline)
                        }

                        val newPolyline = Polyline().apply {
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.color = ContextCompat.getColor(context, android.R.color.holo_purple)
                            setPoints(savedLocationData.points)
                        }
                        mapView.overlays.add(newPolyline)
                        polyline = newPolyline
                    }
                    mapView.invalidate()
                }
            )

            if (!isRecording) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-16).dp, y = (-180).dp)
                        .padding(16.dp)
                        .size(50.dp),
                    shape = CircleShape,
                    color = Color.Red,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                showDialog = true
                                context
                                    .getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_recording", true)
                                    .apply()
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Recording",
                            tint = Color.White
                        )
                    }
                }
            }
            // Stop Recording Button (shown only if recording)
            if (isRecording) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = (-16).dp,
                            y = (-180).dp
                        ) // Same position as the recording button
                        .padding(16.dp)
                        .size(50.dp),
                    shape = CircleShape,
                    color = Color.Gray,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                Toast
                                    .makeText(context, "Recording Stopped", Toast.LENGTH_SHORT)
                                    .show()

                                context
                                    .getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_recording", false)
                                    .apply()

                                isRecording = false
                                val stopIntent = Intent(context, ForegroundService::class.java)
                                context.stopService(stopIntent)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop Recording",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        if (showDialog) {
            RecordingButtonWithDialog(
                context = context,
                onSave = { eventName, eventDate, artOfSport, wheelSize, sprocket, comment, clothing ->
                    val stopIntent = Intent(context, BackgroundLocationService::class.java)
                    context.stopService(stopIntent)

                    val intent = Intent(context, ForegroundService::class.java).apply {
                        putExtra("eventName", eventName)
                        putExtra("eventDate", eventDate)
                        putExtra("artOfSport", artOfSport)
                        putExtra("comment", comment)
                        putExtra("clothing", clothing)
                    }
                    ContextCompat.startForegroundService(context, intent)

                    context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_recording", true)
                        .apply()

                    isRecording = true
                    showDialog = false
                },
                onDismiss = {
                    showDialog = false
                }
            )
        }
    }

    @Composable
    fun RecordingButtonWithDialog(
        context: Context,
        onSave: (
            eventName: String,
            eventDate: String,
            artOfSport: String,
            wheelSize: String,
            sprocket: String,
            comment: String,
            clothing: String
        ) -> Unit, // Callback for save action
        onDismiss: () -> Unit // Callback for dismiss action
    ) {
        var eventName by remember { mutableStateOf("") }
        var eventDate by remember { mutableStateOf("") }
        var artOfSport by remember { mutableStateOf("Running") }
        var wheelSize by remember { mutableStateOf("") }
        var sprocket by remember { mutableStateOf("") }
        var comment by remember { mutableStateOf("") }
        var clothing by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("New Event Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = eventName,
                        onValueChange = { eventName = it },
                        label = { Text("Event Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = eventDate,
                        onValueChange = { eventDate = it },
                        label = { Text("" + provideDateTimeFormat()) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var selectedSport by remember { mutableStateOf(artOfSport) }

                    DropdownMenuField(
                        options = listOf("Running", "Cycling", "Swimming"),
                        selectedOption = selectedSport,
                        onOptionSelected = {
                            selectedSport = it
                            artOfSport = it
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (artOfSport == "Cycling") {
                        OutlinedTextField(
                            value = wheelSize,
                            onValueChange = { wheelSize = it },
                            label = { Text("Wheel Size") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = sprocket,
                            onValueChange = { sprocket = it },
                            label = { Text("Sprocket") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Comment") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = clothing,
                        onValueChange = { clothing = it },
                        label = { Text("Clothing") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Save the event details and trigger the foreground service
                    onSave(eventName, eventDate, artOfSport, wheelSize, sprocket, comment, clothing)
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text(getString(R.string.cancel))
                }
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DropdownMenuField(
        options: List<String>,
        selectedOption: String,
        onOptionSelected: (String) -> Unit,
        modifier: Modifier = Modifier,
        label: String = "Select Sport"
    ) {
        var expanded by remember { mutableStateOf(false) }
        val focusManager = LocalFocusManager.current

        Box(modifier = Modifier.fillMaxWidth()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = modifier.fillMaxWidth()
            ) {
                TextField(
                    value = selectedOption,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    trailingIcon = {
                        TrailingIcon(expanded = expanded)
                    },
                    colors = textFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .onFocusEvent {
                            if (it.isFocused) {
                                expanded = true
                            }
                        }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onOptionSelected(option)
                                expanded = false
                                focusManager.clearFocus()
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
        }
    }

    private val database: FitnessTrackerDatabase by lazy {
        FitnessTrackerDatabase.getInstance(applicationContext)
    }

    fun provideDateTimeFormat() : String {
        val zonedDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val epochMilli = zonedDateTime.toInstant().toEpochMilli()
        val startDateTimeFormatted = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMilli),
            ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return startDateTimeFormatted
    }

    // Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSharedPreferences()

        if (!arePermissionsGranted()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        val context = applicationContext
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_pref", MODE_PRIVATE))

        val intent = Intent(context, BackgroundLocationService::class.java)
        context.startService(intent)

        EventBus.getDefault().register(this)

        setContent {
            MainScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(this, ForegroundService::class.java)
        stopService(intent)
        applicationContext.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_recording", false)
            .apply()
        EventBus.getDefault().unregister(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.zip(grantResults.toList())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }

            if (deniedPermissions.isNotEmpty()) {
                // Handle permissions denied by the user
                // You can show a dialog or finish the activity
                Toast.makeText(
                    this,
                    "Some permissions were denied. Functionality may be limited.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @Composable
    fun EventMapView(record: SingleEventWithMetric) {
        val context = LocalContext.current
        val mapView = remember {
            MapView(context).apply {
                //setTileSource(TileSourceFactory.MAPNIK)
                setTileSource(customTileSource)
                setMultiTouchControls(true)
                controller.setZoom(12.0)
            }
        }

        var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(record.eventId) {
            coroutineScope.launch {
                routePoints = database.eventDao().getRoutePointsForEvent(record.eventId)
                    .map { GeoPoint(it.latitude, it.longitude) }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(vertical = 8.dp)
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            ) { map ->
                map.overlays.clear()

                if (routePoints.isNotEmpty()) {
                    // Main polyline
                    val polyline = Polyline().apply {
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.color = android.graphics.Color.BLUE
                        setPoints(routePoints)
                    }
                    map.overlays.add(polyline)

                    // Add direction arrows
                    addDirectionArrows(map, routePoints)

                    // Add start marker
                    addStartMarker(map, routePoints.first())

                    // Add end marker
                    addEndMarker(map, routePoints.last())

                    // Set bounds to show full route
                    val bounds = BoundingBox.fromGeoPoints(routePoints)
                    map.zoomToBoundingBox(bounds, true, 50, 17.0, 1L)
                    map.controller.setCenter(GeoPoint(routePoints.first().latitude, routePoints.first().longitude))
                }
                map.invalidate()
            }
        }
    }
    private fun addStartMarker(mapView: MapView, startPoint: GeoPoint) {
        val startMarker = Marker(mapView).apply {
            position = startPoint
            icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_start_marker)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Start"
        }
        mapView.overlays.add(startMarker)
    }

    private fun addEndMarker(mapView: MapView, endPoint: GeoPoint) {
        val endMarker = Marker(mapView).apply {
            position = endPoint
            icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_end_marker)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "End"
        }
        mapView.overlays.add(endMarker)
    }

    private fun addDirectionArrows(mapView: MapView, points: List<GeoPoint>) {
        if (points.size < 2) return

        val zoomLevel = mapView.zoomLevelDouble
        // Adjust arrow size based on zoom level (smaller when zoomed out)
        val arrowSize = (zoomLevel / 20.0 * 20).coerceIn(10.0, 30.0).toInt()

        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            totalDistance += points[i].distanceToAsDouble(points[i + 1])
        }

        // Adjust arrow spacing based on total distance
        val arrowSpacing = totalDistance / (10 * (zoomLevel / 15)) // More arrows when zoomed in

        var accumulatedDistance = 0.0
        var nextArrowDistance = arrowSpacing

        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]
            val segmentDistance = start.distanceToAsDouble(end)

            while (accumulatedDistance + segmentDistance > nextArrowDistance) {
                val ratio = (nextArrowDistance - accumulatedDistance) / segmentDistance
                val arrowPoint = GeoPoint(
                    start.latitude + (end.latitude - start.latitude) * ratio,
                    start.longitude + (end.longitude - start.longitude) * ratio
                )

                val bearing = start.bearingTo(end)

                val arrowMarker = Marker(mapView).apply {
                    position = arrowPoint
                    icon = createArrowDrawable(bearing, arrowSize)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(arrowMarker)

                nextArrowDistance += arrowSpacing
            }
            accumulatedDistance += segmentDistance
        }
    }

    private fun createArrowDrawable(bearing: Double, size: Int): Drawable {
        return object : Drawable() {
            private val paint = Paint().apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
                alpha = 180 // Semi-transparent arrows (about 70% opacity)
            }

            override fun draw(canvas: Canvas) {
                val centerX = bounds.exactCenterX()
                val centerY = bounds.exactCenterY()

                canvas.save()
                canvas.rotate(bearing.toFloat(), centerX, centerY)

                // Scaled arrow shape
                val arrowHeight = size.toFloat()
                val arrowWidth = size * 0.6f

                val path = Path().apply {
                    moveTo(centerX, centerY - arrowHeight/2)  // Top point
                    lineTo(centerX - arrowWidth/2, centerY + arrowHeight/2)  // Bottom left
                    lineTo(centerX + arrowWidth/2, centerY + arrowHeight/2)  // Bottom right
                    close()
                }
                canvas.drawPath(path, paint)

                canvas.restore()
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
            }

            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

            override fun getIntrinsicWidth(): Int = size
            override fun getIntrinsicHeight(): Int = size
        }
    }

    suspend fun importGPXFile(
        file: File,
        database: FitnessTrackerDatabase,
        onComplete: (Boolean) -> Unit
    ) {
        try {
            // First, check if we have any user in the database
            val users = database.userDao().getAllUsers()
            val userId = if (users.isEmpty()) {
                // Create a default user if none exists
                val defaultUser = User(
                    firstName = "Default",
                    lastName = "User",
                    birthDate = "2000-01-01",
                    weight = 70.0f,
                    height = 170.0f
                )
                database.userDao().insertUser(defaultUser).toInt()
            } else {
                // Use the first user's ID
                users[0].userId
            }

            val dbf = DocumentBuilderFactory.newInstance()
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(file)
            doc.documentElement.normalize()

            val trackNodes = doc.getElementsByTagName("trk")
            if (trackNodes.length == 0) {
                onComplete(false)
                return
            }

            val track = trackNodes.item(0) as Element
            val name = track.getElementsByTagName("name").item(0).textContent

            val trackpoints = track.getElementsByTagName("trkpt")
            if (trackpoints.length == 0) {
                onComplete(false)
                return
            }

            val firstPoint = trackpoints.item(0) as Element
            val timeStr = firstPoint.getElementsByTagName("time").item(0).textContent
            val dateTime = LocalDateTime.parse(timeStr.removeSuffix("Z"), DateTimeFormatter.ISO_DATE_TIME)
            val eventDate = dateTime.toLocalDate().toString()

            // Create event
            val event = Event(
                userId = userId.toLong(),
                eventName = name,
                eventDate = eventDate,
                artOfSport = "Not Specified",
                comment = "Imported from GPX"
            )
            val eventId = database.eventDao().insertEvent(event).toInt()

            // Lists to store locations and metrics
            val locations = mutableListOf<Location>()
            val metrics = mutableListOf<Metric>()

            var totalDistance = 0.0
            var prevLat = 0.0
            var prevLon = 0.0
            var prevEle = 0.0
            var prevTime: Instant? = null
            var totalElevationGain = 0.0
            var totalElevationLoss = 0.0

            // Process trackpoints
            for (i in 0 until trackpoints.length) {
                val trkpt = trackpoints.item(i) as Element
                val lat = trkpt.getAttribute("lat").toDouble()
                val lon = trkpt.getAttribute("lon").toDouble()
                val ele = trkpt.getElementsByTagName("ele")?.item(0)?.textContent?.toDoubleOrNull() ?: 0.0
                val currentTimeStr = trkpt.getElementsByTagName("time").item(0).textContent
                val currentTime = Instant.parse(currentTimeStr)

                // Add location
                locations.add(
                    Location(
                        eventId = eventId,
                        latitude = lat,
                        longitude = lon,
                        altitude = ele
                    )
                )

                    // Calculate metrics
                    if (prevLat != 0.0 && prevLon != 0.0) {
                        val horizontalDistance = calculateDistance(prevLat, prevLon, lat, lon)
                        val verticalDistance = ele - prevEle
                        val segmentDistance = calculateDistance(prevLat, prevLon, prevEle, lat, lon, ele)

                        Log.d("Point: Horizontal=", "$horizontalDistance, Vertical=$verticalDistance, Total=$segmentDistance")
                        Log.d("Coordinates: ", "($prevLat,$prevLon,$prevEle) -> ($lat,$lon,$ele)")

                        totalDistance += segmentDistance
                        Log.d("Total distance so far: ", "$totalDistance")

                    // Calculate elevation changes
                    val elevationDiff = ele - prevEle
                    if (elevationDiff > 0) {
                        totalElevationGain += elevationDiff
                    } else {
                        totalElevationLoss += -elevationDiff
                    }

                    val duration = Duration.between(prevTime, currentTime)
                    val speedMPS = if (duration.seconds > 0) {
                        segmentDistance / duration.seconds
                    } else 0.0f

                    // Create metric entry with elevation data
                    metrics.add(
                        Metric(
                            eventId = eventId,
                            heartRate = 0, // No heart rate data in basic GPX
                            heartRateDevice = "None",
                            speed = speedMPS.toFloat(),
                            distance = totalDistance,
                            cadence = null,
                            lap = 1,
                            timeInMilliseconds = duration.toMillis(),
                            unity = "metric",
                            elevation = ele.toFloat(),
                            elevationGain = totalElevationGain.toFloat(),
                            elevationLoss = totalElevationLoss.toFloat()
                        )
                    )
                }

                prevLat = lat
                prevLon = lon
                prevEle = ele
                prevTime = currentTime
            }

            // Insert all data
            database.locationDao().insertAll(locations)
            database.metricDao().insertAll(metrics)

            val metricsDebug = database.metricDao().getMetricsForEvent(eventId)
            metricsDebug.forEach { metric ->
                println("Metric ID: ${metric.metricId}, Time: ${metric.timeInMilliseconds}, Distance: ${metric.distance}")
            }

            onComplete(true)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false)
        }
    }
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rad = Math.PI / 180
        val φ1 = lat1 * rad
        val φ2 = lat2 * rad

        // Using exact same formula as GPX Studio
        val a = kotlin.math.sin(φ1) * kotlin.math.sin(φ2) +
                kotlin.math.cos(φ1) * kotlin.math.cos(φ2) *
                kotlin.math.cos((lon2 - lon1) * rad)

        // Important: use coerceAtMost(1.0) just like GPX Studio's Math.min(a, 1)
        return EARTH_RADIUS * kotlin.math.acos(a.coerceAtMost(1.0))
    }

    // Overload that includes elevation
    fun calculateDistance(
        lat1: Double, lon1: Double, ele1: Double,
        lat2: Double, lon2: Double, ele2: Double
    ): Double {
        // First calculate horizontal distance
        val horizontalDistance = calculateDistance(lat1, lon1, lat2, lon2)

        // Calculate elevation difference
        val verticalDistance = ele2 - ele1

        // Use Pythagorean theorem to get true 3D distance
        return kotlin.math.sqrt(horizontalDistance * horizontalDistance +
                verticalDistance * verticalDistance)
    }

    data class SavedLocationData(
        val points: List<GeoPoint>,
        val isRecording: Boolean
    )

    data class GpsPoint(
        val lat: Double,
        val lon: Double,
        val elevation: Double? = null
    )
}