package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.Event

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: Event): Long

    @Query("SELECT * FROM events WHERE eventId = :eventId")
    suspend fun getEventById(eventId: Int): Event

    @Query("SELECT e.userId, e.eventId, e.eventDate, e.eventName , e.artOfSport, e.comment FROM events e")
    suspend fun getAllEvents(): List<Event>
}