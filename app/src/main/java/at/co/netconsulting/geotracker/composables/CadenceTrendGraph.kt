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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.MonthlyCadenceStats
import kotlin.math.roundToInt

@Composable
fun CadenceTrendGraph(
    monthlyCadenceStats: List<MonthlyCadenceStats>,
    modifier: Modifier = Modifier
) {
    val statsWithData = monthlyCadenceStats.filter { it.avgSpm > 0 }
    if (statsWithData.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No running cadence data available", fontSize = 12.sp, color = Color.Gray)
        }
        return
    }

    Canvas(modifier = modifier) {
        val left = 46.dp.toPx()
        val right = 12.dp.toPx()
        val top = 28.dp.toPx()
        val bottom = 30.dp.toPx()
        val graphWidth = (size.width - left - right).coerceAtLeast(1f)
        val graphHeight = (size.height - top - bottom).coerceAtLeast(1f)
        val allValues = statsWithData.flatMap { listOf(it.minSpm, it.avgSpm.roundToInt(), it.maxSpm) }
        val rawMin = allValues.minOrNull() ?: 0
        val rawMax = allValues.maxOrNull() ?: 1
        val padding = ((rawMax - rawMin) * 0.12f).coerceAtLeast(5f)
        val displayMin = (rawMin - padding).coerceAtLeast(0f)
        val displayMax = rawMax + padding
        val range = (displayMax - displayMin).coerceAtLeast(1f)
        val monthLabels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val stepX = graphWidth / 11f

        fun x(index: Int) = left + index * stepX
        fun y(value: Float) = top + graphHeight * (displayMax - value) / range

        val labelPaint = android.graphics.Paint().apply {
            color = Color.Gray.toArgb()
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val yLabelPaint = android.graphics.Paint(labelPaint).apply {
            textAlign = android.graphics.Paint.Align.RIGHT
        }

        for (index in 0..11) {
            val position = x(index)
            drawLine(
                Color.Gray.copy(alpha = 0.18f),
                Offset(position, top),
                Offset(position, top + graphHeight),
                1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                monthLabels[index], position, size.height - 5.dp.toPx(), labelPaint
            )
        }
        for (index in 0..4) {
            val position = top + graphHeight * index / 4
            val value = displayMax - range * index / 4
            drawLine(
                Color.Gray.copy(alpha = 0.22f),
                Offset(left, position),
                Offset(size.width - right, position),
                1.dp.toPx()
            )
            drawContext.canvas.nativeCanvas.drawText(
                value.roundToInt().toString(), left - 5.dp.toPx(), position + 3.dp.toPx(), yLabelPaint
            )
        }

        val minColor = Color(0xFF5E35B1)
        val avgColor = Color(0xFF7E57C2)
        val maxColor = Color(0xFFD81B60)
        val minPath = Path()
        val avgPath = Path()
        val maxPath = Path()
        var previousHadData = false

        monthlyCadenceStats.forEachIndexed { index, stats ->
            if (stats.avgSpm > 0) {
                val position = x(index)
                val minY = y(stats.minSpm.toFloat())
                val avgY = y(stats.avgSpm.toFloat())
                val maxY = y(stats.maxSpm.toFloat())
                if (previousHadData) {
                    minPath.lineTo(position, minY)
                    avgPath.lineTo(position, avgY)
                    maxPath.lineTo(position, maxY)
                } else {
                    minPath.moveTo(position, minY)
                    avgPath.moveTo(position, avgY)
                    maxPath.moveTo(position, maxY)
                }
                drawCircle(minColor, 2.5.dp.toPx(), Offset(position, minY))
                drawCircle(avgColor, 4.dp.toPx(), Offset(position, avgY))
                drawCircle(maxColor, 2.5.dp.toPx(), Offset(position, maxY))
                previousHadData = true
            } else {
                previousHadData = false
            }
        }

        drawPath(minPath, minColor, style = Stroke(1.5.dp.toPx()))
        drawPath(avgPath, avgColor, style = Stroke(3.dp.toPx()))
        drawPath(maxPath, maxColor, style = Stroke(1.5.dp.toPx()))

        val legendPaint = android.graphics.Paint(labelPaint).apply {
            textAlign = android.graphics.Paint.Align.LEFT
            textSize = 9.sp.toPx()
        }
        listOf(
            Triple("Min", minColor, left),
            Triple("Avg", avgColor, left + 58.dp.toPx()),
            Triple("Max", maxColor, left + 116.dp.toPx())
        ).forEach { (label, color, startX) ->
            drawLine(color, Offset(startX, 10.dp.toPx()), Offset(startX + 14.dp.toPx(), 10.dp.toPx()), 2.dp.toPx())
            legendPaint.color = color.toArgb()
            drawContext.canvas.nativeCanvas.drawText(label, startX + 18.dp.toPx(), 13.dp.toPx(), legendPaint)
        }
    }
}
