package at.co.netconsulting.geotracker.tools

import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
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

data class GoogleCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String
)

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

data class CalendarExportResult(
    val exported: Int,
    val skipped: Int,
    val failed: Int,
    val syncRequested: Boolean
)

data class CalendarDeleteResult(
    val deletedEntries: Int,
    val foundEvents: Int,
    val missingEvents: Int,
    val remainingEntries: Int = 0
)

object CalendarExporter {
    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    private const val GEOTRACKER_UID_PREFIX = "geotracker-"
    private const val GEOTRACKER_UID_SUFFIX = "@at.co.netconsulting.geotracker"
    private const val MIN_TIMED_EVENT_DURATION_MS = 60_000L
    private const val DETAIL_MATCH_TOLERANCE_MS = 2 * 60_000L
    private const val BROAD_MATCH_WINDOW_MS = 18 * 60 * 60_000L

    internal data class CalendarDeleteMatch(
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

    internal fun selectEventsForExport(
        events: List<CalendarExportEvent>,
        selectedEventIds: Set<Int>?
    ): List<CalendarExportEvent> = if (selectedEventIds == null) {
        events
    } else {
        events.filter { it.sourceEventId in selectedEventIds }
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
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE
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
            val accountTypeIndex = cursor.getColumnIndexOrThrow(
                CalendarContract.Calendars.ACCOUNT_TYPE
            )
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        GoogleCalendar(
                            cursor.getLong(idIndex),
                            cursor.getString(nameIndex),
                            cursor.getString(accountIndex),
                            cursor.getString(accountTypeIndex)
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    fun exportEvents(
        context: Context,
        calendar: GoogleCalendar,
        events: List<CalendarExportEvent>
    ): CalendarExportResult {
        val resolver = context.contentResolver
        val calendarId = calendar.id
        val uniqueEvents = events.distinctBy { it.uid }
        val existingUids = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.UID_2445),
            existingExportSelection(),
            arrayOf(calendarId.toString()),
            null
        )?.use { cursor ->
            val uidIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.UID_2445)
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(uidIndex))
            }
        } ?: emptySet()

        val eventsToInsert = uniqueEvents.filterNot { it.uid in existingUids }
        var exported = 0
        eventsToInsert.chunked(100).forEach { chunk ->
            val operations = ArrayList<ContentProviderOperation>(chunk.size)
            chunk.forEach { event ->
                operations += ContentProviderOperation
                    .newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValues(toContentValues(calendarId, event))
                    .build()
            }
            val results = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
            val insertedIds = results.mapNotNull { result ->
                result.uri?.lastPathSegment?.toLongOrNull()
            }
            exported += countPersistedEvents(resolver, insertedIds)
        }
        val syncRequested = requestCalendarSync(calendar)
        return CalendarExportResult(
            exported = exported,
            skipped = uniqueEvents.size - eventsToInsert.size,
            failed = eventsToInsert.size - exported,
            syncRequested = syncRequested
        )
    }

    internal fun existingExportSelection(): String =
        CalendarContract.Events.CALENDAR_ID + " = ? AND " +
                CalendarContract.Events.UID_2445 + " IS NOT NULL AND " +
                activeEventSelection()

    private fun countPersistedEvents(resolver: ContentResolver, eventIds: List<Long>): Int {
        if (eventIds.isEmpty()) return 0
        val placeholders = eventIds.joinToString(",") { "?" }
        val selection = CalendarContract.Events._ID + " IN (" + placeholders + ") AND " +
                activeEventSelection()
        return resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            eventIds.map { it.toString() }.toTypedArray(),
            null
        )?.use { it.count } ?: 0
    }

    private fun requestCalendarSync(calendar: GoogleCalendar): Boolean = runCatching {
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(
            Account(calendar.accountName, calendar.accountType),
            CalendarContract.AUTHORITY,
            extras
        )
    }.isSuccess

    fun deleteFromAllWritableGoogleCalendars(
        context: Context,
        events: List<CalendarExportEvent>
    ): CalendarDeleteResult {
        val calendars = getWritableGoogleCalendars(context)
        val result = deleteFromCalendars(context, calendars.map { it.id }, events)
        if (result.deletedEntries > 0) requestCalendarSync(calendars)
        return result
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
        return deleteMatches(resolver, uniqueEvents.map { it.uid }.toSet(), matches)
    }

    fun deleteAllExports(
        context: Context,
        calendars: List<GoogleCalendar>,
        events: List<CalendarExportEvent>
    ): CalendarDeleteResult {
        val calendarIds = calendars.map { it.id }
        val uniqueEvents = events.distinctBy { it.uid }
        if (calendarIds.isEmpty()) {
            return CalendarDeleteResult(
                deletedEntries = 0,
                foundEvents = 0,
                missingEvents = uniqueEvents.size
            )
        }

        val matches = (
                findGeoTrackerExportMatches(context, calendarIds) +
                        findCalendarEventMatches(context, calendarIds, uniqueEvents)
                ).distinct()
        val result = deleteMatches(
            context.contentResolver,
            uniqueEvents.map { it.uid }.toSet(),
            matches
        )
        if (result.deletedEntries > 0) requestCalendarSync(calendars)
        return result
    }

    private fun requestCalendarSync(calendars: List<GoogleCalendar>) {
        calendars.distinctBy { it.accountName to it.accountType }.forEach(::requestCalendarSync)
    }

    private fun deleteMatches(
        resolver: ContentResolver,
        sourceUids: Set<String>,
        matches: List<CalendarDeleteMatch>
    ): CalendarDeleteResult {
        val matchedEventIds = matches.map { it.calendarEventId }.distinct()
        matchedEventIds.forEach { eventId ->
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            resolver.delete(uri, null, null)
        }

        val remainingEventIds = findActiveCalendarEventIds(resolver, matchedEventIds)
        return summarizeDeleteResult(sourceUids, matches, remainingEventIds)
    }

    internal fun summarizeDeleteResult(
        sourceUids: Set<String>,
        matches: List<CalendarDeleteMatch>,
        remainingEventIds: Set<Long>
    ): CalendarDeleteResult {
        val matchedEventIds = matches.map { it.calendarEventId }.toSet()
        val foundEventUids = matches.map { it.sourceUid }.toSet().intersect(sourceUids)

        return CalendarDeleteResult(
            deletedEntries = (matchedEventIds - remainingEventIds).size,
            foundEvents = foundEventUids.size,
            missingEvents = sourceUids.size - foundEventUids.size,
            remainingEntries = remainingEventIds.intersect(matchedEventIds).size
        )
    }

    private fun findActiveCalendarEventIds(
        resolver: ContentResolver,
        eventIds: List<Long>
    ): Set<Long> = buildSet {
        eventIds.chunked(100).forEach { idChunk ->
            val placeholders = idChunk.joinToString(",") { "?" }
            val selection = CalendarContract.Events._ID + " IN (" + placeholders + ") AND " +
                    activeEventSelection()
            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                selection,
                idChunk.map { it.toString() }.toTypedArray(),
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                while (cursor.moveToNext()) add(cursor.getLong(idIndex))
            }
        }
    }

    private fun findGeoTrackerExportMatches(
        context: Context,
        calendarIds: List<Long>
    ): List<CalendarDeleteMatch> {
        val calendarPlaceholders = calendarIds.joinToString(",") { "?" }
        val selection = CalendarContract.Events.CALENDAR_ID +
                " IN (" + calendarPlaceholders + ") AND " +
                CalendarContract.Events.UID_2445 + " LIKE ? AND " +
                activeEventSelection()
        val args = calendarIds.map { it.toString() } +
                "$GEOTRACKER_UID_PREFIX%$GEOTRACKER_UID_SUFFIX%"
        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.UID_2445),
            selection,
            args.toTypedArray(),
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val uidIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.UID_2445)
            buildList {
                while (cursor.moveToNext()) {
                    add(CalendarDeleteMatch(cursor.getLong(idIndex), cursor.getString(uidIndex)))
                }
            }
        } ?: emptyList()
    }

    private fun findCalendarEventMatches(
        context: Context,
        calendarIds: List<Long>,
        events: List<CalendarExportEvent>
    ): List<CalendarDeleteMatch> {
        val uids = events.map { it.uid }
        val uidMatches = findCalendarEventMatchesByUid(context, calendarIds, uids)
        val uidMatched = uidMatches.map { it.sourceUid }.toSet()
        val detailMatches = events.filterNot { it.uid in uidMatched }.flatMap { event ->
            findCalendarEventMatchesByDetails(context, calendarIds, event)
        }
        val detailMatched = detailMatches.map { it.sourceUid }.toSet()
        val instanceMatches = events.filterNot {
            it.uid in uidMatched || it.uid in detailMatched
        }.flatMap { event ->
            findCalendarEventMatchesByInstances(context, calendarIds, event)
        }
        val preciseMatchedUids = (uidMatches + detailMatches + instanceMatches)
            .map { it.sourceUid }
            .toSet()
        val broadMatches = events.filterNot { it.uid in preciseMatchedUids }.flatMap { event ->
            findCalendarEventMatchesByBroadDetails(context, calendarIds, event)
        }
        return (uidMatches + detailMatches + instanceMatches + broadMatches)
            .distinct()
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
        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        put(
            CalendarContract.Events.EVENT_TIMEZONE,
            if (event.allDay) TimeZone.getTimeZone("UTC").id else TimeZone.getDefault().id
        )
        put(CalendarContract.Events.UID_2445, event.uid)
        event.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        event.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
    }
}
