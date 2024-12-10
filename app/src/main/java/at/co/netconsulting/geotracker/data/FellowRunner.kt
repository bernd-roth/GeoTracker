package at.co.netconsulting.geotracker.data

import com.google.gson.annotations.SerializedName

data class FellowRunner(
    val person: String,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val distance: String,
    @SerializedName("currentSpeed") val speed: Float,
    val altitude: String,
    @SerializedName("timestamp") val formattedTimestamp: String
)