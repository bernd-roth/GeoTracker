package at.co.netconsulting.geotracker.tools

import org.junit.Assert.*
import org.junit.Test

class MediaLocationTest {
    @Test fun `reads ISO 6709 video locations including altitude and southern hemisphere`() {
        assertEquals(MediaLocation(48.2082, 16.3738), MediaLocation.parseVideoLocation("+48.2082+016.3738/"))
        assertEquals(MediaLocation(-33.86, -151.2), MediaLocation.parseVideoLocation("-33.86-151.20+15.5/"))
        assertEquals(MediaLocation(0.0, 0.0), MediaLocation.parseVideoLocation("+00.0+000.0/"))
    }

    @Test fun `ignores absent malformed and out of range coordinates`() {
        listOf(null, "", "48,16", "+91.0+016.0/", "+48.0+181.0/", "+48.0", "+48.0+016.0/junk")
            .forEach { assertNull(it, MediaLocation.parseVideoLocation(it)) }
        assertNull(MediaLocation.from(Double.NaN, 16.0))
        assertNull(MediaLocation.from(48.0, Double.POSITIVE_INFINITY))
        assertNull(MediaLocation.from(null, 16.0))
        assertNull(MediaLocation.from(48.0, null))
        assertEquals(MediaLocation(-90.0, 180.0), MediaLocation.from(-90.0, 180.0))
    }
}
