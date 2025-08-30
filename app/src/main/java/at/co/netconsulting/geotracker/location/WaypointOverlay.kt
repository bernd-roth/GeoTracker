package at.co.netconsulting.geotracker.location

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import at.co.netconsulting.geotracker.domain.Waypoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import timber.log.Timber

class WaypointOverlay(
    private val context: Context
) : Overlay() {

    private var waypoints = listOf<Waypoint>()
    
    // Paint objects for drawing waypoint markers
    private val waypointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val waypointStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
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

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || waypoints.isEmpty()) return

        val projection = mapView.projection
        val point = Point()
        
        waypoints.forEach { waypoint ->
            val geoPoint = GeoPoint(waypoint.latitude, waypoint.longitude)
            projection.toPixels(geoPoint, point)
            
            // Check if waypoint is visible on screen
            if (point.x >= -50 && point.x <= mapView.width + 50 &&
                point.y >= -50 && point.y <= mapView.height + 50) {
                
                // Draw waypoint marker (circle with white border)
                val radius = 16f
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, waypointStrokePaint)
                canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius - 2f, waypointPaint)
                
                // Draw waypoint name if not empty
                if (waypoint.name.isNotEmpty()) {
                    val textBounds = Rect()
                    textPaint.getTextBounds(waypoint.name, 0, waypoint.name.length, textBounds)
                    
                    val textX = point.x.toFloat()
                    val textY = point.y.toFloat() - radius - 10f
                    val padding = 8f
                    
                    // Draw text background
                    canvas.drawRect(
                        textX - textBounds.width() / 2f - padding,
                        textY - textBounds.height() - padding,
                        textX + textBounds.width() / 2f + padding,
                        textY + padding,
                        textBackgroundPaint
                    )
                    
                    // Draw text
                    canvas.drawText(waypoint.name, textX, textY, textPaint)
                }
            }
        }
    }
}