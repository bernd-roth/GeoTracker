package at.co.netconsulting.geotracker.tools

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import at.co.netconsulting.geotracker.data.TimeRange
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
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

data class CalendarDeleteResult(
    val deletedEntries: Int,
    val foundEvents: Int,
    val missingEvents: Int,
    val remainingEntries: Int = 0
)

object CalendarExporter {
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    private const val MIN_TIMED_EVENT_DURATION_MS = 60_000L
    private const val DETAIL_MATCH_TOLERANCE_MS = 2 * 60_000L
    private const val BROAD_MATCH_WINDOW_MS = 18 * 60 * 60_000L

    private data class CalendarDeleteMatch(
        val calendarEventId: Long,
        val sourceUid: String
    )

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

    fun getWritableGoogleCalendars(context: Context): List<GoogleCalendar> =
        getWritableCalendars(context, GOOGLE_ACCOUNT_TYPE)

    fun getWritableCalendars(context: Context): List<GoogleCalendar> =
        getWritableCalendars(context, accountType = null)

    private fun getWritableCalendars(context: Context, accountType: String?): List<GoogleCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val accountSelection = accountType?.let {
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND "
        } ?: ""
        val selection = accountSelection +
                "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
                "${CalendarContract.Calendars.SYNC_EVENTS} = 1 AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = buildList {
            accountType?.let { add(it) }
            add(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        }.toTypedArray()

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

    fun deleteFromAllWritableGoogleCalendars(
        context: Context,
        events: List<CalendarExportEvent>
    ): CalendarDeleteResult {
        val calendarIds = getWritableGoogleCalendars(context).map { it.id }
        return deleteFromCalendars(context, calendarIds, events)
    }

    fun deleteFromAllWritableCalendars(
        context: Context,
        events: List<CalendarExportEvent>
    ): CalendarDeleteResult {
        val calendarIds = getWritableCalendars(context).map { it.id }
        return deleteFromCalendars(context, calendarIds, events)
    }

    fun deleteFromCalendars(
        context: Context,
        calendarIds: List<Long>,
        events: List<CalendarExportEvent>
    ): CalendarDeleteResult {
        val uniqueEvents = events.distinctBy { it.uid }
        if (uniqueEvents.isEmpty()) {
            return CalendarDeleteResult(deletedEntries = 0, foundEvents = 0, missingEvents = 0)
        }
        if (calendarIds.isEmpty()) {
            return CalendarDeleteResult(
                deletedEntries = 0,
                foundEvents = 0,
                missingEvents = uniqueEvents.size
            )
        }

        val resolver = context.contentResolver
        val matches = findCalendarEventMatches(context, calendarIds, uniqueEvents)
        val matchedEventIds = matches.map { it.calendarEventId }.distinct()
        matchedEventIds.forEach { eventId ->
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            resolver.delete(uri, null, null)
        }

        val remainingMatches = findCalendarEventMatches(context, calendarIds, uniqueEvents)
        val remainingEventIds = remainingMatches.map { it.calendarEventId }.distinct().toSet()
        val deletedEntries = matchedEventIds.count { it !in remainingEventIds }
        val foundEventUids = matches.map { it.sourceUid }.toSet()

        return CalendarDeleteResult(
            deletedEntries = deletedEntries,
            foundEvents = foundEventUids.size,
            missingEvents = uniqueEvents.size - foundEventUids.size,
            remainingEntries = remainingEventIds.size
        )
    }

    private fun findCalendarEventMatches(
        context: Context,
        calendarIds: List<Long>,
        events: List<CalendarExportEvent>
    ): List<CalendarDeleteMatch> {
        val uids = events.map { it.uid }
        val uidMatches = findCalendarEventMatchesByUid(context, calendarIds, uids)
        val detailMatches = events.flatMap { event ->
            findCalendarEventMatchesByDetails(context, calendarIds, event)
        }
        val instanceMatches = events.flatMap { event ->
            findCalendarEventMatchesByInstances(context, calendarIds, event)
        }
        val preciseMatchedUids = (uidMatches + detailMatches + instanceMatches)
            .map { it.sourceUid }
            .toSet()
        val broadMatches = events.filterNot { it.uid in preciseMatchedUids }.flatMap { event ->
            findCalendarEventMatchesByBroadDetails(context, calendarIds, event)
        }
        return (uidMatches + detailMatches + instanceMatches + broadMatches)
            .distinctBy { it.calendarEventId }
    }

