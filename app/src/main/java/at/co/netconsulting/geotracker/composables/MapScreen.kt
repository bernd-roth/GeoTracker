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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import at.co.netconsulting.geotracker.location.FollowedUsersOverlay
import at.co.netconsulting.geotracker.location.ViewportPathTracker
import at.co.netconsulting.geotracker.service.BackgroundLocationService
import at.co.netconsulting.geotracker.service.FollowingService
import at.co.netconsulting.geotracker.service.ForegroundService
import at.co.netconsulting.geotracker.tools.Tools
import kotlinx.coroutines.delay
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// Dark mode tile source function - works with OSMDroid 6.1.18+
private fun createDarkTileSource(): OnlineTileSourceBase {
    return object : OnlineTileSourceBase(
        "CartoDB Dark Matter",  // name
        0,                      // zoomMinLevel
        20,                     // zoomMaxLevel
        256,                    // tileSizePixels
        ".png",                 // imageFilenameEnding
        arrayOf(                // baseUrl
            "https://cartodb-basemaps-a.global.ssl.fastly.net/dark_all/",
            "https://cartodb-basemaps-b.global.ssl.fastly.net/dark_all/",
            "https://cartodb-basemaps-c.global.ssl.fastly.net/dark_all/",
            "https://cartodb-basemaps-d.global.ssl.fastly.net/dark_all/"
        )
    ) {
        override fun getTileURLString(aMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(aMapTileIndex)
            val x = MapTileIndex.getX(aMapTileIndex)
            val y = MapTileIndex.getY(aMapTileIndex)

            val fileExtension = try {
                imageFilenameEnding() // For older versions that use function
            } catch (e: Exception) {
                try {
                    // For newer versions that might use property (if available)
                    ".png" // Fallback to hardcoded extension
                } catch (e2: Exception) {
                    ".png"
                }
            }

            return "${baseUrl}${zoom}/${x}/${y}${fileExtension}"
        }
    }
}

