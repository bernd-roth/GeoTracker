package at.co.netconsulting.geotracker.composables

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.Metric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AltitudeDetailScreen(
    eventName: String,
    metrics: List<Metric>,
    onBackClick: () -> Unit
) {
    // Filter metrics that have elevation data
    val validMetrics = metrics.filter { it.elevation > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Altitude analysis",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Event name header
            Text(
                text = eventName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (validMetrics.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No altitude data available for this event",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                // Altitude Statistics Summary
                AltitudeStatsSummary(validMetrics)

                // Distance vs Altitude Graph
                AltitudeGraphCard(
                    title = "Distance vs Altitude",
                    description = "Tap on the graph to see elevation at any distance"
                ) {
                    InteractiveDistanceVsAltitudeGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Speed vs Altitude Graph
                AltitudeGraphCard(
                    title = "Speed vs Altitude",
                    description = "Tap on the graph to see speed at different elevations"
                ) {
                    InteractiveSpeedVsAltitudeGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Altitude Change Rate Graph
                AltitudeGraphCard(
                    title = "Altitude Change Rate",
                    description = "Tap on the graph to see climbing/descending rates over time"
                ) {
                    InteractiveAltitudeChangeRateGraph(
                        metrics = validMetrics,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AltitudeStatsSummary(metrics: List<Metric>) {
    val elevations = metrics.map { it.elevation }
    val minElevation = elevations.minOrNull() ?: 0f
    val maxElevation = elevations.maxOrNull() ?: 0f
    val totalElevationGain = calculateElevationGain(metrics)
    val totalElevationLoss = calculateElevationLoss(metrics)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Altitude Summary",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AltitudeStatItem(
                    label = "Min",
                    value = "${minElevation.toInt()} m",
                    color = Color.Blue
                )
                AltitudeStatItem(
                    label = "Max",
                    value = "${maxElevation.toInt()} m",
                    color = Color.Red
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AltitudeStatItem(
                    label = "Total Gain",
                    value = "+${totalElevationGain.toInt()} m",
                    color = Color(0xFF4CAF50)
                )
                AltitudeStatItem(
                    label = "Total Loss",
                    value = "-${totalElevationLoss.toInt()} m",
                    color = Color(0xFFFF5722)
                )
            }
        }
    }
}

@Composable
fun AltitudeStatItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun AltitudeGraphCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(8.dp)
            ) {
                content()
            }
        }
    }
}

private fun calculateElevationGain(metrics: List<Metric>): Double {
    var totalGain = 0.0
    for (i in 1 until metrics.size) {
        val diff = metrics[i].elevation - metrics[i - 1].elevation
        if (diff > 0) {
            totalGain += diff
        }
    }
    return totalGain
}

private fun calculateElevationLoss(metrics: List<Metric>): Double {
    var totalLoss = 0.0
    for (i in 1 until metrics.size) {
        val diff = metrics[i - 1].elevation - metrics[i].elevation
        if (diff > 0) {
            totalLoss += diff
        }
    }
    return totalLoss
}