package at.co.netconsulting.geotracker.composables

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

@Composable
internal fun CadencePointMap(
    database: FitnessTrackerDatabase,
    eventId: Int,
    metrics: List<Metric>,
    selectedMetricIndex: Int?,
    cadenceDisplay: CadenceDisplay,
    modifier: Modifier = Modifier
) {
    var locations by remember(eventId) { mutableStateOf<List<Location>?>(null) }
    val mapRef = remember { mutableStateOf<MapView?>(null) }
    var fittedRoute by remember(eventId) { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        locations = withContext(Dispatchers.IO) {
            database.locationDao().getLocationsForEvent(eventId)
        }
    }

    val loadedLocations = locations
    Box(modifier = modifier.fillMaxWidth().height(280.dp)) {
        when {
            loadedLocations == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            loadedLocations.isEmpty() -> Text(
                "No route data available",
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Center)
            )
            else -> {
                val selectedLocation = selectedMetricIndex?.let {
                    locationForMetric(it, metrics.size, loadedLocations)
                }
                val selectedMetric = selectedMetricIndex?.let(metrics::getOrNull)
                val route = remember(loadedLocations) {
                    val step = (loadedLocations.size / 3000).coerceAtLeast(1)
                    loadedLocations.filterIndexed { index, _ -> index % step == 0 }
                        .map { GeoPoint(it.latitude, it.longitude, it.altitude) }
                }

                AndroidView(
                    factory = { context ->
                        Configuration.getInstance().load(
                            context,
                            context.getSharedPreferences("osmdroid", 0)
                        )
                        MapView(context).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            setBuiltInZoomControls(false)
                            isHorizontalMapRepetitionEnabled = false
                            isVerticalMapRepetitionEnabled = false
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            mapRef.value = this
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        mapView.overlays.add(
                            Polyline().apply {
                                setPoints(route)
                                color = android.graphics.Color.parseColor("#5C6BC0")
                                width = 7f
                            }
                        )

                        if (!fittedRoute && route.isNotEmpty()) {
                            mapView.post {
                                mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(route), false, 45)
                                fittedRoute = true
                            }
                        }

                        if (selectedLocation != null && selectedMetric != null) {
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(
                                    selectedLocation.latitude,
                                    selectedLocation.longitude,
                                    selectedLocation.altitude
                                )
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Cadence ${cadenceDisplay.value(selectedMetric.cadence ?: 0)} ${cadenceDisplay.unit}"
                                snippet = "${selectedMetric.speed.formatOneDecimal()} km/h, " +
                                    "${selectedMetric.elevation.roundToInt()} m"
                            }
                            mapView.overlays.add(marker)
                            mapView.post {
                                mapView.controller.animateTo(marker.position)
                                marker.showInfoWindow()
                            }
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
            mapRef.value?.onDetach()
            mapRef.value = null
        }
    }
}

private fun locationForMetric(
    metricIndex: Int,
    metricCount: Int,
    locations: List<Location>
): Location? {
    val locationIndex = cadenceLocationIndex(metricIndex, metricCount, locations.size) ?: return null
    return locations.getOrNull(locationIndex)
}

internal fun cadenceLocationIndex(
    metricIndex: Int,
    metricCount: Int,
    locationCount: Int
): Int? {
    if (metricCount <= 0 || locationCount <= 0 || metricIndex !in 0 until metricCount) return null
    return when {
        locationCount == metricCount -> metricIndex
        locationCount == metricCount + 1 -> metricIndex + 1
        metricCount == 1 -> 0
        else -> (metricIndex.toDouble() * ((locationCount - 1).toDouble() / (metricCount - 1)))
            .roundToInt()
            .coerceIn(0, locationCount - 1)
    }
}

private fun Float.formatOneDecimal(): String = String.format("%.1f", this)
