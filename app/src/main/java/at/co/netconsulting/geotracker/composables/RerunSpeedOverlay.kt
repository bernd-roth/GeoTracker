package at.co.netconsulting.geotracker.composables

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

// Same palette as Slope Analysis, ordered from slow (dark blue) to fast (red).
internal enum class RerunSpeedBand(val maxKmh: Float, val color: Int, val label: String) {
    STOPPED(2f, 0xFF000080.toInt(), "0-2"),
    WALKING(5f, 0xFF0000FF.toInt(), "2-5"),
    JOGGING(8f, 0xFF87CEEB.toInt(), "5-8"),
    RUNNING(12f, 0xFF008B00.toInt(), "8-12"),
    FAST(16f, 0xFFFFFF00.toInt(), "12-16"),
    SPRINT(20f, 0xFFFF6600.toInt(), "16-20"),
    VERY_FAST(Float.POSITIVE_INFINITY, 0xFFFF0000.toInt(), ">20");

    companion object {
        fun forSpeed(speedKmh: Float): RerunSpeedBand {
            val speed = speedKmh.takeIf { it.isFinite() } ?: 0f
            return entries.first { speed <= it.maxKmh }
        }
    }
}

internal fun rerunMetricIndex(pointIndex: Int, pointCount: Int, metricCount: Int): Int? {
    if (metricCount == 0 || pointCount == 0) return null
    // Recordings normally save one location per metric; some include an initial location.
    return when {
        pointCount == metricCount -> pointIndex
        pointCount == metricCount + 1 -> pointIndex - 1
        pointCount == 1 -> 0
        else -> (pointIndex.toDouble() * (metricCount - 1) / (pointCount - 1)).roundToInt()
    }.coerceIn(0, metricCount - 1)
}

/** Reuses colour segments while revealing the route, including the interpolated leading edge. */
internal class RerunSpeedOverlay(
    private val points: List<GeoPoint>,
    speedsKmh: List<Float>
) : FolderOverlay() {
    private data class Segment(val start: Int, val end: Int, val line: Polyline)
    private val segments = mutableListOf<Segment>()
    private var partialSegment: Segment? = null

    init {
        var start = 0
        var band: RerunSpeedBand? = null
        for (end in 1 until points.size) {
            val metricIndex = rerunMetricIndex(end, points.size, speedsKmh.size)
            val nextBand = RerunSpeedBand.forSpeed(metricIndex?.let(speedsKmh::get) ?: 0f)
            if (band != null && nextBand != band) {
                addSegment(start, end - 1, band)
                start = end - 1
            }
            band = nextBand
        }
        if (band != null) addSegment(start, points.lastIndex, band)
    }

    private fun addSegment(start: Int, end: Int, band: RerunSpeedBand) {
        val line = Polyline().apply {
            setPoints(this@RerunSpeedOverlay.points.subList(start, end + 1))
            color = band.color
            width = 8f
            isEnabled = false
        }
        segments.add(Segment(start, end, line))
        add(line)
    }

    fun showProgress(progress: Float, position: GeoPoint) {
        partialSegment?.let { it.line.setPoints(points.subList(it.start, it.end + 1)) }
        partialSegment = null
        val scaledIndex = progress.coerceIn(0f, 1f) * points.lastIndex
        val currentIndex = scaledIndex.toInt()
        for (segment in segments) {
            segment.line.isEnabled = segment.start < scaledIndex
            if (segment.start < scaledIndex && segment.end > scaledIndex) {
                val visiblePoints = points.subList(segment.start, currentIndex + 1).toMutableList()
                if (scaledIndex > currentIndex) visiblePoints.add(position)
                segment.line.setPoints(visiblePoints)
                partialSegment = segment
            }
        }
    }
}
