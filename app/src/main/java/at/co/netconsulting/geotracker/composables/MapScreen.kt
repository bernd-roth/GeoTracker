package at.co.netconsulting.geotracker.composables

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
            }
        }
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

                    // Add my location overlay
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation()
                        enableFollowLocation()
                    }
                    overlays.add(locationOverlay)

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
                            if (showPath) {
                                pathTracker.updatePathForViewport(this@apply)
                            }
                            return true
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
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

        // Control buttons row at the bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
            if (showPath) {
                Surface(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(48.dp),
                    shape = CircleShape,
                    color = Color.Gray,
                    shadowElevation = 4.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                // Clear the path
                                mapViewRef.value?.let { mapView ->
                                    pathTracker.clearPath(mapView)
                                    Toast.makeText(context, "Path cleared", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Clear Path",
                            tint = Color.White
                        )
                    }
                }
            }
            */

            // Toggle recording button
            if (!isRecording) {
                // Start recording button
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
                // Stop recording button
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
                                // Stop recording
                                val sharedPreferences = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                                val currentEventId = sharedPreferences.getInt("active_event_id", -1)
                                if (currentEventId != -1) {
                                    // Store as last event ID
                                    sharedPreferences.edit()
                                        .putInt("last_event_id", currentEventId)
                                        // Remove active_event_id key completely
                                        .remove("active_event_id")
                                        .apply()

                                    Log.d("MapScreen", "Saved last_event_id=$currentEventId and removed active_event_id")
                                }

                                // Update recording state
                                context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean("is_recording", false)
                                    .apply()

                                isRecording = false
                                Log.d("MapScreen", "Set isRecording=false in SharedPreferences and local state")

                                // Stop the foreground service
                                val stopIntent = Intent(context, ForegroundService::class.java)
                                // Set flag to mark the stop as intentional
                                stopIntent.putExtra("stopping_intentionally", true)
                                context.stopService(stopIntent)
                                Log.d("MapScreen", "Stopped ForegroundService with stopping_intentionally=true")

                                // Start the background service
                                val intent = Intent(context, BackgroundLocationService::class.java)
                                context.startService(intent)
                                Log.d("MapScreen", "Started BackgroundLocationService")

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

    // Recording dialog
    if (showRecordingDialog) {
        RecordingDialog(
            onSave = { eventName, eventDate, artOfSport, comment, clothing, pathOption ->
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
                }
                ContextCompat.startForegroundService(context, intent)
                Log.d("MapScreen", "Started ForegroundService with event details and start_recording=true")

                showRecordingDialog = false

                // Get the newly created event ID after a short delay
                // to ensure the service has created it
                Handler(Looper.getMainLooper()).postDelayed({
                    val newEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                        .getInt("active_event_id", -1)

                    if (newEventId > 0 && pathOption) {
                        Log.d("MapScreen", "Setting path tracker to new event: $newEventId")
                        mapViewRef.value?.let { mapView ->
                            pathTracker.setCurrentEventId(newEventId, mapView)
                        }
                    }
                }, 500) // 500ms delay
            },
            onDismiss = {
                showRecordingDialog = false
            }
        )
    }
}