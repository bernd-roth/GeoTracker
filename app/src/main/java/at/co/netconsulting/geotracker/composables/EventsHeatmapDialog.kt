package at.co.netconsulting.geotracker.composables

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.ColorUtils
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.repository.HeatmapCoordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln1p
import kotlin.math.roundToInt

@Composable
internal fun EventsHeatmapDialog(database: FitnessTrackerDatabase, onDismiss: () -> Unit) {
    val heatmap by produceState<Result<HeatmapData?>?>(null, database) {
        value = try {
            withContext(Dispatchers.Default) {
                val coordinates = database.locationDao().getRecordedHeatmapCoordinates()
                Result.success(coordinates.takeIf { it.isNotEmpty() }?.let(::HeatmapData))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("EventsHeatmap", "Could not load recorded routes", e)
            Result.failure(e)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text(
                    stringResource(R.string.events_heatmap_title),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                val result = heatmap
                val data = result?.getOrNull()
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        result == null -> CircularProgressIndicator()
                        result.isFailure -> Text(
                            stringResource(R.string.events_heatmap_load_failed),
                            modifier = Modifier.padding(16.dp)
                        )
                        data == null -> Text(
                            stringResource(R.string.events_heatmap_no_locations),
                            modifier = Modifier.padding(16.dp)
                        )
                        else -> AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                val density = context.resources.displayMetrics.density
                                MapView(context).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    isVerticalMapRepetitionEnabled = false
                                    val cellSize = (5 * density).roundToInt().coerceAtLeast(1)
                                    overlays.add(HeatmapOverlay(data, cellSize))
                                    overlays.add(CopyrightOverlay(context))
                                    addOnFirstLayoutListener { _, _, _, _, _ ->
                                        val padding = minOf((32 * density).roundToInt(), width / 4, height / 4)
                                        zoomToBoundingBox(data.bounds, false, padding, 16.0, null)
                                    }
                                }
                            }
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.events_heatmap_return)
                        )
                    }
                }
                if (data != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(5.dp)
                                .background(Brush.horizontalGradient(heatmapColors.map { Color(it) }))
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.events_heatmap_lower_density),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                stringResource(R.string.events_heatmap_higher_density),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private class HeatmapData(coordinates: List<HeatmapCoordinate>) {
    private val tileSystem = MapView.getTileSystem()
    val x = DoubleArray(coordinates.size)
    val y = DoubleArray(coordinates.size)
    val bounds: BoundingBox

    init {
        var north = tileSystem.minLatitude
        var south = tileSystem.maxLatitude
        // The DAO sorts by longitude. Exclude the largest gap to fit routes across the date line.
        var largestGap = coordinates.first().longitude + 360.0 - coordinates.last().longitude
        var west = coordinates.first().longitude
        var east = coordinates.last().longitude
        coordinates.forEachIndexed { index, point ->
            val latitude = point.latitude.coerceIn(tileSystem.minLatitude, tileSystem.maxLatitude)
            north = maxOf(north, latitude)
            south = minOf(south, latitude)
            x[index] = tileSystem.getX01FromLongitude(point.longitude)
            y[index] = tileSystem.getY01FromLatitude(latitude)
            if (index > 0) {
                val previous = coordinates[index - 1].longitude
                val gap = point.longitude - previous
                if (gap > largestGap) {
                    largestGap = gap
                    west = point.longitude
                    east = previous
                }
            }
        }
        bounds = BoundingBox(north, east, south, west)
    }

    fun firstPointAtOrAfter(worldX: Double): Int {
        var low = 0
        var high = x.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (x[middle] < worldX) low = middle + 1 else high = middle
        }
        return low
    }
}

private val heatmapColors = listOf(
    AndroidColor.rgb(36, 75, 255),
    AndroidColor.rgb(0, 205, 220),
    AndroidColor.rgb(255, 211, 0),
    AndroidColor.rgb(238, 35, 31)
)

private class HeatmapOverlay(private val data: HeatmapData, private val cellSize: Int) : Overlay() {
    private val kernel = floatArrayOf(0.05f, 0.3f, 0.75f, 1f, 0.75f, 0.3f, 0.05f)
    private val radius = kernel.size / 2
    private val maxLogDensity = ln1p(100f)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var frame: HeatmapFrame? = null

