package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.Metric

private const val MIN_SPEED_KMH = 0.5f   // ignore near-stop points
private const val MAX_PACE_MIN_PER_KM = 20f  // cap to avoid runaway Y-axis

@Composable
fun PaceGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    val paceMetrics = metrics
        .filter { it.speed >= MIN_SPEED_KMH }
        .map { it to (60f / it.speed).coerceAtMost(MAX_PACE_MIN_PER_KM) }

    if (paceMetrics.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = "No pace data available", fontSize = 12.sp, color = Color.Gray)
        }
        return
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 36.dp.toPx()
        val rightPadding = 8.dp.toPx()
        val chartWidth = width - padding - rightPadding
        val chartHeight = height - 2 * padding

        val minTime = paceMetrics.minOf { it.first.timeInMilliseconds }
        val maxTime = paceMetrics.maxOf { it.first.timeInMilliseconds }
        val timeRange = (maxTime - minTime).coerceAtLeast(1L)

        // Use 5th/95th percentile to clip outliers from the Y-axis range
        val sortedPaces = paceMetrics.map { it.second }.sorted()
        val p5 = sortedPaces[(sortedPaces.size * 0.05).toInt()]
        val p95 = sortedPaces[((sortedPaces.size * 0.95).toInt()).coerceAtMost(sortedPaces.size - 1)]
        val paceRange = (p95 - p5).coerceAtLeast(0.5f)
        // Add 15% padding to the clipped range
        val displayMin = (p5 - paceRange * 0.15f).coerceAtLeast(0f)
        val displayMax = p95 + paceRange * 0.15f
        val displayRange = (displayMax - displayMin).coerceAtLeast(0.1f)

        val avgPace = paceMetrics.map { it.second }.average().toFloat()

        fun toX(timeMs: Long): Float =
            padding + chartWidth * (timeMs - minTime).toFloat() / timeRange.toFloat()

        // Note: pace axis is inverted — lower pace (faster) at bottom, higher pace (slower) at top
        fun toY(pace: Float): Float =
            padding + chartHeight * (pace - displayMin) / displayRange

        // Grid lines
        val gridColor = Color.Gray.copy(alpha = 0.25f)
        for (i in 0..4) {
            val y = padding + chartHeight * i / 4
            drawLine(gridColor, Offset(padding, y), Offset(width - rightPadding, y), 1.dp.toPx())
        }
        for (i in 0..4) {
            val x = padding + chartWidth * i / 4
            drawLine(gridColor, Offset(x, padding), Offset(x, padding + chartHeight), 1.dp.toPx())
        }

        // Average pace dashed line
        val avgY = toY(avgPace)
        drawLine(
            color = Color(0xFF80CBC4),
            start = Offset(padding, avgY),
            end = Offset(width - rightPadding, avgY),
            strokeWidth = 1.5f.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )

        // Pace line
        val path = Path()
        paceMetrics.forEachIndexed { index, (metric, pace) ->
            val x = toX(metric.timeInMilliseconds)
            val y = toY(pace)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF4CAF50), style = Stroke(width = 2.dp.toPx()))

        // Labels
        val textPaint = android.graphics.Paint().apply {
            color = Color.Gray.toArgb()
            textSize = 9.sp.toPx()
            isAntiAlias = true
        }

        // Y-axis labels (pace in MM:SS)
        for (i in 0..4) {
            val pace = displayMin + displayRange * i / 4
            val totalSeconds = (pace * 60).toInt()
            val label = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
            val y = toY(pace)
            drawContext.canvas.nativeCanvas.drawText(label, 2.dp.toPx(), y + 3.dp.toPx(), textPaint)
        }

        // X-axis labels (elapsed minutes)
        for (i in 0..4) {
            val elapsedMs = timeRange * i / 4
            val minutes = (elapsedMs / 60_000L).toInt()
            val x = padding + chartWidth * i / 4
            val label = "${minutes}min"
            drawContext.canvas.nativeCanvas.drawText(
                label, x - 10.dp.toPx(), height - 4.dp.toPx(), textPaint
            )
        }
    }
}
