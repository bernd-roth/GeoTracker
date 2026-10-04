package at.co.netconsulting.geotracker.location

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Opposing flags keep both endpoints visible even when a route ends where it began. */
class RecordingFlags(private val mapView: MapView) {
    private var start: Marker? = null
    private var finish: Marker? = null

    fun update(first: GeoPoint?, last: GeoPoint?, recording: Boolean) {
        start = updateMarker(start, first, false)
        finish = updateMarker(finish, last.takeUnless { recording }, true)
        mapView.invalidate()
    }

    fun clear() {
        start?.let { mapView.overlays.remove(it) }
        finish?.let { mapView.overlays.remove(it) }
        start = null
        finish = null
        mapView.invalidate()
    }

    private fun updateMarker(existing: Marker?, point: GeoPoint?, checkered: Boolean): Marker? {
        if (point == null) {
            existing?.let { mapView.overlays.remove(it) }
            return null
        }
        val marker = existing ?: Marker(mapView).apply {
            title = if (checkered) "Recording finish" else "Recording start"
            icon = flagIcon(checkered)
            setAnchor(if (checkered) 0.125f else 0.875f, 1f)
        }
        marker.position = point
        mapView.overlays.remove(marker)
        mapView.overlays.add(marker)
        return marker
    }

    private fun flagIcon(checkered: Boolean): BitmapDrawable {
        val density = mapView.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap((40 * density).toInt(), (48 * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        if (!checkered) {
            canvas.translate(40f, 0f)
            canvas.scale(-1f, 1f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.strokeWidth = 5f
        canvas.drawLine(5f, 3f, 5f, 48f, paint)
        canvas.drawRect(5f, 3f, 37f, 27f, paint)
        paint.color = if (checkered) Color.WHITE else Color.rgb(0, 145, 65)
        canvas.drawRect(6f, 4f, 36f, 26f, paint)
        if (checkered) {
            paint.color = Color.BLACK
            for (row in 0 until 4) for (column in 0 until 5) {
                if ((row + column) % 2 == 0) {
                    canvas.drawRect(6f + column * 6f, 4f + row * 5.5f,
                        12f + column * 6f, 9.5f + row * 5.5f, paint)
                }
            }
        }
        paint.color = Color.DKGRAY
        paint.strokeWidth = 2f
        canvas.drawLine(5f, 3f, 5f, 48f, paint)
        return BitmapDrawable(mapView.resources, bitmap)
    }
}