private fun updateMapStyle(mapView: MapView, isDarkMode: Boolean) {
    val tileSource = if (isDarkMode) {
        createDarkTileSource()
    } else {
        TileSourceFactory.MAPNIK
    }
    mapView.setTileSource(tileSource)
    mapView.invalidate()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var hasInitializedMapCenter by remember { mutableStateOf(false) }

    // Get database instance
    val database = remember { FitnessTrackerDatabase.getInstance(context) }

    // Get following service instance
    val followingService = remember { FollowingService.getInstance(context) }

    var isRecording by remember {
        mutableStateOf(
            context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_recording", false)
        )
    }

    var showRecordingDialog by remember { mutableStateOf(false) }
    var showUserSelectionDialog by remember { mutableStateOf(false) }

    var showSettingsValidationDialog by remember { mutableStateOf(false) }
    var missingSettingsFields by remember { mutableStateOf(listOf<String>()) }

    // Following service state
    val activeUsers by followingService.activeUsers.collectAsState()
    val followingState by followingService.followingState.collectAsState()
    val isFollowingLoading by followingService.isLoading.collectAsState()
    val isConnected by followingService.connectionState.collectAsState()

    // Path visibility state
    var showPath by remember {
        mutableStateOf(
            context.getSharedPreferences("PathSettings", Context.MODE_PRIVATE)
                .getBoolean("show_path", false)
        )
    }

    // Dark mode state
    var isDarkMode by remember {
        mutableStateOf(
            context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                .getBoolean("darkModeEnabled", false)
        )
    }

    // Create a reference to hold the MapView
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    // Add a reference to hold the MyLocationNewOverlay
    val locationOverlayRef = remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    // Add reference to hold the FollowedUsersOverlay
    val followedUsersOverlayRef = remember { mutableStateOf<FollowedUsersOverlay?>(null) }

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
                // Only update path when recording (not when following others)
                if (showPath && currentEventId.value > 0 && isRecording && !followingState.isFollowing) {
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
                        if (showPath && isRecording && !followingState.isFollowing) {
                            mapViewRef.value?.let { mapView ->
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
                Log.d("MapScreen", "App resumed")

                // Only refresh path if recording and not following others
                if (showPath && isRecording && !followingState.isFollowing) {
                    mapViewRef.value?.let { mapView ->
                        pathTracker.updatePathForViewport(mapView, forceUpdate = true)
                    }
                }

                val isServiceRunning = isServiceRunningFunc(
                    context,
                    "at.co.netconsulting.geotracker.service.ForegroundService"
                )

                if (isServiceRunning != isRecording) {
                    Log.d("MapScreen", "Service state mismatch detected: service=$isServiceRunning, UI=$isRecording")
                    isRecording = isServiceRunning
                    context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_recording", isServiceRunning)
                        .apply()
                }
            }
        }
    }

    // Monitor dark mode changes
    LaunchedEffect(Unit) {
        while (true) {
            val newDarkMode = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                .getBoolean("darkModeEnabled", false)

            if (isDarkMode != newDarkMode) {
                Log.d("MapScreen", "Dark mode changed from $isDarkMode to $newDarkMode")
                isDarkMode = newDarkMode
                mapViewRef.value?.let { mapView ->
                    updateMapStyle(mapView, newDarkMode)
                }
            }
            delay(1000) // Check every second
        }
    }

    // Update followed users overlay when following state changes
    LaunchedEffect(followingState.followedUserData) {
        followedUsersOverlayRef.value?.updateFollowedUsers(followingState.followedUserData)
    }

    // Connect to following service only when not recording
    LaunchedEffect(isRecording) {
        if (!isRecording && !isConnected) {
            followingService.connect()
        } else if (isRecording && isConnected) {
            // If we start recording while following, stop following
            if (followingState.isFollowing) {
                followingService.stopFollowing()
            }
        }
    }

    // Initialize or clean up path tracker based on recording and following state
    LaunchedEffect(showPath, currentEventId.value, isRecording, followingState.isFollowing) {
        if (showPath && isRecording && !followingState.isFollowing) {
            // Only show path when recording own event
            mapViewRef.value?.let { mapView ->
                pathTracker.initialize(mapView)
                pathTracker.setCurrentEventId(currentEventId.value, mapView)
                pathTracker.setRecording(isRecording)
                pathTracker.updatePathForViewport(mapView, forceUpdate = true)
            }
            Log.d("MapScreen", "Path tracker initialized for event: ${currentEventId.value}")
        } else {
            // Clear path when following others or not recording
            mapViewRef.value?.let { mapView ->
                pathTracker.clearPath(mapView)
            }
            Log.d("MapScreen", "Path tracking disabled")
        }
    }

    // Monitor recording state changes
    LaunchedEffect(Unit) {
        while (true) {
            val newIsRecording = context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_recording", false)

            val newShowPath = context.getSharedPreferences("PathSettings", Context.MODE_PRIVATE)
                .getBoolean("show_path", false)

            val newEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                .getInt("active_event_id", -1)

            if (isRecording != newIsRecording) {
                Log.d("MapScreen", "Recording state changed from $isRecording to $newIsRecording")
                isRecording = newIsRecording
                pathTracker.setRecording(newIsRecording)
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

        if (isServiceRunning != isRecording) {
            Log.d("MapScreen", "Initial service state mismatch detected: service=$isServiceRunning, UI=$isRecording")
            isRecording = isServiceRunning
            context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_recording", isServiceRunning)
                .apply()

            if (isServiceRunning && currentEventId.value == -1) {
                val serviceState = context.getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
                val storedEventId = serviceState.getInt("event_id", -1)

                if (storedEventId != -1) {
                    Log.d("MapScreen", "Restoring active event ID from ServiceState: $storedEventId")
                    currentEventId.value = storedEventId
                    context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("active_event_id", storedEventId)
                        .apply()
                }
            }
        }
    }

    val locationUpdateListener = remember {
        object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onLocationUpdate(metrics: Metrics) {
                if (!hasInitializedMapCenter && metrics.latitude != 0.0 && metrics.longitude != 0.0) {
                    mapViewRef.value?.let { mapView ->
                        val newLocation = GeoPoint(metrics.latitude, metrics.longitude)
                        mapView.controller.animateTo(newLocation)
                        Log.d("MapScreen", "Map centered on first received location: $newLocation")
                        hasInitializedMapCenter = true
                    }
                }
            }
        }
    }

    DisposableEffect(locationUpdateListener) {
        if (!EventBus.getDefault().isRegistered(locationUpdateListener)) {
            EventBus.getDefault().register(locationUpdateListener)
            Log.d("MapScreen", "Registered location update listener with EventBus")
        }

        onDispose {
            if (EventBus.getDefault().isRegistered(locationUpdateListener)) {
                EventBus.getDefault().unregister(locationUpdateListener)
                Log.d("MapScreen", "Unregistered location update listener from EventBus")
            }
        }
    }

    // COMBINED DISPOSABLE EFFECT - handles all lifecycle-related side effects
    DisposableEffect(Unit) {
        // Register with EventBus
        if (!isEventBusRegistered) {
            EventBus.getDefault().register(locationObserver)
            isEventBusRegistered = true
            Log.d("MapScreen", "Registered with EventBus")
        }

        // Register screen state receiver
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(screenStateReceiver, intentFilter)

        // Add lifecycle observer
        lifecycle.addObserver(lifecycleObserver)

        // Handle recording state and services
        if (isRecording) {
            Log.d("MapScreen", "DisposableEffect: Recording is active")
            pathTracker.setRecording(true)

            currentEventId.value = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                .getInt("active_event_id", -1)

            if (showPath && !followingState.isFollowing) {
                mapViewRef.value?.let { mapView ->
                    pathTracker.setCurrentEventId(currentEventId.value, mapView)
                }
            }
        } else if (!followingState.isFollowing) {
            // Only start background service if not following others
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
            if (isEventBusRegistered) {
                EventBus.getDefault().unregister(locationObserver)
                isEventBusRegistered = false
                Log.d("MapScreen", "Unregistered from EventBus")
            }

            try {
                context.unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                Log.e("MapScreen", "Error unregistering receiver", e)
            }

            lifecycle.removeObserver(lifecycleObserver)

            pathTracker.cleanup()

            if (!isRecording && !followingState.isFollowing) {
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
                    // Get initial dark mode setting
                    val initialDarkMode = ctx.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                        .getBoolean("darkModeEnabled", false)

                    // Set initial tile source based on dark mode
                    setTileSource(
                        if (initialDarkMode) createDarkTileSource() else TileSourceFactory.MAPNIK
                    )

                    setMultiTouchControls(true)
                    controller.setZoom(6)
                    controller.setCenter(GeoPoint(0.0, 0.0))

                    isDrawingCacheEnabled = true
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // Add scale bar
                    val dm: DisplayMetrics = ctx.resources.displayMetrics
                    val scaleBarOverlay = ScaleBarOverlay(this).apply {
                        setCentred(true)
                        setScaleBarOffset(dm.widthPixels / 2, 10)
                    }
                    overlays.add(scaleBarOverlay)

                    // Add my location overlay
                    val locationOverlay =
                        MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                            enableMyLocation()
                            enableFollowLocation()

                            runOnFirstFix {
                                Handler(Looper.getMainLooper()).post {
                                    if (!hasInitializedMapCenter) {
                                        val location = myLocation
                                        if (location != null) {
                                            controller.animateTo(location)
                                            controller.setZoom(15)
                                            Log.d("MapScreen", "Map centered on initial location: $location")
                                            hasInitializedMapCenter = true
                                        }
                                    }
                                }
                            }
                        }
                    overlays.add(locationOverlay)
                    locationOverlayRef.value = locationOverlay

                    // Add followed users overlay
                    val followedUsersOverlay = FollowedUsersOverlay(ctx, this)
                    overlays.add(followedUsersOverlay)
                    followedUsersOverlayRef.value = followedUsersOverlay

                    mapViewRef.value = this

                    if (showPath && isRecording && !followingState.isFollowing) {
                        pathTracker.initialize(this)
                        pathTracker.setCurrentEventId(currentEventId.value, this)
                        pathTracker.setRecording(isRecording)
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            if (isFollowingLocation) {
                                locationOverlayRef.value?.disableFollowLocation()
                                isFollowingLocation = false
                                Log.d("MapScreen", "Map scrolled, disabled follow location")
                            }

                            // Only update path when recording own event, not when following others
                            if (showPath && isRecording && !followingState.isFollowing) {
                                pathTracker.updatePathForViewport(this@apply)
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            // Only update path when recording own event, not when following others
                            if (showPath && isRecording && !followingState.isFollowing) {
                                pathTracker.updatePathForViewport(this@apply)
                            }
                            return true
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Heart Rate Panel - only show when recording
        if (isRecording && !followingState.isFollowing) {
            HeartRatePanel(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 64.dp, start = 16.dp)
            )
        }

        // Following indicator
        if (followingState.isFollowing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                shape = CircleShape,
                color = Color.Blue,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "Following ${followingState.followedUsers.size} user(s)",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White
                )
            }
        }

        // Path loading indicator (only shown when recording own path)
        if (isPathLoading && showPath && isRecording && !followingState.isFollowing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(24.dp),
                color = Color.Red,
                strokeWidth = 2.dp
            )
        }

        // Connection status indicator (only when following)
        if (followingState.isFollowing && !isConnected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = CircleShape,
                color = Color.Red,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "Offline",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White
                )
            }
        }

        // "My Location" button to re-enable follow mode
        if (!isFollowingLocation) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = if (followingState.isFollowing && !isConnected) 64.dp else 16.dp)
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
                            locationOverlayRef.value?.let { overlay ->
                                val myLocation = overlay.myLocation

                                if (myLocation != null) {
                                    mapViewRef.value?.let { mapView ->
                                        mapView.controller.setZoom(15.0)
                                        mapView.controller.animateTo(myLocation)
                                        Log.d("MapScreen", "Centered map on current location: $myLocation with zoom level 15")
                                    }
                                } else {
                                    Log.d("MapScreen", "Unable to center - current location is null")
                                }

                                overlay.enableFollowLocation()
                                isFollowingLocation = true
                                Log.d("MapScreen", "My Location button clicked, enabled follow location")
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

        // Control buttons column at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Follow Users button - only show when not recording
            if (!isRecording) {
                Surface(
                    modifier = Modifier
                        .size(56.dp),
                    shape = CircleShape,
                    color = if (followingState.isFollowing) Color.Blue else Color.Gray,
                    shadowElevation = 8.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                if (followingState.isFollowing) {
                                    // Stop following
                                    followingService.stopFollowing()
                                } else {
                                    // Show user selection dialog
                                    followingService.requestActiveUsers()
                                    showUserSelectionDialog = true
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = if (followingState.isFollowing) "Stop Following" else "Follow Users",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Recording controls
            if (!isRecording && !followingState.isFollowing) {
                // Start recording button - only show when not following
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
                                val missingFields = Tools().validateRequiredSettings(context)
                                if (missingFields.isNotEmpty()) {
                                    showSettingsValidationDialog = true
                                    missingSettingsFields = missingFields
                                } else {
                                    showRecordingDialog = true
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Recording",
                            tint = Color.White
                        )
                    }
                }
            } else if (isRecording && !followingState.isFollowing) {
                // Recording is active, show stop button
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
                                val sharedPreferences = context.getSharedPreferences(
                                    "CurrentEvent",
                                    Context.MODE_PRIVATE
                                )
                                val currentEventId = sharedPreferences.getInt("active_event_id", -1)
                                if (currentEventId != -1) {
                                    sharedPreferences.edit()
                                        .putInt("last_event_id", currentEventId)
                                        .remove("active_event_id")
                                        .apply()
                                }

                                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_recording", false)
                                    .apply()

                                isRecording = false

                                val stopIntent = Intent(context, ForegroundService::class.java)
                                stopIntent.putExtra("stopping_intentionally", true)
                                context.stopService(stopIntent)

                                context.getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("was_running", false)
                                    .apply()

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

    // User Selection Dialog
    if (showUserSelectionDialog) {
        UserSelectionDialog(
            activeUsers = activeUsers,
            currentlyFollowing = followingState.followedUsers,
            isLoading = isFollowingLoading,
            onFollowUsers = { selectedSessionIds ->
                followingService.followUsers(selectedSessionIds)
            },
            onStopFollowing = {
                followingService.stopFollowing()
            },
            onRefreshUsers = {
                followingService.requestActiveUsers()
            },
            onDismiss = {
                showUserSelectionDialog = false
            }
        )
    }

    // Settings validation dialog
    if (showSettingsValidationDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsValidationDialog = false },
            title = { Text("Required Settings Missing") },
            text = {
                Column {
                    Text("Please fill in the following required fields in Settings:")
                    Spacer(modifier = Modifier.height(8.dp))
                    missingSettingsFields.forEach { field ->
                        Text("• $field", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsValidationDialog = false
                        onNavigateToSettings?.invoke()
                    }
                ) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showSettingsValidationDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Recording dialog
    if (showRecordingDialog) {
        RecordingDialog(
            onSave = { eventName, eventDate, artOfSport, comment, clothing, pathOption, heartRateSensor ->
                val currentZoomLevel = mapViewRef.value?.zoomLevelDouble ?: 15.0
                val currentCenter = mapViewRef.value?.mapCenter

                context.getSharedPreferences("PathSettings", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("show_path", pathOption)
                    .apply()

                showPath = pathOption

                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_recording", true)
                    .apply()

                isRecording = true
                Log.d("MapScreen", "Recording started, set isRecording=true")

                val stopIntent = Intent(context, BackgroundLocationService::class.java)
                context.stopService(stopIntent)
                Log.d("MapScreen", "Stopped BackgroundLocationService")

                val intent = Intent(context, ForegroundService::class.java).apply {
                    putExtra("eventName", eventName)
                    putExtra("eventDate", eventDate)
                    putExtra("artOfSport", artOfSport)
                    putExtra("comment", comment)
                    putExtra("clothing", clothing)
                    putExtra("start_recording", true)

                    heartRateSensor?.let {
                        putExtra("heartRateDeviceAddress", it.address)
                        putExtra("heartRateDeviceName", it.name)
                        Log.d("MapScreen", "Adding heart rate sensor: ${it.name} (${it.address})")
                    }
                }
                ContextCompat.startForegroundService(context, intent)
                Log.d("MapScreen", "Started ForegroundService with event details and start_recording=true")

                showRecordingDialog = false

                Handler(Looper.getMainLooper()).postDelayed({
                    val newEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .getInt("active_event_id", -1)

                    mapViewRef.value?.let { mapView ->
                        if (newEventId > 0 && pathOption) {
                            Log.d("MapScreen", "Setting path tracker to new event: $newEventId")
                            pathTracker.setCurrentEventId(newEventId, mapView)
                        }

                        mapView.controller.setZoom(currentZoomLevel)
                        currentCenter?.let { center ->
                            mapView.controller.setCenter(center)
                        }

                        locationOverlayRef.value?.enableFollowLocation()
                        isFollowingLocation = true

                        Log.d("MapScreen", "Restored map zoom level to $currentZoomLevel after recording started")
                    }
                }, 800)
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