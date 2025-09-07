package at.co.netconsulting.geotracker.composables

import android.app.ActivityManager
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
import at.co.netconsulting.geotracker.service.FollowingService
import at.co.netconsulting.geotracker.data.FollowedUserPoint
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
    val followingService = remember { FollowingService.getInstance(context) }

    // Initialize with current session ID, but only if the service is actually running
    LaunchedEffect(Unit) {
        val isServiceRunning = isServiceRunning(context, "at.co.netconsulting.geotracker.service.ForegroundService")
        val sessionId = context.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            .getString("current_session_id", "") ?: ""

        if (isServiceRunning && sessionId.isNotEmpty()) {
            weatherHandler.initializeWithSession(sessionId)
            Log.d("StatisticsScreen", "Service is running - initialized WeatherEventBusHandler with session: $sessionId")
        } else {
            // Service is not running or no session - clear any existing lap times to prevent showing old data
            weatherHandler.clearLapTimes()
            Log.d("StatisticsScreen", "Service not running or no session - cleared existing lap times (service running: $isServiceRunning, sessionId: $sessionId)")
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
    val speedHistory by weatherHandler.speedHistory.collectAsState() // Speed history for chart
    val altitudeHistory by weatherHandler.altitudeHistory.collectAsState() // Altitude history for chart
    val lapTimes by weatherHandler.lapTimes.collectAsState() // Database-driven lap times
    
    // Collect following state
    val followingState by followingService.followingState.collectAsState()

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
                if (metrics != null && metrics!!.coveredDistance > 0 && speedHistory.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        // Convert the speed history into chart entries
                        val entries = remember(speedHistory) {
                            speedHistory.map { (distance, speed) ->
                                Entry(distance.toFloat(), speed)
                            }
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
                        Text(
                            if (metrics != null && metrics!!.coveredDistance > 0)
                                "Collecting speed data..."
                            else
                                "Chart will appear as you move"
                        )
                    }
                }
            }
        )

        // Altitude vs Distance Card
        StatisticsCard(
            title = "Altitude vs Distance",
            content = {
                if (metrics != null && metrics!!.coveredDistance > 0 && altitudeHistory.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        // Convert the altitude history into chart entries
                        val altitudeEntries = remember(altitudeHistory) {
                            altitudeHistory.map { (distance, altitude) ->
                                Entry(distance.toFloat(), altitude.toFloat())
                            }
                        }

                        RealLineChart(
                            entries = altitudeEntries,
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
                        Text(
                            if (metrics != null && metrics!!.coveredDistance > 0)
                                "Collecting altitude data..."
                            else
                                "Chart will appear as you move"
                        )
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

        // Followed Users Statistics
        if (followingState.isFollowing && followingState.followedUsers.isNotEmpty()) {
            followingState.followedUsers.forEach { sessionId ->
                val trail = followingState.getTrail(sessionId)
                if (trail.isNotEmpty()) {
                    FollowedUserStatistics(sessionId, trail)
                }
            }
        }
    }
}

@Composable
fun FollowedUserStatistics(sessionId: String, trail: List<FollowedUserPoint>) {
    val latestPoint = trail.lastOrNull()
    val firstPoint = trail.firstOrNull()
    
    if (latestPoint == null || firstPoint == null) return
    
    // Find the most recent point that has weather data for display
    val latestPointWithWeather = trail.findLast { 
        it.temperature != null || it.weatherCode != null || it.pressure != null || 
        it.relativeHumidity != null || it.windSpeed != null || it.windDirection != null 
    }
    
    // Debug log to see what weather data we have
    Log.d("StatisticsScreen", "Latest point weather data for ${latestPoint.person}: temp=${latestPoint.temperature}, code=${latestPoint.weatherCode}, pressure=${latestPoint.pressure}")
    Log.d("StatisticsScreen", "Latest point WITH weather data for ${latestPoint.person}: temp=${latestPointWithWeather?.temperature}, code=${latestPointWithWeather?.weatherCode}, pressure=${latestPointWithWeather?.pressure}")
    
    // Calculate statistics from trail
    val totalDistance = latestPoint.distance
    val currentSpeed = latestPoint.currentSpeed
    val currentAltitude = latestPoint.altitude
    val currentHeartRate = latestPoint.heartRate
    
    // Calculate average speed and moving average speed
    val averageSpeed = if (trail.size > 1) {
        trail.filter { it.currentSpeed > 0 }.map { it.currentSpeed }.average()
    } else {
        currentSpeed.toDouble()
    }
    
    val movingAverageSpeed = if (trail.size >= 10) {
        trail.takeLast(10).filter { it.currentSpeed > 0 }.map { it.currentSpeed }.average()
    } else {
        averageSpeed
    }
    
    // Calculate elevation gain
    val elevationGain = calculateElevationGain(trail)
    
    // Create chart entries for speed and altitude
    val speedEntries = remember(trail) {
        trail.mapIndexed { index, point ->
            Entry((point.distance / 1000.0).toFloat(), point.currentSpeed)
        }
    }
    
    val altitudeEntries = remember(trail) {
        trail.mapIndexed { index, point ->
            Entry((point.distance / 1000.0).toFloat(), point.altitude.toFloat())
        }
    }
    
    val heartRateEntries = remember(trail) {
        trail.filter { it.heartRate != null }.map { point ->
            Entry((point.distance / 1000.0).toFloat(), point.heartRate!!.toFloat())
        }
    }
    
    // Use actual lap times from WebSocket data instead of calculating fake ones
    val lapTimes = latestPoint.lapTimes?.map { webSocketLapTime ->
        FollowedUserLap(
            lapNumber = webSocketLapTime.lapNumber,
            distance = webSocketLapTime.distance,
            duration = webSocketLapTime.duration
        )
    } ?: emptyList()
    
    // User title
    Text(
        text = "Following: ${latestPoint.person}",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF6650a4),
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
    
    // Session Info Card
    StatisticsCard(
        title = "Session Info - ${latestPoint.person}",
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
                        text = firstPoint.timestamp.substringAfter("T").substringBefore("."),
                        fontSize = 20.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Last Update",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Text(
                        text = latestPoint.timestamp.substringAfter("T").substringBefore("."),
                        fontSize = 20.sp
                    )
                }
            }
        }
    )

    // Speed Card
    StatisticsCard(
        title = "Speed - ${latestPoint.person}",
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("Current", "${String.format("%.1f", currentSpeed)} km/h")
                MetricColumn("Average", "${String.format("%.1f", averageSpeed)} km/h")
                MetricColumn("Moving Avg", "${String.format("%.1f", movingAverageSpeed)} km/h")
            }
        }
    )

    // Distance Card
    StatisticsCard(
        title = "Distance - ${latestPoint.person}",
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("Total", formatDistance(totalDistance))
                MetricColumn("Altitude", formatAltitude(currentAltitude))
                MetricColumn("Elev. Gain", formatElevation(elevationGain))
            }
        }
    )

    // Heart Rate Card
    StatisticsCard(
        title = "Heart Rate - ${latestPoint.person}",
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

                    if (currentHeartRate != null) {
                        Text(
                            text = "$currentHeartRate bpm",
                            fontSize = 20.sp
                        )
                    } else {
                        Text(
                            text = "Not available",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }

                    Text(
                        text = "Remote Data",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    )

    // Weather Card
    StatisticsCard(
        title = "Weather - ${latestPoint.person}",
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("Temperature", formatTemperature(latestPointWithWeather?.temperature))
                MetricColumn("Weather Code", formatWeatherCode(latestPointWithWeather?.weatherCode))
                MetricColumn("Wind", formatWind(latestPointWithWeather?.windSpeed, latestPointWithWeather?.windDirection))
            }
        }
    )

    // Lap Times Card
    StatisticsCard(
        title = "Lap Times - ${latestPoint.person}",
        content = {
            val currentDistance = totalDistance
            val distanceKm = currentDistance / 1000.0

            Column {
                if (lapTimes.isNotEmpty()) {
                    // Display each completed lap with its time
                    lapTimes.forEach { lapTime ->
                        val lapDuration = formatFollowedUserLapTime(lapTime.duration)
                        val lapPace = calculateFollowedUserLapPace(lapTime.duration, lapTime.distance)

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
                        val totalLapTime = lapTimes.sumOf { it.duration }
                        val averageLapTime = totalLapTime / lapTimes.size
                        val bestLap = lapTimes.minByOrNull { it.duration }
                        val worstLap = lapTimes.maxByOrNull { it.duration }

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
                                        text = formatFollowedUserLapTime(totalLapTime),
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
                                        text = formatFollowedUserLapTime(averageLapTime),
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
                                            text = formatFollowedUserLapTime(it.duration),
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
                                                text = formatFollowedUserLapTime(it.duration),
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
                        text = "Waiting for movement data...",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    )

    // Speed vs Distance Chart
    StatisticsCard(
        title = "Speed vs Distance - ${latestPoint.person}",
        content = {
            if (totalDistance > 0 && speedEntries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    RealLineChart(
                        entries = speedEntries,
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
                    Text(
                        if (totalDistance > 0)
                            "Collecting speed data..."
                        else
                            "Chart will appear as user moves"
                    )
                }
            }
        }
    )

    // Altitude vs Distance Chart
    StatisticsCard(
        title = "Altitude vs Distance - ${latestPoint.person}",
        content = {
            if (totalDistance > 0 && altitudeEntries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    RealLineChart(
                        entries = altitudeEntries,
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
                    Text(
                        if (totalDistance > 0)
                            "Collecting altitude data..."
                        else
                            "Chart will appear as user moves"
                    )
                }
            }
        }
    )

    // Heart Rate vs Distance Chart
    StatisticsCard(
        title = "Heart Rate vs Distance - ${latestPoint.person}",
        content = {
            if (totalDistance > 0 && heartRateEntries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
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
                        if (currentHeartRate != null)
                            "Chart will appear as user moves"
                        else
                            "Heart rate data not available from remote user"
                    )
                }
            }
        }
    )
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

private fun isServiceRunning(context: Context, serviceName: String): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Integer.MAX_VALUE)
        .any { service ->
            serviceName == service.service.className && service.foreground
        }
}

