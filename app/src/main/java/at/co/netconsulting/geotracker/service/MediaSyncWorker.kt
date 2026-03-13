package at.co.netconsulting.geotracker.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Background worker that retries uploading any media items still marked as pending
 * (isUploaded = false) for events that have already been synced to the server.
 *
 * Returns Result.retry() if any upload fails so WorkManager applies exponential
 * back-off and tries again automatically once the device is back online.
 */
class MediaSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "MediaSyncWorker"
        private const val PERIODIC_WORK_NAME = "media_sync_periodic"
        private const val ONE_TIME_WORK_NAME = "media_sync_one_time"

        /** Schedule a periodic sync (every 15 minutes, only on network). */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<MediaSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Periodic media sync scheduled")
        }

        /**
         * Trigger an immediate one-time sync (e.g. right after a failed upload).
         * Will run as soon as the device has a network connection.
         */
        fun triggerOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            // REPLACE so rapid failures don't stack multiple one-time jobs
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "One-time media sync triggered")
        }
    }

    override suspend fun doWork(): Result {
        val database = FitnessTrackerDatabase.getInstance(applicationContext)
        val apiClient = GeoTrackerApiClient(applicationContext)

        val pendingMedia = database.eventMediaDao().getPendingUploads()
        if (pendingMedia.isEmpty()) {
            Log.d(TAG, "No pending media found")
            return Result.success()
        }

        Log.d(TAG, "Found ${pendingMedia.size} pending media item(s), starting sync")

        var anyFailed = false

        for (media in pendingMedia) {
            // Only upload media whose parent event has already been synced
            val event = database.eventDao().getEventById(media.eventId)
            val sessionId = event?.sessionId
            if (sessionId.isNullOrBlank()) {
                Log.d(TAG, "Skipping media ${media.mediaUuid} — event ${media.eventId} not yet uploaded")
                continue
            }

            val localFile = media.localFilePath?.let { File(it) }
            if (localFile == null || !localFile.exists()) {
                Log.w(TAG, "Local file missing for media ${media.mediaUuid}, skipping")
                continue
            }

            val result = apiClient.uploadMedia(sessionId, localFile, media.mediaType)
            result.onSuccess { uploadResult ->
                database.eventMediaDao().updateMediaUploadStatus(
                    media.mediaId,
                    true,
                    uploadResult.thumbnailUrl,
                    uploadResult.fullUrl
                )
                database.eventMediaDao().updateMedia(
                    media.copy(mediaUuid = uploadResult.mediaUuid, isUploaded = true)
                )
                Log.d(TAG, "Synced pending media: ${uploadResult.mediaUuid}")
            }.onFailure { error ->
                Log.e(TAG, "Failed to upload media ${media.mediaUuid}: ${error.message}")
                anyFailed = true
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }
}
