package at.co.netconsulting.geotracker.tools

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

/** Missing or invalid metadata never becomes a marker at a guessed location. */
data class MediaLocation(val latitude: Double, val longitude: Double) {
    companion object {
        fun from(latitude: Double?, longitude: Double?): MediaLocation? =
            if (latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
            ) MediaLocation(latitude, longitude) else null

        fun parseVideoLocation(value: String?): MediaLocation? {
            val match = Regex("^([+-]\\d{2}(?:\\.\\d+)?)([+-]\\d{3}(?:\\.\\d+)?)(?:[+-]\\d+(?:\\.\\d+)?)?/?$")
                .matchEntire(value?.trim() ?: return null) ?: return null
            return from(match.groupValues[1].toDoubleOrNull(), match.groupValues[2].toDoubleOrNull())
        }
    }
}

object MediaLocationExtractor {
    // Document providers need not support requesting the original. Fall back to the selected file.
    fun openOriginal(context: Context, uri: Uri): InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val mediaUri = MediaStore.getMediaUri(context, uri) ?: uri
                context.contentResolver.openInputStream(MediaStore.setRequireOriginal(mediaUri))?.let { return it }
            } catch (_: Exception) { }
        }
        return context.contentResolver.openInputStream(uri)
    }

    fun extract(file: File, mediaType: String): MediaLocation? = try {
        if (mediaType == "video") {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                MediaLocation.parseVideoLocation(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            } finally {
                retriever.release()
            }
        } else {
            val coordinates = ExifInterface(file).latLong
            MediaLocation.from(coordinates?.get(0), coordinates?.get(1))
        }
    } catch (_: Exception) {
        null
    }
}
