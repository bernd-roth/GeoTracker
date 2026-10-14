package at.co.netconsulting.geotracker.composables

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import at.co.netconsulting.geotracker.domain.EventMedia
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.tools.MediaLocation
import at.co.netconsulting.geotracker.tools.MediaLocationExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@Composable
internal fun routeMedia(database: FitnessTrackerDatabase, eventId: Int?): List<EventMedia> {
    var media by remember(eventId) { mutableStateOf<List<EventMedia>>(emptyList()) }
    LaunchedEffect(database, eventId) {
        if (eventId == null) return@LaunchedEffect
        // Recover coordinates from attachments saved before location support was added.
        withContext(Dispatchers.IO) {
            database.eventMediaDao().getMediaForEvent(eventId).forEach { item ->
                if (MediaLocation.from(item.latitude, item.longitude) == null) {
                    val location = item.localFilePath?.let { MediaLocationExtractor.extract(File(it), item.mediaType) }
                    if (location != null) database.eventMediaDao().updateLocation(item.mediaId, location.latitude, location.longitude)
                }
            }
        }
        database.eventMediaDao().getMediaForEventFlow(eventId).collect { items ->
            media = items.filter { MediaLocation.from(it.latitude, it.longitude) != null }
        }
    }
    return media
}

@Composable
internal fun RouteMediaToggle(visible: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    FilledIconToggleButton(checked = visible, onCheckedChange = onToggle, modifier = modifier) {
        Icon(Icons.Default.PhotoLibrary, contentDescription = if (visible) "Hide route media" else "Show route media")
    }
}

@Composable
internal fun RouteMediaViewer(media: EventMedia?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    if (media != null) MediaViewerDialog(
        media = media,
        serverBaseUrl = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            .getString("websocketserver", "") ?: "",
        onDismiss = onDismiss
    )
}

internal fun addRouteMediaMarkers(map: MapView, media: List<EventMedia>, onClick: (EventMedia) -> Unit): List<Marker> {
    val size = (36 * map.resources.displayMetrics.density).toInt().coerceAtLeast(36)
    val icons = media.map { it.mediaType }.distinct().associateWith { type ->
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = android.graphics.Color.rgb(103, 58, 183)
        canvas.drawCircle(size / 2f, size / 2f, size * .44f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * .5f
        val label = if (type == "video") "\u25b6" else "\u25a3"
        canvas.drawText(label, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        BitmapDrawable(map.resources, bitmap)
    }
    return media.mapNotNull { item ->
        val location = MediaLocation.from(item.latitude, item.longitude) ?: return@mapNotNull null
        Marker(map).apply {
            position = GeoPoint(location.latitude, location.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = icons[item.mediaType]
            title = item.caption ?: if (item.mediaType == "video") "Video" else "Photo"
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ -> onClick(item); true }
            map.overlays.add(this)
        }
    }
}