    private fun findCalendarEventMatchesByUid(
        context: Context,
        calendarIds: List<Long>,
        uids: List<String>
    ): List<CalendarDeleteMatch> {
        val resolver = context.contentResolver
        return buildList {
            uids.chunked(100).forEach { uidChunk ->
                val (selection, args) = calendarUidSelection(calendarIds, uidChunk)
                resolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf(CalendarContract.Events._ID, CalendarContract.Events.UID_2445),
                    selection,
                    args,
                    null
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                    val uidIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.UID_2445)
                    while (cursor.moveToNext()) {
                        add(
                            CalendarDeleteMatch(
                                calendarEventId = cursor.getLong(idIndex),
                                sourceUid = cursor.getString(uidIndex)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun findCalendarEventMatchesByDetails(
        context: Context,
        calendarIds: List<Long>,
        event: CalendarExportEvent
    ): List<CalendarDeleteMatch> {
        val (selection, args) = calendarDetailSelection(calendarIds, event)
        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            args,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            buildList {
                while (cursor.moveToNext()) {
                    add(CalendarDeleteMatch(cursor.getLong(idIndex), event.uid))
                }
            }
        } ?: emptyList()
    }

    private fun findCalendarEventMatchesByBroadDetails(
        context: Context,
        calendarIds: List<Long>,
        event: CalendarExportEvent
    ): List<CalendarDeleteMatch> {
        val (selection, args) = calendarBroadDetailSelection(calendarIds, event)
        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            args,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            buildList {
                while (cursor.moveToNext()) {
                    add(CalendarDeleteMatch(cursor.getLong(idIndex), event.uid))
                }
            }
        } ?: emptyList()
    }

    private fun findCalendarEventMatchesByInstances(
        context: Context,
        calendarIds: List<Long>,
        event: CalendarExportEvent
    ): List<CalendarDeleteMatch> {
        val calendarIdSet = calendarIds.toSet()
        val rangeStart = maxOf(0L, event.startMillis - DETAIL_MATCH_TOLERANCE_MS)
        val rangeEnd = event.endMillis + DETAIL_MATCH_TOLERANCE_MS
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.ALL_DAY
        )
        return runCatching {
            CalendarContract.Instances.query(
                context.contentResolver,
                projection,
                rangeStart,
                rangeEnd,
                event.title
            )?.use { cursor ->
                val eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val calendarIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                buildList {
                    while (cursor.moveToNext()) {
                        val calendarId = cursor.getLong(calendarIdIndex)
                        val title = cursor.getString(titleIndex)
                        val allDay = cursor.getInt(allDayIndex) == 1
                        val begin = cursor.getLong(beginIndex)
                        val end = cursor.getLong(endIndex)
                        if (calendarId in calendarIdSet &&
                            title == event.title &&
                            allDay == event.allDay &&
                            withinDetailTolerance(begin, event.startMillis) &&
                            withinDetailTolerance(end, event.endMillis)
                        ) {
                            add(CalendarDeleteMatch(cursor.getLong(eventIdIndex), event.uid))
                        }
                    }
                }
            } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    private fun calendarUidSelection(
        calendarIds: List<Long>,
        uids: List<String>
    ): Pair<String, Array<String>> {
        val calendarPlaceholders = calendarIds.joinToString(",") { "?" }
        val uidPlaceholders = uids.joinToString(",") { "?" }
        val selection = CalendarContract.Events.CALENDAR_ID +
                " IN (" + calendarPlaceholders + ") AND " +
                CalendarContract.Events.UID_2445 + " IN (" + uidPlaceholders + ") AND " +
                activeEventSelection()
        val args = calendarIds.map { it.toString() } + uids
        return selection to args.toTypedArray()
    }

    internal fun calendarDetailSelection(
        calendarIds: List<Long>,
        event: CalendarExportEvent
    ): Pair<String, Array<String>> {
        val calendarPlaceholders = calendarIds.joinToString(",") { "?" }
        val allDay = if (event.allDay) 1 else 0
        val selection = CalendarContract.Events.CALENDAR_ID +
                " IN (" + calendarPlaceholders + ") AND " +
                CalendarContract.Events.TITLE + " = ? AND " +
                CalendarContract.Events.ALL_DAY + " = ? AND " +
                CalendarContract.Events.DTSTART + " BETWEEN ? AND ? AND " +
                CalendarContract.Events.DTEND + " BETWEEN ? AND ? AND " +
                activeEventSelection()
        val args = calendarIds.map { it.toString() } + listOf(
            event.title,
            allDay.toString(),
            (event.startMillis - DETAIL_MATCH_TOLERANCE_MS).toString(),
            (event.startMillis + DETAIL_MATCH_TOLERANCE_MS).toString(),
            (event.endMillis - DETAIL_MATCH_TOLERANCE_MS).toString(),
            (event.endMillis + DETAIL_MATCH_TOLERANCE_MS).toString()
        )
        return selection to args.toTypedArray()
    }

    internal fun calendarBroadDetailSelection(
        calendarIds: List<Long>,
        event: CalendarExportEvent
    ): Pair<String, Array<String>> {
        val calendarPlaceholders = calendarIds.joinToString(",") { "?" }
        val (windowStart, windowEnd) = broadMatchWindow(event)
        val selection = CalendarContract.Events.CALENDAR_ID +
                " IN (" + calendarPlaceholders + ") AND " +
                CalendarContract.Events.TITLE + " = ? AND " +
                "((" + CalendarContract.Events.DTSTART + " BETWEEN ? AND ?) OR " +
                "(" + CalendarContract.Events.DTEND + " BETWEEN ? AND ?) OR " +
                "(" + CalendarContract.Events.DTSTART + " <= ? AND " +
                CalendarContract.Events.DTEND + " >= ?)) AND " +
                activeEventSelection()
        val args = calendarIds.map { it.toString() } + listOf(
            event.title,
            windowStart.toString(),
            windowEnd.toString(),
            windowStart.toString(),
            windowEnd.toString(),
            event.startMillis.toString(),
            event.endMillis.toString()
        )
        return selection to args.toTypedArray()
    }

    private fun broadMatchWindow(event: CalendarExportEvent): Pair<Long, Long> {
        if (event.allDay) {
            return event.startMillis to event.endMillis
        }
        val zoneId = ZoneId.systemDefault()
        val localDate = java.time.Instant.ofEpochMilli(event.startMillis)
            .atZone(zoneId)
            .toLocalDate()
        val dayStart = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val dayEnd = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return minOf(dayStart, event.startMillis - BROAD_MATCH_WINDOW_MS) to
                maxOf(dayEnd, event.endMillis + BROAD_MATCH_WINDOW_MS)
    }

    private fun activeEventSelection(): String =
        "(" + CalendarContract.Events.DELETED + " IS NULL OR " +
                CalendarContract.Events.DELETED + " != 1)"

    private fun withinDetailTolerance(actual: Long, expected: Long): Boolean =
        actual in (expected - DETAIL_MATCH_TOLERANCE_MS)..(expected + DETAIL_MATCH_TOLERANCE_MS)

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
