package at.co.netconsulting.geotracker.data

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("current_weather")
    val currentWeather: CurrentWeather,
    @SerializedName("relativehumidity_2m")
    val relativeHumidity: List<Int>,
    val time: List<String>
)