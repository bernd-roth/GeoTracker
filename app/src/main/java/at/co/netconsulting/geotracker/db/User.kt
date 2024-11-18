package at.co.netconsulting.geotracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "User")
data class User(
    @PrimaryKey(autoGenerate = true) val userId: Int = 0,
    val firstName: String,
    val lastName: String,
    val birthDate: String, // Store as ISO date (YYYY-MM-DD)
    val weight: Float,
    val height: Float
)