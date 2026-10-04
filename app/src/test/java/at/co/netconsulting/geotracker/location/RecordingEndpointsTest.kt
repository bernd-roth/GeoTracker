package at.co.netconsulting.geotracker.location

import androidx.room.Room
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingEndpointsTest {
    @Test fun `endpoints include only first and last recorded locations for the selected event`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FitnessTrackerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val user = database.userDao().insertUser(User(firstName = "Test", lastName = "User", birthDate = "2000-01-01", weight = 70f, height = 180f))
            val event = database.eventDao().insertEvent(Event(userId = user, eventName = "Flags", eventDate = "2026-10-04", artOfSport = "Hiking", comment = "")).toInt()
            val dao = database.locationDao()
            assertTrue(dao.observeRecordingEndpoints(event).first().isEmpty())
            val first = dao.insertLocation(Location(eventId = event, latitude = 48.2, longitude = 16.3, altitude = 0.0)).toInt()
            assertEquals(listOf(first), dao.observeRecordingEndpoints(event).first().map { it.locationId })
            dao.insertLocation(Location(eventId = event, latitude = 49.0, longitude = 17.0, altitude = 0.0))
            // Return to the starting coordinates: these are still two distinct endpoints.
            val last = dao.insertLocation(Location(eventId = event, latitude = 48.2, longitude = 16.3, altitude = 0.0)).toInt()
            assertEquals(listOf(first, last), dao.observeRecordingEndpoints(event).first().map { it.locationId })
            assertTrue(dao.observeRecordingEndpoints(event + 1).first().isEmpty())
        } finally {
            database.close()
        }
    }
}
