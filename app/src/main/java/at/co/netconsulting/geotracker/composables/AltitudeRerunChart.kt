package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AltitudeRerunChart(
    altitudes: List<Double>,
    distancesMeters: List<Double>,
    progress: Float,
    referenceProgress: Float? = null,
    modifier: Modifier = Modifier
) {
    val validAltitudes = remember(altitudes) {
        altitudes.filter { it.isFinite() && it != 0.0 }
            .ifEmpty { altitudes.filter { it.isFinite() } }
    }

    val minAlt = remember(validAltitudes) { validAltitudes.minOrNull() ?: 0.0 }
    val maxAlt = remember(validAltitudes) { validAltitudes.maxOrNull() ?: 0.0 }
    val range = (maxAlt - minAlt).coerceAtLeast(1.0)

    val totalDistanceM = distancesMeters.lastOrNull() ?: 0.0
    val totalKm = totalDistanceM / 1000.0

    val density = LocalDensity.current
    val axisTextPx = with(density) { 9.sp.toPx() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.55f),
        shadowElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Header
            Text(
                text = "Altitude",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopStart)
            )

            // Max altitude label - top right
            Text(
                text = "${maxAlt.toInt()} m",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            // Min altitude label - bottom right (above the km axis row)
            Text(
                text = "${minAlt.toInt()} m",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 14.dp)
            )

            if (altitudes.size < 2) {
                Text(
                    text = "No altitude data",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                return@Box
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 18.dp, bottom = 26.dp, end = 40.dp, start = 4.dp)
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val n = altitudes.size
                val stepX = w / (n - 1).coerceAtLeast(1)

                fun yFor(alt: Double): Float {
                    val norm = ((alt - minAlt) / range).toFloat().coerceIn(0f, 1f)
                    return h - norm * h
                }

                val path = Path()
                altitudes.forEachIndexed { i, alt ->
                    val x = i * stepX
                    val y = yFor(alt)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                val fillPath = Path().apply {
                    addPath(path)
                    lineTo((n - 1) * stepX, h)
                    lineTo(0f, h)
                    close()
                }

                drawPath(
                    path = fillPath,
                    color = Color(0xFF4CAF50).copy(alpha = 0.25f)
                )
                drawPath(
                    path = path,
                    color = Color(0xFF4CAF50),
                    style = Stroke(width = 3f)
                )

                // X-axis: 5 km gridlines + labels (skip if route is too short)
                if (totalKm >= 1.0 && distancesMeters.size == altitudes.size) {
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = axisTextPx
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val maxKm = totalKm.toInt()
                    var km = 0
                    while (km <= maxKm) {
                        val targetMeters = km * 1000.0
                        val xFrac = (targetMeters / totalDistanceM).toFloat().coerceIn(0f, 1f)
                        val xPx = xFrac * w

                        // Gridline (faint, dashed look via short repeated segments would be nicer
                        // but a thin solid line keeps the code minimal)
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(xPx, 0f),
                            end = Offset(xPx, h),
                            strokeWidth = 1f
                        )

                        // Label below chart
                        drawContext.canvas.nativeCanvas.drawText(
                            "${km} km",
                            xPx,
                            h + axisTextPx + 4f,
                            labelPaint
                        )

                        km += 5
                    }
                }

                fun positionFor(progressValue: Float): Offset {
                    val clamped = progressValue.coerceIn(0f, 1f)
                    val scaledIndex = clamped * (n - 1)
                    val lowerIndex = scaledIndex.toInt().coerceIn(0, n - 1)
                    val upperIndex = (lowerIndex + 1).coerceAtMost(n - 1)
                    val segmentProgress = (scaledIndex - lowerIndex).coerceIn(0f, 1f)
                    val interpolatedAltitude = altitudes[lowerIndex] +
                            (altitudes[upperIndex] - altitudes[lowerIndex]) * segmentProgress
                    return Offset(scaledIndex * stepX, yFor(interpolatedAltitude))
                }

                fun drawPositionDot(
                    progressValue: Float,
                    color: Color,
                    guideColor: Color,
                    innerRadius: Float,
                    outerRadius: Float
                ) {
                    val dot = positionFor(progressValue)
                    drawLine(
                        color = guideColor,
                        start = Offset(dot.x, 0f),
                        end = Offset(dot.x, h),
                        strokeWidth = 1.5f
                    )
                    drawCircle(
                        color = Color.White,
                        radius = outerRadius,
                        center = dot
                    )
                    drawCircle(
                        color = color,
                        radius = innerRadius,
                        center = dot
                    )
                }

                referenceProgress?.let {
                    drawPositionDot(
                        progressValue = it,
                        color = Color(0xFF1E88E5),
                        guideColor = Color(0xFF1E88E5).copy(alpha = 0.45f),
                        innerRadius = 4.5f,
                        outerRadius = 6.5f
                    )
                }

                // Red dot: current live position on the profile.
                drawPositionDot(
                    progressValue = progress,
                    color = Color(0xFFE53935),
                    guideColor = Color.White.copy(alpha = 0.6f),
                    innerRadius = 5f,
                    outerRadius = 7f
                )
            }

            // Current altitude label
            val clamped = progress.coerceIn(0f, 1f)
            val idx = (clamped * (altitudes.size - 1).coerceAtLeast(0)).toInt()
                .coerceIn(0, altitudes.size - 1)
            val currentAlt = altitudes.getOrNull(idx) ?: 0.0
            Text(
                text = "${currentAlt.toInt()} m",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
            )
        }
    }
}
