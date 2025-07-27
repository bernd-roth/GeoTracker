package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.domain.Metric

@Composable
fun BarometricAltitudeGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Filter metrics that have altitude data
        val gpsAltitudes = metrics.map { it.elevation }.filter { it > 0f }
        val barometerAltitudes = metrics.mapNotNull { it.altitudeFromPressure }.filter { it != 0f }

        if (gpsAltitudes.isEmpty() && barometerAltitudes.isEmpty()) {
            // Draw "No data" text
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 32f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText(
                    "No altitude data",
                    width / 2,
                    height / 2,
                    paint
                )
            }
            return@Canvas
        }

        // Determine the range for both datasets
        val allAltitudes = gpsAltitudes + barometerAltitudes
        val minAltitude = allAltitudes.minOrNull() ?: 0f
        val maxAltitude = allAltitudes.maxOrNull() ?: 100f
        val altitudeRange = maxAltitude - minAltitude

        // Add some padding to the range
        val paddedMin = minAltitude - (altitudeRange * 0.1f)
        val paddedMax = maxAltitude + (altitudeRange * 0.1f)
        val paddedRange = paddedMax - paddedMin

        // Draw GPS altitude line (blue)
        if (gpsAltitudes.isNotEmpty()) {
            val gpsPath = Path()
            gpsAltitudes.forEachIndexed { index, altitude ->
                val x = (index.toFloat() / (gpsAltitudes.size - 1).coerceAtLeast(1)) * width
                val y = height - ((altitude - paddedMin) / paddedRange) * height

                if (index == 0) {
                    gpsPath.moveTo(x, y)
                } else {
                    gpsPath.lineTo(x, y)
                }
            }

            drawPath(
                path = gpsPath,
                color = Color.Blue,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Draw barometric altitude line (green)
        if (barometerAltitudes.isNotEmpty()) {
            val barometerPath = Path()
            barometerAltitudes.forEachIndexed { index, altitude ->
                val x = (index.toFloat() / (barometerAltitudes.size - 1).coerceAtLeast(1)) * width
                val y = height - ((altitude - paddedMin) / paddedRange) * height

                if (index == 0) {
                    barometerPath.moveTo(x, y)
                } else {
                    barometerPath.lineTo(x, y)
                }
            }

            drawPath(
                path = barometerPath,
                color = Color.Green,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Draw altitude labels
        val labelCount = 3
        for (i in 0 until labelCount) {
            val altitude = paddedMin + (paddedRange * i / (labelCount - 1))
            val y = height - (i.toFloat() / (labelCount - 1)) * height

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                canvas.nativeCanvas.drawText(
                    "${altitude.toInt()}m",
                    8f,
                    y + 8f,
                    paint
                )
            }
        }

        // Draw legend
        val legendY = 20f
        if (gpsAltitudes.isNotEmpty()) {
            drawLine(
                color = Color.Blue,
                start = Offset(width - 120f, legendY),
                end = Offset(width - 90f, legendY),
                strokeWidth = 3.dp.toPx()
            )
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLUE
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                canvas.nativeCanvas.drawText(
                    "GPS",
                    width - 85f,
                    legendY + 8f,
                    paint
                )
            }
        }

        if (barometerAltitudes.isNotEmpty()) {
            val barometerLegendY = if (gpsAltitudes.isNotEmpty()) legendY + 30f else legendY
            drawLine(
                color = Color.Green,
                start = Offset(width - 120f, barometerLegendY),
                end = Offset(width - 90f, barometerLegendY),
                strokeWidth = 3.dp.toPx()
            )
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#4CAF50")
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                canvas.nativeCanvas.drawText(
                    "Barometer",
                    width - 85f,
                    barometerLegendY + 8f,
                    paint
                )
            }
        }
    }
}