package at.co.netconsulting.geotracker.sync

import android.content.Context
import android.util.Log
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Manages synchronization of activities to external platforms
 * (Strava, Garmin Connect, TrainingPeaks)
 */
class ExportSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "ExportSyncManager"
    }

    private val stravaClient = StravaApiClient(context)
    private val garminClient = GarminConnectApiClient(context)
    private val trainingPeaksClient = TrainingPeaksApiClient(context)

    /**
     * Get authentication status for all platforms
     */
    fun getAuthenticationStatus(): Map<SyncPlatform, Boolean> {
        return mapOf(
            SyncPlatform.STRAVA to stravaClient.isAuthenticated(),
            SyncPlatform.GARMIN to garminClient.isAuthenticated(),
            SyncPlatform.TRAINING_PEAKS to trainingPeaksClient.isAuthenticated()
        )
    }

    /**
     * Get API client for a specific platform
     */
    fun getStravaClient() = stravaClient
    fun getGarminClient() = garminClient
    fun getTrainingPeaksClient() = trainingPeaksClient

    /**
     * Disconnect from a specific platform
     */
    fun disconnect(platform: SyncPlatform) {
        when (platform) {
            SyncPlatform.STRAVA -> stravaClient.disconnect()
            SyncPlatform.GARMIN -> garminClient.disconnect()
            SyncPlatform.TRAINING_PEAKS -> trainingPeaksClient.disconnect()
        }
    }

    /**
     * Sync an event to one or more platforms
     *
     * @param eventId The event ID to sync
     * @param platforms List of platforms to sync to
     * @return Map of platform to result
     */
    suspend fun syncEvent(
        eventId: Int,
        platforms: List<SyncPlatform>
    ): Map<SyncPlatform, SyncResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<SyncPlatform, SyncResult>()

        try {
            // Load event data
            val database = FitnessTrackerDatabase.getInstance(context)
            val event = database.eventDao().getEventById(eventId)
                ?: return@withContext mapOf<SyncPlatform, SyncResult>().also {
                    platforms.forEach { platform ->
                        results[platform] = SyncResult.Failure("Event not found")
                    }
                }

            val locations = database.locationDao().getLocationsByEventId(eventId)
            if (locations.isEmpty()) {
                platforms.forEach { platform ->
                    results[platform] = SyncResult.Failure("No location data to sync")
                }
                return@withContext results
            }

            // Generate GPX file
            val gpxFile = generateGpxFile(eventId)
            if (gpxFile == null || !gpxFile.exists()) {
                platforms.forEach { platform ->
                    results[platform] = SyncResult.Failure("Failed to generate GPX file")
                }
                return@withContext results
            }

            // Upload to each platform
            for (platform in platforms) {
                results[platform] = when (platform) {
                    SyncPlatform.STRAVA -> syncToStrava(event.eventName, event.artOfSport, gpxFile)
                    SyncPlatform.GARMIN -> syncToGarmin(event.eventName, event.artOfSport, gpxFile)
                    SyncPlatform.TRAINING_PEAKS -> syncToTrainingPeaks(event.eventName, gpxFile)
                }
            }

            // Clean up temporary GPX file
            gpxFile.delete()

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing event", e)
            platforms.forEach { platform ->
                if (!results.containsKey(platform)) {
                    results[platform] = SyncResult.Failure("Sync error: ${e.message}")
                }
            }
        }

        results
    }

    /**
     * Generate GPX file for an event
     */
    private suspend fun generateGpxFile(eventId: Int): File? = withContext(Dispatchers.IO) {
        try {
            val database = FitnessTrackerDatabase.getInstance(context)
            val event = database.eventDao().getEventById(eventId) ?: return@withContext null
            val locations = database.locationDao().getLocationsByEventId(eventId)
            val metrics = database.metricDao().getMetricsByEventId(eventId)

            if (locations.isEmpty()) return@withContext null

            // Map sport type to GPX activity type
            val activityType = when (event.artOfSport.lowercase()) {
                "running", "jogging", "marathon", "trail running", "ultramarathon", "road running" -> "run"
                "cycling", "bicycle", "bike", "biking", "gravel bike", "racing bicycle", "mountain bike" -> "bike"
                "hiking", "walking", "trekking", "mountain hiking", "forest hiking" -> "hike"
                "swimming - open water", "swimming - pool" -> "swim"
                else -> event.artOfSport.lowercase().replace(" ", "_")
            }

            // Create GPX content
            val gpxBuilder = StringBuilder()
            gpxBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
                |<gpx version="1.1"
                |    creator="GeoTracker"
                |    xmlns="http://www.topografix.com/GPX/1/1"
                |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                |    xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
                |    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd
                |                        http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd">
                |  <metadata>
                |    <name>${event.eventName}</name>
                |    <time>${event.eventDate}T00:00:00Z</time>
                |  </metadata>
                |  <trk>
                |    <name>${event.eventName}</name>
                |    <type>${activityType}</type>
                |    <trkseg>
                """.trimMargin())

            locations.forEachIndexed { index, location ->
                val metric = metrics.getOrNull(index)

                if (metric != null && metric.timeInMilliseconds > 0) {
                    val timestamp = Instant.ofEpochMilli(metric.timeInMilliseconds)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                    gpxBuilder.append("""
                        |      <trkpt lat="${location.latitude}" lon="${location.longitude}">
                        |        <ele>${location.altitude}</ele>
                        |        <time>${timestamp}</time>
                        """.trimMargin())

                    // Add heart rate if available
                    if (metric.heartRate > 0) {
                        gpxBuilder.append("""
                            |        <extensions>
                            |          <gpxtpx:TrackPointExtension>
                            |            <gpxtpx:hr>${metric.heartRate}</gpxtpx:hr>
                            |          </gpxtpx:TrackPointExtension>
                            |        </extensions>
                            """.trimMargin())
                    }

                    gpxBuilder.append("""
                        |      </trkpt>
                        """.trimMargin())
                }
            }

            gpxBuilder.append("""
                |    </trkseg>
                |  </trk>
                |</gpx>
                """.trimMargin())

            // Save to temporary file
            val filename = "${event.eventName}_${event.eventDate}.gpx"
                .replace(" ", "_")
                .replace(":", "-")
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_")

            val tempFile = File(context.cacheDir, filename)
            tempFile.writeText(gpxBuilder.toString())

            // DEBUG: Also save to external storage for inspection
            try {
                val debugFile = File(context.getExternalFilesDir(null), "debug_sync_$filename")
                debugFile.writeText(gpxBuilder.toString())
                Log.d(TAG, "DEBUG: Saved sync GPX to: ${debugFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save debug GPX", e)
            }

            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error generating GPX file", e)
            null
        }
    }

    /**
     * Sync to Strava
     */
    private suspend fun syncToStrava(
        eventName: String,
        activityType: String,
        gpxFile: File
    ): SyncResult {
        if (!stravaClient.isAuthenticated()) {
            return SyncResult.Failure("Not authenticated with Strava")
        }

        val result = stravaClient.uploadActivity(gpxFile, eventName, activityType)
        return if (result.isSuccess) {
            SyncResult.Success(result.getOrNull() ?: "uploaded")
        } else {
            SyncResult.Failure(result.exceptionOrNull()?.message ?: "Upload failed")
        }
    }

    /**
     * Sync to Garmin Connect
     */
    private suspend fun syncToGarmin(
        eventName: String,
        activityType: String,
        gpxFile: File
    ): SyncResult {
        if (!garminClient.isAuthenticated()) {
            return SyncResult.Failure("Not authenticated with Garmin Connect")
        }

        val result = garminClient.uploadActivity(gpxFile, eventName, activityType)
        return if (result.isSuccess) {
            SyncResult.Success(result.getOrNull() ?: "uploaded")
        } else {
            SyncResult.Failure(result.exceptionOrNull()?.message ?: "Upload failed")
        }
    }

    /**
     * Sync to TrainingPeaks
     */
    private suspend fun syncToTrainingPeaks(
        eventName: String,
        gpxFile: File
    ): SyncResult {
        if (!trainingPeaksClient.isAuthenticated()) {
            return SyncResult.Failure("Not authenticated with TrainingPeaks")
        }

        val result = trainingPeaksClient.uploadActivity(gpxFile, eventName)
        return if (result.isSuccess) {
            SyncResult.Success(result.getOrNull() ?: "uploaded")
        } else {
            SyncResult.Failure(result.exceptionOrNull()?.message ?: "Upload failed")
        }
    }
}

/**
 * Supported sync platforms
 */
enum class SyncPlatform {
    STRAVA,
    GARMIN,
    TRAINING_PEAKS
}

/**
 * Result of a sync operation
 */
sealed class SyncResult {
    data class Success(val activityId: String) : SyncResult()
    data class Failure(val error: String) : SyncResult()
}
