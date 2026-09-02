package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.achievements.AchievementActivity
import at.co.netconsulting.geotracker.achievements.AchievementCalculator
import at.co.netconsulting.geotracker.achievements.AchievementDefinition
import at.co.netconsulting.geotracker.achievements.AchievementKind
import at.co.netconsulting.geotracker.achievements.AchievementRecord
import at.co.netconsulting.geotracker.achievements.AchievementSample
import at.co.netconsulting.geotracker.achievements.SportAchievements
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

private enum class AchievementsTab(val title: String) {
    PERSONAL_BESTS("Personal bests"),
    ACTIVITY_SUMMARY("Activity summary")
}

@Composable
fun AchievementsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember { FitnessTrackerDatabase.getInstance(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var sports by remember { mutableStateOf<List<SportAchievements>>(emptyList()) }
    var selectedSport by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(database) {
        isLoading = true
        errorMessage = null
        try {
            val events = withContext(Dispatchers.IO) {
                database.eventDao().getRecordedEvents().first()
            }
            val activities = ArrayList<AchievementActivity>(events.size)

            events.forEachIndexed { index, event ->
                val metrics = withContext(Dispatchers.IO) {
                    database.metricDao().getMetricsForEvent(event.eventId)
                }
                activities += AchievementActivity(
                    eventId = event.eventId,
                    eventName = event.eventName,
                    eventDate = event.eventDate,
                    sport = event.artOfSport,
                    sportFamily = event.sportFamily,
                    discipline = event.discipline,
                    eventFormat = event.eventFormat,
                    samples = metrics.map { metric ->
                        AchievementSample(
                            timeMillis = metric.timeInMilliseconds,
                            distanceMeters = metric.distance
                        )
                    }
                )
                loadingProgress = if (events.isEmpty()) 1f else (index + 1f) / events.size
            }

            sports = withContext(Dispatchers.Default) {
                AchievementCalculator.calculate(activities)
            }
            selectedSport = selectedSport
                ?.takeIf { selected -> sports.any { it.sport == selected } }
                ?: sports.firstOrNull()?.sport
        } catch (exception: Exception) {
            errorMessage = exception.message ?: "Unable to calculate achievements"
        } finally {
            isLoading = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            AchievementsTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(tab.title) }
                )
            }
        }

        when (AchievementsTab.entries[selectedTab]) {
            AchievementsTab.PERSONAL_BESTS -> PersonalBestsContent(
                sports = sports,
                selectedSport = selectedSport,
                isLoading = isLoading,
                loadingProgress = loadingProgress,
                errorMessage = errorMessage,
                onSportSelected = { selectedSport = it },
                modifier = Modifier.weight(1f)
            )

            AchievementsTab.ACTIVITY_SUMMARY -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                YearlyStatsOverview(
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonalBestsContent(
    sports: List<SportAchievements>,
    selectedSport: String?,
    isLoading: Boolean,
    loadingProgress: Float,
    errorMessage: String?,
    onSportSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            isLoading -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Scanning recorded activities")
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { loadingProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            errorMessage != null -> EmptyAchievementsCard(
                title = "Achievements could not be loaded",
                description = errorMessage
            )

            sports.isEmpty() -> EmptyAchievementsCard(
                title = "No achievements yet",
                description = "Complete a recorded activity to start your personal-best history."
            )

            else -> {
                val currentSport = sports.firstOrNull { it.sport == selectedSport } ?: sports.first()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    AchievementsHeader(sports)
                    SportSelector(
                        sports = sports,
                        selectedSport = currentSport.sport,
                        onSportSelected = onSportSelected
                    )
                    SportOverview(currentSport)
                    AchievementSection(
                        title = "Fastest distance",
                        subtitle = "Shortest elapsed time for a continuous segment",
                        definitions = currentSport.distanceDefinitions,
                        records = currentSport.records
                    )
                    AchievementSection(
                        title = "Timed distance",
                        subtitle = "Greatest distance in one continuous time window",
                        definitions = currentSport.durationDefinitions,
                        records = currentSport.records
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementsHeader(sports: List<SportAchievements>) {
    val recordCount = sports.sumOf { it.records.size }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "$recordCount personal best${if (recordCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Across ${sports.size} recorded sport ${if (sports.size == 1) "type" else "types"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SportSelector(
    sports: List<SportAchievements>,
    selectedSport: String,
    onSportSelected: (String) -> Unit
) {
    Text(
        text = "Sport",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sports.forEach { sport ->
            FilterChip(
                selected = sport.sport == selectedSport,
                onClick = { onSportSelected(sport.sport) },
                label = { Text(sport.sport) }
            )
        }
    }
}

@Composable
private fun SportOverview(sport: SportAchievements) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Activities", style = MaterialTheme.typography.labelMedium)
                Text(
                    sport.activityCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Total distance", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatRecordDistance(sport.totalDistanceMeters),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AchievementSection(
    title: String,
    subtitle: String,
    definitions: List<AchievementDefinition>,
    records: Map<AchievementDefinition, AchievementRecord>
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        definitions.forEachIndexed { index, definition ->
            AchievementRow(definition, records[definition])
            if (index < definitions.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun AchievementRow(
    definition: AchievementDefinition,
    record: AchievementRecord?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = definition.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.28f)
        )
        Column(modifier = Modifier.weight(0.72f)) {
            Text(
                text = when {
                    record == null -> "—"
                    definition.kind == AchievementKind.DISTANCE -> {
                        formatRecordDuration(record.value.roundToLong())
                    }
                    else -> formatRecordDistance(record.value)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (record == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = record?.let {
                    listOfNotNull(
                        it.eventName,
                        it.eventDate,
                        it.discipline,
                        it.eventFormat
                    ).joinToString(" · ")
                }
                    ?: "No qualifying activity yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyAchievementsCard(title: String, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRecordDistance(distanceMeters: Double): String =
    if (distanceMeters < 1_000.0) {
        "${distanceMeters.roundToLong()} m"
    } else {
        String.format("%.2f km", distanceMeters / 1_000.0)
    }

private fun formatRecordDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds)
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}
