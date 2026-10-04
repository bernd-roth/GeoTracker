package at.co.netconsulting.geotracker

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import at.co.netconsulting.geotracker.domain.*
import at.co.netconsulting.geotracker.repository.RemoteSessionImporter
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteSessionImporterTest {
    private lateinit var database: FitnessTrackerDatabase
    private lateinit var preferences: SharedPreferences
    private lateinit var importer: RemoteSessionImporter

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, FitnessTrackerDatabase::class.java).allowMainThreadQueries().build()
        preferences = context.getSharedPreferences("import-tests", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("firstname", "Test").putString("lastname", "Owner")
            .putString("birthdate", "2000-01-01").putLong("userId", 999).commit()
        importer = RemoteSessionImporter(database, preferences)
    }
    @After fun close() { database.close() }

    @Test fun `missing times are not replaced with download times and laps are optional`() = runBlocking {
        val source = session()
        val eventId = importer.importSession(source.copy(
            gpsPoints = listOf(
                source.gpsPoints.single().copy(receivedAt = null),
                source.gpsPoints.single().copy(receivedAt = "invalid"),
                source.gpsPoints.single().copy(receivedAt = "2026-10-01T10:00:00.123+02:00")
            ),
            lapTimes = emptyList()
        ))
        val expected = java.time.Instant.parse("2026-10-01T08:00:00.123Z").toEpochMilli()
        assertEquals(listOf(0L, 0L, expected), database.metricDao().getMetricsByEventId(eventId).map { it.timeInMilliseconds })
        assertEquals(expected, database.metricDao().getEventTimeRange(eventId)!!.minTime)
        assertTrue(database.lapTimeDao().getLapTimesByEvent(eventId).isEmpty())
    }

    @Test fun `empty database and stale preference recreate owner and save all data`() = runBlocking {
        val eventId = importer.importSession(session())
        val event = database.eventDao().getEventById(eventId)!!
        assertNotEquals(999L, event.userId)
        assertEquals("Test", database.userDao().getUserById(event.userId)!!.firstName)
        assertEquals(event.userId, preferences.getLong("userId", -1))
        assertEquals(1, database.locationDao().getLocationsForEvent(eventId).size)
        assertEquals(1, database.metricDao().getMetricsByEventId(eventId).size)
        assertEquals(1, database.lapTimeDao().getLapTimesByEvent(eventId).size)
        assertEquals(1, database.weatherDao().getWeatherForEvent(eventId).size)
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun `matching owner is reused without replacing rows or deleting their recordings`() = runBlocking {
        val userId = database.userDao().insertUser(User(firstName = "Test", lastName = "Owner", birthDate = "2000-01-01", weight = 70f, height = 180f))
        val existingId = database.eventDao().insertEvent(Event(userId = userId, eventName = "Keep me", eventDate = "2026-01-01", artOfSport = "Running", comment = "")).toInt()
        database.locationDao().insertLocation(Location(eventId = existingId, latitude = 48.0, longitude = 16.0, altitude = 100.0))
        val importedId = importer.importSession(session())
        assertEquals(userId, database.eventDao().getEventById(importedId)!!.userId)
        assertEquals(1, database.userDao().getUserCount())
        assertEquals(1, database.locationDao().getLocationsForEvent(existingId).size)
    }

    @Test fun `saved user ID belonging to someone else is not reused`() = runBlocking {
        val other = database.userDao().insertUser(User(firstName = "Different", lastName = "Person", birthDate = "1990-01-01", weight = 70f, height = 180f))
        preferences.edit().putLong("userId", other).commit()
        val eventId = importer.importSession(session())
        assertNotEquals(other, database.eventDao().getEventById(eventId)!!.userId)
        assertNotNull(database.userDao().getUserById(other))
    }

    @Test fun `repeating a download does not duplicate recordings or samples`() = runBlocking {
        val firstId = importer.importSession(session())
        assertEquals(firstId, importer.importSession(session()))
        assertEquals(1, database.eventDao().getEventCount())
        assertEquals(1, database.metricDao().getMetricsByEventId(firstId).size)
    }

    @Test fun `failed child insertion rolls back user event and earlier samples then allows retry`() = runBlocking {
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_metric BEFORE INSERT ON metrics BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { importer.importSession(session()); fail("Expected import to fail") } catch (expected: android.database.sqlite.SQLiteException) { }
        assertEquals(0, database.eventDao().getEventCount())
        assertEquals(0, database.userDao().getUserCount())
        database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM locations").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        assertEquals(999L, preferences.getLong("userId", -1))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_metric")
        assertTrue(importer.importSession(session()) > 0)
    }

    private fun session() = GeoTrackerApiClient.FullSessionData(
        sessionId = "remote-test", eventName = "Recovered recording", sportType = "Running",
        sportFamily = null, discipline = null, eventFormat = null, comment = "", startDateTime = "2026-10-01T08:00:00",
        startCity = null, startCountry = null, startAddress = null, endCity = null, endCountry = null, endAddress = null,
        gpsPoints = listOf(GeoTrackerApiClient.GpsPointData(
            latitude = 48.0, longitude = 16.0, altitude = 100.0, receivedAt = "2026-10-01T08:00:00Z",
            currentSpeed = 3f, distance = 100.0, heartRate = 100, lap = 1, pressure = null, pressureAccuracy = null,
            altitudeFromPressure = null, seaLevelPressure = null, slope = null, temperature = 20f,
            cumulativeElevationGain = null, horizontalAccuracy = 5f, verticalAccuracyMeters = null,
            numberOfSatellites = 10, usedNumberOfSatellites = 8, speedAccuracyMetersPerSecond = null,
            windSpeed = null, windDirection = null, humidity = null, weatherCode = null
        )), lapTimes = listOf(GeoTrackerApiClient.LapTimeData(1, 1000, 61000, 100.0))
    )
}
