package at.co.netconsulting.geotracker.composables


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.AltitudeDistanceInfo
import at.co.netconsulting.geotracker.data.AltitudeSpeedInfo
import at.co.netconsulting.geotracker.data.AltitudeChangeRateInfo
import at.co.netconsulting.geotracker.domain.Metric
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun InteractiveDistanceVsAltitudeGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<AltitudeDistanceInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestAltitudeDistancePoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
                        showInfo = info
                    }
                }
        ) {
            if (metrics.isEmpty()) return@Canvas

            val padding = 60.dp.toPx()
            val graphWidth = size.width - 2 * padding
            val graphHeight = size.height - 2 * padding

            // Use existing distance field (already cumulative)
            val maxDistance = metrics.maxOfOrNull { it.distance } ?: 1.0
            val minElevation = metrics.minOf { it.elevation.toDouble() }
            val maxElevation = metrics.maxOf { it.elevation.toDouble() }
            val elevationRange = (maxElevation - minElevation).coerceAtLeast(1.0)

            // Draw axes
            drawLine(
                color = Color.Gray,
                start = Offset(padding, padding),
                end = Offset(padding, size.height - padding),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Gray,
                start = Offset(padding, size.height - padding),
                end = Offset(size.width - padding, size.height - padding),
                strokeWidth = 2.dp.toPx()
            )

            // Draw grid lines and Y-axis values (elevation)
            val paint = Paint().asFrameworkPaint().apply {
                color = Color.Gray.toArgb()
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            for (i in 0..5) {
                val y = padding + (graphHeight * i / 5)
                if (i > 0 && i < 5) {
                    drawLine(
                        color = Color.LightGray,
                        start = Offset(padding, y),
                        end = Offset(size.width - padding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val elevationValue = maxElevation - (elevationRange * i / 5)
                drawContext.canvas.nativeCanvas.drawText(
                    "${elevationValue.toInt()}",
                    padding - 10.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint
                )
            }

            // Draw X-axis values (distance)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (i in 0..4) {
                val x = padding + (graphWidth * i / 4)
                val distanceValue = maxDistance * i / 4
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", distanceValue / 1000),
                    x,
                    size.height - padding + 20.dp.toPx(),
                    paint
                )
            }

            // Draw elevation profile
            if (metrics.size > 1 && maxDistance > 0.0 && elevationRange > 0.0) {
                // Draw filled area under the graph
                val filledPath = Path().apply {
                    moveTo(padding, size.height - padding)
                    metrics.forEachIndexed { index, metric ->
                        val x = padding + (metric.distance / maxDistance * graphWidth).toFloat()
                        val y = size.height - padding - ((metric.elevation.toDouble() - minElevation) / elevationRange * graphHeight).toFloat()
                        if (index == 0) {
                            lineTo(x, y)
                        } else {
                            lineTo(x, y)
                        }
                    }
                    lineTo(size.width - padding, size.height - padding)
                    close()
                }

                drawPath(
                    path = filledPath,
                    color = Color(0x334CAF50),
                    style = androidx.compose.ui.graphics.drawscope.Fill
                )

                // Draw elevation line
                val path = Path()
                metrics.forEachIndexed { index, metric ->
                    val x = padding + (metric.distance / maxDistance * graphWidth).toFloat()
                    val y = size.height - padding - ((metric.elevation.toDouble() - minElevation) / elevationRange * graphHeight).toFloat()

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFF4CAF50),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            // Draw axis labels
            val labelPaint = Paint().asFrameworkPaint().apply {
                color = Color.Black.toArgb()
                textSize = 14.sp.toPx()
                isFakeBoldText = true
            }

            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, 20.dp.toPx(), size.height / 2)
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                "Elevation (m)",
                20.dp.toPx(),
                size.height / 2,
                labelPaint
            )
            drawContext.canvas.nativeCanvas.restore()

            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                "Distance (km)",
                size.width / 2,
                size.height - 5.dp.toPx(),
                labelPaint
            )
        }

        // Show info window
        showInfo?.let { info ->
            AltitudeInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Elevation: ${info.elevation.toInt()} m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Distance: ${String.format("%.2f", info.distance / 1000)} km",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveAltitudeChangeRateGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<AltitudeChangeRateInfo?>(null) }

    // Calculate elevation change rates with filtering
    val changeRatesData = remember(metrics) {
        val rates = mutableListOf<Double>()
        val timeStamps = mutableListOf<Long>()

        for (i in 1 until metrics.size) {
            val timeDiff = (metrics[i].timeInMilliseconds - metrics[i-1].timeInMilliseconds) / 1000.0 // seconds
            val elevationDiff = metrics[i].elevation - metrics[i-1].elevation

            // Only include rates where we have meaningful time difference
            if (timeDiff >= 1.0) {
                val ratePerMinute = (elevationDiff / timeDiff) * 60 // meters per minute
                rates.add(ratePerMinute)
                timeStamps.add(metrics[i].timeInMilliseconds)
            }
        }
        Pair(rates, timeStamps)
    }

    val changeRates = changeRatesData.first
    val timeStamps = changeRatesData.second

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (changeRates.isNotEmpty()) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val info = findClosestAltitudeChangeRatePoint(changeRates, timeStamps, offset, Size(size.width.toFloat(), size.height.toFloat()))
                                showInfo = info
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (changeRates.isEmpty()) {
                val paint = Paint().asFrameworkPaint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 16.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                drawContext.canvas.nativeCanvas.drawText(
                    "Insufficient data for rate calculation",
                    size.width / 2,
                    size.height / 2,
                    paint
                )
                return@Canvas
            }

            val padding = 60.dp.toPx()
            val graphWidth = size.width - 2 * padding
            val graphHeight = size.height - 2 * padding
            val centerY = size.height / 2

            val maxRate = changeRates.maxOf { abs(it) }.coerceAtLeast(1.0)
            val minTime = timeStamps.minOrNull() ?: 0L
            val maxTime = timeStamps.maxOrNull() ?: 1L
            val timeRange = maxTime - minTime

            // Draw axes
            drawLine(
                color = Color.Gray,
                start = Offset(padding, padding),
                end = Offset(padding, size.height - padding),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Gray,
                start = Offset(padding, size.height - padding),
                end = Offset(size.width - padding, size.height - padding),
                strokeWidth = 2.dp.toPx()
            )

            // Draw center line (zero rate)
            drawLine(
                color = Color.Gray,
                start = Offset(padding, centerY),
                end = Offset(size.width - padding, centerY),
                strokeWidth = 1.dp.toPx()
            )

            // Draw grid lines and Y-axis values (rate)
            val paint = Paint().asFrameworkPaint().apply {
                color = Color.Gray.toArgb()
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            for (i in 0..4) {
                val y = padding + (graphHeight * i / 4)
                if (i != 2) { // Skip center line
                    drawLine(
                        color = Color.LightGray,
                        start = Offset(padding, y),
                        end = Offset(size.width - padding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val rateValue = maxRate - (maxRate * 2 * i / 4)
                drawContext.canvas.nativeCanvas.drawText(
                    "${rateValue.toInt()}",
                    padding - 10.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint
                )
            }

            // Draw X-axis values (time)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (i in 0..4) {
                val x = padding + (graphWidth * i / 4)
                val timeValue = minTime + (timeRange * i / 4)
                val minutes = ((timeValue - minTime) / (1000 * 60)).toInt()
                drawContext.canvas.nativeCanvas.drawText(
                    "${minutes}m",
                    x,
                    size.height - padding + 20.dp.toPx(),
                    paint
                )
            }

            // Draw rate line
            if (changeRates.size > 1 && timeRange > 0L && maxRate > 0.0) {
                for (i in 0 until changeRates.size - 1) {
                    val startTime = timeStamps[i]
                    val endTime = timeStamps[i + 1]

                    val startX = padding + ((startTime - minTime).toDouble() / timeRange * graphWidth).toFloat()
                    val endX = padding + ((endTime - minTime).toDouble() / timeRange * graphWidth).toFloat()

                    val startY = centerY - (changeRates[i] / maxRate * centerY / 2).toFloat()
                    val endY = centerY - (changeRates[i + 1] / maxRate * centerY / 2).toFloat()

                    val rate = changeRates[i]
                    val color = when {
                        rate > 0 -> Color(0xFF4CAF50) // Climbing - green
                        rate < 0 -> Color(0xFFFF5722) // Descending - red
                        else -> Color.Gray // Flat
                    }

                    drawLine(
                        color = color,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw axis labels
            val labelPaint = Paint().asFrameworkPaint().apply {
                color = Color.Black.toArgb()
                textSize = 14.sp.toPx()
                isFakeBoldText = true
            }

            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, 20.dp.toPx(), size.height / 2)
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                "Rate (m/min)",
                20.dp.toPx(),
                size.height / 2,
                labelPaint
            )
            drawContext.canvas.nativeCanvas.restore()

            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                "Time (minutes)",
                size.width / 2,
                size.height - 5.dp.toPx(),
                labelPaint
            )
        }

        // Show info window
        showInfo?.let { info ->
            AltitudeInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Rate: ${String.format("%.1f", info.changeRate)} m/min",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Time: ${formatAltitudeTime(info.timeInMilliseconds)}",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveSpeedVsAltitudeGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<AltitudeSpeedInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestAltitudeSpeedPoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
                        showInfo = info
                    }
                }
        ) {
            if (metrics.isEmpty()) return@Canvas

            val padding = 60.dp.toPx()
            val graphWidth = size.width - 2 * padding
            val graphHeight = size.height - 2 * padding

            val minElevation = metrics.minOf { it.elevation.toDouble() }
            val maxElevation = metrics.maxOf { it.elevation.toDouble() }
            val maxSpeed = metrics.maxOf { it.speed.toDouble() }
            val elevationRange = (maxElevation - minElevation).coerceAtLeast(1.0)
            val speedRange = maxSpeed.coerceAtLeast(1.0)

            // Draw axes
            drawLine(
                color = Color.Gray,
                start = Offset(padding, padding),
                end = Offset(padding, size.height - padding),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = Color.Gray,
                start = Offset(padding, size.height - padding),
                end = Offset(size.width - padding, size.height - padding),
                strokeWidth = 2.dp.toPx()
            )

            // Draw grid lines and Y-axis values (speed)
            val paint = Paint().asFrameworkPaint().apply {
                color = Color.Gray.toArgb()
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            for (i in 0..5) {
                val y = padding + (graphHeight * i / 5)
                if (i > 0 && i < 5) {
                    drawLine(
                        color = Color.LightGray,
                        start = Offset(padding, y),
                        end = Offset(size.width - padding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val speedValue = maxSpeed - (speedRange * i / 5)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", speedValue),
                    padding - 10.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint
                )
            }

            // Draw X-axis values (elevation)
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (i in 0..4) {
                val x = padding + (graphWidth * i / 4)
                val elevationValue = minElevation + (elevationRange * i / 4)
                drawContext.canvas.nativeCanvas.drawText(
                    "${elevationValue.toInt()}",
                    x,
                    size.height - padding + 20.dp.toPx(),
                    paint
                )
            }

            // Draw data points
            metrics.forEach { metric ->
                val x = if (elevationRange > 0) {
                    padding + ((metric.elevation.toDouble() - minElevation) / elevationRange * graphWidth).toFloat()
                } else {
                    padding + graphWidth / 2
                }

                val y = if (speedRange > 0) {
                    size.height - padding - (metric.speed.toDouble() / speedRange * graphHeight).toFloat()
                } else {
                    size.height - padding - graphHeight / 2
                }

                // Color coding based on speed ranges
                val color = when {
                    metric.speed < 1f -> Color(0xFF3F51B5) // Very slow (blue)
                    metric.speed < 3f -> Color(0xFF2196F3) // Slow (light blue)
                    metric.speed < 5f -> Color(0xFF4CAF50) // Medium (green)
                    metric.speed < 8f -> Color(0xFFFF9800) // Fast (orange)
                    else -> Color(0xFFF44336) // Very fast (red)
                }

                drawCircle(
                    color = color,
                    radius = 3f,
                    center = Offset(x, y)
                )
            }

            // Draw axis labels
            val labelPaint = Paint().asFrameworkPaint().apply {
                color = Color.Black.toArgb()
                textSize = 14.sp.toPx()
                isFakeBoldText = true
            }

            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(-90f, 20.dp.toPx(), size.height / 2)
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                "Speed (km/h)",
                20.dp.toPx(),
                size.height / 2,
                labelPaint
            )
            drawContext.canvas.nativeCanvas.restore()

            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                "Elevation (m)",
                size.width / 2,
                size.height - 5.dp.toPx(),
                labelPaint
            )

            // Draw speed legend in top-right corner
            val legendStartY = 20.dp.toPx()
            val legendLineLength = 30.dp.toPx()
            val legendSpacing = 25.dp.toPx()
            val legendTextOffset = 35.dp.toPx()
            
            val speedRanges = listOf(
                Pair("< 1.0", Color(0xFF3F51B5)),     // Very slow (blue)
                Pair("1.0-3.0", Color(0xFF2196F3)),   // Slow (light blue)
                Pair("3.0-5.0", Color(0xFF4CAF50)),   // Medium (green) 
                Pair("5.0-8.0", Color(0xFFFF9800)),   // Fast (orange)
                Pair("> 8.0", Color(0xFFF44336))      // Very fast (red)
            )
            
            val legendTextPaint = Paint().asFrameworkPaint().apply {
                textSize = 12.sp.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }
            
            speedRanges.forEachIndexed { index, (range, color) ->
                val yPosition = legendStartY + (index * legendSpacing)
                
                // Draw colored line
                drawLine(
                    color = color,
                    start = Offset(size.width - 120.dp.toPx(), yPosition),
                    end = Offset(size.width - 120.dp.toPx() + legendLineLength, yPosition),
                    strokeWidth = 3.dp.toPx()
                )
                
                // Draw text label
                legendTextPaint.color = color.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    range,
                    size.width - 120.dp.toPx() + legendTextOffset,
                    yPosition + 4.dp.toPx(),
                    legendTextPaint
                )
            }
        }


        // Show info window
        showInfo?.let { info ->
            AltitudeInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Speed: ${String.format("%.1f", info.speed)} km/h",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Elevation: ${info.elevation.toInt()} m",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AltitudeInfoWindow(
    position: Offset,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    Surface(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (position.x - 60.dp.toPx()).roundToInt(),
                    y = (position.y - 80.dp.toPx()).roundToInt()
                )
            }
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures {
                    onDismiss()
                }
            },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        content()
    }
}

private fun findClosestAltitudeDistancePoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: Size
): AltitudeDistanceInfo? {
    if (metrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val maxDistance = metrics.maxOfOrNull { it.distance } ?: 1.0
    val minElevation = metrics.minOf { it.elevation.toDouble() }
    val maxElevation = metrics.maxOf { it.elevation.toDouble() }
    val elevationRange = (maxElevation - minElevation).coerceAtLeast(1.0)

    if (maxDistance == 0.0 || elevationRange == 0.0) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    metrics.forEach { metric ->
        val x = padding + (metric.distance / maxDistance * graphWidth).toFloat()
        val y = canvasSize.height - padding - ((metric.elevation.toDouble() - minElevation) / elevationRange * graphHeight).toFloat()

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + (metric.distance / maxDistance * graphWidth).toFloat()
        val y = canvasSize.height - padding - ((metric.elevation.toDouble() - minElevation) / elevationRange * graphHeight).toFloat()

        AltitudeDistanceInfo(
            elevation = metric.elevation,
            distance = metric.distance,
            position = Offset(x, y)
        )
    }
}

private fun findClosestAltitudeSpeedPoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: Size
): AltitudeSpeedInfo? {
    if (metrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minElevation = metrics.minOf { it.elevation.toDouble() }
    val maxElevation = metrics.maxOf { it.elevation.toDouble() }
    val maxSpeed = metrics.maxOf { it.speed.toDouble() }
    val elevationRange = (maxElevation - minElevation).coerceAtLeast(1.0)
    val speedRange = maxSpeed.coerceAtLeast(1.0)

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    metrics.forEach { metric ->
        val x = padding + ((metric.elevation.toDouble() - minElevation) / elevationRange * graphWidth).toFloat()
        val y = canvasSize.height - padding - (metric.speed.toDouble() / speedRange * graphHeight).toFloat()

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.elevation.toDouble() - minElevation) / elevationRange * graphWidth).toFloat()
        val y = canvasSize.height - padding - (metric.speed.toDouble() / speedRange * graphHeight).toFloat()

        AltitudeSpeedInfo(
            elevation = metric.elevation,
            speed = metric.speed,
            position = Offset(x, y)
        )
    }
}

