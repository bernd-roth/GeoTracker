package at.co.netconsulting.geotracker.tools

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeocodingHelperTest {

    @Test
    fun `returns completed geocoding work`() = runTest {
        val result = GeocodingHelper.runWithGeocodingTimeout(timeoutMs = 1_000L) {
            "Vienna"
        }

        assertEquals("Vienna", result)
    }

    @Test
    fun `abandons geocoding work when the callback stalls`() = runTest {
        val result = GeocodingHelper.runWithGeocodingTimeout(timeoutMs = 100L) {
            delay(1_000L)
            "too late"
        }

        assertNull(result)
    }
}
