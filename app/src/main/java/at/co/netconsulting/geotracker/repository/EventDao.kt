package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import at.co.netconsulting.geotracker.domain.Event

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: Event): Long
}