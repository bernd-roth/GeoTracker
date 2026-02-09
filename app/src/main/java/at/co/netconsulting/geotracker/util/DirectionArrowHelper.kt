package at.co.netconsulting.geotracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

object DirectionArrowHelper {

    private const val ARROW_INTERVAL_METERS = 1000.0
    private const val ARROW_SIZE_DP = 20f

    fun addDirectionArrows(mapView: MapView, points: List<GeoPoint>) {
        if (points.size < 2) return

        val context = mapView.context
        val arrowBitmap = createArrowBitmap(context)
        var accumulatedDistance = 0.0

        for (i in 0 until points.size - 1) {
            val from = points[i]
            val to = points[i + 1]
            val segmentDistance = from.distanceToAsDouble(to)
            accumulatedDistance += segmentDistance

            if (accumulatedDistance >= ARROW_INTERVAL_METERS) {
                accumulatedDistance = 0.0

                val bearing = from.bearingTo(to).toFloat()
                val midLat = (from.latitude + to.latitude) / 2
                val midLon = (from.longitude + to.longitude) / 2
                val midPoint = GeoPoint(midLat, midLon)

                val marker = Marker(mapView).apply {
                    position = midPoint
                    icon = android.graphics.drawable.BitmapDrawable(context.resources, arrowBitmap)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    rotation = -bearing
                    setInfoWindow(null)
                    isFlat = true
                }
                mapView.overlays.add(marker)
            }
        }
    }

    private fun createArrowBitmap(context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (ARROW_SIZE_DP * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = size / 2f
        val cy = size / 2f
        val halfSize = size / 2f

        // Chevron path pointing up (north)
        val path = Path().apply {
            moveTo(cx, cy - halfSize * 0.7f)           // top center (tip)
            lineTo(cx + halfSize * 0.5f, cy + halfSize * 0.4f) // bottom right
            lineTo(cx, cy + halfSize * 0.1f)            // bottom center notch
            lineTo(cx - halfSize * 0.5f, cy + halfSize * 0.4f) // bottom left
            close()
        }

        // White fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)

        // Dark stroke
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 50, 50, 50)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawPath(path, strokePaint)

        return bitmap
    }
}
