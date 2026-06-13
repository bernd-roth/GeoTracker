package at.co.netconsulting.geotracker.tools

import android.content.Context
import android.util.Log
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Properties

object SmbBackupStorage {
    const val KEY_GPX_SMB_URL = "gpxSmbUrl"
    const val KEY_GPX_SMB_USERNAME = "gpxSmbUsername"
    const val KEY_GPX_SMB_PASSWORD = "gpxSmbPassword"
    const val KEY_GPX_SMB_DOMAIN = "gpxSmbDomain"

    const val KEY_DATABASE_SMB_URL = "databaseSmbUrl"
    const val KEY_DATABASE_SMB_USERNAME = "databaseSmbUsername"
    const val KEY_DATABASE_SMB_PASSWORD = "databaseSmbPassword"
    const val KEY_DATABASE_SMB_DOMAIN = "databaseSmbDomain"

    private const val TAG = "SmbBackupStorage"
    private const val USER_SETTINGS = "UserSettings"

    data class Destination(
        val url: String,
        val username: String,
        val password: String,
        val domain: String
    ) {
        val isConfigured: Boolean
            get() = url.isNotBlank()
    }

    fun getGpxDestination(context: Context): Destination =
        getDestination(
            context,
            KEY_GPX_SMB_URL,
            KEY_GPX_SMB_USERNAME,
            KEY_GPX_SMB_PASSWORD,
            KEY_GPX_SMB_DOMAIN
        )

    fun getDatabaseDestination(context: Context): Destination =
        getDestination(
            context,
            KEY_DATABASE_SMB_URL,
            KEY_DATABASE_SMB_USERNAME,
            KEY_DATABASE_SMB_PASSWORD,
            KEY_DATABASE_SMB_DOMAIN
        )

    fun saveGpxDestination(context: Context, destination: Destination) {
        saveDestination(
            context,
            destination,
            KEY_GPX_SMB_URL,
            KEY_GPX_SMB_USERNAME,
            KEY_GPX_SMB_PASSWORD,
            KEY_GPX_SMB_DOMAIN
        )
    }

    fun saveDatabaseDestination(context: Context, destination: Destination) {
        saveDestination(
            context,
            destination,
            KEY_DATABASE_SMB_URL,
            KEY_DATABASE_SMB_USERNAME,
            KEY_DATABASE_SMB_PASSWORD,
            KEY_DATABASE_SMB_DOMAIN
        )
    }

    fun clearGpxDestination(context: Context) {
        clearDestination(
            context,
            KEY_GPX_SMB_URL,
            KEY_GPX_SMB_USERNAME,
            KEY_GPX_SMB_PASSWORD,
            KEY_GPX_SMB_DOMAIN
        )
    }

    fun clearDatabaseDestination(context: Context) {
        clearDestination(
            context,
            KEY_DATABASE_SMB_URL,
            KEY_DATABASE_SMB_USERNAME,
            KEY_DATABASE_SMB_PASSWORD,
            KEY_DATABASE_SMB_DOMAIN
        )
    }

    fun writeTextFile(
        destination: Destination,
        fileName: String,
        content: String
    ): Boolean {
        if (!destination.isConfigured) return false

        return writeFile(destination, fileName) { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    fun copyFile(
        destination: Destination,
        sourceFile: File,
        fileName: String,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (!destination.isConfigured) return false
        if (!sourceFile.exists() || !sourceFile.isFile) {
            Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
            return false
        }

        val totalBytes = sourceFile.length()
        return writeFile(destination, fileName) { output ->
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

    fun clearGpxFiles(destination: Destination): Boolean {
        if (!destination.isConfigured) return false

        return try {
            val context = createContext(destination)
            val folder = openFolder(destination, context, createIfMissing = false)
            if (!folder.exists()) return true

            folder.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".gpx", ignoreCase = true)) {
                    file.delete()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not clear GPX files from SMB destination: ${destination.url}", e)
            false
        }
    }

    fun testDestination(destination: Destination): Boolean {
        if (!destination.isConfigured) return false

        val testFileName = ".geotracker_smb_test_${System.currentTimeMillis()}.tmp"
        return try {
            val context = createContext(destination)
            val folder = openFolder(destination, context, createIfMissing = true)
            val testFile = SmbFile(folder, testFileName)

            SmbFileOutputStream(testFile).use { output ->
                output.write("GeoTracker SMB test".toByteArray(Charsets.UTF_8))
            }

            if (testFile.exists()) {
                testFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMB destination test failed: ${destination.url}", e)
            false
        }
    }

    private fun writeFile(
        destination: Destination,
        fileName: String,
        writer: (OutputStream) -> Unit
    ): Boolean {
        return try {
            val context = createContext(destination)
            val folder = openFolder(destination, context, createIfMissing = true)
            val file = SmbFile(folder, fileName)

            if (file.exists()) {
                file.delete()
            }

            SmbFileOutputStream(file).use { output ->
                writer(output)
                output.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not write $fileName to SMB destination: ${destination.url}", e)
            false
        }
    }

    private fun openFolder(
        destination: Destination,
        context: CIFSContext,
        createIfMissing: Boolean
    ): SmbFile {
        val folder = SmbFile(normalizeFolderUrl(destination.url), context)
        if (createIfMissing && !folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    private fun createContext(destination: Destination): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "30000")
        }
        val baseContext = BaseContext(PropertyConfiguration(properties))
        val authenticator = NtlmPasswordAuthenticator(
            destination.domain.trim(),
            destination.username.trim(),
            destination.password
        )
        return baseContext.withCredentials(authenticator)
    }

    private fun normalizeFolderUrl(rawUrl: String): String {
        var url = rawUrl.trim().replace('\\', '/')
        if (url.startsWith("//")) {
            url = "smb:$url"
        } else if (!url.startsWith("smb://", ignoreCase = true)) {
            url = "smb://$url"
        }

        return if (url.endsWith("/")) url else "$url/"
    }

    private fun getDestination(
        context: Context,
        urlKey: String,
        usernameKey: String,
        passwordKey: String,
        domainKey: String
    ): Destination {
        val prefs = context.getSharedPreferences(USER_SETTINGS, Context.MODE_PRIVATE)
        return Destination(
            url = prefs.getString(urlKey, "") ?: "",
            username = prefs.getString(usernameKey, "") ?: "",
            password = prefs.getString(passwordKey, "") ?: "",
            domain = prefs.getString(domainKey, "") ?: ""
        )
    }

    private fun saveDestination(
        context: Context,
        destination: Destination,
        urlKey: String,
        usernameKey: String,
        passwordKey: String,
        domainKey: String
    ) {
        context.getSharedPreferences(USER_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putString(urlKey, destination.url.trim())
            .putString(usernameKey, destination.username.trim())
            .putString(passwordKey, destination.password)
            .putString(domainKey, destination.domain.trim())
            .apply()
    }

    private fun clearDestination(
        context: Context,
        urlKey: String,
        usernameKey: String,
        passwordKey: String,
        domainKey: String
    ) {
        context.getSharedPreferences(USER_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .remove(urlKey)
            .remove(usernameKey)
            .remove(passwordKey)
            .remove(domainKey)
            .apply()
    }
}
