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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

private const val DETAIL_MIN_SPEED = 0.5f
private const val DETAIL_MAX_PACE = 20f

private val PaceGreen  = Color(0xFF4CAF50)
private val PaceOrange = Color(0xFFFF9800)
private val PaceTeal   = Color(0xFF80CBC4)

private fun Float.toPaceLabel(): String {
    val totalSeconds = (this * 60).roundToInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun Long.toElapsedLabel(): String {
    val min = (this / 60_000L).toInt()
    val sec = ((this % 60_000L) / 1000L).toInt()
    return "${min}min ${sec}s"
}

private fun paceMetricsFrom(metrics: List<Metric>): List<Pair<Metric, Float>> =
    metrics
        .filter { it.speed >= DETAIL_MIN_SPEED }
        .map { it to (60f / it.speed).coerceAtMost(DETAIL_MAX_PACE) }

private fun percentileRange(values: List<Float>, lo: Double = 0.05, hi: Double = 0.95): Pair<Float, Float> {
    val sorted = values.sorted()
    val p5  = sorted[(sorted.size * lo).toInt()]
    val p95 = sorted[((sorted.size * hi).toInt()).coerceAtMost(sorted.size - 1)]
    return p5 to p95
}

/** From the pace-metric pairs, find the one with the actual minimum pace (excluding cap artefacts). */
private fun findBestPair(paceMetrics: List<Pair<Metric, Float>>): Pair<Metric, Float>? =
    paceMetrics.filter { it.second < DETAIL_MAX_PACE }.minByOrNull { it.second }

/** Find the pair closest to the 95th-percentile pace (i.e. the typical slowest point). */
private fun findSlowestPair(paceMetrics: List<Pair<Metric, Float>>): Pair<Metric, Float>? {
    val uncapped = paceMetrics.filter { it.second < DETAIL_MAX_PACE }
    if (uncapped.isEmpty()) return null
    val (_, p95) = percentileRange(uncapped.map { it.second })
    return uncapped.minByOrNull { abs(it.second - p95) }
}

// ── Detail screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaceDetailScreen(
    eventName: String,
    metrics: List<Metric>,
    onBackClick: () -> Unit
) {
    val paceMetrics = paceMetricsFrom(metrics)
    val bestPair    = remember(paceMetrics) { findBestPair(paceMetrics) }
    val slowestPair = remember(paceMetrics) { findSlowestPair(paceMetrics) }
    val minTime     = remember(paceMetrics) { paceMetrics.minOfOrNull { it.first.timeInMilliseconds } ?: 0L }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Pace Analysis", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
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
            Text(
                text = eventName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (paceMetrics.size < 2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No pace data available for this event",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                PaceStatsSummary(paceMetrics)

                PaceHighlightsCard(
                    bestPair    = bestPair,
                    slowestPair = slowestPair,
                    minTime     = minTime
                )

                PaceGraphCard(
                    title       = "Pace vs Time",
                    description = "Tap on the graph to see pace at any point in time"
                ) {
                    InteractivePaceVsTimeGraph(
                        paceMetrics = paceMetrics,
                        bestPair    = bestPair,
                        slowestPair = slowestPair,
                        modifier    = Modifier.fillMaxWidth().height(220.dp)
                    )
                }

                PaceGraphCard(
                    title       = "Pace vs Distance",
                    description = "Tap on the graph to see pace at any distance"
                ) {
                    InteractivePaceVsDistanceGraph(
                        paceMetrics = paceMetrics,
                        bestPair    = bestPair,
                        slowestPair = slowestPair,
                        modifier    = Modifier.fillMaxWidth().height(220.dp)
                    )
                }
            }
        }
    }
}

// ── Stats summary card ─────────────────────────────────────────────────────

@Composable
fun PaceStatsSummary(paceMetrics: List<Pair<Metric, Float>>) {
    val paces = paceMetrics.map { it.second }
    val (p5, p95) = percentileRange(paces)
    val avgPace = paces.average().toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Pace Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PaceStatItem(label = "Best",    value = p5.toPaceLabel()      + " /km", color = PaceGreen)
                PaceStatItem(label = "Average", value = avgPace.toPaceLabel() + " /km", color = MaterialTheme.colorScheme.primary)
                PaceStatItem(label = "Slowest", value = p95.toPaceLabel()     + " /km", color = PaceOrange)
            }
        }
    }
}

