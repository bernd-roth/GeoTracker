package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.domain.Metric
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

@Composable
fun SlopeColoredMap(
    metrics: List<Metric>,
    locationPoints: List<GeoPoint>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            // Initialize osmdroid configuration
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))

            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                controller.setZoom(14.0)
                isClickable = false

                if (metrics.isNotEmpty()) {
                    // Set initial center to the first point that has location data
                    val firstMetricWithLocation = metrics.firstOrNull { it.elevation > 0 }
                    firstMetricWithLocation?.let {
                        // We'll need to get lat/lon from location data - for now center on a default
                        // This will be updated when we add the slope-colored path
                    }
                }
            }
        },
        update = { mapView ->
            // Clear existing overlays
            mapView.overlays.clear()

            if (metrics.size > 1 && locationPoints.isNotEmpty()) {
                // Create slope-colored path segments
                createSlopeColoredPath(mapView, metrics, locationPoints)

                // Set map center to first location point
                mapView.controller.setCenter(locationPoints.first())

                // Refresh map
                mapView.invalidate()
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

private fun createSlopeColoredPath(mapView: MapView, metrics: List<Metric>, locationPoints: List<GeoPoint>) {
    // Calculate slope segments based on metrics and combine with location data
    val slopeSegments = calculateSlopeSegments(metrics, locationPoints)

    slopeSegments.forEach { segment ->
        if (segment.geoPoints.isNotEmpty()) {
            val polyline = Polyline().apply {
                // Set color based on slope
                color = getSlopeColor(segment.slope).toArgb()
                width = 6f

                // Set the points for this segment
                setPoints(segment.geoPoints)
            }

            mapView.overlays.add(polyline)
        }
    }
}

private fun calculateSlopeSegments(metrics: List<Metric>, locationPoints: List<GeoPoint>): List<SlopeSegment> {
    if (metrics.size < 2 || locationPoints.isEmpty()) return emptyList()

    // Sort metrics by time to ensure correct order
    val sortedMetrics = metrics.sortedBy { it.timeInMilliseconds }
    val segments = mutableListOf<SlopeSegment>()
    var currentSegmentIndices = mutableListOf<Int>()
    var currentSlopeRange = getSlopeRange(0.0) // Start with flat

    // Calculate slope between consecutive points
    for (i in 1 until sortedMetrics.size) {
        val previousMetric = sortedMetrics[i - 1]
        val currentMetric = sortedMetrics[i]

        // Calculate actual distance between points (not cumulative distance)
        val distanceDiff = if (currentMetric.distance > previousMetric.distance) {
            // If distance is cumulative, use the difference
            currentMetric.distance - previousMetric.distance
        } else {
            // If distance resets or is not cumulative, calculate from stored distance
            currentMetric.distance
        }

        // Use GPS elevation for slope calculations
        val currentElevation = currentMetric.elevation.toDouble()
        val previousElevation = previousMetric.elevation.toDouble()

        val elevationDiff = currentElevation - previousElevation

        val slope = if (distanceDiff > 5.0) {
            // Slope = (elevation change / distance change) * 100 to get percentage
            val calculatedSlope = (elevationDiff / distanceDiff) * 100.0
            // Filter extreme values but allow steeper slopes
            if (calculatedSlope >= -50.0 && calculatedSlope <= 50.0) calculatedSlope else 0.0
        } else {
            0.0 // No meaningful distance change
        }

        val pointSlopeRange = getSlopeRange(slope)

        if (pointSlopeRange == currentSlopeRange) {
            // Same slope range, add to current segment
            if (currentSegmentIndices.isEmpty()) {
                currentSegmentIndices.add(i - 1)
            }
            currentSegmentIndices.add(i)
        } else {
            // Different slope range, finish current segment and start new one
            if (currentSegmentIndices.isNotEmpty()) {
                val segmentMetrics = currentSegmentIndices.map { sortedMetrics[it] }
                val segmentGeoPoints = currentSegmentIndices.mapNotNull { index ->
                    if (index < locationPoints.size) locationPoints[index] else null
                }
                val avgSlope = calculateAverageSlope(segmentMetrics)

                if (segmentGeoPoints.isNotEmpty()) {
                    segments.add(SlopeSegment(
                        points = segmentMetrics,
                        geoPoints = segmentGeoPoints,
                        slope = avgSlope
                    ))
                }
            }

            currentSegmentIndices = mutableListOf(i - 1, i)
            currentSlopeRange = pointSlopeRange
        }
    }

    // Add the last segment
    if (currentSegmentIndices.isNotEmpty()) {
        val segmentMetrics = currentSegmentIndices.map { sortedMetrics[it] }
        val segmentGeoPoints = currentSegmentIndices.mapNotNull { index ->
            if (index < locationPoints.size) locationPoints[index] else null
        }
        val avgSlope = calculateAverageSlope(segmentMetrics)

        if (segmentGeoPoints.isNotEmpty()) {
            segments.add(SlopeSegment(
                points = segmentMetrics,
                geoPoints = segmentGeoPoints,
                slope = avgSlope
            ))
        }
    }

    return segments
}

private fun calculateAverageSlope(metrics: List<Metric>): Double {
    if (metrics.size < 2) return 0.0

    var totalDistance = 0.0
    var totalElevationChange = 0.0

    for (i in 1 until metrics.size) {
        // Calculate actual distance between points
        val distanceDiff = if (metrics[i].distance > metrics[i-1].distance) {
            metrics[i].distance - metrics[i-1].distance
        } else {
            metrics[i].distance
        }

        val elevationDiff = metrics[i].elevation - metrics[i-1].elevation

        if (distanceDiff > 5.0) {
            totalDistance += distanceDiff
            totalElevationChange += elevationDiff
        }
    }

    return if (totalDistance > 0) {
        (totalElevationChange / totalDistance) * 100.0
    } else {
        0.0
    }
}

private fun getSlopeRange(slope: Double): SlopeRange {
    return when {
        slope < -8.0 -> SlopeRange.STEEP_DECLINE     // Red
        slope < -3.0 -> SlopeRange.MODERATE_DECLINE  // Orange
        slope < -1.0 -> SlopeRange.GENTLE_DECLINE    // Yellow
        slope < 1.0 -> SlopeRange.FLAT               // Dark Green
        slope < 3.0 -> SlopeRange.GENTLE_INCLINE     // Light Blue
        slope < 8.0 -> SlopeRange.MODERATE_INCLINE   // Blue
        else -> SlopeRange.STEEP_INCLINE             // Dark Blue
    }
}

private fun getSlopeColor(slope: Double): Color {
    return when {
        slope < -8.0 -> Color.Red           // Steep decline
        slope < -3.0 -> Color(0xFFFF6600)  // Orange - Moderate decline
        slope < -1.0 -> Color.Yellow        // Gentle decline
        slope < 1.0 -> Color(0,139,0)      // Flat - consistent with app theme
        slope < 3.0 -> Color(0xFF87CEEB)   // Light blue - Gentle incline
        slope < 8.0 -> Color.Blue           // Moderate incline
        else -> Color(0xFF000080)           // Dark blue - Steep incline
    }
}

private enum class SlopeRange {
    STEEP_DECLINE, MODERATE_DECLINE, GENTLE_DECLINE, FLAT,
    GENTLE_INCLINE, MODERATE_INCLINE, STEEP_INCLINE
}

private data class SlopeSegment(
    val points: List<Metric>,
    val geoPoints: List<GeoPoint>,
    val slope: Double
)