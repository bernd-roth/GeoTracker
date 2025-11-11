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
import java.time.LocalDateTime
import java.time.Duration

/**
 * Overlay to display a popup info window when the user clicks on their current location marker.
 * Shows current speed, distance, and total activity time.
 */
class CurrentLocationInfoOverlay(private val context: Context) : Overlay() {

    private var currentPosition: GeoPoint? = null
    private var currentSpeed: Float = 0f // m/s
    private var currentDistance: Double = 0.0 // meters
    private var startDateTime: LocalDateTime? = null
    private var showPopup: Boolean = false
    private var isEnabled: Boolean = false

    companion object {
        private const val TAG = "CurrentLocationInfoOverlay"
        private const val TOUCH_RADIUS = 50f // Touch detection radius in pixels
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

    // Paint for blue highlight text (for header)
    private val blueTextPaint = Paint().apply {
        color = Color.argb(255, 33, 150, 243) // Material Blue
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    /**
     * Enable the overlay for recording mode
     */
    fun enable(startTime: LocalDateTime) {
        isEnabled = true
        startDateTime = startTime
        showPopup = false
        Log.d(TAG, "Current location info overlay enabled at $startTime")
    }

    /**
     * Disable the overlay
     */
    fun disable() {
        isEnabled = false
        showPopup = false
        currentPosition = null
        startDateTime = null
        currentSpeed = 0f
        currentDistance = 0.0
    }

    /**
     * Update current location metrics
     */
    fun updateMetrics(latitude: Double, longitude: Double, speed: Float, distance: Double) {
        currentPosition = GeoPoint(latitude, longitude)
        currentSpeed = speed
        currentDistance = distance
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !isEnabled || !showPopup) return

        val position = currentPosition ?: return

        // Draw popup above current location marker
        drawPopup(canvas, mapView, position)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!isEnabled) return false

        val currentPos = currentPosition ?: return false

        val touchPoint = Point(e.x.toInt(), e.y.toInt())

        // Convert current location to screen coordinates
        val screenPoint = Point()
        mapView.projection.toPixels(currentPos, screenPoint)

        val distance = Math.sqrt(
            Math.pow((touchPoint.x - screenPoint.x).toDouble(), 2.0) +
            Math.pow((touchPoint.y - screenPoint.y).toDouble(), 2.0)
        ).toFloat()

        if (distance <= TOUCH_RADIUS) {
            // Toggle popup
            showPopup = !showPopup
            mapView.invalidate()
            Log.d(TAG, "Current location marker clicked, popup: $showPopup")
            return true
        }

        // If tap is elsewhere and popup is showing, hide it
        if (showPopup) {
            showPopup = false
            mapView.invalidate()
            return true
        }

        return false
    }

    /**
     * Draw popup window above current location marker
     */
    private fun drawPopup(canvas: Canvas, mapView: MapView, position: GeoPoint) {
        // Convert position to screen coordinates
        val screenPoint = Point()
        mapView.projection.toPixels(position, screenPoint)

        val lineHeight = 35f
        val boxPadding = 12f
        val offsetAboveMarker = 80f // Distance above the marker

        // Calculate total activity time
        val activityTime = calculateActivityTime()

        // Prepare text lines
        val speedKmh = currentSpeed * 3.6
        val distanceKm = currentDistance / 1000.0
        val lines = mutableListOf(
            "Current Location",
            String.format("Speed: %.1f km/h", speedKmh),
            String.format("Distance: %.2f km", distanceKm),
            String.format("Time: %s", activityTime)
        )

        // Calculate box dimensions
        var maxWidth = 0f
        for (line in lines) {
            val textWidth = infoTextPaint.measureText(line)
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
            val textPaint = if (index == 0) {
                blueTextPaint // Header in blue
            } else {
                infoTextPaint // Rest in white
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

    /**
     * Calculate total activity time from start to now
     */
    private fun calculateActivityTime(): String {
        val start = startDateTime ?: return "00:00:00"
        val now = LocalDateTime.now()
        val duration = Duration.between(start, now)

        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