private fun calculateElevationGain(trail: List<FollowedUserPoint>): Double {
    if (trail.size < 2) return 0.0
    
    var totalGain = 0.0
    
    for (i in 1 until trail.size) {
        val currentAltitude = trail[i].altitude
        val previousAltitude = trail[i - 1].altitude
        val gain = currentAltitude - previousAltitude
        
        if (gain > 0) {
            totalGain += gain
        }
    }
    
    return totalGain
}

// Data class for followed user lap times
data class FollowedUserLap(
    val lapNumber: Int,
    val distance: Double, // in meters, should be 1000.0 for completed laps
    val duration: Long // duration in milliseconds (simulated from timestamps)
)

private fun calculateFollowedUserLaps(trail: List<FollowedUserPoint>): List<FollowedUserLap> {
    if (trail.isEmpty()) return emptyList()
    
    val lapTimes = mutableListOf<FollowedUserLap>()
    var currentLap = 1
    var lapStartIndex = 0
    
    for (i in 1 until trail.size) {
        val currentDistance = trail[i].distance
        val lapThreshold = currentLap * 1000.0 // Each lap is 1000 meters
        
        if (currentDistance >= lapThreshold) {
            // Find the points before and after the lap boundary
            val prevPoint = trail[i - 1]
            val currentPoint = trail[i]
            
            // Calculate approximate time when the lap was completed using linear interpolation
            val distanceToLap = lapThreshold - prevPoint.distance
            val segmentDistance = currentPoint.distance - prevPoint.distance
            
            // Since we don't have actual timestamps as Long values, we'll simulate duration
            // based on the assumption that points are collected at regular intervals
            val pointInterval = 1000L // Assume 1 second between points
            val lapStartTime = lapStartIndex * pointInterval
            val lapEndTime = (i - 1) * pointInterval + (pointInterval * (distanceToLap / segmentDistance)).toLong()
            
            val lapDuration = lapEndTime - lapStartTime
            
            lapTimes.add(
                FollowedUserLap(
                    lapNumber = currentLap,
                    distance = 1000.0,
                    duration = lapDuration
                )
            )
            
            currentLap++
            lapStartIndex = i - 1
        }
    }
    
    return lapTimes
}

private fun formatFollowedUserLapTime(durationInMilliseconds: Long): String {
    val totalSeconds = durationInMilliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun calculateFollowedUserLapPace(durationInMilliseconds: Long, distanceKm: Double): String {
    if (distanceKm <= 0 || durationInMilliseconds <= 0) return "-- /km"
    
    val totalSeconds = durationInMilliseconds / 1000.0
    val paceSecondsPerKm = (totalSeconds / distanceKm).toInt()
    val paceMinutes = paceSecondsPerKm / 60
    val paceSeconds = paceSecondsPerKm % 60
    return String.format("%d:%02d /km", paceMinutes, paceSeconds)
}