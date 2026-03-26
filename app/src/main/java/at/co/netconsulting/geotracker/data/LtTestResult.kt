package at.co.netconsulting.geotracker.data

/**
 * EventBus data class posted when the Lactate Threshold 30-min TT completes.
 * Consumed by MapScreen to show the result dialog.
 */
data class LtTestResult(
    val avgHeartRate: Int,
    val avgPaceMinPerKm: Double,
    val totalSamples: Int,
    val testDate: String
)
