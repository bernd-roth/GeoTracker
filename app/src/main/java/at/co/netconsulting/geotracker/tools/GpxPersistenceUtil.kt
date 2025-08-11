package at.co.netconsulting.geotracker.tools

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import at.co.netconsulting.geotracker.data.ImportedGpxTrack

object GpxPersistenceUtil {
    private const val PREF_NAME = "GpxRecordingData"
    private const val KEY_IMPORTED_GPX = "imported_gpx_track"
    private const val KEY_IS_RECORDING = "is_recording_with_gpx"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    /**
     * Save imported GPX track when recording starts
     */
    fun saveImportedGpxTrack(context: Context, gpxTrack: ImportedGpxTrack?) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()

        if (gpxTrack != null) {
            val jsonString = gson.toJson(gpxTrack)
            editor.putString(KEY_IMPORTED_GPX, jsonString)
            editor.putBoolean(KEY_IS_RECORDING, true)
        } else {
            editor.remove(KEY_IMPORTED_GPX)
            editor.putBoolean(KEY_IS_RECORDING, false)
        }

        editor.apply()
    }

    /**
     * Load imported GPX track (returns null if not recording or no track)
     */
    fun loadImportedGpxTrack(context: Context): ImportedGpxTrack? {
        val prefs = getPrefs(context)
        val isRecording = prefs.getBoolean(KEY_IS_RECORDING, false)

        if (!isRecording) {
            return null
        }

        val jsonString = prefs.getString(KEY_IMPORTED_GPX, null)
        return if (jsonString != null) {
            try {
                gson.fromJson(jsonString, ImportedGpxTrack::class.java)
            } catch (e: Exception) {
                android.util.Log.e("GpxPersistence", "Error parsing GPX track: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    /**
     * Clear imported GPX track when recording stops
     */
    fun clearImportedGpxTrack(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_IMPORTED_GPX)
            .putBoolean(KEY_IS_RECORDING, false)
            .apply()
    }

    /**
     * Check if we have a persisted GPX track
     */
    fun hasImportedGpxTrack(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isRecording = prefs.getBoolean(KEY_IS_RECORDING, false)
        val hasTrack = prefs.contains(KEY_IMPORTED_GPX)
        return isRecording && hasTrack
    }
}