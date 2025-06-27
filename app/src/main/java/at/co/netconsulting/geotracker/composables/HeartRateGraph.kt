// Add these imports to your existing imports
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.Metric

@Composable
fun HeartRateGraph(
    metrics: List<Metric>, // Using your domain Metric class
    modifier: Modifier = Modifier
) {
    // Filter metrics to get only heart rate data
    val heartRateMetrics = metrics.filter { it.heartRate > 0 }

    if (heartRateMetrics.isEmpty()) {
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

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 20.dp.toPx()

        if (heartRateMetrics.size < 2) return@Canvas

        // Extract timestamps and heart rates from your domain Metric class
        val timestamps = heartRateMetrics.map { it.timeInMilliseconds }
        val heartRates = heartRateMetrics.map { it.heartRate.toFloat() }

        // Calculate bounds
        val minTime = timestamps.minOrNull() ?: 0L
        val maxTime = timestamps.maxOrNull() ?: 0L
        val minHeartRate = heartRates.minOrNull() ?: 0f
        val maxHeartRate = heartRates.maxOrNull() ?: 0f

        val timeRange = maxTime - minTime

        // Add some padding to the heart rate range for better visualization
        val heartRateRange = maxHeartRate - minHeartRate
        val paddedMinHeartRate = (minHeartRate - heartRateRange * 0.1f).coerceAtLeast(0f)
        val paddedMaxHeartRate = maxHeartRate + heartRateRange * 0.1f

        // Draw background grid lines
        val gridColor = Color.Gray.copy(alpha = 0.3f)

        // Horizontal grid lines (heart rate)
        for (i in 0..4) {
            val y = padding + (height - 2 * padding) * i / 4
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Vertical grid lines (time)
        for (i in 0..4) {
            val x = padding + (width - 2 * padding) * i / 4
            drawLine(
                color = gridColor,
                start = Offset(x, padding),
                end = Offset(x, height - padding),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Create path for heart rate line
        val path = Path()

        heartRateMetrics.forEachIndexed { index, metric ->
            val timestamp = metric.timeInMilliseconds
            val heartRate = metric.heartRate.toFloat()

            val x = if (timeRange > 0) {
                padding + (width - 2 * padding) *
                        (timestamp - minTime).toFloat() / timeRange.toFloat()
            } else {
                padding + (width - 2 * padding) / 2 // Center if no time range
            }
            val y = height - padding - (height - 2 * padding) *
                    (heartRate - paddedMinHeartRate) / (paddedMaxHeartRate - paddedMinHeartRate)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw the heart rate line
        drawPath(
            path = path,
            color = Color.Red,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw data points
        heartRateMetrics.forEach { metric ->
            val timestamp = metric.timeInMilliseconds
            val heartRate = metric.heartRate.toFloat()

            val x = if (timeRange > 0) {
                padding + (width - 2 * padding) *
                        (timestamp - minTime).toFloat() / timeRange.toFloat()
            } else {
                padding + (width - 2 * padding) / 2 // Center if no time range
            }
            val y = height - padding - (height - 2 * padding) *
                    (heartRate - paddedMinHeartRate) / (paddedMaxHeartRate - paddedMinHeartRate)

            drawCircle(
                color = Color.Red,
                radius = 2.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Draw labels
        val textPaint = android.graphics.Paint().apply {
            color = Color.Black.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }

        // Y-axis labels (heart rate)
        for (i in 0..4) {
            val heartRate = paddedMinHeartRate + (paddedMaxHeartRate - paddedMinHeartRate) * (4 - i) / 4
            val y = padding + (height - 2 * padding) * i / 4

            drawContext.canvas.nativeCanvas.drawText(
                "${heartRate.toInt()}",
                5.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        // X-axis labels (time) - show relative time in minutes
        for (i in 0..4) {
            val timeOffset = if (timeRange > 0) timeRange * i / 4 else 0L
            val minutes = (timeOffset / 60000).toInt() // Convert to minutes
            val x = padding + (width - 2 * padding) * i / 4

            drawContext.canvas.nativeCanvas.drawText(
                "${minutes}m",
                x - 8.dp.toPx(),
                height - 5.dp.toPx(),
                textPaint
            )
        }
    }
}