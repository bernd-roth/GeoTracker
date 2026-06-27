package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.Metric
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class CadenceRelationXAxis { TIME, DISTANCE, ALTITUDE, SPEED }

private data class IndexedCadenceMetric(val index: Int, val metric: Metric)

@Composable
fun InteractiveCadenceGraph(
    metrics: List<Metric>,
    xAxis: CadenceRelationXAxis,
    selectedMetricIndex: Int?,
    onPointSelected: (Int) -> Unit,
    displayMultiplier: Int = 1,
    modifier: Modifier = Modifier
) {
    val minTime = metrics.minOfOrNull { it.timeInMilliseconds } ?: 0L
    val points = metrics.mapIndexedNotNull { index, metric ->
        if ((metric.cadence ?: 0) > 0 && metric.timeInMilliseconds > 0 &&
            (xAxis != CadenceRelationXAxis.DISTANCE || metric.distance > 0.0)
        ) {
            IndexedCadenceMetric(index, metric)
        } else null
    }.sortedBy { xValue(it.metric, xAxis, minTime) }

    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No cadence data available", fontSize = 12.sp, color = Color.Gray)
        }
        return
    }

    val density = LocalDensity.current
    val xValues = points.map { xValue(it.metric, xAxis, minTime) }
    val minX = xValues.minOrNull() ?: 0.0
    val maxX = xValues.maxOrNull() ?: 1.0
    val xRange = (maxX - minX).coerceAtLeast(1.0)
    val cadenceValues = points.map { it.metric.cadence!!.toFloat() * displayMultiplier }
    val minCadence = cadenceValues.minOrNull() ?: 0f
    val maxCadence = cadenceValues.maxOrNull() ?: 1f
    val cadencePadding = ((maxCadence - minCadence) * 0.15f).coerceAtLeast(3f)
    val displayMin = (minCadence - cadencePadding).coerceAtLeast(0f)
    val displayMax = maxCadence + cadencePadding
    val cadenceRange = (displayMax - displayMin).coerceAtLeast(1f)
    val averageCadence = cadenceValues.average().toFloat()

    Canvas(
        modifier = modifier.pointerInput(points, xAxis) {
            detectTapGestures { tap ->
                val left = with(density) { 46.dp.toPx() }
                val right = with(density) { 10.dp.toPx() }
                val vertical = with(density) { 30.dp.toPx() }
                val chartWidth = (size.width - left - right).coerceAtLeast(1f)
                val chartHeight = (size.height - 2 * vertical).coerceAtLeast(1f)

                val closest = points.minByOrNull { point ->
                    val value = xValue(point.metric, xAxis, minTime)
                    val x = left + chartWidth * ((value - minX) / xRange).toFloat()
                    val y = vertical + chartHeight *
                        (displayMax - point.metric.cadence!!.toFloat() * displayMultiplier) / cadenceRange
                    hypot((tap.x - x).toDouble(), (tap.y - y).toDouble())
                }
                closest?.let { onPointSelected(it.index) }
            }
        }
    ) {
        val left = 46.dp.toPx()
        val right = 10.dp.toPx()
        val vertical = 30.dp.toPx()
        val chartWidth = (size.width - left - right).coerceAtLeast(1f)
        val chartHeight = (size.height - 2 * vertical).coerceAtLeast(1f)

        fun toX(value: Double): Float = left + chartWidth * ((value - minX) / xRange).toFloat()
        fun toY(value: Float): Float = vertical + chartHeight * (displayMax - value) / cadenceRange

        val gridColor = Color.Gray.copy(alpha = 0.22f)
        for (index in 0..4) {
            val y = vertical + chartHeight * index / 4
            drawLine(gridColor, Offset(left, y), Offset(size.width - right, y), 1.dp.toPx())
            val x = left + chartWidth * index / 4
            drawLine(gridColor, Offset(x, vertical), Offset(x, vertical + chartHeight), 1.dp.toPx())
        }

        drawLine(
            color = Color(0xFF80CBC4),
            start = Offset(left, toY(averageCadence)),
            end = Offset(size.width - right, toY(averageCadence)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )

        if (xAxis == CadenceRelationXAxis.TIME || xAxis == CadenceRelationXAxis.DISTANCE) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = toX(xValue(point.metric, xAxis, minTime))
                val y = toY(point.metric.cadence!!.toFloat() * displayMultiplier)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF7E57C2), style = Stroke(2.dp.toPx()))
        }

        points.forEach { point ->
            val center = Offset(
                toX(xValue(point.metric, xAxis, minTime)),
                toY(point.metric.cadence!!.toFloat() * displayMultiplier)
            )
            val selected = point.index == selectedMetricIndex
            if (selected) drawCircle(Color.White, 8.dp.toPx(), center)
            drawCircle(
                color = if (selected) Color(0xFFD81B60) else Color(0xFF7E57C2),
                radius = if (selected) 6.dp.toPx() else 2.5.dp.toPx(),
                center = center
            )
        }

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
            val cadence = displayMax - cadenceRange * index / 4
            val y = vertical + chartHeight * index / 4
            drawContext.canvas.nativeCanvas.drawText(
                cadence.roundToInt().toString(), left - 4.dp.toPx(), y + 3.dp.toPx(), yPaint
            )

            val value = minX + xRange * index / 4
            val x = left + chartWidth * index / 4
            drawContext.canvas.nativeCanvas.drawText(
                formatXAxis(value, xAxis), x, size.height - 4.dp.toPx(), xPaint
            )
        }
    }
}

private fun xValue(
    metric: Metric,
    xAxis: CadenceRelationXAxis,
    minTime: Long
): Double = when (xAxis) {
    CadenceRelationXAxis.TIME -> (metric.timeInMilliseconds - minTime).toDouble()
    CadenceRelationXAxis.DISTANCE -> metric.distance
    CadenceRelationXAxis.ALTITUDE -> metric.elevation.toDouble()
    CadenceRelationXAxis.SPEED -> metric.speed.toDouble()
}

private fun formatXAxis(value: Double, xAxis: CadenceRelationXAxis): String = when (xAxis) {
    CadenceRelationXAxis.TIME -> "${(value / 60_000.0).roundToInt()}min"
    CadenceRelationXAxis.DISTANCE -> String.format("%.1fkm", value / 1000.0)
    CadenceRelationXAxis.ALTITUDE -> "${value.roundToInt()}m"
    CadenceRelationXAxis.SPEED -> String.format("%.1f", value)
}
