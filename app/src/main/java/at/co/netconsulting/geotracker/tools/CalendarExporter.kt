package at.co.netconsulting.geotracker.tools

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import at.co.netconsulting.geotracker.data.TimeRange
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

data class GoogleCalendar(val id: Long, val displayName: String, val accountName: String)

data class CalendarExportEvent(
    val sourceEventId: Int,
    val sourceUserId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val description: String?,
    val location: String?
) {
    val uid: String
        get() = "geotracker-$sourceUserId-$sourceEventId@at.co.netconsulting.geotracker"
}

data class CalendarExportResult(val exported: Int, val skipped: Int)

object CalendarExporter {
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    private const val MIN_TIMED_EVENT_DURATION_MS = 60_000L

    suspend fun loadRecordedEvents(
        database: FitnessTrackerDatabase
    ): List<CalendarExportEvent> = withContext(Dispatchers.IO) {
        val timeRanges = database.metricDao().getAllEventTimeRanges().associateBy { it.eventId }
        database.eventDao().getRecordedEventsForCalendar().map { event ->
            val range = timeRanges[event.eventId]?.let { TimeRange(it.minTime, it.maxTime) }
            buildExportEvent(event, range)
        }
    }

    internal fun buildExportEvent(event: Event, timeRange: TimeRange?): CalendarExportEvent {
        val hasValidTimeRange = timeRange != null && timeRange.minTime > 0L &&
                timeRange.maxTime >= timeRange.minTime
        val (startMillis, endMillis) = if (hasValidTimeRange) {
            timeRange!!.minTime to maxOf(
                timeRange.maxTime,
                timeRange.minTime + MIN_TIMED_EVENT_DURATION_MS
            )
        } else {
            val date = runCatching { LocalDate.parse(event.eventDate) }
                .getOrElse { LocalDate.now() }
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() to
                    date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val description = listOf(event.artOfSport, event.comment)
            .filter { it.isNotBlank() }.joinToString("\n").ifBlank { null }
        val location = event.startAddress ?: listOfNotNull(event.startCity, event.startCountry)
            .joinToString(", ").ifBlank { null }

        return CalendarExportEvent(
            event.eventId, event.userId, event.eventName, startMillis, endMillis,
            !hasValidTimeRange, description, location
        )
    }

    fun createInsertIntent(event: CalendarExportEvent): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay)
            event.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            event.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        }

    fun getWritableGoogleCalendars(context: Context): List<GoogleCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val selection = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
                "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
                "${CalendarContract.Calendars.SYNC_EVENTS} = 1 AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(
            GOOGLE_ACCOUNT_TYPE,
            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()
        )

        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            )
            val accountIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GoogleCalendar(
                            cursor.getLong(idIndex),
                            cursor.getString(nameIndex),
                            cursor.getString(accountIndex)
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    fun exportAll(
        context: Context,
        calendarId: Long,
        events: List<CalendarExportEvent>
    ): CalendarExportResult {
        val resolver = context.contentResolver
        val existingUids = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.UID_2445),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                    "${CalendarContract.Events.UID_2445} IS NOT NULL",
            arrayOf(calendarId.toString()),
            null
        )?.use { cursor ->
            val uidIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.UID_2445)
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(uidIndex))
            }
        } ?: emptySet()

        val eventsToInsert = events.filterNot { it.uid in existingUids }
        eventsToInsert.chunked(100).forEach { chunk ->
            val operations = ArrayList<ContentProviderOperation>(chunk.size)
            chunk.forEach { event ->
                operations += ContentProviderOperation
                    .newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValues(toContentValues(calendarId, event))
                    .build()
            }
            resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        }
        return CalendarExportResult(eventsToInsert.size, events.size - eventsToInsert.size)
    }

    private fun toContentValues(calendarId: Long, event: CalendarExportEvent) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.DTSTART, event.startMillis)
        put(CalendarContract.Events.DTEND, event.endMillis)
        put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
        put(
            CalendarContract.Events.EVENT_TIMEZONE,
            if (event.allDay) TimeZone.getTimeZone("UTC").id else TimeZone.getDefault().id
        )
        put(CalendarContract.Events.UID_2445, event.uid)
        event.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        event.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
    }
}
