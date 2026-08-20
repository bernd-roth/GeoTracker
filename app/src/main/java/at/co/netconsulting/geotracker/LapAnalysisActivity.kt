package at.co.netconsulting.geotracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import at.co.netconsulting.geotracker.composables.InteractivePathMap
import at.co.netconsulting.geotracker.composables.SpeedDistanceGraph
import at.co.netconsulting.geotracker.composables.AltitudeProfileGraph
import at.co.netconsulting.geotracker.composables.SpeedColorLegend
import at.co.netconsulting.geotracker.data.PathPoint
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.LapTime
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.tools.Tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LapAnalysisActivity : ComponentActivity() {

    private lateinit var database: FitnessTrackerDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = FitnessTrackerDatabase.getInstance(this)
        val eventId = intent.getIntExtra("EVENT_ID", -1)

        if (eventId == -1) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                LapAnalysisScreen(eventId = eventId, database = database)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LapAnalysisScreen(eventId: Int, database: FitnessTrackerDatabase) {
    var event by remember { mutableStateOf<Event?>(null) }
    var allPathPoints by remember { mutableStateOf<List<PathPoint>>(emptyList()) }
    var pathPoints by remember { mutableStateOf<List<PathPoint>>(emptyList()) }
    var lapTimes by remember { mutableStateOf<List<LapTime>>(emptyList()) }
    var selectedPoint by remember { mutableStateOf<PathPoint?>(null) }
    var selectedLapGroup by remember { mutableStateOf<CalculatedLapGroup?>(null) }
    var lapGroupSize by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(true) }

    // val context = LocalContext.current // Unused for now

    // Load data when the screen starts
    LaunchedEffect(eventId) {
        withContext(Dispatchers.IO) {
            try {
                // Load event details
                val eventData = database.eventDao().getEventById(eventId)

                // Load locations, metrics, and weather data
                val locations = database.locationDao().getLocationsByEventId(eventId)
                val metrics = database.metricDao().getMetricsByEventId(eventId)
                val weatherData = database.weatherDao().getWeatherForEvent(eventId)

                // Load lap times
                val laps = database.lapTimeDao().getLapTimesByEvent(eventId)

                // Create path points by matching metrics and locations
                val points = mutableListOf<PathPoint>()

                // Debug info
                android.util.Log.d("LapAnalysis", "Found ${metrics.size} metrics, ${locations.size} locations, ${weatherData.size} weather entries")

                if (metrics.isNotEmpty()) {
                    // Sort data by time first to ensure proper order
                    val sortedMetrics = metrics.sortedBy { it.timeInMilliseconds }
                    val sortedLocations = locations.sortedBy { it.locationId }
                    val sortedWeather = weatherData.sortedBy { it.weatherId }

                    // Calculate session start time for duration calculation
                    val sessionStartTime = sortedMetrics.firstOrNull()?.timeInMilliseconds ?: 0L

                    sortedMetrics.forEachIndexed { index, metric ->
                        // Try to get corresponding location by index, or use proportional mapping
                        val locationIndex = if (sortedLocations.isNotEmpty()) {
                            (index * sortedLocations.size / sortedMetrics.size).coerceIn(0, sortedLocations.size - 1)
                        } else -1

                        val location = if (locationIndex >= 0) sortedLocations[locationIndex] else null

                        // Get weather data (usually one entry per event, so use first available)
                        val weather = sortedWeather.firstOrNull()

                        // Calculate total duration from start
                        val totalDuration = metric.timeInMilliseconds - sessionStartTime

                        // Debug each metric
                        android.util.Log.d("LapAnalysis", "Metric $index: distance=${metric.distance}, speed=${metric.speed}, time=${metric.timeInMilliseconds}, duration=${totalDuration}")

                        // Only add points with valid distance values (should be positive and reasonable)
                        if (metric.distance >= 0 && metric.distance < 1000000) { // Less than 1000km seems reasonable
                            points.add(
                                PathPoint(
                                    latitude = location?.latitude ?: 0.0,
                                    longitude = location?.longitude ?: 0.0,
                                    speed = metric.speed.coerceAtLeast(0f), // Ensure speed is not negative
                                    distance = metric.distance,
                                    timestamp = metric.timeInMilliseconds,
                                    altitude = location?.altitude ?: metric.elevation.toDouble(),
                                    totalDuration = totalDuration,
                                    temperature = weather?.temperature ?: metric.temperature,
                                    windSpeed = weather?.windSpeed?.toDouble(),
                                    windDirection = null, // Weather has direction as string, would need parsing
                                    relativeHumidity = weather?.relativeHumidity,
                                    pressure = metric.pressure
                                )
                            )
                        }
                    }
                }

                // Sort by distance to ensure proper order
                val sortedPoints = points.sortedBy { it.distance }
                android.util.Log.d("LapAnalysis", "Created ${sortedPoints.size} path points")
                sortedPoints

                withContext(Dispatchers.Main) {
                    event = eventData
                    allPathPoints = sortedPoints
                    pathPoints = sortedPoints
                    lapTimes = laps
                    selectedLapGroup = null
                    lapGroupSize = 1
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    // Filter path points when a lap is selected
    LaunchedEffect(selectedLapGroup) {
        val lapGroup = selectedLapGroup
        if (lapGroup != null) {
            // Filter points that fall within the selected lap group's time range
            val filteredPoints = allPathPoints.filter { point ->
                point.timestamp >= lapGroup.startTime && point.timestamp <= lapGroup.endTime
            }
            pathPoints = filteredPoints
            android.util.Log.d(
                "LapAnalysis",
                "Filtered to ${filteredPoints.size} points for laps ${lapGroup.label}"
            )
        } else {
            // Show all points when no lap is selected
            pathPoints = allPathPoints
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = event?.eventName ?: "Lap Analysis",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Map section (fixed at top)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    if (pathPoints.isNotEmpty()) {
                        InteractivePathMap(
                            pathPoints = pathPoints,
                            selectedPoint = selectedPoint,
                            zoomToFit = selectedLapGroup != null,
                            key = selectedLapGroup?.let { "${it.firstLapNumber}-${it.lastLapNumber}" },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No path data available")
                        }
                    }
                }

                // Scrollable content below the map
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Speed Graph section (fixed height)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        if (pathPoints.isNotEmpty()) {
                            SpeedDistanceGraph(
                                pathPoints = pathPoints,
                                onPointClick = { point: PathPoint -> selectedPoint = point },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No speed data available")
                            }
                        }
                    }

                    // Speed Color Legend
                    SpeedColorLegend(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    // Altitude Profile Graph section (fixed height)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        if (pathPoints.isNotEmpty()) {
                            AltitudeProfileGraph(
                                pathPoints = pathPoints,
                                onPointClick = { point: PathPoint -> selectedPoint = point },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No altitude data available")
                            }
                        }
                    }

                    // Lap times table (fixed height to show ~10 entries)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp)
                            .padding(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LapTimesTable(
                            lapTimes = lapTimes,
                            lapGroupSize = lapGroupSize,
                            selectedLapGroup = selectedLapGroup,
                            onLapGroupSizeChange = { newGroupSize ->
                                lapGroupSize = newGroupSize
                                selectedLapGroup = null
                            },
                            onLapClick = { lap ->
                                selectedLapGroup = if (selectedLapGroup == lap) null else lap
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LapTimesTable(
    lapTimes: List<LapTime>,
    lapGroupSize: Int,
    selectedLapGroup: CalculatedLapGroup?,
    onLapGroupSizeChange: (Int) -> Unit,
    onLapClick: (CalculatedLapGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    var groupMenuExpanded by remember { mutableStateOf(false) }
    val calculatedLaps = remember(lapTimes, lapGroupSize) {
        groupLapTimes(lapTimes, lapGroupSize)
    }
    val eligiblePaces = calculatedLaps.filterNot { it.isIncomplete }
        .mapNotNull { it.paceSecondsPerKm }
    val fastestPace = eligiblePaces.minOrNull()
    val slowestPace = eligiblePaces.maxOrNull()

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lap Times",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            if (lapTimes.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = groupMenuExpanded,
                    onExpandedChange = { groupMenuExpanded = !groupMenuExpanded },
                    modifier = Modifier.width(140.dp)
                ) {
                    OutlinedTextField(
                        value = "$lapGroupSize ${if (lapGroupSize == 1) "lap" else "laps"}",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Combine") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = groupMenuExpanded,
                        onDismissRequest = { groupMenuExpanded = false }
                    ) {
                        (1..lapTimes.size).forEach { size ->
                            DropdownMenuItem(
                                text = { Text("$size ${if (size == 1) "lap" else "laps"}") },
                                onClick = {
                                    onLapGroupSizeChange(size)
                                    groupMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (lapTimes.isEmpty()) {
            Text(
                text = "No lap data available",
                color = Color.Gray,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Lap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(0.23f)
                        )
                        Text(
                            text = "km",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(0.19f)
                        )
                        Text(
                            text = "Time",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(0.31f)
                        )
                        Text(
                            text = "Pace",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(0.27f)
                        )
                    }
                    HorizontalDivider()
                }

                // Lap data
                items(calculatedLaps) { lapGroup ->
                    val isSelected = selectedLapGroup == lapGroup
                    val pace = lapGroup.paceSecondsPerKm
                    val resultColor = when {
                        lapGroup.isIncomplete -> Color.Gray
                        pace != null && pace == fastestPace -> Color(0xFF008B00)
                        pace != null && pace == slowestPace -> Color.Red
                        else -> Color.Unspecified
                    }
                    val rowTextColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        resultColor
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        onClick = { onLapClick(lapGroup) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = lapGroup.label,
                                modifier = Modifier.weight(0.23f),
                                fontSize = 13.sp,
                                color = rowTextColor
                            )
                            Text(
                                text = String.format("%.2f", lapGroup.distanceKm),
                                modifier = Modifier.weight(0.19f),
                                fontSize = 13.sp,
                                color = rowTextColor
                            )
                            Text(
                                text = Tools().formatDuration(lapGroup.durationMs),
                                modifier = Modifier.weight(0.31f),
                                fontSize = 13.sp,
                                color = rowTextColor
                            )
                            Text(
                                text = calculatePace(lapGroup.durationMs, lapGroup.distanceKm),
                                modifier = Modifier.weight(0.27f),
                                fontSize = 13.sp,
                                color = rowTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}
