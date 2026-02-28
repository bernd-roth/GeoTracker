package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.WaypointPhoto

@Dao
interface WaypointPhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: WaypointPhoto): Long

    @Query("SELECT * FROM waypoint_photos WHERE waypointId = :waypointId")
    suspend fun getPhotosForWaypoint(waypointId: Long): List<WaypointPhoto>

    @Delete
    suspend fun deletePhoto(photo: WaypointPhoto)

    @Query("DELETE FROM waypoint_photos WHERE waypointId = :waypointId")
    suspend fun deletePhotosForWaypoint(waypointId: Long)
}
