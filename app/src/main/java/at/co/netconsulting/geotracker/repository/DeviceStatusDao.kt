package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.DeviceStatus

@Dao
interface DeviceStatusDao {
    @Insert
    suspend fun insertDeviceStatus(deviceStatus: DeviceStatus): Long

    @Query("SELECT * FROM device_status WHERE eventId = :eventId ORDER BY statusId DESC LIMIT 1")
    suspend fun getLastDeviceStatusByEvent(eventId: Int): DeviceStatus?
}