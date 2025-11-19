package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.co.netconsulting.geotracker.data.RouteComparison
import at.co.netconsulting.geotracker.data.RouteSimilarity
import at.co.netconsulting.geotracker.viewmodel.RouteComparisonViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteComparisonScreen(
    eventId: Int,
    onNavigateBack: () -> Unit,
    viewModel: RouteComparisonViewModel = viewModel()
) {
    val similarRoutes by viewModel.similarRoutes.collectAsState()
    val currentComparison by viewModel.currentComparison.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val eventName by viewModel.eventName.collectAsState()
    val comparisonProgress by viewModel.comparisonProgress.collectAsState()

    // Load similar routes when screen opens
    LaunchedEffect(eventId) {
        android.util.Log.d("RouteComparisonScreen", "Screen loaded for eventId: $eventId")
        viewModel.findSimilarRoutes(eventId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare Routes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // Show comparison detail first (highest priority - user clicked on a match)
                currentComparison != null -> {
                    RouteComparisonDetail(
                        comparison = currentComparison!!,
                        onBack = { viewModel.clearComparison() }
                    )
                }
                // Show live progress view
                comparisonProgress != null -> {
                    LiveComparisonProgress(
                        progress = comparisonProgress!!,
                        onRouteSelected = { similarRoute ->
                            viewModel.compareRoutes(eventId, similarRoute.eventId)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Show errors
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                }
                // Fallback loading indicator
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                similarRoutes.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Similar Routes Found",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Complete more routes on similar paths to see comparisons",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    SimilarRoutesList(
                        eventName = eventName,
                        similarRoutes = similarRoutes,
                        onRouteSelected = { similarRoute ->
                            viewModel.compareRoutes(eventId, similarRoute.eventId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarRoutesList(
    eventName: String,
    similarRoutes: List<RouteSimilarity>,
    onRouteSelected: (RouteSimilarity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Comparing: $eventName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Found ${similarRoutes.size} similar route(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        items(similarRoutes) { similarRoute ->
            SimilarRouteCard(
                similarRoute = similarRoute,
                onClick = { onRouteSelected(similarRoute) }
            )
        }
    }
}

@Composable
private fun SimilarRouteCard(
    similarRoute: RouteSimilarity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = similarRoute.eventName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = similarRoute.eventDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Similarity score badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = getSimilarityColor(similarRoute.similarityScore)
                ) {
                    Text(
                        text = "${(similarRoute.similarityScore * 100).toInt()}% match",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Additional details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailChip(
                    icon = Icons.Default.Place,
                    label = "Start: ${similarRoute.startPointDistance.toInt()}m"
                )
                DetailChip(
                    icon = Icons.Default.LocationOn,
                    label = "End: ${similarRoute.endPointDistance.toInt()}m"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Tap to compare",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.CenterVertically),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DetailChip(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RouteComparisonDetail(
    comparison: RouteComparison,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back to list")
            }
            Text(
                text = "Route Comparison",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Event names
        ComparisonHeader(comparison)

        Spacer(modifier = Modifier.height(24.dp))

        // Overall performance summary
        OverallPerformanceCard(comparison)

        Spacer(modifier = Modifier.height(16.dp))

        // Time comparison
        MetricComparisonCard(
            title = "Time",
            primaryValue = formatDuration(comparison.primaryEvent.endTime - comparison.primaryEvent.startTime),
            comparisonValue = formatDuration(comparison.comparisonEvent.endTime - comparison.comparisonEvent.startTime),
            difference = formatDurationDifference(comparison.timeDifference),
            isImprovement = comparison.isFasterOverall,
            icon = Icons.Default.Timer
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Speed comparison
        MetricComparisonCard(
            title = "Average Speed",
            primaryValue = String.format("%.2f km/h", comparison.primaryEvent.averageSpeed),
            comparisonValue = String.format("%.2f km/h", comparison.comparisonEvent.averageSpeed),
            difference = String.format("%+.2f km/h", comparison.avgSpeedDifference),
            isImprovement = comparison.avgSpeedDifference > 0,
            icon = Icons.Default.Speed
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Elevation comparison
        MetricComparisonCard(
            title = "Elevation Gain",
            primaryValue = String.format("%.1f m", comparison.primaryEvent.elevationGain),
            comparisonValue = String.format("%.1f m", comparison.comparisonEvent.elevationGain),
            difference = String.format("%+.1f m", comparison.elevationGainDifference),
            isImprovement = comparison.hasLessElevationGain,
            icon = Icons.Default.Terrain
        )

        // Heart rate comparisons (if available)
        if (comparison.avgHeartRateDifference != null) {
            Spacer(modifier = Modifier.height(12.dp))
            MetricComparisonCard(
                title = "Average Heart Rate",
                primaryValue = "${comparison.primaryAvgHeartRate} bpm",
                comparisonValue = "${comparison.comparisonAvgHeartRate} bpm",
                difference = "${if (comparison.avgHeartRateDifference > 0) "+" else ""}${comparison.avgHeartRateDifference} bpm",
                isImprovement = comparison.avgHeartRateDifference < 0, // Lower HR is better for same effort
                icon = Icons.Default.Favorite
            )
        }

        if (comparison.maxHeartRateDifference != null) {
            Spacer(modifier = Modifier.height(12.dp))
            MetricComparisonCard(
                title = "Max Heart Rate",
                primaryValue = "${comparison.primaryMaxHeartRate} bpm",
                comparisonValue = "${comparison.comparisonMaxHeartRate} bpm",
                difference = "${if (comparison.maxHeartRateDifference > 0) "+" else ""}${comparison.maxHeartRateDifference} bpm",
                isImprovement = comparison.maxHeartRateDifference < 0,
                icon = Icons.Default.FavoriteBorder
            )
        }

        if (comparison.minHeartRateDifference != null) {
            Spacer(modifier = Modifier.height(12.dp))
            MetricComparisonCard(
                title = "Min Heart Rate",
                primaryValue = "${comparison.primaryMinHeartRate} bpm",
                comparisonValue = "${comparison.comparisonMinHeartRate} bpm",
                difference = "${if (comparison.minHeartRateDifference > 0) "+" else ""}${comparison.minHeartRateDifference} bpm",
                isImprovement = comparison.minHeartRateDifference < 0,
                icon = Icons.Default.MonitorHeart
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Segment breakdown
        if (comparison.segmentComparisons.isNotEmpty()) {
            SegmentBreakdownCard(comparison.segmentComparisons)
        }
    }
}

@Composable
private fun ComparisonHeader(comparison: RouteComparison) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Primary",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = comparison.primaryEvent.event.eventName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = comparison.primaryEvent.event.eventDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.CompareArrows,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "Comparison",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = comparison.comparisonEvent.event.eventName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = comparison.comparisonEvent.event.eventDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OverallPerformanceCard(comparison: RouteComparison) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (comparison.isFasterOverall)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                if (comparison.isFasterOverall) Icons.Default.TrendingUp else Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (comparison.isFasterOverall)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (comparison.isFasterOverall) "Faster!" else "Similar Performance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    comparison.isFasterOverall && comparison.hasBetterPacing ->
                        "Improved time with better pacing"
                    comparison.isFasterOverall ->
                        "Improved overall time"
                    comparison.hasBetterPacing ->
                        "Better pacing consistency"
                    else ->
                        "Keep training for improvements"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricComparisonCard(
    title: String,
    primaryValue: String,
    comparisonValue: String,
    difference: String,
    isImprovement: Boolean,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Primary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = primaryValue,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Comparison",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = comparisonValue,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Difference indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (isImprovement) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isImprovement) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Text(
                    text = difference,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isImprovement) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SegmentBreakdownCard(segments: List<at.co.netconsulting.geotracker.data.SegmentComparison>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Segment Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Compare performance across route segments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            segments.forEach { segment ->
                SegmentComparisonRow(segment)
                if (segment != segments.last()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SegmentComparisonRow(segment: at.co.netconsulting.geotracker.data.SegmentComparison) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Segment header with distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Segment ${segment.segmentIndex + 1}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${String.format("%.1f", segment.startDistanceMeters / 1000)} - ${String.format("%.1f", segment.endDistanceMeters / 1000)} km",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time comparison
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Time:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(80.dp)
            )

            val isFaster = segment.timeDifference < 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    if (isFaster) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (isFaster) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Text(
                    text = formatDurationDifference(segment.timeDifference),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFaster) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Heart rate comparison (if available)
        if (segment.primaryAvgHeartRate != null && segment.comparisonAvgHeartRate != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Heart Rate:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(80.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${segment.primaryAvgHeartRate} bpm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${segment.comparisonAvgHeartRate} bpm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    segment.heartRateDifference?.let { hrDiff ->
                        val isLowerHr = hrDiff < 0
                        Text(
                            text = "(${if (hrDiff > 0) "+" else ""}$hrDiff)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLowerHr) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Helper functions
private fun getSimilarityColor(score: Double): Color {
    return when {
        score >= 0.9 -> Color(0xFF4CAF50) // Green
        score >= 0.8 -> Color(0xFF8BC34A) // Light green
        score >= 0.7 -> Color(0xFFFFC107) // Amber
        else -> Color(0xFFFF9800) // Orange
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatDurationDifference(millis: Long): String {
    val absMillis = Math.abs(millis)
    val sign = if (millis < 0) "-" else "+"
    val hours = TimeUnit.MILLISECONDS.toHours(absMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(absMillis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(absMillis) % 60

    return when {
        hours > 0 -> String.format("%s%d:%02d:%02d", sign, hours, minutes, seconds)
        else -> String.format("%s%d:%02d", sign, minutes, seconds)
    }
}

@Composable
private fun LiveComparisonProgress(
    progress: RouteComparisonViewModel.ComparisonProgress,
    onRouteSelected: (RouteSimilarity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with progress
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Comparing Routes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${progress.currentIndex}/${progress.totalCandidates}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = progress.currentIndex.toFloat() / progress.totalCandidates.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Current event being compared
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Checking: ${progress.currentEventName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (progress.matchesFound.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${progress.matchesFound.size} match${if (progress.matchesFound.size == 1) "" else "es"} found so far",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Matches found so far
        if (progress.matchesFound.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Matches Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap to compare now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            items(progress.matchesFound) { similarRoute ->
                SimilarRouteCard(
                    similarRoute = similarRoute,
                    onClick = { onRouteSelected(similarRoute) }
                )
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Searching for similar routes...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
