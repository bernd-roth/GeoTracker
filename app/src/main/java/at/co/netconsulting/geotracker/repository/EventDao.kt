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

    @Query("SELECT * FROM events ORDER BY eventDate DESC")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE userId = :userId ORDER BY eventDate DESC")
    fun getEventsForUser(userId: Long): Flow<List<Event>>

    // Method for pagination
    @Query("SELECT * FROM events ORDER BY eventDate DESC LIMIT :limit OFFSET :offset")
    suspend fun getEventsPaged(limit: Int, offset: Int): List<Event>

    // Count total events
    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    @Query("SELECT * FROM weather WHERE eventId = :eventId ORDER BY weatherId DESC LIMIT 1")
    suspend fun getLatestWeatherForEvent(eventId: Int): Weather?

    // Database cleanup methods
    @Query("""
        SELECT DISTINCT e.* FROM events e
        WHERE e.eventId IN (
            SELECT DISTINCT l.eventId FROM locations l
        )
        AND e.eventId NOT IN (
            SELECT DISTINCT m.eventId FROM metrics m WHERE m.timeInMilliseconds > 0
        )
    """)
    suspend fun getEventsWithLocationsButNoValidMetrics(): List<Event>

    @Query("""
        DELETE FROM events WHERE eventId IN (
            SELECT DISTINCT e.eventId FROM events e
            WHERE e.eventId IN (
                SELECT DISTINCT l.eventId FROM locations l
            )
            AND e.eventId NOT IN (
                SELECT DISTINCT m.eventId FROM metrics m WHERE m.timeInMilliseconds > 0
            )
        )
    """)
    suspend fun deleteEventsWithLocationsButNoValidMetrics(): Int

    // Enhanced cleanup: Find events with invalid timestamps (1970 or future dates)
    @Query("""
        SELECT DISTINCT e.* FROM events e
        WHERE e.eventId IN (
            SELECT DISTINCT m.eventId FROM metrics m
            WHERE m.timeInMilliseconds < 31536000000
            OR m.timeInMilliseconds > :futureThreshold
        )
        AND e.eventId IN (
            SELECT DISTINCT l.eventId FROM locations l
        )
    """)
    suspend fun getEventsWithInvalidTimestamps(futureThreshold: Long = System.currentTimeMillis() + 86400000): List<Event>

    @Query("""
        DELETE FROM events WHERE eventId IN (
            SELECT DISTINCT e.eventId FROM events e
            WHERE e.eventId IN (
                SELECT DISTINCT m.eventId FROM metrics m
                WHERE m.timeInMilliseconds < 31536000000
                OR m.timeInMilliseconds > :futureThreshold
            )
            AND e.eventId IN (
                SELECT DISTINCT l.eventId FROM locations l
            )
        )
    """)
    suspend fun deleteEventsWithInvalidTimestamps(futureThreshold: Long = System.currentTimeMillis() + 86400000): Int

    // Simplified: Just get events with locations but no valid metrics first
    @Query("""
        SELECT DISTINCT e.* FROM events e
        WHERE e.eventId IN (
            SELECT DISTINCT l.eventId FROM locations l
        )
        AND e.eventId NOT IN (
            SELECT DISTINCT m.eventId FROM metrics m
            WHERE m.timeInMilliseconds > 31536000000
            AND m.timeInMilliseconds < :futureThreshold
        )
    """)
    suspend fun getAllInvalidEvents(futureThreshold: Long = System.currentTimeMillis() + 86400000): List<Event>

    @Query("""
        DELETE FROM events WHERE eventId IN (
            SELECT DISTINCT e.eventId FROM events e
            WHERE e.eventId IN (
                SELECT DISTINCT l.eventId FROM locations l
            )
            AND e.eventId NOT IN (
                SELECT DISTINCT m.eventId FROM metrics m
                WHERE m.timeInMilliseconds > 31536000000
                AND m.timeInMilliseconds < :futureThreshold
            )
        )
    """)
    suspend fun deleteAllInvalidEvents(futureThreshold: Long = System.currentTimeMillis() + 86400000): Int

    // Delete specific events by their IDs
    @Query("DELETE FROM events WHERE eventId IN (:eventIds)")
    suspend fun deleteEventsByIds(eventIds: List<Int>): Int
}