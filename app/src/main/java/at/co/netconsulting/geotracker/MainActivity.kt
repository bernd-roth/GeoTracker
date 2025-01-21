package at.co.netconsulting.geotracker

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import at.co.netconsulting.geotracker.data.EditState
import at.co.netconsulting.geotracker.data.EventDetails
import at.co.netconsulting.geotracker.data.LapTimeInfo
import at.co.netconsulting.geotracker.data.LocationEvent
import at.co.netconsulting.geotracker.data.MemoryPressureEvent
import at.co.netconsulting.geotracker.data.SavedLocationData
import at.co.netconsulting.geotracker.data.SingleEventWithMetric
import at.co.netconsulting.geotracker.data.StopServiceEvent
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.User
import at.co.netconsulting.geotracker.gpx.export
import at.co.netconsulting.geotracker.location.CustomLocationListener
import at.co.netconsulting.geotracker.service.BackgroundLocationService
import at.co.netconsulting.geotracker.service.DatabaseBackupService
import at.co.netconsulting.geotracker.service.ForegroundService
import at.co.netconsulting.geotracker.service.GpxExportService
import at.co.netconsulting.geotracker.tools.Tools
import at.co.netconsulting.geotracker.tools.getTotalAscent
import at.co.netconsulting.geotracker.tools.getTotalDescent
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
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

class MainActivity : ComponentActivity() {
    private var latitudeState = mutableDoubleStateOf(-999.0)
    private var longitudeState = mutableDoubleStateOf(-999.0)
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
    //persisting route point and zoom level
    private var persistedRoutePoints = mutableListOf<GeoPoint>()
    private var persistedMarkerPoint: LatLng? = null
    private var persistedZoomLevel: Double? = null
    private var persistedMapCenter: GeoPoint? = null
    //showing route on main map
    private var selectedEventPolylines = mutableListOf<Polyline>()
    private var selectedEventsState = mutableStateOf<List<SingleEventWithMetric>>(emptyList())
    private var selectedRecordsState = mutableStateOf<List<SingleEventWithMetric>>(emptyList())
    //satellite info
    private lateinit var satelliteInfoManager: SatelliteInfoManager

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
        horizontalAccuracyInMetersState.value = event.horizontalAccuracy
        usedInFixCount = satelliteInfoManager.currentSatelliteInfo.value.visibleSatellites
        satelliteCount = satelliteInfoManager.currentSatelliteInfo.value.totalSatellites

