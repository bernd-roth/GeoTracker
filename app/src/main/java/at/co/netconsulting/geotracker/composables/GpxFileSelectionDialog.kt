package at.co.netconsulting.geotracker.composables

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream

@Composable
fun GPXFileSelectionDialog(
    context: Context,
    onDismissRequest: () -> Unit,
    onFileSelected: (List<File>) -> Unit  // Modified to accept a list of files
) {
    var showError by remember { mutableStateOf(false) }

    // Function to convert Uri to File
    fun urisToFiles(uris: List<Uri>): List<File> {
        return uris.mapNotNull { uri ->
            try {
                val tempFile = File(context.cacheDir, "temp_gpx_file_${System.currentTimeMillis()}.gpx")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // Modified to use GetMultipleContents
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val files = urisToFiles(uris)
            if (files.isNotEmpty()) {
                onFileSelected(files)
            } else {
                showError = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select GPX Files") },
        text = {
            if (showError) {
                Text("Error importing GPX files. Please try again.")
            } else {
                Text("Select one or more GPX files to import")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    launcher.launch("*/*")
                }
            ) {
                Text("Choose Files")
            }
        },
        dismissButton = {
            Button(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}