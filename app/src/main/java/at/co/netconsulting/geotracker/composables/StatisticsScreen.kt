package at.co.netconsulting.geotracker.composables

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.data.CurrentWeather
import at.co.netconsulting.geotracker.data.Metrics
import com.github.mikephil.charting.data.Entry
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun StatisticsScreen() {
    val context = LocalContext.current
    val TAG = "StatisticsScreen"

    // State for metrics data
    var currentMetrics by remember { mutableStateOf<Metrics?>(null) }
    var lapTimes by remember { mutableStateOf<MutableMap<Int, Duration>>(mutableMapOf()) }
    var startTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var weatherData by remember { mutableStateOf<CurrentWeather?>(null) }
    var currentDuration by remember { mutableStateOf("00:00:00") }

    // Force recomposition every second to update duration
    var ticker by remember { mutableStateOf(0) }
    LaunchedEffect(key1 = true) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            ticker++

            // Update duration if start time is set
            if (startTime != null) {
                val duration = Duration.between(startTime, LocalDateTime.now())
                currentDuration = formatDuration(duration)
            }
        }
    }

    // State for chart data (limited to last 100 entries to prevent memory issues)
    var speedDistanceEntries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var altitudeDistanceEntries by remember { mutableStateOf<List<Entry>>(emptyList()) }

    // Create EventBus listener object
    val eventBusListener = remember {
        object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onMetricsEvent(metrics: Metrics) {
                Log.d(TAG, "Received metrics: speed=${metrics.speed}, distance=${metrics.coveredDistance}, lap=${metrics.lap}")

                // Update current metrics
                currentMetrics = metrics

                // Set start time if not already set
                if (startTime == null) {
                    startTime = metrics.startDateTime
                    Log.d(TAG, "Start time set to: ${metrics.startDateTime}")
                }

                // Update chart data (keep only last 100 entries to prevent memory issues)
                val distance = metrics.coveredDistance.toFloat() / 1000f // Convert to km
                speedDistanceEntries = (speedDistanceEntries + Entry(distance, metrics.speed))
                    .takeLast(100)
                altitudeDistanceEntries = (altitudeDistanceEntries + Entry(distance, metrics.altitude.toFloat()))
                    .takeLast(100)

                // Calculate distance-based lap, since the lap in metrics may not be updated properly
                val distanceKm = metrics.coveredDistance / 1000.0
                val currentLapBasedOnDistance = distanceKm.toInt()

                // Log lap information for debugging
                Log.d(TAG, "Lap info - reported lap: ${metrics.lap}, " +
                        "distance: ${String.format("%.2f", distanceKm)} km, " +
                        "calculated lap: $currentLapBasedOnDistance")

                // Only record laps for complete kilometers
                if (currentLapBasedOnDistance > 0 && !lapTimes.containsKey(currentLapBasedOnDistance)) {
                    val lapDuration = Duration.between(startTime, LocalDateTime.now())
                    lapTimes[currentLapBasedOnDistance] = lapDuration
                    Log.d(TAG, "New lap recorded: lap=$currentLapBasedOnDistance, " +
                            "distance=${String.format("%.2f", distanceKm)} km, " +
                            "duration=${formatDuration(lapDuration)}")
                }
            }

            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onWeatherReceived(weather: CurrentWeather) {
                Log.d(TAG, "Received weather update: temp=${weather.temperature}, wind=${weather.windspeed}, time=${weather.time}")
                weatherData = weather

                // Print the full object to help with debugging
                Log.d(TAG, "Full weather object: $weather")
            }

            @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
            fun onEvent(event: Any) {
                Log.d(TAG, "EventBus received event of type: ${event.javaClass.simpleName}")
            }
        }
    }

    // Register and unregister from EventBus
    LaunchedEffect(Unit) {
        if (!EventBus.getDefault().isRegistered(eventBusListener)) {
            EventBus.getDefault().register(eventBusListener)
            Log.d(TAG, "Registered EventBus listener")
        }
    }

    // Clean up when the composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            if (EventBus.getDefault().isRegistered(eventBusListener)) {
                EventBus.getDefault().unregister(eventBusListener)
                Log.d(TAG, "Unregistered EventBus listener")
            }
        }
    }

    // Main content
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Session Info Card
        StatisticCard(title = "Session Info") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Start Time",
                    value = startTime?.format(DateTimeFormatter.ofPattern("HH:mm:ss")) ?: "--:--:--"
                )

                StatItem(
                    label = "Duration",
                    value = currentDuration
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speed Card
        StatisticCard(title = "Speed") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Current",
                    value = "${String.format("%.1f", currentMetrics?.speed ?: 0f)} km/h"
                )

                StatItem(
                    label = "Average",
                    value = "${String.format("%.1f", currentMetrics?.averageSpeed ?: 0.0)} km/h"
                )

                StatItem(
                    label = "Moving Avg",
                    value = "${String.format("%.1f", currentMetrics?.movingAverageSpeed ?: 0.0)} km/h"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Distance Card
        StatisticCard(title = "Distance") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Total",
                    value = "${String.format("%.2f", (currentMetrics?.coveredDistance ?: 0.0) / 1000)} km"
                )

                StatItem(
                    label = "Altitude",
                    value = "${String.format("%.1f", currentMetrics?.altitude ?: 0.0)} m"
                )

                StatItem(
                    label = "Elev. Gain",
                    value = "${String.format("%.1f", currentMetrics?.cumulativeElevationGain ?: 0.0)} m"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weather Card
        StatisticCard(title = "Weather") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Temperature",
                    value = weatherData?.temperature?.let { "${String.format("%.1f", it)}°C" } ?: "N/A"
                )

                StatItem(
                    label = "Weather Code",
                    value = weatherData?.weathercode?.toString() ?: "N/A"
                )

                StatItem(
                    label = "Wind",
                    value = weatherData?.let {
                        val direction = getWindDirection(it.winddirection)
                        "${String.format("%.1f", it.windspeed)} km/h $direction"
                    } ?: "N/A"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lap Times Card
        StatisticCard(title = "Lap Times") {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (lapTimes.isEmpty()) {
                    Text(
                        text = "No lap data available yet",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                    )
                } else {
                    lapTimes.entries.sortedBy { it.key }.forEach { (lap, duration) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Lap ${lap} (${lap} km):",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (lap < lapTimes.size) {
                            Divider(modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speed vs Distance Chart
        StatisticCard(title = "Speed vs Distance") {
            if (speedDistanceEntries.isEmpty()) {
                Text(
                    text = "No chart data available yet",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                )
            } else {
                RealLineChart(
                    entries = speedDistanceEntries,
                    lineColor = Color(0xFF2196F3),
                    fillColor = Color(0x332196F3),
                    xLabel = "Distance (km)",
                    yLabel = "Speed (km/h)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Altitude vs Distance Chart
        StatisticCard(title = "Altitude vs Distance") {
            if (altitudeDistanceEntries.isEmpty()) {
                Text(
                    text = "No chart data available yet",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                )
            } else {
                RealLineChart(
                    entries = altitudeDistanceEntries,
                    lineColor = Color(0xFF4CAF50),
                    fillColor = Color(0x334CAF50),
                    xLabel = "Distance (km)",
                    yLabel = "Altitude (m)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun StatisticCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )

            content()
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun LineChart(
    entries: List<Entry>,
    lineColor: Color,
    fillColor: Color,
    xLabel: String,
    yLabel: String,
    modifier: Modifier = Modifier
) {
    // In a real implementation, you would use a charting library like MP Android Chart
    Box(
        modifier = modifier
            .background(
                color = fillColor,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Text(
            text = "Chart: $xLabel vs $yLabel",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.Center)
        )

        Text(
            text = "${entries.size} data points",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        )
    }
}

// Utility function to format duration
fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    val seconds = duration.seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

// Helper function to convert wind direction in degrees to cardinal direction
fun getWindDirection(degrees: Double): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = ((degrees + 22.5) % 360 / 45).toInt()
    return directions[index]
}