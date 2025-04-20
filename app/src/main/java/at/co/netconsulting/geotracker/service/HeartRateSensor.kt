package at.co.netconsulting.geotracker.service

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.UUID
import at.co.netconsulting.geotracker.data.HeartRateData
import org.greenrobot.eventbus.EventBus
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HeartRateSensorService(private val context: Context) {
    private val TAG = "HeartRateSensorService"

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bleClient = RxBleClient.create(context)
    private val bleDisposables = CompositeDisposable()

    private var currentDeviceAddress: String? = null
    private var currentDeviceName: String? = null
    private var connection: RxBleConnection? = null
    private var isConnected = false
    private var lastHeartRate = 0

    // Heart Rate Service UUID (standard for BLE heart rate devices)
    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    private val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

    // List to store found devices
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    // Callback for device scanning
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name != null && !discoveredDevices.contains(device)) {
                Log.d(TAG, "Found device: ${device.name}, address: ${device.address}")
                discoveredDevices.add(device)
                EventBus.getDefault().post(HeartRateData(
                    deviceName = device.name ?: "Unknown Device",
                    deviceAddress = device.address,
                    heartRate = 0,
                    isConnected = false,
                    isScanning = true
                ))
            }
        }
    }

    // Start scanning for heart rate devices
    fun startScan() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required Bluetooth permissions")
            return
        }

        discoveredDevices.clear()

        // Notify that scanning has started
        EventBus.getDefault().post(HeartRateData(
            deviceName = "",
            deviceAddress = "",
            heartRate = 0,
            isConnected = false,
            isScanning = true
        ))

        try {
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.bluetoothLeScanner.startScan(
                    listOf(scanFilter),
                    scanSettings,
                    scanCallback
                )

                // Auto-stop scan after 10 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    stopScan()
                }, 10000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
        }
    }

    // Stop scanning
    fun stopScan() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
                // Notify that scanning has stopped
                EventBus.getDefault().post(HeartRateData(
                    deviceName = "",
                    deviceAddress = "",
                    heartRate = 0,
                    isConnected = false,
                    isScanning = false
                ))
                Log.d(TAG, "BLE scan stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan", e)
            }
        }
    }

    // Connect to a specific device by address
    fun connectToDevice(address: String) {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required Bluetooth permissions")
            return
        }

        // Cleanup any existing connection
        disconnect()

        currentDeviceAddress = address
        val device = bleClient.getBleDevice(address)
        currentDeviceName = device.name

        Log.d(TAG, "Connecting to device: ${device.name ?: "Unknown"}, address: $address")

        // Establish connection
        bleDisposables.add(
            device.establishConnection(false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ connection ->
                    this.connection = connection
                    isConnected = true

                    // Notify about successful connection
                    EventBus.getDefault().post(HeartRateData(
                        deviceName = device.name ?: "Unknown Device",
                        deviceAddress = address,
                        heartRate = 0,
                        isConnected = true,
                        isScanning = false
                    ))

                    Log.d(TAG, "Connected to ${device.name}")

                    // Setup notifications for heart rate characteristic
                    setupHeartRateNotifications()
                }, { error ->
                    Log.e(TAG, "Error connecting to device", error)
                    isConnected = false

                    // Notify about connection failure
                    EventBus.getDefault().post(HeartRateData(
                        deviceName = device.name ?: "Unknown Device",
                        deviceAddress = address,
                        heartRate = 0,
                        isConnected = false,
                        isScanning = false,
                        error = error.message
                    ))
                })
        )
    }

    // Disconnect from current device
    fun disconnect() {
        bleDisposables.clear()
        connection = null
        isConnected = false

        if (currentDeviceAddress != null) {
            Log.d(TAG, "Disconnected from heart rate sensor")

            // Notify about disconnection
            EventBus.getDefault().post(HeartRateData(
                deviceName = currentDeviceName ?: "Unknown Device",
                deviceAddress = currentDeviceAddress ?: "",
                heartRate = 0,
                isConnected = false,
                isScanning = false
            ))
        }
    }

    // Setup notifications for heart rate measurements
    private fun setupHeartRateNotifications() {
        connection?.let { conn ->
            bleDisposables.add(
                conn.setupNotification(HEART_RATE_MEASUREMENT_UUID)
                    .flatMap { it }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ bytes ->
                        // Parse heart rate data according to Bluetooth standard
                        val heartRate = parseHeartRateData(bytes)
                        lastHeartRate = heartRate

                        Log.d(TAG, "Heart rate update: $heartRate bpm")

                        // Publish heart rate data
                        EventBus.getDefault().post(HeartRateData(
                            deviceName = currentDeviceName ?: "Unknown Device",
                            deviceAddress = currentDeviceAddress ?: "",
                            heartRate = heartRate,
                            isConnected = true,
                            isScanning = false
                        ))

                    }, { error ->
                        Log.e(TAG, "Error with heart rate notification", error)
                    })
            )
        }
    }

    // Parse heart rate data according to BLE Heart Rate Service specification
    private fun parseHeartRateData(data: ByteArray): Int {
        if (data.isEmpty()) return 0

        // Check the format flag - first bit of the first byte
        val isFormatUINT16 = (data[0].toInt() and 0x01) == 1

        return if (isFormatUINT16 && data.size >= 3) {
            // Heart rate value is in the 2nd and 3rd bytes (UINT16)
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        } else if (data.size >= 2) {
            // Heart rate value is in the 2nd byte (UINT8)
            data[1].toInt() and 0xFF
        } else {
            0
        }
    }

    // Check if we have the required permissions
    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
    }

    // Get the latest heart rate
    fun getLastHeartRate(): Int {
        return lastHeartRate
    }

    // Check if connected to a heart rate sensor
    fun isConnected(): Boolean {
        return isConnected
    }

    // Get the current device name
    fun getCurrentDeviceName(): String? {
        return currentDeviceName
    }

    // Clean up resources
    fun cleanup() {
        stopScan()
        disconnect()
        bleDisposables.clear()
    }

    companion object {
        private var instance: HeartRateSensorService? = null

        fun getInstance(context: Context): HeartRateSensorService {
            return instance ?: synchronized(this) {
                instance ?: HeartRateSensorService(context).also { instance = it }
            }
        }
    }
}