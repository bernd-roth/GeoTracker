package at.co.netconsulting.geotracker.composables

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Waypoint
import at.co.netconsulting.geotracker.data.WaypointData
import at.co.netconsulting.geotracker.data.WebSocketMessage
import org.greenrobot.eventbus.EventBus
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import timber.log.Timber

@Composable
fun WaypointDialog(
    currentLatitude: Double,
    currentLongitude: Double,
    currentEventId: Int,
    onDismiss: () -> Unit,
    onWaypointSaved: () -> Unit
) {
    val context = LocalContext.current
    var waypointName by remember { mutableStateOf("") }
    var waypointDescription by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    
    // Map state
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val currentLocation = remember { GeoPoint(currentLatitude, currentLongitude) }
    
    // Database
    val database = remember { FitnessTrackerDatabase.getInstance(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Waypoint",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Waypoint")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Location display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Location",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Latitude: ${String.format("%.6f", currentLatitude)}°",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Longitude: ${String.format("%.6f", currentLongitude)}°",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Map preview
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    AndroidView(
                        factory = { ctx ->
                            // Initialize OSMDroid configuration if not already done
                            try {
                                Configuration.getInstance().load(
                                    ctx,
                                    ctx.getSharedPreferences("osm_pref", Context.MODE_PRIVATE)
                                )
                                Configuration.getInstance().userAgentValue = "GeoTracker/1.0"
                            } catch (e: Exception) {
                                Timber.e(e, "Error initializing OSMDroid config")
                            }

                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(16.0) // Close zoom for waypoint detail
                                controller.setCenter(currentLocation)
                                
                                // Disable hardware acceleration for the small preview
                                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                
                                // Add current location marker
                                val marker = Marker(this).apply {
                                    position = currentLocation
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "Waypoint Location"
                                }
                                overlays.add(marker)
                                
                                // Store reference
                                mapViewRef.value = this
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Waypoint name input
                OutlinedTextField(
                    value = waypointName,
                    onValueChange = { waypointName = it },
                    label = { Text("Waypoint Name *") },
                    placeholder = { Text("Enter waypoint name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = waypointName.trim().isEmpty()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Waypoint description input
                OutlinedTextField(
                    value = waypointDescription,
                    onValueChange = { waypointDescription = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Enter description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (waypointName.trim().isNotEmpty() && !isSaving) {
                        isSaving = true
                        
                        // Save waypoint to database
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val waypoint = Waypoint(
                                    eventId = currentEventId,
                                    latitude = currentLatitude,
                                    longitude = currentLongitude,
                                    name = waypointName.trim(),
                                    description = if (waypointDescription.trim().isEmpty()) null else waypointDescription.trim()
                                )
                                
                                withContext(Dispatchers.IO) {
                                    database.waypointDao().insertWaypoint(waypoint)
                                }
                                
                                // Send waypoint to websocket server
                                sendWaypointToWebSocket(waypoint, context)
                                
                                Timber.d("Waypoint saved: ${waypoint.name} at ${waypoint.latitude}, ${waypoint.longitude}")
                                onWaypointSaved()
                            } catch (e: Exception) {
                                Timber.e(e, "Error saving waypoint")
                                isSaving = false
                            }
                        }
                    }
                },
                enabled = waypointName.trim().isNotEmpty() && !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save Waypoint")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
    
    // Cleanup map view when dialog is dismissed
    DisposableEffect(Unit) {
        onDispose {
            mapViewRef.value?.onDetach()
        }
    }
}

/**
 * Sends waypoint data to websocket server as separate message
 */
private fun sendWaypointToWebSocket(waypoint: Waypoint, context: Context) {
    try {
        // Get session ID and event name from SharedPreferences
        val sessionPrefs = context.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
        val sessionId = sessionPrefs.getString("current_session_id", "") ?: ""
        val eventName = sessionPrefs.getString("current_event_name", "Unknown Event") ?: "Unknown Event"
        
        if (sessionId.isEmpty()) {
            Timber.w("Cannot send waypoint - no sessionId available")
            return
        }
        
        // Create WaypointData for websocket transmission
        val waypointData = WaypointData(
            latitude = waypoint.latitude,
            longitude = waypoint.longitude,
            name = waypoint.name,
            description = waypoint.description,
            elevation = waypoint.elevation,
            timestamp = System.currentTimeMillis()
        )
        
        // Create dedicated waypoint message
        val waypointMessage = WebSocketMessage.WaypointMessage(
            sessionId = sessionId,
            eventName = eventName,
            waypoint = waypointData
        )
        
        // Send via EventBus - CustomLocationListener will handle this separately from regular metrics
        EventBus.getDefault().post(waypointMessage)
        
        Timber.d("Waypoint message sent: '${waypoint.name}' at (${waypoint.latitude}, ${waypoint.longitude}) for session $sessionId")
    } catch (e: Exception) {
        Timber.e(e, "Error sending waypoint to websocket")
    }
}