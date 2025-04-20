package at.co.netconsulting.geotracker.composables

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import at.co.netconsulting.geotracker.data.HeartRateData
import at.co.netconsulting.geotracker.data.HeartRateSensorDevice
import at.co.netconsulting.geotracker.service.HeartRateSensorService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateSensorDialog(
    onSelectDevice: (HeartRateSensorDevice?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val heartRateService = remember { HeartRateSensorService.getInstance(context) }

    // State for discovered devices
    val devices = remember { mutableStateListOf<HeartRateSensorDevice>() }

    // State for selected device
    var selectedDevice by remember { mutableStateOf<HeartRateSensorDevice?>(null) }

    // State for scanning
    var isScanning by remember { mutableStateOf(false) }

    // State for errors
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check if Bluetooth permissions are granted
    val hasBluetoothPermissions = ActivityCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

    // Check if Bluetooth is enabled
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val isBluetoothEnabled = bluetoothAdapter?.isEnabled == true

    // Handle EventBus registration for receiving heart rate sensor events
    DisposableEffect(Unit) {
        // Create EventBus listener object
        val eventBusListener = object : Any() {
            @Subscribe(threadMode = ThreadMode.MAIN)
            fun onHeartRateData(data: HeartRateData) {
                // Update scanning state
                isScanning = data.isScanning

                // Update error message if any
                if (data.error != null) {
                    errorMessage = data.error
                }

                // Add device to list if it's not already there and has a name
                if (data.deviceName.isNotEmpty() && data.deviceAddress.isNotEmpty()) {
                    val device = HeartRateSensorDevice(data.deviceName, data.deviceAddress)

                    // Only add if it's not already in the list
                    if (!devices.any { it.address == device.address }) {
                        devices.add(device)
                    }
                }
            }
        }

        // Register with EventBus
        if (!EventBus.getDefault().isRegistered(eventBusListener)) {
            EventBus.getDefault().register(eventBusListener)
        }

        // Start scanning when dialog opens
        if (hasBluetoothPermissions && isBluetoothEnabled) {
            heartRateService.startScan()
        }

        // Cleanup when dialog closes
        onDispose {
            heartRateService.stopScan()
            if (EventBus.getDefault().isRegistered(eventBusListener)) {
                EventBus.getDefault().unregister(eventBusListener)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Bluetooth Icon",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Select Heart Rate Sensor")
            }
        },
        text = {
            Column {
                // Show error messages if any
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Show warning if Bluetooth is not enabled
                if (!isBluetoothEnabled) {
                    Text(
                        text = "Bluetooth is not enabled. Please enable Bluetooth and try again.",
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Show warning if permissions are not granted
                if (!hasBluetoothPermissions) {
                    Text(
                        text = "Bluetooth permissions are required. Please grant the necessary permissions in Settings.",
                        color = Color.Red,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Optional: No Heart Rate Sensor option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedDevice == null,
                        onClick = { selectedDevice = null }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("No Heart Rate Sensor")
                }

                Divider()

                // Show loading indicator when scanning
                if (isScanning) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scanning for devices...")
                        }
                    }
                }

                // Show list of devices
                if (devices.isEmpty() && !isScanning) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "No heart rate sensors found",
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn {
                        items(devices) { device ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedDevice?.address == device.address,
                                    onClick = { selectedDevice = device }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(device.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectDevice(selectedDevice)
                    onDismiss()
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Row {
                // Scan button
                if (hasBluetoothPermissions && isBluetoothEnabled) {
                    IconButton(
                        onClick = {
                            devices.clear()
                            heartRateService.startScan()
                        },
                        enabled = !isScanning
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan again"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Cancel button
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}