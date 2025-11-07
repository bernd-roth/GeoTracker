package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.data.PathPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker

@Composable
fun InteractivePathMap(
    pathPoints: List<PathPoint>,
    selectedPoint: PathPoint?,
    zoomToFit: Boolean = false,
    modifier: Modifier = Modifier,
    key: Any? = null
) {
    // val context = LocalContext.current // Unused for now
    // Use key to force recomposition when it changes
    key(key) {
        AndroidView(
            factory = { ctx ->
            // Initialize osmdroid configuration
            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                // Disable following user location - map should not center on current position
                controller.setZoom(15.0)

                if (pathPoints.isNotEmpty()) {
                    // Set initial center to the first point
                    val firstPoint = pathPoints.first()
                    controller.setCenter(GeoPoint(firstPoint.latitude, firstPoint.longitude))

                    // Initial path setup will be done in update
                }
            }
        },
        update = { mapView ->
            android.util.Log.d("InteractivePathMap", "Updating map with selectedPoint: ${selectedPoint?.let { "lat=${it.latitude}, lon=${it.longitude}, speed=${it.speed}" } ?: "null"}")

            // First, close any existing info windows by iterating through current overlays
            mapView.overlays.forEach { overlay ->
                if (overlay is Marker) {
                    android.util.Log.d("InteractivePathMap", "Closing existing marker info window")
                    overlay.closeInfoWindow()
                }
            }

            // Clear existing overlays
            mapView.overlays.clear()

            if (pathPoints.isNotEmpty()) {
                // Create speed-colored path segments
                createSpeedColoredPath(mapView, pathPoints)

                // Zoom to fit all points if requested
                if (zoomToFit) {
                    mapView.post {
                        val geoPoints = pathPoints.map { GeoPoint(it.latitude, it.longitude) }
                        if (geoPoints.isNotEmpty()) {
                            val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
                            mapView.zoomToBoundingBox(boundingBox, true, 50)
                            android.util.Log.d("InteractivePathMap", "Zoomed to bounding box for ${geoPoints.size} points")
                        }
                    }
                }

                // Add selected point marker if any
                selectedPoint?.let { point ->
                    android.util.Log.d("InteractivePathMap", "Creating marker for point at ${point.latitude}, ${point.longitude}")
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(point.latitude, point.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = createMarkerTitle(point)
                        snippet = createMarkerSnippet(point)

                        // Set marker click behavior to always show info window
                        setOnMarkerClickListener { marker, _ ->
                            android.util.Log.d("InteractivePathMap", "Marker clicked - showing info window")
                            marker.showInfoWindow()
                            true
                        }
                    }
                    mapView.overlays.add(marker)

                    // Use multiple attempts to ensure the info window shows
                    mapView.post {
                        android.util.Log.d("InteractivePathMap", "First attempt to show info window")
                        marker.showInfoWindow()

                        // Second attempt after a delay if first didn't work
                        mapView.postDelayed({
                            if (!marker.isInfoWindowShown) {
                                android.util.Log.d("InteractivePathMap", "Second attempt to show info window")
                                marker.showInfoWindow()
                            }

                            // Third attempt with different approach if still not shown
                            mapView.postDelayed({
                                if (!marker.isInfoWindowShown) {
                                    android.util.Log.d("InteractivePathMap", "Third attempt - force showing info window")
                                    try {
                                        // Force the marker to be clicked programmatically
                                        marker.closeInfoWindow()
                                        marker.showInfoWindow()
                                    } catch (e: Exception) {
                                        android.util.Log.e("InteractivePathMap", "Failed to show info window", e)
                                    }
                                }
                            }, 100)
                        }, 200)
                    }
                }

                // Refresh map
                mapView.invalidate()
            }
        },
        modifier = modifier.fillMaxSize()
        )
    }
}

private fun createSpeedColoredPath(mapView: MapView, pathPoints: List<PathPoint>) {
    // Group consecutive points with similar speed ranges for efficiency
    val speedSegments = groupPointsBySpeed(pathPoints)

    speedSegments.forEach { segment ->
        val polyline = Polyline().apply {
            // Set color based on speed
            color = getSpeedColor(segment.averageSpeed).toArgb()
            width = 8f

            // Add points to the polyline
            setPoints(segment.points.map { point: PathPoint ->
                GeoPoint(point.latitude, point.longitude)
            })
        }

        mapView.overlays.add(polyline)
    }
}

