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
import at.co.netconsulting.geotracker.data.MonthlyHeartRateStats
import kotlin.math.max
import kotlin.math.min

@Composable
fun HeartRateTrendGraph(
    monthlyHeartRateStats: List<MonthlyHeartRateStats>,
    modifier: Modifier = Modifier
) {
    val statsWithData = monthlyHeartRateStats.filter { it.avgHR > 0 }

    if (statsWithData.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No heart rate data available",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        return
    }

    // Get colors outside of Canvas
    val maxHRColor = Color.Red
    val avgHRColor = MaterialTheme.colorScheme.primary
    val minHRColor = Color.Blue

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val leftPadding = 60.dp.toPx() // Extra space for HR labels
        val rightPadding = 40.dp.toPx()
        val topPadding = 40.dp.toPx()
        val bottomPadding = 50.dp.toPx() // Extra space for month labels
        val graphWidth = width - leftPadding - rightPadding
        val graphHeight = height - topPadding - bottomPadding

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        // Find HR ranges for scaling
        val allHRValues = statsWithData.flatMap { listOf(it.minHR, it.maxHR, it.avgHR.toInt()) }
        val minHRValue = allHRValues.minOrNull() ?: 60
        val maxHRValue = allHRValues.maxOrNull() ?: 180
        val hrRange = maxHRValue - minHRValue

        // Ensure minimum range for better visualization with padding
        val rangePadding = maxOf(10, hrRange / 10) // 10% padding or minimum 10 bpm
        val adjustedMinHR = maxOf(40, minHRValue - rangePadding)
        val adjustedMaxHR = minOf(220, maxHRValue + rangePadding)
        val adjustedRange = adjustedMaxHR - adjustedMinHR

        // Draw axes
        val axisColor = Color.Gray
        val axisStrokeWidth = 2.dp.toPx()

        // Y-axis (left)
        drawLine(
            color = axisColor,
            start = Offset(leftPadding, topPadding),
            end = Offset(leftPadding, height - bottomPadding),
            strokeWidth = axisStrokeWidth
        )

        // X-axis (bottom)
        drawLine(
            color = axisColor,
            start = Offset(leftPadding, height - bottomPadding),
            end = Offset(width - rightPadding, height - bottomPadding),
            strokeWidth = axisStrokeWidth
        )

        // Draw grid lines and labels
        val monthNames = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val stepX = graphWidth / 11f // 12 months, 11 intervals

        for (i in 0..11) {
            val x = leftPadding + (i * stepX)

            // Vertical grid lines
            drawLine(
                color = axisColor.copy(alpha = 0.3f),
                start = Offset(x, topPadding),
                end = Offset(x, height - bottomPadding),
                strokeWidth = 1.dp.toPx()
            )

            // Month labels
            drawContext.canvas.nativeCanvas.drawText(
                monthNames[i],
                x,
                height - bottomPadding + 25.dp.toPx(),
                android.graphics.Paint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // Draw horizontal grid lines and HR labels
        val numberOfYLabels = 6
        for (i in 0..numberOfYLabels) {
            val y = topPadding + (i * graphHeight / numberOfYLabels)
            val hrValue = adjustedMaxHR - (i * adjustedRange / numberOfYLabels)

            // Horizontal grid lines
            if (i < numberOfYLabels) {
                drawLine(
                    color = axisColor.copy(alpha = 0.3f),
                    start = Offset(leftPadding, y),
                    end = Offset(width - rightPadding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // HR labels (positioned properly to the left of Y-axis)
            drawContext.canvas.nativeCanvas.drawText(
                "${hrValue.toInt()}",
                leftPadding - 10.dp.toPx(),
                y + 5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // Create paths for the three HR lines
        val maxHRPath = Path()
        val avgHRPath = Path()
        val minHRPath = Path()

        // Calculate positions and draw lines - process all 12 months
        for (monthIndex in 0..11) {
            val x = leftPadding + (monthIndex * stepX)
            val stats = monthlyHeartRateStats[monthIndex]

            if (stats.avgHR > 0) {
                // Calculate Y positions with proper scaling (inverted Y axis)
                val maxY = height - bottomPadding - ((stats.maxHR - adjustedMinHR).toFloat() / adjustedRange * graphHeight)
                val avgY = height - bottomPadding - ((stats.avgHR.toFloat() - adjustedMinHR).toFloat() / adjustedRange * graphHeight)
                val minY = height - bottomPadding - ((stats.minHR - adjustedMinHR).toFloat() / adjustedRange * graphHeight)

                // Clamp Y values to graph bounds
                val clampedMaxY = maxY.coerceIn(topPadding, height - bottomPadding)
                val clampedAvgY = avgY.coerceIn(topPadding, height - bottomPadding)
                val clampedMinY = minY.coerceIn(topPadding, height - bottomPadding)

                // Add to paths
                if (monthIndex == 0 || monthlyHeartRateStats.take(monthIndex).none { it.avgHR > 0 }) {
                    // First point or first point with data
                    maxHRPath.moveTo(x, clampedMaxY)
                    avgHRPath.moveTo(x, clampedAvgY)
                    minHRPath.moveTo(x, clampedMinY)
                } else {
                    maxHRPath.lineTo(x, clampedMaxY)
                    avgHRPath.lineTo(x, clampedAvgY)
                    minHRPath.lineTo(x, clampedMinY)
                }

                // Draw data points
                drawCircle(
                    color = maxHRColor,
                    radius = 3.dp.toPx(),
                    center = Offset(x, clampedMaxY)
                )
                drawCircle(
                    color = avgHRColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, clampedAvgY)
                )
                drawCircle(
                    color = minHRColor,
                    radius = 3.dp.toPx(),
                    center = Offset(x, clampedMinY)
                )

                // Draw activity count if significant
                if (stats.activitiesWithHR > 1) {
                    val labelY = (clampedMaxY - 15.dp.toPx()).coerceAtLeast(topPadding + 15.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(
                        "${stats.activitiesWithHR}",
                        x,
                        labelY,
                        android.graphics.Paint().apply {
                            color = maxHRColor.toArgb()
                            textSize = 8.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
        }

        // Draw the paths
        drawPath(
            path = maxHRPath,
            color = maxHRColor,
            style = Stroke(width = 2.dp.toPx())
        )
        drawPath(
            path = avgHRPath,
            color = avgHRColor,
            style = Stroke(width = 3.dp.toPx())
        )
        drawPath(
            path = minHRPath,
            color = minHRColor,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw legend (positioned within graph area)
        val legendY = topPadding + 20.dp.toPx()
        val legendStartX = width - rightPadding - 120.dp.toPx()

        // Max HR legend
        drawLine(
            color = maxHRColor,
            start = Offset(legendStartX, legendY),
            end = Offset(legendStartX + 15.dp.toPx(), legendY),
            strokeWidth = 2.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(
            "Max HR",
            legendStartX + 20.dp.toPx(),
            legendY + 5.dp.toPx(),
            android.graphics.Paint().apply {
                color = maxHRColor.toArgb()
                textSize = 9.sp.toPx()
            }
        )

        // Avg HR legend
        val avgLegendY = legendY + 15.dp.toPx()
        drawLine(
            color = avgHRColor,
            start = Offset(legendStartX, avgLegendY),
            end = Offset(legendStartX + 15.dp.toPx(), avgLegendY),
            strokeWidth = 3.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(
            "Avg HR",
            legendStartX + 20.dp.toPx(),
            avgLegendY + 5.dp.toPx(),
            android.graphics.Paint().apply {
                color = avgHRColor.toArgb()
                textSize = 9.sp.toPx()
            }
        )

        // Min HR legend
        val minLegendY = avgLegendY + 15.dp.toPx()
        drawLine(
            color = minHRColor,
            start = Offset(legendStartX, minLegendY),
            end = Offset(legendStartX + 15.dp.toPx(), minLegendY),
            strokeWidth = 2.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(
            "Min HR",
            legendStartX + 20.dp.toPx(),
            minLegendY + 5.dp.toPx(),
            android.graphics.Paint().apply {
                color = minHRColor.toArgb()
                textSize = 9.sp.toPx()
            }
        )
    }
}