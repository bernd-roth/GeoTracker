package at.co.netconsulting.geotracker.data

data class BarometerData(
    val pressure: Float,                    // Pressure in hPa/mbar
    val accuracy: Int,                      // Sensor accuracy (0-3)
    val altitudeFromPressure: Float,        // Calculated altitude
    val seaLevelPressure: Float = 1013.25f, // Reference pressure
    val timestamp: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = true
)