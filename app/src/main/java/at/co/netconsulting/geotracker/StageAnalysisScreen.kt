package at.co.netconsulting.geotracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.composables.stageDistance
import at.co.netconsulting.geotracker.composables.stageDuration
import at.co.netconsulting.geotracker.domain.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

private val stageColors = listOf(0xFF1565C0.toInt(), 0xFFC62828.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt(), 0xFFEF6C00.toInt(), 0xFF00838F.toInt())
private data class AnalysedStage(val event: Event, val metrics: List<Metric>, val route: List<GeoPoint>, val totals: StageTotals)
private fun speedLabel(speed: Double?) = speed?.let { String.format(Locale.getDefault(), "%.1f km/h", it) } ?: "—"

@Composable
fun StageAnalysisHost(eventId: Int, database: FitnessTrackerDatabase, onNavigateBack: () -> Unit, initialAllStages: Boolean = false) {
    var selectedId by rememberSaveable(eventId) { mutableStateOf(eventId) }
    var showAll by rememberSaveable(eventId) { mutableStateOf(initialAllStages) }
    val summaries by remember(database) { database.stageGroupDao().observeStages() }.collectAsState(emptyList())
    val selected = summaries.find { it.event.eventId == selectedId }
    val selector: @Composable () -> Unit = {
        if (selected != null) {
            Text(selected.groupName, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(selected = !showAll, onClick = { showAll = false }, label = { Text("This stage") })
                FilterChip(selected = showAll, onClick = { showAll = true }, label = { Text("All stages") })
            }
        }
    }
    if (showAll && selected != null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            TextButton(onClick = onNavigateBack) { Text("Back to events") }
            selector()
            StageGroupAnalysis(database, selected.event.stageGroupId!!) { selectedId = it; showAll = false }
        }
    } else {
        key(selectedId) {
            LapAnalysisScreen(selectedId, database, onNavigateBack, selector)
        }
    }
}

