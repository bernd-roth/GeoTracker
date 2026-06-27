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
import kotlin.math.roundToInt

enum class CadenceXAxis { TIME, DISTANCE }

@Composable
fun CadenceGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier,
    xAxis: CadenceXAxis = CadenceXAxis.TIME
) {
    val points = metrics
        .filter { (it.cadence ?: 0) > 0 && it.timeInMilliseconds > 0 }
        .filter { xAxis == CadenceXAxis.TIME || it.distance > 0.0 }
        .sortedBy { if (xAxis == CadenceXAxis.TIME) it.timeInMilliseconds.toDouble() else it.distance }

    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No cadence data available", fontSize = 12.sp, color = Color.Gray)
        }
        return
    }

    Canvas(modifier = modifier) {
        val leftPadding = 38.dp.toPx()
        val rightPadding = 8.dp.toPx()
        val verticalPadding = 24.dp.toPx()
        val chartWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
        val chartHeight = (size.height - 2 * verticalPadding).coerceAtLeast(1f)

        val minTime = points.first().timeInMilliseconds
        val xValues = points.map {
            if (xAxis == CadenceXAxis.TIME) {
                (it.timeInMilliseconds - minTime).toDouble()
            } else {
                it.distance
            }
        }
        val minX = xValues.minOrNull() ?: 0.0
        val maxX = xValues.maxOrNull() ?: 1.0
        val xRange = (maxX - minX).coerceAtLeast(1.0)

        val cadenceValues = points.map { it.cadence!!.toFloat() }
        val minCadence = cadenceValues.minOrNull() ?: 0f
        val maxCadence = cadenceValues.maxOrNull() ?: 1f
        val cadencePadding = ((maxCadence - minCadence) * 0.15f).coerceAtLeast(3f)
        val displayMin = (minCadence - cadencePadding).coerceAtLeast(0f)
        val displayMax = maxCadence + cadencePadding
        val cadenceRange = (displayMax - displayMin).coerceAtLeast(1f)
        val averageCadence = cadenceValues.average().toFloat()

        fun toX(value: Double): Float =
            leftPadding + chartWidth * ((value - minX) / xRange).toFloat()

        fun toY(value: Float): Float =
            verticalPadding + chartHeight * (displayMax - value) / cadenceRange

        val gridColor = Color.Gray.copy(alpha = 0.22f)
        for (index in 0..4) {
            val y = verticalPadding + chartHeight * index / 4
            drawLine(gridColor, Offset(leftPadding, y), Offset(size.width - rightPadding, y), 1.dp.toPx())
            val x = leftPadding + chartWidth * index / 4
            drawLine(gridColor, Offset(x, verticalPadding), Offset(x, verticalPadding + chartHeight), 1.dp.toPx())
        }

        drawLine(
            color = Color(0xFF80CBC4),
            start = Offset(leftPadding, toY(averageCadence)),
            end = Offset(size.width - rightPadding, toY(averageCadence)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )

        val path = Path()
        points.forEachIndexed { index, metric ->
            val xValue = if (xAxis == CadenceXAxis.TIME) {
                (metric.timeInMilliseconds - minTime).toDouble()
            } else {
                metric.distance
            }
            val x = toX(xValue)
            val y = toY(metric.cadence!!.toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF7E57C2), style = Stroke(2.dp.toPx()))

        val yPaint = android.graphics.Paint().apply {
            color = Color.Gray.toArgb()
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }
        val xPaint = android.graphics.Paint(yPaint).apply {
            textAlign = android.graphics.Paint.Align.CENTER
        }

        for (index in 0..4) {
            val cadence = displayMin + cadenceRange * (4 - index) / 4
            val y = verticalPadding + chartHeight * index / 4
            drawContext.canvas.nativeCanvas.drawText(
                cadence.roundToInt().toString(),
                leftPadding - 4.dp.toPx(),
                y + 3.dp.toPx(),
                yPaint
            )

            val x = leftPadding + chartWidth * index / 4
            val value = minX + xRange * index / 4
            val label = if (xAxis == CadenceXAxis.TIME) {
                "${(value / 60_000.0).roundToInt()}min"
            } else {
                String.format("%.1fkm", value / 1000.0)
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                size.height - 3.dp.toPx(),
                xPaint
            )
        }
    }
}