    override fun draw(canvas: Canvas, projection: Projection) {
        val screen = projection.screenRect
        if (screen.isEmpty) return
        val width = ceil(screen.width().toDouble() / cellSize).toInt() + 2 * radius
        val height = ceil(screen.height().toDouble() / cellSize).toInt() + 2 * radius
        val currentFrame = frame?.takeIf { it.bitmap.width == width && it.bitmap.height == height }
            ?: HeatmapFrame(width, height).also { frame = it }
        val left = screen.left - radius * cellSize
        val top = screen.top - radius * cellSize
        val worldSize = projection.worldMapSize
        val worldLeft = (left - projection.offsetX) / worldSize
        val worldTop = (top - projection.offsetY) / worldSize
        val cellInWorld = cellSize / worldSize
        if (currentFrame.worldLeft != worldLeft || currentFrame.worldTop != worldTop ||
            currentFrame.cellInWorld != cellInWorld
        ) {
            currentFrame.worldLeft = worldLeft
            currentFrame.worldTop = worldTop
            currentFrame.cellInWorld = cellInWorld
            render(currentFrame, width, height)
        }
        currentFrame.destination.set(
            left.toFloat(), top.toFloat(),
            (left + width * cellSize).toFloat(), (top + height * cellSize).toFloat()
        )
        canvas.drawBitmap(currentFrame.bitmap, null, currentFrame.destination, bitmapPaint)
    }

    private fun render(frame: HeatmapFrame, width: Int, height: Int) {
        val worldRight = frame.worldLeft + width * frame.cellInWorld
        val worldBottom = frame.worldTop + height * frame.cellInWorld
        frame.counts.fill(0f)
        frame.density.fill(0f)
        // Repeat horizontally with the map, including around the international date line.
        for (world in ceil(frame.worldLeft - 1).toInt()..floor(worldRight).toInt()) {
            val left = frame.worldLeft - world
            val right = worldRight - world
            var point = data.firstPointAtOrAfter(left)
            while (point < data.x.size && data.x[point] < right) {
                val y = data.y[point]
                if (y >= frame.worldTop && y < worldBottom) {
                    val row = ((y - frame.worldTop) / frame.cellInWorld).toInt().coerceIn(0, height - 1)
                    val column = ((data.x[point] - left) / frame.cellInWorld).toInt().coerceIn(0, width - 1)
                    frame.counts[row * width + column]++
                }
                point++
            }
        }
        // Spread each populated cell into its neighbors before assigning colors to summed density.
        frame.counts.forEachIndexed { index, count ->
            if (count > 0f) {
                val row = index / width
                val column = index % width
                for (y in maxOf(0, row - radius)..minOf(height - 1, row + radius)) {
                    val weightedCount = count * kernel[y - row + radius]
                    for (x in maxOf(0, column - radius)..minOf(width - 1, column + radius)) {
                        frame.density[y * width + x] += weightedCount * kernel[x - column + radius]
                    }
                }
            }
        }
        frame.density.forEachIndexed { index, density ->
            frame.pixels[index] = if (density == 0f) AndroidColor.TRANSPARENT else colorForDensity(density)
        }
        frame.bitmap.setPixels(frame.pixels, 0, width, 0, 0, width, height)
    }

    private fun colorForDensity(density: Float): Int {
        val colorPosition = (ln1p(density) / maxLogDensity).coerceAtMost(1f) * heatmapColors.lastIndex
        val stop = colorPosition.toInt().coerceAtMost(heatmapColors.lastIndex - 1)
        val color = ColorUtils.blendARGB(heatmapColors[stop], heatmapColors[stop + 1], colorPosition - stop)
        val opacity = (density / (density + 0.35f) * 230).roundToInt()
        return ColorUtils.setAlphaComponent(color, opacity)
    }
}

private class HeatmapFrame(width: Int, height: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val destination = RectF()
    val counts = FloatArray(width * height)
    val density = FloatArray(width * height)
    val pixels = IntArray(width * height)
    var worldLeft = Double.NaN
    var worldTop = Double.NaN
    var cellInWorld = Double.NaN
}
