package at.co.netconsulting.geotracker.location

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Draws a stylized "catcher car" icon on the map at the position computed
 * by the Wings for Life Run feature. Position is updated externally via
 * [setPosition] — this overlay is a pure renderer and holds no scheduling
 * logic of its own.
 */
class WingsForLifeCatcherOverlay : Overlay() {

    @Volatile private var position: GeoPoint? = null
    @Volatile private var caught: Boolean = false

    private val bodyPaint = Paint().apply {
        color = Color.rgb(220, 0, 30) // catcher car red
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val cabinPaint = Paint().apply {
        color = Color.argb(220, 30, 30, 30)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val labelBgPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val labelTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    fun setPosition(point: GeoPoint?, wasCaught: Boolean) {
        position = point
        caught = wasCaught
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pos = position ?: return

        val screenPoint = Point()
        mapView.projection.toPixels(pos, screenPoint)
        val cx = screenPoint.x.toFloat()
        val cy = screenPoint.y.toFloat()

        // Body: rounded rectangle ~ 56 x 28 px
        val halfW = 28f
        val halfH = 14f
        val body = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        if (caught) {
            // Pulse to a darker tone when the catcher has overtaken the runner.
            bodyPaint.color = Color.rgb(90, 0, 10)
        } else {
            bodyPaint.color = Color.rgb(220, 0, 30)
        }
        canvas.drawRoundRect(body, 8f, 8f, bodyPaint)
        canvas.drawRoundRect(body, 8f, 8f, borderPaint)

        // Cabin: smaller rect on top
        val cabin = RectF(cx - 14f, cy - 18f, cx + 14f, cy - 4f)
        canvas.drawRoundRect(cabin, 4f, 4f, cabinPaint)
        canvas.drawRoundRect(cabin, 4f, 4f, borderPaint)

        // Wheels: two small circles below
        canvas.drawCircle(cx - 18f, cy + halfH, 5f, cabinPaint)
        canvas.drawCircle(cx + 18f, cy + halfH, 5f, cabinPaint)

        // Label above the icon
        val label = if (caught) "CAUGHT" else "CATCHER"
        val labelW = labelTextPaint.measureText(label)
        val labelBg = RectF(
            cx - labelW / 2 - 8f,
            cy - 50f,
            cx + labelW / 2 + 8f,
            cy - 26f
        )
        canvas.drawRoundRect(labelBg, 6f, 6f, labelBgPaint)
        canvas.drawText(label, cx, cy - 32f, labelTextPaint)
    }
}
