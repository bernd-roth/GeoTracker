package at.co.netconsulting.geotracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "device_status",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DeviceStatus(
    @PrimaryKey(autoGenerate = true) val statusId: Int = 0,
    val eventId: Int,
    val numberOfSatellites: Int,
    val sensorAccuracy: String,
    val signalStrength: String,
    val batteryLevel: String,
    val connectionStatus: String
)