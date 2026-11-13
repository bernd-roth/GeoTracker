package at.co.netconsulting.geotracker.composables

import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import at.co.netconsulting.geotracker.data.ImportedGpxTrack
import at.co.netconsulting.geotracker.data.RouteWeatherData
import at.co.netconsulting.geotracker.location.WeatherIconsOverlay
import at.co.netconsulting.geotracker.location.WeatherOverlay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Shows the recording setup without starting a recording or intercepting dialog scrolling. */
@Composable
fun RecordingPreviewMap(
    track: ImportedGpxTrack?,
    weatherData: RouteWeatherData?,
    showOpponent: Boolean,
    reverseDirection: Boolean,
    currentLocation: GeoPoint?,
    modifier: Modifier = Modifier
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapRef = remember { arrayOfNulls<MapView>(1) }
    // Bound preview work for large imports, retaining both endpoints.
    val points = remember(track?.points, reverseDirection) {
        val original = track?.points.orEmpty()
        val step = (original.size / 1500 + 1)
        val sampled = original.filterIndexed { index, _ ->
            index % step == 0 || index == original.lastIndex
        }
        if (reverseDirection) sampled.reversed() else sampled
    }
    val previewWeather = remember(weatherData, points) {
        weatherData?.copy(routePoints = points)
    }
    val opponentEnabled = showOpponent && track?.timestamps?.isNotEmpty() == true

    Card(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column {
            Text("Recording preview", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(12.dp))
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                factory = { context ->
                    Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        setBuiltInZoomControls(false)
                        isHorizontalMapRepetitionEnabled = false
                        isVerticalMapRepetitionEnabled = false
                        setOnTouchListener { _, _ -> true }
                        mapRef[0] = this
                    }
                },
                update = { map ->
                    val contentKey = listOf(points, previewWeather, opponentEnabled,
                        currentLocation.takeIf { points.isEmpty() })
                    if (map.tag != contentKey) {
                        map.tag = contentKey
                        map.overlays.clear()
                        if (points.isNotEmpty()) {
                            map.overlays.add(Polyline().apply {
                                setPoints(points)
                                outlinePaint.color = AndroidColor.rgb(33, 150, 243)
                                outlinePaint.strokeWidth = 6f
                            })
                            previewWeather?.let { weather ->
                                map.overlays.addAll(WeatherOverlay(weather).createTemperatureOverlay(map))
                                map.overlays.addAll(WeatherIconsOverlay(map.context, weather).createWeatherMarkers(map))
                            }
                            addPreviewMarker(map, points.last(), "Finish", AndroidColor.rgb(198, 40, 40))
                            addPreviewMarker(map, points.first(), "Start", AndroidColor.rgb(46, 125, 50))
                            if (opponentEnabled) {
                                addPreviewMarker(map, points.first(), "Opponent start", AndroidColor.rgb(123, 31, 162), true)
                            }
                            track?.waypoints.orEmpty().forEach { waypoint ->
                                addPreviewMarker(map, GeoPoint(waypoint.latitude, waypoint.longitude),
                                    waypoint.name, AndroidColor.rgb(255, 152, 0))
                            }
                        } else if (currentLocation != null) {
                            addPreviewMarker(map, currentLocation, "Your location", AndroidColor.rgb(33, 150, 243))
                        }
                        // Fit the route independently of the user's distance from its start.
                        val fitPoints = points.ifEmpty { listOfNotNull(currentLocation) }
                        map.post {
                            if (mapRef[0] === map && map.tag == contentKey) {
                                if (fitPoints.isEmpty()) {
                                    map.controller.setZoom(2.0)
                                    map.controller.setCenter(GeoPoint(0.0, 0.0))
                                } else {
                                    map.controller.setCenter(fitPoints.first())
                                    map.controller.setZoom(16.0)
                                    if (fitPoints.any { it.distanceToAsDouble(fitPoints.first()) > 1.0 }) {
                                        map.zoomToBoundingBox(BoundingBox.fromGeoPoints(fitPoints), false, 40, 16.0, null)
                                    }
                                }
                            }
                        }
                        map.invalidate()
                    }
                }
            )
            Text(
                text = if (points.isEmpty()) {
                    if (currentLocation == null) "Waiting for location. Select or import a track to preview it."
                    else "Your location. Select or import a track to preview it."
                } else buildString {
                    append(track?.filename.orEmpty())
                    append("\nGreen: start · Red: finish")
                    if (reverseDirection) append(" · Reversed")
                    if (opponentEnabled) append("\nPurple: opponent at start")
                    if (weatherData != null) append("\nWeather: temperature colors and forecast icons")
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapRef[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapRef[0]?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapRef[0]?.onDetach()
            mapRef[0] = null
        }
    }
}

private fun addPreviewMarker(map: MapView, point: GeoPoint, label: String, color: Int, opponent: Boolean = false) {
    val size = (if (opponent) 24 else 14) * map.resources.displayMetrics.density
    map.overlays.add(Marker(map).apply {
        position = point
        title = label
        setAnchor(Marker.ANCHOR_CENTER, if (opponent) Marker.ANCHOR_BOTTOM else Marker.ANCHOR_CENTER)
        icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2 * map.resources.displayMetrics.density.toInt(), AndroidColor.WHITE)
            setSize(size.toInt(), size.toInt())
        }
        setInfoWindow(null)
    })
}
