package at.co.netconsulting.geotracker.data

/**
 * EventBus data class posted every second during a Lactate Threshold test.
 * Consumed by MapScreen to update the countdown overlay.
 */
data class LtPhaseChanged(
    val phase: String,           // "settle" or "measurement"
    val remainingSeconds: Long,
    val totalSeconds: Long = 1800L
)
