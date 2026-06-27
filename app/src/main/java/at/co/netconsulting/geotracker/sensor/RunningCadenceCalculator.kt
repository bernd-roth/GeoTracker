package at.co.netconsulting.geotracker.sensor

import java.util.ArrayDeque
import kotlin.math.roundToInt

/**
 * Calculates Garmin-compatible running cadence from individual step timestamps.
 *
 * Garmin's GPX cadence field is expressed as cycles per minute. For running,
 * one cycle is two steps, so the exported value is half the step rate normally
 * shown to a runner.
 */
class RunningCadenceCalculator(
    private val windowNanos: Long = DEFAULT_WINDOW_NANOS,
    private val staleAfterNanos: Long = DEFAULT_STALE_AFTER_NANOS,
    private val minimumSteps: Int = DEFAULT_MINIMUM_STEPS
) {
    private val stepTimestamps = ArrayDeque<Long>()

    @Synchronized
    fun addStep(timestampNanos: Long) {
        if (timestampNanos <= 0L ||
            (stepTimestamps.isNotEmpty() && timestampNanos <= stepTimestamps.last())
        ) {
            return
        }

        stepTimestamps.addLast(timestampNanos)
        discardOlderThan(timestampNanos - windowNanos)
    }

    /** Returns cycles per minute, or zero while stationary/warming up. */
    @Synchronized
    fun cadenceAt(timestampNanos: Long): Int {
        if (stepTimestamps.isEmpty() ||
            timestampNanos - stepTimestamps.last() > staleAfterNanos
        ) {
            return 0
        }

        discardOlderThan(timestampNanos - windowNanos)
        if (stepTimestamps.size < minimumSteps) return 0

        val durationNanos = stepTimestamps.last() - stepTimestamps.first()
        if (durationNanos <= 0L) return 0

        val stepIntervals = stepTimestamps.size - 1
        val stepsPerMinute = stepIntervals * NANOS_PER_MINUTE.toDouble() / durationNanos
        return (stepsPerMinute / STEPS_PER_CYCLE)
            .roundToInt()
            .coerceIn(0, MAX_GPX_CADENCE)
    }

    @Synchronized
    fun reset() {
        stepTimestamps.clear()
    }

    private fun discardOlderThan(cutoffNanos: Long) {
        while (stepTimestamps.isNotEmpty() && stepTimestamps.first() < cutoffNanos) {
            stepTimestamps.removeFirst()
        }
    }

    companion object {
        private const val NANOS_PER_MINUTE = 60_000_000_000L
        private const val STEPS_PER_CYCLE = 2.0
        private const val MAX_GPX_CADENCE = 254
        private const val DEFAULT_WINDOW_NANOS = 10_000_000_000L
        private const val DEFAULT_STALE_AFTER_NANOS = 3_000_000_000L
        private const val DEFAULT_MINIMUM_STEPS = 4
    }
}
