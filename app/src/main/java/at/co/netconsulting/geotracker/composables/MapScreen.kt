package at.co.netconsulting.geotracker.composables

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.location.ViewportPathTracker
import at.co.netconsulting.geotracker.service.BackgroundLocationService
import at.co.netconsulting.geotracker.service.ForegroundService
import kotlinx.coroutines.delay
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Get database instance
    val database = remember { FitnessTrackerDatabase.getInstance(context) }

    var isRecording by remember {
        mutableStateOf(
            context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_recording", false)
        )
    }

    var showRecordingDialog by remember { mutableStateOf(false) }

    // Path visibility state
    var showPath by remember {
        mutableStateOf(
            context.getSharedPreferences("PathSettings", Context.MODE_PRIVATE)
                .getBoolean("show_path", false)
        )
    }

    // Create a reference to hold the MapView
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    // Add a reference to hold the MyLocationNewOverlay
    val locationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    // Track follow mode state
    var isFollowingLocation by remember { mutableStateOf(true) }

    // Get current event ID from shared preferences
    val currentEventId = remember {
        mutableStateOf(
            context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                .getInt("active_event_id", -1)
        )
    }

    // Create the viewport-based path tracker
    val pathTracker = remember { ViewportPathTracker(database) }

    // Access the isLoading state directly
    val isPathLoading by remember { pathTracker.isLoading }

    // Remember EventBus registration state
    var isEventBusRegistered by remember { mutableStateOf(false) }

    // Observer object for EventBus that will receive location updates
    val locationObserver = remember {
        object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onLocationUpdate(metrics: Metrics) {
                // When we get a location update and an event is active, refresh the path
                if (showPath && currentEventId.value > 0) {
                    mapViewRef.value?.let { mapView ->
                        pathTracker.updatePathForViewport(mapView)
                    }
                }
            }
        }
    }

    // Screen state receiver for handling screen on/off events
    val screenStateReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d("MapScreen", "Screen turned on, refreshing path")
                        if (showPath) {
                            mapViewRef.value?.let { mapView ->
                                // Small delay to ensure the map is fully ready
                                Handler(Looper.getMainLooper()).postDelayed({
                                    pathTracker.refreshPath(mapView)
                                }, 300)
                            }
                        }
                    }
                }
            }
        }
    }

    // Lifecycle observer for app resume events
    val lifecycleObserver = remember {
        object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                // Refresh path when app is resumed
                Log.d("MapScreen", "App resumed, refreshing path")
                if (showPath) {
                    mapViewRef.value?.let { mapView ->
                        pathTracker.updatePathForViewport(mapView, forceUpdate = true)
                    }
                }

                // Check if service is actually running (in case of app restart)
                val isServiceRunning = isServiceRunningFunc(
                    context,
                    "at.co.netconsulting.geotracker.service.ForegroundService"
                )

                // If there's a mismatch between SharedPreferences and actual service state
                if (isServiceRunning != isRecording) {
                    Log.d("MapScreen", "Service state mismatch detected: service=$isServiceRunning, UI=$isRecording")

                    // Update UI to match actual service state
                    isRecording = isServiceRunning

                    // Update SharedPreferences
                    context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_recording", isServiceRunning)
                        .apply()
                }
            }
        }
    }

    // tracking pause status
    var isPaused by remember {
        mutableStateOf(
            context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_paused", false)
        )
    }

    // Initialize or clean up path tracker based on showPath
    LaunchedEffect(showPath, currentEventId.value) {
        if (showPath) {
            // Initialize path tracker
            mapViewRef.value?.let { mapView ->
                pathTracker.initialize(mapView)
                pathTracker.setCurrentEventId(currentEventId.value, mapView)
                pathTracker.setRecording(isRecording)

                // Initial update for the current viewport
                pathTracker.updatePathForViewport(mapView, forceUpdate = true)
            }
            Log.d("MapScreen", "Path tracker initialized for event: ${currentEventId.value}")
        } else {
            // Clear path if visibility is toggled off
            mapViewRef.value?.let { mapView ->
                pathTracker.clearPath(mapView)
            }
            Log.d("MapScreen", "Path tracking disabled")
        }
    }

    // Monitor recording state changes
    LaunchedEffect(Unit) {
        // Check recording state periodically
        while (true) {
            val newIsRecording = context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_recording", false)

            val newIsPaused = context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_paused", false)

            val newShowPath = context.getSharedPreferences("PathSettings", Context.MODE_PRIVATE)
                .getBoolean("show_path", false)

            val newEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                .getInt("active_event_id", -1)

            if (isRecording != newIsRecording) {
                Log.d("MapScreen", "Recording state changed from $isRecording to $newIsRecording")
                isRecording = newIsRecording
                pathTracker.setRecording(newIsRecording)
            }

            if (isPaused != newIsPaused) {
                Log.d("MapScreen", "Pause state changed from $isPaused to $newIsPaused")
                isPaused = newIsPaused
            }

            if (showPath != newShowPath) {
                Log.d("MapScreen", "Path visibility changed from $showPath to $newShowPath")
                showPath = newShowPath
            }

            if (currentEventId.value != newEventId) {
                Log.d("MapScreen", "Event ID changed from ${currentEventId.value} to $newEventId")
                currentEventId.value = newEventId
            }

            delay(1000)
        }
    }

    // Check if service is actually running on initial launch
    LaunchedEffect(Unit) {
        val isServiceRunning = isServiceRunningFunc(
            context,
            "at.co.netconsulting.geotracker.service.ForegroundService"
        )

        // If there's a mismatch between SharedPreferences and actual service state
        if (isServiceRunning != isRecording) {
            Log.d("MapScreen", "Initial service state mismatch detected: service=$isServiceRunning, UI=$isRecording")

            // Update UI to match actual service state
            isRecording = isServiceRunning

            // Update SharedPreferences
            context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_recording", isServiceRunning)
                .apply()

            // If service is running but we don't have an active event ID, check ServiceState
            if (isServiceRunning && currentEventId.value == -1) {
                val serviceState = context.getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
                val storedEventId = serviceState.getInt("event_id", -1)

                if (storedEventId != -1) {
                    Log.d("MapScreen", "Restoring active event ID from ServiceState: $storedEventId")
                    // Update current event ID in both state and SharedPreferences
                    currentEventId.value = storedEventId
                    context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("active_event_id", storedEventId)
                        .apply()
                }
            }
        }
    }

    // COMBINED DISPOSABLE EFFECT - handles all lifecycle-related side effects
    DisposableEffect(Unit) {
        // 1. Register with EventBus
        if (!isEventBusRegistered) {
            EventBus.getDefault().register(locationObserver)
            isEventBusRegistered = true
            Log.d("MapScreen", "Registered with EventBus")
        }

        // 2. Register screen state receiver
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(screenStateReceiver, intentFilter)

        // 3. Add lifecycle observer
        lifecycle.addObserver(lifecycleObserver)

        // 4. Handle recording state and services
        if (isRecording) {
            // Recording is active, ForegroundService should be running
            Log.d("MapScreen", "DisposableEffect: Recording is active")
            pathTracker.setRecording(true)

            // Update current event ID
            currentEventId.value = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                .getInt("active_event_id", -1)

            // Update path tracker with current event ID
            if (showPath) {
                mapViewRef.value?.let { mapView ->
                    pathTracker.setCurrentEventId(currentEventId.value, mapView)
                }
            }
        } else {
            // Start background service for normal tracking (if not already running)
            try {
                Log.d("MapScreen", "DisposableEffect: Starting background service")
                val intent = Intent(context, BackgroundLocationService::class.java)
                context.startService(intent)
                pathTracker.setRecording(false)
            } catch (e: Exception) {
                Log.e("MapScreen", "Failed to start background service", e)
                Toast.makeText(context, "Failed to start location service", Toast.LENGTH_SHORT).show()
            }
        }

        // Combined cleanup function
        onDispose {
            // 1. Unregister from EventBus
            if (isEventBusRegistered) {
                EventBus.getDefault().unregister(locationObserver)
                isEventBusRegistered = false
                Log.d("MapScreen", "Unregistered from EventBus")
            }

            // 2. Unregister screen receiver
            try {
                context.unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                Log.e("MapScreen", "Error unregistering receiver", e)
            }

            // 3. Remove lifecycle observer
            lifecycle.removeObserver(lifecycleObserver)

            // 4. Clean up path tracker
            pathTracker.cleanup()

            // 5. Clean up services
            if (!isRecording) {
                // Only stop background service if we're not recording
                try {
                    Log.d("MapScreen", "onDispose: Stopping background service")
                    context.stopService(Intent(context, BackgroundLocationService::class.java))
                } catch (e: Exception) {
                    Log.e("MapScreen", "Error stopping background service", e)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OSM Map View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)

                    // Set a default location (can be updated with user's location later)
                    controller.setCenter(GeoPoint(48.2082, 16.3738)) // Vienna coordinates

                    // Optimize for screen on/off cycles
                    isDrawingCacheEnabled = true
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // Add scale bar
                    val dm: DisplayMetrics = ctx.resources.displayMetrics
                    val scaleBarOverlay = ScaleBarOverlay(this).apply {
                        setCentred(true)
                        setScaleBarOffset(dm.widthPixels / 2, 10)
                    }
                    overlays.add(scaleBarOverlay)

                    // Add my location overlay with follow location enabled initially
                    val locationOverlay =
                        MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                            enableMyLocation()
                            // Enable follow location by default
                            enableFollowLocation()
                        }
                    overlays.add(locationOverlay)

                    // Store reference to the location overlay
                    locationOverlayRef.value = locationOverlay

                    // Store reference to MapView
                    mapViewRef.value = this

                    // If path display is enabled, initialize the tracker now that we have a MapView
                    if (showPath) {
                        pathTracker.initialize(this)
                        pathTracker.setCurrentEventId(currentEventId.value, this)
                        pathTracker.setRecording(isRecording)
                    }

                    // Add map listener for viewport and zoom changes
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            // Disable follow location on any scroll
                            if (isFollowingLocation) {
                                locationOverlayRef.value?.disableFollowLocation()
                                isFollowingLocation = false
                                Log.d("MapScreen", "Map scrolled, disabled follow location")
                            }

                            if (showPath) {
                                pathTracker.updatePathForViewport(this@apply)
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            // Do not change follow location state on zoom
                            // Just update the path if needed
                            if (showPath) {
                                pathTracker.updatePathForViewport(this@apply)
                            }
                            return true
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Heart Rate Panel - Add this to the Box
        HeartRatePanel(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 16.dp)
        )

        // Path loading indicator (only shown when loading)
        if (isPathLoading && showPath) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(24.dp),
                color = Color.Red,
                strokeWidth = 2.dp
            )
        }

        // "My Location" button to re-enable follow mode
        if (!isFollowingLocation) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            // Get the current location from the overlay
                            locationOverlayRef.value?.let { overlay ->
                                // Get current location from the overlay
                                val myLocation = overlay.myLocation

                                // If we have a valid location, center on it
                                if (myLocation != null) {
                                    mapViewRef.value?.controller?.animateTo(myLocation)
                                    Log.d(
                                        "MapScreen",
                                        "Centered map on current location: $myLocation"
                                    )
                                } else {
                                    Log.d(
                                        "MapScreen",
                                        "Unable to center - current location is null"
                                    )
                                }

                                // Enable follow location mode
                                overlay.enableFollowLocation()
                                isFollowingLocation = true
                                Log.d(
                                    "MapScreen",
                                    "My Location button clicked, enabled follow location"
                                )
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = Color.Black
                    )
                }
            }
        }

        // Control buttons row at the bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Display different buttons based on recording state
            if (!isRecording) {
                // Start recording button (no change)
                Surface(
                    modifier = Modifier
                        .size(56.dp),
                    shape = CircleShape,
                    color = Color.Red,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                showRecordingDialog = true
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Recording",
                            tint = Color.White
                        )
                    }
                }
            } else {
                // Recording is active, show pause/resume and stop buttons
                // Pause/Resume button
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(end = 8.dp),
                    shape = CircleShape,
                    color = if (isPaused) Color.Green else Color(0xFFFFA500), // Orange when not paused, Green when paused
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                // Toggle pause state
                                val newPausedState = !isPaused
                                isPaused = newPausedState

                                // Update shared preferences
                                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_paused", newPausedState)
                                    .apply()

                                // Send command to service
                                val intent = Intent(context, ForegroundService::class.java).apply {
                                    action = if (newPausedState) {
                                        "at.co.netconsulting.geotracker.PAUSE_RECORDING"
                                    } else {
                                        "at.co.netconsulting.geotracker.RESUME_RECORDING"
                                    }
                                }
                                context.startService(intent)

                                Toast.makeText(
                                    context,
                                    if (newPausedState) "Recording Paused" else "Recording Resumed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    ) {
                        Icon(
                            imageVector = if (isPaused)
                                Icons.Default.PlayArrow  // Show play icon when paused
                            else
                                Icons.Filled.Pause,     // Show pause icon when recording
                            contentDescription = if (isPaused) "Resume Recording" else "Pause Recording",
                            tint = Color.White
                        )
                    }
                }

                // Stop recording button (no change)
                Surface(
                    modifier = Modifier
                        .size(56.dp),
                    shape = CircleShape,
                    color = Color.Gray,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                // Stop recording logic (unchanged)
                                val sharedPreferences = context.getSharedPreferences(
                                    "CurrentEvent",
                                    Context.MODE_PRIVATE
                                )
                                val currentEventId = sharedPreferences.getInt("active_event_id", -1)
                                if (currentEventId != -1) {
                                    // Store as last event ID
                                    sharedPreferences.edit()
                                        .putInt("last_event_id", currentEventId)
                                        // Remove active_event_id key completely
                                        .remove("active_event_id")
                                        .apply()
                                }

                                // Update recording state
                                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_recording", false)
                                    .putBoolean("is_paused", false) // Also reset pause state
                                    .apply()

                                isRecording = false
                                isPaused = false

                                // Stop the foreground service
                                val stopIntent = Intent(context, ForegroundService::class.java)
                                // Set flag to mark the stop as intentional
                                stopIntent.putExtra("stopping_intentionally", true)
                                context.stopService(stopIntent)

                                // Also explicitly clear recovery state here to ensure it's cleared
                                // even if service destruction is interrupted
                                context.getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("was_running", false)
                                    .apply()

                                // Start the background service
                                val intent = Intent(context, BackgroundLocationService::class.java)
                                context.startService(intent)

                                Toast.makeText(context, "Recording Stopped", Toast.LENGTH_SHORT)
                                    .show()
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
    }

    // Recording dialog
    if (showRecordingDialog) {
        RecordingDialog(
            onSave = { eventName, eventDate, artOfSport, comment, clothing, pathOption, heartRateSensor ->
                // Store current zoom level and center before making any changes
                val currentZoomLevel = mapViewRef.value?.zoomLevelDouble ?: 15.0
                val currentCenter = mapViewRef.value?.mapCenter

                // Save path visibility preference
                context.getSharedPreferences("PathSettings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("show_path", pathOption)
                    .apply()

                // Update local state
                showPath = pathOption

                // Start recording
                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_recording", true)
                    .apply()

                isRecording = true
                Log.d("MapScreen", "Recording started, set isRecording=true")

                // Stop background service
                val stopIntent = Intent(context, BackgroundLocationService::class.java)
                context.stopService(stopIntent)
                Log.d("MapScreen", "Stopped BackgroundLocationService")

                // Start foreground service with event details
                val intent = Intent(context, ForegroundService::class.java).apply {
                    putExtra("eventName", eventName)
                    putExtra("eventDate", eventDate)
                    putExtra("artOfSport", artOfSport)
                    putExtra("comment", comment)
                    putExtra("clothing", clothing)
                    putExtra("start_recording", true)

                    // Add heart rate sensor information if selected
                    heartRateSensor?.let {
                        putExtra("heartRateDeviceAddress", it.address)
                        putExtra("heartRateDeviceName", it.name)
                        Log.d("MapScreen", "Adding heart rate sensor: ${it.name} (${it.address})")
                    }
                }
                ContextCompat.startForegroundService(context, intent)
                Log.d("MapScreen", "Started ForegroundService with event details and start_recording=true")

                showRecordingDialog = false

                // Get the newly created event ID and restore map settings after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    val newEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .getInt("active_event_id", -1)

                    mapViewRef.value?.let { mapView ->
                        // First set the event ID for path tracking if needed
                        if (newEventId > 0 && pathOption) {
                            Log.d("MapScreen", "Setting path tracker to new event: $newEventId")
                            pathTracker.setCurrentEventId(newEventId, mapView)
                        }

                        // Then restore the original zoom level and center
                        mapView.controller.setZoom(currentZoomLevel)
                        currentCenter?.let { center ->
                            mapView.controller.setCenter(center)
                        }

                        // Make sure follow location is enabled
                        locationOverlayRef.value?.enableFollowLocation()
                        isFollowingLocation = true

                        Log.d("MapScreen", "Restored map zoom level to $currentZoomLevel after recording started")
                    }
                }, 800) // Slightly longer delay to ensure all operations complete
            },
            onDismiss = {
                showRecordingDialog = false
            }
        )
    }
}

// Helper function to check if a service is running
private fun isServiceRunningFunc(context: Context, serviceName: String): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Integer.MAX_VALUE)
        .any { service ->
            serviceName == service.service.className && service.foreground
        }
}