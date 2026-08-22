package at.co.netconsulting.geotracker

import at.co.netconsulting.geotracker.domain.LapTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LapAnalysisCalculationsTest {

    @Test
    fun `groups consecutive laps and calculates pace from combined totals`() {
        val groups = groupLapTimes(
            listOf(
                lap(number = 1, start = 0L, duration = 300_000L, distance = 1.0),
                lap(number = 2, start = 300_000L, duration = 330_000L, distance = 1.0),
                lap(number = 3, start = 630_000L, duration = 720_000L, distance = 2.0)
            ),
            requestedGroupSize = 2
        )

        assertEquals(2, groups.size)
        assertEquals("1-2", groups[0].label)
        assertEquals(630_000L, groups[0].durationMs)
        assertEquals(630_000L, groups[0].totalDurationMs)
        assertEquals(2.0, groups[0].distanceKm, 0.0)
        assertEquals(315.0, groups[0].paceSecondsPerKm!!, 0.0)
        assertFalse(groups[0].isIncomplete)

        assertEquals("3", groups[1].label)
        assertEquals(720_000L, groups[1].durationMs)
        assertEquals(1_350_000L, groups[1].totalDurationMs)
        assertEquals(2.0, groups[1].distanceKm, 0.0)
        assertEquals(360.0, groups[1].paceSecondsPerKm!!, 0.0)
        assertTrue(groups[1].isIncomplete)
    }

    @Test
    fun `uses actual distance for non-kilometre laps`() {
        val groups = groupLapTimes(
            listOf(
                lap(number = 1, start = 0L, duration = 3_600_000L, distance = 6.7606),
                lap(number = 2, start = 3_600_000L, duration = 3_300_000L, distance = 6.7606)
            ),
            requestedGroupSize = 2
        )

        assertEquals(13.5212, groups.single().distanceKm, 0.000_000_1)
        assertEquals(6_900_000L, groups.single().durationMs)
        assertEquals(510.309_736, groups.single().paceSecondsPerKm!!, 0.000_001)
    }

    @Test
    fun `marks a short final recorded lap as incomplete`() {
        val groups = groupLapTimes(
            listOf(
                lap(number = 1, start = 0L, duration = 300_000L, distance = 1.0),
                lap(number = 2, start = 300_000L, duration = 300_000L, distance = 1.0),
                lap(number = 3, start = 600_000L, duration = 60_000L, distance = 0.2)
            ),
            requestedGroupSize = 1
        )

        assertFalse(groups[0].isIncomplete)
        assertFalse(groups[1].isIncomplete)
        assertTrue(groups[2].isIncomplete)
    }

    @Test
    fun `invalid group size falls back to individual laps`() {
        val groups = groupLapTimes(
            listOf(
                lap(number = 1, start = 0L, duration = 300_000L, distance = 1.0),
                lap(number = 2, start = 300_000L, duration = 310_000L, distance = 1.0)
            ),
            requestedGroupSize = 0
        )

        assertEquals(2, groups.size)
        assertEquals("1", groups[0].label)
        assertEquals("2", groups[1].label)
        assertEquals(300_000L, groups[0].totalDurationMs)
        assertEquals(610_000L, groups[1].totalDurationMs)
    }

    @Test
    fun `pace uses distance and rounds cleanly across minute boundary`() {
        assertEquals("5:15/km", calculatePace(durationMs = 630_000L, distanceKm = 2.0))
        assertEquals("6:00/km", calculatePace(durationMs = 359_999L, distanceKm = 1.0))
        assertEquals("--/km", calculatePace(durationMs = 300_000L, distanceKm = 0.0))
    }

    private fun lap(
        number: Int,
        start: Long,
        duration: Long,
        distance: Double
    ) = LapTime(
        sessionId = "session",
        eventId = 1,
        lapNumber = number,
        startTime = start,
        endTime = start + duration,
        distance = distance
    )
}
