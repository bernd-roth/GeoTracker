package at.co.netconsulting.geotracker

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import at.co.netconsulting.geotracker.data.LocationEvent
import at.co.netconsulting.geotracker.service.BackgroundLocationService
import at.co.netconsulting.geotracker.service.ForegroundService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : ComponentActivity() {
    private var latitudeState = mutableDoubleStateOf(0.0)
    private var longitudeState = mutableDoubleStateOf(0.0)
    private var horizontalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var altitudeState = mutableDoubleStateOf(0.0)
    private var speedState = mutableFloatStateOf(0.0f)
    private var speedAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var verticalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var coveredDistanceState = mutableDoubleStateOf(0.0)
    private lateinit var mapView: MapView
    private lateinit var polyline: Polyline
    private var usedInFixCount = 0
    private var satelliteCount = 0
    private lateinit var marker: Marker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = applicationContext
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_pref", MODE_PRIVATE))

        val intent = Intent(context, BackgroundLocationService::class.java)
        context.startService(intent)

        EventBus.getDefault().register(this)

        setContent {
            MainScreen()
        }
    }

    // Publisher/Subscriber
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
        latitudeState.value = event.latitude
        longitudeState.value = event.longitude
        speedState.value = event.speed
        speedAccuracyInMetersState.value = event.speedAccuracyMetersPerSecond
        altitudeState.value = event.altitude
        verticalAccuracyInMetersState.value = event.verticalAccuracyMeters
        coveredDistanceState.value = event.coveredDistance

        if (isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")) {
            drawPolyline(latitudeState.value, longitudeState.value)
        } else {
            mapView.controller.setCenter(GeoPoint(latitudeState.value, longitudeState.value))
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

    private fun drawPolyline(latitude: Double, longitude: Double) {
        val size = polyline.actualPoints.size

        if (size == 1) {
            createMarker()
            //mapView.controller.setZoom(17.0)
            mapView.controller.setCenter(GeoPoint(latitude, longitude))
            polyline.addPoint(GeoPoint(latitude, longitude))
        } else {
            //mapView.controller.setZoom(17.0)
            mapView.controller.setCenter(GeoPoint(latitude, longitude))
            polyline.addPoint(GeoPoint(latitude, longitude))
        }
        mapView.invalidate()
    }

    private fun createMarker() {
        marker.position = polyline.actualPoints[0]
        marker.title = getString(R.string.marker_title)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.startflag)
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
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
                            0 -> MapScreen() // The actual map
                            1 -> StatisticsScreen() // Statistics content
                            2 -> SettingsScreen() // Settings content
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun MapScreen() {
        Column {
            OpenStreetMapView()
        }
    }

    @Composable
    fun StatisticsScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "Statistics", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Speed: 0.0 km/h")
            Text("Covered Distance: 0.0 km")
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
        var height by remember { mutableStateOf(sharedPreferences.getString("height", "") ?: "") }
        var weight by remember { mutableStateOf(sharedPreferences.getString("weight", "") ?: "") }
        var websocketserver by remember { mutableStateOf(sharedPreferences.getString("websocketserver", "") ?: "") }

        // Check if the app is ignored by battery optimization
        val isBatteryOptimized = isBatteryOptimizationIgnored(context)

        // State for the switch (shows if app is excluded from battery optimizations)
        var isOptimized by remember { mutableStateOf(isBatteryOptimized) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
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
                value = height,
                onValueChange = { height = it },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

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
        height: String,
        weight: String,
        websocketserver: String
    ) {
        val editor = sharedPreferences.edit()
        editor.putString("firstname", firstName)
        editor.putString("lastname", lastName)
        editor.putString("birthdate", birthDate)
        editor.putString("height", height)
        editor.putString("weight", weight)
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
        verticalAccuracyInMeters: Float,
        horizontalAccuracyInMeters: Float,
        numberOfSatellites: Int,
        usedNumberOfSatellites: Int,
        coveredDistance: Double
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            //Text(text = "Latitude: ${"%.2f".format(latitude)} Longitude: ${"%.2f".format(longitude)}", fontSize = 10.sp, color = Color.Black)
            Text(
                text = "Latitude: $latitude Longitude: $longitude",
                fontSize = 10.sp,
                color = Color.Black
            )
            Text("Speed: $speed km/h", fontSize = 10.sp, color = Color.Black)
            Text(
                "Speed accuracy: ±${"%.2f".format(speedAccuracyInMeters)} m/s",
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
            //Text("Vertical accuracy: ${"%.2f".format(verticalAccuracyInMeters)} meter", fontSize = 18.sp, color = Color.Black)
        }
    }

    @Composable
    fun OpenStreetMapView() {
        val context = LocalContext.current

        var showDialog by remember { mutableStateOf(false) }
        var isRecording by remember {
            mutableStateOf(
                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                    .getBoolean("is_recording", false)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Map view setup (same as before)
            AndroidView(factory = {
                mapView = MapView(context).apply {
                    setTileSource(customTileSource)
                    setMultiTouchControls(true)
                    controller.setZoom(9.0)
                    controller.setCenter(GeoPoint(0.0, 0.0))

                    // Initialize polyline for tracking locations
                    polyline = Polyline().apply {
                        width = 10f
                        color = context.resources.getColor(android.R.color.holo_purple, null)
                    }
                    overlays.add(polyline)
                }
                marker = Marker(mapView)

                // ScaleBar overlay
                val dm: DisplayMetrics = context.resources.displayMetrics
                val scaleBarOverlay = ScaleBarOverlay(mapView)
                scaleBarOverlay.setCentred(true)
                scaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 2000)
                mapView.overlays.add(scaleBarOverlay)

                // MyLocation overlay
                val mLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
                mLocationOverlay.enableMyLocation()
                mapView.overlays.add(mLocationOverlay)

                mapView
            }, modifier = Modifier.fillMaxSize())

            // Recording Button (shown only if not recording)
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
                                // Show the dialog to get event details
                                showDialog = true
                                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
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
                        .offset(x = (-16).dp, y = (-180).dp) // Same position as the recording button
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

                                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
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

        // Show the dialog if state is true
        if (showDialog) {
            RecordingButtonWithDialog(
                context = context,
                onSave = { eventName, eventDate, artOfSport, wheelSize, sprocket, comment, clothing ->
                    // Start the foreground service with all fields as extras
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

                    // Update recording state and dismiss dialog
                    isRecording = true
                    showDialog = false
                },
                onDismiss = {
                    // Simply dismiss the dialog
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
                        label = { Text("Event Date (YYYY-MM-DD)") },
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

    // Override
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
}