@Composable
private fun StageGroupAnalysis(database: FitnessTrackerDatabase, groupId: Long, onStageClick: (Int) -> Unit) {
    var stages by remember(groupId) { mutableStateOf<List<AnalysedStage>?>(null) }
    var error by remember(groupId) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(groupId, attempt) {
        error = false
        try {
            stages = withContext(Dispatchers.IO) {
                database.stageGroupDao().members(groupId).map { event ->
                    val metrics = database.metricDao().getMetricsByEventId(event.eventId).sortedBy { it.timeInMilliseconds }
                    val locations = database.locationDao().getLocationsForEvent(event.eventId)
                    val stride = (locations.size / 2000 + 1)
                    val route = locations.filterIndexed { index, _ -> index % stride == 0 || index == locations.lastIndex }
                        .filter { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
                        .map { GeoPoint(it.latitude, it.longitude) }
                    val totals = calculateStageTotals(metrics)
                    val metricStride = metrics.size / 2000 + 1
                    val chartMetrics = metrics.filterIndexed { index, _ -> index % metricStride == 0 || index == metrics.lastIndex }
                    AnalysedStage(event, chartMetrics, route, totals)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = true }
    }
    if (error) {
        Text("Could not load stage analysis.", Modifier.padding(16.dp))
        TextButton(onClick = { attempt++ }) { Text("Retry") }
        return
    }
    val loaded = stages ?: run { CircularProgressIndicator(Modifier.padding(16.dp)); return }
    val totals = remember(loaded) { combineStageTotals(loaded.map { it.totals }) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("${loaded.size} stages · ${stageDistance(totals.distanceMeters)}", style = MaterialTheme.typography.headlineSmall)
            Text("Recorded time: ${stageDuration(totals.durationMillis)}")
            Text("Average speed: ${speedLabel(totals.averageSpeedKmh)} · Maximum: ${speedLabel(totals.maxSpeedKmh)}")
            Text("Ascent: ${totals.ascentMeters.toInt()} m · Descent: ${totals.descentMeters.toInt()} m")
            Text("Recorded time includes stops within a stage and excludes gaps between stages.", style = MaterialTheme.typography.bodySmall)
        }
        item { StageRoutesMap(loaded) }
        item { Text("Stage comparison", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(Modifier.fillMaxWidth()) {
                Text("Stage", Modifier.weight(1.4f))
                Text("Distance / time", Modifier.weight(1.2f))
                Text("Avg speed", Modifier.weight(1f))
            }
        }
        itemsIndexed(loaded, key = { _, stage -> stage.event.eventId }) { index, stage ->
            Row(Modifier.fillMaxWidth().clickable { onStageClick(stage.event.eventId) }.padding(vertical = 8.dp)) {
                Text("${stage.event.stageOrder ?: index + 1} · ${stage.event.eventName}", Modifier.weight(1.4f), color = Color(stageColors[index % stageColors.size]))
                Text("${stageDistance(stage.totals.distanceMeters)}\n${stageDuration(stage.totals.durationMillis)}", Modifier.weight(1.2f))
                Text(speedLabel(stage.totals.averageSpeedKmh), Modifier.weight(1f))
            }
        }
        item { Text("Speed across stages (km/h)", style = MaterialTheme.typography.titleMedium) }
        item { StageChart(loaded, altitude = false) }
        item { Text("Altitude across stages (m)", style = MaterialTheme.typography.titleMedium) }
        item { StageChart(loaded, altitude = true) }
        item { Text("Charts use cumulative distance (km). Each coloured line is one stage; gaps are not connected.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun StageRoutesMap(stages: List<AnalysedStage>) {
    val allPoints = remember(stages) { stages.flatMap { it.route } }
    if (allPoints.isEmpty()) { Text("No route data available"); return }
    var map by remember { mutableStateOf<MapView?>(null) }
    DisposableEffect(Unit) { onDispose { map?.onDetach() } }
    AndroidView(modifier = Modifier.fillMaxWidth().height(280.dp), factory = { context ->
        org.osmdroid.config.Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            map = this
        }
    }, update = { view ->
        view.overlays.clear()
        stages.forEachIndexed { index, stage ->
            if (stage.route.isNotEmpty()) view.overlays.add(Polyline().apply {
                setPoints(stage.route)
                outlinePaint.color = stageColors[index % stageColors.size]
                outlinePaint.strokeWidth = 6f
                title = "Stage ${stage.event.stageOrder ?: index + 1}: ${stage.event.eventName}"
            })
        }
        view.post {
            if (allPoints.size > 1) view.zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints), false, 48)
            else { view.controller.setZoom(14.0); view.controller.setCenter(allPoints.first()) }
        }
        view.invalidate()
    })
}

@Composable
private fun StageChart(stages: List<AnalysedStage>, altitude: Boolean) {
    val data = remember(stages, altitude) {
        var offset = 0.0
        val datasets = stages.mapIndexedNotNull { index, stage ->
            val stride = stage.metrics.size / 2000 + 1
            val entries = stage.metrics.filterIndexed { i, _ -> i % stride == 0 || i == stage.metrics.lastIndex }
                .filter { it.distance.isFinite() && it.distance >= 0 && (if (altitude) it.elevation.isFinite() else it.speed.isFinite()) }
                .sortedBy { it.distance }
                .map { Entry(((offset + it.distance) / 1000).toFloat(), if (altitude) it.elevation else it.speed * 3.6f) }
            offset += stage.totals.distanceMeters
            if (entries.isEmpty()) null else LineDataSet(entries, "Stage ${stage.event.stageOrder ?: index + 1}").apply {
                color = stageColors[index % stageColors.size]
                setCircleColor(color)
                setDrawCircles(entries.size == 1)
                setDrawValues(false)
                lineWidth = 2f
            }
        }
        LineData(datasets)
    }
    AndroidView(modifier = Modifier.fillMaxWidth().height(230.dp), factory = { context ->
        LineChart(context).apply {
            description.isEnabled = false
            axisRight.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            legend.isWordWrapEnabled = true
            setNoDataText("No metric data available")
        }
    }, update = { chart -> chart.data = data; chart.notifyDataSetChanged(); chart.invalidate() })
}
