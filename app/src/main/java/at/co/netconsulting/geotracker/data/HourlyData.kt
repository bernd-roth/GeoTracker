package at.co.netconsulting.geotracker.data

import com.google.gson.annotations.SerializedName

data class HourlyData(
    @SerializedName("time") val time: List<String>?,
    @SerializedName("relativehumidity_2m") val relativeHumidity: List<Int>?
)