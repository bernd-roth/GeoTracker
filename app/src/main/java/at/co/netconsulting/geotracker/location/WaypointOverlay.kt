package at.co.netconsulting.geotracker.location

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.view.MotionEvent
import at.co.netconsulting.geotracker.domain.Waypoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import timber.log.Timber
import kotlin.math.sqrt

class WaypointOverlay(
    private val context: Context
) : Overlay() {

    private var waypoints = listOf<Waypoint>()

    var onWaypointTapped: ((Waypoint) -> Unit)? = null
    
    // Paint objects for drawing waypoint markers
    private val waypointPaint = Paint().apply {
        color = Color.parseColor("#FF5722") // Orange-red for better visibility
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val waypointStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f // Slightly thicker border
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    private val textBackgroundPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 200
    }

    fun updateWaypoints(newWaypoints: List<Waypoint>) {
        waypoints = newWaypoints
        Timber.d("WaypointOverlay updated with ${waypoints.size} waypoints")
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val tapX = e.x
        val tapY = e.y
        val point = Point()
        val projection = mapView.projection
        waypoints.forEach { waypoint ->
            val geoPoint = GeoPoint(waypoint.latitude, waypoint.longitude)
            projection.toPixels(geoPoint, point)
            val dx = tapX - point.x
            val dy = tapY - point.y
            if (sqrt(dx * dx + dy * dy) <= 35f) {
                onWaypointTapped?.invoke(waypoint)
                return true
            }
        }
        return false
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        
        // Enhanced logging for debugging
        if (waypoints.isEmpty()) {
            Timber.d("WaypointOverlay draw: No waypoints to display")
            return
        } else {
            Timber.d("WaypointOverlay draw: Drawing ${waypoints.size} waypoints")
        }

        val projection = mapView.projection
        val point = Point()
        
        waypoints.forEach { waypoint ->
            val geoPoint = GeoPoint(waypoint.latitude, waypoint.longitude)
            projection.toPixels(geoPoint, point)
            
            // Check if waypoint is visible on screen
            if (point.x >= -50 && point.x <= mapView.width + 50 &&
                point.y >= -50 && point.y <= mapView.height + 50) {
                
                Timber.v("Drawing waypoint ${waypoint.name} at screen position (${point.x}, ${point.y})")
                
                // Draw waypoint marker (larger circle with prominent border)
                val radius = 20f
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius + 2f, waypointStrokePaint)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, waypointPaint)
                
                // Add a small inner circle for more visibility
                val innerPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius / 3f, innerPaint)
                
                // Draw waypoint name if not empty
                if (waypoint.name.isNotEmpty()) {
                    val textBounds = Rect()
                    textPaint.getTextBounds(waypoint.name, 0, waypoint.name.length, textBounds)
                    
                    val textX = point.x.toFloat()
                    val textY = point.y.toFloat() - radius - 15f // More space from marker
                    val padding = 6f
                    
                    // Draw text background with rounded corners effect
                    val backgroundRect = android.graphics.RectF(
                        textX - textBounds.width() / 2f - padding,
                        textY - textBounds.height() - padding,
                        textX + textBounds.width() / 2f + padding,
                        textY + padding
                    )
                    canvas.drawRoundRect(backgroundRect, 8f, 8f, textBackgroundPaint)
                    
                    // Draw text
                    canvas.drawText(waypoint.name, textX, textY, textPaint)
                    
                    Timber.v("Drew waypoint text '${waypoint.name}' at (${textX}, ${textY})")
                }
            }
        }
    }
}