package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.Weather

@Dao
interface WeatherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeather(weather: Weather): Long

    @Update
    suspend fun updateWeather(weather: Weather)

    @Delete
    suspend fun deleteWeather(weather: Weather)

    @Query("SELECT * FROM weather WHERE weatherId = :weatherId")
    suspend fun getWeatherById(weatherId: Int): Weather?

    @Query("SELECT * FROM weather WHERE eventId = :eventId")
    suspend fun getWeatherForEvent(eventId: Int): List<Weather>
}