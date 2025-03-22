package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import at.co.netconsulting.geotracker.domain.Weather

@Dao
interface WeatherDao {
    @Insert
    suspend fun insertWeather(weather: Weather): Long
}