package at.co.netconsulting.geotracker.location

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingFlagsTest {
    @Test fun `start stays fixed and finish appears only after stopping with no duplicate markers`() {
        val map = MapView(RuntimeEnvironment.getApplication())
        map.setUseDataConnection(false)
        try {
            val flags = RecordingFlags(map)
            val start = GeoPoint(48.2, 16.3)
            val end = GeoPoint(48.3, 16.4)
            flags.update(null, null, true)
            assertTrue(map.overlays.filterIsInstance<Marker>().isEmpty())
            flags.update(start, start, true)
            flags.update(start, end, true)
            assertEquals(start, map.overlays.filterIsInstance<Marker>().single().position)

            flags.update(start, end, false)
            flags.update(start, end, false)
            val markers = map.overlays.filterIsInstance<Marker>()
            assertEquals(2, markers.size)
            assertEquals(start, markers.first().position)
            assertEquals(end, markers.last().position)

            flags.clear()
            flags.update(end, end, true)
            assertEquals(end, map.overlays.filterIsInstance<Marker>().single().position)
            flags.clear()
            assertTrue(map.overlays.filterIsInstance<Marker>().isEmpty())
        } finally {
            map.onDetach()
        }
    }
}
