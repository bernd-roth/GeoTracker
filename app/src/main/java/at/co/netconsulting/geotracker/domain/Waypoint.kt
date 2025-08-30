package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "waypoints",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["eventId"])
    ]
)
data class Waypoint(
    @PrimaryKey(autoGenerate = true) val waypointId: Long = 0,
    val eventId: Int,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String? = null,
    val elevation: Double? = null
)