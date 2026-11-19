package at.co.netconsulting.geotracker.location

import android.location.Location

/**
 * Counts only consecutive fixes with evidence of movement. The first three qualifying
 * fixes must span two seconds; their segment lengths are held until confirmation.
 * A rejected fix discards that pending distance and breaks the coordinate chain.
 */
internal class MovementDistanceFilter(
    private val distanceBetween: (Sample, Sample) -> Double = { previous, current ->
        val result = FloatArray(1)
        Location.distanceBetween(
            previous.latitude, previous.longitude, current.latitude, current.longitude, result
        )
        result[0].toDouble()
    }
) {
    internal data class Sample(
        val elapsedRealtimeNanos: Long,
        val latitude: Double,
        val longitude: Double,
        val speedMetersPerSecond: Float?,
        val speedAccuracyMetersPerSecond: Float?
    )

    var isMoving: Boolean = false
        private set

    private var lastTimestamp: Long? = null
    private var previousCandidate: Sample? = null
    private var candidateStartNanos = 0L
    private var candidateCount = 0
    private var pendingDistance = 0.0

    fun update(location: Location): Double = update(
        Sample(
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
            latitude = location.latitude,
            longitude = location.longitude,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
            speedAccuracyMetersPerSecond = if (location.hasSpeedAccuracy()) {
                location.speedAccuracyMetersPerSecond
            } else null
        )
    )

    fun update(sample: Sample): Double {
        // Cached, duplicated or out-of-order fixes cannot confirm movement or move the anchor.
        if (sample.elapsedRealtimeNanos < 0L ||
            lastTimestamp?.let { sample.elapsedRealtimeNanos <= it } == true
        ) return 0.0
        lastTimestamp = sample.elapsedRealtimeNanos

        val speed = sample.speedMetersPerSecond
        val accuracy = sample.speedAccuracyMetersPerSecond
        val validPosition = sample.latitude.isFinite() && sample.longitude.isFinite() &&
            sample.latitude in -90.0..90.0 && sample.longitude in -180.0..180.0
        // Missing accuracy falls back to speed + temporal confirmation. A present but
        // malformed accuracy is rejected rather than treated as perfect certainty.
        val confidentSpeed = accuracy == null ||
            (accuracy.isFinite() && accuracy >= 0f && speed != null && speed > accuracy)
        if (!validPosition || speed == null || !speed.isFinite() ||
            speed < MIN_SPEED_METERS_PER_SECOND || !confidentSpeed
        ) {
            clearMovement()
            return 0.0
        }

        val previous = previousCandidate
        previousCandidate = sample
        if (previous == null) {
            candidateStartNanos = sample.elapsedRealtimeNanos
            candidateCount = 1
            return 0.0
        }

        val segmentDistance = distanceBetween(previous, sample)
        if (!segmentDistance.isFinite() || segmentDistance < 0.0) {
            clearMovement()
            return 0.0
        }
        if (isMoving) return segmentDistance

        candidateCount++
        pendingDistance += segmentDistance
        if (candidateCount < REQUIRED_SAMPLES ||
            sample.elapsedRealtimeNanos - candidateStartNanos < CONFIRMATION_NANOS
        ) return 0.0

        isMoving = true
        val confirmedDistance = pendingDistance
        pendingDistance = 0.0
        return confirmedDistance
    }

    fun reset() {
        lastTimestamp = null
        clearMovement()
    }

    private fun clearMovement() {
        isMoving = false
        previousCandidate = null
        candidateStartNanos = 0L
        candidateCount = 0
        pendingDistance = 0.0
    }

    private companion object {
        const val MIN_SPEED_METERS_PER_SECOND = 2.0 / 3.6
        const val REQUIRED_SAMPLES = 3
        const val CONFIRMATION_NANOS = 2_000_000_000L
    }
}
