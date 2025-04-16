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

    /**
     * Get locations within a geographic bounding box with simplification based on sample rate
     * Higher sample rates = fewer points (1 = every point, 2 = every 2nd point, etc.)
     */
    @Query("""
        SELECT * FROM locations 
        WHERE eventId = :eventId 
        AND latitude BETWEEN :latSouth AND :latNorth 
        AND longitude BETWEEN :lonWest AND :lonEast
        AND locationId % :sampleRate = 0
        ORDER BY locationId ASC
    """)
    suspend fun getLocationsInBoundingBox(
        eventId: Int,
        latNorth: Double,
        latSouth: Double,
        lonEast: Double,
        lonWest: Double,
        sampleRate: Int = 1
    ): List<Location>

    /**
     * Get total count of locations for an event
     */
    @Query("SELECT COUNT(*) FROM locations WHERE eventId = :eventId")
    suspend fun getLocationCountForEvent(eventId: Int): Int

    /**
     * Get the min and max coordinates for an event to determine the full path bounds
     */
    @Query("""
        SELECT 
            MIN(latitude) as minLat, 
            MAX(latitude) as maxLat, 
            MIN(longitude) as minLon, 
            MAX(longitude) as maxLon 
        FROM locations 
        WHERE eventId = :eventId
    """)
    suspend fun getPathBounds(eventId: Int): PathBounds?

    /**
     * Get a downsampled path (for zoomed out views)
     */
    @Query("""
        SELECT * FROM locations
        WHERE eventId = :eventId
        AND locationId % :sampleRate = 0
        ORDER BY locationId ASC
        LIMIT :limit
    """)
    suspend fun getDownsampledPath(
        eventId: Int,
        sampleRate: Int,
        limit: Int = 1000
    ): List<Location>
}