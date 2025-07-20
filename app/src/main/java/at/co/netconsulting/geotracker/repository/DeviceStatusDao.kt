package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.DeviceStatus

@Dao
interface DeviceStatusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceStatus(deviceStatus: DeviceStatus): Long

    @Update
    suspend fun updateDeviceStatus(deviceStatus: DeviceStatus)

    @Delete
    suspend fun deleteDeviceStatus(deviceStatus: DeviceStatus)

    @Query("SELECT * FROM device_status WHERE deviceStatusId = :deviceStatusId")
    suspend fun getDeviceStatusById(deviceStatusId: Int): DeviceStatus?

    @Query("SELECT * FROM device_status WHERE eventId = :eventId ORDER BY deviceStatusId DESC LIMIT 1")
    suspend fun getLastDeviceStatusByEvent(eventId: Int): DeviceStatus?

    @Query("SELECT MAX(CAST(numberOfSatellites AS INTEGER)) FROM device_status WHERE eventId = :eventId")
    suspend fun getMaxSatellitesForEvent(eventId: Int): Int?

    @Query("SELECT MIN(CAST(numberOfSatellites AS INTEGER)) FROM device_status WHERE eventId = :eventId AND numberOfSatellites > 0")
    suspend fun getMinSatellitesForEvent(eventId: Int): Int?

    @Query("SELECT AVG(CAST(numberOfSatellites AS INTEGER)) FROM device_status WHERE eventId = :eventId AND numberOfSatellites > 0")
    suspend fun getAvgSatellitesForEvent(eventId: Int): Double?

    // Alternative: Get all satellite data in one query (more efficient)
    @Query("SELECT numberOfSatellites FROM device_status WHERE eventId = :eventId AND numberOfSatellites IS NOT NULL AND numberOfSatellites > '0' ORDER BY deviceStatusId")
    suspend fun getSatelliteCountsForEvent(eventId: Int): List<String>
}