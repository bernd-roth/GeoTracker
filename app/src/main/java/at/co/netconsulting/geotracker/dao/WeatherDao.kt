package at.co.netconsulting.geotracker.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.db.Weather

@Dao
interface WeatherDao {
    @Insert
    suspend fun insertWeather(weather: Weather): Long

    @Query("SELECT * FROM weather WHERE eventId = :eventId ORDER BY weatherId DESC LIMIT 1")
    suspend fun getLastWeatherByEvent(eventId: Int): Weather?
}