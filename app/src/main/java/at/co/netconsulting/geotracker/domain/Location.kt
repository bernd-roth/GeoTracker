package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "locations",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Location(
    @PrimaryKey(autoGenerate = true) val locationId: Int = 0,
    val eventId: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val backyardLap: Int = 0
)