private fun groupPointsBySpeed(pathPoints: List<PathPoint>): List<SpeedSegment> {
    if (pathPoints.isEmpty()) return emptyList()

    val segments = mutableListOf<SpeedSegment>()
    var currentSegmentPoints = mutableListOf<PathPoint>()
    var currentSpeedRange = getSpeedRange(pathPoints.first().speed)

    pathPoints.forEach { point ->
        val pointSpeedRange = getSpeedRange(point.speed)

        if (pointSpeedRange == currentSpeedRange) {
            // Same speed range, add to current segment
            currentSegmentPoints.add(point)
        } else {
            // Different speed range, finish current segment and start new one
            if (currentSegmentPoints.isNotEmpty()) {
                segments.add(
                    SpeedSegment(
                        points = currentSegmentPoints.toList(),
                        averageSpeed = currentSegmentPoints.map { point: PathPoint -> point.speed }.average().toFloat()
                    )
                )
            }

            currentSegmentPoints = mutableListOf(point)
            currentSpeedRange = pointSpeedRange
        }
    }

    // Add the last segment
    if (currentSegmentPoints.isNotEmpty()) {
        segments.add(
            SpeedSegment(
                points = currentSegmentPoints.toList(),
                averageSpeed = currentSegmentPoints.map { point: PathPoint -> point.speed }.average().toFloat()
            )
        )
    }

    return segments
}

private fun getSpeedRange(speed: Float): SpeedRange {
    return when {
        speed < 2f -> SpeedRange.VERY_SLOW    // Red
        speed < 4f -> SpeedRange.SLOW         // Yellow
        speed < 6f -> SpeedRange.MEDIUM       // Blue
        else -> SpeedRange.FAST               // Green
    }
}

private fun getSpeedColor(speed: Float): Color {
    return when {
        speed < 2f -> Color.Red      // Below 2 km/h
        speed < 4f -> Color.Yellow   // 2-4 km/h
        speed < 6f -> Color.Blue     // 4-6 km/h
        else -> Color.Green          // Above 6 km/h
    }
}

private enum class SpeedRange {
    VERY_SLOW, SLOW, MEDIUM, FAST
}

private data class SpeedSegment(
    val points: List<PathPoint>,
    val averageSpeed: Float
)

private fun createMarkerTitle(point: PathPoint): String {
    return "📍 Location Details"
}

private fun createMarkerSnippet(point: PathPoint): String {
    val lines = mutableListOf<String>()

    // Speed and movement info
    lines.add("🏃 Speed: ${String.format("%.1f", point.speed)} km/h")
    lines.add("📏 Distance: ${String.format("%.2f", point.distance / 1000.0)} km")
    lines.add("⛰️ Altitude: ${String.format("%.1f", point.altitude)} m")

    // Duration info
    val durationHours = point.totalDuration / (1000 * 60 * 60)
    val durationMinutes = (point.totalDuration % (1000 * 60 * 60)) / (1000 * 60)
    val durationSeconds = (point.totalDuration % (1000 * 60)) / 1000

    if (durationHours > 0) {
        lines.add("⏱️ Duration: ${durationHours}h ${durationMinutes}m ${durationSeconds}s")
    } else if (durationMinutes > 0) {
        lines.add("⏱️ Duration: ${durationMinutes}m ${durationSeconds}s")
    } else {
        lines.add("⏱️ Duration: ${durationSeconds}s")
    }

    // Weather conditions (if available)
    point.temperature?.let { temp ->
        lines.add("🌡️ Temperature: ${String.format("%.1f", temp)}°C")
    }

    point.windSpeed?.let { wind ->
        lines.add("💨 Wind Speed: ${String.format("%.1f", wind)} km/h")
    }

    point.relativeHumidity?.let { humidity ->
        lines.add("💧 Humidity: ${humidity}%")
    }

    point.pressure?.let { pressure ->
        lines.add("🌫️ Pressure: ${String.format("%.1f", pressure)} hPa")
    }

    return lines.joinToString("\n")
}