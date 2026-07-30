package at.co.netconsulting.geotracker.composables

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.CadenceBucketStats
import at.co.netconsulting.geotracker.data.CadenceYearlyStats
import at.co.netconsulting.geotracker.data.EventWithTotalDistance
import at.co.netconsulting.geotracker.data.HeartRateYearlyStats
import at.co.netconsulting.geotracker.data.HeartRateZoneStats
import at.co.netconsulting.geotracker.data.MonthlyHeartRateStats
import at.co.netconsulting.geotracker.data.MonthlyCadenceStats
import at.co.netconsulting.geotracker.data.MonthlyStats
import at.co.netconsulting.geotracker.data.WeeklyStats
import at.co.netconsulting.geotracker.data.YearlyStatsData
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

// Simple cache to avoid recalculating statistics
private var statsCache: Map<Int, YearlyStatsData>? = null
private var cacheTimestamp: Long = 0
private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

private enum class StatisticsLoadingPhase {
    ACTIVITIES,
    TRENDS,
    HEART_RATE,
    SPM
}

@Composable
fun YearlyStatisticsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var yearlyStatsData by remember { mutableStateOf<Map<Int, YearlyStatsData>>(emptyMap()) }
    var expandedYear by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableStateOf(0f) }
    var loadingMessage by remember { mutableStateOf("Finding your activities") }
    var loadingDetail by remember { mutableStateOf("Opening your training history") }
    var loadingPhase by remember { mutableStateOf(StatisticsLoadingPhase.ACTIVITIES) }
    val database = remember { FitnessTrackerDatabase.getInstance(context) }

    // Current year for highlighting
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    // Load comprehensive yearly statistics with caching and progress
    LaunchedEffect(Unit) {
        isLoading = true
        loadingProgress = 0f
        loadingMessage = "Finding your activities"
        loadingDetail = "Opening your training history"
        loadingPhase = StatisticsLoadingPhase.ACTIVITIES

        try {
            // Check cache first
            val currentTime = System.currentTimeMillis()
            if (statsCache != null && (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
                yearlyStatsData = statsCache!!
                isLoading = false
                return@LaunchedEffect
            }

            val eventsWithMetrics = getAllEventsWithMetrics(database) { progress, message, detail ->
                loadingProgress = 0.04f + (progress * 0.42f)
                loadingMessage = message
                loadingDetail = detail
            }

            loadingPhase = StatisticsLoadingPhase.TRENDS
            loadingMessage = "Building yearly trends"
            loadingDetail = "Grouping ${eventsWithMetrics.size} activities by week and month"
            loadingProgress = 0.48f

            val statsData = calculateComprehensiveStatsWithProgress(
                eventsWithMetrics,
                database
            ) { progress, message, detail, phase ->
                loadingProgress = 0.48f + (progress * 0.50f)
                loadingMessage = message
                loadingDetail = detail
                loadingPhase = phase
            }

            // Cache the results
            statsCache = statsData
            cacheTimestamp = currentTime

            loadingProgress = 1f
            yearlyStatsData = statsData
            isLoading = false
        } catch (e: Exception) {
            Log.e("YearlyStatisticsScreen", "Error loading stats", e)
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (isLoading) {
            StatisticsLoadingCard(
                progress = loadingProgress,
                message = loadingMessage,
                detail = loadingDetail,
                phase = loadingPhase
            )
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
private fun StatisticsLoadingCard(
    progress: Float,
    message: String,
    detail: String,
    phase: StatisticsLoadingPhase
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "statistics progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "statistics loading pulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statistics icon scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .scale(iconScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Preparing detailed statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your activity story is taking shape",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
            )

            Spacer(modifier = Modifier.height(22.dp))
            StatisticsLoadingSteps(currentPhase = phase)
        }
    }
}

@Composable
private fun StatisticsLoadingSteps(currentPhase: StatisticsLoadingPhase) {
    val labels = listOf("Activities", "Trends", "Heart rate", "SPM")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            val isComplete = index < currentPhase.ordinal
            val isActive = index == currentPhase.ordinal
            val stepColor = when {
                isComplete -> MaterialTheme.colorScheme.primary
                isActive -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(stepColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isComplete) "✓" else "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isComplete -> MaterialTheme.colorScheme.onPrimary
                            isActive -> MaterialTheme.colorScheme.onTertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive || isComplete) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )
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
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
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

            statsData.cadenceStats?.let { cadenceStats ->
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Running Cadence (SPM)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HeartRateStatItem(
                                label = "Overall Min",
                                value = "${cadenceStats.overallMinSpm} spm",
                                color = Color(0xFF5E35B1),
                                modifier = Modifier.weight(1f)
                            )
                            HeartRateStatItem(
                                label = "Overall Avg",
                                value = "${cadenceStats.overallAvgSpm.toInt()} spm",
                                color = Color(0xFF7E57C2),
                                modifier = Modifier.weight(1f)
                            )
                            HeartRateStatItem(
                                label = "Overall Max",
                                value = "${cadenceStats.overallMaxSpm} spm",
                                color = Color(0xFFD81B60),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val cadenceCoverage = if (cadenceStats.totalRunningActivities > 0) {
                            cadenceStats.activitiesWithCadence * 100 / cadenceStats.totalRunningActivities
                        } else {
                            0
                        }
                        Text(
                            text = "${cadenceStats.activitiesWithCadence} running activities with cadence · $cadenceCoverage% coverage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Text(
                    text = "Monthly SPM Trend",
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
                    CadenceTrendGraph(
                        monthlyCadenceStats = cadenceStats.monthlyCadenceStats,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "SPM Distribution",
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
                    CadenceDistributionGraph(
                        buckets = cadenceStats.distribution,
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
    onProgress: (Float, String, String, StatisticsLoadingPhase) -> Unit
): Map<Int, YearlyStatsData> {
    return withContext(Dispatchers.Default) {
        val yearGroups = events.groupBy { event ->
            event.eventDate.split("-")[0].toInt()
        }.toSortedMap(compareByDescending { it })

        if (yearGroups.isEmpty()) {
            withContext(Dispatchers.Main) {
                onProgress(
                    1f,
                    "No recorded activities found",
                    "There is nothing to calculate yet",
                    StatisticsLoadingPhase.TRENDS
                )
            }
            return@withContext emptyMap()
        }

        val totalYears = yearGroups.size
        val overallActivityCount = events.size.coerceAtLeast(1)
        val results = mutableMapOf<Int, YearlyStatsData>()

        yearGroups.entries.forEachIndexed { yearIndex, (year, yearEvents) ->
            withContext(Dispatchers.Main) {
                onProgress(
                    0.12f * (yearIndex.toFloat() / totalYears),
                    "Building trends for $year",
                    "Year ${yearIndex + 1} of $totalYears",
                    StatisticsLoadingPhase.TRENDS
                )
            }

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

            results[year] = YearlyStatsData(
                year = year,
                totalDistance = totalDistance,
                totalActivities = totalActivities,
                averageDistancePerActivity = averageDistancePerActivity,
                averageDistancePerMonth = averageDistancePerMonth,
                monthlyStats = monthlyStats,
                weeklyStats = weeklyStats,
                heartRateStats = null
            )

            withContext(Dispatchers.Main) {
                onProgress(
                    0.12f * ((yearIndex + 1f) / totalYears),
                    "Built trends for $year",
                    "Year ${yearIndex + 1} of $totalYears",
                    StatisticsLoadingPhase.TRENDS
                )
            }
        }

        var analyzedActivities = 0
        yearGroups.entries.forEach { (year, yearEvents) ->
            val heartRateStats = calculateHeartRateStats(
                year = year,
                events = yearEvents,
                database = database
            ) { completedInYear, _ ->
                val completedOverall = analyzedActivities + completedInYear
                val calculationProgress = completedOverall.toFloat() / overallActivityCount

                withContext(Dispatchers.Main) {
                    onProgress(
                        0.12f + (calculationProgress * 0.63f),
                        "Reading heart-rate data for $year",
                        "$completedOverall of ${events.size} activities analyzed",
                        StatisticsLoadingPhase.HEART_RATE
                    )
                }
            }

            results[year] = results.getValue(year).copy(heartRateStats = heartRateStats)
            analyzedActivities += yearEvents.size
        }

        val runningActivityCount = events.count { cadenceDisplayFor(it.artOfSport).isRunning }
        var analyzedRunningActivities = 0

        if (runningActivityCount == 0) {
            withContext(Dispatchers.Main) {
                onProgress(
                    1f,
                    "SPM analysis complete",
                    "No running activities with cadence were found",
                    StatisticsLoadingPhase.SPM
                )
            }
        } else {
            yearGroups.entries.forEach { (year, yearEvents) ->
                val runningYearEvents = yearEvents.filter {
                    cadenceDisplayFor(it.artOfSport).isRunning
                }
                if (runningYearEvents.isEmpty()) return@forEach

                val cadenceStats = calculateCadenceStats(
                    year = year,
                    events = runningYearEvents,
                    database = database
                ) { completedInYear, _ ->
                    val completedOverall = analyzedRunningActivities + completedInYear
                    val cadenceProgress = completedOverall.toFloat() / runningActivityCount

                    withContext(Dispatchers.Main) {
                        onProgress(
                            0.75f + (cadenceProgress * 0.25f),
                            "Calculating SPM for $year",
                            "$completedOverall of $runningActivityCount running activities analyzed",
                            StatisticsLoadingPhase.SPM
                        )
                    }
                }

                results[year] = results.getValue(year).copy(cadenceStats = cadenceStats)
                analyzedRunningActivities += runningYearEvents.size
            }
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

private suspend fun getAllEventsWithMetrics(
    database: FitnessTrackerDatabase,
    onProgress: suspend (progress: Float, message: String, detail: String) -> Unit
): List<EventWithTotalDistance> {
    return withContext(Dispatchers.IO) {
        val result = mutableListOf<EventWithTotalDistance>()

        try {
            withContext(Dispatchers.Main) {
                onProgress(0.02f, "Finding your activities", "Opening your training history")
            }

            val eventsFlow = database.eventDao().getRecordedEvents()
            val events = eventsFlow.first()

            if (events.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onProgress(1f, "No recorded activities found", "There is nothing to scan yet")
                }
                return@withContext emptyList()
            }

            // Get all event IDs at once
            val eventIds = events.map { it.eventId }

            val eventDistances = getMaxDistancesForEvents(database, eventIds) { completed, total ->
                withContext(Dispatchers.Main) {
                    onProgress(
                        0.05f + (completed.toFloat() / total * 0.90f),
                        "Reading activity details",
                        "$completed of $total activities scanned"
                    )
                }
            }

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

            withContext(Dispatchers.Main) {
                onProgress(1f, "Activities ready", "${result.size} activities loaded")
            }
        } catch (e: Exception) {
            Log.e("YearlyStatisticsScreen", "Error getting events: ${e.message}", e)
        }
        result
    }
}

private suspend fun getMaxDistancesForEvents(
    database: FitnessTrackerDatabase,
    eventIds: List<Int>,
    onProgress: suspend (completed: Int, total: Int) -> Unit
): Map<Int, Double> {
    return withContext(Dispatchers.IO) {
        try {
            val distances = mutableMapOf<Int, Double>()
            var completedEvents = 0

            // Keep the queries small and publish each completed activity to the UI.
            val chunkSize = 50
            eventIds.chunked(chunkSize).forEach { chunk ->
                chunk.forEach { eventId ->
                    try {
                        distances[eventId] = database.metricDao()
                            .getMaxDistanceForEvent(eventId) ?: 0.0
                    } catch (e: Exception) {
                        Log.w("YearlyStatisticsScreen", "Error getting distance for event $eventId: ${e.message}")
                        distances[eventId] = 0.0
                    }

                    completedEvents++
                    onProgress(completedEvents, eventIds.size)
                }
            }

            distances
        } catch (e: Exception) {
            Log.e("YearlyStatisticsScreen", "Error in batch distance query: ${e.message}", e)
            emptyMap()
        }
    }
}

private suspend fun calculateCadenceStats(
    year: Int,
    events: List<EventWithTotalDistance>,
    database: FitnessTrackerDatabase,
    onProgress: suspend (completed: Int, total: Int) -> Unit
): CadenceYearlyStats? {
    return withContext(Dispatchers.IO) {
        try {
            val allSpmValues = mutableListOf<Int>()
            val monthlySpmValues = (1..12).associateWith { mutableListOf<Int>() }
            val monthlyActivityCounts = mutableMapOf<Int, Int>()
            var activitiesWithCadence = 0

            events.forEachIndexed { index, event ->
                try {
                    val spmValues = database.metricDao()
                        .getCadenceValuesForEvent(event.eventId)
                        .map { rawCadence -> rawCadence * 2 }

                    if (spmValues.isNotEmpty()) {
                        activitiesWithCadence++
                        allSpmValues.addAll(spmValues)
                        val month = event.eventDate.split("-")[1].toInt()
                        monthlySpmValues.getValue(month).addAll(spmValues)
                        monthlyActivityCounts[month] = (monthlyActivityCounts[month] ?: 0) + 1
                    }
                } catch (e: Exception) {
                    Log.w("CadenceStats", "Error getting cadence for event ${event.eventId}: ${e.message}")
                }

                onProgress(index + 1, events.size)
            }

            if (allSpmValues.isEmpty()) return@withContext null

            val monthlyStats = (1..12).map { month ->
                val values = monthlySpmValues.getValue(month)
                MonthlyCadenceStats(
                    year = year,
                    month = month,
                    minSpm = values.minOrNull() ?: 0,
                    avgSpm = if (values.isNotEmpty()) values.average() else 0.0,
                    maxSpm = values.maxOrNull() ?: 0,
                    activitiesWithCadence = monthlyActivityCounts[month] ?: 0
                )
            }

            val distribution = cadenceDistributionFor(allSpmValues)

            CadenceYearlyStats(
                year = year,
                overallMinSpm = allSpmValues.minOrNull() ?: 0,
                overallAvgSpm = allSpmValues.average(),
                overallMaxSpm = allSpmValues.maxOrNull() ?: 0,
                activitiesWithCadence = activitiesWithCadence,
                totalRunningActivities = events.size,
                monthlyCadenceStats = monthlyStats,
                distribution = distribution
            )
        } catch (e: Exception) {
            Log.e("CadenceStats", "Error calculating SPM statistics for $year: ${e.message}", e)
            null
        }
    }
}

internal fun cadenceDistributionFor(spmValues: List<Int>): List<CadenceBucketStats> = listOf(
    CadenceBucketStats("<140", spmValues.count { it < 140 }),
    CadenceBucketStats("140-149", spmValues.count { it in 140..149 }),
    CadenceBucketStats("150-159", spmValues.count { it in 150..159 }),
    CadenceBucketStats("160-169", spmValues.count { it in 160..169 }),
    CadenceBucketStats("170-179", spmValues.count { it in 170..179 }),
    CadenceBucketStats("180+", spmValues.count { it >= 180 })
)

private suspend fun calculateHeartRateStats(
    year: Int,
    events: List<EventWithTotalDistance>,
    database: FitnessTrackerDatabase,
    onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }
): HeartRateYearlyStats? {
    return withContext(Dispatchers.IO) {
        try {
            // Batch get heart rate data for all events at once
            val eventIds = events.map { it.eventId }
            val heartRateData = getBatchHeartRateData(database, eventIds, onProgress)

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
private suspend fun getBatchHeartRateData(
    database: FitnessTrackerDatabase,
    eventIds: List<Int>,
    onProgress: suspend (completed: Int, total: Int) -> Unit
): Map<Int, List<Int>> {
    return withContext(Dispatchers.IO) {
        try {
            val heartRateData = mutableMapOf<Int, List<Int>>()
            var completedEvents = 0

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

                    completedEvents++
                    onProgress(completedEvents, eventIds.size)
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
