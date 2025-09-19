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
import at.co.netconsulting.geotracker.data.WeeklyStats
import kotlin.math.max
import kotlin.math.min

@Composable
fun WeeklyTrendGraph(
    weeklyStats: List<WeeklyStats>,
    modifier: Modifier = Modifier
) {
    val statsWithData = weeklyStats.filter { it.totalDistance > 0 }

    if (statsWithData.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No weekly data available",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        return
    }

    // Get the secondary color outside of Canvas
    val lineColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 40.dp.toPx()
        val graphWidth = width - (2 * padding)
        val graphHeight = height - (2 * padding)

        if (graphWidth <= 0 || graphHeight <= 0 || weeklyStats.isEmpty()) return@Canvas

        // Find the maximum distance for scaling
        val maxDistance = weeklyStats.maxOfOrNull { it.totalDistance } ?: 1.0
        val minWeek = weeklyStats.minOfOrNull { it.week } ?: 1
        val maxWeek = weeklyStats.maxOfOrNull { it.week } ?: 52
        val weekRange = max(1, maxWeek - minWeek)

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

        // Draw horizontal grid lines and distance labels
        val numberOfYLabels = 4
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

        // Draw vertical grid lines and week labels (show every 4-8 weeks depending on range)
        val weekStep = when {
            weekRange <= 12 -> 2  // Show every 2 weeks
            weekRange <= 26 -> 4  // Show every 4 weeks
            else -> 8             // Show every 8 weeks
        }

        var currentWeek = minWeek
        while (currentWeek <= maxWeek) {
            val x = padding + ((currentWeek - minWeek).toFloat() / weekRange * graphWidth)

            // Vertical grid lines
            drawLine(
                color = axisColor.copy(alpha = 0.3f),
                start = Offset(x, padding),
                end = Offset(x, height - padding),
                strokeWidth = 1.dp.toPx()
            )

            // Week labels
            drawContext.canvas.nativeCanvas.drawText(
                "W$currentWeek",
                x,
                height - padding + 20.dp.toPx(),
                android.graphics.Paint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 8.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )

            currentWeek += weekStep
        }

        // Draw the line graph
        val path = Path()

        weeklyStats.forEachIndexed { index, stats ->
            val x = padding + ((stats.week - minWeek).toFloat() / weekRange * graphWidth)
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
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )

                // Draw activity count as small text above the point (only for significant weeks)
                if (stats.activityCount > 2) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "${stats.activityCount}",
                        x,
                        y - 12.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = lineColor.toArgb()
                            textSize = 7.sp.toPx()
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
            style = Stroke(width = 2.dp.toPx())
        )
    }
}