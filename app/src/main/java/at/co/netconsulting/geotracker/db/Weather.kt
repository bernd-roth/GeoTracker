package at.co.netconsulting.geotracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "weather",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Weather(
    @PrimaryKey(autoGenerate = true) val weatherId: Int = 0,
    val eventId: Int,
    val weatherRestApi: String,
    val temperature: Float,
    val windSpeed: Float,
    val windDirection: String,
    val relativeHumidity: Int
)