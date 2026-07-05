package at.co.netconsulting.geotracker.tools

import at.co.netconsulting.geotracker.data.TimeRange
import at.co.netconsulting.geotracker.domain.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CalendarExporterTest {
    @Test
    fun `timed export keeps event title and recorded time range`() {
        val event = recordedEvent(
            eventName = "Sunday run",
            artOfSport = "Running",
            comment = "Easy pace",
            startAddress = "Main Street 1"
        )

        val exported = CalendarExporter.buildExportEvent(
            event,
            TimeRange(minTime = 1_700_000_000_000L, maxTime = 1_700_003_600_000L)
        )

        assertEquals("Sunday run", exported.title)
        assertEquals(1_700_000_000_000L, exported.startMillis)
        assertEquals(1_700_003_600_000L, exported.endMillis)
        assertFalse(exported.allDay)
        assertEquals("Running\nEasy pace", exported.description)
        assertEquals("Main Street 1", exported.location)
    }

    @Test
    fun `event without timestamps becomes an all-day event on its recorded date`() {
        val event = recordedEvent(eventName = "Strength", eventDate = "2026-07-05")

        val exported = CalendarExporter.buildExportEvent(event, null)

        val expectedStart = LocalDate.parse("2026-07-05")
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("Strength", exported.title)
        assertEquals(expectedStart, exported.startMillis)
        assertEquals(expectedStart + 86_400_000L, exported.endMillis)
        assertTrue(exported.allDay)
    }

    @Test
    fun `zero-length recording is exported with a minimum duration`() {
        val event = recordedEvent(eventName = "Short activity")
        val start = 1_700_000_000_000L

        val exported = CalendarExporter.buildExportEvent(event, TimeRange(start, start))

        assertEquals(start + 60_000L, exported.endMillis)
        assertFalse(exported.allDay)
    }

    private fun recordedEvent(
        eventName: String,
        eventDate: String = "2026-07-05",
        artOfSport: String = "",
        comment: String = "",
        startAddress: String? = null
    ) = Event(
        eventId = 42,
        userId = 7,
        eventName = eventName,
        eventDate = eventDate,
        artOfSport = artOfSport,
        comment = comment,
        startAddress = startAddress,
        eventSource = "recorded"
    )
}
