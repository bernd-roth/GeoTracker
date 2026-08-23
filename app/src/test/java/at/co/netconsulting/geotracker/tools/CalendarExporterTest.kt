package at.co.netconsulting.geotracker.tools

import android.provider.CalendarContract
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
        assertEquals("geotracker-7-42@at.co.netconsulting.geotracker", exported.uid)
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

    @Test
    fun `calendar detail fallback selection matches title all-day flag and rounded times`() {
        val start = 1_700_000_000_000L
        val end = 1_700_003_600_000L
        val exported = CalendarExporter.buildExportEvent(
            recordedEvent(eventName = "Yesterday run"),
            TimeRange(start, end)
        )

        val (selection, args) = CalendarExporter.calendarDetailSelection(listOf(11L, 12L), exported)

        assertTrue(selection.contains(CalendarContract.Events.CALENDAR_ID))
        assertTrue(selection.contains(CalendarContract.Events.TITLE))
        assertTrue(selection.contains(CalendarContract.Events.DTSTART))
        assertTrue(selection.contains(CalendarContract.Events.DTEND))
        assertEquals(
            listOf(
                "11",
                "12",
                "Yesterday run",
                "0",
                (start - 120_000L).toString(),
                (start + 120_000L).toString(),
                (end - 120_000L).toString(),
                (end + 120_000L).toString()
            ),
            args.toList()
        )
    }

    @Test
    fun `marked export includes only selected event ids in recording order`() {
        val first = CalendarExporter.buildExportEvent(
            recordedEvent(eventName = "First", eventId = 41),
            TimeRange(1_700_000_000_000L, 1_700_000_060_000L)
        )
        val second = CalendarExporter.buildExportEvent(
            recordedEvent(eventName = "Second", eventId = 42),
            TimeRange(1_700_000_120_000L, 1_700_000_180_000L)
        )
        val third = CalendarExporter.buildExportEvent(
            recordedEvent(eventName = "Third", eventId = 43),
            TimeRange(1_700_000_240_000L, 1_700_000_300_000L)
        )

        val selected = CalendarExporter.selectEventsForExport(
            listOf(first, second, third),
            setOf(41, 43)
        )

        assertEquals(listOf(41, 43), selected.map { it.sourceEventId })
    }

    @Test
    fun `null selection exports all while empty selection exports none`() {
        val events = listOf(
            CalendarExporter.buildExportEvent(
                recordedEvent(eventName = "First", eventId = 41),
                TimeRange(1_700_000_000_000L, 1_700_000_060_000L)
            ),
            CalendarExporter.buildExportEvent(
                recordedEvent(eventName = "Second", eventId = 42),
                TimeRange(1_700_000_120_000L, 1_700_000_180_000L)
            )
        )

        assertEquals(events, CalendarExporter.selectEventsForExport(events, null))
        assertTrue(CalendarExporter.selectEventsForExport(events, emptySet()).isEmpty())
    }

    @Test
    fun `deleted calendar tombstones do not count as existing exports`() {
        val selection = CalendarExporter.existingExportSelection()

        assertTrue(selection.contains(CalendarContract.Events.CALENDAR_ID))
        assertTrue(selection.contains(CalendarContract.Events.UID_2445))
        assertTrue(selection.contains(CalendarContract.Events.DELETED))
        assertTrue(selection.contains("!= 1"))
    }

    @Test
    fun `bulk delete counts exact removed rows instead of rerunning fallback matching`() {
        val sourceUids = (1..336).map { "geotracker-7-$it@at.co.netconsulting.geotracker" }.toSet()
        val matches = (1..334).map { id ->
            CalendarExporter.CalendarDeleteMatch(
                calendarEventId = id.toLong(),
                sourceUid = "geotracker-7-$id@at.co.netconsulting.geotracker"
            )
        }

        val result = CalendarExporter.summarizeDeleteResult(
            sourceUids = sourceUids,
            matches = matches,
            remainingEventIds = emptySet()
        )

        assertEquals(334, result.deletedEntries)
        assertEquals(334, result.foundEvents)
        assertEquals(2, result.missingEvents)
        assertEquals(0, result.remainingEntries)
    }

    @Test
    fun `bulk delete reports only exact row ids that remain active`() {
        val matches = listOf(
            CalendarExporter.CalendarDeleteMatch(10L, "uid-1"),
            CalendarExporter.CalendarDeleteMatch(11L, "uid-2"),
            CalendarExporter.CalendarDeleteMatch(12L, "uid-3")
        )

        val result = CalendarExporter.summarizeDeleteResult(
            sourceUids = setOf("uid-1", "uid-2", "uid-3"),
            matches = matches,
            remainingEventIds = setOf(11L)
        )

        assertEquals(2, result.deletedEntries)
        assertEquals(1, result.remainingEntries)
    }

    private fun recordedEvent(
        eventName: String,
        eventId: Int = 42,
        eventDate: String = "2026-07-05",
        artOfSport: String = "",
        comment: String = "",
        startAddress: String? = null
    ) = Event(
        eventId = eventId,
        userId = 7,
        eventName = eventName,
        eventDate = eventDate,
        artOfSport = artOfSport,
        comment = comment,
        startAddress = startAddress,
        eventSource = "recorded"
    )
}
