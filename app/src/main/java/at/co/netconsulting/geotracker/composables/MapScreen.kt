package at.co.netconsulting.geotracker.composables

import android.content.Context
import android.content.Intent
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
import at.co.netconsulting.geotracker.service.BackgroundLocationService
import at.co.netconsulting.geotracker.service.ForegroundService
import kotlinx.coroutines.delay
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
    var isRecording by remember {
        mutableStateOf(
            context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_recording", false)
        )
    }
    var showRecordingDialog by remember { mutableStateOf(false) }

    // Start the appropriate service when the screen is shown
    DisposableEffect(key1 = isRecording) {
        if (isRecording) {
            // Recording is active, ForegroundService should be running
            // No need to start it again here as it's started from the dialog
            Log.d("MapScreen", "DisposableEffect: Recording is active")
        } else {
            // Start background service for normal tracking (if not already running)
            try {
                Log.d("MapScreen", "DisposableEffect: Starting background service")
                val intent = Intent(context, BackgroundLocationService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("MapScreen", "Failed to start background service", e)
                Toast.makeText(context, "Failed to start location service", Toast.LENGTH_SHORT).show()
            }
        }

        // Clean up when leaving the screen
        onDispose {
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

    // Monitor recording state changes
    LaunchedEffect(Unit) {
        // Check recording state periodically
        while (true) {
            val newIsRecording = context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .getBoolean("is_recording", false)

            if (isRecording != newIsRecording) {
                Log.d("MapScreen", "Recording state changed from $isRecording to $newIsRecording")
                isRecording = newIsRecording
            }

            delay(1000)
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
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Recording control button
        if (!isRecording) {
            // Start recording button
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-16).dp, y = (-16).dp)
                    .padding(16.dp)
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
                    .align(Alignment.BottomEnd)
                    .offset(x = (-16).dp, y = (-16).dp)
                    .padding(16.dp)
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

    // Recording dialog
    if (showRecordingDialog) {
        RecordingDialog(
            onSave = { eventName, eventDate, artOfSport, comment, clothing ->
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
                    putExtra("start_recording", true)  // Explicit flag
                }
                ContextCompat.startForegroundService(context, intent)
                Log.d("MapScreen", "Started ForegroundService with event details and start_recording=true")

                showRecordingDialog = false
            },
            onDismiss = {
                showRecordingDialog = false
            }
        )
    }
}