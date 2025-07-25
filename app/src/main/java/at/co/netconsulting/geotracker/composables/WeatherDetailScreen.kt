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
import at.co.netconsulting.geotracker.data.WeatherAltitudeInfo
import at.co.netconsulting.geotracker.data.WeatherDistanceInfo
import at.co.netconsulting.geotracker.data.WeatherTimeInfo
import at.co.netconsulting.geotracker.domain.Metric
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(
    eventName: String,
    metrics: List<Metric>,
    onBackClick: () -> Unit
) {
    // Filter metrics that have temperature data
    val validMetrics = metrics.filter { metric ->
        val temp = metric.temperature
        temp != null && temp > 0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Weather Analysis",
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
                            text = "No weather data available for this event",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                // Weather Statistics Summary
                WeatherStatsSummary(validMetrics)

                // Temperature vs Time Graph
                WeatherGraphCard(
                    title = "Temperature vs Time",
                    description = "Tap on the graph to see temperature at any point in time"
                ) {
                    InteractiveTemperatureVsTimeGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Temperature vs Distance Graph
                WeatherGraphCard(
                    title = "Temperature vs Distance",
                    description = "Tap on the graph to see temperature at any distance"
                ) {
                    InteractiveTemperatureVsDistanceGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Temperature vs Altitude Scatter Plot
                WeatherGraphCard(
                    title = "Temperature vs Altitude",
                    description = "Tap on the graph to see temperature at different elevations"
                ) {
                    InteractiveTemperatureVsAltitudeScatterPlot(
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
fun WeatherStatsSummary(metrics: List<Metric>) {
    val temperatures = metrics.mapNotNull { it.temperature?.let { it1 -> if (it1 > 0f) it.temperature else null } }
    val minTemp = temperatures.minOrNull() ?: 0f
    val maxTemp = temperatures.maxOrNull() ?: 0f
    val avgTemp = if (temperatures.isNotEmpty()) temperatures.sum() / temperatures.size else 0f

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
                text = "Temperature Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                WeatherStatItem(
                    label = "Min",
                    value = String.format("%.1f°C", minTemp),
                    color = Color.Blue
                )
                WeatherStatItem(
                    label = "Average",
                    value = String.format("%.1f°C", avgTemp),
                    color = MaterialTheme.colorScheme.primary
                )
                WeatherStatItem(
                    label = "Max",
                    value = String.format("%.1f°C", maxTemp),
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
fun WeatherStatItem(label: String, value: String, color: Color) {
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
fun WeatherGraphCard(
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
fun InteractiveTemperatureVsTimeGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<WeatherTimeInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestWeatherTimePoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
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
            val minTemp = metrics.minOf { it.temperature!! }
            val maxTemp = metrics.maxOf { it.temperature!! }

            val timeRange = maxTime - minTime
            val tempRange = maxTemp - minTemp

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

            // Draw grid lines and Y-axis values (temperature)
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

                val tempValue = maxTemp - (tempRange * i / 5)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f°C", tempValue),
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

            // Draw temperature line
            if (metrics.size > 1 && timeRange > 0L && tempRange > 0f) {
                val path = androidx.compose.ui.graphics.Path()

                metrics.forEachIndexed { index, metric ->
                    val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
                    val y = size.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFFFF6B35), // Orange color for temperature
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
                "Temperature (°C)",
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
            WeatherInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Temperature: ${String.format("%.1f°C", info.temperature)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Time: ${formatWeatherTime(info.timeInMilliseconds)}",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveTemperatureVsDistanceGraph(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<WeatherDistanceInfo?>(null) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val info = findClosestWeatherDistancePoint(metrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
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
            val minTemp = metrics.minOf { it.temperature!! }
            val maxTemp = metrics.maxOf { it.temperature!! }

            val distanceRange = maxDistance - minDistance
            val tempRange = maxTemp - minTemp

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

            // Draw grid lines and Y-axis values (temperature)
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

                val tempValue = maxTemp - (tempRange * i / 5)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f°C", tempValue),
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

            // Draw temperature line
            if (metrics.size > 1 && distanceRange > 0.0 && tempRange > 0f) {
                val path = androidx.compose.ui.graphics.Path()

                metrics.forEachIndexed { index, metric ->
                    val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
                    val y = size.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFFFF6B35), // Orange color for temperature
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
                "Temperature (°C)",
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
            WeatherInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Temperature: ${String.format("%.1f°C", info.temperature)}",
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
fun InteractiveTemperatureVsAltitudeScatterPlot(
    metrics: List<Metric>,
    modifier: Modifier = Modifier
) {
    var showInfo by remember { mutableStateOf<WeatherAltitudeInfo?>(null) }

    // Filter metrics that have both temperature and elevation data
    val validMetrics = remember(metrics) {
        metrics.filter { metric ->
            val temp = metric.temperature
            temp != null && temp > 0f && metric.elevation > 0f
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
                                val info = findClosestWeatherAltitudePoint(validMetrics, offset, Size(size.width.toFloat(), size.height.toFloat()))
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
            val minTemp = validMetrics.minOf { it.temperature!! }
            val maxTemp = validMetrics.maxOf { it.temperature!! }

            val elevationRange = maxElevation - minElevation
            val tempRange = maxTemp - minTemp

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

            // Y-axis grid lines and values (temperature)
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

                val tempValue = maxTemp - (tempRange * i / 5)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format("%.1f°C", tempValue),
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

                val y = if (tempRange > 0) {
                    size.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)
                } else {
                    size.height - padding - graphHeight / 2
                }

                // Color coding based on temperature ranges
                val color = when {
                    metric.temperature!! < 5f -> Color.Blue // Cold
                    metric.temperature!! < 15f -> Color.Cyan // Cool
                    metric.temperature!! < 25f -> Color.Green // Moderate
                    metric.temperature!! < 30f -> Color.Yellow // Warm
                    else -> Color.Red // Hot
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
                "Temperature (°C)",
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

            drawCircle(color = Color.Red, radius = 4.dp.toPx(), center = Offset(legendX, legendY))
            drawContext.canvas.nativeCanvas.drawText("30°C+", legendX + 10.dp.toPx(), legendY + 3.dp.toPx(), legendPaint)

            drawCircle(color = Color.Yellow, radius = 4.dp.toPx(), center = Offset(legendX, legendY + 15.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("25-30°C", legendX + 10.dp.toPx(), legendY + 18.dp.toPx(), legendPaint)

            drawCircle(color = Color.Green, radius = 4.dp.toPx(), center = Offset(legendX, legendY + 30.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("15-25°C", legendX + 10.dp.toPx(), legendY + 33.dp.toPx(), legendPaint)

            drawCircle(color = Color.Cyan, radius = 4.dp.toPx(), center = Offset(legendX, legendY + 45.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("5-15°C", legendX + 10.dp.toPx(), legendY + 48.dp.toPx(), legendPaint)

            drawCircle(color = Color.Blue, radius = 4.dp.toPx(), center = Offset(legendX, legendY + 60.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText("<5°C", legendX + 10.dp.toPx(), legendY + 63.dp.toPx(), legendPaint)
        }

        // Show info window
        showInfo?.let { info ->
            WeatherInfoWindow(
                position = info.position,
                onDismiss = { showInfo = null }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Temperature: ${String.format("%.1f°C", info.temperature)}",
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
fun WeatherInfoWindow(
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

// Helper functions to find closest points for weather data
private fun findClosestWeatherTimePoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: Size
): WeatherTimeInfo? {
    if (metrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minTime = metrics.minOf { it.timeInMilliseconds }
    val maxTime = metrics.maxOf { it.timeInMilliseconds }
    val minTemp = metrics.minOf { it.temperature!! }
    val maxTemp = metrics.maxOf { it.temperature!! }

    val timeRange = maxTime - minTime
    val tempRange = maxTemp - minTemp

    if (timeRange == 0L || tempRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    metrics.forEach { metric ->
        val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.timeInMilliseconds - minTime).toFloat() / timeRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)

        WeatherTimeInfo(
            temperature = metric.temperature!!,
            timeInMilliseconds = metric.timeInMilliseconds - minTime, // Calculate elapsed time from start
            position = Offset(x, y)
        )
    }
}

private fun findClosestWeatherDistancePoint(
    metrics: List<Metric>,
    clickOffset: Offset,
    canvasSize: Size
): WeatherDistanceInfo? {
    if (metrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minDistance = metrics.minOf { it.distance }
    val maxDistance = metrics.maxOf { it.distance }
    val minTemp = metrics.minOf { it.temperature!! }
    val maxTemp = metrics.maxOf { it.temperature!! }

    val distanceRange = maxDistance - minDistance
    val tempRange = maxTemp - minTemp

    if (distanceRange == 0.0 || tempRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    metrics.forEach { metric ->
        val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
        val y = canvasSize.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.distance - minDistance) / distanceRange * graphWidth).toFloat()
        val y = canvasSize.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)

        WeatherDistanceInfo(
            temperature = metric.temperature!!,
            distance = metric.distance,
            position = Offset(x, y)
        )
    }
}

private fun findClosestWeatherAltitudePoint(
    validMetrics: List<Metric>, // Already filtered for temperature > 0 && elevation > 0
    clickOffset: Offset,
    canvasSize: Size
): WeatherAltitudeInfo? {
    if (validMetrics.isEmpty()) return null

    val padding = 60 * 3f
    val graphWidth = canvasSize.width - 2 * padding
    val graphHeight = canvasSize.height - 2 * padding

    val minElevation = validMetrics.minOf { it.elevation }
    val maxElevation = validMetrics.maxOf { it.elevation }
    val minTemp = validMetrics.minOf { it.temperature!! }
    val maxTemp = validMetrics.maxOf { it.temperature!! }

    val elevationRange = maxElevation - minElevation
    val tempRange = maxTemp - minTemp

    if (elevationRange == 0f || tempRange == 0f) return null

    var closestMetric: Metric? = null
    var minDistanceToPoint = Float.MAX_VALUE

    validMetrics.forEach { metric ->
        val x = padding + ((metric.elevation - minElevation) / elevationRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)

        val distanceToPoint = sqrt((clickOffset.x - x) * (clickOffset.x - x) + (clickOffset.y - y) * (clickOffset.y - y))

        if (distanceToPoint < minDistanceToPoint) {
            minDistanceToPoint = distanceToPoint
            closestMetric = metric
        }
    }

    return closestMetric?.let { metric ->
        val x = padding + ((metric.elevation - minElevation) / elevationRange * graphWidth)
        val y = canvasSize.height - padding - ((metric.temperature!! - minTemp) / tempRange * graphHeight)

        WeatherAltitudeInfo(
            temperature = metric.temperature!!,
            elevation = metric.elevation,
            position = Offset(x, y)
        )
    }
}

private fun formatWeatherTime(elapsedTimeInMilliseconds: Long): String {
    val totalMinutes = (elapsedTimeInMilliseconds / (1000 * 60))
    val seconds = (elapsedTimeInMilliseconds / 1000) % 60
    return String.format("%d:%02d", totalMinutes, seconds)
}