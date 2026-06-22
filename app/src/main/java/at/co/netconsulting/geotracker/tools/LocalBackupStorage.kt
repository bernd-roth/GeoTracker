package at.co.netconsulting.geotracker.tools

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.OutputStream

object LocalBackupStorage {
    const val ROOT_FOLDER = "GeoTracker"
    const val GPX_BACKUP_FOLDER = ROOT_FOLDER
    const val LEGACY_GPX_BACKUP_FOLDER = "GeoTracker/GPX"
    const val DATABASE_BACKUP_FOLDER = "GeoTracker/DatabaseBackups"

    private const val TAG = "LocalBackupStorage"

    fun displayPath(relativeFolder: String): String = "Downloads/$relativeFolder"

    fun writeTextFile(
        context: Context,
        relativeFolder: String,
        fileName: String,
        mimeType: String,
        content: String
    ): Boolean {
        return writeFile(context, relativeFolder, fileName, mimeType) { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    fun copyFile(
        context: Context,
        relativeFolder: String,
        sourceFile: File,
        fileName: String,
        mimeType: String,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
            return false
        }

        val totalBytes = sourceFile.length()
        return writeFile(context, relativeFolder, fileName, mimeType) { output ->
            sourceFile.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesCopied = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    onProgress?.invoke(bytesCopied, totalBytes)
                }
            }
        }
    }

    fun clearGpxFiles(context: Context, relativeFolder: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            clearGpxFilesScoped(context, relativeFolder)
        } else {
            clearGpxFilesLegacy(relativeFolder)
        }
    }

    private fun writeFile(
        context: Context,
        relativeFolder: String,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeFileScoped(context, relativeFolder, fileName, mimeType, writer)
        } else {
            writeFileLegacy(relativeFolder, fileName, writer)
        }
    }

    private fun writeFileScoped(
        context: Context,
        relativeFolder: String,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Boolean {
        var createdUri: Uri? = null
        return try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, mediaStoreRelativePath(relativeFolder))
            }

            createdUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (createdUri == null) {
                Log.e(TAG, "Could not create $fileName in ${displayPath(relativeFolder)}")
                return false
            }

            val outputStream = resolver.openOutputStream(createdUri, "w")
            if (outputStream == null) {
                resolver.delete(createdUri, null, null)
                Log.e(TAG, "Could not open $fileName in ${displayPath(relativeFolder)}")
                return false
            }

            outputStream.use { output ->
                writer(output)
                output.flush()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not write $fileName to ${displayPath(relativeFolder)}", e)
            createdUri?.let { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (deleteError: Exception) {
                    Log.w(TAG, "Could not delete partial file: $uri", deleteError)
                }
            }
            false
        }
    }

    private fun writeFileLegacy(
        relativeFolder: String,
        fileName: String,
        writer: (OutputStream) -> Unit
    ): Boolean {
        return try {
            val directory = downloadsDirectory(relativeFolder)
            if (!directory.exists() && !directory.mkdirs()) {
                Log.e(TAG, "Could not create ${directory.absolutePath}")
                return false
            }

            val file = File(directory, fileName)
            file.outputStream().use { output ->
                writer(output)
                output.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not write $fileName to ${displayPath(relativeFolder)}", e)
            false
        }
    }

    private fun clearGpxFilesScoped(context: Context, relativeFolder: String): Boolean {
        val expectedRelativePath = mediaStoreRelativePath(relativeFolder).trimEnd('/')
        return try {
            var hadError = false
            val resolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH
            )

            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.gpx"),
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIndex) ?: continue
                    val relativePath = cursor.getString(pathIndex)?.trimEnd('/') ?: continue
                    if (!displayName.endsWith(".gpx", ignoreCase = true) || relativePath != expectedRelativePath) {
                        continue
                    }

                    val uri = ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex)
                    )
                    try {
                        if (resolver.delete(uri, null, null) == 0) {
                            hadError = true
                            Log.w(TAG, "Could not delete GPX file: $displayName")
                        }
                    } catch (e: Exception) {
                        hadError = true
                        Log.w(TAG, "Could not delete GPX file: $displayName", e)
                    }
                }
            }

            !hadError
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear GPX files from ${displayPath(relativeFolder)}", e)
            false
        }
    }

    private fun clearGpxFilesLegacy(relativeFolder: String): Boolean {
        return try {
            val directory = downloadsDirectory(relativeFolder)
            if (!directory.exists()) return true

            var hadError = false
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".gpx", ignoreCase = true) && !file.delete()) {
                    hadError = true
                    Log.w(TAG, "Could not delete GPX file: ${file.absolutePath}")
                }
            }

            !hadError
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear GPX files from ${displayPath(relativeFolder)}", e)
            false
        }
    }

    private fun mediaStoreRelativePath(relativeFolder: String): String {
        return Environment.DIRECTORY_DOWNLOADS + "/" + relativeFolder
    }

    private fun downloadsDirectory(relativeFolder: String): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            relativeFolder
        )
    }
}
