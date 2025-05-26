package at.co.netconsulting.geotracker.domain

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.co.netconsulting.geotracker.repository.CurrentRecordingDao
import at.co.netconsulting.geotracker.repository.DeviceStatusDao
import at.co.netconsulting.geotracker.repository.EventDao
import at.co.netconsulting.geotracker.repository.LapTimeDao
import at.co.netconsulting.geotracker.repository.LocationDao
import at.co.netconsulting.geotracker.repository.MetricDao
import at.co.netconsulting.geotracker.repository.UserDao
import at.co.netconsulting.geotracker.repository.WeatherDao

@Database(
    entities = [
        User::class,
        Event::class,
        Metric::class,
        Location::class,
        Weather::class,
        DeviceStatus::class,
        CurrentRecording::class,
        LapTime::class
    ],
    version = 7, // Increment to force new migration
    exportSchema = false
)
abstract class FitnessTrackerDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun metricDao(): MetricDao
    abstract fun locationDao(): LocationDao
    abstract fun weatherDao(): WeatherDao
    abstract fun deviceStatusDao(): DeviceStatusDao
    abstract fun currentRecordingDao(): CurrentRecordingDao
    abstract fun lapTimeDao(): LapTimeDao

    companion object {
        @Volatile
        private var INSTANCE: FitnessTrackerDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
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

                database.execSQL("DROP TABLE metrics")
                database.execSQL("ALTER TABLE metrics_new RENAME TO metrics")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS index_device_status_eventId")

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

                try {
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
                    // Continue with migration anyway
                }

                database.execSQL("DROP TABLE IF EXISTS device_status")
                database.execSQL("ALTER TABLE device_status_new RENAME TO device_status")
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_device_status_eventId ON device_status(eventId)
                """)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS current_recording (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        eventId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        altitude REAL NOT NULL,
                        speed REAL NOT NULL,
                        distance REAL NOT NULL,
                        lap INTEGER NOT NULL,
                        currentLapDistance REAL NOT NULL,
                        movementDuration TEXT NOT NULL,
                        inactivityDuration TEXT NOT NULL,
                        movementStateJson TEXT NOT NULL,
                        lazyStateJson TEXT NOT NULL,
                        startDateTimeEpoch INTEGER NOT NULL
                    )
                """)

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_current_recording_sessionId 
                    ON current_recording(sessionId)
                """)

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS lap_times (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        eventId INTEGER NOT NULL,
                        lapNumber INTEGER NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        distance REAL NOT NULL
                    )
                """)

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_lap_times_sessionId 
                    ON lap_times(sessionId)
                """)

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_lap_times_eventId 
                    ON lap_times(eventId)
                """)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS current_recording")

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS current_recording (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        eventId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        altitude REAL NOT NULL,
                        speed REAL NOT NULL,
                        distance REAL NOT NULL,
                        lap INTEGER NOT NULL,
                        currentLapDistance REAL NOT NULL,
                        movementDuration TEXT NOT NULL,
                        inactivityDuration TEXT NOT NULL,
                        movementStateJson TEXT NOT NULL,
                        lazyStateJson TEXT NOT NULL,
                        startDateTimeEpoch INTEGER NOT NULL
                    )
                """)

                database.execSQL("CREATE INDEX IF NOT EXISTS index_current_recording_sessionId ON current_recording(sessionId)")
            }
        }

        // Add completely new migration to fix the schema issue once and for all
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Completely drop and recreate the problematic table
                database.execSQL("DROP TABLE IF EXISTS current_recording")
                database.execSQL("DROP INDEX IF EXISTS index_current_recording_sessionId")

                // Create the table with exactly the schema Room expects
                database.execSQL("""
                    CREATE TABLE current_recording (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        eventId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        altitude REAL NOT NULL,
                        speed REAL NOT NULL,
                        distance REAL NOT NULL,
                        lap INTEGER NOT NULL,
                        currentLapDistance REAL NOT NULL,
                        movementDuration TEXT NOT NULL,
                        inactivityDuration TEXT NOT NULL,
                        movementStateJson TEXT NOT NULL,
                        lazyStateJson TEXT NOT NULL,
                        startDateTimeEpoch INTEGER NOT NULL
                    )
                """)

                // Create the index exactly as Room expects
                database.execSQL("CREATE INDEX index_current_recording_sessionId ON current_recording(sessionId)")
            }
        }

        fun getInstance(context: Context): FitnessTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FitnessTrackerDatabase::class.java,
                    "fitness_tracker.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7 // Add the new migration
                    )
                    .fallbackToDestructiveMigration() // Keep as safety net
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}