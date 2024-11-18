package at.co.netconsulting.geotracker.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.db.Event

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: Event): Long // Returns the ID of the inserted row

    @Query("SELECT * FROM events WHERE userId = :userId ORDER BY eventDate DESC LIMIT 1")
    suspend fun getLastEventByUser(userId: Int): Event?
}