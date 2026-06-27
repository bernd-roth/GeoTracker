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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadenceDetailScreen(
    eventId: Int,
    eventName: String,
    sportType: String,
    metrics: List<Metric>,
    onBackClick: () -> Unit
) {
    val cadenceValues = metrics.mapNotNull { it.cadence?.takeIf { value -> value > 0 } }
    val cadenceDisplay = cadenceDisplayFor(sportType)
    val context = LocalContext.current
    var selectedMetricIndex by remember(eventId, metrics) {
        mutableStateOf(metrics.indexOfFirst { (it.cadence ?: 0) > 0 }.takeIf { it >= 0 })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cadence Analysis", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = eventName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (cadenceValues.size < 2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No cadence data available for this event")
                    }
                }
            } else {
                CadenceSummary(cadenceValues, cadenceDisplay)
                CadenceGraphCard(
                    title = "Cadence vs Time",
                    description = "Tap a cadence point to locate it on the map"
                ) {
                    InteractiveCadenceGraph(
                        metrics = metrics,
                        xAxis = CadenceRelationXAxis.TIME,
                        selectedMetricIndex = selectedMetricIndex,
                        onPointSelected = { selectedMetricIndex = it },
                        displayMultiplier = cadenceDisplay.multiplier,
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
                CadenceGraphCard(
                    title = "Cadence vs Distance",
                    description = "Tap a cadence point to locate it on the map"
                ) {
                    InteractiveCadenceGraph(
                        metrics = metrics,
                        xAxis = CadenceRelationXAxis.DISTANCE,
                        selectedMetricIndex = selectedMetricIndex,
                        onPointSelected = { selectedMetricIndex = it },
                        displayMultiplier = cadenceDisplay.multiplier,
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
                CadenceGraphCard(
                    title = "Cadence vs Altitude",
                    description = "Cadence distribution by recorded altitude; tap to select a sample"
                ) {
                    InteractiveCadenceGraph(
                        metrics = metrics,
                        xAxis = CadenceRelationXAxis.ALTITUDE,
                        selectedMetricIndex = selectedMetricIndex,
                        onPointSelected = { selectedMetricIndex = it },
                        displayMultiplier = cadenceDisplay.multiplier,
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
                CadenceGraphCard(
                    title = "Cadence vs Speed",
                    description = "Cadence distribution by speed in km/h; tap to select a sample"
                ) {
                    InteractiveCadenceGraph(
                        metrics = metrics,
                        xAxis = CadenceRelationXAxis.SPEED,
                        selectedMetricIndex = selectedMetricIndex,
                        onPointSelected = { selectedMetricIndex = it },
                        displayMultiplier = cadenceDisplay.multiplier,
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                }
                CadenceGraphCard(
                    title = "Selected Cadence Point",
                    description = "The selected graph point is highlighted on the recorded route"
                ) {
                    CadencePointMap(
                        database = FitnessTrackerDatabase.getInstance(context),
                        eventId = eventId,
                        metrics = metrics,
                        selectedMetricIndex = selectedMetricIndex,
                        cadenceDisplay = cadenceDisplay,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CadenceSummary(values: List<Int>, display: CadenceDisplay) {
    val displayValues = values.map(display::value)
    val average = displayValues.average().roundToInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Cadence Summary", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CadenceStat("Minimum", displayValues.minOrNull() ?: 0, display.unit)
                CadenceStat("Average", average, display.unit)
                CadenceStat("Maximum", displayValues.maxOrNull() ?: 0, display.unit)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (display.isRunning) {
                    "Running cadence is shown in steps per minute, counting both feet."
                } else {
                    "Cadence is shown in ${display.unit}."
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun CadenceStat(label: String, value: Int, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7E57C2))
        Text(unit, fontSize = 11.sp, color = Color.Gray)
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun CadenceGraphCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
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
