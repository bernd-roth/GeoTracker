package at.co.netconsulting.geotracker.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GeoTrackerApiClientTest {
    @Test
    fun `manual upload uses earliest recorded metric as session start`() {
        val viennaStart = ZonedDateTime.of(
            2026, 9, 12, 11, 0, 0, 0,
            ZoneId.of("Europe/Vienna")
        ).toInstant().toEpochMilli()

        val result = GeoTrackerApiClient.resolveUploadStartDateTime(
            eventDate = "2026-09-12",
            metricTimestamps = listOf(viennaStart + 2_000L, viennaStart, viennaStart + 1_000L)
        )

        assertEquals("2026-09-12T09:00:00Z", result)
    }

    @Test
    fun `upload timestamps preserve milliseconds and identify UTC`() {
        assertEquals(
            "2026-09-12T09:00:00.123Z",
            GeoTrackerApiClient.formatUploadTimestamp(1_789_203_600_123L)
        )
    }

    @Test
    fun `manual upload falls back to an unambiguous date when metric times are invalid`() {
        val result = GeoTrackerApiClient.resolveUploadStartDateTime(
            eventDate = "2026-09-12",
            metricTimestamps = listOf(0L, -1L)
        )

        assertEquals("2026-09-12T00:00:00Z", result)
    }
}
