package at.co.netconsulting.geotracker.sync

import at.co.netconsulting.geotracker.domain.Location
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class UploadCoordinatesTest {
    @Test
    fun `omits missing fixes while preserving original metric indices and local data`() {
        val locations = listOf(
            point(-999.0, -999.0), point(48.1, 16.1),
            point(48.2, -999.0), point(-999.0, 16.2), point(48.3, 16.3)
        )
        val result = locationsForUpload(locations)
        org.junit.Assert.assertEquals(listOf(1, 4), result.map { it.index })
        org.junit.Assert.assertEquals(listOf(locations[1], locations[4]), result.map { it.value })
        org.junit.Assert.assertEquals(5, locations.size)
    }

    @Test
    fun `refuses upload or replacement when all fixes are missing`() {
        assertThrows(IllegalArgumentException::class.java) {
            locationsForUpload(listOf(point(-999.0, -999.0)))
        }
    }

    @Test
    fun `still reports other invalid coordinates at their original point number`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            locationsForUpload(listOf(point(-999.0, -999.0), point(100.0, 16.3)))
        }
        assertTrue(error.message!!.contains("GPS point 2"))
    }

    @Test
    fun `accepts valid coordinates including zero and geographic boundaries`() {
        listOf(0.0 to 0.0, 48.2 to 16.3, -90.0 to -180.0, 90.0 to 180.0).forEach { (lat, lon) ->
            validateUploadCoordinates(point(lat, lon), 1)
        }
    }

    @Test
    fun `rejects invalid latitude with point identity and value`() {
        listOf(100.0, -91.0, 90.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
            .forEach { latitude ->
                val error = assertThrows(IllegalArgumentException::class.java) {
                    validateUploadCoordinates(point(latitude, 16.3), 7)
                }
                assertTrue(error.message!!.contains("GPS point 7 (location ID 42)"))
                assertTrue(error.message!!.contains("invalid latitude $latitude"))
            }
    }

    @Test
    fun `rejects invalid longitude`() {
        listOf(180.1, -181.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
            .forEach { longitude ->
                val error = assertThrows(IllegalArgumentException::class.java) {
                    validateUploadCoordinates(point(48.2, longitude), 3)
                }
                assertTrue(error.message!!.contains("invalid longitude $longitude"))
            }
    }

    private fun point(latitude: Double, longitude: Double) = Location(
        locationId = 42, eventId = 1, latitude = latitude, longitude = longitude, altitude = 0.0
    )
}
