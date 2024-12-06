package at.co.netconsulting.geotracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route_points")
data class RoutePoint(
    val eventId: Int,
    val latitude: Double,
    val longitude: Double
)