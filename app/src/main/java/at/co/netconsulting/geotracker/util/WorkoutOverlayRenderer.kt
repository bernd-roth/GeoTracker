package at.co.netconsulting.geotracker.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import at.co.netconsulting.geotracker.data.EventWithDetails
import at.co.netconsulting.geotracker.tools.Tools
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WorkoutOverlayRenderer {

    fun renderOverlay(sourceBitmap: Bitmap, event: EventWithDetails): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val result = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val overlayHeight = (height * 0.22f).toInt()
        val overlayTop = height - overlayHeight

        // Draw gradient from transparent to dark at top edge of overlay
        val gradientHeight = (overlayHeight * 0.3f).toInt()
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, (overlayTop - gradientHeight).toFloat(),
                0f, overlayTop.toFloat(),
                0x00000000, 0x99000000.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(
            0f, (overlayTop - gradientHeight).toFloat(),
            width.toFloat(), overlayTop.toFloat(),
            gradientPaint
        )

        // Draw solid dark overlay bar
        val barPaint = Paint().apply {
            color = 0xCC000000.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, overlayTop.toFloat(), width.toFloat(), height.toFloat(), barPaint)

        // Scale text sizes relative to overlay height so content always fits
        val baseFontSize = overlayHeight / 5.5f
        val smallFontSize = baseFontSize * 0.65f
        val largeFontSize = baseFontSize * 1.1f
        val padding = overlayHeight / 8f

        // Paint styles
        val sportPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = largeFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = smallFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = baseFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = smallFontSize * 0.9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textAlign = Paint.Align.RIGHT
        }

        // Format stats
        val sportType = event.event.artOfSport.ifEmpty { "Workout" }
        val dateStr = formatEventDate(event.event.eventDate)
        val durationMs = event.endTime - event.startTime
        val durationStr = if (durationMs > 0) Tools().formatDuration(durationMs) else "--"
        val distanceKm = event.totalDistance / 1000.0
        val distanceStr = String.format("%.2f", distanceKm)
        //val paceStr = formatPace(event.averageSpeed)
        val elevationStr = if (event.elevationGain > 0) "+${event.elevationGain.toInt()}m" else null
        val heartRateStr = if (event.avgHeartRate > 0) "${event.avgHeartRate} bpm" else null

        // Layout: Row 1 - Sport type and date
        var y = overlayTop + padding + largeFontSize
        canvas.drawText(sportType, padding, y, sportPaint)

        // Date aligned right of sport type
        val sportWidth = sportPaint.measureText(sportType)
        canvas.drawText("  $dateStr", padding + sportWidth, y, labelPaint.apply {
            textSize = baseFontSize * 0.75f
        })

        // Row 2 - Main stats labels
        y += padding * 0.6f + smallFontSize

        val stats = mutableListOf<Pair<String, String>>()
        stats.add("DISTANCE" to "$distanceStr km")
        stats.add("DURATION" to durationStr)
        //stats.add("PACE" to paceStr)
        if (elevationStr != null) stats.add("ELEV. GAIN" to elevationStr)
        if (heartRateStr != null) stats.add("AVG HR" to heartRateStr)

        val availableWidth = width - 2 * padding
        val statWidth = availableWidth / stats.size

        // Reset label paint size
        labelPaint.textSize = smallFontSize

        for ((index, stat) in stats.withIndex()) {
            val x = padding + statWidth * index

            // Draw label
            canvas.drawText(stat.first, x, y, labelPaint)

            // Draw value below label
            canvas.drawText(stat.second, x, y + baseFontSize * 0.9f, valuePaint)
        }

        // Branding bottom-right
        canvas.drawText(
            "GeoTracker",
            width - padding,
            height - padding * 0.4f,
            brandPaint
        )

        return result
    }

    private fun formatEventDate(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            if (date != null) outputFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun formatPace(averageSpeedKmh: Double): String {
        if (averageSpeedKmh <= 0) return "--"
        val paceSeconds = (3600.0 / averageSpeedKmh).toInt()
        return "%d:%02d /km".format(paceSeconds / 60, paceSeconds % 60)
    }
}
