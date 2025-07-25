package at.co.netconsulting.geotracker.composables

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.osmdroid.views.overlay.Polyline
import timber.log.Timber

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
    onNavigateToSettings: (() -> Unit)? = null,
    routeToDisplay: List<GeoPoint>? = null, // New parameter for route display
    onRouteDisplayed: (() -> Unit)? = null // New callback when route is displayed
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

    // New state for route display
    var displayedRoute by remember { mutableStateOf<List<GeoPoint>?>(null) }
    var showRouteDisplayIndicator by remember { mutableStateOf(false) }

    // Create a reference to hold the route overlay
    val routeOverlayRef = remember { mutableStateOf<Polyline?>(null) }

    // Persist auto-follow state
    var isFollowingLocation by remember {
        mutableStateOf(
            context.getSharedPreferences("MapSettings", Context.MODE_PRIVATE)
                .getBoolean("auto_follow_enabled", true)
        )
    }

    // Track the last known location for restoration
    val lastKnownLocation = remember { mutableStateOf<GeoPoint?>(null) }

    // Flag to ignore scroll events during programmatic map movements (for logging)
    var ignoringScrollEvents by remember { mutableStateOf(false) }

    // Track touch events to detect genuine user interaction
    var lastTouchTime by remember { mutableStateOf(0L) }

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

    // Function to save auto-follow state
    fun saveAutoFollowState(enabled: Boolean) {
        context.getSharedPreferences("MapSettings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_follow_enabled", enabled)
            .apply()
        Timber.d("Saved auto-follow state: $enabled")
    }

    // Function to programmatically trigger "My Location" button logic
    fun triggerMyLocationLogic() {
        if (isFollowingLocation) {
            Timber.d("Triggering My Location button logic programmatically")

            locationOverlayRef.value?.let { overlay ->
                overlay.enableFollowLocation()

                val myLocation = overlay.myLocation ?: lastKnownLocation.value

                if (myLocation != null) {
                    mapViewRef.value?.let { mapView ->
                        // Set flag for logging purposes
                        ignoringScrollEvents = true
                        mapView.controller.setZoom(15.0)
                        mapView.controller.setCenter(myLocation)
                        Timber.d("Programmatically centered map on location: $myLocation")

                        // Reset flag after short delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            ignoringScrollEvents = false
                        }, 300)
                    }
                } else {
                    Timber.d("No location available for programmatic centering")
                }
            }
            Timber.d("My Location logic triggered programmatically")
        }
    }

    // Handle route display when routeToDisplay changes
    LaunchedEffect(routeToDisplay) {
        if (routeToDisplay != null && routeToDisplay.isNotEmpty()) {
            displayedRoute = routeToDisplay
            showRouteDisplayIndicator = true

            mapViewRef.value?.let { mapView ->
                // Remove existing route overlay if present
                routeOverlayRef.value?.let { existingOverlay ->
                    mapView.overlays.remove(existingOverlay)
                }

                // Create new route overlay
                val routeOverlay = Polyline().apply {
                    setPoints(routeToDisplay)
                    color = android.graphics.Color.RED // Use red color to distinguish from recording path
                    width = 8f
                    title = "Event Route"
                }

                // Add the route overlay to the map
                mapView.overlays.add(routeOverlay)
                routeOverlayRef.value = routeOverlay

                // Center map on the route
                if (routeToDisplay.isNotEmpty()) {
                    // Calculate bounding box of the route
                    val minLat = routeToDisplay.minOf { it.latitude }
                    val maxLat = routeToDisplay.maxOf { it.latitude }
                    val minLon = routeToDisplay.minOf { it.longitude }
                    val maxLon = routeToDisplay.maxOf { it.longitude }

                    // Center on the route
                    val centerLat = (minLat + maxLat) / 2
                    val centerLon = (minLon + maxLon) / 2
                    val center = GeoPoint(centerLat, centerLon)

                    // Calculate appropriate zoom level
                    val latDiff = maxLat - minLat
                    val lonDiff = maxLon - minLon
                    val maxDiff = maxOf(latDiff, lonDiff)

                    val zoomLevel = when {
                        maxDiff > 1.0 -> 8.0
                        maxDiff > 0.1 -> 10.0
                        maxDiff > 0.01 -> 12.0
                        maxDiff > 0.001 -> 14.0
                        else -> 16.0
                    }

                    mapView.controller.setCenter(center)
                    mapView.controller.setZoom(zoomLevel)

                    // Disable auto-follow when displaying a route
                    isFollowingLocation = false
                    saveAutoFollowState(false)

                    mapView.invalidate()

                    Timber.d("Displayed route with ${routeToDisplay.size} points, centered at $center, zoom $zoomLevel")
                }
            }

            // Notify that route has been displayed
            onRouteDisplayed?.invoke()
        }
    }

    // Single location listener that handles both path updates and auto-follow
    val locationObserver = remember {
        object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onLocationUpdate(metrics: Metrics) {
                Timber.d("Location update: lat=${metrics.latitude}, lon=${metrics.longitude}, follow=$isFollowingLocation")

                // Update last known location
                if (metrics.latitude != 0.0 && metrics.longitude != 0.0) {
                    lastKnownLocation.value = GeoPoint(metrics.latitude, metrics.longitude)
                }

                // Handle auto-follow functionality
                if (isFollowingLocation && metrics.latitude != 0.0 && metrics.longitude != 0.0) {
                    mapViewRef.value?.let { mapView ->
                        val newLocation = GeoPoint(metrics.latitude, metrics.longitude)

                        // Set ignoring flag BEFORE any programmatic movement for logging
                        ignoringScrollEvents = true

                        if (hasInitializedMapCenter) {
                            mapView.controller.setCenter(newLocation)
                            Timber.d("Auto-follow: centered to $newLocation")
                        } else {
                            mapView.controller.setCenter(newLocation)
                            mapView.controller.setZoom(15.0)
                            hasInitializedMapCenter = true
                            Timber.d("Auto-follow: initial center at $newLocation")
                        }

                        // Ensure overlay is also following
                        locationOverlayRef.value?.let { overlay ->
                            if (!overlay.isFollowLocationEnabled) {
                                overlay.enableFollowLocation()
                                Timber.d("Re-enabled overlay follow location during location update")
                            }
                        }

                        // Reset ignoring flag after a delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            ignoringScrollEvents = false
                            Timber.d("Re-enabled scroll event handling after location update")
                        }, 300) // Reduced delay since we're not using this for protection anymore
                    }
                }

                // Handle path updates when recording (not when following others)
                if (showPath && currentEventId.value > 0 && isRecording && !followingState.isFollowing) {
                    mapViewRef.value?.let { mapView ->
                        pathTracker.updatePathForViewport(mapView)
                    }
                }
            }
        }
    }

    // Screen state receiver that triggers My Location logic
    val screenStateReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Timber.d("Screen turned ON - triggering My Location logic")

                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isFollowingLocation) {
                                triggerMyLocationLogic()
                            }
                        }, 1000)

                        if (showPath && isRecording && !followingState.isFollowing) {
                            mapViewRef.value?.let { mapView ->
                                Handler(Looper.getMainLooper()).postDelayed({
                                    pathTracker.refreshPath(mapView)
                                }, 300)
                            }
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Timber.d("Screen turned OFF - saving auto-follow state: $isFollowingLocation")
                        saveAutoFollowState(isFollowingLocation)
                    }
                }
            }
        }
    }

    // Lifecycle observer that triggers My Location logic
    val lifecycleObserver = remember {
        object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                Timber.d("App RESUMED - triggering My Location logic")

                Handler(Looper.getMainLooper()).postDelayed({
                    if (isFollowingLocation) {
                        triggerMyLocationLogic()
                    }
                }, 500)

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
                    Timber.d("Service state mismatch detected: service=$isServiceRunning, UI=$isRecording")
                    isRecording = isServiceRunning
                    context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_recording", isServiceRunning)
                        .apply()
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                Timber.d("App PAUSED - saving auto-follow state: $isFollowingLocation")
                saveAutoFollowState(isFollowingLocation)
            }
        }
    }

    // Periodic auto-follow monitoring
    LaunchedEffect(isFollowingLocation) {
        if (isFollowingLocation) {
            while (true) {
                delay(10000)

                locationOverlayRef.value?.let { overlay ->
                    if (!overlay.isFollowLocationEnabled && isFollowingLocation) {
                        Timber.d("Detected overlay follow disabled - triggering My Location logic")
                        triggerMyLocationLogic()
                    }
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
                Timber.d("Dark mode changed from $isDarkMode to $newDarkMode")
                isDarkMode = newDarkMode
                mapViewRef.value?.let { mapView ->
                    updateMapStyle(mapView, newDarkMode)
                }
            }
            delay(1000)
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
            if (followingState.isFollowing) {
                followingService.stopFollowing()
            }
        }
    }

    // Handle auto-follow state when recording starts/stops
    LaunchedEffect(isRecording) {
        if (isRecording) {
            isFollowingLocation = true
            saveAutoFollowState(true)
            Handler(Looper.getMainLooper()).postDelayed({
                if (isFollowingLocation) {
                    triggerMyLocationLogic()
                }
            }, 100)
            Timber.d("Recording started - enabled auto-follow")
        }
    }

    // Initialize or clean up path tracker based on recording and following state
    LaunchedEffect(showPath, currentEventId.value, isRecording, followingState.isFollowing) {
        if (showPath && isRecording && !followingState.isFollowing) {
            mapViewRef.value?.let { mapView ->
                pathTracker.initialize(mapView)
                pathTracker.setCurrentEventId(currentEventId.value, mapView)
                pathTracker.setRecording(isRecording)
                pathTracker.updatePathForViewport(mapView, forceUpdate = true)
            }
            Timber.d("Path tracker initialized for event: ${currentEventId.value}")
        } else {
            mapViewRef.value?.let { mapView ->
                pathTracker.clearPath(mapView)
            }
            Timber.d("Path tracking disabled")
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
                Timber.d("Recording state changed from $isRecording to $newIsRecording")
                isRecording = newIsRecording
                pathTracker.setRecording(newIsRecording)
            }

            if (showPath != newShowPath) {
                Timber.d("Path visibility changed from $showPath to $newShowPath")
                showPath = newShowPath
            }

            if (currentEventId.value != newEventId) {
                Timber.d("Event ID changed from ${currentEventId.value} to $newEventId")
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
            Timber.d("Initial service state mismatch detected: service=$isServiceRunning, UI=$isRecording")
            isRecording = isServiceRunning
            context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_recording", isServiceRunning)
                .apply()

            if (isServiceRunning && currentEventId.value == -1) {
                val serviceState = context.getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
                val storedEventId = serviceState.getInt("event_id", -1)

                if (storedEventId != -1) {
                    Timber.d("Restoring active event ID from ServiceState: $storedEventId")
                    currentEventId.value = storedEventId
                    context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("active_event_id", storedEventId)
                        .apply()
                }
            }
        }
    }

    // Single location update listener registration
    DisposableEffect(locationObserver) {
        if (!EventBus.getDefault().isRegistered(locationObserver)) {
            EventBus.getDefault().register(locationObserver)
            Timber.d("Registered consolidated location listener with EventBus")
        }

        onDispose {
            if (EventBus.getDefault().isRegistered(locationObserver)) {
                EventBus.getDefault().unregister(locationObserver)
                Timber.d("Unregistered consolidated location listener from EventBus")
            }
        }
    }

    // COMBINED DISPOSABLE EFFECT - handles all lifecycle-related side effects
    DisposableEffect(Unit) {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(screenStateReceiver, intentFilter)

        lifecycle.addObserver(lifecycleObserver)

        if (isRecording) {
            Timber.d("DisposableEffect: Recording is active")
            pathTracker.setRecording(true)

            currentEventId.value = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                .getInt("active_event_id", -1)

            if (showPath && !followingState.isFollowing) {
                mapViewRef.value?.let { mapView ->
                    pathTracker.setCurrentEventId(currentEventId.value, mapView)
                }
            }
        } else if (!followingState.isFollowing) {
            try {
                Timber.d("DisposableEffect: Starting background service")
                val intent = Intent(context, BackgroundLocationService::class.java)
                context.startService(intent)
                pathTracker.setRecording(false)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start background service")
                Toast.makeText(context, "Failed to start location service", Toast.LENGTH_SHORT).show()
            }
        }

        onDispose {
            try {
                context.unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                Timber.e(e, "Error unregistering receiver")
            }

            lifecycle.removeObserver(lifecycleObserver)

            pathTracker.cleanup()

            if (!isRecording && !followingState.isFollowing) {
                try {
                    Timber.d("onDispose: Stopping background service")
                    context.stopService(Intent(context, BackgroundLocationService::class.java))
                } catch (e: Exception) {
                    Timber.e(e, "Error stopping background service")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OSM Map View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    val initialDarkMode = ctx.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                        .getBoolean("darkModeEnabled", false)

                    setTileSource(
                        if (initialDarkMode) createDarkTileSource() else TileSourceFactory.MAPNIK
                    )

                    setMultiTouchControls(true)
                    controller.setZoom(6)
                    controller.setCenter(GeoPoint(0.0, 0.0))

                    isDrawingCacheEnabled = true
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    val dm: DisplayMetrics = ctx.resources.displayMetrics
                    val scaleBarOverlay = ScaleBarOverlay(this).apply {
                        setCentred(true)
                        setScaleBarOffset(dm.widthPixels / 2, 10)
                    }
                    overlays.add(scaleBarOverlay)

                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation()

                        if (isFollowingLocation) {
                            enableFollowLocation()
                            Timber.d("Initial enableFollowLocation() called")
                        }

                        runOnFirstFix {
                            Handler(Looper.getMainLooper()).post {
                                if (!hasInitializedMapCenter && displayedRoute == null) {
                                    val location = myLocation
                                    if (location != null) {
                                        lastKnownLocation.value = location

                                        ignoringScrollEvents = true
                                        controller.setCenter(location)
                                        controller.setZoom(15)
                                        hasInitializedMapCenter = true

                                        Handler(Looper.getMainLooper()).postDelayed({
                                            ignoringScrollEvents = false
                                            Timber.d("Re-enabled scroll events after GPS fix")
                                        }, 300)

                                        Timber.d("Map centered on initial GPS fix: $location")
                                    }
                                }
                            }
                        }
                    }
                    overlays.add(locationOverlay)
                    locationOverlayRef.value = locationOverlay

                    val followedUsersOverlay = FollowedUsersOverlay(ctx, this)
                    overlays.add(followedUsersOverlay)
                    followedUsersOverlayRef.value = followedUsersOverlay

                    mapViewRef.value = this

                    if (showPath && isRecording && !followingState.isFollowing) {
                        pathTracker.initialize(this)
                        pathTracker.setCurrentEventId(currentEventId.value, this)
                        pathTracker.setRecording(isRecording)
                    }

                    // Touch-based scroll detection for auto-follow
                    // Set up touch listener to detect user interaction
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                lastTouchTime = System.currentTimeMillis()
                                Timber.d("Touch detected - potential manual interaction")
                            }
                        }
                        false // Don't consume the event
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastTouch = currentTime - lastTouchTime

                            Timber.d("Scroll event: isFollowing=$isFollowingLocation, timeSinceTouch=${timeSinceLastTouch}ms")

                            // REVOLUTIONARY APPROACH: Only disable auto-follow if there was a recent touch event
                            if (isFollowingLocation && timeSinceLastTouch < 1000) {
                                // Only disable if user touched the screen within the last second
                                locationOverlayRef.value?.disableFollowLocation()
                                isFollowingLocation = false
                                saveAutoFollowState(false)
                                Timber.d("Touch-based manual scroll detected - disabled auto-follow")
                            } else if (isFollowingLocation) {
                                // Scroll event without recent touch - likely automatic, ignore
                                Timber.d("Scroll event without recent touch - keeping auto-follow active")
                            }

                            if (showPath && isRecording && !followingState.isFollowing) {
                                pathTracker.updatePathForViewport(this@apply)
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
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

        // Route display indicator - shows when displaying a route from events
        if (showRouteDisplayIndicator && displayedRoute != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Red.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Route Display",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Displaying Event Route (${displayedRoute?.size ?: 0} points)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            // Clear the displayed route
                            mapViewRef.value?.let { mapView ->
                                routeOverlayRef.value?.let { overlay ->
                                    mapView.overlays.remove(overlay)
                                    mapView.invalidate()
                                }
                            }
                            displayedRoute = null
                            showRouteDisplayIndicator = false
                            routeOverlayRef.value = null

                            // Re-enable auto-follow if not recording
                            if (!isRecording) {
                                isFollowingLocation = true
                                saveAutoFollowState(true)
                                triggerMyLocationLogic()
                            }

                            Timber.d("Cleared displayed route")
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear Route",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Heart Rate Panel - only show when recording
        if (isRecording && !followingState.isFollowing) {
            HeartRatePanel(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = if (showRouteDisplayIndicator) 120.dp else 64.dp, start = 16.dp)
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

        // Path loading indicator
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

        // Connection status indicator
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

        // Control buttons column at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Auto-follow indicator above My Location button
            if (isFollowingLocation && hasInitializedMapCenter) {
                Surface(
                    shape = CircleShape,
                    color = Color.Blue.copy(alpha = 0.8f),
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = "Auto-Follow",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // My Location button (moved from top right) - always visible
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = if (isFollowingLocation) Color.Blue else Color.White,
                shadowElevation = 8.dp,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            Timber.d("My Location button clicked - toggling auto-follow")

                            isFollowingLocation = !isFollowingLocation
                            saveAutoFollowState(isFollowingLocation)

                            if (isFollowingLocation) {
                                triggerMyLocationLogic()
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = if (isFollowingLocation) "Disable Auto-Follow" else "Enable Auto-Follow",
                        tint = if (isFollowingLocation) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                                    followingService.stopFollowing()
                                } else {
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
            onSave = { eventName, eventDate, artOfSport, comment, clothing, pathOption, heartRateSensor, enableWebSocketTransfer ->
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
                Timber.d("Recording started, set isRecording=true")

                val stopIntent = Intent(context, BackgroundLocationService::class.java)
                context.stopService(stopIntent)
                Timber.d("Stopped BackgroundLocationService")

                val intent = Intent(context, ForegroundService::class.java).apply {
                    putExtra("eventName", eventName)
                    putExtra("eventDate", eventDate)
                    putExtra("artOfSport", artOfSport)
                    putExtra("comment", comment)
                    putExtra("clothing", clothing)
                    putExtra("start_recording", true)
                    putExtra("enableWebSocketTransfer", enableWebSocketTransfer) // NEW: Pass WebSocket setting

                    heartRateSensor?.let {
                        putExtra("heartRateDeviceAddress", it.address)
                        putExtra("heartRateDeviceName", it.name)
                        Timber.d("Adding heart rate sensor: ${it.name} (${it.address})")
                    }
                }
                ContextCompat.startForegroundService(context, intent)
                Timber.d("Started ForegroundService with event details, WebSocket transfer: $enableWebSocketTransfer, and start_recording=true")

                showRecordingDialog = false

                // Recording dialog completion handler
                Handler(Looper.getMainLooper()).postDelayed({
                    val newEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .getInt("active_event_id", -1)

                    mapViewRef.value?.let { mapView ->
                        if (newEventId > 0 && pathOption) {
                            Timber.d("Setting path tracker to new event: $newEventId")
                            pathTracker.setCurrentEventId(newEventId, mapView)
                        }

                        // Restore zoom and center
                        mapView.controller.setZoom(currentZoomLevel)
                        currentCenter?.let { center ->
                            mapView.controller.setCenter(center)
                        }

                        isFollowingLocation = true
                        saveAutoFollowState(true)
                        triggerMyLocationLogic()

                        Timber.d("Restored map zoom level to $currentZoomLevel after recording started")
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