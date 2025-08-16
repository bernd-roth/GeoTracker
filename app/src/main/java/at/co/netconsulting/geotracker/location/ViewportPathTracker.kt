package at.co.netconsulting.geotracker.location

import android.graphics.Color
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.repository.PathBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.max
import kotlin.math.min

/**
 * ViewportPathTracker handles displaying track paths with database-backed storage
 * and viewport-based loading for memory efficiency
 */
class ViewportPathTracker(private val database: FitnessTrackerDatabase) {
    // Main path polyline
    private var pathPolyline: Polyline? = null

    // Current event ID being tracked
    private var currentEventId: Int = -1

    // Current viewport and zoom level
    private var currentViewport: BoundingBox? = null
    private var currentZoomLevel: Double = 0.0

    // Cache of current points in memory
    private var pointCache = mutableListOf<GeoPoint>()

    // Debounce job for viewport updates
    private var viewportUpdateJob: Job? = null

    // Coroutine scope for async operations
    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track recording status
    val isRecording = mutableStateOf(false)

    // Track loading status - using Boolean instead of Long
    val isLoading = mutableStateOf(false)

    // ViewPort buffer size
    private val viewportBufferPercent = 0.2 // 20%

    // Sample rates by zoom level (zoom level -> sample rate)
    private val zoomSampleRates = mapOf(
        0.0 to 100,   // World view - extremely simplified (1 per 100 points)
        5.0 to 50,    // Continent view - very simplified
        10.0 to 20,   // Country view - simplified
        12.0 to 10,   // Region view - moderately simplified
        14.0 to 5,    // City view - lightly simplified
        16.0 to 2,    // Neighborhood view - slightly simplified
        18.0 to 1,    // Street view - full detail
        22.0 to 1     // Building view - full detail
//        0.0 to 50,
//        5.0 to 25,
//        10.0 to 10,
//        12.0 to 5,
//        14.0 to 2,
//        16.0 to 1,
//        18.0 to 1,
//        22.0 to 1
    )

    // Colors for different path segments
    private val pathColor = Color.RED
    private val pathWidth = 7.0f

    // Flag to control if zooming to path bounds is enabled
    private var zoomToPathEnabled = false

    /**
     * Initialize the path tracker with a MapView
     */
    fun initialize(mapView: MapView) {
        if (pathPolyline == null) {
            pathPolyline = Polyline().apply {
                outlinePaint.color = pathColor
                outlinePaint.strokeWidth = pathWidth
                isGeodesic = true  // Follow earth curvature for long distances
            }
            mapView.overlays.add(pathPolyline)
        }
    }

    /**
     * Set the current event ID to display
     */
    fun setCurrentEventId(eventId: Int, mapView: MapView?) {
        // Only update if needed
        if (currentEventId != eventId) {
            currentEventId = eventId
            pointCache.clear()

            // If we have a mapView, update the display
            mapView?.let {
                updatePathForViewport(it, forceUpdate = true)
            }

            // If this is a valid event, and zooming is enabled, try to zoom to the path bounds
//            if (eventId > 0 && mapView != null && zoomToPathEnabled) {
//                zoomToPathBounds(eventId, mapView)
//            }
        }
    }

    /**
     * Set the recording state
     */
    fun setRecording(recording: Boolean) {
        isRecording.value = recording
        // Always set zoomToPathEnabled to false regardless of recording state
        // This prevents any auto-zooming behavior
        zoomToPathEnabled = false
        Log.d(TAG, "Recording state changed to $recording, auto-zoom disabled")
    }

    /**
     * Clear the path display
     */
    fun clearPath(mapView: MapView) {
        pointCache.clear()
        pathPolyline?.setPoints(emptyList())
        mapView.invalidate()
        Log.d(TAG, "Path cleared")
    }

