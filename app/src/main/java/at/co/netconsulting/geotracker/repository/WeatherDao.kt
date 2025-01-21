package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.Weather

@Dao
interface WeatherDao {
    @Insert
    suspend fun insertWeather(weather: Weather): Long

    @Query("SELECT * FROM weather WHERE eventId = :eventId ORDER BY weatherId DESC LIMIT 1")
    suspend fun getLastWeatherByEvent(eventId: Int): Weather?

    @Query("DELETE FROM weather WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: Int)

    @Query("DELETE FROM weather WHERE eventId = :eventId")
    suspend fun deleteWeatherByEventId(eventId: Int)

    @Query("SELECT * FROM weather WHERE eventId = :eventId")
    suspend fun getAllWeatherByEvent(eventId: Int): List<Weather>
}