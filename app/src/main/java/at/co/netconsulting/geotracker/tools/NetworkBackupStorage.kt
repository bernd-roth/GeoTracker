package at.co.netconsulting.geotracker.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import java.io.File
import java.io.OutputStream

object NetworkBackupStorage {
    const val KEY_GPX_BACKUP_TREE_URI = "gpxBackupTreeUri"
    const val KEY_DATABASE_BACKUP_TREE_URI = "databaseBackupTreeUri"

    private const val TAG = "NetworkBackupStorage"
    private const val USER_SETTINGS = "UserSettings"

    fun getGpxBackupTreeUri(context: Context): Uri? =
        getTreeUri(context, KEY_GPX_BACKUP_TREE_URI)

    fun getDatabaseBackupTreeUri(context: Context): Uri? =
        getTreeUri(context, KEY_DATABASE_BACKUP_TREE_URI)

    fun saveGpxBackupTreeUri(context: Context, uri: Uri): Boolean =
        saveTreeUri(context, KEY_GPX_BACKUP_TREE_URI, uri)

    fun saveDatabaseBackupTreeUri(context: Context, uri: Uri): Boolean =
        saveTreeUri(context, KEY_DATABASE_BACKUP_TREE_URI, uri)

    fun clearGpxBackupTreeUri(context: Context) {
        clearTreeUri(context, KEY_GPX_BACKUP_TREE_URI)
    }

    fun clearDatabaseBackupTreeUri(context: Context) {
        clearTreeUri(context, KEY_DATABASE_BACKUP_TREE_URI)
    }

    fun getTreeDisplayName(context: Context, treeUri: Uri): String? {
        return try {
            val documentUri = rootDocumentUri(treeUri)
            context.contentResolver.query(
                documentUri,
                arrayOf(Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            } ?: treeUri.lastPathSegment
        } catch (e: Exception) {
            Log.w(TAG, "Could not read display name for tree URI: $treeUri", e)
            treeUri.lastPathSegment
        }
    }

    fun writeTextFileToTree(
        context: Context,
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        content: String
    ): Boolean {
        return writeFileToTree(context, treeUri, fileName, mimeType) { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    fun copyFileToTree(
        context: Context,
        treeUri: Uri,
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
        return writeFileToTree(context, treeUri, fileName, mimeType) { output ->
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

    fun writeFileToTree(
        context: Context,
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit
    ): Boolean {
        return try {
            deleteChildByName(context, treeUri, fileName)

            val createdDocumentUri = DocumentsContract.createDocument(
                context.contentResolver,
                rootDocumentUri(treeUri),
                mimeType,
                fileName
            )

            if (createdDocumentUri == null) {
                Log.e(TAG, "Could not create document $fileName in $treeUri")
                return false
            }

            context.contentResolver.openOutputStream(createdDocumentUri, "w")?.use { output ->
                writer(output)
                output.flush()
            } ?: return false

            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not write $fileName to tree URI: $treeUri", e)
            false
        }
    }

    fun clearGpxFiles(context: Context, treeUri: Uri): Boolean {
        return clearFilesInTree(context, treeUri) { displayName, mimeType ->
            mimeType != Document.MIME_TYPE_DIR && displayName.endsWith(".gpx", ignoreCase = true)
        }
    }

    private fun clearFilesInTree(
        context: Context,
        treeUri: Uri,
        shouldDelete: (displayName: String, mimeType: String?) -> Boolean
    ): Boolean {
        return try {
            var hadError = false
            queryChildren(context, treeUri) { childUri, displayName, mimeType ->
                if (shouldDelete(displayName, mimeType)) {
                    try {
                        if (!DocumentsContract.deleteDocument(context.contentResolver, childUri)) {
                            hadError = true
                            Log.w(TAG, "Provider could not delete $displayName")
                        }
                    } catch (e: Exception) {
                        hadError = true
                        Log.w(TAG, "Could not delete $displayName", e)
                    }
                }
            }
            !hadError
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear GPX files in tree URI: $treeUri", e)
            false
        }
    }

    private fun deleteChildByName(context: Context, treeUri: Uri, fileName: String) {
        queryChildren(context, treeUri) { childUri, displayName, mimeType ->
            if (mimeType != Document.MIME_TYPE_DIR && displayName == fileName) {
                DocumentsContract.deleteDocument(context.contentResolver, childUri)
            }
        }
    }

    private fun queryChildren(
        context: Context,
        treeUri: Uri,
        onChild: (childUri: Uri, displayName: String, mimeType: String?) -> Unit
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex) ?: continue
                val mimeType = cursor.getString(mimeTypeIndex)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                onChild(childUri, displayName, mimeType)
            }
        }
    }

    private fun rootDocumentUri(treeUri: Uri): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
    }

    private fun getTreeUri(context: Context, key: String): Uri? {
        val uriString = context
            .getSharedPreferences(USER_SETTINGS, Context.MODE_PRIVATE)
            .getString(key, null)
            ?: return null

        return Uri.parse(uriString)
    }

    private fun saveTreeUri(context: Context, key: String, uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            context.getSharedPreferences(USER_SETTINGS, Context.MODE_PRIVATE)
                .edit()
                .putString(key, uri.toString())
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not persist tree URI permission: $uri", e)
            false
        }
    }

    private fun clearTreeUri(context: Context, key: String) {
        val uri = getTreeUri(context, key)
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not release persisted URI permission: $uri", e)
            }
        }

        context.getSharedPreferences(USER_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }
}