        if (isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")) {
            val newPoints = locationChangeEventState.value.latLngs.map {
                GeoPoint(it.latitude, it.longitude)
            }

            // Only update if we have valid coordinates
            if (newPoints.isNotEmpty() &&
                !(newPoints.last().latitude == 0.0 && newPoints.last().longitude == 0.0)) {
                savedLocationData = SavedLocationData(newPoints, true)
                persistedRoutePoints = newPoints.toMutableList()

                // Only update polyline if we have valid points
                if (newPoints.isNotEmpty()) {
                    val oldPolyline = mapView.overlays.find { it is Polyline } as? Polyline
                    if (oldPolyline != null) {
                        // Instead of removing, update existing polyline's points
                        oldPolyline.setPoints(newPoints)
                        mapView.invalidate()
                    } else {
                        // Create new polyline only if none exists
                        drawPolyline(locationChangeEventState.value)
                    }
                }
            }
        }
    }

    private fun isServiceRunning(serviceName: String): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { service ->
                serviceName == service.service.className && service.foreground
            }
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
            mapView.controller.setCenter(GeoPoint(firstPoint.latitude, firstPoint.longitude))
        } else {
            mapView.controller.setCenter(GeoPoint(0.0,0.0))
            mapView.controller.setZoom(5.0) // Only set zoom for initial state
        }
        mapView.invalidate()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        remember { mutableStateOf<GeoPoint?>(null) }
        remember { mutableStateOf(17.0) }
        remember { mutableStateOf<GeoPoint?>(null) }

        // Coroutine scope to auto-hide the top bar after a delay
        rememberCoroutineScope()

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
        //satellite info
        val satelliteInfo by satelliteInfoManager.currentSatelliteInfo.collectAsState()

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                if(latitudeState.value!=-999.0 && longitudeState.value!=-999.0) {
                    BottomSheetContent(
                        latitude = latitudeState.value,
                        longitude = longitudeState.value,
                        speed = speedState.value,
                        speedAccuracyInMeters = speedState.value,
                        altitude = altitudeState.value,
                        verticalAccuracyInMeters = verticalAccuracyInMetersState.value,
                        horizontalAccuracyInMeters = horizontalAccuracyInMetersState.value,
                        numberOfSatellites = satelliteInfo.totalSatellites,
                        usedNumberOfSatellites = satelliteInfo.visibleSatellites,
                        coveredDistance = coveredDistanceState.value
                    )
                } else {
                    BottomSheetContent(
                        latitude = Double.NaN,
                        longitude = Double.NaN,
                        speed = speedState.value,
                        speedAccuracyInMeters = speedState.value,
                        altitude = altitudeState.value,
                        verticalAccuracyInMeters = verticalAccuracyInMetersState.value,
                        horizontalAccuracyInMeters = horizontalAccuracyInMetersState.value,
                        numberOfSatellites = satelliteInfo.totalSatellites,
                        usedNumberOfSatellites = satelliteInfo.visibleSatellites,
                        coveredDistance = coveredDistanceState.value
                    )
                }
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
                            0 -> MapScreen()
                            1 -> StatisticsScreenPreview(
                                context = applicationContext,
                                locationEventState = locationEventState,
                                onEventsSelected = { events ->
                                    displaySelectedEvents(events)
                                },
                                selectedRecords = selectedRecordsState.value,
                                onSelectedRecordsChange = { newSelectedRecords ->
                                    selectedRecordsState.value = newSelectedRecords
                                }
                            )
                            2 -> SettingsScreen()
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun MapScreen(
    ) {
        Column {
            OpenStreetMapView(selectedEvents = selectedEventsState.value)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun StatisticsScreenPreview(
        context: Context,
        locationEventState: MutableState<LocationEvent?>,
        latLngs: List<LatLng> = emptyList(),
        onEventsSelected: (List<SingleEventWithMetric>) -> Unit,
        selectedRecords: List<SingleEventWithMetric>,
        onSelectedRecordsChange: (List<SingleEventWithMetric>) -> Unit
    ) {
        var editState by remember { mutableStateOf(EditState()) }
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
            locationChangeEventList = CustomLocationListener.LocationChangeEvent(latLngs),
            totalAscent = 0.0,
            totalDescent = 0.0
        )
        var users by remember { mutableStateOf<List<User>>(emptyList()) }
        var events by remember { mutableStateOf<List<Event>>(emptyList()) }
        var records by remember { mutableStateOf<List<SingleEventWithMetric>>(emptyList()) }
        var expanded by remember { mutableStateOf(false) }
        var lapTimesMap by remember { mutableStateOf<Map<Int, List<LapTimeInfo>>>(emptyMap()) }
        var showDeleteErrorDialog by remember { mutableStateOf(false) }

        LaunchedEffect(selectedRecords) {
            onEventsSelected(selectedRecords)
        }

        // Search state
        var searchQuery by remember { mutableStateOf("") }

        val coroutineScope = rememberCoroutineScope()

        suspend fun delete(eventId: Int) {
            val currentRecordingEventId = getCurrentlyRecordingEventId()

            if (eventId == currentRecordingEventId &&
                isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")) {
                showDeleteErrorDialog = true
                return
            }

            try {
                database.metricDao().deleteMetricsByEventId(eventId)
                database.locationDao().deleteLocationsByEventId(eventId)
                database.weatherDao().deleteWeatherByEventId(eventId)
                database.deviceStatusDao().deleteDeviceStatusByEventId(eventId)
                database.eventDao().delete(eventId)

                Toast.makeText(
                    context,
                    "Event deleted successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Update the records list after successful deletion
                records = records.filter { it.eventId != eventId }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error deleting event", e)
                Toast.makeText(
                    context,
                    "Error deleting event: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        suspend fun loadData() {
            try {
                users = database.userDao().getAllUsers()
                Log.d("Loaded", "${users.size} users")

                events = database.eventDao().getEventDateEventNameGroupByEventDate()
                Log.d("Loaded", "${events.size} events")

                records = database.eventDao().getDetailsFromEventJoinedOnMetricsWithRecordingData()
                Log.d("Loaded", "${records.size} records")

                // Load lap times separately
                lapTimesMap = database.eventDao().getLapTimesForEvents()
                    .groupBy { it.eventId }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("Error", "loading data: ${e.message}")
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

            // Event selector
            Text(text = "Select an event", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = selectedRecords.joinToString(", ") { it.eventName },
                onValueChange = { /* No-op */ },
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
            )

            if (expanded) {
                Dialog(onDismissRequest = { expanded = false }) {
                    EventSelectionDialog(
                        records = records,
                        selectedRecords = selectedRecords,
                        editState = editState,
                        lapTimesMap = lapTimesMap,
                        onRecordSelected = { record ->
                            onSelectedRecordsChange(
                                if (selectedRecords.contains(record)) {
                                    selectedRecords.filter { it != record }
                                } else {
                                    selectedRecords + record
                                }
                            )
                        },
                        onDismiss = { expanded = false },
                        onDelete = { eventId ->
                            coroutineScope.launch {
                                delete(eventId)
                            }
                        },
                        onExport = { eventId ->
                            coroutineScope.launch {
                                export(eventId, applicationContext)
                            }
                        },
                        onEdit = { eventId, newName, newDate ->
                            if (eventId == -1) {
                                // Cancel edit
                                editState = EditState()
                            } else if (editState.isEditing) {
                                // Save edit
                                coroutineScope.launch {
                                    updateEvent(eventId, newName, newDate)
                                    loadData() // Reload data after update
                                    editState = EditState() // Reset edit state
                                }
                            } else {
                                // Start edit
                                editState = EditState(
                                    isEditing = true,
                                    eventId = eventId,
                                    currentEventName = newName,
                                    currentEventDate = newDate
                                )
                            }
                        },
                        onDeleteAllContent = {
                            coroutineScope.launch {
                                deleteContentAllTables()
                                records = emptyList()
                                expanded = false
                            }
                        },
                        onExportGPX = {
                            coroutineScope.launch {
                                exportGPX()
                            }
                        },
                        onImportGPX = {
                            showGPXDialog = true
                            expanded = false
                        },
                        onBackupDatabase = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val backupIntent = Intent(context, DatabaseBackupService::class.java).apply {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        addFlags(Intent.FLAG_FROM_BACKGROUND)
                                    }
                                }
                                ContextCompat.startForegroundService(context, backupIntent)
                            } else {
                                context.startService(Intent(context, DatabaseBackupService::class.java))
                            }
                            Toast.makeText(
                                context,
                                "Database backup started",
                                Toast.LENGTH_SHORT
                            ).show()
                            expanded = false
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
    fun EventSelectionDialog(
        records: List<SingleEventWithMetric>,
        selectedRecords: List<SingleEventWithMetric>,
        editState: EditState,
        lapTimesMap: Map<Int, List<LapTimeInfo>>,
        onRecordSelected: (SingleEventWithMetric) -> Unit,
        onDismiss: () -> Unit,
        onDelete: (Int) -> Unit,
        onExport: (Int) -> Unit,
        onEdit: (Int, String, String) -> Unit,
        onDeleteAllContent: () -> Unit,
        onExportGPX: () -> Unit,
        onImportGPX: () -> Unit,
        onBackupDatabase: () -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }

        Surface(
            modifier = Modifier
                .width(400.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
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

                filteredRecords.forEach { record ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable {
                                if (!editState.isEditing) {
                                    onRecordSelected(record)
                                }
                            },
                        color = if (selectedRecords.contains(record)) {
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
                                var editedName by remember { mutableStateOf(editState.currentEventName) }
                                var editedDate by remember { mutableStateOf(editState.currentEventDate) }

                                OutlinedTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    label = { Text("Event Name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = editedDate,
                                    onValueChange = { editedDate = it },
                                    label = { Text("Event Date (YYYY-MM-DD)") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { onEdit(-1, "", "") }) {
                                        Text("Cancel")
                                    }
                                    TextButton(
                                        onClick = { onEdit(record.eventId, editedName, editedDate) }
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

                                if (record.eventId == getCurrentlyRecordingEventId() &&
                                    isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")
                                ) {
                                    Text(
                                        text = "⚫ Ongoing Recording",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }

                                // Lap times section with colored backgrounds for fastest/slowest laps
                                lapTimesMap[record.eventId]?.let { lapTimes ->
                                    if (lapTimes.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Lap Times",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
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

                                            // Find fastest and slowest completed laps (only considering laps that cover 1km)
                                            // Get last lap number to exclude it from consideration
                                            val lastLapNumber = lapTimes.maxOfOrNull { it.lapNumber } ?: 0

                                            val completedLaps = lapTimes.filter { lapTime ->
                                                // Filter laps that are:
                                                // 1. Started (lap number > 0)
                                                // 2. Not the current/last lap
                                                // 3. Have valid time (> 0 and < MAX_VALUE)
                                                lapTime.lapNumber > 0 &&
                                                        lapTime.lapNumber < lastLapNumber &&
                                                        lapTime.timeInMillis > 0 &&
                                                        lapTime.timeInMillis < Long.MAX_VALUE
                                            }

                                            val fastestLap = completedLaps.minByOrNull { it.timeInMillis }
                                            val slowestLap = completedLaps.maxByOrNull { it.timeInMillis }

                                            lapTimes.forEach { lapTime ->
                                                val backgroundColor = when {
                                                    lapTime.lapNumber == lastLapNumber -> Color.Transparent // Current lap
                                                    lapTime.timeInMillis <= 0 || lapTime.timeInMillis == Long.MAX_VALUE -> Color.Transparent // Invalid/incomplete lap
                                                    lapTime == fastestLap -> Color(0xFF90EE90)
                                                    lapTime == slowestLap -> Color(0xFFF44336)
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
                                        }
                                    }
                                }

                                // Action buttons
                                if (!(record.eventId == getCurrentlyRecordingEventId() &&
                                            isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService"))) {
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
        val intent = Intent(applicationContext, GpxExportService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(applicationContext, "GPX export started. Files will be saved to Downloads/GeoTracker", Toast.LENGTH_LONG).show()
    }

    private suspend fun deleteContentAllTables() {
        database.eventDao().deleteAllContent()
    }

    private fun edit(eventId: Int) {
        Toast.makeText(applicationContext, "Edit not implemented yet", Toast.LENGTH_LONG).show()
    }

//    private fun export(eventId: Int) {
//        Toast.makeText(applicationContext, "Single export not implemented yet", Toast.LENGTH_LONG).show()
//    }

    private fun getCurrentlyRecordingEventId(): Int {
        return if (isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")) {
            getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                .getInt("active_event_id", -1)
        } else {
            -1
        }
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
            Text("Total ascent: ${"%.3f".format(getTotalAscent())} meter", style = MaterialTheme.typography.bodyLarge)
            Text("Total descent: ${"%.3f".format(getTotalDescent())} meter", style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    fun SelectedEventPanel(record: SingleEventWithMetric) {
        var eventDetails by remember { mutableStateOf<EventDetails?>(null) }
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val database = remember { FitnessTrackerDatabase.getInstance(context) }

        LaunchedEffect(record.eventId) {
            coroutineScope.launch {
                // Get metrics for calculations
                val metrics = database.metricDao().getMetricsByEventId(record.eventId)

                // Get last weather reading
                val weather = database.weatherDao().getLastWeatherByEvent(record.eventId)

                // Calculate statistics
                val maxSpeed = metrics.maxOfOrNull { it.speed } ?: 0f // Convert m/s to km/h
                val avgSpeed = metrics.map { it.speed }.average().toFloat()

                // Calculate actual duration from first to last metric
                val duration = if (metrics.isNotEmpty()) {
                    val firstTime = metrics.minOf { it.timeInMilliseconds }
                    val lastTime = metrics.maxOf { it.timeInMilliseconds }
                    lastTime - firstTime // This gives us the actual duration
                } else 0L

                val totalAscent = metrics.sumOf { it.elevationGain.toDouble() }
                val totalDescent = metrics.sumOf { it.elevationLoss.toDouble() }

                eventDetails = EventDetails(
                    duration = Tools().formatTime(duration),
                    maxSpeed = maxSpeed,
                    avgSpeed = avgSpeed,
//                    totalAscent = totalAscent,
//                    totalDescent = totalDescent,
                    temperature = weather?.temperature ?: 0f,
                    windSpeed = weather?.windSpeed ?: 0f,
                    humidity = weather?.relativeHumidity ?: 0
                )
            }
        }

        Column(modifier = Modifier.padding(8.dp)) {
            // Existing fields
            Text("Event date: ${record.eventDate?.takeIf { it.isNotEmpty() } ?: "No date provided"}",
                style = MaterialTheme.typography.bodyLarge)
            Text("Event name: ${record.eventName?.takeIf { it.isNotEmpty() } ?: "No event name provided"}",
                style = MaterialTheme.typography.bodyLarge)
            Text("Covered distance: ${"%.3f".format(record.distance?.div(1000) ?: 0.0)} Km",
                style = MaterialTheme.typography.bodyLarge)

            // Additional statistics
            eventDetails?.let { details ->
                Text("Duration: ${details.duration}",
                    style = MaterialTheme.typography.bodyLarge)
                Text("Max. speed: ${"%.2f".format(details.maxSpeed)} km/h",
                    style = MaterialTheme.typography.bodyLarge)
                Text("Avg. speed: ${"%.2f".format(details.avgSpeed)} km/h",
                    style = MaterialTheme.typography.bodyLarge)
//                Text("Total ascent: ${"%.1f".format(details.totalAscent)} m",
//                    style = MaterialTheme.typography.bodyLarge)
//                Text("Total descent: ${"%.1f".format(details.totalDescent)} m",
//                    style = MaterialTheme.typography.bodyLarge)
                Text("Temperature: ${"%.1f".format(details.temperature)}°C",
                    style = MaterialTheme.typography.bodyLarge)
                Text("Wind speed: ${"%.1f".format(details.windSpeed)} km/h",
                    style = MaterialTheme.typography.bodyLarge)
                Text("Humidity: ${details.humidity}%",
                    style = MaterialTheme.typography.bodyLarge)
            }

            // Existing map view
            EventMapView(record = record)
        }
    }

    private fun saveAllSettings(
        sharedPreferences: SharedPreferences,
        firstName: String,
        lastName: String,
        birthDate: String,
        height: Float,
        weight: Float,
        websocketserver: String
    ) {
        sharedPreferences.edit().apply {
            putString("firstname", firstName)
            putString("lastname", lastName)
            putString("birthdate", birthDate)
            putFloat("height", height)
            putFloat("weight", weight)
            putString("websocketserver", websocketserver)
            apply()
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

        isBatteryOptimizationIgnored(context)

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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = weight.toString(),
                onValueChange = { input ->
                    weight = input.toFloatOrNull() ?: weight
                },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = websocketserver,
                onValueChange = { websocketserver = it },
                label = { Text("Websocket ip address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                        websocketserver
                    )

                    Toast.makeText(context, "All settings saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }

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

    @Composable
    fun OpenStreetMapView(selectedEvents: List<SingleEventWithMetric> = emptyList()) {
        val context = LocalContext.current

        var showDialog by remember { mutableStateOf(false) }
        var isRecording by remember {
            mutableStateOf(
                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                    .getBoolean("is_recording", false)
            )
        }

        // Add DisposableEffect to save map state when leaving the composable
        DisposableEffect(Unit) {
            onDispose {
                // Save current map state when leaving the map view
                persistedZoomLevel = mapView.zoomLevelDouble
                persistedMapCenter = mapView.mapCenter as GeoPoint?
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = {
                    mapView = MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)

                        // Initialize with persisted data if available
                        if (persistedRoutePoints.isNotEmpty()) {
                            polyline = Polyline().apply {
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.color =
                                    ContextCompat.getColor(context, android.R.color.holo_purple)
                                setPoints(persistedRoutePoints)
                            }
                            overlays.add(polyline)

                            // Restore marker if available
                            persistedMarkerPoint?.let { point ->
                                createMarker(point)
                            }

                            // Restore zoom and center position
                            controller.setCenter(persistedMapCenter ?: persistedRoutePoints[0])
                            persistedZoomLevel?.let { zoom ->
                                controller.setZoom(zoom)
                            }
                        } else if (savedLocationData.points.isNotEmpty()) {
                            polyline = Polyline().apply {
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.color =
                                    ContextCompat.getColor(context, android.R.color.holo_purple)
                                setPoints(savedLocationData.points)
                            }
                            overlays.add(polyline)

                            // Use saved location data for initial position if no persisted state
                            if (persistedMapCenter == null) {
                                controller.setCenter(savedLocationData.points[0])
                            }
                            if (persistedZoomLevel == null) {
                                controller.setZoom(15.0)
                            }
                        }

                        // Restore previous zoom and center if available
                        persistedMapCenter?.let { center ->
                            controller.setCenter(center)
                        }
                        persistedZoomLevel?.let { zoom ->
                            controller.setZoom(zoom)
                        }

                        // Rest of the MapView setup...
                        val dm: DisplayMetrics = context.resources.displayMetrics
                        val scaleBarOverlay = ScaleBarOverlay(this).apply {
                            setCentred(true)
                            setScaleBarOffset(dm.widthPixels / 2, 2000)
                        }
                        overlays.add(scaleBarOverlay)

                        val mLocationOverlay =
                            MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
                                enableMyLocation()
                            }
                        overlays.add(mLocationOverlay)
                    }
                    marker = Marker(mapView)

                    if (persistedMarkerPoint != null) {
                        createMarker(persistedMarkerPoint!!)
                    } else if (savedLocationData.points.isNotEmpty()) {
                        createMarker(
                            LatLng(
                                savedLocationData.points[0].latitude,
                                savedLocationData.points[0].longitude
                            )
                        )
                    } else {
                        createMarker(LatLng(0.0, 0.0))
                    }
                    if (selectedEvents.isNotEmpty()) {
                        displaySelectedEvents(selectedEvents)
                    }
                    mapView
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    // Update map when switching back to tab
                    val points = if (persistedRoutePoints.isNotEmpty()) {
                        persistedRoutePoints
                    } else if (savedLocationData.points.isNotEmpty()) {
                        savedLocationData.points
                    } else {
                        emptyList()
                    }

                    if (points.isNotEmpty()) {
                        val oldPolyline = mapView.overlays.find { it is Polyline } as? Polyline
                        if (oldPolyline != null) {
                            mapView.overlays.remove(oldPolyline)
                        }

                        val newPolyline = Polyline().apply {
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.color =
                                ContextCompat.getColor(context, android.R.color.holo_purple)
                            setPoints(points)
                        }
                        mapView.overlays.add(newPolyline)
                        polyline = newPolyline

                        // Only set center and zoom if they haven't been persisted yet
                        if (persistedMapCenter == null) {
                            mapView.controller.setCenter(points[0])
                        }
                        if (persistedZoomLevel == null) {
                            mapView.controller.setZoom(15.0)
                        }
                        selectedEventPolylines.forEach { polyline ->
                            if (!mapView.overlays.contains(polyline)) {
                                mapView.overlays.add(polyline)
                            }
                        }
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
                                //stop the ForegroundService
                                val stopIntent = Intent(context, ForegroundService::class.java)
                                context.stopService(stopIntent)
                                //start the BackgroundLocationService again
                                val intent = Intent(context, BackgroundLocationService::class.java)
                                context.startService(intent)
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
                onSave = { eventName, eventDate, artOfSport, wheelSize, sprocket, comment, clothing ->
                    // Clear existing polyline and route data
                    mapView.overlays.removeAll { it is Polyline }
                    persistedRoutePoints.clear()
                    savedLocationData = SavedLocationData(emptyList(), false)

                    // Remove existing marker
                    mapView.overlays.removeAll { it is Marker }

                    // Reset states
                    locationChangeEventState.value =
                        CustomLocationListener.LocationChangeEvent(emptyList())

                    // Stop background service and start foreground service
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

                    // Invalidate the map to refresh the view
                    mapView.invalidate()
                },
                onDismiss = {
                    showDialog = false
                }
            )
        }
    }

    @Composable
    fun RecordingButtonWithDialog(
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
                        label = { Text("" + Tools().provideDateTimeFormat()) },
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

    // Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        satelliteInfoManager = SatelliteInfoManager(this)
        checkServiceStateOnStart()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

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

    override fun onResume() {
        super.onResume()
        satelliteInfoManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        satelliteInfoManager.stopListening()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Critical memory situation - try to keep service alive
                System.gc() // Request garbage collection
                Runtime.getRuntime().gc() // Alternative way to request GC

                // Notify service to reduce memory usage if possible
                EventBus.getDefault().post(MemoryPressureEvent(level))
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                // Memory pressure has been relieved
                EventBus.getDefault().post(MemoryPressureEvent(level))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Post the event before stopping the service
        EventBus.getDefault().post(StopServiceEvent())
        // Remove this line since we're using EventBus now
        // val serviceIntent = Intent(this, ForegroundService::class.java)
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
        val eventMapView = remember {
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
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
                factory = { eventMapView },
                modifier = Modifier.fillMaxSize(),
                update = { map ->
                    if (routePoints.isNotEmpty()) {
                        map.overlays.clear()

                        // Main polyline
                        val polyline = Polyline().apply {
                            outlinePaint.strokeWidth = 5f
                            outlinePaint.color = android.graphics.Color.BLUE
                            setPoints(routePoints)
                        }
                        map.overlays.add(polyline)

                        try {
                            // Add direction arrows
                            addDirectionArrows(map, routePoints)

                            // Add start and end markers
                            addStartMarker(map, routePoints.first())
                            addEndMarker(map, routePoints.last())

                            // Set bounds to show full route
                            val bounds = BoundingBox.fromGeoPoints(routePoints)
                            map.zoomToBoundingBox(bounds, true, 50, 17.0, 1L)
                            map.controller.setCenter(routePoints.first())
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error adding markers or arrows", e)
                        }

                        map.invalidate()
                    }
                }
            )
        }
    }
    private fun addStartMarker(mapView: MapView, startPoint: GeoPoint) {
        try {
            // Make sure mapView is initialized and not in an inconsistent state
            if (!mapView.isLayoutOccurred || !mapView.isAttachedToWindow) {
                Log.d("MainActivity", "MapView not ready for markers")
                return
            }

            val startMarker = Marker(mapView).apply {
                position = startPoint
                icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_start_marker)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Start"
            }
            mapView.overlays.add(startMarker)
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error adding start marker", e)
        }
    }

    private fun addEndMarker(mapView: MapView, endPoint: GeoPoint) {
        try {
            // Make sure mapView is initialized and not in an inconsistent state
            if (!mapView.isLayoutOccurred || !mapView.isAttachedToWindow) {
                Log.d("MainActivity", "MapView not ready for markers")
                return
            }

            val endMarker = Marker(mapView).apply {
                position = endPoint
                icon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_end_marker)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "End"
            }
            mapView.overlays.add(endMarker)
            mapView.invalidate()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error adding end marker", e)
        }
    }

    private fun addDirectionArrows(mapView: MapView, points: List<GeoPoint>) {
        // Early return if not enough points or MapView isn't ready
        if (points.size < 2 || !mapView.isLayoutOccurred || !mapView.isAttachedToWindow) {
            Log.d("MainActivity", "MapView not ready for direction arrows or insufficient points")
            return
        }

        try {
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
                if (!mapView.isLayoutOccurred || !mapView.isAttachedToWindow) {
                    Log.d("MainActivity", "MapView became invalid during arrow creation")
                    return
                }

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

                    try {
                        val arrowOverlay = object : Overlay(mapView.context) {
                            private val paint = Paint().apply {
                                color = android.graphics.Color.WHITE
                                style = Paint.Style.FILL
                                isAntiAlias = true
                                alpha = 180
                            }

                            override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
                                if (shadow) return

                                val point = mapView.projection.toPixels(arrowPoint, null)
                                val centerX = point.x.toFloat()
                                val centerY = point.y.toFloat()

                                canvas.save()
                                canvas.rotate(bearing.toFloat(), centerX, centerY)

                                val arrowHeight = arrowSize.toFloat()
                                val arrowWidth = arrowSize * 0.6f

                                val path = Path().apply {
                                    moveTo(centerX, centerY - arrowHeight/2)
                                    lineTo(centerX - arrowWidth/2, centerY + arrowHeight/2)
                                    lineTo(centerX + arrowWidth/2, centerY + arrowHeight/2)
                                    close()
                                }
                                canvas.drawPath(path, paint)
                                canvas.restore()
                            }
                        }

                        mapView.overlays.add(arrowOverlay)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error creating arrow overlay", e)
                    }

                    nextArrowDistance += arrowSpacing
                }
                accumulatedDistance += segmentDistance
            }

            mapView.invalidate()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error adding direction arrows", e)
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

    private suspend fun importGPXFile(
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
                    val horizontalDistance = Tools().calculateDistance(prevLat, prevLon, lat, lon)
                    val verticalDistance = ele - prevEle
                    val segmentDistance = Tools().calculateDistance(prevLat, prevLon, prevEle, lat, lon, ele)

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

            onComplete(true)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete(false)
        }
    }
    private fun checkServiceStateOnStart() {
        val isServiceRunning = isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")
        val sharedPreferences = getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
        val currentEventPrefs = getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)

        if (!isServiceRunning) {
            // Service not running - clean up any stale state
            sharedPreferences.edit()
                .putBoolean("is_recording", false)
                .apply()
            currentEventPrefs.edit()
                .remove("active_event_id")
                .apply()
        } else {
            // Service is running - ensure UI state is correct
            sharedPreferences.edit()
                .putBoolean("is_recording", true)
                .apply()
        }
    }
    //Edit button
    private suspend fun updateEvent(eventId: Int, newEventName: String, newEventDate: String) {
        try {
            database.eventDao().updateEventDetails(
                eventId = eventId,
                eventName = newEventName,
                eventDate = newEventDate
            )
            Toast.makeText(
                applicationContext,
                "Event updated successfully",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating event", e)
            Toast.makeText(
                applicationContext,
                "Error updating event: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun displaySelectedEvents(events: List<SingleEventWithMetric>) {
        selectedEventsState.value = events

        if (!::mapView.isInitialized) {
            Log.d("MainActivity", "MapView not ready for display events")
            return
        }

        // Clear previous selected event polylines
        mapView.overlays.removeAll { it in selectedEventPolylines }
        selectedEventPolylines.clear()

        // Create and add new polylines for each selected event
        events.forEach { event ->
            lifecycleScope.launch {
                try {
                    val routePoints = database.eventDao().getRoutePointsForEvent(event.eventId)
                        .map { GeoPoint(it.latitude, it.longitude) }

                    if (routePoints.isNotEmpty()) {
                        // Create and add the polyline
                        val polyline = Polyline().apply {
                            outlinePaint.strokeWidth = 5f
                            outlinePaint.color = android.graphics.Color.BLUE
                            setPoints(routePoints)
                        }

                        selectedEventPolylines.add(polyline)
                        mapView.overlays.add(polyline)

                        try {
                            // Add markers and arrows
                            addDirectionArrows(mapView, routePoints)
                            addStartMarker(mapView, routePoints.first())
                            addEndMarker(mapView, routePoints.last())
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error adding markers or arrows for event ${event.eventId}", e)
                        }

                        // Zoom to show all points if this is the first selected event
                        if (selectedEventPolylines.size == 1) {
                            val bounds = BoundingBox.fromGeoPoints(routePoints)
                            mapView.zoomToBoundingBox(bounds, true, 50, 17.0, 1L)
                            mapView.controller.setCenter(routePoints.first())
                        }

                        mapView.invalidate()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error displaying event ${event.eventId}", e)
                }
            }
        }
    }
}