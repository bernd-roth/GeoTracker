package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.Metric

@Composable
fun AltitudeGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    if (metrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No altitude data available",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        return
    }

    // Filter out metrics with invalid elevation
    val validMetrics = metrics.filter { it.elevation > 0 }

    if (validMetrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No valid altitude data",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        return
    }

    // Get min and max elevation for scaling
    val minElevation = validMetrics.minOf { it.elevation.toDouble() }
    val maxElevation = validMetrics.maxOf { it.elevation.toDouble() }

    // Ensure we have a difference to avoid division by zero
    val elevationRange = (maxElevation - minElevation).coerceAtLeast(1.0).toFloat()

    // Canvas to draw the graph
    Canvas(
        modifier = modifier
    ) {
        val width = size.width
        val height = size.height

        // Calculate points
        val points = validMetrics.mapIndexed { index, metric ->
            val x = width * index / (validMetrics.size - 1)
            val normalizedElevation = (metric.elevation.toDouble() - minElevation) / elevationRange
            val y = height - (normalizedElevation * height).toFloat()
            Offset(x.toFloat(), y)
        }

        // Draw the elevation line
        if (points.size > 1) {
            // Draw path connecting all points
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

            drawPath(
                path = path,
                color = Color(0xFF4CAF50),
                style = Stroke(
                    width = 3f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw area under the graph
            val filledPath = Path().apply {
                moveTo(0f, height)
                lineTo(points.first().x, height)
                lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
                lineTo(points.last().x, height)
                lineTo(0f, height)
                close()
            }

            drawPath(
                path = filledPath,
                color = Color(0x334CAF50),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        }

        // Draw min and max labels
        drawContext.canvas.nativeCanvas.apply {
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }

            // Max elevation label
            drawText(
                "${maxElevation.toInt()} m",
                8f,
                12f,
                textPaint
            )

            // Min elevation label
            drawText(
                "${minElevation.toInt()} m",
                8f,
                height - 8f,
                textPaint
            )
        }
    }
}