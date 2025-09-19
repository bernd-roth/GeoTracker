package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.MonthlyStats
import kotlin.math.max

@Composable
fun MonthlyTrendGraph(
    monthlyStats: List<MonthlyStats>,
    modifier: Modifier = Modifier
) {
    val statsWithData = monthlyStats.filter { it.totalDistance > 0 }

    if (statsWithData.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No monthly data available",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        return
    }

    // Get the primary color outside of Canvas
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 40.dp.toPx()
        val graphWidth = width - (2 * padding)
        val graphHeight = height - (2 * padding)

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // Find the maximum distance for scaling
        val maxDistance = monthlyStats.maxOfOrNull { it.totalDistance } ?: 1.0
        val minDistance = 0.0

        // Draw axes
        val axisColor = Color.Gray
        val axisStrokeWidth = 2.dp.toPx()

        // Y-axis (left)
        drawLine(
            color = axisColor,
            start = Offset(padding, padding),
            end = Offset(padding, height - padding),
            strokeWidth = axisStrokeWidth
        )

        // X-axis (bottom)
        drawLine(
            color = axisColor,
            start = Offset(padding, height - padding),
            end = Offset(width - padding, height - padding),
            strokeWidth = axisStrokeWidth
        )

        // Draw grid lines and labels
        val monthNames = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val stepX = graphWidth / 11f // 12 months, 11 intervals

        for (i in 0..11) {
            val x = padding + (i * stepX)

            // Vertical grid lines
            drawLine(
                color = axisColor.copy(alpha = 0.3f),
                start = Offset(x, padding),
                end = Offset(x, height - padding),
                strokeWidth = 1.dp.toPx()
            )

            // Month labels
            drawContext.canvas.nativeCanvas.drawText(
                monthNames[i],
                x - 8.dp.toPx(),
                height - padding + 20.dp.toPx(),
                android.graphics.Paint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // Draw horizontal grid lines and distance labels
        val numberOfYLabels = 5
        for (i in 0..numberOfYLabels) {
            val y = padding + (i * graphHeight / numberOfYLabels)
            val distance = maxDistance - (i * maxDistance / numberOfYLabels)

            // Horizontal grid lines
            if (i < numberOfYLabels) {
                drawLine(
                    color = axisColor.copy(alpha = 0.3f),
                    start = Offset(padding, y),
                    end = Offset(width - padding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Distance labels
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", distance),
                padding - 30.dp.toPx(),
                y + 5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // Draw the line graph
        val path = Path()

        monthlyStats.forEachIndexed { index, stats ->
            val x = padding + (index * stepX)
            val y = if (maxDistance > 0) {
                height - padding - ((stats.totalDistance / maxDistance) * graphHeight).toFloat()
            } else {
                height - padding
            }

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }

            // Draw data points
            if (stats.totalDistance > 0) {
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )

                // Draw activity count as small text above the point
                if (stats.activityCount > 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "${stats.activityCount}",
                        x,
                        y - 15.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = lineColor.toArgb()
                            textSize = 8.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
        }

        // Draw the path
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}