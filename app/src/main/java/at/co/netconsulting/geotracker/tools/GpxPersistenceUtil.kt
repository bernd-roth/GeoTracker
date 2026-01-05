package at.co.netconsulting.geotracker.tools

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import at.co.netconsulting.geotracker.data.ImportedGpxTrack
import at.co.netconsulting.geotracker.data.RouteWeatherData

object GpxPersistenceUtil {
    private const val PREF_NAME = "GpxRecordingData"
    private const val KEY_IMPORTED_GPX = "imported_gpx_track"
    private const val KEY_IS_RECORDING = "is_recording_with_gpx"
    private const val KEY_TRACK_ACTIVE = "track_active"
    private const val KEY_TRACK_TIMESTAMP = "track_timestamp" // Add timestamp for change detection
    private const val KEY_WEATHER_OVERLAY = "weather_overlay_data"
    private const val KEY_WEATHER_ACTIVE = "weather_active"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    /**
     * Save imported GPX track - can be called during recording start or anytime
     */
    fun saveImportedGpxTrack(context: Context, gpxTrack: ImportedGpxTrack?) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()

        if (gpxTrack != null) {
            val jsonString = gson.toJson(gpxTrack)
            editor.putString(KEY_IMPORTED_GPX, jsonString)
            editor.putBoolean(KEY_TRACK_ACTIVE, true)
            editor.putLong(KEY_TRACK_TIMESTAMP, System.currentTimeMillis()) // Update timestamp
            android.util.Log.d("GpxPersistence", "Saved track: ${gpxTrack.filename} with ${gpxTrack.points.size} points")
        } else {
            editor.remove(KEY_IMPORTED_GPX)
            editor.putBoolean(KEY_TRACK_ACTIVE, false)
            editor.putLong(KEY_TRACK_TIMESTAMP, System.currentTimeMillis()) // Update timestamp for clearing
            android.util.Log.d("GpxPersistence", "Cleared track data")
        }

        editor.apply()
    }

    /**
     * Load imported GPX track if available
     */
    fun loadImportedGpxTrack(context: Context): ImportedGpxTrack? {
        val prefs = getPrefs(context)
        val isTrackActive = prefs.getBoolean(KEY_TRACK_ACTIVE, false)

        if (!isTrackActive) {
            return null
        }

        val jsonString = prefs.getString(KEY_IMPORTED_GPX, null)
        return if (jsonString != null) {
            try {
                val track = gson.fromJson(jsonString, ImportedGpxTrack::class.java)
                android.util.Log.d("GpxPersistence", "Loaded track: ${track.filename} with ${track.points.size} points")
                track
            } catch (e: Exception) {
                android.util.Log.e("GpxPersistence", "Error parsing GPX track: ${e.message}")
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }

    /**
     * Get the timestamp when track was last modified
     */
    fun getTrackTimestamp(context: Context): Long {
        val prefs = getPrefs(context)
        return prefs.getLong(KEY_TRACK_TIMESTAMP, 0L)
    }

    /**
     * Clear imported GPX track completely
     */
    fun clearImportedGpxTrack(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_IMPORTED_GPX)
            .putBoolean(KEY_TRACK_ACTIVE, false)
            .putLong(KEY_TRACK_TIMESTAMP, System.currentTimeMillis()) // Update timestamp
            .apply()
        android.util.Log.d("GpxPersistence", "Cleared persisted track")
    }

    /**
     * Check if we have a persisted GPX track
     */
    fun hasImportedGpxTrack(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isTrackActive = prefs.getBoolean(KEY_TRACK_ACTIVE, false)
        val hasTrack = prefs.contains(KEY_IMPORTED_GPX)
        return isTrackActive && hasTrack
    }

    /**
     * Set recording state (separate from track persistence)
     */
    fun setRecordingState(context: Context, isRecording: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_IS_RECORDING, isRecording)
            .apply()
    }

    /**
     * Get recording state
     */
    fun isRecordingWithGpx(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_IS_RECORDING, false)
    }

    /**
     * Clear track only when recording stops (if desired)
     */
    fun clearTrackOnRecordingStop(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_IS_RECORDING, false)
            .apply()
        // Note: We don't clear the track here - only the recording state
        // Track persists until explicitly cleared
    }

    /**
     * Get track info without loading full track data
     */
    fun getTrackInfo(context: Context): Pair<String, Int>? {
        val prefs = getPrefs(context)
        val isTrackActive = prefs.getBoolean(KEY_TRACK_ACTIVE, false)

        if (!isTrackActive) return null

        val jsonString = prefs.getString(KEY_IMPORTED_GPX, null)
        return if (jsonString != null) {
            try {
                val track = gson.fromJson(jsonString, ImportedGpxTrack::class.java)
                Pair(track.filename, track.points.size)
            } catch (e: Exception) {
                android.util.Log.e("GpxPersistence", "Error parsing track info: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    // ========== Weather Overlay Persistence ==========

    /**
     * Save weather overlay data for display on map
     */
    fun saveWeatherOverlay(context: Context, weatherData: RouteWeatherData?) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()

        if (weatherData != null) {
            val jsonString = gson.toJson(weatherData)
            editor.putString(KEY_WEATHER_OVERLAY, jsonString)
            editor.putBoolean(KEY_WEATHER_ACTIVE, true)
            android.util.Log.d("GpxPersistence", "Saved weather overlay: ${weatherData.weatherForecasts.size} forecasts")
        } else {
            editor.remove(KEY_WEATHER_OVERLAY)
            editor.putBoolean(KEY_WEATHER_ACTIVE, false)
            android.util.Log.d("GpxPersistence", "Cleared weather overlay data")
        }

        editor.apply()
    }

    /**
     * Load weather overlay data if available
     */
    fun loadWeatherOverlay(context: Context): RouteWeatherData? {
        val prefs = getPrefs(context)
        val isWeatherActive = prefs.getBoolean(KEY_WEATHER_ACTIVE, false)

        if (!isWeatherActive) {
            return null
        }

        val jsonString = prefs.getString(KEY_WEATHER_OVERLAY, null)
        return if (jsonString != null) {
            try {
                val weatherData = gson.fromJson(jsonString, RouteWeatherData::class.java)
                android.util.Log.d("GpxPersistence", "Loaded weather overlay: ${weatherData.weatherForecasts.size} forecasts")
                weatherData
            } catch (e: Exception) {
                android.util.Log.e("GpxPersistence", "Error parsing weather overlay: ${e.message}")
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }

    /**
     * Clear weather overlay data
     */
    fun clearWeatherOverlay(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .remove(KEY_WEATHER_OVERLAY)
            .putBoolean(KEY_WEATHER_ACTIVE, false)
            .apply()
        android.util.Log.d("GpxPersistence", "Cleared weather overlay")
    }

    /**
     * Check if we have weather overlay data
     */
    fun hasWeatherOverlay(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isWeatherActive = prefs.getBoolean(KEY_WEATHER_ACTIVE, false)
        val hasWeather = prefs.contains(KEY_WEATHER_OVERLAY)
        return isWeatherActive && hasWeather
    }
}