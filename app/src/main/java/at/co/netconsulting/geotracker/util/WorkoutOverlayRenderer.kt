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

        // Format stats
        val sportType = event.event.artOfSport.ifEmpty { "Workout" }
        val dateStr = formatEventDate(event.event.eventDate)
        val durationMs = event.endTime - event.startTime
        val durationStr = if (durationMs > 0) Tools().formatDuration(durationMs) else "--"
        val distanceKm = event.totalDistance / 1000.0
        val distanceStr = String.format("%.2f km", distanceKm)
        val elevationStr = if (event.elevationGain > 0) "+${event.elevationGain.toInt()}m" else null
        val heartRateStr = if (event.avgHeartRate > 0) "${event.avgHeartRate} bpm" else null

        // Build lines: each stat on its own line
        val lines = mutableListOf<Pair<String, String>>() // label, value
        lines.add("Distance:" to distanceStr)
        lines.add("Duration:" to durationStr)
        if (elevationStr != null) lines.add("Elev. Gain:" to elevationStr)
        if (heartRateStr != null) lines.add("Avg HR:" to heartRateStr)

        // Calculate overlay height based on number of lines
        // Header (sport + date) + stat lines + branding + padding
        val totalLines = 1 + lines.size + 1 // header + stats + branding
        val overlayHeight = (height * (0.06f * totalLines + 0.04f)).toInt()
            .coerceAtMost((height * 0.45f).toInt())
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

        // Scale text sizes relative to overlay height
        val lineHeight = overlayHeight.toFloat() / (totalLines + 1.5f)
        val largeFontSize = lineHeight * 0.85f
        val baseFontSize = lineHeight * 0.75f
        val smallFontSize = baseFontSize * 0.7f
        val padding = overlayHeight / 10f

        // Paint styles
        val sportPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = largeFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = baseFontSize * 0.75f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = baseFontSize
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

        // Layout: all left-aligned, one item per line
        var y = overlayTop + padding + largeFontSize

        // Line 1: Sport type
        canvas.drawText(sportType, padding, y, sportPaint)

        // Line 2: Date below sport type
        y += lineHeight
        canvas.drawText(dateStr, padding, y, datePaint)

        // Lines 3+: Each stat on its own line as "Label: Value"
        for (line in lines) {
            y += lineHeight
            val labelText = line.first + " "
            canvas.drawText(labelText, padding, y, labelPaint)
            val labelWidth = labelPaint.measureText(labelText)
            canvas.drawText(line.second, padding + labelWidth, y, valuePaint)
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
