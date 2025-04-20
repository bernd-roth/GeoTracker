package at.co.netconsulting.geotracker.composables

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.viewmodel.GpxImportViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A dedicated screen for importing GPX files with preview capabilities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpxImportScreen(
    onNavigateBack: () -> Unit,
    onImportSuccess: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val importViewModel = remember { GpxImportViewModel(FitnessTrackerDatabase.getInstance(context)) }

    // State for file selection
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var eventName by remember { mutableStateOf("") }
    var sportType by remember { mutableStateOf("") }
    var eventComment by remember { mutableStateOf("") }

    // Import status
    val isImporting by importViewModel.isImporting.collectAsState()
    val importProgress by importViewModel.importProgress.collectAsState()
    val importResult by importViewModel.importResult.collectAsState()

    // File picker launcher
    val gpxFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                selectedFileUri = uri

                // Extract file name
                uri.path?.let { path ->
                    val fileName = path.substringAfterLast('/')
                    selectedFileName = fileName

                    // Set default event name based on file name
                    if (eventName.isEmpty()) {
                        eventName = fileName.replace(".gpx", "").replace("_", " ")
                    }
                }
            }
        }
    }

    // Handle import result
    LaunchedEffect(importResult) {
        importResult?.let { result ->
            when (result) {
                is GpxImportViewModel.ImportResult.Success -> {
                    // Short delay to show completion
                    delay(500)
                    Toast.makeText(context, "GPX file imported successfully!", Toast.LENGTH_SHORT)
                        .show()
                    onImportSuccess(result.eventId)
                }

                is GpxImportViewModel.ImportResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Launch file picker
    fun launchGpxFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/gpx+xml", "text/xml", "*/*"))
        }
        gpxFileLauncher.launch(intent)
    }

    // Start import process
    fun startImport() {
        selectedFileUri?.let { uri ->
            coroutineScope.launch {
                importViewModel.importGpxFile(context, uri)
                // Note: We'll need to update the GpxImporter to use these values
                // For now, just using the default implementation
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import GPX File") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // File selection card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select GPX File",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedFileUri == null) {
                        // No file selected yet
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(onClick = { launchGpxFilePicker() }),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Upload GPX",
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Tap to select a GPX file",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        // File selected
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "File Selected",
                                tint = Color.Green,
                                modifier = Modifier.size(36.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Selected file:",
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = selectedFileName,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { launchGpxFilePicker() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (selectedFileUri == null) "Select GPX File" else "Change File")
                    }
                }
            }

            // Event details
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Event Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = eventName,
                        onValueChange = { eventName = it },
                        label = { Text("Event Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = sportType,
                        onValueChange = { sportType = it },
                        label = { Text("Sport Type") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = eventComment,
                        onValueChange = { eventComment = it },
                        label = { Text("Comments") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Import button and status
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isImporting) {
                        // Show progress
                        Text(
                            text = "Importing GPX File...",
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = importProgress,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${(importProgress * 100).toInt()}%",
                            fontSize = 14.sp
                        )
                    } else if (importResult != null) {
                        // Show result
                        when (importResult) {
                            is GpxImportViewModel.ImportResult.Success -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color.Green,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.size(8.dp))

                                    Text(
                                        text = "Import successful!",
                                        color = Color.Green,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            is GpxImportViewModel.ImportResult.Error -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Error",
                                        tint = Color.Red,
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.size(8.dp))

                                    Text(
                                        text = (importResult as GpxImportViewModel.ImportResult.Error).message,
                                        color = Color.Red,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { startImport() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedFileUri != null && !isImporting
                    ) {
                        Text("Import GPX")
                    }
                }
            }
        }
    }
}