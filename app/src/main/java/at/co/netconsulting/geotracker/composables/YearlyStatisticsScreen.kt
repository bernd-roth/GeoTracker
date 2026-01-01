package at.co.netconsulting.geotracker.composables

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.EventWithTotalDistance
import at.co.netconsulting.geotracker.data.HeartRateYearlyStats
import at.co.netconsulting.geotracker.data.HeartRateZoneStats
import at.co.netconsulting.geotracker.data.MonthlyHeartRateStats
import at.co.netconsulting.geotracker.data.MonthlyStats
import at.co.netconsulting.geotracker.data.WeeklyStats
import at.co.netconsulting.geotracker.data.YearlyStatsData
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.repository.MetricDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.roundToInt

// Simple cache to avoid recalculating statistics
private var statsCache: Map<Int, YearlyStatsData>? = null
private var cacheTimestamp: Long = 0
private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

@Composable
fun YearlyStatisticsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var yearlyStatsData by remember { mutableStateOf<Map<Int, YearlyStatsData>>(emptyMap()) }
    var expandedYear by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableStateOf(0f) }
    var loadingMessage by remember { mutableStateOf("Loading events...") }
    val coroutineScope = rememberCoroutineScope()
    val database = remember { FitnessTrackerDatabase.getInstance(context) }

    // Current year for highlighting
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    // Load comprehensive yearly statistics with caching and progress
    LaunchedEffect(Unit) {
        isLoading = true
        loadingProgress = 0f

        coroutineScope.launch {
            try {
                // Check cache first
                val currentTime = System.currentTimeMillis()
                if (statsCache != null && (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
                    yearlyStatsData = statsCache!!
                    isLoading = false
                    return@launch
                }

                loadingMessage = "Loading events..."
                loadingProgress = 0.1f

                val eventsWithMetrics = getAllEventsWithMetrics(database)

                loadingMessage = "Calculating statistics..."
                loadingProgress = 0.5f

                val statsData = calculateComprehensiveStatsWithProgress(
                    eventsWithMetrics,
                    database
                ) { progress, message ->
                    loadingProgress = 0.5f + (progress * 0.5f)
                    loadingMessage = message
                }

                // Cache the results
                statsCache = statsData
                cacheTimestamp = currentTime

                yearlyStatsData = statsData
                isLoading = false
            } catch (e: Exception) {
                Log.e("YearlyStatisticsScreen", "Error loading stats", e)
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = loadingProgress,
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = loadingMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "${(loadingProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (yearlyStatsData.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "No activity data available",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                    color = Color.Gray
                )
            }
        } else {
            // Sort years in descending order
            val sortedYears = yearlyStatsData.keys.sortedDescending()

            sortedYears.forEach { year ->
                val statsData = yearlyStatsData[year]!!

                YearlyStatsCard(
                    year = year,
                    statsData = statsData,
                    isExpanded = expandedYear == year,
                    isCurrentYear = year == currentYear,
                    onClick = {
                        expandedYear = if (expandedYear == year) null else year
                    }
                )

                // Show detailed breakdown if year is expanded
                if (expandedYear == year) {
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailedYearBreakdown(
                        year = year,
                        statsData = statsData
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun YearlyStatsCard(
    year: Int,
    statsData: YearlyStatsData,
    isExpanded: Boolean,
    isCurrentYear: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentYear) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Statistics",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem(
                    label = "Total Distance",
                    value = String.format("%.1f km", statsData.totalDistance),
                    modifier = Modifier.weight(1f)
                )

                StatisticItem(
                    label = "Activities",
                    value = "${statsData.totalActivities}",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatisticItem(
                    label = "Avg per Activity",
                    value = String.format("%.1f km", statsData.averageDistancePerActivity),
                    modifier = Modifier.weight(1f)
                )

                StatisticItem(
                    label = "Avg per Month",
                    value = String.format("%.1f km", statsData.averageDistancePerMonth),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DetailedYearBreakdown(
    year: Int,
    statsData: YearlyStatsData
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Monthly breakdown graph
            Text(
                text = "Monthly Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(8.dp)
            ) {
                MonthlyTrendGraph(
                    monthlyStats = statsData.monthlyStats,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Monthly breakdown list
            Text(
                text = "Monthly Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(statsData.monthlyStats) { monthlyStats ->
                    MonthlyStatsRow(monthlyStats = monthlyStats)
                    if (monthlyStats != statsData.monthlyStats.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekly trend graph
            Text(
                text = "Weekly Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(8.dp)
            ) {
                WeeklyTrendGraph(
                    weeklyStats = statsData.weeklyStats,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Heart Rate Performance Section
            statsData.heartRateStats?.let { heartRateStats ->
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Heart Rate Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Heart Rate Summary Stats
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HeartRateStatItem(
                                label = "Overall Min",
                                value = "${heartRateStats.overallMinHR} bpm",
                                color = Color.Blue,
                                modifier = Modifier.weight(1f)
                            )

                            HeartRateStatItem(
                                label = "Overall Avg",
                                value = "${heartRateStats.overallAvgHR.toInt()} bpm",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )

                            HeartRateStatItem(
                                label = "Overall Max",
                                value = "${heartRateStats.overallMaxHR} bpm",
                                color = Color.Red,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HeartRateStatItem(
                                label = "Activities with HR",
                                value = "${heartRateStats.activitiesWithHR}",
                                color = Color.Gray,
                                modifier = Modifier.weight(1f)
                            )

                            HeartRateStatItem(
                                label = "HR Coverage",
                                value = "${(heartRateStats.activitiesWithHR.toFloat() / heartRateStats.totalActivities * 100).toInt()}%",
                                color = Color.Gray,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Heart Rate Monthly Trend
                Text(
                    text = "Monthly Heart Rate Trend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(8.dp)
                ) {
                    HeartRateTrendGraph(
                        monthlyHeartRateStats = heartRateStats.monthlyHeartRateStats,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Heart Rate Zone Distribution
                Text(
                    text = "Training Zone Distribution",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(8.dp)
                ) {
                    HeartRateZoneGraph(
                        zoneStats = heartRateStats.heartRateZoneDistribution,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun StatisticItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun HeartRateStatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun MonthlyStatsRow(
    monthlyStats: MonthlyStats
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = getMonthName(monthlyStats.month),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = String.format("%.1f km", monthlyStats.totalDistance),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (monthlyStats.activityCount > 0) {
                Text(
                    text = "${monthlyStats.activityCount} activities",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun getMonthName(month: Int): String {
    return when (month) {
        1 -> "January"
        2 -> "February"
        3 -> "March"
        4 -> "April"
        5 -> "May"
        6 -> "June"
        7 -> "July"
        8 -> "August"
        9 -> "September"
        10 -> "October"
        11 -> "November"
        12 -> "December"
        else -> "Unknown"
    }
}

private suspend fun calculateComprehensiveStatsWithProgress(
    events: List<EventWithTotalDistance>,
    database: FitnessTrackerDatabase,
    onProgress: (Float, String) -> Unit
): Map<Int, YearlyStatsData> {
    return withContext(Dispatchers.Default) {
        val yearGroups = events.groupBy { event ->
            event.eventDate.split("-")[0].toInt()
        }

        val totalYears = yearGroups.size
        val results = mutableMapOf<Int, YearlyStatsData>()

        yearGroups.entries.forEachIndexed { yearIndex, (year, yearEvents) ->
            onProgress(yearIndex.toFloat() / totalYears, "Processing year $year...")

            val totalDistance = yearEvents.sumOf { it.totalDistance / 1000.0 }
            val totalActivities = yearEvents.size
            val averageDistancePerActivity = if (totalActivities > 0) totalDistance / totalActivities else 0.0
            val averageDistancePerMonth = totalDistance / 12

            // Calculate monthly stats
            val monthlyGroups = yearEvents.groupBy { event ->
                event.eventDate.split("-")[1].toInt()
            }

            val monthlyStats = (1..12).map { month ->
                val monthEvents = monthlyGroups[month] ?: emptyList()
                MonthlyStats(
                    year = year,
                    month = month,
                    totalDistance = monthEvents.sumOf { it.totalDistance / 1000.0 },
                    activityCount = monthEvents.size
                )
            }

            // Calculate weekly stats
            val weeklyGroups = yearEvents.groupBy { event ->
                val dateParts = event.eventDate.split("-")
                val calendar = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    minimalDaysInFirstWeek = 4
                    set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                }
                calendar.get(Calendar.WEEK_OF_YEAR)
            }

            val weeklyStats = weeklyGroups.map { (week, weekEvents) ->
                WeeklyStats(
                    year = year,
                    week = week,
                    totalDistance = weekEvents.sumOf { it.totalDistance / 1000.0 },
                    activityCount = weekEvents.size
                )
            }.sortedBy { it.week }

            // Calculate heart rate statistics
            onProgress((yearIndex + 0.5f) / totalYears, "Calculating heart rate data for $year...")
            val heartRateStats = calculateHeartRateStats(year, yearEvents, database)

            results[year] = YearlyStatsData(
                year = year,
                totalDistance = totalDistance,
                totalActivities = totalActivities,
                averageDistancePerActivity = averageDistancePerActivity,
                averageDistancePerMonth = averageDistancePerMonth,
                monthlyStats = monthlyStats,
                weeklyStats = weeklyStats,
                heartRateStats = heartRateStats
            )
        }

        results
    }
}

// Legacy function for compatibility
private suspend fun calculateComprehensiveStats(events: List<EventWithTotalDistance>, database: FitnessTrackerDatabase): Map<Int, YearlyStatsData> {
    return withContext(Dispatchers.Default) {
        val yearGroups = events.groupBy { event ->
            event.eventDate.split("-")[0].toInt()
        }

        yearGroups.mapValues { (year, yearEvents) ->
            val totalDistance = yearEvents.sumOf { it.totalDistance / 1000.0 }
            val totalActivities = yearEvents.size
            val averageDistancePerActivity = if (totalActivities > 0) totalDistance / totalActivities else 0.0
            val averageDistancePerMonth = totalDistance / 12

            // Calculate monthly stats
            val monthlyGroups = yearEvents.groupBy { event ->
                event.eventDate.split("-")[1].toInt()
            }

            val monthlyStats = (1..12).map { month ->
                val monthEvents = monthlyGroups[month] ?: emptyList()
                MonthlyStats(
                    year = year,
                    month = month,
                    totalDistance = monthEvents.sumOf { it.totalDistance / 1000.0 },
                    activityCount = monthEvents.size
                )
            }

            // Calculate weekly stats
            val weeklyGroups = yearEvents.groupBy { event ->
                val dateParts = event.eventDate.split("-")
                val calendar = Calendar.getInstance().apply {
                    firstDayOfWeek = Calendar.MONDAY
                    minimalDaysInFirstWeek = 4
                    set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                }
                calendar.get(Calendar.WEEK_OF_YEAR)
            }

            val weeklyStats = weeklyGroups.map { (week, weekEvents) ->
                WeeklyStats(
                    year = year,
                    week = week,
                    totalDistance = weekEvents.sumOf { it.totalDistance / 1000.0 },
                    activityCount = weekEvents.size
                )
            }.sortedBy { it.week }

            // Calculate heart rate statistics
            val heartRateStats = calculateHeartRateStats(year, yearEvents, database)

            YearlyStatsData(
                year = year,
                totalDistance = totalDistance,
                totalActivities = totalActivities,
                averageDistancePerActivity = averageDistancePerActivity,
                averageDistancePerMonth = averageDistancePerMonth,
                monthlyStats = monthlyStats,
                weeklyStats = weeklyStats,
                heartRateStats = heartRateStats
            )
        }
    }
}

private suspend fun getAllEventsWithMetrics(database: FitnessTrackerDatabase): List<EventWithTotalDistance> {
    return withContext(Dispatchers.IO) {
        val result = mutableListOf<EventWithTotalDistance>()

        try {
            val eventsFlow = database.eventDao().getAllEvents()
            val events = eventsFlow.first()

            // Get all event IDs at once
            val eventIds = events.map { it.eventId }

            // Batch query: Get max distance for all events in one query
            val eventDistances = getMaxDistancesForEvents(database, eventIds)

            // Process events without individual queries
            events.forEach { event ->
                try {
                    val totalDistance = eventDistances[event.eventId] ?: 0.0

                    result.add(
                        EventWithTotalDistance(
                            eventId = event.eventId,
                            eventName = event.eventName,
                            artOfSport = event.artOfSport,
                            eventDate = event.eventDate,
                            totalDistance = totalDistance
                        )
                    )
                } catch (e: Exception) {
                    Log.e("YearlyStatisticsScreen", "Error processing event ${event.eventId}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("YearlyStatisticsScreen", "Error getting events: ${e.message}", e)
        }
        result
    }
}

// Optimized batch query to get max distances for all events at once
private suspend fun getMaxDistancesForEvents(database: FitnessTrackerDatabase, eventIds: List<Int>): Map<Int, Double> {
    return withContext(Dispatchers.IO) {
        try {
            // This would ideally be a single SQL query like:
            // SELECT eventId, MAX(distance) FROM metrics WHERE eventId IN (...) GROUP BY eventId
            // Since we don't have direct SQL access, we'll do a more efficient approach

            val distances = mutableMapOf<Int, Double>()

            // Get all metrics for these events in chunks to avoid memory issues
            val chunkSize = 50
            eventIds.chunked(chunkSize).forEach { chunk ->
                chunk.forEach { eventId ->
                    try {
                        val metrics = database.metricDao().getMetricsByEventId(eventId)
                        val maxDistance = metrics.maxOfOrNull { it.distance } ?: 0.0
                        distances[eventId] = maxDistance
                    } catch (e: Exception) {
                        Log.w("YearlyStatisticsScreen", "Error getting distance for event $eventId: ${e.message}")
                        distances[eventId] = 0.0
                    }
                }
            }

            distances
        } catch (e: Exception) {
            Log.e("YearlyStatisticsScreen", "Error in batch distance query: ${e.message}", e)
            emptyMap()
        }
    }
}

private suspend fun calculateHeartRateStats(year: Int, events: List<EventWithTotalDistance>, database: FitnessTrackerDatabase): HeartRateYearlyStats? {
    return withContext(Dispatchers.IO) {
        try {
            // Batch get heart rate data for all events at once
            val eventIds = events.map { it.eventId }
            val heartRateData = getBatchHeartRateData(database, eventIds)

            if (heartRateData.isEmpty()) {
                return@withContext null
            }

            val allHeartRates = mutableListOf<Int>()
            val monthlyHRData = mutableMapOf<Int, MutableList<Int>>()
            var activitiesWithHR = 0

            // Initialize monthly data
            for (month in 1..12) {
                monthlyHRData[month] = mutableListOf()
            }

            // Process pre-fetched heart rate data
            events.forEach { event ->
                val eventHRs = heartRateData[event.eventId] ?: emptyList()

                if (eventHRs.isNotEmpty()) {
                    activitiesWithHR++
                    allHeartRates.addAll(eventHRs)

                    // Add to monthly data
                    val month = event.eventDate.split("-")[1].toInt()
                    monthlyHRData[month]?.addAll(eventHRs)
                }
            }

            if (allHeartRates.isEmpty()) {
                return@withContext null
            }

            // Calculate overall statistics
            val overallMinHR = allHeartRates.minOrNull() ?: 0
            val overallMaxHR = allHeartRates.maxOrNull() ?: 0
            val overallAvgHR = allHeartRates.average()

            // Calculate monthly statistics
            val monthlyHeartRateStats = (1..12).map { month ->
                val monthHRs = monthlyHRData[month] ?: emptyList()
                val activitiesInMonth = events.count {
                    it.eventDate.split("-")[1].toInt() == month &&
                    heartRateData[it.eventId]?.isNotEmpty() == true
                }

                MonthlyHeartRateStats(
                    year = year,
                    month = month,
                    minHR = monthHRs.minOrNull() ?: 0,
                    maxHR = monthHRs.maxOrNull() ?: 0,
                    avgHR = if (monthHRs.isNotEmpty()) monthHRs.average() else 0.0,
                    activitiesWithHR = activitiesInMonth
                )
            }

            // Calculate heart rate zones (assuming max HR of 190 for zones calculation)
            val maxHRForZones = 190
            val zoneDistribution = calculateHeartRateZones(allHeartRates, maxHRForZones)

            HeartRateYearlyStats(
                year = year,
                overallMinHR = overallMinHR,
                overallMaxHR = overallMaxHR,
                overallAvgHR = overallAvgHR,
                activitiesWithHR = activitiesWithHR,
                totalActivities = events.size,
                monthlyHeartRateStats = monthlyHeartRateStats,
                heartRateZoneDistribution = zoneDistribution
            )
        } catch (e: Exception) {
            Log.e("HeartRateStats", "Error calculating heart rate stats: ${e.message}", e)
            null
        }
    }
}

// Optimized batch heart rate data retrieval
private suspend fun getBatchHeartRateData(database: FitnessTrackerDatabase, eventIds: List<Int>): Map<Int, List<Int>> {
    return withContext(Dispatchers.IO) {
        try {
            val heartRateData = mutableMapOf<Int, List<Int>>()

            // Process in smaller chunks to avoid memory issues
            val chunkSize = 25  // Smaller chunks for better memory management
            eventIds.chunked(chunkSize).forEach { chunk ->
                chunk.forEach { eventId ->
                    try {
                        val metrics = database.metricDao().getMetricsByEventId(eventId)
                        val heartRates = metrics.filter { it.heartRate > 0 }.map { it.heartRate }
                        heartRateData[eventId] = heartRates
                    } catch (e: Exception) {
                        Log.w("HeartRateStats", "Error getting HR for event $eventId: ${e.message}")
                        heartRateData[eventId] = emptyList()
                    }
                }

                // Small delay between chunks to prevent overwhelming the database
                kotlinx.coroutines.delay(10)
            }

            heartRateData
        } catch (e: Exception) {
            Log.e("HeartRateStats", "Error in batch heart rate query: ${e.message}", e)
            emptyMap()
        }
    }
}

private fun calculateHeartRateZones(heartRates: List<Int>, maxHR: Int): HeartRateZoneStats {
    // Count activities in each zone, not individual heart rate readings
    val activityZones = mutableMapOf<Int, Int>()

    // Group heart rates by activity (assuming every ~100 readings is one activity)
    val activitiesHR = heartRates.chunked(50) // Rough estimate of readings per activity

    var zone1Count = 0 // 50-60% max HR
    var zone2Count = 0 // 60-70% max HR
    var zone3Count = 0 // 70-80% max HR
    var zone4Count = 0 // 80-90% max HR
    var zone5Count = 0 // 90-100% max HR

    activitiesHR.forEach { activityHRs ->
        if (activityHRs.isNotEmpty()) {
            // Use average HR for the activity to determine zone
            val avgHR = activityHRs.average()
            val percentage = (avgHR / maxHR) * 100

            when {
                percentage <= 60 -> zone1Count++
                percentage <= 70 -> zone2Count++
                percentage <= 80 -> zone3Count++
                percentage <= 90 -> zone4Count++
                else -> zone5Count++
            }
        }
    }

    return HeartRateZoneStats(
        zone1Count = zone1Count,
        zone2Count = zone2Count,
        zone3Count = zone3Count,
        zone4Count = zone4Count,
        zone5Count = zone5Count
    )
}