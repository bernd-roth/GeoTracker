package at.co.netconsulting.geotracker.repository

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecordingTimestampTest {
    @Test fun `preserves fractions and timezone offsets`() {
        val expected = Instant.parse("2026-10-01T08:00:00.123Z").toEpochMilli()
        assertEquals(expected, parseRecordingTimestamp("2026-10-01T08:00:00.123Z"))
        assertEquals(expected, parseRecordingTimestamp("2026-10-01T10:00:00.123+02:00"))
        assertEquals(expected, parseRecordingTimestamp("2026-10-01T03:00:00.123-05:00"))
        assertEquals(expected, parseRecordingTimestamp("2026-10-01 08:00:00.123"))
    }

    @Test fun `missing or invalid recording times remain unknown`() {
        listOf(null, "", "null", "invalid", "2026-02-30T08:00:00Z").forEach {
            assertNull(parseRecordingTimestamp(it))
        }
    }
}
