package at.co.netconsulting.geotracker.location

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.util.Log
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Overlay to display a "ghost" marker showing where the user was at the same elapsed time
 * on a reference track (opponent track).
 */
class GhostRacerOverlay(
    private val context: Context,
    private val referencePoints: List<GeoPoint>,
    private val referenceTimestamps: List<Long>
) : Overlay() {

    private var ghostPosition: GeoPoint? = null
    private var recordingStartTime: Long = 0L
    private var isEnabled = false

    // Current user metrics
    private var currentUserPosition: GeoPoint? = null
    private var currentUserSpeed: Float = 0f // m/s
    private var currentUserDistance: Double = 0.0 // meters

    // Ghost racer metrics
    private var ghostRacerDistance: Double = 0.0 // meters
    private var ghostRacerSpeed: Float = 0f // m/s
    private var lastGhostUpdateTime: Long = 0L

    // Popup state
    private enum class PopupType { NONE, GHOST, USER }
    private var showingPopup: PopupType = PopupType.NONE

    companion object {
        private const val TAG = "GhostRacerOverlay"
        private const val TOUCH_RADIUS = 50f // Touch detection radius in pixels
    }

    // Paint for the ghost marker (semi-transparent purple dot)
    private val ghostPaint = Paint().apply {
        color = Color.argb(200, 147, 51, 234) // Purple with transparency
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint for the ghost marker border
    private val ghostBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    // Paint for info box background
    private val infoBoxBackgroundPaint = Paint().apply {
        color = Color.argb(220, 0, 0, 0) // Semi-transparent black
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint for info box text
    private val infoTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    // Paint for purple highlight text (for ghost info)
    private val purpleTextPaint = Paint().apply {
        color = Color.argb(255, 147, 51, 234) // Purple
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    /**
     * Enable ghost racer mode and set the recording start time
     */
    fun startGhostRacer(startTime: Long) {
        recordingStartTime = startTime
        isEnabled = true
        Log.d(TAG, "Ghost racer started. Points: ${referencePoints.size}, Timestamps: ${referenceTimestamps.size}")
        if (referenceTimestamps.isNotEmpty()) {
            Log.d(TAG, "First timestamp: ${referenceTimestamps.first()}, Last: ${referenceTimestamps.last()}")
        }
    }

    /**
     * Disable ghost racer mode
     */
    fun stopGhostRacer() {
        isEnabled = false
        ghostPosition = null
        currentUserPosition = null
        ghostRacerDistance = 0.0
        ghostRacerSpeed = 0f
        currentUserDistance = 0.0
        currentUserSpeed = 0f
        showingPopup = PopupType.NONE
    }

    /**
     * Update current user metrics for display
     */
    fun updateUserMetrics(latitude: Double, longitude: Double, speed: Float, distance: Double) {
        currentUserPosition = GeoPoint(latitude, longitude)
        currentUserSpeed = speed
        currentUserDistance = distance
    }

    /**
     * Update the ghost position based on current elapsed time
     */
    fun updateGhostPosition(currentTime: Long) {
        if (!isEnabled || referencePoints.isEmpty() || referenceTimestamps.isEmpty()) {
            ghostPosition = null
            return
        }

        val elapsedTime = currentTime - recordingStartTime

        // Find the ghost position based on elapsed time
        val previousGhostPosition = ghostPosition
        val newPosition = calculateGhostPosition(elapsedTime)

        if (newPosition != ghostPosition) {
            ghostPosition = newPosition

            // Calculate ghost racer distance and speed
            calculateGhostMetrics(elapsedTime, previousGhostPosition, newPosition, currentTime)

            Log.d(TAG, "Ghost position updated - Elapsed: ${elapsedTime}ms, Position: ${ghostPosition?.latitude}, ${ghostPosition?.longitude}")
        }
    }

    /**
     * Calculate ghost position at a given elapsed time using interpolation
     */
    private fun calculateGhostPosition(elapsedTime: Long): GeoPoint? {
        if (referenceTimestamps.isEmpty() || referencePoints.isEmpty()) return null

        // If elapsed time is before the reference track started, return first point
        if (elapsedTime <= referenceTimestamps.first()) {
            return referencePoints.first()
        }

        // If elapsed time is after the reference track ended, return last point
        if (elapsedTime >= referenceTimestamps.last()) {
            return referencePoints.last()
        }

        // Find the two points that bracket the elapsed time
        for (i in 0 until referenceTimestamps.size - 1) {
            val t1 = referenceTimestamps[i]
            val t2 = referenceTimestamps[i + 1]

            if (elapsedTime >= t1 && elapsedTime <= t2) {
                // Interpolate between the two points
                val p1 = referencePoints[i]
                val p2 = referencePoints[i + 1]

                val ratio = (elapsedTime - t1).toDouble() / (t2 - t1).toDouble()
                val lat = p1.latitude + (p2.latitude - p1.latitude) * ratio
                val lon = p1.longitude + (p2.longitude - p1.longitude) * ratio

                return GeoPoint(lat, lon)
            }
        }

        return null
    }

    /**
     * Calculate ghost racer metrics (distance and speed)
     */
    private fun calculateGhostMetrics(elapsedTime: Long, previousPosition: GeoPoint?, newPosition: GeoPoint?, currentTime: Long) {
        // Calculate distance covered by ghost along the reference track
        // Find which index we're at in the reference track
        if (elapsedTime <= referenceTimestamps.first()) {
            ghostRacerDistance = 0.0
            ghostRacerSpeed = 0f
        } else if (elapsedTime >= referenceTimestamps.last()) {
            // Calculate total distance of reference track
            ghostRacerDistance = calculateTotalReferenceDistance()
            ghostRacerSpeed = 0f
        } else {
            // Find the segment we're in and calculate cumulative distance
            for (i in 0 until referenceTimestamps.size - 1) {
                val t1 = referenceTimestamps[i]
                val t2 = referenceTimestamps[i + 1]

                if (elapsedTime >= t1 && elapsedTime <= t2) {
                    // Calculate distance up to this point
                    var cumulativeDistance = 0.0
                    for (j in 0 until i) {
                        cumulativeDistance += calculateDistance(referencePoints[j], referencePoints[j + 1])
                    }

                    // Add partial distance within current segment
                    val ratio = (elapsedTime - t1).toDouble() / (t2 - t1).toDouble()
                    val segmentDistance = calculateDistance(referencePoints[i], referencePoints[i + 1])
                    cumulativeDistance += segmentDistance * ratio

                    ghostRacerDistance = cumulativeDistance

                    // Calculate speed: distance moved / time elapsed
                    if (previousPosition != null && newPosition != null && lastGhostUpdateTime > 0) {
                        val distanceMoved = calculateDistance(previousPosition, newPosition)
                        val timeDiff = (currentTime - lastGhostUpdateTime) / 1000.0 // seconds
                        if (timeDiff > 0) {
                            ghostRacerSpeed = (distanceMoved / timeDiff).toFloat()
                        }
                    }

                    break
                }
            }
        }

        lastGhostUpdateTime = currentTime
    }

    /**
     * Calculate total distance of reference track
     */
    private fun calculateTotalReferenceDistance(): Double {
        var totalDistance = 0.0
        for (i in 0 until referencePoints.size - 1) {
            totalDistance += calculateDistance(referencePoints[i], referencePoints[i + 1])
        }
        return totalDistance
    }

    /**
     * Calculate distance between two GeoPoints using Haversine formula (returns meters)
     */
    private fun calculateDistance(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLat = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Calculate distance between current user position and ghost position
     */
    private fun calculateDistanceBetweenUserAndGhost(): Double? {
        val userPos = currentUserPosition ?: return null
        val ghostPos = ghostPosition ?: return null
        return calculateDistance(userPos, ghostPos)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled) return

        val ghostPos = ghostPosition ?: return

        // Convert GeoPoint to screen coordinates
        val screenPoint = Point()
        mapView.projection.toPixels(ghostPos, screenPoint)

        // Draw the ghost marker (white border + purple fill)
        val radius = 24f // Increased size for better visibility
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius, ghostBorderPaint)
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius - 3f, ghostPaint)

        // Draw a smaller inner circle for better visibility
        val innerPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), 8f, innerPaint)

        // Draw popup if one is active
        when (showingPopup) {
            PopupType.GHOST -> drawPopupAboveMarker(canvas, mapView, ghostPos, isGhost = true)
            PopupType.USER -> {
                currentUserPosition?.let { userPos ->
                    drawPopupAboveMarker(canvas, mapView, userPos, isGhost = false)
                }
            }
            PopupType.NONE -> {} // No popup to draw
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!isEnabled) return false

        val touchPoint = Point(e.x.toInt(), e.y.toInt())

        // Check if tap is on ghost marker
        ghostPosition?.let { ghostPos ->
            val ghostScreenPoint = Point()
            mapView.projection.toPixels(ghostPos, ghostScreenPoint)

            val distance = Math.sqrt(
                Math.pow((touchPoint.x - ghostScreenPoint.x).toDouble(), 2.0) +
                Math.pow((touchPoint.y - ghostScreenPoint.y).toDouble(), 2.0)
            ).toFloat()

            if (distance <= TOUCH_RADIUS) {
                // Toggle ghost popup
                showingPopup = if (showingPopup == PopupType.GHOST) {
                    PopupType.NONE
                } else {
                    PopupType.GHOST
                }
                mapView.invalidate()
                return true
            }
        }

        // Check if tap is on user marker
        currentUserPosition?.let { userPos ->
            val userScreenPoint = Point()
            mapView.projection.toPixels(userPos, userScreenPoint)

            val distance = Math.sqrt(
                Math.pow((touchPoint.x - userScreenPoint.x).toDouble(), 2.0) +
                Math.pow((touchPoint.y - userScreenPoint.y).toDouble(), 2.0)
            ).toFloat()

            if (distance <= TOUCH_RADIUS) {
                // Toggle user popup
                showingPopup = if (showingPopup == PopupType.USER) {
                    PopupType.NONE
                } else {
                    PopupType.USER
                }
                mapView.invalidate()
                return true
            }
        }

        // If tap is elsewhere and popup is showing, hide it
        if (showingPopup != PopupType.NONE) {
            showingPopup = PopupType.NONE
            mapView.invalidate()
            return true
        }

        return false
    }

    /**
     * Draw popup window above a marker
     */
    private fun drawPopupAboveMarker(canvas: Canvas, mapView: MapView, position: GeoPoint, isGhost: Boolean) {
        // Convert position to screen coordinates
        val screenPoint = Point()
        mapView.projection.toPixels(position, screenPoint)

        val lineHeight = 35f
        val boxPadding = 12f
        val offsetAboveMarker = 80f // Distance above the marker

        // Calculate distance between user and ghost
        val distanceBetween = calculateDistanceBetweenUserAndGhost()

        // Prepare text lines based on marker type
        val lines = if (isGhost) {
            val speedKmh = ghostRacerSpeed * 3.6
            val distanceKm = ghostRacerDistance / 1000.0
            mutableListOf(
                "Ghost Racer",
                String.format("Speed: %.1f km/h", speedKmh),
                String.format("Distance: %.2f km", distanceKm)
            )
        } else {
            val speedKmh = currentUserSpeed * 3.6
            val distanceKm = currentUserDistance / 1000.0
            mutableListOf(
                "You",
                String.format("Speed: %.1f km/h", speedKmh),
                String.format("Distance: %.2f km", distanceKm)
            )
        }

        // Add gap information if available
        if (distanceBetween != null) {
            lines.add(String.format("Gap: %.0f m", distanceBetween))
        }

        // Calculate box dimensions
        var maxWidth = 0f
        for (line in lines) {
            val textPaint = if (isGhost) purpleTextPaint else infoTextPaint
            val textWidth = textPaint.measureText(line)
            if (textWidth > maxWidth) maxWidth = textWidth
        }

        val boxWidth = maxWidth + boxPadding * 2
        val boxHeight = lines.size * lineHeight + boxPadding * 2

        // Position box above the marker, centered horizontally
        val left = screenPoint.x - boxWidth / 2
        val top = screenPoint.y - offsetAboveMarker - boxHeight

        // Draw background
        val rect = android.graphics.RectF(left, top, left + boxWidth, top + boxHeight)
        canvas.drawRoundRect(rect, 8f, 8f, infoBoxBackgroundPaint)

        // Draw text lines
        var yPos = top + boxPadding + lineHeight - 8f
        for ((index, line) in lines.withIndex()) {
            val textPaint = if (isGhost && index == 0) {
                purpleTextPaint
            } else {
                infoTextPaint
            }
            canvas.drawText(line, left + boxPadding, yPos, textPaint)
            yPos += lineHeight
        }

        // Draw a small pointer/triangle from popup to marker
        drawPointerToMarker(canvas, left + boxWidth / 2, top + boxHeight, screenPoint.y.toFloat())
    }

    /**
     * Draw a small pointer from popup to marker
     */
    private fun drawPointerToMarker(canvas: Canvas, centerX: Float, popupBottom: Float, markerTop: Float) {
        val pointerPath = android.graphics.Path()
        val pointerWidth = 12f

        pointerPath.moveTo(centerX, popupBottom)
        pointerPath.lineTo(centerX - pointerWidth, popupBottom)
        pointerPath.lineTo(centerX, markerTop)
        pointerPath.lineTo(centerX + pointerWidth, popupBottom)
        pointerPath.close()

        canvas.drawPath(pointerPath, infoBoxBackgroundPaint)
    }

}
