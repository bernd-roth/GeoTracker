package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.Waypoint

@Dao
interface WaypointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: Waypoint): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<Waypoint>)

    @Update
    suspend fun updateWaypoint(waypoint: Waypoint)

    @Delete
    suspend fun deleteWaypoint(waypoint: Waypoint)

    @Query("SELECT * FROM waypoints WHERE waypointId = :waypointId")
    suspend fun getWaypointById(waypointId: Long): Waypoint?

    @Query("SELECT * FROM waypoints WHERE eventId = :eventId ORDER BY name ASC")
    suspend fun getWaypointsForEvent(eventId: Int): List<Waypoint>

    @Query("DELETE FROM waypoints WHERE eventId = :eventId")
    suspend fun deleteWaypointsForEvent(eventId: Int)

    @Query("SELECT COUNT(*) FROM waypoints WHERE eventId = :eventId")
    suspend fun getWaypointCountForEvent(eventId: Int): Int
}