package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.Location

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: Location): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<Location>)

    @Update
    suspend fun updateLocation(location: Location)

    @Delete
    suspend fun deleteLocation(location: Location)

    @Query("SELECT * FROM locations WHERE locationId = :locationId")
    suspend fun getLocationById(locationId: Int): Location?

    @Query("SELECT * FROM locations WHERE eventId = :eventId ORDER BY locationId ASC")
    suspend fun getLocationsForEvent(eventId: Int): List<Location>

    @Query("SELECT * FROM locations WHERE eventId = :eventId ORDER BY locationId ASC")
    suspend fun getLocationsByEventId(eventId: Int): List<Location>
}