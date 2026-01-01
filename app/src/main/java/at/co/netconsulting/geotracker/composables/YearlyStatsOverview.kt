package at.co.netconsulting.geotracker.composables

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.data.EventWithTotalDistance
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.repository.MetricDao
import at.co.netconsulting.geotracker.viewmodel.EventsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun YearlyStatsOverview(
    modifier: Modifier = Modifier,
    eventsViewModel: EventsViewModel,
    onWeekSelected: (Int, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var yearlyStats by remember { mutableStateOf<Map<Int, Double>>(emptyMap()) }
    var weeklyStats by remember { mutableStateOf<Map<Pair<Int, Int>, Double>>(emptyMap()) } // (Year, WeekNumber) -> Distance
    var expandedYear by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val database = remember { FitnessTrackerDatabase.getInstance(context) }

    // Current year for highlighting
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    LaunchedEffect(Unit) {

        isLoading = true
        coroutineScope.launch {
            try {
                // For statistics, we need actual distances, so use the full method
                val events = getAllEventsWithMetrics(database)

                // Calculate yearly totals
                val yearTotals = events
                    .groupBy { event ->
                        // Extract year from eventDate (format: YYYY-MM-DD)
                        event.eventDate.split("-")[0].toInt()
                    }
                    .mapValues { (_, eventsInYear) ->
                        eventsInYear.sumOf { it.totalDistance / 1000.0 } // Convert to km
                    }

                // Calculate weekly totals
                val weekTotals = events
                    .groupBy { event ->
                        val dateParts = event.eventDate.split("-")
                        val year = dateParts[0].toInt()

                        val calendar = Calendar.getInstance().apply {
                            firstDayOfWeek = Calendar.MONDAY // Ensure Monday is first day
                            minimalDaysInFirstWeek = 4 // ISO 8601 standard
                            set(year, dateParts[1].toInt() - 1, dateParts[2].toInt())
                        }

                        val week = calendar.get(Calendar.WEEK_OF_YEAR)
                        Log.d("YearlyStatsOverview", "Event: ${event.eventName}, Date: ${event.eventDate}, " +
                                "Week: $week, Distance: ${"%.2f".format(event.totalDistance/1000.0)} km")
                        Pair(year, week)
                    }
                    .mapValues { (_, eventsInWeek) ->
                        eventsInWeek.sumOf { it.totalDistance / 1000.0 } // Convert to km
                    }

                yearlyStats = yearTotals
                weeklyStats = weekTotals
                isLoading = false
            } catch (e: Exception) {
                Log.e("YearlyStatsOverview", "Error loading stats", e)
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Distance Statistics",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else if (yearlyStats.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    text = "No activity data available",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            // Year cards
            yearlyStats.toSortedMap(compareByDescending { it }).forEach { (year, totalDistance) ->
                YearCard(
                    year = year,
                    totalDistance = totalDistance,
                    isExpanded = expandedYear == year,
                    isCurrentYear = year == currentYear,
                    onClick = {
                        expandedYear = if (expandedYear == year) null else year
                    }
                )

                // Show weekly breakdown if year is expanded
                if (expandedYear == year) {
                    WeeklyBreakdown(
                        year = year,
                        weeklyStats = weeklyStats.filter { it.key.first == year },
                        onWeekSelected = onWeekSelected
                    )
                }
            }
        }
    }
}

@Composable
fun YearCard(
    year: Int,
    totalDistance: Double,
    isExpanded: Boolean,
    isCurrentYear: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentYear) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format("%.1f km", totalDistance),
                    style = MaterialTheme.typography.bodyLarge
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
    }
}

@Composable
fun WeeklyBreakdown(
    year: Int,
    weeklyStats: Map<Pair<Int, Int>, Double>,
    onWeekSelected: (Int, Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Weekly Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Calendar instance to get week dates
            val calendar = Calendar.getInstance().apply {
                firstDayOfWeek = Calendar.MONDAY
                minimalDaysInFirstWeek = 4
                set(Calendar.YEAR, year)
            }

            // Current week for highlighting
            val currentWeek = if (Calendar.getInstance().get(Calendar.YEAR) == year) {
                Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
            } else {
                -1
            }

            if (weeklyStats.isEmpty()) {
                Text(
                    text = "No weekly data available for $year",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp),
                    color = Color.Gray
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Sort weeks in descending order
                    val sortedWeeks = weeklyStats.keys
                        .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }
                            .thenByDescending { it.second })

                    sortedWeeks.forEach { (_, week) ->
                        // Set calendar to the week in question
                        calendar.set(Calendar.WEEK_OF_YEAR, week)
                        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) // Start of week

                        val startDay = calendar.get(Calendar.DAY_OF_MONTH)
                        val startMonth = calendar.get(Calendar.MONTH) + 1 // Months are 0-indexed

                        // End of week (Sunday)
                        calendar.add(Calendar.DAY_OF_MONTH, 6)
                        val endDay = calendar.get(Calendar.DAY_OF_MONTH)
                        val endMonth = calendar.get(Calendar.MONTH) + 1

                        val distance = weeklyStats[Pair(year, week)] ?: 0.0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (week == currentWeek) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { onWeekSelected(year, week) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Week $week ($startDay/$startMonth - $endDay/$endMonth)",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = String.format("%.1f km", distance),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

// Fast loading: Get basic events with minimal distance calculation
private suspend fun getAllEventsBasic(database: FitnessTrackerDatabase): List<EventWithTotalDistance> {
    return withContext(Dispatchers.IO) {
        val result = mutableListOf<EventWithTotalDistance>()

        try {
            val eventsFlow = database.eventDao().getAllEvents()
            val events = eventsFlow.first()

            // Process events with basic distance calculation
            events.forEach { event ->
                // For basic loading, add events without distance calculation
                // Distance will be loaded later when statistics are actually needed
                result.add(
                    EventWithTotalDistance(
                        eventId = event.eventId,
                        eventName = event.eventName,
                        artOfSport = event.artOfSport,
                        eventDate = event.eventDate,
                        totalDistance = 0.0 // Will be calculated when needed
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("YearlyStatsOverview", "Error getting basic events: ${e.message}", e)
        }
        result
    }
}

// Helper function to get events from the database with distance
private suspend fun getAllEventsWithMetrics(database: FitnessTrackerDatabase): List<EventWithTotalDistance> {
    // Use withContext to move to IO dispatcher for database operations
    return withContext(Dispatchers.IO) {
        val result = mutableListOf<EventWithTotalDistance>()

        try {
            // Get events as a Flow and collect the latest value
            val eventsFlow = database.eventDao().getAllEvents()
            val events = eventsFlow.first() // Get the first emission from the Flow

            // Process each event to get its metrics
            events.forEach { event ->
                try {
                    val metrics = database.metricDao().getMetricsByEventId(event.eventId)

                    // Calculate total distance for this event (max distance represents total at end of event)
                    val totalDistance = metrics.maxOfOrNull { it.distance } ?: 0.0

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
                    Log.e("YearlyStatsOverview", "Error processing event ${event.eventId}: ${e.message}", e)
                    // Continue processing other events
                }
            }
        } catch (e: Exception) {
            Log.e("YearlyStatsOverview", "Error getting events: ${e.message}", e)
        }
        result
    }
}