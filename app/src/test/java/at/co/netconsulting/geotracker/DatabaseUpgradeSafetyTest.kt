package at.co.netconsulting.geotracker

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseUpgradeSafetyTest {
    private fun createSavedDatabase(name: String, version: Int) {
        val context = RuntimeEnvironment.getApplication()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    javaClass.getResourceAsStream("/database-v29.sql")!!.bufferedReader().use { it.readText() }
                        .split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it) }
                    db.execSQL("INSERT INTO User (userId, firstName, lastName, birthDate, weight, height, bmi) VALUES (1, 'Saved', 'Owner', '2000-01-01', 70, 180, 21.6)")
                    db.execSQL("INSERT INTO events (eventId, userId, eventName, eventDate, artOfSport, comment, isUploaded) VALUES (7, 1, 'Saved event', '2026-10-01', 'Running', 'keep', 0)")
                    db.execSQL("INSERT INTO locations (eventId, latitude, longitude, altitude, backyardLap) VALUES (7, 48.0, 16.0, 100, 0)")
                    db.execSQL("INSERT INTO metrics (eventId, heartRate, heartRateDevice, speed, distance, lap, timeInMilliseconds, unity, elevation, elevationGain, elevationLoss, slope) VALUES (7, 120, 'sensor', 3, 1000, 1, 123456, 'km/h', 100, 5, 0, 0)")
                    db.execSQL("INSERT INTO weather (eventId, weatherRestApi, temperature, windSpeed, windDirection, relativeHumidity) VALUES (7, 'saved', 20, 2, 'N', 50)")
                    db.execSQL("INSERT INTO lap_times (sessionId, eventId, lapNumber, startTime, endTime, distance) VALUES ('saved-session', 7, 1, 1000, 301000, 1000)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
    }

    @Test fun `production builder migrates complete version 29 database preserving recordings and children`() = runBlocking(Dispatchers.IO) {
        val context = RuntimeEnvironment.getApplication()
        val name = "saved-v29.db"
        createSavedDatabase(name, 29)
        val database = FitnessTrackerDatabase.buildDatabase(context, name)
        try {
            assertEquals("Saved event", database.eventDao().getEventById(7)!!.eventName)
            assertEquals(1, database.userDao().getUserCount())
            assertEquals(1, database.locationDao().getLocationsForEvent(7).size)
            assertEquals(1000.0, database.metricDao().getMetricsByEventId(7).single().distance, 0.0)
            assertEquals(1, database.weatherDao().getWeatherForEvent(7).size)
            assertEquals(1, database.lapTimeDao().getLapTimesByEvent(7).size)
            assertNull(database.eventDao().getEventById(7)!!.stageGroupId)
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            assertEquals(30, database.openHelper.writableDatabase.version)
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun `missing upgrade and downgrade paths fail without deleting saved rows`() {
        val context = RuntimeEnvironment.getApplication()
        for (version in listOf(2, 31)) {
            val name = "unsupported-$version.db"
            createSavedDatabase(name, version)
            val database = FitnessTrackerDatabase.buildDatabase(context, name)
            try {
                try { database.openHelper.writableDatabase; fail("Unsupported version should fail without recreating tables") }
                catch (expected: IllegalStateException) { assertTrue(expected.message.orEmpty().contains("migration", ignoreCase = true)) }
            } finally { database.close() }
            val saved = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                assertEquals(version, saved.version)
                saved.rawQuery("SELECT eventName FROM events WHERE eventId = 7", null).use { assertTrue(it.moveToFirst()); assertEquals("Saved event", it.getString(0)) }
                saved.rawQuery("SELECT COUNT(*) FROM metrics", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            } finally { saved.close(); context.deleteDatabase(name) }
        }
    }
}
