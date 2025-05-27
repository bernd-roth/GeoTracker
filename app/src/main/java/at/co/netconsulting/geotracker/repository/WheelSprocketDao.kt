package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.WheelSprocket

@Dao
interface WheelSprocketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWheelSprocket(wheelSprocket: WheelSprocket): Long

    @Update
    suspend fun updateWheelSprocket(wheelSprocket: WheelSprocket)

    @Delete
    suspend fun deleteWheelSprocket(wheelSprocket: WheelSprocket)

    @Query("SELECT * FROM wheel_sprocket WHERE wheelId = :wheelId")
    suspend fun getWheelSprocketById(wheelId: Int): WheelSprocket?

    @Query("SELECT * FROM wheel_sprocket WHERE eventId = :eventId ORDER BY wheelId DESC LIMIT 1")
    suspend fun getWheelSprocketForEvent(eventId: Int): WheelSprocket?

    @Query("SELECT * FROM wheel_sprocket WHERE eventId = :eventId")
    suspend fun getAllWheelSprocketsForEvent(eventId: Int): List<WheelSprocket>

    @Query("DELETE FROM wheel_sprocket WHERE eventId = :eventId")
    suspend fun deleteWheelSprocketsForEvent(eventId: Int)
}