package at.co.netconsulting.geotracker.repository

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Remote timestamps without an offset are UTC, as in the server database. */
internal fun parseRecordingTimestamp(value: String?): Long? {
    val timestamp = value?.trim()?.replace(' ', 'T')?.takeIf { it.isNotEmpty() }
        ?: return null
    return (runCatching { OffsetDateTime.parse(timestamp).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull())
        ?.takeIf { it > 0L }
}
