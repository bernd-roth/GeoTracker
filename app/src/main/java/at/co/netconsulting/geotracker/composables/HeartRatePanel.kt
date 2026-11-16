package at.co.netconsulting.geotracker.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.data.HeartRateData
import at.co.netconsulting.geotracker.service.HeartRateSensorService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@Composable
fun HeartRatePanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sensorService = remember { HeartRateSensorService.getInstance(context) }
    var currentHeartRate by remember { mutableIntStateOf(sensorService.getLastHeartRate()) }
    var deviceName by remember { mutableStateOf(sensorService.getCurrentDeviceName().orEmpty()) }
    var isConnected by remember { mutableStateOf(sensorService.isConnected()) }
    var batteryLevel by remember { mutableStateOf(sensorService.getLastBatteryLevel()) }
    var showDetailDialog by remember { mutableStateOf(false) }

    // Animation for heart rate changes
    val heartRateAlpha by animateFloatAsState(
        targetValue = if (currentHeartRate > 0) 1f else 0.7f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "heartRateAlpha"
    )

    // Show detail dialog if requested
    if (showDetailDialog) {
        HeartRateDetailDialog(
            onDismiss = { showDetailDialog = false }
        )
    }

    // Subscribe to heart rate updates via EventBus
    DisposableEffect(Unit) {
        val eventBusListener = object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onHeartRateData(data: HeartRateData) {
                // Discovery events describe scan results, not the active connection.
                if (data.isScanning || data.deviceAddress.isEmpty()) return
                if (data.heartRate > 0) {
                    currentHeartRate = data.heartRate
                } else if (!data.isConnected) {
                    currentHeartRate = 0
                }
                deviceName = data.deviceName
                isConnected = data.isConnected
                batteryLevel = if (data.isConnected) data.batteryLevel else null
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

    // Only show the panel if connected to a heart rate sensor
    AnimatedVisibility(
        visible = isConnected,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Card(
            modifier = modifier
                .padding(8.dp)
                .width(110.dp)
                .clickable { showDetailDialog = true },
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Heart icon with "beating" animation
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = heartRateAlpha * 0.2f))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Heart Rate",
                        tint = Color.Red.copy(alpha = heartRateAlpha),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Heart rate value
                Text(
                    text = if (currentHeartRate > 0) "$currentHeartRate" else "---",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "bpm",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                batteryLevel?.let { level ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryStd,
                                contentDescription = "Heart rate sensor battery",
                                modifier = Modifier.size(14.dp)
                            )
                            Text(text = "$level%", fontSize = 12.sp)
                        }
                    }
                }

                // Device name display (optional)
                if (deviceName.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = deviceName,
                        fontSize = 10.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
