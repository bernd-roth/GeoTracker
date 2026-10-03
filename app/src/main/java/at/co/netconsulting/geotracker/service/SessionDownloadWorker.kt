package at.co.netconsulting.geotracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import at.co.netconsulting.geotracker.MainActivity
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.repository.RemoteSessionImporter
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A durable, network-constrained batch in the background download queue. */
class SessionDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val ids = runCatching { JSONArray(inputData.getString(KEY_SESSION_IDS).orEmpty()) }
            .getOrNull() ?: return Result.failure()
        if (ids.length() == 0) return Result.success()

        val batchNumber = inputData.getInt(KEY_BATCH_NUMBER, 1)
        val batchCount = inputData.getInt(KEY_BATCH_COUNT, 1)
        val api = GeoTrackerApiClient(applicationContext)
        val database = FitnessTrackerDatabase.getInstance(applicationContext)
        val importer = RemoteSessionImporter(
            database,
            applicationContext.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        )
        val completed = JSONArray()
        val failed = JSONObject()

        setForeground(createForegroundInfo(batchNumber, batchCount, 0, ids.length()))
        for (index in 0 until ids.length()) {
            if (isStopped) return Result.failure()
            val sessionId = ids.optString(index)
            if (sessionId.isBlank()) continue
            setProgress(progressData(ids, completed, failed, sessionId, batchNumber, batchCount, index))

            val downloaded = api.downloadSessionWithDetails(sessionId)
            downloaded.fold(
                onSuccess = { session ->
                    try {
                        importer.importSession(session)
                        completed.put(sessionId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (e is IOException) return Result.retry()
                        failed.put(sessionId, "Save failed: ${e.message.orEmpty().take(160)}")
                    }
                },
                onFailure = { error ->
                    if (isRetryable(error)) return Result.retry()
                    failed.put(sessionId, error.message.orEmpty().take(160).ifBlank { "Download failed" })
                }
            )
            setProgress(progressData(ids, completed, failed, null, batchNumber, batchCount, index + 1))
            setForeground(createForegroundInfo(batchNumber, batchCount, index + 1, ids.length()))
        }
        return Result.success(
            Data.Builder()
                .putString(KEY_COMPLETED_IDS, completed.toString())
                .putString(KEY_FAILED_MESSAGES, failed.toString())
                .build()
        )
    }

    private fun isRetryable(error: Throwable): Boolean =
        error is IOException || Regex("API error: (408|425|429|5\\d\\d)").containsMatchIn(error.message.orEmpty())

    private fun progressData(
        ids: JSONArray,
        completed: JSONArray,
        failed: JSONObject,
        activeId: String?,
        batchNumber: Int,
        batchCount: Int,
        completedCount: Int
    ) = Data.Builder()
        .putString(KEY_SESSION_IDS, ids.toString())
        .putString(KEY_COMPLETED_IDS, completed.toString())
        .putString(KEY_FAILED_MESSAGES, failed.toString())
        .putString(KEY_ACTIVE_SESSION_ID, activeId)
        .putInt(KEY_BATCH_NUMBER, batchNumber)
        .putInt(KEY_BATCH_COUNT, batchCount)
        .build()

    private fun createForegroundInfo(batchNumber: Int, batchCount: Int, completed: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Event downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val openApp = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_start_marker)
            .setContentTitle("Downloading events")
            .setContentText("Batch $batchNumber of $batchCount · Event $completed of $total")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total, completed, false)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "remote_event_downloads"
        const val TAG_DOWNLOAD = "remote_event_download"
        const val SESSION_TAG_PREFIX = "remote_event_session:"
        const val KEY_SESSION_IDS = "session_ids"
        const val KEY_BATCH_NUMBER = "batch_number"
        const val KEY_BATCH_COUNT = "batch_count"
        const val KEY_COMPLETED_IDS = "completed_session_ids"
        const val KEY_FAILED_MESSAGES = "failed_session_messages"
        const val KEY_ACTIVE_SESSION_ID = "active_session_id"
        
        
        private const val CHANNEL_ID = "remote_event_downloads"
        private const val NOTIFICATION_ID = 4107
        private const val MAX_BATCH_SIZE = 25

        fun enqueue(context: Context, sessionIds: List<String>) {
            val uniqueIds = sessionIds.map(String::trim).filter(String::isNotEmpty).distinct()
            if (uniqueIds.isEmpty()) return
            val batches = uniqueIds.chunked(MAX_BATCH_SIZE)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val requests = batches.mapIndexed { index, ids ->
                androidx.work.OneTimeWorkRequestBuilder<SessionDownloadWorker>()
                    .setInputData(Data.Builder()
                        .putString(KEY_SESSION_IDS, JSONArray(ids).toString())
                        .putInt(KEY_BATCH_NUMBER, index + 1)
                        .putInt(KEY_BATCH_COUNT, batches.size)
                        .build())
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag(TAG_DOWNLOAD)
                    .apply { ids.forEach { sessionId -> addTag(SESSION_TAG_PREFIX + sessionId) } }
                    .build()
            }
            var continuation = androidx.work.WorkManager.getInstance(context)
                .beginUniqueWork(UNIQUE_WORK_NAME, androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE, requests.first())
            requests.drop(1).forEach { continuation = continuation.then(it) }
            continuation.enqueue()
        }
    }
}
