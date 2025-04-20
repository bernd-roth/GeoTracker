package at.co.netconsulting.geotracker.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.tools.GpxImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel to handle GPX import operations
 */
class GpxImportViewModel(private val database: FitnessTrackerDatabase) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    /**
     * Import a GPX file
     * @param context Android context
     * @param uri URI of the GPX file to import
     */
    fun importGpxFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importProgress.value = 0f
            _importResult.value = null

            try {
                // Update progress to indicate start
                _importProgress.value = 0.1f

                // Create GPX importer and import the file
                val gpxImporter = GpxImporter(context)

                // Update progress
                _importProgress.value = 0.3f

                // Perform the import
                val newEventId = gpxImporter.importGpx(uri)

                // Update progress
                _importProgress.value = 0.8f

                // Check if import was successful
                if (newEventId > 0) {
                    _importResult.value = ImportResult.Success(newEventId)
                } else {
                    _importResult.value = ImportResult.Error("Failed to import GPX file")
                }

                // Final progress update
                _importProgress.value = 1f
            } catch (e: Exception) {
                _importResult.value = ImportResult.Error("Error importing GPX: ${e.message}")
            } finally {
                _isImporting.value = false
            }
        }
    }

    /**
     * Reset the import result
     */
    fun resetImportResult() {
        _importResult.value = null
    }

    /**
     * Result of a GPX import operation
     */
    sealed class ImportResult {
        data class Success(val eventId: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}

// Extension to EventsViewModel to support GPX import functionality
fun EventsViewModel.refreshAfterImport() {
    // Refresh event list after an import
    loadEvents()
}