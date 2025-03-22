package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.Location

@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(location: Location): Long
}