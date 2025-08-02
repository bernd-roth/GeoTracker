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
import at.co.netconsulting.geotracker.data.BarometerAltitudeInfo
import at.co.netconsulting.geotracker.data.BarometerDistanceInfo
import at.co.netconsulting.geotracker.data.BarometerTimeInfo
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.tools.BarometerUtils
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarometerDetailScreen(
    eventName: String,
    metrics: List<Metric>,
    onBackClick: () -> Unit
) {
    // Filter metrics that have pressure data
    val validMetrics = metrics.filter { metric ->
        val pressure = metric.pressure
        pressure != null && pressure > 0f && BarometerUtils.isRealisticPressure(pressure)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Barometer Analysis",
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
                            text = "No barometer data available for this event",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                // Barometer Statistics Summary
                BarometerStatsSummary(validMetrics)

                // Pressure vs Time Graph
                BarometerGraphCard(
                    title = "Pressure vs Time",
                    description = "Tap on the graph to see pressure at any point in time"
                ) {
                    InteractivePressureVsTimeGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Pressure vs Distance Graph
                BarometerGraphCard(
                    title = "Pressure vs Distance",
                    description = "Tap on the graph to see pressure at any distance"
                ) {
                    InteractivePressureVsDistanceGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Pressure vs Altitude Scatter Plot
                BarometerGraphCard(
                    title = "Pressure vs Altitude",
                    description = "Tap on the graph to see pressure at different elevations"
                ) {
                    InteractivePressureVsAltitudeScatterPlot(
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
fun BarometerStatsSummary(metrics: List<Metric>) {
    val pressures = metrics.mapNotNull { it.pressure?.let { pressure -> if (pressure > 0f && BarometerUtils.isRealisticPressure(pressure)) it.pressure else null } }
    val minPressure = pressures.minOrNull() ?: 0f
    val maxPressure = pressures.maxOrNull() ?: 0f
    val avgPressure = if (pressures.isNotEmpty()) pressures.sum() / pressures.size else 0f

    // Calculate pressure trend
    val pressureTrend = if (pressures.size > 10) {
        val firstHalf = pressures.take(pressures.size / 2).average().toFloat()
        val secondHalf = pressures.drop(pressures.size / 2).average().toFloat()
        val change = secondHalf - firstHalf
        BarometerUtils.getWeatherTrend(secondHalf, firstHalf)
    } else {
        "Insufficient data for trend analysis"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Pressure Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BarometerStatItem(
                    label = "Min",
                    value = String.format("%.1f hPa", minPressure),
                    color = Color.Blue
                )
                BarometerStatItem(
                    label = "Average",
                    value = String.format("%.1f hPa", avgPressure),
                    color = MaterialTheme.colorScheme.primary
                )
                BarometerStatItem(
                    label = "Max",
                    value = String.format("%.1f hPa", maxPressure),
                    color = Color.Red
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pressure Trend: $pressureTrend",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun BarometerStatItem(label: String, value: String, color: Color) {
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
fun BarometerGraphCard(
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
fun InteractivePressureVsTimeGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<BarometerTimeInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestBarometerTimePoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
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
            val minPressure = metrics.minOf { it.pressure!! }
            val maxPressure = metrics.maxOf { it.pressure!! }

            val timeRange = maxTime - minTime
            val pressureRange = maxPressure - minPressure

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

            // Draw grid lines and Y-axis values (pressure)
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

                val pressureValue = maxPressure - (pressureRange * i / 5)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", pressureValue),
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

            // Draw pressure line
            if (metrics.size > 1 && timeRange > 0L && pressureRange > 0f) {
                val path = androidx.compose.ui.graphics.Path()

                metrics.forEachIndexed { index, metric ->
                    val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
                    val y = size.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFF2196F3), // Blue color for pressure
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
                "Pressure (hPa)",
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
            BarometerInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Pressure: ${String.format("%.1f hPa", info.pressure)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Time: ${formatBarometerTime(info.timeInMilliseconds)}",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InteractivePressureVsDistanceGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<BarometerDistanceInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestBarometerDistancePoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
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
            val minPressure = metrics.minOf { it.pressure!! }
            val maxPressure = metrics.maxOf { it.pressure!! }

            val distanceRange = maxDistance - minDistance
            val pressureRange = maxPressure - minPressure

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

            // Draw grid lines and Y-axis values (pressure)
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

                val pressureValue = maxPressure - (pressureRange * i / 5)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", pressureValue),
                    padding - 10.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint
                )
            }

            // Draw X-axis values (distance)
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

            // Draw pressure line
            if (metrics.size > 1 && distanceRange > 0.0 && pressureRange > 0f) {
                val path = androidx.compose.ui.graphics.Path()

                metrics.forEachIndexed { index, metric ->
                    val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
                    val y = size.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFF2196F3), // Blue color for pressure
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
                "Pressure (hPa)",
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
            BarometerInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Pressure: ${String.format("%.1f hPa", info.pressure)}",
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
fun InteractivePressureVsAltitudeScatterPlot(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<BarometerAltitudeInfo?>(null) }

    // Filter metrics that have both pressure and elevation data
    val validMetrics = remember(metrics) {
        metrics.filter { metric ->
            val pressure = metric.pressure
            pressure != null && pressure > 0f && BarometerUtils.isRealisticPressure(pressure) && metric.elevation > 0f
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (validMetrics.isNotEmpty()) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val info = findClosestBarometerAltitudePoint(validMetrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
                                showInfo = info
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
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
            val minPressure = validMetrics.minOf { it.pressure!! }
            val maxPressure = validMetrics.maxOf { it.pressure!! }

            val elevationRange = maxElevation - minElevation
            val pressureRange = maxPressure - minPressure

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

            // Y-axis grid lines and values (pressure)
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

                val pressureValue = maxPressure - (pressureRange * i / 5)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f", pressureValue),
                    padding - 10.dp.toPx(),
                    y + 4.dp.toPx(),
                    paint
                )
            }

            // X-axis grid lines and values (elevation)
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

                val y = if (pressureRange > 0) {
                    size.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)
                } else {
                    size.height - padding - graphHeight / 2
                }

                // Color coding based on pressure ranges (atmospheric pressure classification)
                val color = when {
                    metric.pressure!! < 980f -> Color(0xFF3F51B5) // Very low pressure (deep blue)
                    metric.pressure!! < 1000f -> Color(0xFF2196F3) // Low pressure (blue)
                    metric.pressure!! < 1020f -> Color(0xFF4CAF50) // Normal pressure (green)
                    metric.pressure!! < 1040f -> Color(0xFFFF9800) // High pressure (orange)
                    else -> Color(0xFFF44336) // Very high pressure (red)
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
                "Pressure (hPa)",
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
                textSize = 10.sp.toPx()
            }

            val legendX = size.width - 120.dp.toPx()
            val legendY = padding + 15.dp.toPx()

            drawCircle(color = Color(0xFFF44336), radius = 4.dp.toPx(), center = Offset(legendX, legendY))
            drawContext.canvas.nativeCanvas.drawText("1040+ hPa", legendX + 10.dp.toPx(), legendY + 3.dp.toPx(), legendPaint)

            drawCircle(color = Color(0xFFFF9800), radius = 4.dp.toPx(), center = Offset(legendX, legendY + 15.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("1020-1040", legendX + 10.dp.toPx(), legendY + 18.dp.toPx(), legendPaint)

            drawCircle(color = Color(0xFF4CAF50), radius = 4.dp.toPx(), center = Offset(legendX, legendY + 30.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("1000-1020", legendX + 10.dp.toPx(), legendY + 33.dp.toPx(), legendPaint)

            drawCircle(color = Color(0xFF2196F3), radius = 4.dp.toPx(), center = Offset(legendX, legendY + 45.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("980-1000", legendX + 10.dp.toPx(), legendY + 48.dp.toPx(), legendPaint)

            drawCircle(color = Color(0xFF3F51B5), radius = 4.dp.toPx(), center = Offset(legendX, legendY + 60.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("<980 hPa", legendX + 10.dp.toPx(), legendY + 63.dp.toPx(), legendPaint)
        }

        // Show info window
        showInfo?.let { info ->
            BarometerInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Pressure: ${String.format("%.1f hPa", info.pressure)}",
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
fun BarometerInfoWindow(
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

// Helper functions to find closest points for barometer data
private fun findClosestBarometerTimePoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: Size
): BarometerTimeInfo? {
    if (metrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minTime = metrics.minOf { it.timeInMilliseconds }
    val maxTime = metrics.maxOf { it.timeInMilliseconds }
    val minPressure = metrics.minOf { it.pressure!! }
    val maxPressure = metrics.maxOf { it.pressure!! }

    val timeRange = maxTime - minTime
    val pressureRange = maxPressure - minPressure

    if (timeRange == 0L || pressureRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    metrics.forEach { metric ->
        val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)

        BarometerTimeInfo(
            pressure = metric.pressure!!,
            timeInMilliseconds = metric.timeInMilliseconds - minTime,
            position = Offset(x, y)
        )
    }
}

private fun findClosestBarometerDistancePoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: Size
): BarometerDistanceInfo? {
    if (metrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minDistance = metrics.minOf { it.distance }
    val maxDistance = metrics.maxOf { it.distance }
    val minPressure = metrics.minOf { it.pressure!! }
    val maxPressure = metrics.maxOf { it.pressure!! }

    val distanceRange = maxDistance - minDistance
    val pressureRange = maxPressure - minPressure

    if (distanceRange == 0.0 || pressureRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    metrics.forEach { metric ->
        val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
        val y = canvasSize.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
        val y = canvasSize.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)

        BarometerDistanceInfo(
            pressure = metric.pressure!!,
            distance = metric.distance,
            position = Offset(x, y)
        )
    }
}

private fun findClosestBarometerAltitudePoint(
    validMetrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: Size
): BarometerAltitudeInfo? {
    if (validMetrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minElevation = validMetrics.minOf { it.elevation }
    val maxElevation = validMetrics.maxOf { it.elevation }
    val minPressure = validMetrics.minOf { it.pressure!! }
    val maxPressure = validMetrics.maxOf { it.pressure!! }

    val elevationRange = maxElevation - minElevation
    val pressureRange = maxPressure - minPressure

    if (elevationRange == 0f || pressureRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    validMetrics.forEach { metric ->
        val x = padding + ((metric.elevation - minElevation) / elevationRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.elevation - minElevation) / elevationRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.pressure!! - minPressure) / pressureRange * graphHeight)

        BarometerAltitudeInfo(
            pressure = metric.pressure!!,
            elevation = metric.elevation,
            position = Offset(x, y)
        )
    }
}

private fun formatBarometerTime(elapsedTimeInMilliseconds: Long): String {
    val totalMinutes = (elapsedTimeInMilliseconds / (1000 * 60))
    val seconds = (elapsedTimeInMilliseconds / 1000) % 60
    return String.format("%d:%02d", totalMinutes, seconds)
}