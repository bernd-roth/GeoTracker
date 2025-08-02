package at.co.netconsulting.geotracker.composables

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.service.WeatherEventBusHandler
import at.co.netconsulting.geotracker.tools.Tools
import com.github.mikephil.charting.data.Entry
import org.greenrobot.eventbus.EventBus
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun StatisticsScreen() {
    val context = LocalContext.current

    // Get singleton instance with context
    val weatherHandler = remember { WeatherEventBusHandler.getInstance(context) }

    // Initialize with current session ID
    LaunchedEffect(Unit) {
        val sessionId = context.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            .getString("current_session_id", "") ?: ""

        if (sessionId.isNotEmpty()) {
            weatherHandler.initializeWithSession(sessionId)
            Log.d("StatisticsScreen", "Initialized WeatherEventBusHandler with session: $sessionId")
        }
    }

    // Add DisposableEffect to properly handle EventBus registration
    DisposableEffect(Unit) {
        // Make sure the handler is registered when the screen becomes visible
        if (!EventBus.getDefault().isRegistered(weatherHandler)) {
            EventBus.getDefault().register(weatherHandler)
            Log.d("StatisticsScreen", "Registered WeatherEventBusHandler with EventBus")
        }

        onDispose {
            // Don't unregister here, as it might affect other screens
            // Let the handler manage its own lifecycle
        }
    }

    // Collect flows as state
    val weather by weatherHandler.weather.collectAsState()
    val metrics by weatherHandler.metrics.collectAsState()
    val heartRateData by weatherHandler.heartRate.collectAsState()
    val heartRateHistory by weatherHandler.heartRateHistory.collectAsState()
    val lapTimes by weatherHandler.lapTimes.collectAsState() // Database-driven lap times

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Session Info Card
        StatisticsCard(
            title = "Session Info",
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Start Time",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Text(
                            text = metrics?.startDateTime?.toLocalTime()?.toString() ?: "--:--:--",
                            fontSize = 20.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Duration",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Text(
                            text = formatDuration(metrics),
                            fontSize = 20.sp
                        )
                    }
                }
            }
        )

        // Speed Card
        StatisticsCard(
            title = "Speed",
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricColumn("Current", "${String.format("%.1f", metrics?.speed ?: 0f)} km/h")
                    MetricColumn("Average", "${String.format("%.1f", metrics?.averageSpeed ?: 0.0)} km/h")
                    MetricColumn("Moving Avg", "${String.format("%.1f", metrics?.movingAverageSpeed ?: 0.0)} km/h")
                }
            }
        )

        // Distance Card
        StatisticsCard(
            title = "Distance",
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricColumn("Total", formatDistance(metrics?.coveredDistance ?: 0.0))
                    MetricColumn("Altitude", formatAltitude(metrics?.altitude ?: 0.0))
                    MetricColumn("Elev. Gain", formatElevation(metrics?.cumulativeElevationGain ?: 0.0))
                }
            }
        )

        // Heart Rate Card
        StatisticsCard(
            title = "Heart Rate",
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Current",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        if (heartRateData?.isConnected == true) {
                            Text(
                                text = "${heartRateData?.heartRate ?: 0} bpm",
                                fontSize = 20.sp
                            )
                        } else {
                            Text(
                                text = if (heartRateData?.isScanning == true) "Scanning..." else "Not connected",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                        }

                        // Show device name if connected
                        heartRateData?.deviceName?.let { deviceName ->
                            if (deviceName.isNotEmpty() && heartRateData?.isConnected == true) {
                                Text(
                                    text = deviceName,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        )

        // Weather Card
        StatisticsCard(
            title = "Weather",
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricColumn("Temperature", formatTemperature(weather?.temperature))
                    MetricColumn("Weather Code", formatWeatherCode(weather?.weathercode))
                    MetricColumn("Wind", formatWind(weather?.windspeed, weather?.winddirection))
                }
            }
        )

        // Enhanced Lap Times Card with Database Integration
        StatisticsCard(
            title = "Lap Times",
            content = {
                val currentDistance = metrics?.coveredDistance ?: 0.0
                val distanceKm = currentDistance / 1000.0
                val currentLapNumber = weatherHandler.getCurrentLap(currentDistance)

                Column {
                    if (lapTimes.isNotEmpty()) {
                        // Display each completed lap with its time
                        lapTimes.forEach { lapTime ->
                            val lapDuration = formatLapTime(lapTime.endTime - lapTime.startTime)
                            val lapPace = calculateLapPace(lapTime.endTime - lapTime.startTime, lapTime.distance)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${lapTime.lapNumber} km:",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = lapDuration,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color.Green
                                    )
                                    Text(
                                        text = lapPace,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        // Show current lap progress if in progress
                        val completedLaps = lapTimes.size
                        val currentLapDistance = currentDistance - (completedLaps * 1000)

                        if (currentDistance > completedLaps * 1000) {
                            val nextLapNumber = completedLaps + 1
                            val lapProgress = currentLapDistance / 1000.0

                            Spacer(modifier = Modifier.height(4.dp))

                            // Current lap progress
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Current ($nextLapNumber km):",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Blue
                                )

                                Text(
                                    text = String.format("%.0f m", currentLapDistance),
                                    fontSize = 14.sp,
                                    color = Color.Blue
                                )
                            }

                            // Progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color.LightGray.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(lapProgress.toFloat())
                                        .background(Color.Blue)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Summary statistics
                        if (lapTimes.isNotEmpty()) {
                            val totalLapTime = lapTimes.sumOf { it.endTime - it.startTime }
                            val averageLapTime = totalLapTime / lapTimes.size
                            val bestLap = lapTimes.minByOrNull { it.endTime - it.startTime }
                            val worstLap = lapTimes.maxByOrNull { it.endTime - it.startTime }

                            Column {
                                // Divider
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.Gray.copy(alpha = 0.3f))
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Summary stats
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Total and Average
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = "Total Lap Time",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = formatLapTime(totalLapTime),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "Avg Lap",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = formatLapTime(averageLapTime),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }

                                    // Best and Worst
                                    Column(horizontalAlignment = Alignment.End) {
                                        bestLap?.let {
                                            Text(
                                                text = "Best (${it.lapNumber} km)",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = formatLapTime(it.endTime - it.startTime),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color(0xFF4CAF50)
                                            )
                                        }

                                        if (lapTimes.size > 1) {
                                            Spacer(modifier = Modifier.height(4.dp))

                                            worstLap?.let {
                                                Text(
                                                    text = "Slowest (${it.lapNumber} km)",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                                Text(
                                                    text = formatLapTime(it.endTime - it.startTime),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = Color(0xFFFF9800)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    } else if (distanceKm > 0) {
                        // Show current lap progress when no laps completed yet
                        val currentLapDistanceMeters = currentDistance % 1000
                        val lapProgress = currentLapDistanceMeters / 1000.0

                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Current (1 km):",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Blue
                                )

                                Text(
                                    text = String.format("%.0f / 1000 m", currentLapDistanceMeters),
                                    fontSize = 14.sp,
                                    color = Color.Blue
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Progress bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .background(
                                        Color.LightGray.copy(alpha = 0.3f),
                                        RoundedCornerShape(3.dp)
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(lapProgress.toFloat())
                                        .background(
                                            Color.Blue,
                                            RoundedCornerShape(3.dp)
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = String.format("%.1f%% complete", lapProgress * 100),
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    } else {
                        Text(
                            text = "Start moving to begin lap tracking",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        )

        // Speed vs Distance Card
        StatisticsCard(
            title = "Speed vs Distance",
            content = {
                if (metrics != null && metrics!!.coveredDistance > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        // Create entries for chart based on covered distance
                        val entries = remember(metrics) {
                            // For simplicity, create sample entries based on current metrics
                            val distanceInKm = metrics!!.coveredDistance / 1000
                            val entries = mutableListOf<Entry>()

                            // Create 5 sample points
                            val pointCount = 5
                            for (i in 0..pointCount) {
                                val x = (distanceInKm * i / pointCount).toFloat()
                                val y = metrics!!.speed * (0.8f + (Math.random() * 0.4f)).toFloat()
                                entries.add(Entry(x, y))
                            }
                            entries
                        }

                        RealLineChart(
                            entries = entries,
                            lineColor = Color(0xFF2196F3),
                            fillColor = Color(0xFF2196F3),
                            xLabel = "Distance (km)",
                            yLabel = "Speed (km/h)"
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.LightGray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chart will appear as you move")
                    }
                }
            }
        )

        // Altitude vs Distance Card
        StatisticsCard(
            title = "Altitude vs Distance",
            content = {
                if (metrics != null && metrics!!.coveredDistance > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        // Create entries for chart based on altitude and distance
                        val altitudeDistanceEntries = remember(metrics) {
                            val distanceInKm = metrics!!.coveredDistance / 1000
                            val entries = mutableListOf<Entry>()

                            // Create sample points
                            val pointCount = 5
                            val baseAltitude = metrics!!.altitude.toFloat()
                            for (i in 0..pointCount) {
                                val x = (distanceInKm * i / pointCount).toFloat()
                                // Simulate altitude variation
                                val variation = (Math.sin(i.toDouble()) * 5).toFloat()
                                val y = baseAltitude + variation
                                entries.add(Entry(x, y))
                            }
                            entries
                        }

                        RealLineChart(
                            entries = altitudeDistanceEntries,
                            lineColor = Color(0xFF4CAF50),
                            fillColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                            xLabel = "Distance (km)",
                            yLabel = "Altitude (m)"
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.LightGray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chart will appear as you move")
                    }
                }
            }
        )

        // Heart Rate vs Distance Card
        StatisticsCard(
            title = "Heart Rate vs Distance",
            content = {
                if (metrics != null && metrics!!.coveredDistance > 0 && heartRateHistory.isNotEmpty() &&
                    heartRateData?.isConnected == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        // Convert the heart rate history into chart entries
                        val heartRateEntries = remember(heartRateHistory) {
                            heartRateHistory.map { (distance, rate) ->
                                Entry(distance.toFloat(), rate.toFloat())
                            }
                        }

                        HeartRateDistanceChart(
                            entries = heartRateEntries,
                            lineColor = Color(0xFFE91E63), // Pink color for heart rate
                            fillColor = Color(0xFFE91E63).copy(alpha = 0.2f)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.LightGray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (heartRateData?.isConnected == true)
                                "Chart will appear as you move"
                            else
                                "Connect a heart rate sensor to see data"
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun StatisticsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F3FF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = Color(0xFF6650a4),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF6650a4).copy(alpha = 0.2f))
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal // Explicitly set to normal, not bold
        )
    }
}

@Composable
fun LapInfoRow(label: String, value: String, textColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = value,
            fontSize = 14.sp,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Normal,
            color = textColor
        )
    }
}

// Helper functions
private fun formatDuration(metrics: Metrics?): String {
    if (metrics == null) return "00:00:00"

    // Calculate duration between startDateTime and now
    val duration = Duration.between(
        metrics.startDateTime,
        LocalDateTime.now()
    )
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    val seconds = duration.seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatDistance(distance: Double): String {
    return String.format("%.2f km", distance / 1000)
}

private fun formatAltitude(altitude: Double): String {
    return String.format("%.1f m", altitude)
}

private fun formatElevation(elevation: Double): String {
    return String.format("%.1f m", elevation)
}

private fun formatTemperature(temperature: Double?): String {
    return if (temperature != null) {
        String.format("%.1f°C", temperature)
    } else {
        "N/A"
    }
}

private fun formatWeatherCode(code: Int?): String {
    if (code == null) return "N/A"
    return when(code) {
        0 -> "Clear"
        1, 2, 3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        95, 96, 99 -> "Storm"
        else -> code.toString()
    }
}

private fun formatWind(speed: Double?, direction: Double?): String {
    if (speed == null || direction == null) return "N/A"
    val directionStr = getWindDirection(direction)
    return String.format("%.1f Km/h %s", speed, directionStr)
}

private fun getWindDirection(degrees: Double): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = ((degrees + 22.5) % 360 / 45).toInt()
    return directions[index % 8]
}

// Helper functions for lap time formatting
private fun formatLapTime(timeInMilliseconds: Long): String {
    val totalSeconds = timeInMilliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun calculateLapPace(timeInMilliseconds: Long, distanceKm: Double): String {
    if (distanceKm <= 0 || timeInMilliseconds <= 0) return "-- /km"

    val totalSeconds = timeInMilliseconds / 1000.0
    val paceSecondsPerKm = (totalSeconds / distanceKm).toInt()
    val paceMinutes = paceSecondsPerKm / 60
    val paceSeconds = paceSecondsPerKm % 60
    return String.format("%d:%02d /km", paceMinutes, paceSeconds)
}