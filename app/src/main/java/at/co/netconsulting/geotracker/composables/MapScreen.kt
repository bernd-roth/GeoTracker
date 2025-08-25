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
import at.co.netconsulting.geotracker.data.FollowedUserPoint
import at.co.netconsulting.geotracker.data.GpsStatus
import at.co.netconsulting.geotracker.data.ImportedGpxTrack
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.location.FollowedUsersOverlay
import at.co.netconsulting.geotracker.location.ViewportPathTracker
import at.co.netconsulting.geotracker.service.BackgroundLocationService
import at.co.netconsulting.geotracker.service.FollowingService
import at.co.netconsulting.geotracker.service.ForegroundService
import at.co.netconsulting.geotracker.tools.Tools
import at.co.netconsulting.geotracker.tools.GpxPersistenceUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    onNavigateToSettings: () -> Unit,
    routeToDisplay: List<GeoPoint>?,
    onRouteDisplayed: () -> Unit,
    getCurrentGpsStatus: () -> GpsStatus,
    importedGpxTrack: ImportedGpxTrack? = null,
    onGpxTrackDisplayed: () -> Unit = {}
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

    // Track overlay references and state
    val gpxTrackOverlayRef = remember { mutableStateOf<Polyline?>(null) }
    var currentImportedTrack by remember { mutableStateOf<ImportedGpxTrack?>(null) }

    // Flag to track when map is fully initialized
    var isMapInitialized by remember { mutableStateOf(false) }

    // Persist auto-follow state
    var isFollowingLocation by remember {
        mutableStateOf(
            context.getSharedPreferences("MapSettings", Context.MODE_PRIVATE)
                .getBoolean("auto_follow_enabled", true)
        )
    }

    // Auto-follow followed users state
    var isAutoFollowingUsers by remember {
        mutableStateOf(
            context.getSharedPreferences("MapSettings", Context.MODE_PRIVATE)
                .getBoolean("auto_follow_users_enabled", false)
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

    // Track the last known timestamp for efficient change detection
    val lastTrackTimestamp = remember { mutableStateOf(0L) }

    // Function to save auto-follow state
    fun saveAutoFollowState(enabled: Boolean) {
        context.getSharedPreferences("MapSettings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_follow_enabled", enabled)
            .apply()
        Timber.d("Saved auto-follow state: $enabled")
    }

    // Function to save auto-follow users state
    fun saveAutoFollowUsersState(enabled: Boolean) {
        context.getSharedPreferences("MapSettings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_follow_users_enabled", enabled)
            .apply()
        Timber.d("Saved auto-follow users state: $enabled")
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

    // Function to auto-follow followed users
    fun autoFollowUsers() {
        if (isAutoFollowingUsers && followingState.isFollowing) {
            // Get current positions of followed users
            val followedUserPositions = followingState.followedUserTrails.values
                .mapNotNull { trail -> trail.lastOrNull() }
                .filter { it.latitude != 0.0 && it.longitude != 0.0 }

            if (followedUserPositions.isNotEmpty()) {
                mapViewRef.value?.let { mapView ->
                    ignoringScrollEvents = true

                    if (followedUserPositions.size == 1) {
                        // Follow single user
                        val userPoint = followedUserPositions.first()
                        val geoPoint = GeoPoint(userPoint.latitude, userPoint.longitude)
                        mapView.controller.setCenter(geoPoint)
                        Timber.d("Auto-following single user: ${userPoint.person} at $geoPoint")
                    } else {
                        // Follow multiple users - center on average position
                        val avgLat = followedUserPositions.map { it.latitude }.average()
                        val avgLon = followedUserPositions.map { it.longitude }.average()
                        val centerPoint = GeoPoint(avgLat, avgLon)
                        mapView.controller.setCenter(centerPoint)
                        Timber.d("Auto-following ${followedUserPositions.size} users at $centerPoint")
                    }

                    Handler(Looper.getMainLooper()).postDelayed({
                        ignoringScrollEvents = false
                    }, 300)
                }
            }
        }
    }

    // Function to display track on map
    fun displayTrackOnMap(track: ImportedGpxTrack) {
        mapViewRef.value?.let { mapView ->
            // Remove existing GPX track overlay if present
            gpxTrackOverlayRef.value?.let { existingOverlay ->
                mapView.overlays.remove(existingOverlay)
                Timber.d("Removed existing GPX track overlay")
            }

            // Create new GPX track overlay in BLACK
            val gpxOverlay = Polyline().apply {
                setPoints(track.points)
                color = android.graphics.Color.BLACK
                width = 8f
                title = "Track: ${track.filename}"
                paint.alpha = 200
            }

            // Add the GPX overlay to the map (add it first so live track appears on top)
            mapView.overlays.add(0, gpxOverlay)
            gpxTrackOverlayRef.value = gpxOverlay
            currentImportedTrack = track

            // Center map on the imported track only if not recording or if it's a new track
            if (track.points.isNotEmpty() && (!isRecording || currentImportedTrack?.filename != track.filename)) {
                val minLat = track.points.minOf { it.latitude }
                val maxLat = track.points.maxOf { it.latitude }
                val minLon = track.points.minOf { it.longitude }
                val maxLon = track.points.maxOf { it.longitude }

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

                // Disable auto-follow when displaying imported track (unless recording)
                if (!isRecording) {
                    isFollowingLocation = false
                    saveAutoFollowState(false)
                }

                Timber.d("Displayed track, centered at $center, zoom $zoomLevel")
            }

            mapView.invalidate()
        }
    }

    // Function to clear all tracks from map
    fun clearAllTracksFromMap() {
        mapViewRef.value?.let { mapView ->
            // Clear GPX track overlay
            gpxTrackOverlayRef.value?.let { overlay ->
                mapView.overlays.remove(overlay)
                Timber.d("Removed GPX track overlay")
            }
            gpxTrackOverlayRef.value = null
            currentImportedTrack = null

            // Clear live recording path
            pathTracker.clearPath(mapView)

            mapView.invalidate()
            Timber.d("Cleared all tracks from map")
        }

        // Clear persistence
        GpxPersistenceUtil.clearImportedGpxTrack(context)
    }

    // Efficient track monitoring using timestamp
    LaunchedEffect(Unit) {
        while (true) {
            if (isMapInitialized) {
                val currentTimestamp = GpxPersistenceUtil.getTrackTimestamp(context)

                // Only check for changes if timestamp has changed
                if (currentTimestamp != lastTrackTimestamp.value) {
                    lastTrackTimestamp.value = currentTimestamp

                    val persistedTrack = GpxPersistenceUtil.loadImportedGpxTrack(context)

                    when {
                        persistedTrack == null && currentImportedTrack != null -> {
                            // Track was cleared, remove it from map
                            Timber.d("Persisted track cleared, removing from map")
                            mapViewRef.value?.let { mapView ->
                                gpxTrackOverlayRef.value?.let { overlay ->
                                    mapView.overlays.remove(overlay)
                                }
                                mapView.invalidate()
                            }
                            gpxTrackOverlayRef.value = null
                            currentImportedTrack = null
                        }
                        persistedTrack != null &&
                                (currentImportedTrack == null ||
                                        persistedTrack.filename != currentImportedTrack!!.filename) -> {
                            // New or different track, load it
                            Timber.d("Loading/updating track: ${persistedTrack.filename}")
                            displayTrackOnMap(persistedTrack)
                        }
                    }
                }
            }
            delay(500) // Check every 500ms for more responsive updates
        }
    }

    // Simplified initial loading
    LaunchedEffect(isMapInitialized) {
        if (isMapInitialized) {
            // Initialize the timestamp tracking
            lastTrackTimestamp.value = GpxPersistenceUtil.getTrackTimestamp(context)

            val persistedTrack = GpxPersistenceUtil.loadImportedGpxTrack(context)
            if (persistedTrack != null && currentImportedTrack == null) {
                Timber.d("Loading persisted track on initialization: ${persistedTrack.filename}")
                delay(200)
                displayTrackOnMap(persistedTrack)
            }
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
                    color = android.graphics.Color.RED
                    width = 8f
                    title = "Event Route"
                }

                // Add the route overlay to the map
                mapView.overlays.add(routeOverlay)
                routeOverlayRef.value = routeOverlay

                // Center map on the route
                if (routeToDisplay.isNotEmpty()) {
                    val minLat = routeToDisplay.minOf { it.latitude }
                    val maxLat = routeToDisplay.maxOf { it.latitude }
                    val minLon = routeToDisplay.minOf { it.longitude }
                    val maxLon = routeToDisplay.maxOf { it.longitude }

                    val centerLat = (minLat + maxLat) / 2
                    val centerLon = (minLon + maxLon) / 2
                    val center = GeoPoint(centerLat, centerLon)

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

                    Timber.d("Displayed route with ${routeToDisplay.size} points")
                }
            }

            // Notify that route has been displayed
            onRouteDisplayed()
        }
    }

    // Handle following state changes - center map on followed users when following starts
    LaunchedEffect(followingState.isFollowing, followingState.followedUsers) {
        if (followingState.isFollowing && followingState.followedUsers.isNotEmpty()) {
            // Get positions of followed users from activeUsers data
            val followedUserPositions = activeUsers.filter { user ->
                user.sessionId in followingState.followedUsers &&
                        user.latitude != 0.0 && user.longitude != 0.0
            }

            if (followedUserPositions.isNotEmpty()) {
                mapViewRef.value?.let { mapView ->
                    // If following a single user, center on that user
                    if (followedUserPositions.size == 1) {
                        val user = followedUserPositions.first()
                        val geoPoint = GeoPoint(user.latitude, user.longitude)

                        ignoringScrollEvents = true
                        mapView.controller.setCenter(geoPoint)
                        mapView.controller.setZoom(15.0)

                        Handler(Looper.getMainLooper()).postDelayed({
                            ignoringScrollEvents = false
                        }, 300)

                        Timber.d("Centered map on followed user: ${user.person} at $geoPoint")
                    } else {
                        // If following multiple users, calculate center point
                        val avgLat = followedUserPositions.map { it.latitude }.average()
                        val avgLon = followedUserPositions.map { it.longitude }.average()
                        val centerPoint = GeoPoint(avgLat, avgLon)

                        ignoringScrollEvents = true
                        mapView.controller.setCenter(centerPoint)
                        mapView.controller.setZoom(13.0) // Slightly zoomed out for multiple users

                        Handler(Looper.getMainLooper()).postDelayed({
                            ignoringScrollEvents = false
                        }, 300)

                        Timber.d("Centered map on ${followedUserPositions.size} followed users at $centerPoint")
                    }

                    // Disable auto-follow when following other users (even during recording)
                    isFollowingLocation = false
                    saveAutoFollowState(false)
                    Timber.d("Disabled auto-follow when starting to follow users (recording: $isRecording)")
                }
            }
        } else if (!followingState.isFollowing) {
            // Re-enable auto-follow when stopping following (regardless of recording state)
            if (!isRecording) {
                // Only auto-enable if not recording - during recording user controls this manually
                isFollowingLocation = true
                saveAutoFollowState(true)
                // Optionally trigger my location logic to center back on user
                Handler(Looper.getMainLooper()).postDelayed({
                    triggerMyLocationLogic()
                }, 500)
                Timber.d("Re-enabled auto-follow when stopped following users (not recording)")
            } else {
                Timber.d("Stopped following during recording - auto-follow remains user-controlled")
            }
        }
    }

    // Single location listener with forced updates during recording
    val locationObserver = remember {
        object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onLocationUpdate(metrics: Metrics) {
                Timber.d("Location update: lat=${metrics.latitude}, lon=${metrics.longitude}, follow=$isFollowingLocation")

                // Update last known location
                if (metrics.latitude != 0.0 && metrics.longitude != 0.0) {
                    lastKnownLocation.value = GeoPoint(metrics.latitude, metrics.longitude)
                }

                // Handle auto-follow functionality - but not when following other users
                if (isFollowingLocation && metrics.latitude != 0.0 && metrics.longitude != 0.0 && !followingState.isFollowing) {
                    mapViewRef.value?.let { mapView ->
                        val newLocation = GeoPoint(metrics.latitude, metrics.longitude)

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

                        locationOverlayRef.value?.let { overlay ->
                            if (!overlay.isFollowLocationEnabled) {
                                overlay.enableFollowLocation()
                                Timber.d("Re-enabled overlay follow location during location update")
                            }
                        }

                        Handler(Looper.getMainLooper()).postDelayed({
                            ignoringScrollEvents = false
                            Timber.d("Re-enabled scroll event handling after location update")
                        }, 300)
                    }
                }

                // Force path updates when recording - ignore viewport restrictions
                if (showPath && currentEventId.value > 0 && isRecording && !followingState.isFollowing) {
                    mapViewRef.value?.let { mapView ->
                        // ALWAYS force update during recording - bypass all similarity checks
                        pathTracker.updatePathForViewport(mapView, forceUpdate = true)

                        // Additional immediate refresh for good measure
                        Handler(Looper.getMainLooper()).postDelayed({
                            pathTracker.refreshPath(mapView)
                            Timber.d("Additional path refresh after location update")
                        }, 100)
                    }
                }
            }
        }
    }

    // Periodic path updates during recording to ensure path always appears
    LaunchedEffect(isRecording, showPath, currentEventId.value) {
        if (isRecording && showPath && currentEventId.value > 0 && !followingState.isFollowing) {
            while (isRecording && showPath && currentEventId.value > 0 && !followingState.isFollowing) {
                delay(2000) // Update every 2 seconds during recording

                mapViewRef.value?.let { mapView ->
                    Timber.d("Periodic path refresh during recording")
                    pathTracker.updatePathForViewport(mapView, forceUpdate = true)
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
                        Timber.d("Screen turned ON - triggering auto-follow logic")

                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isFollowingLocation) {
                                triggerMyLocationLogic()
                            }
                            if (isAutoFollowingUsers) {
                                autoFollowUsers()
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
                        Timber.d("Screen turned OFF - saving auto-follow states: ownLocation=$isFollowingLocation, users=$isAutoFollowingUsers")
                        saveAutoFollowState(isFollowingLocation)
                        saveAutoFollowUsersState(isAutoFollowingUsers)
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
                Timber.d("App RESUMED - triggering auto-follow logic")

                Handler(Looper.getMainLooper()).postDelayed({
                    if (isFollowingLocation) {
                        triggerMyLocationLogic()
                    }
                    if (isAutoFollowingUsers) {
                        autoFollowUsers()
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
                Timber.d("App PAUSED - saving auto-follow states: ownLocation=$isFollowingLocation, users=$isAutoFollowingUsers")
                saveAutoFollowState(isFollowingLocation)
                saveAutoFollowUsersState(isAutoFollowingUsers)
            }
        }
    }

    // Periodic auto-follow monitoring for both own location and users
    LaunchedEffect(isFollowingLocation, isAutoFollowingUsers) {
        if (isFollowingLocation || isAutoFollowingUsers) {
            while (true) {
                delay(10000) // Check every 10 seconds

                if (isFollowingLocation) {
                    locationOverlayRef.value?.let { overlay ->
                        if (!overlay.isFollowLocationEnabled && isFollowingLocation) {
                            Timber.d("Detected overlay follow disabled - triggering My Location logic")
                            triggerMyLocationLogic()
                        }
                    }
                }

                if (isAutoFollowingUsers && followingState.isFollowing) {
                    Timber.d("Periodic auto-follow users check")
                    autoFollowUsers()
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

    // Update followed users overlay when following state changes AND auto-follow users
    LaunchedEffect(followingState.followedUserTrails) {
        followedUsersOverlayRef.value?.updateFollowedUsersWithTrails(followingState.followedUserTrails)

        // Auto-follow users when their positions update
        if (isAutoFollowingUsers && followingState.isFollowing) {
            autoFollowUsers()
        }
    }

    // Connect to following service when not connected (allow during recording)
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            followingService.connect()
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

    // Initialize or clean up path tracker with immediate updates
    LaunchedEffect(showPath, currentEventId.value, isRecording, followingState.isFollowing) {
        if (showPath && isRecording && !followingState.isFollowing) {
            mapViewRef.value?.let { mapView ->
                pathTracker.initialize(mapView)
                pathTracker.setCurrentEventId(currentEventId.value, mapView)
                pathTracker.setRecording(isRecording)

                // Force immediate update when initializing
                pathTracker.updatePathForViewport(mapView, forceUpdate = true)

                // Schedule additional immediate updates for the first few seconds
                repeat(5) { attempt ->
                    delay(1000L * (attempt + 1)) // 1s, 2s, 3s, 4s, 5s
                    if (isActive && isRecording && showPath && !followingState.isFollowing) {
                        pathTracker.updatePathForViewport(mapView, forceUpdate = true)
                        Timber.d("Scheduled path update ${attempt + 1}")
                    }
                }
            }
            Timber.d("Path tracker initialized with immediate updates for event: ${currentEventId.value}")
        } else {
            mapViewRef.value?.let { mapView ->
                pathTracker.clearPath(mapView)
            }
            Timber.d("Path tracking disabled")
        }
    }

    // Monitor recording state changes - with track cleanup when recording stops
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

                // If recording stopped, clear all tracks
                if (isRecording && !newIsRecording) {
                    Timber.d("Recording stopped - clearing all tracks")
                    clearAllTracksFromMap()
                }

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

    // Check if service is actually running on initial launch AND restore auto-follow states
    LaunchedEffect(Unit) {
        // Restore auto-follow states from SharedPreferences
        isAutoFollowingUsers = context.getSharedPreferences("MapSettings", Context.MODE_PRIVATE)
            .getBoolean("auto_follow_users_enabled", false)

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

        Timber.d("Restored auto-follow states: ownLocation=$isFollowingLocation, users=$isAutoFollowingUsers")
    }

    // Single location update listener registration
    DisposableEffect(locationObserver) {
        if (!EventBus.getDefault().isRegistered(locationObserver)) {
            EventBus.getDefault().register(locationObserver)
            Timber.d("Registered enhanced location listener with EventBus")
        }

        onDispose {
            if (EventBus.getDefault().isRegistered(locationObserver)) {
                EventBus.getDefault().unregister(locationObserver)
                Timber.d("Unregistered enhanced location listener from EventBus")
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
        } else if (!followingState.isFollowing && !isRecording) {
            // Only start background service if not recording AND not following
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
                                if (!hasInitializedMapCenter && displayedRoute == null && currentImportedTrack == null) {
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
                    setOnTouchListener { _, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                lastTouchTime = System.currentTimeMillis()
                                Timber.d("Touch detected - potential manual interaction")
                            }
                        }
                        false
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            val currentTime = System.currentTimeMillis()
                            val timeSinceLastTouch = currentTime - lastTouchTime

                            Timber.d("Scroll event: isFollowing=$isFollowingLocation, isFollowingUsers=$isAutoFollowingUsers, timeSinceTouch=${timeSinceLastTouch}ms")

                            if (timeSinceLastTouch < 1000) {
                                // User manually scrolled - disable both auto-follow modes
                                if (isFollowingLocation) {
                                    locationOverlayRef.value?.disableFollowLocation()
                                    isFollowingLocation = false
                                    saveAutoFollowState(false)
                                    Timber.d("Touch-based manual scroll detected - disabled auto-follow own location")
                                }

                                if (isAutoFollowingUsers) {
                                    isAutoFollowingUsers = false
                                    saveAutoFollowUsersState(false)
                                    Timber.d("Touch-based manual scroll detected - disabled auto-follow users")
                                }
                            } else {
                                Timber.d("Scroll event without recent touch - keeping auto-follow states active")
                            }

                            // Force path updates during recording on scroll
                            if (showPath && isRecording && !followingState.isFollowing) {
                                pathTracker.updatePathForViewport(this@apply, forceUpdate = true)
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            // Force path updates during recording on zoom
                            if (showPath && isRecording && !followingState.isFollowing) {
                                pathTracker.updatePathForViewport(this@apply, forceUpdate = true)
                            }
                            return true
                        }
                    })

                    // Mark map as initialized after a short delay to ensure everything is set up
                    Handler(Looper.getMainLooper()).postDelayed({
                        isMapInitialized = true
                        Timber.d("Map initialization completed")

                        // If we have a current track, redisplay it after map is initialized
                        currentImportedTrack?.let { track ->
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (gpxTrackOverlayRef.value == null) {
                                    Timber.d("Redisplaying existing track after map reinitialization")
                                    displayTrackOnMap(track)
                                }
                            }, 100)
                        }
                    }, 300)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Track Display Indicator
        if (currentImportedTrack != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Track Overlay",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Track: ${currentImportedTrack?.filename} (${currentImportedTrack?.points?.size ?: 0} pts)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Only show close button when not recording
                    if (!isRecording) {
                        IconButton(
                            onClick = {
                                // Clear all tracks
                                clearAllTracksFromMap()
                                Timber.d("Manually cleared all tracks")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Track",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Route display indicator - shows when displaying a route from events
        if (showRouteDisplayIndicator && displayedRoute != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (currentImportedTrack != null) 80.dp else 16.dp),
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
                    .padding(
                        top = when {
                            currentImportedTrack != null && showRouteDisplayIndicator -> 180.dp
                            currentImportedTrack != null -> 120.dp
                            showRouteDisplayIndicator -> 120.dp
                            else -> 64.dp
                        },
                        start = 16.dp
                    )
            )
        }

        // Following indicator
        if (followingState.isFollowing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = when {
                        currentImportedTrack != null && showRouteDisplayIndicator -> 160.dp
                        currentImportedTrack != null || showRouteDisplayIndicator -> 80.dp
                        else -> 16.dp
                    }),
                shape = CircleShape,
                color = if (isRecording) Color.Green else Color.Blue,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = if (isRecording) {
                        "Recording + Following ${followingState.followedUsers.size} user(s)"
                    } else {
                        "Following ${followingState.followedUsers.size} user(s)"
                    },
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
            if ((isFollowingLocation || isAutoFollowingUsers) && hasInitializedMapCenter) {
                Surface(
                    shape = CircleShape,
                    color = if (isAutoFollowingUsers) Color.Green.copy(alpha = 0.8f) else Color.Blue.copy(alpha = 0.8f),
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = if (isAutoFollowingUsers) "Following Users" else "Auto-Follow",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // My Location button
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = when {
                    isAutoFollowingUsers -> Color.Green
                    isFollowingLocation -> Color.Blue
                    else -> Color.White
                },
                shadowElevation = 8.dp,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            Timber.d("My Location button clicked - current states: followLocation=$isFollowingLocation, followUsers=$isAutoFollowingUsers")

                            when {
                                isAutoFollowingUsers -> {
                                    // Currently following users, switch to own location
                                    isAutoFollowingUsers = false
                                    saveAutoFollowUsersState(false)
                                    isFollowingLocation = true
                                    saveAutoFollowState(true)
                                    triggerMyLocationLogic()
                                    Timber.d("Switched from following users to own location")
                                }
                                isFollowingLocation -> {
                                    // Currently following own location, disable
                                    isFollowingLocation = false
                                    saveAutoFollowState(false)
                                    Timber.d("Disabled auto-follow own location")
                                }
                                else -> {
                                    // Neither active, enable own location
                                    isFollowingLocation = true
                                    saveAutoFollowState(true)
                                    triggerMyLocationLogic()
                                    Timber.d("Enabled auto-follow own location")
                                }
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = when {
                            isAutoFollowingUsers -> "Switch to My Location"
                            isFollowingLocation -> "Disable Auto-Follow"
                            else -> "Enable Auto-Follow"
                        },
                        tint = when {
                            isAutoFollowingUsers || isFollowingLocation -> Color.White
                            else -> Color.Black
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Follow Users button - always available
            Surface(
                modifier = Modifier.size(56.dp),
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

            // Recording controls
            if (!isRecording && !followingState.isFollowing) {
                Surface(
                    modifier = Modifier.size(56.dp),
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
                    modifier = Modifier.size(56.dp),
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

                                // Stop the service
                                val stopIntent = Intent(context, ForegroundService::class.java)
                                stopIntent.putExtra("stopping_intentionally", true)
                                context.stopService(stopIntent)

                                context.getSharedPreferences("ServiceState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("was_running", false)
                                    .apply()

                                // Clear all tracks immediately
                                clearAllTracksFromMap()

                                // Update local state
                                isRecording = false

                                val intent = Intent(context, BackgroundLocationService::class.java)
                                context.startService(intent)

                                Toast.makeText(context, "Recording Stopped", Toast.LENGTH_SHORT).show()
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
            currentPrecisionMode = followingService.getTrailPrecision(),
            onFollowUsers = { selectedSessionIds ->
                followingService.followUsers(selectedSessionIds)
            },
            onStopFollowing = {
                followingService.stopFollowing()
            },
            onRefreshUsers = {
                followingService.requestActiveUsers()
            },
            onPrecisionModeChanged = { mode ->
                followingService.setTrailPrecision(mode)

                Toast.makeText(
                    context,
                    "Trail precision: ${mode.description}",
                    Toast.LENGTH_SHORT
                ).show()
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
                        onNavigateToSettings()
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
            gpsStatus = getCurrentGpsStatus(),
            onSave = { eventName, eventDate, artOfSport, comment, clothing, pathOption, heartRateSensor, enableWebSocketTransfer, importedGpx ->
                val currentZoomLevel = mapViewRef.value?.zoomLevelDouble ?: 15.0
                val currentCenter = mapViewRef.value?.mapCenter

                // Handle imported track
                importedGpx?.let { gpxTrack ->
                    GpxPersistenceUtil.saveImportedGpxTrack(context, gpxTrack)
                    Timber.d("Recording started with track: ${gpxTrack.filename}")
                }

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
                    putExtra("enableWebSocketTransfer", enableWebSocketTransfer)

                    importedGpx?.let { gpxTrack ->
                        putExtra("hasImportedGpx", true)
                        putExtra("gpxFilename", gpxTrack.filename)
                        putExtra("gpxPointCount", gpxTrack.points.size)
                    }

                    heartRateSensor?.let {
                        putExtra("heartRateDeviceAddress", it.address)
                        putExtra("heartRateDeviceName", it.name)
                        Timber.d("Adding heart rate sensor: ${it.name} (${it.address})")
                    }
                }
                ContextCompat.startForegroundService(context, intent)
                Timber.d("Started ForegroundService with event details")

                showRecordingDialog = false

                // Post-recording setup with immediate path display
                Handler(Looper.getMainLooper()).postDelayed({
                    val newEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .getInt("active_event_id", -1)

                    mapViewRef.value?.let { mapView ->
                        if (newEventId > 0 && pathOption) {
                            Timber.d("Setting path tracker to new event: $newEventId")
                            pathTracker.setCurrentEventId(newEventId, mapView)

                            // Force immediate path display multiple times
                            pathTracker.updatePathForViewport(mapView, forceUpdate = true)

                            // Schedule immediate displays
                            Handler(Looper.getMainLooper()).postDelayed({
                                pathTracker.updatePathForViewport(mapView, forceUpdate = true)
                            }, 500)

                            Handler(Looper.getMainLooper()).postDelayed({
                                pathTracker.refreshPath(mapView)
                            }, 1500)

                            Handler(Looper.getMainLooper()).postDelayed({
                                pathTracker.refreshPath(mapView)
                            }, 3000)
                        }

                        // Restore map position if no track is imported
                        if (importedGpx == null) {
                            mapView.controller.setZoom(currentZoomLevel)
                            currentCenter?.let { center ->
                                mapView.controller.setCenter(center)
                            }
                        }

                        isFollowingLocation = true
                        saveAutoFollowState(true)
                        triggerMyLocationLogic()

                        Timber.d("Enhanced setup completed with immediate path display")
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