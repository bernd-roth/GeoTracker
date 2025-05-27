package at.co.netconsulting.geotracker.repository

import androidx.room.*
import at.co.netconsulting.geotracker.domain.*

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

    @Query("SELECT * FROM planned_events WHERE plannedEventDate >= date('now') ORDER BY plannedEventDate ASC")
    suspend fun getUpcomingPlannedEvents(): List<PlannedEvent>

    @Query("SELECT * FROM planned_events WHERE userId = :userId AND plannedEventDate >= date('now') ORDER BY plannedEventDate ASC")
    suspend fun getUpcomingPlannedEventsForUser(userId: Int): List<PlannedEvent>

    @Query("SELECT COUNT(*) FROM planned_events WHERE userId = :userId")
    suspend fun getPlannedEventCountForUser(userId: Int): Int
}