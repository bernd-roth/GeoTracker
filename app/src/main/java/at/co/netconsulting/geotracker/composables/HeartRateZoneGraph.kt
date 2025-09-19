package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.HeartRateZoneStats
import kotlin.math.max

@Composable
fun HeartRateZoneGraph(
    zoneStats: HeartRateZoneStats,
    modifier: Modifier = Modifier
) {
    val totalActivities = zoneStats.zone1Count + zoneStats.zone2Count +
                         zoneStats.zone3Count + zoneStats.zone4Count + zoneStats.zone5Count

    if (totalActivities == 0) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No heart rate zone data available",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        return
    }

    // Heart rate zone colors (based on training zones)
    val zoneColors = listOf(
        Color(0xFF4CAF50), // Zone 1 - Recovery (Green)
        Color(0xFF8BC34A), // Zone 2 - Aerobic Base (Light Green)
        Color(0xFFFFC107), // Zone 3 - Aerobic (Yellow)
        Color(0xFFFF9800), // Zone 4 - Lactate Threshold (Orange)
        Color(0xFFF44336)  // Zone 5 - Neuromuscular Power (Red)
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val leftPadding = 50.dp.toPx() // Extra space for count labels
        val rightPadding = 20.dp.toPx()
        val topPadding = 40.dp.toPx()
        val bottomPadding = 60.dp.toPx() // Extra space for zone labels and percentages
        val graphWidth = width - leftPadding - rightPadding
        val graphHeight = height - topPadding - bottomPadding

        if (graphWidth <= 0 || graphHeight <= 0) return@Canvas

        val zoneCounts = listOf(
            zoneStats.zone1Count,
            zoneStats.zone2Count,
            zoneStats.zone3Count,
            zoneStats.zone4Count,
            zoneStats.zone5Count
        )

        val maxCount = zoneCounts.maxOrNull() ?: 1
        val barWidth = graphWidth / 5f * 0.8f // 80% of available width per zone
        val barSpacing = graphWidth / 5f * 0.2f / 4f // Remaining 20% distributed as spacing

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

        // Draw horizontal grid lines and count labels
        val numberOfYLabels = 5
        for (i in 0..numberOfYLabels) {
            val y = topPadding + (i * graphHeight / numberOfYLabels)
            val count = maxCount - (i * maxCount / numberOfYLabels)

            // Horizontal grid lines
            if (i < numberOfYLabels) {
                drawLine(
                    color = axisColor.copy(alpha = 0.3f),
                    start = Offset(leftPadding, y),
                    end = Offset(width - rightPadding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Count labels (positioned properly to the left of Y-axis)
            drawContext.canvas.nativeCanvas.drawText(
                "${count}",
                leftPadding - 10.dp.toPx(),
                y + 5.dp.toPx(),
                android.graphics.Paint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 11.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
            )
        }

        // Draw bars for each zone
        zoneCounts.forEachIndexed { index, count ->
            val barX = leftPadding + (index * (barWidth + barSpacing)) + (barSpacing / 2f)
            val barHeight = if (maxCount > 0) (count.toFloat() / maxCount * graphHeight) else 0f
            val barY = height - bottomPadding - barHeight

            // Draw bar
            drawRect(
                color = zoneColors[index],
                topLeft = Offset(barX, barY),
                size = Size(barWidth, barHeight)
            )

            // Draw count on top of bar
            if (count > 0) {
                val labelY = (barY - 8.dp.toPx()).coerceAtLeast(topPadding + 15.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(
                    "$count",
                    barX + barWidth / 2f,
                    labelY,
                    android.graphics.Paint().apply {
                        color = zoneColors[index].toArgb()
                        textSize = 11.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                )
            }

            // Draw zone label
            drawContext.canvas.nativeCanvas.drawText(
                "Zone ${index + 1}",
                barX + barWidth / 2f,
                height - bottomPadding + 20.dp.toPx(),
                android.graphics.Paint().apply {
                    color = zoneColors[index].toArgb()
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
            )

            // Draw percentage
            val percentage = if (totalActivities > 0) (count.toFloat() / totalActivities * 100) else 0f
            if (percentage > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${percentage.toInt()}%",
                    barX + barWidth / 2f,
                    height - bottomPadding + 40.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = Color.Gray.toArgb()
                        textSize = 9.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }

        // Draw legend at the top
        val legendY = topPadding - 10.dp.toPx()
        val legendText = "Heart Rate Training Zones Distribution"
        drawContext.canvas.nativeCanvas.drawText(
            legendText,
            width / 2f,
            legendY,
            android.graphics.Paint().apply {
                color = Color.Gray.toArgb()
                textSize = 12.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
        )
    }
}