package at.co.netconsulting.geotracker.domain

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.co.netconsulting.geotracker.repository.DeviceStatusDao
import at.co.netconsulting.geotracker.repository.EventDao
import at.co.netconsulting.geotracker.repository.LocationDao
import at.co.netconsulting.geotracker.repository.MetricDao
import at.co.netconsulting.geotracker.repository.UserDao
import at.co.netconsulting.geotracker.repository.WeatherDao

@Database(
    entities = [User::class, Event::class, Metric::class, Location::class, Weather::class, DeviceStatus::class],
    version = 4,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the old table and create a new one with all required columns
                database.execSQL("""
                    CREATE TABLE metrics_new (
                        metricId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER NOT NULL,
                        heartRate INTEGER NOT NULL,
                        heartRateDevice TEXT NOT NULL,
                        speed REAL NOT NULL,
                        distance REAL NOT NULL,
                        cadence INTEGER,
                        lap INTEGER NOT NULL,
                        timeInMilliseconds INTEGER NOT NULL,
                        unity TEXT NOT NULL,
                        elevation REAL NOT NULL,
                        elevationGain REAL NOT NULL,
                        elevationLoss REAL NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(eventId) ON DELETE CASCADE
                    )
                """)

                // Copy data from the old table to the new one
                database.execSQL("""
                    INSERT INTO metrics_new (
                        metricId, eventId, heartRate, heartRateDevice, 
                        speed, distance, cadence, lap, 
                        timeInMilliseconds, unity, 
                        elevation, elevationGain, elevationLoss
                    )
                    SELECT 
                        metricId, eventId, heartRate, heartRateDevice, 
                        speed, distance, cadence, lap, 
                        timeInMilliseconds, unity, 
                        0, 0, 0
                    FROM metrics
                """)

                // Remove the old table
                database.execSQL("DROP TABLE metrics")

                // Rename the new table to the correct name
                database.execSQL("ALTER TABLE metrics_new RENAME TO metrics")
            }
        }

        // Add a new migration from version 3 to 4
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Instead of trying to modify the existing table, we'll drop and recreate it
                // This is a more reliable approach for SQLite schema changes

                // First, check if we need to drop the index if it exists
                database.execSQL("DROP INDEX IF EXISTS index_device_status_eventId")

                // Create new table with exactly the expected schema
                database.execSQL("""
                    CREATE TABLE device_status_new (
                        deviceStatusId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER NOT NULL,
                        numberOfSatellites TEXT NOT NULL,
                        sensorAccuracy TEXT NOT NULL,
                        signalStrength TEXT NOT NULL,
                        batteryLevel TEXT NOT NULL,
                        connectionStatus TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(eventId) ON DELETE CASCADE
                    )
                """)

                // Copy data from old table if it exists
                try {
                    // Try to check if the old table has data
                    database.execSQL("""
                        INSERT INTO device_status_new (
                            deviceStatusId, eventId, numberOfSatellites, sensorAccuracy,
                            signalStrength, batteryLevel, connectionStatus, sessionId
                        )
                        SELECT 
                            deviceStatusId, eventId, numberOfSatellites, sensorAccuracy,
                            signalStrength, batteryLevel, connectionStatus, sessionId
                        FROM device_status
                    """)
                } catch (e: Exception) {
                    // If copying fails, it might be due to a schema mismatch or empty table
                    // We'll continue with the migration anyway
                }

                // Drop the old table
                database.execSQL("DROP TABLE IF EXISTS device_status")

                // Rename the new table
                database.execSQL("ALTER TABLE device_status_new RENAME TO device_status")

                // Add the foreign key constraint
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_device_status_eventId ON device_status(eventId)
                """)
            }
        }

        // A destructive fallback migration that will delete and recreate all tables
        // Use this only if regular migrations fail repeatedly
        private val FALLBACK_MIGRATION_ALL = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // This will delete all data but ensure the database works
                database.execSQL("PRAGMA foreign_keys = OFF")

                // Drop existing tables if they exist
                database.execSQL("DROP TABLE IF EXISTS device_status")

                // Create tables with current schema
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS device_status (
                        deviceStatusId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER NOT NULL,
                        numberOfSatellites TEXT NOT NULL,
                        sensorAccuracy TEXT NOT NULL,
                        signalStrength TEXT NOT NULL,
                        batteryLevel TEXT NOT NULL,
                        connectionStatus TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(eventId) ON DELETE CASCADE
                    )
                """)

                database.execSQL("CREATE INDEX IF NOT EXISTS index_device_status_eventId ON device_status(eventId)")

                database.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        fun getInstance(context: Context): FitnessTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FitnessTrackerDatabase::class.java,
                    "fitness_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_3_4)
                    .fallbackToDestructiveMigration() // This is a last resort if migrations fail
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}