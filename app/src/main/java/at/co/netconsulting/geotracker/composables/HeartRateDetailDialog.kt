package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import at.co.netconsulting.geotracker.data.HeartRateData
import kotlinx.coroutines.delay
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.max
import kotlin.math.min

@Composable
fun HeartRateDetailDialog(
    onDismiss: () -> Unit
) {
    var currentHeartRate by remember { mutableIntStateOf(0) }
    var deviceName by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var maxHeartRate by remember { mutableIntStateOf(0) }
    var minHeartRate by remember { mutableIntStateOf(999) }
    var avgHeartRate by remember { mutableIntStateOf(0) }

    // Keep a history of heart rate values for the chart
    val heartRateHistory = remember { mutableStateListOf<Int>() }
    val maxHistoryPoints = 60 // Show up to 60 seconds of data

    // Subscribe to heart rate updates via EventBus
    DisposableEffect(Unit) {
        val eventBusListener = object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onHeartRateData(data: HeartRateData) {
                if (data.heartRate > 0) {
                    currentHeartRate = data.heartRate

                    // Update the history list
                    if (heartRateHistory.size >= maxHistoryPoints) {
                        heartRateHistory.removeAt(0)
                    }
                    heartRateHistory.add(currentHeartRate)

                    // Update stats
                    maxHeartRate = max(maxHeartRate, currentHeartRate)
                    minHeartRate = if (minHeartRate == 999) currentHeartRate else min(minHeartRate, currentHeartRate)
                    avgHeartRate = heartRateHistory.sum() / heartRateHistory.size
                }
                deviceName = data.deviceName
                isConnected = data.isConnected
            }
        }

        // Register with EventBus
        if (!EventBus.getDefault().isRegistered(eventBusListener)) {
            EventBus.getDefault().register(eventBusListener)
        }

        onDispose {
            if (EventBus.getDefault().isRegistered(eventBusListener)) {
                EventBus.getDefault().unregister(eventBusListener)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart Rate",
                            tint = Color.Red,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Heart Rate Monitor",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Current heart rate display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (currentHeartRate > 0) "$currentHeartRate" else "---",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "beats per minute",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        if (deviceName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Device: $deviceName",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Heart rate statistics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Min heart rate
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "MIN",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (minHeartRate < 999) "$minHeartRate" else "---",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Average heart rate
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "AVG",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (avgHeartRate > 0) "$avgHeartRate" else "---",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Max heart rate
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "MAX",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (maxHeartRate > 0) "$maxHeartRate" else "---",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Heart rate chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (heartRateHistory.isNotEmpty()) {
                        HeartRateChart(
                            heartRateHistory = heartRateHistory.toList(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Waiting for heart rate data...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Connection status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isConnected) Color.Green else Color.Red,
                                shape = RoundedCornerShape(4.dp)
                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isConnected) "Connected" else "Disconnected",
                        fontSize = 14.sp,
                        color = if (isConnected)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun HeartRateChart(
    heartRateHistory: List<Int>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .height(120.dp)
            .padding(4.dp)
    ) {
        if (heartRateHistory.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val maxValue = max(heartRateHistory.maxOrNull() ?: 100, 100) // Ensure we have a reasonable max
        val minValue = min(heartRateHistory.minOrNull() ?: 60, 60)   // Ensure we have a reasonable min
        val range = maxValue - minValue + 20 // Add padding

        // Calculate x step based on width and number of points
        val xStep = width / (heartRateHistory.size - 1).coerceAtLeast(1)

        // Create a path for the line
        val path = Path()

        // Define the y-position converting function
        val getYPosition = { value: Int ->
            height - ((value - minValue + 10) / range.toFloat() * height)
        }

        // Start the path at the first point
        if (heartRateHistory.isNotEmpty()) {
            val firstY = getYPosition(heartRateHistory.first())
            path.moveTo(0f, firstY)

            // Add points to the path
            heartRateHistory.forEachIndexed { index, value ->
                if (index > 0) {
                    val x = index * xStep
                    val y = getYPosition(value)
                    path.lineTo(x, y)
                }
            }
        }

        // Draw the path
        drawPath(
            path = path,
            color = Color.Red,
            style = Stroke(
                width = 3f,
                cap = StrokeCap.Round
            )
        )

        // Draw points at each heart rate measurement
        heartRateHistory.forEachIndexed { index, value ->
            val x = index * xStep
            val y = getYPosition(value)

            drawCircle(
                color = Color.Red,
                radius = 3f,
                center = Offset(x, y)
            )
        }
    }
}