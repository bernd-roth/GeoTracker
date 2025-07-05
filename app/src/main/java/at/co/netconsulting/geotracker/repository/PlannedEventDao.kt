package at.co.netconsulting.geotracker.repository

import androidx.room.*
import at.co.netconsulting.geotracker.domain.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedEvent(plannedEvent: PlannedEvent): Long

    @Update
    suspend fun updatePlannedEvent(plannedEvent: PlannedEvent)

    @Delete
    suspend fun deletePlannedEvent(plannedEvent: PlannedEvent)

    @Query("SELECT * FROM planned_events WHERE plannedEventId = :plannedEventId")
    suspend fun getPlannedEventById(plannedEventId: Int): PlannedEvent?

    @Query("SELECT * FROM planned_events WHERE userId = :userId ORDER BY plannedEventDate ASC")
    suspend fun getPlannedEventsForUser(userId: Int): List<PlannedEvent>

    @Query("SELECT * FROM planned_events WHERE userId = :userId ORDER BY plannedEventDate ASC")
    fun getPlannedEventsForUserFlow(userId: Int): Flow<List<PlannedEvent>>

    @Query("SELECT * FROM planned_events WHERE plannedEventDate >= date('now') ORDER BY plannedEventDate ASC")
    suspend fun getUpcomingPlannedEvents(): List<PlannedEvent>

    @Query("SELECT * FROM planned_events WHERE userId = :userId AND plannedEventDate >= date('now') ORDER BY plannedEventDate ASC")
    suspend fun getUpcomingPlannedEventsForUser(userId: Int): List<PlannedEvent>

    @Query("SELECT COUNT(*) FROM planned_events WHERE userId = :userId")
    suspend fun getPlannedEventCountForUser(userId: Int): Int

    // New search query that searches through all relevant fields including reminder and recurring
    @Query("""
        SELECT * FROM planned_events 
        WHERE userId = :userId AND (
            plannedEventName LIKE '%' || :searchQuery || '%' OR
            plannedEventCountry LIKE '%' || :searchQuery || '%' OR
            plannedEventCity LIKE '%' || :searchQuery || '%' OR
            plannedEventType LIKE '%' || :searchQuery || '%' OR
            website LIKE '%' || :searchQuery || '%' OR
            comment LIKE '%' || :searchQuery || '%' OR
            reminderDateTime LIKE '%' || :searchQuery || '%' OR
            recurringType LIKE '%' || :searchQuery || '%'
        ) 
        ORDER BY plannedEventDate ASC
    """)
    suspend fun searchPlannedEvents(userId: Int, searchQuery: String): List<PlannedEvent>

    // Get all events for user (both past and future)
    @Query("SELECT * FROM planned_events WHERE userId = :userId ORDER BY plannedEventDate DESC")
    suspend fun getAllPlannedEventsForUser(userId: Int): List<PlannedEvent>

    // Get events with active reminders
    @Query("SELECT * FROM planned_events WHERE userId = :userId AND isReminderActive = 1 AND reminderDateTime != ''")
    suspend fun getEventsWithActiveReminders(userId: Int): List<PlannedEvent>

    // Get upcoming events with reminders (for rescheduling after boot)
    @Query("""
        SELECT * FROM planned_events 
        WHERE isReminderActive = 1 
        AND reminderDateTime != '' 
        AND datetime(reminderDateTime) > datetime('now')
        ORDER BY reminderDateTime ASC
    """)
    suspend fun getUpcomingReminders(): List<PlannedEvent>
}