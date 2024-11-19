package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "network",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Network(
    @PrimaryKey(autoGenerate = true) val networkId: Int = 0,
    val eventId: Int,
    val websocketIpAddress: String,
    val restApiAddress: String
)