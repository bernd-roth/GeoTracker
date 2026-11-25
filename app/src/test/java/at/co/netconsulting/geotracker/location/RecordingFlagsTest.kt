package at.co.netconsulting.geotracker.location

import at.co.netconsulting.geotracker.repository.RecordingStats
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

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
            val startTime = LocalDateTime.parse("2026-11-24T17:00:00")
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val stats = RecordingStats(startTime, startTime + 1_500_000L, 5000.0)
            flags.update(null, null, true)
            assertTrue(map.overlays.filterIsInstance<Marker>().isEmpty())
            flags.update(start, start, true)
            flags.update(start, end, true, stats)
            val startMarker = map.overlays.filterIsInstance<Marker>().single()
            assertEquals(start, startMarker.position)
            assertEquals("Start time: 17:00:00", startMarker.snippet)

            flags.update(start, end, false, stats)
            flags.update(start, end, false, stats)
            val markers = map.overlays.filterIsInstance<Marker>()
            assertEquals(2, markers.size)
            assertEquals(start, markers.first().position)
            assertEquals(end, markers.last().position)
            assertEquals(
                "End time: 17:25:00<br>Duration: 25m 00s<br>Distance: ${String.format("%.2f km", 5.0)}",
                markers.last().snippet
            )

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
