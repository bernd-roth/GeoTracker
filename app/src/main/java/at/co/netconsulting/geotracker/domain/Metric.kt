package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "metrics",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Metric(
    @PrimaryKey(autoGenerate = true) val metricId: Int = 0,
    val eventId: Int,
    val heartRate: Int,
    val heartRateDevice: String,
    val speed: Float,
    val distance: Double,
    val cadence: Int?,
    val lap: Int,
    val timeInMilliseconds: Long,
    val unity: String,
    val elevation: Float = 0f,
    val elevationGain: Float = 0f,
    val elevationLoss: Float = 0f,
    val steps: Int? = null,
    val strideLength: Float? = null,
    val temperature: Float? = null,
    val accuracy: Float? = null,
    // Barometer data, more reliable than GPS data
    val pressure: Float? = null,                    // Atmospheric pressure in hPa/mbar
    val pressureAccuracy: Int? = null,              // Sensor accuracy (0=unreliable, 3=high)
    val altitudeFromPressure: Float? = null,        // Calculated altitude from pressure
    val seaLevelPressure: Float? = null             // Reference sea level pressure for altitude calculation
)