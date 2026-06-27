package at.co.netconsulting.geotracker.composables

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CadencePointMapTest {
    @Test
    fun `running cadence displays both feet as steps per minute`() {
        val display = cadenceDisplayFor("Trail Running")

        assertEquals("spm", display.unit)
        assertEquals(160, display.value(80))
    }

    @Test
    fun `non-running cadence retains raw GPX cycles`() {
        val display = cadenceDisplayFor("Cycling")

        assertEquals("cycles/min", display.unit)
        assertEquals(80, display.value(80))
    }

    @Test
    fun `live recording maps metric to same location index`() {
        assertEquals(42, cadenceLocationIndex(42, metricCount = 100, locationCount = 100))
    }

    @Test
    fun `GPX import maps metric after extra initial location`() {
        assertEquals(43, cadenceLocationIndex(42, metricCount = 99, locationCount = 100))
    }

    @Test
    fun `mismatched streams use proportional location index`() {
        assertEquals(50, cadenceLocationIndex(25, metricCount = 51, locationCount = 101))
    }

    @Test
    fun `invalid metric index has no location`() {
        assertNull(cadenceLocationIndex(5, metricCount = 5, locationCount = 5))
    }
}
