package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
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
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import at.co.netconsulting.geotracker.viewmodel.DownloadEventsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadEventsDialog(
    onDismiss: () -> Unit,
    viewModel: DownloadEventsViewModel = viewModel()
) {
    val availableSessions by viewModel.availableSessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val selectedSessions by viewModel.selectedSessions.collectAsState()
    val showAllSessions by viewModel.showAllSessions.collectAsState()

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
                        text = "Download Events from Server",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show all sessions toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show all events (including downloaded)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = showAllSessions,
                        onCheckedChange = { viewModel.toggleShowAllSessions() }
                    )
                }

                // Info text or empty state
                if (availableSessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Loading sessions from server...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.Green
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "All events have been downloaded!",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
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
                            text = "${availableSessions.size} events on server",
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

                    // Sessions list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableSessions, key = { it.sessionId }) { session ->
                            SessionDownloadItem(
                                session = session,
                                isSelected = selectedSessions.contains(session.sessionId),
                                downloadState = downloadProgress[session.sessionId] ?: DownloadEventsViewModel.DownloadState.Idle,
                                onSelectionChange = { viewModel.toggleSessionSelection(session.sessionId) },
                                onClearError = { viewModel.clearDownloadState(session.sessionId) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Count sessions that need download
                    val readyToDownloadCount = downloadProgress.count { (sessionId, state) ->
                        sessionId in selectedSessions && state is DownloadEventsViewModel.DownloadState.ReadyToDownload
                    }

                    // Check Status button
                    OutlinedButton(
                        onClick = { viewModel.checkSelectedSessions() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = selectedSessions.isNotEmpty() && !isLoading
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
                                text = if (selectedSessions.isEmpty()) {
                                    "Select events to check"
                                } else {
                                    "Check ${selectedSessions.size} event${if (selectedSessions.size > 1) "s" else ""}"
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Download button
                    Button(
                        onClick = { viewModel.downloadSelectedSessions() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = readyToDownloadCount > 0 && !isLoading,
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
                            Text("Downloading...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (readyToDownloadCount == 0) {
                                    "Check events first"
                                } else {
                                    "Download $readyToDownloadCount event${if (readyToDownloadCount > 1) "s" else ""}"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDownloadItem(
    session: GeoTrackerApiClient.RemoteSessionSummary,
    isSelected: Boolean,
    downloadState: DownloadEventsViewModel.DownloadState,
    onSelectionChange: () -> Unit,
    onClearError: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = downloadState !is DownloadEventsViewModel.DownloadState.Downloading,
                onClick = { onSelectionChange() }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (downloadState) {
                is DownloadEventsViewModel.DownloadState.Success -> Color(0xFFE8F5E9)
                is DownloadEventsViewModel.DownloadState.AlreadyDownloaded -> Color(0xFFE3F2FD)
                is DownloadEventsViewModel.DownloadState.ReadyToDownload -> Color(0xFFF3E5F5) // Purple for ready
                is DownloadEventsViewModel.DownloadState.Error -> Color(0xFFFFEBEE)
                is DownloadEventsViewModel.DownloadState.Downloading -> Color(0xFFFFF9C4)
                is DownloadEventsViewModel.DownloadState.Checking -> Color(0xFFFFF3E0)
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
                enabled = downloadState !is DownloadEventsViewModel.DownloadState.Downloading
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Session info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.eventName ?: "Unnamed Event",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Date and sport type
                val dateDisplay = session.startDateTime?.let {
                    try {
                        it.substring(0, 10)
                    } catch (e: Exception) {
                        "Unknown date"
                    }
                } ?: "Unknown date"

                Text(
                    text = "$dateDisplay • ${session.sportType ?: "Unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Location info
                val locationInfo = listOfNotNull(session.startCity, session.startCountry)
                    .joinToString(", ")
                if (locationInfo.isNotBlank()) {
                    Text(
                        text = locationInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // GPS point count
                Text(
                    text = "${session.gpsPointCount} GPS points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Show download status
                when (downloadState) {
                    is DownloadEventsViewModel.DownloadState.Success -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\u2713 ${downloadState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    is DownloadEventsViewModel.DownloadState.AlreadyDownloaded -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\u2713 Already in local database",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1565C0)
                        )
                    }
                    is DownloadEventsViewModel.DownloadState.ReadyToDownload -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\u2B07 Ready to download",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7B1FA2) // Purple
                        )
                    }
                    is DownloadEventsViewModel.DownloadState.Error -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\u2717 Error: ${downloadState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828)
                        )
                    }
                    is DownloadEventsViewModel.DownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Downloading...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is DownloadEventsViewModel.DownloadState.Checking -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Checking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {}
                }
            }

            // Status icon
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (downloadState) {
                    is DownloadEventsViewModel.DownloadState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is DownloadEventsViewModel.DownloadState.AlreadyDownloaded -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Already downloaded",
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is DownloadEventsViewModel.DownloadState.ReadyToDownload -> {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Ready to download",
                            tint = Color(0xFF7B1FA2), // Purple
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    is DownloadEventsViewModel.DownloadState.Error -> {
                        IconButton(onClick = onClearError) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    is DownloadEventsViewModel.DownloadState.Downloading,
                    is DownloadEventsViewModel.DownloadState.Checking -> {
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
