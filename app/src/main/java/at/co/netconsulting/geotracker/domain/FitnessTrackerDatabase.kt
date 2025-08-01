package at.co.netconsulting.geotracker.domain

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import at.co.netconsulting.geotracker.repository.*
import android.util.Log

@Database(
    entities = [
        User::class,
        Event::class,
        Metric::class,
        Location::class,
        Weather::class,
        DeviceStatus::class,
        CurrentRecording::class,
        LapTime::class,
        PlannedEvent::class,
        Clothing::class,
        WheelSprocket::class,
        Network::class
    ],
    version = 15, // ✅ INCREMENTED FROM 14 TO 15
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

    // Add DAOs for additional entities
    abstract fun plannedEventDao(): PlannedEventDao
    abstract fun clothingDao(): ClothingDao
    abstract fun wheelSprocketDao(): WheelSprocketDao
    abstract fun networkDao(): NetworkDao

    companion object {
        @Volatile
        private var INSTANCE: FitnessTrackerDatabase? = null
        private const val TAG = "FitnessTrackerDB"

        // ✅ ALL EXISTING MIGRATIONS (keeping all your existing ones)
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

        // MIGRATION 7->8: Add practical fields (no power/advanced running dynamics)
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add practical columns to metrics table that can be collected without expensive hardware
                database.execSQL("ALTER TABLE metrics ADD COLUMN steps INTEGER")
                database.execSQL("ALTER TABLE metrics ADD COLUMN strideLength REAL")
                database.execSQL("ALTER TABLE metrics ADD COLUMN temperature REAL")
                database.execSQL("ALTER TABLE metrics ADD COLUMN accuracy REAL")

                // Create missing tables that exist in your entities but not in database

                // Planned Events table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS planned_events (
                        plannedEventId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        plannedEventName TEXT NOT NULL,
                        plannedEventDate TEXT NOT NULL,
                        plannedEventType TEXT NOT NULL,
                        plannedEventCountry TEXT NOT NULL,
                        plannedEventCity TEXT NOT NULL,
                        plannedLatitude REAL,
                        plannedLongitude REAL,
                        FOREIGN KEY (userId) REFERENCES User(userId) ON DELETE CASCADE
                    )
                """)

                // Clothing table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS clothing (
                        clothingId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER NOT NULL,
                        clothing TEXT NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(eventId) ON DELETE CASCADE
                    )
                """)

                // Wheel Sprocket table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS wheel_sprocket (
                        wheelId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER NOT NULL,
                        wheelSize REAL,
                        sprocket INTEGER,
                        FOREIGN KEY (eventId) REFERENCES events(eventId) ON DELETE CASCADE
                    )
                """)

                // Network table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS network (
                        networkId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER NOT NULL,
                        websocketIpAddress TEXT NOT NULL,
                        restApiAddress TEXT NOT NULL,
                        FOREIGN KEY (eventId) REFERENCES events(eventId) ON DELETE CASCADE
                    )
                """)

                // Create indices for foreign keys
                database.execSQL("CREATE INDEX IF NOT EXISTS index_planned_events_userId ON planned_events(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_clothing_eventId ON clothing(eventId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_wheel_sprocket_eventId ON wheel_sprocket(eventId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_network_eventId ON network(eventId)")
            }
        }

        // NEW MIGRATION 8->9: Add competitions fields to planned_events table
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE planned_events ADD COLUMN isEnteredAndFinished INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE planned_events ADD COLUMN website TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE planned_events ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            }
        }

        // NEW MIGRATION 9->10: Add reminder fields to planned_events table
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE planned_events ADD COLUMN reminderDateTime TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE planned_events ADD COLUMN isReminderActive INTEGER NOT NULL DEFAULT 0")
            }
        }

        // NEW MIGRATION 10->11: Add recurring reminder fields to planned_events table
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE planned_events ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE planned_events ADD COLUMN recurringType TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE planned_events ADD COLUMN recurringInterval INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE planned_events ADD COLUMN recurringEndDate TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE planned_events ADD COLUMN recurringDaysOfWeek TEXT NOT NULL DEFAULT ''")
            }
        }

        // MIGRATION 11->12: Add barometer fields to metrics table
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE metrics ADD COLUMN pressure REAL")
                database.execSQL("ALTER TABLE metrics ADD COLUMN pressureAccuracy INTEGER")
                database.execSQL("ALTER TABLE metrics ADD COLUMN altitudeFromPressure REAL")
                database.execSQL("ALTER TABLE metrics ADD COLUMN seaLevelPressure REAL")
            }
        }

        // MIGRATION 12->13: Fix Event table userId data type consistency
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Fix Event table userId data type from Long to Int
                database.execSQL("""
                    CREATE TABLE events_new (
                        eventId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        eventName TEXT NOT NULL,
                        eventDate TEXT NOT NULL,
                        artOfSport TEXT NOT NULL,
                        comment TEXT NOT NULL,
                        FOREIGN KEY (userId) REFERENCES User(userId) ON DELETE CASCADE
                    )
                """)

                // Copy data (cast Long to Int if events table exists and has data)
                try {
                    database.execSQL("""
                        INSERT INTO events_new (eventId, userId, eventName, eventDate, artOfSport, comment)
                        SELECT eventId, CAST(userId AS INTEGER), eventName, eventDate, artOfSport, comment
                        FROM events
                    """)
                } catch (e: Exception) {
                    // If events table doesn't exist or is empty, that's okay
                }

                database.execSQL("DROP TABLE IF EXISTS events")
                database.execSQL("ALTER TABLE events_new RENAME TO events")
            }
        }

        // ✅ NEW MIGRATION 13->14: Fix lap_times table schema completely
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d(TAG, "Starting migration 13->14 for lap_times table")

                try {
                    // Step 1: Backup existing data if table exists
                    val backupData = mutableListOf<Array<Any?>>()

                    try {
                        val cursor = database.query("SELECT * FROM lap_times ORDER BY id")
                        while (cursor.moveToNext()) {
                            val rowData = arrayOfNulls<Any>(7)
                            rowData[0] = if (cursor.isNull(0)) null else cursor.getLong(0)  // id
                            rowData[1] = if (cursor.isNull(1)) "" else cursor.getString(1)   // sessionId
                            rowData[2] = if (cursor.isNull(2)) 0 else cursor.getInt(2)      // eventId
                            rowData[3] = if (cursor.isNull(3)) 0 else cursor.getInt(3)      // lapNumber
                            rowData[4] = if (cursor.isNull(4)) 0 else cursor.getLong(4)     // startTime
                            rowData[5] = if (cursor.isNull(5)) 0 else cursor.getLong(5)     // endTime
                            rowData[6] = if (cursor.isNull(6)) 0.0 else cursor.getDouble(6) // distance
                            backupData.add(rowData)
                        }
                        cursor.close()
                        Log.d(TAG, "Backed up ${backupData.size} lap_times records")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not backup lap_times data (table might not exist): ${e.message}")
                    }

                    // Step 2: Drop existing table and indices
                    database.execSQL("DROP INDEX IF EXISTS index_lap_times_sessionId")
                    database.execSQL("DROP INDEX IF EXISTS index_lap_times_eventId")
                    database.execSQL("DROP TABLE IF EXISTS lap_times")

                    // Step 3: Create the table exactly as Room expects it
                    database.execSQL("""
                        CREATE TABLE lap_times (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            sessionId TEXT NOT NULL,
                            eventId INTEGER NOT NULL,
                            lapNumber INTEGER NOT NULL,
                            startTime INTEGER NOT NULL,
                            endTime INTEGER NOT NULL,
                            distance REAL NOT NULL
                        )
                    """)

                    // Step 4: Create indices exactly as Room expects
                    database.execSQL("""
                        CREATE INDEX index_lap_times_sessionId ON lap_times(sessionId)
                    """)

                    database.execSQL("""
                        CREATE INDEX index_lap_times_eventId ON lap_times(eventId)
                    """)

                    // Step 5: Restore backed up data
                    if (backupData.isNotEmpty()) {
                        Log.d(TAG, "Restoring ${backupData.size} lap_times records")
                        database.beginTransaction()
                        try {
                            backupData.forEach { row ->
                                database.execSQL("""
                                    INSERT INTO lap_times (sessionId, eventId, lapNumber, startTime, endTime, distance)
                                    VALUES (?, ?, ?, ?, ?, ?)
                                """, arrayOf(
                                    row[1] ?: "",      // sessionId
                                    row[2] ?: 0,       // eventId
                                    row[3] ?: 0,       // lapNumber
                                    row[4] ?: 0L,      // startTime
                                    row[5] ?: 0L,      // endTime
                                    row[6] ?: 0.0      // distance
                                ))
                            }
                            database.setTransactionSuccessful()
                            Log.d(TAG, "Successfully restored all lap_times data")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error restoring lap_times data: ${e.message}", e)
                        } finally {
                            database.endTransaction()
                        }
                    }

                    Log.d(TAG, "Migration 13->14 completed successfully")

                } catch (e: Exception) {
                    Log.e(TAG, "Critical error in migration 13->14, creating clean lap_times table", e)

                    // If anything fails catastrophically, just create a clean table
                    try {
                        database.execSQL("DROP INDEX IF EXISTS index_lap_times_sessionId")
                        database.execSQL("DROP INDEX IF EXISTS index_lap_times_eventId")
                        database.execSQL("DROP TABLE IF EXISTS lap_times")

                        database.execSQL("""
                            CREATE TABLE lap_times (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                sessionId TEXT NOT NULL,
                                eventId INTEGER NOT NULL,
                                lapNumber INTEGER NOT NULL,
                                startTime INTEGER NOT NULL,
                                endTime INTEGER NOT NULL,
                                distance REAL NOT NULL
                            )
                        """)

                        database.execSQL("CREATE INDEX index_lap_times_sessionId ON lap_times(sessionId)")
                        database.execSQL("CREATE INDEX index_lap_times_eventId ON lap_times(eventId)")

                        Log.d(TAG, "Created clean lap_times table as fallback")
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "Even fallback table creation failed", fallbackError)
                        throw fallbackError
                    }
                }
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d(TAG, "Starting migration 14->15 - cleaning up lap_times table")

                try {
                    // This migration does nothing to the table structure since it should already be correct
                    // We're just bumping the version to reset the schema validation
                    Log.d(TAG, "Migration 14->15 completed - no changes needed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in migration 14->15", e)
                    throw e
                }
            }
        }

        fun getInstance(context: Context): FitnessTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    Log.d(TAG, "Creating database instance")
                    Room.databaseBuilder(
                        context.applicationContext,
                        FitnessTrackerDatabase::class.java,
                        "fitness_tracker.db"
                    )
                        .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            MIGRATION_6_7,
                            MIGRATION_7_8,
                            MIGRATION_8_9,
                            MIGRATION_9_10,
                            MIGRATION_10_11,
                            MIGRATION_11_12,
                            MIGRATION_12_13,
                            MIGRATION_13_14,
                            MIGRATION_14_15  // ✅ ADD THE NEW MIGRATION
                        )
                        .addCallback(object : RoomDatabase.Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                Log.d(TAG, "Database created successfully")
                            }

                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                Log.d(TAG, "Database opened successfully")
                            }
                        })
                        .fallbackToDestructiveMigration() // Keep as safety net
                        .build()
                        .also {
                            INSTANCE = it
                            Log.d(TAG, "Database instance created and cached")
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating database instance", e)
                    throw e
                }
            }
        }
    }
}