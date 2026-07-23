package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.Weather
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)

    @Query("SELECT * FROM events WHERE eventId = :eventId")
    suspend fun getEventById(eventId: Int): Event?

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId, MIN(timeInMilliseconds) as startTime
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
    """)
    fun getAllEvents(): Flow<List<Event>>

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId, MIN(timeInMilliseconds) as startTime
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        WHERE e.eventSource IS NULL OR e.eventSource = 'recorded'
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
    """)
    fun getRecordedEvents(): Flow<List<Event>>

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId, MIN(timeInMilliseconds) as startTime
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        WHERE e.eventSource IS NULL OR e.eventSource = 'recorded'
        ORDER BY COALESCE(m.startTime, 0) ASC, e.eventDate ASC
    """)
    suspend fun getRecordedEventsForCalendar(): List<Event>

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId, MIN(timeInMilliseconds) as startTime
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        WHERE e.userId = :userId
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
    """)
    fun getEventsForUser(userId: Long): Flow<List<Event>>

    // Method for pagination
    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId, MIN(timeInMilliseconds) as startTime
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getEventsPaged(limit: Int, offset: Int): List<Event>

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId, MIN(timeInMilliseconds) as startTime
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        WHERE e.eventSource IS NULL OR e.eventSource = 'recorded'
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getRecordedEventsPaged(limit: Int, offset: Int): List<Event>

    // Method for source-filtered pagination (recorded vs imported)
    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId, MIN(timeInMilliseconds) as startTime
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        WHERE e.eventSource = :source
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getEventsPagedBySource(source: String, limit: Int, offset: Int): List<Event>

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId,
                   MIN(timeInMilliseconds) AS startTime,
                   MAX(speed) AS maxSpeedMps,
                   AVG(speed) AS avgSpeedMps,
                   MAX(distance) AS maxDistanceMeters
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        WHERE (e.eventSource IS NULL OR e.eventSource = 'recorded')
        AND (
            LOWER(e.eventName) LIKE '%' || :query || '%' OR
            LOWER(e.artOfSport) LIKE '%' || :query || '%' OR
            LOWER(e.comment) LIKE '%' || :query || '%' OR
            e.eventDate LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.startCity, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.startCountry, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.startAddress, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.endCity, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.endCountry, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.endAddress, '')) LIKE '%' || :query || '%' OR
            CAST(e.eventId AS TEXT) LIKE '%' || :query || '%' OR
            CAST(ROUND(COALESCE(m.maxSpeedMps, 0) * 3.6, 1) AS TEXT) LIKE '%' || :query || '%' OR
            CAST(ROUND(COALESCE(m.avgSpeedMps, 0) * 3.6, 1) AS TEXT) LIKE '%' || :query || '%' OR
            CAST(ROUND(COALESCE(m.maxDistanceMeters, 0) / 1000.0, 2) AS TEXT) LIKE '%' || :query || '%' OR
            (:query IN ('speed', 'avg speed', 'average speed', 'max speed') AND COALESCE(m.maxSpeedMps, 0) > 0) OR
            (:query IN ('distance', 'km') AND COALESCE(m.maxDistanceMeters, 0) > 0)
        )
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
        LIMIT :limit
    """)
    suspend fun searchRecordedEvents(query: String, limit: Int): List<Event>

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId,
                   MIN(timeInMilliseconds) AS startTime,
                   MAX(speed) AS maxSpeedMps,
                   AVG(speed) AS avgSpeedMps,
                   MAX(distance) AS maxDistanceMeters
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        WHERE e.eventSource = 'imported'
        AND (
            LOWER(e.eventName) LIKE '%' || :query || '%' OR
            LOWER(e.artOfSport) LIKE '%' || :query || '%' OR
            LOWER(e.comment) LIKE '%' || :query || '%' OR
            e.eventDate LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.startCity, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.startCountry, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.startAddress, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.endCity, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.endCountry, '')) LIKE '%' || :query || '%' OR
            LOWER(COALESCE(e.endAddress, '')) LIKE '%' || :query || '%' OR
            CAST(e.eventId AS TEXT) LIKE '%' || :query || '%' OR
            CAST(ROUND(COALESCE(m.maxSpeedMps, 0) * 3.6, 1) AS TEXT) LIKE '%' || :query || '%' OR
            CAST(ROUND(COALESCE(m.avgSpeedMps, 0) * 3.6, 1) AS TEXT) LIKE '%' || :query || '%' OR
            CAST(ROUND(COALESCE(m.maxDistanceMeters, 0) / 1000.0, 2) AS TEXT) LIKE '%' || :query || '%' OR
            (:query IN ('speed', 'avg speed', 'average speed', 'max speed') AND COALESCE(m.maxSpeedMps, 0) > 0) OR
            (:query IN ('distance', 'km') AND COALESCE(m.maxDistanceMeters, 0) > 0)
        )
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
        LIMIT :limit
    """)
    suspend fun searchImportedEvents(query: String, limit: Int): List<Event>

    // Count total events
    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    @Query("SELECT * FROM weather WHERE eventId = :eventId ORDER BY weatherId DESC LIMIT 1")
    suspend fun getLatestWeatherForEvent(eventId: Int): Weather?

    // Upload tracking methods
    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN (
            SELECT eventId, MIN(timeInMilliseconds) as startTime
            FROM metrics
            GROUP BY eventId
        ) m ON e.eventId = m.eventId
        WHERE e.isUploaded = 0
        ORDER BY COALESCE(m.startTime, 0) DESC, e.eventDate DESC
    """)
    suspend fun getUnuploadedEvents(): List<Event>

    @Query("""
        UPDATE events
        SET sessionId = :sessionId,
            isUploaded = :isUploaded,
            uploadedAt = :uploadedAt
        WHERE eventId = :eventId
    """)
    suspend fun updateEventUploadStatus(
        eventId: Int,
        sessionId: String?,
        isUploaded: Boolean,
        uploadedAt: Long?
    )

    @Query("SELECT COUNT(*) FROM events WHERE isUploaded = 0")
    suspend fun getUnuploadedEventCount(): Int

    @Query("SELECT * FROM events WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getEventBySessionId(sessionId: String): Event?

    @Query("""
        SELECT * FROM events
        WHERE eventDate LIKE :yearPrefix || '%'
        AND (eventSource IS NULL OR eventSource = 'recorded')
        ORDER BY eventDate ASC
    """)
    suspend fun getEventsForYear(yearPrefix: String): List<Event>

    @Query("""
        UPDATE events
        SET startCity = :startCity,
            startCountry = :startCountry,
            startAddress = :startAddress
        WHERE eventId = :eventId
    """)
    suspend fun updateEventStartLocation(eventId: Int, startCity: String?, startCountry: String?, startAddress: String?)

    @Query("""
        UPDATE events
        SET endCity = :endCity,
            endCountry = :endCountry,
            endAddress = :endAddress
        WHERE eventId = :eventId
    """)
    suspend fun updateEventEndLocation(eventId: Int, endCity: String?, endCountry: String?, endAddress: String?)
}
