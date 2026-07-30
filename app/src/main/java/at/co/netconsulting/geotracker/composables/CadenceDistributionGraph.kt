package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.CadenceBucketStats

@Composable
fun CadenceDistributionGraph(
    buckets: List<CadenceBucketStats>,
    modifier: Modifier = Modifier
) {
    if (buckets.none { it.sampleCount > 0 }) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No SPM samples available", fontSize = 12.sp, color = Color.Gray)
        }
        return
    }

    Canvas(modifier = modifier) {
        val left = 34.dp.toPx()
        val right = 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 32.dp.toPx()
        val graphWidth = (size.width - left - right).coerceAtLeast(1f)
        val graphHeight = (size.height - top - bottom).coerceAtLeast(1f)
        val maxCount = buckets.maxOfOrNull { it.sampleCount }?.coerceAtLeast(1) ?: 1
        val slotWidth = graphWidth / buckets.size
        val barWidth = slotWidth * 0.64f
        val axisColor = Color.Gray.copy(alpha = 0.28f)
        val barColors = listOf(
            Color(0xFF9575CD), Color(0xFF7E57C2), Color(0xFF5E35B1),
            Color(0xFF3949AB), Color(0xFFD81B60), Color(0xFFC2185B)
        )
        val labelPaint = android.graphics.Paint().apply {
            color = Color.Gray.toArgb()
            textSize = 8.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        drawLine(
            axisColor,
            Offset(left, top + graphHeight),
            Offset(size.width - right, top + graphHeight),
            1.dp.toPx()
        )

        buckets.forEachIndexed { index, bucket ->
            val centerX = left + slotWidth * (index + 0.5f)
            val barHeight = graphHeight * bucket.sampleCount / maxCount
            val barTop = top + graphHeight - barHeight
            drawRect(
                color = barColors[index % barColors.size],
                topLeft = Offset(centerX - barWidth / 2, barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )
            if (bucket.sampleCount > 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    bucket.sampleCount.toString(),
                    centerX,
                    (barTop - 4.dp.toPx()).coerceAtLeast(9.dp.toPx()),
                    labelPaint
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                bucket.label,
                centerX,
                size.height - 7.dp.toPx(),
                labelPaint
            )
        }
    }
}
