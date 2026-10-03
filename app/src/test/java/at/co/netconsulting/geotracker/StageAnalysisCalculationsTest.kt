package at.co.netconsulting.geotracker

import at.co.netconsulting.geotracker.domain.Metric
import org.junit.Assert.*
import org.junit.Test

class StageAnalysisCalculationsTest {
    private fun metric(time: Long, distance: Double, elevation: Float = 100f) = Metric(
        eventId = 1, heartRate = 0, heartRateDevice = "", speed = 2f,
        distance = distance, cadence = null, lap = 1, timeInMilliseconds = time,
        unity = "metric", elevation = elevation
    )

    @Test fun `excludes overnight gaps and weights average speed by duration`() {
        val first = calculateStageTotals(listOf(metric(0, 0.0), metric(3600000, 10000.0)))
        val second = calculateStageTotals(listOf(metric(86400000, 0.0), metric(93600000, 10000.0)))
        val combined = combineStageTotals(listOf(first, second))
        assertEquals(10800000L, combined.durationMillis)
        assertEquals(20000.0, combined.distanceMeters, 0.0)
        assertEquals(20.0 / 3, combined.averageSpeedKmh!!, 0.00001)
        assertEquals(7.2, combined.maxSpeedKmh, 0.00001)
    }

    @Test fun `elevation changes do not span stage boundaries`() {
        val first = calculateStageTotals(listOf(metric(1000, 0.0, 100f), metric(2000, 10.0, 120f)))
        val second = calculateStageTotals(listOf(metric(4000, 0.0, 800f), metric(5000, 10.0, 790f)))
        val combined = combineStageTotals(listOf(first, second))
        assertEquals(20.0, combined.ascentMeters, 0.0)
        assertEquals(10.0, combined.descentMeters, 0.0)
    }

    @Test fun `empty or single sample does not invent an average speed`() {
        assertNull(calculateStageTotals(emptyList()).averageSpeedKmh)
        assertNull(calculateStageTotals(listOf(metric(1000, 10.0))).averageSpeedKmh)
    }

    @Test fun `sorts samples and ignores invalid distance`() {
        val totals = calculateStageTotals(listOf(metric(3000, Double.NaN), metric(2000, 20.0), metric(1000, -1.0)))
        assertEquals(2000L, totals.durationMillis)
        assertEquals(20.0, totals.distanceMeters, 0.0)
    }
}
