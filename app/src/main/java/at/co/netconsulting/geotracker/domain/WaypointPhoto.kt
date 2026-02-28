package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "waypoint_photos",
    foreignKeys = [ForeignKey(
        entity = Waypoint::class,
        parentColumns = ["waypointId"],
        childColumns = ["waypointId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["waypointId"])]
)
data class WaypointPhoto(
    @PrimaryKey(autoGenerate = true) val photoId: Long = 0,
    val waypointId: Long,
    val photoPath: String   // absolute path inside filesDir/waypoint_photos/
)
