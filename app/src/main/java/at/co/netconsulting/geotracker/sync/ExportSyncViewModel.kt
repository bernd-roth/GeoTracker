package at.co.netconsulting.geotracker.sync

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing export sync operations
 */
class ExportSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val syncManager = ExportSyncManager(application)

    // Authentication status for each platform
    private val _authStatus = MutableStateFlow<Map<SyncPlatform, Boolean>>(emptyMap())
    val authStatus: StateFlow<Map<SyncPlatform, Boolean>> = _authStatus.asStateFlow()

    // Sync operation state
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refreshAuthStatus()
    }

    /**
     * Refresh authentication status for all platforms
     */
    fun refreshAuthStatus() {
        _authStatus.value = syncManager.getAuthenticationStatus()
    }

    /**
     * Get authorization intent for a platform
     */
    fun getAuthorizationIntent(platform: SyncPlatform): Intent? {
        return try {
            when (platform) {
                SyncPlatform.STRAVA -> syncManager.getStravaClient().createAuthorizationIntent()
                SyncPlatform.GARMIN -> null // Garmin uses username/password
                SyncPlatform.TRAINING_PEAKS -> syncManager.getTrainingPeaksClient().createAuthorizationIntent()
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to create authorization intent: ${e.message}"
            null
        }
    }

    /**
     * Handle OAuth authorization response
     */
    fun handleAuthorizationResponse(platform: SyncPlatform, intent: Intent) {
        viewModelScope.launch {
            try {
                val result = when (platform) {
                    SyncPlatform.STRAVA -> syncManager.getStravaClient().handleAuthorizationResponse(intent)
                    SyncPlatform.TRAINING_PEAKS -> syncManager.getTrainingPeaksClient().handleAuthorizationResponse(intent)
                    SyncPlatform.GARMIN -> Result.failure(Exception("Garmin uses different auth method"))
                }

                result.fold(
                    onSuccess = {
                        refreshAuthStatus()
                        _errorMessage.value = null
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Authentication failed: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error handling authorization: ${e.message}"
            }
        }
    }

    /**
     * Handle manual authorization URL (for when redirect fails)
     */
    fun handleManualAuthorizationUrl(platform: SyncPlatform, url: String) {
        viewModelScope.launch {
            try {
                val result = when (platform) {
                    SyncPlatform.STRAVA -> syncManager.getStravaClient().handleManualAuthorizationUrl(url)
                    SyncPlatform.TRAINING_PEAKS -> syncManager.getTrainingPeaksClient().handleManualAuthorizationUrl(url)
                    SyncPlatform.GARMIN -> Result.failure(Exception("Garmin uses different auth method"))
                }

                result.fold(
                    onSuccess = {
                        refreshAuthStatus()
                        _errorMessage.value = null
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Authentication failed: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error handling manual authorization: ${e.message}"
            }
        }
    }

    /**
     * Authenticate with Garmin Connect (username/password)
     */
    fun authenticateGarmin(username: String, password: String) {
        viewModelScope.launch {
            try {
                val result = syncManager.getGarminClient().authenticate(username, password)
                result.fold(
                    onSuccess = {
                        refreshAuthStatus()
                        _errorMessage.value = null
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Garmin authentication failed: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error authenticating with Garmin: ${e.message}"
            }
        }
    }

    /**
     * Disconnect from a platform
     */
    fun disconnect(platform: SyncPlatform) {
        syncManager.disconnect(platform)
        refreshAuthStatus()
    }

    /**
     * Sync an event to selected platforms
     */
    fun syncEvent(eventId: Int, platforms: List<SyncPlatform>) {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.Syncing(platforms)
                _errorMessage.value = null

                val results = syncManager.syncEvent(eventId, platforms)

                _syncState.value = SyncState.Completed(results)

                // Check if any sync failed
                val failures = results.filterValues { it is SyncResult.Failure }
                if (failures.isNotEmpty()) {
                    val errorMessages = failures.entries.joinToString("\n") { (platform, result) ->
                        "$platform: ${(result as SyncResult.Failure).error}"
                    }
                    _errorMessage.value = "Some syncs failed:\n$errorMessages"
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
                _errorMessage.value = "Sync error: ${e.message}"
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Reset sync state
     */
    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }
}

/**
 * State of sync operation
 */
sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val platforms: List<SyncPlatform>) : SyncState()
    data class Completed(val results: Map<SyncPlatform, SyncResult>) : SyncState()
    data class Error(val message: String) : SyncState()
}
