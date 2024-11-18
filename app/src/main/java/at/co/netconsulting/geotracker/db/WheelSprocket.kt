package at.co.netconsulting.geotracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "wheel_sprocket",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WheelSprocket(
    @PrimaryKey(autoGenerate = true) val wheelId: Int = 0,
    val eventId: Int,
    val wheelSize: Float?,
    val sprocket: Int?
)