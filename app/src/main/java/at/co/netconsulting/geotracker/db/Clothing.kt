package at.co.netconsulting.geotracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "clothing",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Clothing(
    @PrimaryKey(autoGenerate = true) val clothingId: Int = 0,
    val eventId: Int,
    val clothing: String
)