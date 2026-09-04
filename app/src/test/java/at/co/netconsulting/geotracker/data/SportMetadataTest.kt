package at.co.netconsulting.geotracker.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SportMetadataTest {
    @Test
    fun `maps legacy races to running formats`() {
        assertEquals(
            SportMetadata("Running", "Road Running", "Half marathon"),
            SportCatalog.fromLegacy("Halfmarathon")
        )
        assertEquals(
            SportMetadata("Running", eventFormat = "Ultramarathon (other)"),
            SportCatalog.fromLegacy("Ultramarathon")
        )
    }

    @Test
    fun `maps a discipline to its family`() {
        assertEquals(
            SportMetadata("Cycling", "Mountain Bike"),
            SportCatalog.fromLegacy("Mountain Bike")
        )
        assertEquals(
            SportMetadata("Cycling", "E-Bike"),
            SportCatalog.fromLegacy("E-Bike")
        )
        assertEquals(
            SportMetadata("Running", "Track Running"),
            SportCatalog.fromLegacy("track running")
        )
    }

    @Test
    fun `stored family is authoritative when optional fields were cleared`() {
        val metadata = SportCatalog.resolve(
            legacySport = "Road Running",
            family = "Running",
            discipline = null,
            eventFormat = null
        )

        assertEquals("Running", metadata.family)
        assertNull(metadata.discipline)
    }

    @Test
    fun `special event modes retain their legacy compatibility value`() {
        assertEquals(
            "Backyard Ultra",
            SportMetadata("Running", eventFormat = "Backyard Ultra").legacySportType()
        )
    }
}
