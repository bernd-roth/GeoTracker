package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.Location

@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(location: Location): Long

    @Query("SELECT * FROM locations WHERE eventId = :eventId ORDER BY locationId DESC LIMIT 1")
    suspend fun getLastLocationByEvent(eventId: Int): Location?

    @Insert
    suspend fun insertAll(locations: kotlin.collections.MutableList<at.co.netconsulting.geotracker.domain.Location>)

    @Query("DELETE FROM locations WHERE eventId = :eventId")
    suspend fun deleteLocationsByEventId(eventId: Int)

    @Query("SELECT * FROM locations WHERE eventId = :eventId ORDER BY locationId ASC")
    suspend fun getLocationsByEventId(eventId: Int): List<Location>

    @Query("SELECT * FROM locations WHERE eventId = :eventId ORDER BY locationId")
    suspend fun getLocationsForEvent(eventId: Int): List<Location>

    @Query("SELECT * FROM locations WHERE eventId = :eventId ORDER BY locationId LIMIT 1")
    suspend fun getFirstLocationForEvent(eventId: Int): Location?
}