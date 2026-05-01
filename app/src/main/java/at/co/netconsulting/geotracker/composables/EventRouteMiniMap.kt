package at.co.netconsulting.geotracker.composables

import android.view.View
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val MAX_PREVIEW_POINTS = 1500

/**
 * Read-only OSM mini-map showing the recorded path of an event.
 * Lazy-loads location points from the database and auto-fits the polyline bounds.
 */
@Composable
fun EventRouteMiniMap(
    database: FitnessTrackerDatabase,
    eventId: Int,
    modifier: Modifier = Modifier
) {
    var points by remember(eventId) { mutableStateOf<List<GeoPoint>?>(null) }
    val mapViewRef = remember { mutableListOf<MapView?>().also { it.add(null) } }

    LaunchedEffect(eventId) {
        val loaded = withContext(Dispatchers.IO) {
            val locations = database.locationDao().getLocationsForEvent(eventId)
            val step = if (locations.size > MAX_PREVIEW_POINTS) {
                locations.size / MAX_PREVIEW_POINTS + 1
            } else 1
            locations.filterIndexed { i, _ -> i % step == 0 }
                .map { GeoPoint(it.latitude, it.longitude) }
        }
        points = loaded
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            val loadedPoints = points
            if (loadedPoints == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (loadedPoints.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No route data available",
                        color = Color.Gray
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        Configuration.getInstance().load(
                            ctx,
                            ctx.getSharedPreferences("osmdroid", 0)
                        )
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(false)
                            setBuiltInZoomControls(false)
                            isHorizontalMapRepetitionEnabled = false
                            isVerticalMapRepetitionEnabled = false
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            // Read-only preview: swallow all touch events
                            setOnTouchListener { _, _ -> true }
                            mapViewRef[0] = this
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()

                        val polyline = Polyline().apply {
                            setPoints(loadedPoints)
                            color = android.graphics.Color.parseColor("#2196F3")
                            width = 6f
                        }
                        mapView.overlays.add(polyline)

                        addEndpointMarker(
                            mapView,
                            loadedPoints.first(),
                            android.graphics.Color.parseColor("#2E7D32") // green start
                        )
                        if (loadedPoints.size > 1) {
                            addEndpointMarker(
                                mapView,
                                loadedPoints.last(),
                                android.graphics.Color.parseColor("#C62828") // red end
                            )
                        }

                        mapView.post {
                            val box = BoundingBox.fromGeoPoints(loadedPoints)
                            mapView.zoomToBoundingBox(box, false, 40)
                        }
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef[0]?.onDetach()
            mapViewRef[0] = null
        }
    }
}

private fun addEndpointMarker(mapView: MapView, point: GeoPoint, color: Int) {
    val size = 24
    val bitmap = android.graphics.Bitmap.createBitmap(
        size, size, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val borderPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, borderPaint)
    val innerPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3f, innerPaint)

    val marker = Marker(mapView).apply {
        position = point
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = android.graphics.drawable.BitmapDrawable(mapView.resources, bitmap)
        setInfoWindow(null)
    }
    mapView.overlays.add(marker)
}
