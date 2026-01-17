package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.viewmodel.UploadEventsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadEventsDialog(
    onDismiss: () -> Unit,
    viewModel: UploadEventsViewModel = viewModel()
) {
    val unuploadedEvents by viewModel.unuploadedEvents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val selectedEvents by viewModel.selectedEvents.collectAsState()
    val showAllEvents by viewModel.showAllEvents.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upload Events to Server",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show all events toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show all events (including uploaded)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = showAllEvents,
                        onCheckedChange = { viewModel.toggleShowAllEvents() }
                    )
                }

                // Info text
                if (unuploadedEvents.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Green
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "All events have been uploaded!",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    // Selection controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${unuploadedEvents.size} events not uploaded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text("Select All")
                            }
                            TextButton(onClick = { viewModel.deselectAll() }) {
                                Text("Deselect All")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Events list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(unuploadedEvents, key = { it.eventId }) { event ->
                            EventUploadItem(
                                event = event,
                                isSelected = selectedEvents.contains(event.eventId),
                                uploadState = uploadProgress[event.eventId] ?: UploadEventsViewModel.UploadState.Idle,
                                onSelectionChange = { viewModel.toggleEventSelection(event.eventId) },
                                onClearError = { viewModel.clearUploadState(event.eventId) },
                                onResetUploadStatus = { viewModel.resetUploadStatus(event.eventId) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Count events that need upload
                    val needsUploadCount = uploadProgress.count { (eventId, state) ->
                        eventId in selectedEvents && state is UploadEventsViewModel.UploadState.NeedsUpload
                    }

                    // Check Status button
                    OutlinedButton(
                        onClick = { viewModel.checkSelectedEvents() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = selectedEvents.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedEvents.isEmpty()) {
                                    "Select events to check"
                                } else {
                                    "Check ${selectedEvents.size} event${if (selectedEvents.size > 1) "s" else ""}"
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Upload button (only uploads events with NeedsUpload state)
                    Button(
                        onClick = { viewModel.uploadSelectedEvents() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = needsUploadCount > 0 && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (needsUploadCount == 0) {
                                    "Check events first"
                                } else {
                                    "Upload $needsUploadCount event${if (needsUploadCount > 1) "s" else ""}"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventUploadItem(
    event: Event,
    isSelected: Boolean,
    uploadState: UploadEventsViewModel.UploadState,
    onSelectionChange: () -> Unit,
    onClearError: () -> Unit,
    onResetUploadStatus: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = uploadState !is UploadEventsViewModel.UploadState.Uploading,
                onClick = { onSelectionChange() },
                onLongClick = {
                    if (event.isUploaded) {
                        onResetUploadStatus()
                    }
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (uploadState) {
                is UploadEventsViewModel.UploadState.Success -> Color(0xFFE8F5E9)
                is UploadEventsViewModel.UploadState.AlreadyExists -> Color(0xFFE3F2FD)
                is UploadEventsViewModel.UploadState.NeedsUpload -> Color(0xFFFCE4EC)
                is UploadEventsViewModel.UploadState.Error -> Color(0xFFFFEBEE)
                is UploadEventsViewModel.UploadState.Uploading -> Color(0xFFFFF9C4)
                is UploadEventsViewModel.UploadState.Checking -> Color(0xFFFFF3E0)
                else -> if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionChange() },
                enabled = uploadState !is UploadEventsViewModel.UploadState.Uploading
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Event info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.eventName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${event.eventDate} • ${event.artOfSport}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show if already uploaded
                if (event.isUploaded && uploadState is UploadEventsViewModel.UploadState.Idle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✓ Already uploaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1976D2),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Show upload status
                when (uploadState) {
                    is UploadEventsViewModel.UploadState.Success -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✓ ${uploadState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    is UploadEventsViewModel.UploadState.AlreadyExists -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✓ Already on server (skipped)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1565C0)
                        )
                    }
                    is UploadEventsViewModel.UploadState.NeedsUpload -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⬆ Not on server - ready to upload",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC2185B)
                        )
                    }
                    is UploadEventsViewModel.UploadState.Error -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "✗ Error: ${uploadState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828)
                        )
                    }
                    is UploadEventsViewModel.UploadState.Uploading -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Uploading...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is UploadEventsViewModel.UploadState.Checking -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Checking server...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {}
                }
            }

            // Status icon and actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show reset button for already uploaded events
                if (event.isUploaded && uploadState is UploadEventsViewModel.UploadState.Idle) {
                    IconButton(
                        onClick = onResetUploadStatus,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset upload status",
                            tint = Color(0xFF1976D2),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Status icon
                when (uploadState) {
                    is UploadEventsViewModel.UploadState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Uploaded",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is UploadEventsViewModel.UploadState.AlreadyExists -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Already exists",
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is UploadEventsViewModel.UploadState.NeedsUpload -> {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Needs upload",
                            tint = Color(0xFFC2185B),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is UploadEventsViewModel.UploadState.Error -> {
                        IconButton(onClick = onClearError) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    is UploadEventsViewModel.UploadState.Uploading,
                    is UploadEventsViewModel.UploadState.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
