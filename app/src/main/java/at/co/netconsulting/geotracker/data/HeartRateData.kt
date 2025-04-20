package at.co.netconsulting.geotracker.data

/**
 * Data class representing heart rate sensor data
 * Used for communication between components via EventBus
 */
data class HeartRateData(
    val deviceName: String,
    val deviceAddress: String,
    val heartRate: Int,
    val isConnected: Boolean,
    val isScanning: Boolean,
    val error: String? = null
)