@Composable
fun PaceStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 14.sp, color = Color.Gray)
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ── Highlights card ────────────────────────────────────────────────────────

@Composable
fun PaceHighlightsCard(
    bestPair:    Pair<Metric, Float>?,
    slowestPair: Pair<Metric, Float>?,
    minTime:     Long
) {
    if (bestPair == null && slowestPair == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Pace Highlights", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            bestPair?.let { (metric, pace) ->
                val elapsedMs = metric.timeInMilliseconds - minTime
                HighlightRow(
                    dotColor   = PaceGreen,
                    label      = "Fastest",
                    pace       = pace.toPaceLabel() + " /km",
                    elapsed    = elapsedMs.toElapsedLabel(),
                    distanceKm = metric.distance / 1000.0
                )
            }

            if (bestPair != null && slowestPair != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
            }

            slowestPair?.let { (metric, pace) ->
                val elapsedMs = metric.timeInMilliseconds - minTime
                HighlightRow(
                    dotColor   = PaceOrange,
                    label      = "Slowest",
                    pace       = pace.toPaceLabel() + " /km",
                    elapsed    = elapsedMs.toElapsedLabel(),
                    distanceKm = metric.distance / 1000.0
                )
            }
        }
    }
}

@Composable
private fun HighlightRow(
    dotColor:   Color,
    label:      String,
    pace:       String,
    elapsed:    String,
    distanceKm: Double
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.width(60.dp))
                Text(pace, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = dotColor)
            }
            Text(
                text = "at $elapsed · ${String.format("%.2f km", distanceKm)}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

// ── Card wrapper ───────────────────────────────────────────────────────────

@Composable
fun PaceGraphCard(
    title:       String,
    description: String,
    content:     @Composable () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text     = description,
                fontSize = 14.sp,
                color    = Color.Gray,
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

// ── Pace vs Time interactive graph ─────────────────────────────────────────

data class PaceTimeInfo(val elapsed: String, val paceLabel: String)

@Composable
fun InteractivePaceVsTimeGraph(
    paceMetrics: List<Pair<Metric, Float>>,
    bestPair:    Pair<Metric, Float>?,
    slowestPair: Pair<Metric, Float>?,
    modifier:    Modifier = Modifier
) {
    var showInfo  by remember { mutableStateOf<PaceTimeInfo?>(null) }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    val paces = paceMetrics.map { it.second }
    val (p5, p95) = percentileRange(paces)
    val paceRange  = (p95 - p5).coerceAtLeast(0.5f)
    val displayMin = (p5  - paceRange * 0.15f).coerceAtLeast(0f)
    val displayMax =  p95 + paceRange * 0.15f
    val displayRange = (displayMax - displayMin).coerceAtLeast(0.1f)
    val avgPace = paces.average().toFloat()

    val minTime   = paceMetrics.minOf { it.first.timeInMilliseconds }
    val maxTime   = paceMetrics.maxOf { it.first.timeInMilliseconds }
    val timeRange = (maxTime - minTime).coerceAtLeast(1L)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val padding   = with(density) { 52.dp.toPx() }
                        val rightPad  = with(density) { 8.dp.toPx() }
                        val chartWidth = size.width - padding - rightPad
                        val fraction  = ((offset.x - padding) / chartWidth).coerceIn(0f, 1f)
                        val tappedMs  = minTime + (timeRange * fraction).toLong()
                        val closest   = paceMetrics.minByOrNull { abs(it.first.timeInMilliseconds - tappedMs) }
                        if (closest != null) {
                            val elapsedMs = closest.first.timeInMilliseconds - minTime
                            showInfo  = PaceTimeInfo(elapsedMs.toElapsedLabel(), closest.second.toPaceLabel())
                            tapOffset = offset
                        }
                    }
                }
        ) {
            val padding    = 52.dp.toPx()
            val rightPad   = 8.dp.toPx()
            val chartWidth  = size.width - padding - rightPad
            val chartHeight = size.height - 2 * padding

            fun toX(timeMs: Long)  = padding + chartWidth * (timeMs - minTime).toFloat() / timeRange
            fun toY(pace: Float)   = padding + chartHeight * (pace - displayMin) / displayRange

            // Axes
            drawLine(Color.Gray, Offset(padding, padding), Offset(padding, size.height - padding), 2.dp.toPx())
            drawLine(Color.Gray, Offset(padding, size.height - padding), Offset(size.width - rightPad, size.height - padding), 2.dp.toPx())

            // Grid + Y labels
            val yPaint = Paint().asFrameworkPaint().apply {
                color = Color.Gray.toArgb(); textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT; isAntiAlias = true
            }
            for (i in 0..4) {
                val pace = displayMin + displayRange * i / 4
                val y = toY(pace)
                drawLine(Color.Gray.copy(alpha = 0.2f), Offset(padding, y), Offset(size.width - rightPad, y), 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(pace.toPaceLabel(), padding - 4.dp.toPx(), y + 3.dp.toPx(), yPaint)
            }

            // X labels
            val xPaint = Paint().asFrameworkPaint().apply {
                color = Color.Gray.toArgb(); textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            for (i in 0..4) {
                val minutes = ((timeRange * i / 4) / 60_000L).toInt()
                val x = padding + chartWidth * i / 4
                drawContext.canvas.nativeCanvas.drawText("${minutes}min", x, size.height - 4.dp.toPx(), xPaint)
            }

            // Avg pace dashed line
            drawLine(
                PaceTeal, Offset(padding, toY(avgPace)), Offset(size.width - rightPad, toY(avgPace)),
                1.5f.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )

            // Pace line
            val path = Path()
            paceMetrics.forEachIndexed { index, (metric, pace) ->
                val x = toX(metric.timeInMilliseconds)
                val y = toY(pace.coerceIn(displayMin, displayMax))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, PaceGreen, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            // Best marker (green)
            bestPair?.let { (metric, pace) ->
                val x = toX(metric.timeInMilliseconds)
                val y = toY(pace.coerceIn(displayMin, displayMax))
                drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
                drawCircle(PaceGreen,  5.dp.toPx(), Offset(x, y))
                val lp = Paint().asFrameworkPaint().apply {
                    color = PaceGreen.toArgb(); textSize = 8.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText("▲ Best", x, (y - 10.dp.toPx()), lp)
            }

            // Slowest marker (orange)
            slowestPair?.let { (metric, pace) ->
                val x = toX(metric.timeInMilliseconds)
                val y = toY(pace.coerceIn(displayMin, displayMax))
                drawCircle(Color.White,  7.dp.toPx(), Offset(x, y))
                drawCircle(PaceOrange,   5.dp.toPx(), Offset(x, y))
                val lp = Paint().asFrameworkPaint().apply {
                    color = PaceOrange.toArgb(); textSize = 8.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText("▼ Slowest", x, (y + 18.dp.toPx()), lp)
            }

            // Tap dot
            showInfo?.let {
                drawCircle(PaceGreen, 5.dp.toPx(), tapOffset)
            }
        }

        // Tooltip
        showInfo?.let { info ->
            val tx = (tapOffset.x + 12.dp.value).roundToInt()
            val ty = (tapOffset.y - 40.dp.value).roundToInt().coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .offset { IntOffset(tx, ty) }
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Text("Time: ${info.elapsed}",       fontSize = 11.sp, color = Color.White)
                    Text("Pace: ${info.paceLabel} /km", fontSize = 11.sp, color = PaceGreen)
                }
            }
        }
    }
}

// ── Pace vs Distance interactive graph ────────────────────────────────────

data class PaceDistInfo(val distanceKm: Double, val paceLabel: String)

@Composable
fun InteractivePaceVsDistanceGraph(
    paceMetrics: List<Pair<Metric, Float>>,
    bestPair:    Pair<Metric, Float>?,
    slowestPair: Pair<Metric, Float>?,
    modifier:    Modifier = Modifier
) {
    var showInfo  by remember { mutableStateOf<PaceDistInfo?>(null) }
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    val distMetrics = paceMetrics.filter { it.first.distance > 0 }
    if (distMetrics.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No distance data available", fontSize = 12.sp, color = Color.Gray)
        }
        return
    }

    val paces = distMetrics.map { it.second }
    val (p5, p95) = percentileRange(paces)
    val paceRange  = (p95 - p5).coerceAtLeast(0.5f)
    val displayMin = (p5  - paceRange * 0.15f).coerceAtLeast(0f)
    val displayMax =  p95 + paceRange * 0.15f
    val displayRange = (displayMax - displayMin).coerceAtLeast(0.1f)
    val avgPace = paces.average().toFloat()

    val minDist   = distMetrics.minOf { it.first.distance }
    val maxDist   = distMetrics.maxOf { it.first.distance }
    val distRange = (maxDist - minDist).coerceAtLeast(0.001)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val padding   = with(density) { 52.dp.toPx() }
                        val rightPad  = with(density) { 8.dp.toPx() }
                        val chartWidth = size.width - padding - rightPad
                        val fraction  = ((offset.x - padding) / chartWidth).coerceIn(0f, 1f)
                        val tappedDist = minDist + distRange * fraction
                        val closest   = distMetrics.minByOrNull { abs(it.first.distance - tappedDist) }
                        if (closest != null) {
                            showInfo  = PaceDistInfo(closest.first.distance / 1000.0, closest.second.toPaceLabel())
                            tapOffset = offset
                        }
                    }
                }
        ) {
            val padding    = 52.dp.toPx()
            val rightPad   = 8.dp.toPx()
            val chartWidth  = size.width - padding - rightPad
            val chartHeight = size.height - 2 * padding

            fun toX(dist: Double) = (padding + chartWidth * (dist - minDist) / distRange).toFloat()
            fun toY(pace: Float)  = padding + chartHeight * (pace - displayMin) / displayRange

            // Axes
            drawLine(Color.Gray, Offset(padding, padding), Offset(padding, size.height - padding), 2.dp.toPx())
            drawLine(Color.Gray, Offset(padding, size.height - padding), Offset(size.width - rightPad, size.height - padding), 2.dp.toPx())

            // Grid + Y labels
            val yPaint = Paint().asFrameworkPaint().apply {
                color = Color.Gray.toArgb(); textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT; isAntiAlias = true
            }
            for (i in 0..4) {
                val pace = displayMin + displayRange * i / 4
                val y = toY(pace)
                drawLine(Color.Gray.copy(alpha = 0.2f), Offset(padding, y), Offset(size.width - rightPad, y), 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(pace.toPaceLabel(), padding - 4.dp.toPx(), y + 3.dp.toPx(), yPaint)
            }

            // X labels
            val xPaint = Paint().asFrameworkPaint().apply {
                color = Color.Gray.toArgb(); textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            for (i in 0..4) {
                val dist = (minDist + distRange * i / 4) / 1000.0
                val x = padding + chartWidth * i / 4
                drawContext.canvas.nativeCanvas.drawText(String.format("%.1f km", dist), x, size.height - 4.dp.toPx(), xPaint)
            }

            // Avg pace dashed line
            drawLine(
                PaceTeal, Offset(padding, toY(avgPace)), Offset(size.width - rightPad, toY(avgPace)),
                1.5f.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )

            // Pace line
            val path = Path()
            distMetrics.forEachIndexed { index, (metric, pace) ->
                val x = toX(metric.distance)
                val y = toY(pace.coerceIn(displayMin, displayMax))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, PaceGreen, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            // Best marker
            bestPair?.let { (metric, pace) ->
                if (metric.distance > 0) {
                    val x = toX(metric.distance)
                    val y = toY(pace.coerceIn(displayMin, displayMax))
                    drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
                    drawCircle(PaceGreen,  5.dp.toPx(), Offset(x, y))
                    val lp = Paint().asFrameworkPaint().apply {
                        color = PaceGreen.toArgb(); textSize = 8.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText("▲ Best", x, y - 10.dp.toPx(), lp)
                }
            }

            // Slowest marker
            slowestPair?.let { (metric, pace) ->
                if (metric.distance > 0) {
                    val x = toX(metric.distance)
                    val y = toY(pace.coerceIn(displayMin, displayMax))
                    drawCircle(Color.White,  7.dp.toPx(), Offset(x, y))
                    drawCircle(PaceOrange,   5.dp.toPx(), Offset(x, y))
                    val lp = Paint().asFrameworkPaint().apply {
                        color = PaceOrange.toArgb(); textSize = 8.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText("▼ Slowest", x, y + 18.dp.toPx(), lp)
                }
            }

            showInfo?.let {
                drawCircle(PaceGreen, 5.dp.toPx(), tapOffset)
            }
        }

        // Tooltip
        showInfo?.let { info ->
            val tx = (tapOffset.x + 12.dp.value).roundToInt()
            val ty = (tapOffset.y - 40.dp.value).roundToInt().coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .offset { IntOffset(tx, ty) }
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(String.format("Dist: %.2f km", info.distanceKm), fontSize = 11.sp, color = Color.White)
                    Text("Pace: ${info.paceLabel} /km",                   fontSize = 11.sp, color = PaceGreen)
                }
            }
        }
    }
}
