package at.co.netconsulting.geotracker.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import at.co.netconsulting.geotracker.service.SessionDownloadWorker
import org.json.JSONArray
import org.json.JSONObject
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadEventsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FitnessTrackerDatabase.getInstance(application)
    private val apiClient = GeoTrackerApiClient(application)

    private val _availableSessions = MutableStateFlow<List<GeoTrackerApiClient.RemoteSessionSummary>>(emptyList())
    val availableSessions: StateFlow<List<GeoTrackerApiClient.RemoteSessionSummary>> = _availableSessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadState>> = _downloadProgress.asStateFlow()

    private val _selectedSessions = MutableStateFlow<Set<String>>(emptySet())
    val selectedSessions: StateFlow<Set<String>> = _selectedSessions.asStateFlow()

    private val _hasBackgroundDownloads = MutableStateFlow(false)
    val hasBackgroundDownloads: StateFlow<Boolean> = _hasBackgroundDownloads.asStateFlow()
    private val _backgroundDownloadStatus = MutableStateFlow("")
    val backgroundDownloadStatus: StateFlow<String> = _backgroundDownloadStatus.asStateFlow()
    private val workManager = WorkManager.getInstance(application)
    private val _isCancelling = MutableStateFlow(false)
    val isCancelling = _isCancelling.asStateFlow()
    private val _queueError = MutableStateFlow<String?>(null)
    val queueError = _queueError.asStateFlow()
    private var activeSessionIds = emptySet<String>()
    private val observedFinishedWork = mutableSetOf<java.util.UUID>()

    fun cancelDownloads() {
        if (_isCancelling.value) return
        _isCancelling.value = true
        _queueError.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    workManager.cancelUniqueWork(SessionDownloadWorker.UNIQUE_WORK_NAME).result.get()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _queueError.value = "Could not cancel downloads: ${e.message}"
            } finally {
                _isCancelling.value = false
            }
        }
    }

    sealed class DownloadState {
        object Idle : DownloadState()
        object Checking : DownloadState()
        object ReadyToDownload : DownloadState()
        object AlreadyDownloaded : DownloadState()
        object ActivelyRecording : DownloadState()
        object Queued : DownloadState()
        object Downloading : DownloadState()
        data class Success(val message: String) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    companion object {
        private const val TAG = "DownloadEventsViewModel"
    }

    init {
        Log.d(TAG, "DownloadEventsViewModel initialized")
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(SessionDownloadWorker.UNIQUE_WORK_NAME).collect(::updateBackgroundDownloadState)
        }
        loadAvailableSessions()
    }

    fun loadAvailableSessions() {
        Log.d(TAG, "loadAvailableSessions() called")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Starting to fetch sessions from server...")

                val sharedPreferences = getApplication<Application>()
                    .getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                val firstname = sharedPreferences.getString("firstname", "") ?: ""
                val lastname = sharedPreferences.getString("lastname", "")
                val birthdate = sharedPreferences.getString("birthdate", "")

                if (firstname.isBlank()) {
                    Log.e(TAG, "Cannot list sessions: user firstname not configured")
                    _availableSessions.value = emptyList()
                    return@launch
                }

                // Only show sessions belonging to the configured profile.
                val result = withContext(Dispatchers.IO) {
                    Log.d(TAG, "Calling apiClient.listUserSessions() for configured user: $firstname")
                    apiClient.listUserSessions(
                        firstname = firstname,
                        lastname = lastname,
                        birthdate = birthdate,
                        filterByUser = true
                    )
                }

                result.fold(
                    onSuccess = { sessions ->
                        _availableSessions.value = sessions
                        Log.d(TAG, "SUCCESS: Loaded ${sessions.size} remote sessions")

                        // Reset download progress
                        val previousProgress = _downloadProgress.value
                        _downloadProgress.value = sessions.associate { session ->
                            session.sessionId to (previousProgress[session.sessionId] ?: DownloadState.Idle)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "FAILURE: Error loading sessions: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "EXCEPTION: Error loading sessions: ${e.message}", e)
            } finally {
                _isLoading.value = false
                Log.d(TAG, "loadAvailableSessions() completed, isLoading=false")
            }
        }
    }

    fun toggleSessionSelection(sessionId: String) {
        val currentSelected = _selectedSessions.value.toMutableSet()
        if (currentSelected.contains(sessionId)) {
            currentSelected.remove(sessionId)
        } else {
            currentSelected.add(sessionId)
        }
        _selectedSessions.value = currentSelected
    }

    fun selectAll() {
        _selectedSessions.value = _availableSessions.value.map { it.sessionId }.toSet()
    }

    fun deselectAll() {
        _selectedSessions.value = emptySet()
    }

    /** Select all sessions belonging to a specific user */
    fun selectAllForUser(userKey: String, sessions: List<GeoTrackerApiClient.RemoteSessionSummary>) {
        val currentSelected = _selectedSessions.value.toMutableSet()
        sessions.forEach { currentSelected.add(it.sessionId) }
        _selectedSessions.value = currentSelected
    }

    /** Deselect all sessions belonging to a specific user */
    fun deselectAllForUser(userKey: String, sessions: List<GeoTrackerApiClient.RemoteSessionSummary>) {
        val currentSelected = _selectedSessions.value.toMutableSet()
        sessions.forEach { currentSelected.remove(it.sessionId) }
        _selectedSessions.value = currentSelected
    }

    fun checkSelectedSessions() {
        val selectedIds = _selectedSessions.value
        val sessionsToCheck = _availableSessions.value.filter { it.sessionId in selectedIds }

        if (sessionsToCheck.isEmpty()) {
            Log.w(TAG, "No sessions selected for check")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                sessionsToCheck.forEach { session ->
                    checkSessionStatus(session)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Check only the selected sessions for a specific user */
    fun checkSessionsForUser(sessions: List<GeoTrackerApiClient.RemoteSessionSummary>) {
        val selectedIds = _selectedSessions.value
        val sessionsToCheck = sessions.filter { it.sessionId in selectedIds }

        if (sessionsToCheck.isEmpty()) {
            Log.w(TAG, "No sessions selected for this user")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                sessionsToCheck.forEach { session ->
                    checkSessionStatus(session)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Schedule this user's ready sessions; WorkManager continues after this screen closes. */
    fun downloadSessionsForUser(sessions: List<GeoTrackerApiClient.RemoteSessionSummary>) {
        enqueueReadySessions(sessions)
    }

    private suspend fun checkSessionStatus(session: GeoTrackerApiClient.RemoteSessionSummary) {
        if (session.sessionId in activeSessionIds) return
        Log.d(TAG, "Checking session: ${session.eventName} (ID: ${session.sessionId})")
        updateProgress(session.sessionId, DownloadState.Checking)

        // Block download of sessions that are currently being recorded
        if (session.isRecording) {
            Log.d(TAG, "Session is actively recording: ${session.sessionId}")
            updateProgress(session.sessionId, DownloadState.ActivelyRecording)
            return
        }

        // Check if session is already in local database
        val existingEvent = withContext(Dispatchers.IO) {
            database.eventDao().getEventBySessionId(session.sessionId)
        }

        if (existingEvent != null) {
            Log.d(TAG, "Session already downloaded: ${session.sessionId}")
            updateProgress(session.sessionId, DownloadState.AlreadyDownloaded)
        } else {
            Log.d(TAG, "Session ready for download: ${session.sessionId}")
            updateProgress(session.sessionId, DownloadState.ReadyToDownload)
        }
    }

    fun downloadSelectedSessions() {
        enqueueReadySessions(_availableSessions.value.filter { it.sessionId in _selectedSessions.value })
    }

    private fun enqueueReadySessions(sessions: List<GeoTrackerApiClient.RemoteSessionSummary>) {
        if (_isCancelling.value || _isLoading.value) return
        val sessionsToQueue = sessions.distinctBy { it.sessionId }.filter { session ->
            session.sessionId !in activeSessionIds &&
                _downloadProgress.value[session.sessionId] == DownloadState.ReadyToDownload
        }
        if (sessionsToQueue.isEmpty()) {
            Log.w(TAG, "No sessions ready to download (run check first or all already downloaded)")
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                sessionsToQueue.forEach { updateProgress(it.sessionId, DownloadState.Queued) }
                withContext(Dispatchers.IO) {
                    SessionDownloadWorker.enqueue(getApplication<Application>(), sessionsToQueue.map { it.sessionId })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not queue event downloads", e)
                sessionsToQueue.forEach { updateProgress(it.sessionId, DownloadState.Error("Could not start background download: ${e.message}")) }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateBackgroundDownloadState(workInfos: List<WorkInfo>) {
        val next = _downloadProgress.value.toMutableMap()
        var outstanding = 0
        var running = false
        activeSessionIds = workInfos.filter { !it.state.isFinished }.flatMap { info ->
            info.tags.filter { it.startsWith(SessionDownloadWorker.SESSION_TAG_PREFIX) }
                .map { it.removePrefix(SessionDownloadWorker.SESSION_TAG_PREFIX) }
        }.toSet()
        // Active work always wins over historical attempts for the same event.
        workInfos.sortedBy { if (it.state.isFinished) 0 else 1 }.forEach { info ->
            // Do not replay old results over a subsequent Check or retry selection.
            if (info.state.isFinished && !observedFinishedWork.add(info.id)) return@forEach
            val inputIds = info.tags.filter { it.startsWith(SessionDownloadWorker.SESSION_TAG_PREFIX) }.map { it.removePrefix(SessionDownloadWorker.SESSION_TAG_PREFIX) }
            val pending = info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED || info.state == WorkInfo.State.RUNNING
            if (info.state == WorkInfo.State.RUNNING) running = true
            val progress = if (info.state == WorkInfo.State.RUNNING) info.progress else info.outputData
            val completedIds = stringArray(progress.getString(SessionDownloadWorker.KEY_COMPLETED_IDS))
            val failedMessages = runCatching {
                JSONObject(progress.getString(SessionDownloadWorker.KEY_FAILED_MESSAGES).orEmpty())
            }.getOrDefault(JSONObject())
            if (pending) outstanding += inputIds.count { it !in completedIds && !failedMessages.has(it) }
            completedIds.forEach { if (pending || next[it] != DownloadState.ReadyToDownload) next[it] = DownloadState.Success("Downloaded") }
            failedMessages.keys().forEach { id ->
                if (pending || next[id] != DownloadState.ReadyToDownload) next[id] = DownloadState.Error(failedMessages.optString(id, "Download failed"))
            }
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> inputIds.forEach { id ->
                    if (id !in completedIds && !failedMessages.has(id)) next[id] = DownloadState.Queued
                }
                WorkInfo.State.RUNNING -> {
                    inputIds.forEach { id ->
                        if (id !in completedIds && !failedMessages.has(id)) next[id] = DownloadState.Queued
                    }
                    info.progress.getString(SessionDownloadWorker.KEY_ACTIVE_SESSION_ID)?.let { next[it] = DownloadState.Downloading }
                }
                WorkInfo.State.FAILED -> inputIds.forEach { id ->
                    if (next[id] !is DownloadState.Success && next[id] !is DownloadState.Error) {
                        next[id] = DownloadState.Error("Background download stopped. Check the connection and retry.")
                    }
                }
                WorkInfo.State.CANCELLED -> inputIds.forEach { id ->
                    if (next[id] == DownloadState.Queued || next[id] == DownloadState.Downloading) next[id] = DownloadState.Idle
                }
                else -> Unit
            }
        }
        _downloadProgress.value = next
        _hasBackgroundDownloads.value = activeSessionIds.isNotEmpty()
        _backgroundDownloadStatus.value = if (activeSessionIds.isNotEmpty()) {
            if (running) "$outstanding events remaining. Downloading."
            else "$outstanding events queued. Waiting for network, Android scheduling, or retry."
        } else ""
    }

    private fun stringArray(value: String?): List<String> = runCatching {
        val array = JSONArray(value.orEmpty())
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun updateProgress(sessionId: String, state: DownloadState) {
        val currentProgress = _downloadProgress.value.toMutableMap()
        currentProgress[sessionId] = state
        _downloadProgress.value = currentProgress
    }

    fun clearDownloadState(sessionId: String) {
        updateProgress(sessionId, DownloadState.Idle)
    }
}