private fun findClosestAltitudeChangeRatePoint(
    changeRates: List<Double>,
    timeStamps: List<Long>,
    clickOffset: Offset,
    canvasSize: Size
): AltitudeChangeRateInfo? {
    if (changeRates.isEmpty() || timeStamps.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val centerY = canvasSize.height / 2

    val maxRate = changeRates.maxOf { abs(it) }.coerceAtLeast(1.0)
    val minTime = timeStamps.minOrNull() ?: 0L
    val maxTime = timeStamps.maxOrNull() ?: 1L
    val timeRange = maxTime - minTime

    if (timeRange == 0L || maxRate == 0.0) return null

    var closestIndex = 0
    var minDistanceToPoint = Float.MAX_VALUE

    changeRates.forEachIndexed { index, rate ->
        val time = timeStamps[index]
        val x = padding + ((time - minTime).toDouble() / timeRange * graphWidth).toFloat()
        val y = centerY - (rate / maxRate * centerY / 2).toFloat()

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestIndex = index
        }
    }

    val time = timeStamps[closestIndex]
    val x = padding + ((time - minTime).toDouble() / timeRange * graphWidth).toFloat()
    val y = centerY - (changeRates[closestIndex] / maxRate * centerY / 2).toFloat()

    return AltitudeChangeRateInfo(
        changeRate = changeRates[closestIndex],
        timeInMilliseconds = time - minTime,
        position = Offset(x, y)
    )
}

private fun formatAltitudeTime(elapsedTimeInMilliseconds: Long): String {
    val totalMinutes = (elapsedTimeInMilliseconds / (1000 * 60))
    val seconds = (elapsedTimeInMilliseconds / 1000) % 60
    return String.format("%d:%02d", totalMinutes, seconds)
}