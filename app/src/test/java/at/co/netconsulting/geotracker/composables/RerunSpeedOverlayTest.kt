package at.co.netconsulting.geotracker.composables

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class RerunSpeedOverlayTest {
    @Test
    fun `speed boundaries use the slope palette from blue to red`() {
        val speeds = listOf(0f, 2.01f, 5.01f, 8.01f, 12.01f, 16.01f, 20.01f)
        val colors = listOf(0xFF000080, 0xFF0000FF, 0xFF87CEEB, 0xFF008B00,
            0xFFFFFF00, 0xFFFF6600, 0xFFFF0000).map { it.toInt() }
        assertEquals(colors, speeds.map { RerunSpeedBand.forSpeed(it).color })
        RerunSpeedBand.entries.dropLast(1).forEach {
            assertEquals(it, RerunSpeedBand.forSpeed(it.maxKmh))
        }
        assertEquals(RerunSpeedBand.STOPPED, RerunSpeedBand.forSpeed(Float.NaN))
    }

    @Test
    fun `speed data aligns with recordings and imported extra starting point`() {
        assertEquals(2, rerunMetricIndex(2, 4, 4))
        assertEquals(0, rerunMetricIndex(1, 5, 4))
        assertEquals(3, rerunMetricIndex(4, 5, 4))
        assertEquals(2, rerunMetricIndex(4, 9, 5))
        assertNull(rerunMetricIndex(1, 4, 0))
    }

    @Test
    fun `animation reveals connected colour segments and retains full colours`() {
        val points = (0..4).map { GeoPoint(48.0, 16.0 + it * 0.001) }
        val overlay = RerunSpeedOverlay(points, listOf(1f, 1f, 1f, 25f, 25f))
        val lines = overlay.items.map { it as Polyline }
        assertEquals(2, lines.size)
        val earlyPoint = GeoPoint(48.0, 16.0005)
        overlay.showProgress(0.125f, earlyPoint)
        assertTrue(lines[0].isEnabled)
        assertFalse(lines[1].isEnabled)
        assertEquals(earlyPoint, lines[0].actualPoints.last())
        val leadingPoint = GeoPoint(48.0, 16.0025)
        overlay.showProgress(0.625f, leadingPoint)
        assertEquals(points[2], lines[0].actualPoints.last())
        assertEquals(points[2], lines[1].actualPoints.first())
        assertEquals(leadingPoint, lines[1].actualPoints.last())
        overlay.showProgress(1f, points.last())
        assertTrue(lines.all { it.isEnabled })
        assertEquals(points.last(), lines.last().actualPoints.last())
        assertEquals(RerunSpeedBand.STOPPED.color, lines[0].color)
        assertEquals(RerunSpeedBand.VERY_FAST.color, lines[1].color)
    }

    @Test
    fun `short routes and missing metrics are safe`() {
        val point = GeoPoint(48.0, 16.0)
        val single = RerunSpeedOverlay(listOf(point), emptyList())
        single.showProgress(1f, point)
        assertTrue(single.items.isEmpty())
        val noMetrics = RerunSpeedOverlay(listOf(point, GeoPoint(48.1, 16.1)), emptyList())
        noMetrics.showProgress(1f, point)
        assertEquals(RerunSpeedBand.STOPPED.color, (noMetrics.items.single() as Polyline).color)
    }
}
