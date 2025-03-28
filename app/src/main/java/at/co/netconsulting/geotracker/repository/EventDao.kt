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
}