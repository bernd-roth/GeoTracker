package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.Event

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: Event): Long

    @Query("SELECT * FROM events WHERE userId = :userId ORDER BY eventDate DESC LIMIT 1")
    suspend fun getLastEventByUser(userId: Int): Event?

    @Query("SELECT eventId FROM events WHERE userId = :userId ORDER BY eventDate DESC LIMIT 1")
    suspend fun getLastEventIdByUser(userId: Int): Int
}