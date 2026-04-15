package at.co.netconsulting.geotracker.composables

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.data.FollowedUserPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * A small picture-in-picture mini-map that shows a followed user's
 * live position and trail, displayed in the bottom-left corner of the main map.
 */
@Composable
fun FollowedUserMiniMap(
    trail: List<FollowedUserPoint>,
    personName: String,
    isDarkMode: Boolean,
    isSatelliteView: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPoint = trail.lastOrNull() ?: return

    val miniMapRef = remember { mutableListOf<MapView?>() .also { it.add(null) } }
    val polylineRef = remember { mutableListOf<Polyline?>().also { it.add(null) } }
    val markerRef = remember { mutableListOf<Marker?>().also { it.add(null) } }

    Surface(
        modifier = modifier
            .size(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(getAppropriateTileSource(isDarkMode, isSatelliteView))
                        setMultiTouchControls(false)
                        setBuiltInZoomControls(false)
                        // Disable all user interaction - this is a read-only preview
                        setOnTouchListener { _, _ -> true }
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)

                        controller.setZoom(16.0)
                        controller.setCenter(
                            GeoPoint(currentPoint.latitude, currentPoint.longitude)
                        )

                        miniMapRef[0] = this
                    }
                },
                update = { mapView ->
                    // Update tile source if map style changed
                    mapView.setTileSource(getAppropriateTileSource(isDarkMode, isSatelliteView))

                    // Center on latest position
                    mapView.controller.setCenter(
                        GeoPoint(currentPoint.latitude, currentPoint.longitude)
                    )

                    // Update polyline
                    polylineRef[0]?.let { mapView.overlays.remove(it) }
                    if (trail.size >= 2) {
                        val geoPoints = trail.map { GeoPoint(it.latitude, it.longitude) }
                        val polyline = Polyline().apply {
                            setPoints(geoPoints)
                            color = android.graphics.Color.parseColor("#2196F3")
                            width = 6f
                        }
                        mapView.overlays.add(polyline)
                        polylineRef[0] = polyline
                    }

                    // Update current position marker
                    markerRef[0]?.let { mapView.overlays.remove(it) }
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(currentPoint.latitude, currentPoint.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = createMiniMapMarkerIcon(context)
                        title = personName
                    }
                    mapView.overlays.add(marker)
                    markerRef[0] = marker

                    mapView.invalidate()
                },
                modifier = Modifier.matchParentSize()
            )

            // Name label overlay at the top
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = personName,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            miniMapRef[0]?.onDetach()
            miniMapRef[0] = null
        }
    }
}

/**
 * Creates a small colored dot icon for the mini-map marker.
 */
private fun createMiniMapMarkerIcon(context: Context): android.graphics.drawable.BitmapDrawable {
    val size = 24
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // White border circle
    val borderPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, borderPaint)

    // Blue inner circle
    val innerPaint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, innerPaint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

