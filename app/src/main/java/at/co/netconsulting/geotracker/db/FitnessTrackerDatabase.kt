package at.co.netconsulting.geotracker.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import at.co.netconsulting.geotracker.dao.DeviceStatusDao
import at.co.netconsulting.geotracker.dao.EventDao
import at.co.netconsulting.geotracker.dao.LocationDao
import at.co.netconsulting.geotracker.dao.MetricDao
import at.co.netconsulting.geotracker.dao.UserDao
import at.co.netconsulting.geotracker.dao.WeatherDao

@Database(
    entities = [User::class, Event::class, Metric::class, Location::class, Weather::class, DeviceStatus::class],
    version = 1,
    exportSchema = false
)
abstract class FitnessTrackerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun metricDao(): MetricDao
    abstract fun locationDao(): LocationDao
    abstract fun weatherDao(): WeatherDao
    abstract fun deviceStatusDao(): DeviceStatusDao

    companion object {
        @Volatile
        private var INSTANCE: FitnessTrackerDatabase? = null

        fun getInstance(context: Context): FitnessTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FitnessTrackerDatabase::class.java,
                    "fitness_tracker.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}