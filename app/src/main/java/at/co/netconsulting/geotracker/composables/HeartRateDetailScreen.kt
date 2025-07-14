package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.Metric
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Data classes for info windows
data class DistanceInfo(
    val heartRate: Int,
    val distance: Double,
    val position: Offset
)

data class TimeInfo(
    val heartRate: Int,
    val timeInMilliseconds: Long,
    val position: Offset
)

data class AltitudeInfo(
    val heartRate: Int,
    val elevation: Float,
    val position: Offset
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateDetailScreen(
    eventName: String,
    metrics: List<Metric>,
    onBackClick: () -> Unit
) {
    // Filter metrics that have heart rate data
    val validMetrics = metrics.filter { it.heartRate > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Heart Rate Analysis",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Event name header
            Text(
                text = eventName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (validMetrics.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No heart rate data available for this event",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                // Heart Rate Statistics Summary
                HeartRateStatsSummary(validMetrics)

                // Heart Rate vs Distance Graph
                GraphCard(
                    title = "Heart Rate vs Distance",
                    description = "Tap on the graph to see detailed information at any point"
                ) {
                    InteractiveHeartRateVsDistanceGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Heart Rate vs Time Graph
                GraphCard(
                    title = "Heart Rate vs Time",
                    description = "Tap on the graph to see detailed information at any point"
                ) {
                    InteractiveHeartRateVsTimeGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Heart Rate vs Altitude Scatter Plot
                GraphCard(
                    title = "Heart Rate vs Altitude",
                    description = "Tap on the graph to see detailed information at any point"
                ) {
                    InteractiveHeartRateVsAltitudeScatterPlot(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeartRateStatsSummary(metrics: List<Metric>) {
    val heartRates = metrics.map { it.heartRate }
    val minHR = heartRates.minOrNull() ?: 0
    val maxHR = heartRates.maxOrNull() ?: 0
    val avgHR = if (heartRates.isNotEmpty()) heartRates.average().toInt() else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Heart Rate Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Min",
                    value = "$minHR bpm",
                    color = Color.Blue
                )
                StatItem(
                    label = "Average",
                    value = "$avgHR bpm",
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    label = "Max",
                    value = "$maxHR bpm",
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun GraphCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun InteractiveHeartRateVsDistanceGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<DistanceInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestDistancePoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
                        showInfo = info
                    }
                }
        ) {
            if (metrics.isEmpty()) return@Canvas

            val padding = 60.dp.toPx()
            val graphWidth = size.width - 2 * padding
            val graphHeight = size.height - 2 * padding

            val minDistance = metrics.minOf { it.distance }
            val maxDistance = metrics.maxOf { it.distance }
            val minHeartRate = metrics.minOf { it.heartRate }.toFloat()
            val maxHeartRate = metrics.maxOf { it.heartRate }.toFloat()

            val distanceRange = maxDistance - minDistance
            val heartRateRange = maxHeartRate - minHeartRate

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

            // Draw grid lines and Y-axis values
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

                val heartRateValue = (maxHeartRate - (heartRateRange * i / 5)).toInt()
                drawContext.canvas.nativeCanvas.drawText(
                    "$heartRateValue",
                    padding - 10.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint
                )
            }

            // Draw X-axis values
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (i in 0..4) {
                val x = padding + (graphWidth * i / 4)
                val distanceValue = minDistance + (distanceRange * i / 4)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", distanceValue / 1000),
                    x,
                    size.height - padding + 20.dp.toPx(),
                    paint
                )
            }

            // Draw heart rate line
            if (metrics.size > 1) {
                val path = androidx.compose.ui.graphics.Path()

                metrics.forEachIndexed { index, metric ->
                    val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
                    val y = size.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color.Red,
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
                "Heart Rate (bpm)",
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
            InfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Heart Rate: ${info.heartRate} bpm",
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
fun InteractiveHeartRateVsTimeGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<TimeInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestTimePoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
                        showInfo = info
                    }
                }
        ) {
            if (metrics.isEmpty()) return@Canvas

            val padding = 60.dp.toPx()
            val graphWidth = size.width - 2 * padding
            val graphHeight = size.height - 2 * padding

            val minTime = metrics.minOf { it.timeInMilliseconds }
            val maxTime = metrics.maxOf { it.timeInMilliseconds }
            val minHeartRate = metrics.minOf { it.heartRate }.toFloat()
            val maxHeartRate = metrics.maxOf { it.heartRate }.toFloat()

            val timeRange = maxTime - minTime
            val heartRateRange = maxHeartRate - minHeartRate

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

            // Draw grid lines and Y-axis values
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

                val heartRateValue = (maxHeartRate - (heartRateRange * i / 5)).toInt()
                drawContext.canvas.nativeCanvas.drawText(
                    "$heartRateValue",
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

            // Draw heart rate line
            if (metrics.size > 1 && timeRange > 0) {
                val path = androidx.compose.ui.graphics.Path()

                metrics.forEachIndexed { index, metric ->
                    val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
                    val y = size.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color.Blue,
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
                "Heart Rate (bpm)",
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
            InfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Heart Rate: ${info.heartRate} bpm",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Time: ${formatTime(info.timeInMilliseconds)}",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveHeartRateVsAltitudeScatterPlot(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<AltitudeInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestAltitudePoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
                        showInfo = info
                    }
                }
        ) {
            // Filter metrics that have both heart rate and elevation data
            val validMetrics = metrics.filter { it.heartRate > 0 && it.elevation > 0 }

            if (validMetrics.isEmpty()) {
                val paint = Paint().asFrameworkPaint().apply {
                    color = Color.Gray.toArgb()
                    textSize = 16.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                drawContext.canvas.nativeCanvas.drawText(
                    "No elevation data available",
                    size.width / 2,
                    size.height / 2,
                    paint
                )
                return@Canvas
            }

            val padding = 60.dp.toPx()
            val graphWidth = size.width - 2 * padding
            val graphHeight = size.height - 2 * padding

            val minElevation = validMetrics.minOf { it.elevation }
            val maxElevation = validMetrics.maxOf { it.elevation }
            val minHeartRate = validMetrics.minOf { it.heartRate }.toFloat()
            val maxHeartRate = validMetrics.maxOf { it.heartRate }.toFloat()

            val elevationRange = maxElevation - minElevation
            val heartRateRange = maxHeartRate - minHeartRate

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

            // Draw grid lines and axis values
            val paint = Paint().asFrameworkPaint().apply {
                color = Color.Gray.toArgb()
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            // Y-axis grid lines and values
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

                val heartRateValue = (maxHeartRate - (heartRateRange * i / 5)).toInt()
                drawContext.canvas.nativeCanvas.drawText(
                    "$heartRateValue",
                    padding - 10.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint
                )
            }

            // X-axis grid lines and values
            paint.textAlign = android.graphics.Paint.Align.CENTER
            for (i in 0..4) {
                val x = padding + (graphWidth * i / 4)
                if (i > 0 && i < 4) {
                    drawLine(
                        color = Color.LightGray,
                        start = Offset(x, padding),
                        end = Offset(x, size.height - padding),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val elevationValue = minElevation + (elevationRange * i / 4)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.0f", elevationValue),
                    x,
                    size.height - padding + 20.dp.toPx(),
                    paint
                )
            }

            // Draw scatter points
            validMetrics.forEach { metric ->
                val x = if (elevationRange > 0) {
                    padding + ((metric.elevation - minElevation) / elevationRange * graphWidth)
                } else {
                    padding + graphWidth / 2
                }

                val y = if (heartRateRange > 0) {
                    size.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)
                } else {
                    size.height - padding - graphHeight / 2
                }

                val color = when {
                    metric.heartRate < (minHeartRate + heartRateRange * 0.3) -> Color.Green
                    metric.heartRate < (minHeartRate + heartRateRange * 0.7) -> Color.Yellow
                    else -> Color.Red
                }

                drawCircle(
                    color = color,
                    radius = 4.dp.toPx(),
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
                "Heart Rate (bpm)",
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

            // Draw legend
            val legendPaint = Paint().asFrameworkPaint().apply {
                color = Color.Black.toArgb()
                textSize = 12.sp.toPx()
            }

            val legendX = size.width - 120.dp.toPx()
            val legendY = padding + 20.dp.toPx()

            drawCircle(color = Color.Red, radius = 6.dp.toPx(), center = Offset(legendX, legendY))
            drawContext.canvas.nativeCanvas.drawText("High HR", legendX + 15.dp.toPx(), legendY + 4.dp.toPx(), legendPaint)

            drawCircle(color = Color.Yellow, radius = 6.dp.toPx(), center = Offset(legendX, legendY + 20.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("Med HR", legendX + 15.dp.toPx(), legendY + 24.dp.toPx(), legendPaint)

            drawCircle(color = Color.Green, radius = 6.dp.toPx(), center = Offset(legendX, legendY + 40.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("Low HR", legendX + 15.dp.toPx(), legendY + 44.dp.toPx(), legendPaint)
        }

        // Show info window
        showInfo?.let { info ->
            InfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Heart Rate: ${info.heartRate} bpm",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Elevation: ${String.format("%.1f", info.elevation)} m",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InfoWindow(
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

// Helper functions to find closest points
private fun findClosestDistancePoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size
): DistanceInfo? {
    if (metrics.isEmpty()) return null

    val padding = 60 * 3f // Convert to px (approximately)
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minDistance = metrics.minOf { it.distance }
    val maxDistance = metrics.maxOf { it.distance }
    val minHeartRate = metrics.minOf { it.heartRate }.toFloat()
    val maxHeartRate = metrics.maxOf { it.heartRate }.toFloat()

    val distanceRange = maxDistance - minDistance
    val heartRateRange = maxHeartRate - minHeartRate

    if (distanceRange == 0.0 || heartRateRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    metrics.forEach { metric ->
        val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
        val y = canvasSize.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)

        val distance = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distance < minDistanceToPoint) {
            minDistanceToPoint = distance
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
        val y = canvasSize.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)

        DistanceInfo(
            heartRate = metric.heartRate,
            distance = metric.distance,
            position = Offset(x, y)
        )
    }
}

private fun findClosestTimePoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size
): TimeInfo? {
    if (metrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minTime = metrics.minOf { it.timeInMilliseconds }
    val maxTime = metrics.maxOf { it.timeInMilliseconds }
    val minHeartRate = metrics.minOf { it.heartRate }.toFloat()
    val maxHeartRate = metrics.maxOf { it.heartRate }.toFloat()

    val timeRange = maxTime - minTime
    val heartRateRange = maxHeartRate - minHeartRate

    if (timeRange == 0L || heartRateRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    metrics.forEach { metric ->
        val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)

        val distance = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distance < minDistanceToPoint) {
            minDistanceToPoint = distance
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)

        TimeInfo(
            heartRate = metric.heartRate,
            timeInMilliseconds = metric.timeInMilliseconds,
            position = Offset(x, y)
        )
    }
}

private fun findClosestAltitudePoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: androidx.compose.ui.geometry.Size
): AltitudeInfo? {
    val validMetrics = metrics.filter { it.heartRate > 0 && it.elevation > 0 }
    if (validMetrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minElevation = validMetrics.minOf { it.elevation }
    val maxElevation = validMetrics.maxOf { it.elevation }
    val minHeartRate = validMetrics.minOf { it.heartRate }.toFloat()
    val maxHeartRate = validMetrics.maxOf { it.heartRate }.toFloat()

    val elevationRange = maxElevation - minElevation
    val heartRateRange = maxHeartRate - minHeartRate

    if (elevationRange == 0f || heartRateRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    validMetrics.forEach { metric ->
        val x = padding + ((metric.elevation - minElevation) / elevationRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)

        val distance = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distance < minDistanceToPoint) {
            minDistanceToPoint = distance
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.elevation - minElevation) / elevationRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.heartRate - minHeartRate) / heartRateRange * graphHeight)

        AltitudeInfo(
            heartRate = metric.heartRate,
            elevation = metric.elevation,
            position = Offset(x, y)
        )
    }
}

private fun formatTime(timeInMilliseconds: Long): String {
    val minutes = (timeInMilliseconds / (1000 * 60)) % 60
    val seconds = (timeInMilliseconds / 1000) % 60
    return String.format("%02d:%02d", minutes, seconds)
}