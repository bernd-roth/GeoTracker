package at.co.netconsulting.geotracker.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GpxImporterTest {
    @Test
    fun `accepts GPX without an XML declaration`() {
        val content = """
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="TOURDATA">
                <trk>
                    <trkseg>
                        <trkpt lat="47.9537772711989" lon="13.5852813720703">
                            <time>2026-07-16T22:22:43Z</time>
                        </trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent().toByteArray()

        assertTrue(isValidGpxContent(content))
    }

    @Test
    fun `accepts GPX with an XML declaration`() {
        val content = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="GeoTracker">
                <wpt lat="47.9538" lon="13.5864" />
            </gpx>
        """.trimIndent().toByteArray()

        assertTrue(isValidGpxContent(content))
    }

    @Test
    fun `rejects malformed GPX XML`() {
        val content = """
            <gpx version="1.1" creator="GeoTracker">
                <trk>
            </gpx>
        """.trimIndent().toByteArray()

        assertFalse(isValidGpxContent(content))
    }

    @Test
    fun `rejects XML whose root is not GPX`() {
        val content = """
            <route version="1.1">
                <trk />
            </route>
        """.trimIndent().toByteArray()

        assertFalse(isValidGpxContent(content))
    }
}
