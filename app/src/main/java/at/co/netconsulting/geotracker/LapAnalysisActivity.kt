package at.co.netconsulting.geotracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var pathPoints by remember { mutableStateOf<List<PathPoint>>(emptyList()) }
    var lapTimes by remember { mutableStateOf<List<LapTime>>(emptyList()) }
    var selectedPoint by remember { mutableStateOf<PathPoint?>(null) }
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
                    pathPoints = sortedPoints
                    lapTimes = laps
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
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
                // Map section (35% of screen)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.35f)
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    if (pathPoints.isNotEmpty()) {
                        InteractivePathMap(
                            pathPoints = pathPoints,
                            selectedPoint = selectedPoint,
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

                // Speed Graph section (20% of screen)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
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

                // Speed Color Legend (5% of screen)
                SpeedColorLegend(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Altitude Profile Graph section (20% of screen)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
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

                // Lap times table (20% of screen)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    LapTimesTable(
                        lapTimes = lapTimes,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun LapTimesTable(
    lapTimes: List<LapTime>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = "Lap Times",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (lapTimes.isEmpty()) {
            Text(
                text = "No lap data available",
                color = Color.Gray,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            LazyColumn {
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
                            modifier = Modifier.weight(0.3f)
                        )
                        Text(
                            text = "Time",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.4f)
                        )
                        Text(
                            text = "Pace",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.3f)
                        )
                    }
                    HorizontalDivider()
                }

                // Lap data
                items(lapTimes) { lapTime ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${lapTime.lapNumber}",
                            modifier = Modifier.weight(0.3f)
                        )
                        Text(
                            text = Tools().formatDuration(lapTime.endTime - lapTime.startTime),
                            modifier = Modifier.weight(0.4f)
                        )
                        Text(
                            text = calculatePace(lapTime.endTime - lapTime.startTime),
                            modifier = Modifier.weight(0.3f)
                        )
                    }
                }
            }
        }
    }
}

fun calculatePace(durationMs: Long): String {
    val durationSeconds = durationMs / 1000.0
    val paceSecondsPerKm = (durationSeconds / 1.0).toInt() // Assuming 1km laps
    val minutes = paceSecondsPerKm / 60
    val seconds = paceSecondsPerKm % 60
    return String.format("%d:%02d/km", minutes, seconds)
}