    /**
     * Zoom the map to show the full path
     */
    private fun zoomToPathBounds(eventId: Int, mapView: MapView) {
        trackerScope.launch {
            try {
                val bounds = database.locationDao().getPathBounds(eventId)

                bounds?.let {
                    // Add padding to the bounds
                    val expandedBounds = it.expand(0.1)

                    withContext(Dispatchers.Main) {
                        val boundingBox = BoundingBox(
                            expandedBounds.maxLat,
                            expandedBounds.maxLon,
                            expandedBounds.minLat,
                            expandedBounds.minLon
                        )

                        // Only zoom if we're allowed to
                        if (zoomToPathEnabled) {
                            mapView.zoomToBoundingBox(boundingBox, true, 100)
                            Log.d(TAG, "Zoomed to path bounds: $boundingBox")
                        } else {
                            Log.d(TAG, "Skipping auto-zoom as it is disabled during recording")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error zooming to path bounds", e)
            }
        }
    }

    /**
     * Update the path display when map viewport changes
     */
    fun updatePathForViewport(mapView: MapView, forceUpdate: Boolean = false) {
        if (currentEventId <= 0) return

        val viewport = mapView.boundingBox
        val zoomLevel = mapView.zoomLevelDouble

        // Always force updates during recording
        val shouldForceUpdate = forceUpdate || isRecording.value

        // If not forcing update and viewport hasn't changed much, skip (but NOT during recording)
        if (!shouldForceUpdate &&
            isViewportSimilar(viewport, currentViewport) &&
            isSameZoomLevel(zoomLevel, currentZoomLevel)) {
            return
        }

        // Debounce viewport updates, but use much shorter delay during recording
        viewportUpdateJob?.cancel()
        viewportUpdateJob = trackerScope.launch {
            // REDUCED debounce delay during recording: 50ms vs 100ms
            val debounceDelay = if (isRecording.value) 50L else 100L
            delay(debounceDelay)

            // Store current viewport and zoom
            currentViewport = viewport
            currentZoomLevel = zoomLevel

            isLoading.value = true

            try {
                // Use sample rate 1 during recording for immediate display
                val sampleRate = if (isRecording.value) {
                    1 // Show every point during recording
                } else {
                    getSampleRateForZoom(zoomLevel)
                }

                // Convert OSMDroid BoundingBox to our PathBounds with buffer
                val expandedBounds = viewport.toPathBounds().expand(viewportBufferPercent)

                // Query database for points in this viewport with appropriate sampling
                val locations = database.locationDao().getLocationsInBoundingBox(
                    eventId = currentEventId,
                    latNorth = expandedBounds.maxLat,
                    latSouth = expandedBounds.minLat,
                    lonEast = expandedBounds.maxLon,
                    lonWest = expandedBounds.minLon,
                    sampleRate = sampleRate
                )

                // Convert to GeoPoints
                val newPoints = locations.map {
                    GeoPoint(it.latitude, it.longitude)
                }

                // Update cache and display
                pointCache = newPoints.toMutableList()

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    pathPolyline?.setPoints(pointCache)
                    mapView.invalidate()

                    Log.d(TAG, "Updated path with ${pointCache.size} points at zoom $zoomLevel (sample rate: $sampleRate, recording: ${isRecording.value})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating path for viewport", e)
            } finally {
                isLoading.value = false
            }
        }
    }

    /**
     * Add a path segment overlay between two points
     */
    fun addPointsFromDb(eventId: Int, mapView: MapView) {
        trackerScope.launch {
            try {
                // Fetch the max sample rate (1) for highest detail
                val locations = database.locationDao().getLocationsForEvent(eventId)

                val points = locations.map {
                    GeoPoint(it.latitude, it.longitude)
                }

                withContext(Dispatchers.Main) {
                    pointCache = points.toMutableList()
                    pathPolyline?.setPoints(points)
                    mapView.invalidate()

                    Log.d(TAG, "Loaded ${points.size} points from database for event $eventId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading points from database", e)
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        trackerScope.cancel()
        viewportUpdateJob?.cancel()
        pointCache.clear()
    }

    /**
     * Check if two viewports are similar enough to avoid reloading
     */
    private fun isViewportSimilar(viewport1: BoundingBox?, viewport2: BoundingBox?): Boolean {
        if (viewport1 == null || viewport2 == null) return false

        // Check if one viewport is mostly contained within the other
        val latOverlap = min(viewport1.latNorth, viewport2.latNorth) - max(viewport1.latSouth, viewport2.latSouth)
        val lonOverlap = min(viewport1.lonEast, viewport2.lonEast) - max(viewport1.lonWest, viewport2.lonWest)

        if (latOverlap <= 0 || lonOverlap <= 0) return false

        // Calculate areas
        val area1 = (viewport1.latNorth - viewport1.latSouth) * (viewport1.lonEast - viewport1.lonWest)
        val area2 = (viewport2.latNorth - viewport2.latSouth) * (viewport2.lonEast - viewport2.lonWest)
        val overlapArea = latOverlap * lonOverlap

        // If overlap is more than 70% of the smaller viewport, consider them similar
        val smallerArea = min(area1, area2)
        return overlapArea / smallerArea > 0.7
    }

    /**
     * Check if two zoom levels are in the same detail band
     */
    private fun isSameZoomLevel(zoom1: Double, zoom2: Double): Boolean {
        return getSampleRateForZoom(zoom1) == getSampleRateForZoom(zoom2)
    }

    /**
     * Get the appropriate sample rate for a zoom level
     */
    private fun getSampleRateForZoom(zoomLevel: Double): Int {
        // Find the closest defined zoom level that's less than or equal to our current zoom
        val closestZoom = zoomSampleRates.keys
            .filter { it <= zoomLevel }
            .maxByOrNull { it } ?: zoomSampleRates.keys.minOrNull() ?: 0.0

        return zoomSampleRates[closestZoom] ?: 1
    }

    /**
     * Convert OSMDroid BoundingBox to our PathBounds
     */
    private fun BoundingBox.toPathBounds(): PathBounds {
        return PathBounds(
            minLat = this.latSouth,
            maxLat = this.latNorth,
            minLon = this.lonWest,
            maxLon = this.lonEast
        )
    }

    fun refreshPath(mapView: MapView) {
        // Clear the current viewport cache to force reload
        currentViewport = null
        currentZoomLevel = 0.0

        // Force update with current viewport
        updatePathForViewport(mapView, forceUpdate = true)
    }

    companion object {
        private const val TAG = "ViewportPathTracker"
    }
}