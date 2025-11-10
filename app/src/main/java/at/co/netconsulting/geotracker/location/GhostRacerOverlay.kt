package at.co.netconsulting.geotracker.location

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.util.Log
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

    companion object {
        private const val TAG = "GhostRacerOverlay"
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
        val newPosition = calculateGhostPosition(elapsedTime)

        if (newPosition != ghostPosition) {
            ghostPosition = newPosition
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
    }
}
