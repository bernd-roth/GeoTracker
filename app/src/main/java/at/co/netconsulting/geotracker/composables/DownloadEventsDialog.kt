package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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

/** Grouping key for a user based on firstname + lastname */
private fun userKey(session: GeoTrackerApiClient.RemoteSessionSummary): String {
    val first = session.firstname?.trim().orEmpty()
    val last = session.lastname?.trim().orEmpty()
    return if (first.isEmpty() && last.isEmpty()) "Unknown" else "$first $last".trim()
}

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

    // Filter state - hide sessions with few GPS points
    var hideSmallSessions by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val minGpsPoints = 10

    // First deduplicate by sessionId (server may return duplicates), then apply filters
    val filteredSessions = remember(availableSessions, hideSmallSessions, searchQuery) {
        val deduplicated = availableSessions.distinctBy { it.sessionId }

        val filtered = if (hideSmallSessions) {
            deduplicated.filter { it.gpsPointCount >= minGpsPoints }
        } else {
            deduplicated
        }

        if (searchQuery.isBlank()) {
            filtered
        } else {
            val query = searchQuery.lowercase()
            filtered.filter { session ->
                (session.eventName?.lowercase()?.contains(query) == true) ||
                (session.sportType?.lowercase()?.contains(query) == true) ||
                (session.startDateTime?.lowercase()?.contains(query) == true) ||
                (session.startCity?.lowercase()?.contains(query) == true) ||
                (session.startCountry?.lowercase()?.contains(query) == true) ||
                (session.firstname?.lowercase()?.contains(query) == true) ||
                (session.lastname?.lowercase()?.contains(query) == true)
            }
        }
    }
    val deduplicatedSessions = remember(availableSessions) {
        availableSessions.distinctBy { it.sessionId }
    }
    val totalFilteredCount = if (hideSmallSessions) {
        deduplicatedSessions.count { it.gpsPointCount >= minGpsPoints }
    } else {
        deduplicatedSessions.size
    }

    // Group filtered sessions by user
    val groupedByUser = remember(filteredSessions) {
        filteredSessions.groupBy { userKey(it) }.toSortedMap()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Compact header with title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Download Events",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                // Compact filter toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hide incomplete (<$minGpsPoints pts)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(
                        checked = hideSmallSessions,
                        onCheckedChange = { hideSmallSessions = it },
                        modifier = Modifier.height(24.dp)
                    )
                }

                // Search box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    placeholder = { Text("Search events, dates, sport...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )

                // Info text or empty state
                if (filteredSessions.isEmpty()) {
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
                                    text = if (hideSmallSessions && availableSessions.isNotEmpty()) {
                                        "No events with $minGpsPoints+ GPS points.\nDisable filter to see all ${availableSessions.size} events."
                                    } else if (availableSessions.isEmpty()) {
                                        "No events found on server."
                                    } else {
                                        "No events to display."
                                    },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                } else {
                    // Total count info
                    val hiddenByFilter = deduplicatedSessions.size - totalFilteredCount
                    val filterInfo = if (hiddenByFilter > 0) " ($hiddenByFilter hidden)" else ""
                    Text(
                        text = "$totalFilteredCount events from ${groupedByUser.size} users$filterInfo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Grouped sessions list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        groupedByUser.forEach { (userName, userSessions) ->
                            // User header
                            item(key = "header_$userName") {
                                UserSectionHeader(
                                    userName = userName,
                                    sessionCount = userSessions.size,
                                    selectedCount = userSessions.count { it.sessionId in selectedSessions },
                                    isLoading = isLoading,
                                    readyToDownloadCount = userSessions.count { session ->
                                        session.sessionId in selectedSessions &&
                                        downloadProgress[session.sessionId] is DownloadEventsViewModel.DownloadState.ReadyToDownload
                                    },
                                    onSelectAll = { viewModel.selectAllForUser(userName, userSessions) },
                                    onDeselectAll = { viewModel.deselectAllForUser(userName, userSessions) },
                                    onCheck = { viewModel.checkSessionsForUser(userSessions) },
                                    onDownload = { viewModel.downloadSessionsForUser(userSessions) }
                                )
                            }

                            // Sessions for this user
                            items(userSessions, key = { it.sessionId }) { session ->
                                SessionDownloadItemCompact(
                                    session = session,
                                    isSelected = selectedSessions.contains(session.sessionId),
                                    downloadState = downloadProgress[session.sessionId] ?: DownloadEventsViewModel.DownloadState.Idle,
                                    onSelectionChange = { viewModel.toggleSessionSelection(session.sessionId) }
                                )
                            }

                            // Divider after each user group
                            item(key = "divider_$userName") {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSectionHeader(
    userName: String,
    sessionCount: Int,
    selectedCount: Int,
    isLoading: Boolean,
    readyToDownloadCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary,
            thickness = 1.dp
        )
        Spacer(modifier = Modifier.height(4.dp))

        // User name + event count
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = userName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$sessionCount events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Per-user controls: Select All/None + Check/Download
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Select All / None
            Row {
                TextButton(
                    onClick = onSelectAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("All", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(
                    onClick = onDeselectAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("None", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Check / Download
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = onCheck,
                    enabled = selectedCount > 0 && !isLoading,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (selectedCount == 0) "Check" else "Check $selectedCount",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Button(
                    onClick = onDownload,
                    enabled = readyToDownloadCount > 0 && !isLoading,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (readyToDownloadCount == 0) "Download" else "Download $readyToDownloadCount",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
fun SessionDownloadItemCompact(
    session: GeoTrackerApiClient.RemoteSessionSummary,
    isSelected: Boolean,
    downloadState: DownloadEventsViewModel.DownloadState,
    onSelectionChange: () -> Unit
) {
    val backgroundColor = when (downloadState) {
        is DownloadEventsViewModel.DownloadState.Success -> Color(0xFFE8F5E9)
        is DownloadEventsViewModel.DownloadState.AlreadyDownloaded -> Color(0xFFE3F2FD)
        is DownloadEventsViewModel.DownloadState.ReadyToDownload -> Color(0xFFF3E5F5)
        is DownloadEventsViewModel.DownloadState.ActivelyRecording -> Color(0xFFFFEBEE)
        is DownloadEventsViewModel.DownloadState.Error -> Color(0xFFFFEBEE)
        is DownloadEventsViewModel.DownloadState.Downloading,
        is DownloadEventsViewModel.DownloadState.Checking -> Color(0xFFFFF9C4)
        else -> if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = downloadState !is DownloadEventsViewModel.DownloadState.Downloading,
                onClick = onSelectionChange
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectionChange() },
                modifier = Modifier.size(24.dp),
                enabled = downloadState !is DownloadEventsViewModel.DownloadState.Downloading
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Event info - compact (no "Recorded by" since sessions are grouped by user)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.eventName ?: "Unnamed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                val dateStr = session.startDateTime?.take(10) ?: ""
                val pointsStr = "${session.gpsPointCount} pts"
                Text(
                    text = "$dateStr • ${session.sportType ?: ""} • $pointsStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Status indicator
            when (downloadState) {
                is DownloadEventsViewModel.DownloadState.Success ->
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                is DownloadEventsViewModel.DownloadState.AlreadyDownloaded ->
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF1565C0), modifier = Modifier.size(20.dp))
                is DownloadEventsViewModel.DownloadState.ReadyToDownload ->
                    Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(20.dp))
                is DownloadEventsViewModel.DownloadState.ActivelyRecording ->
                    Icon(Icons.Default.FiberManualRecord, "Recording", tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                is DownloadEventsViewModel.DownloadState.Error ->
                    Icon(Icons.Default.Error, null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                is DownloadEventsViewModel.DownloadState.Downloading,
                is DownloadEventsViewModel.DownloadState.Checking ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> {}
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
                is DownloadEventsViewModel.DownloadState.ActivelyRecording -> Color(0xFFFFEBEE)
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

                // Recorded by
                val recordedBy = listOfNotNull(session.firstname, session.lastname)
                    .joinToString(" ")
                if (recordedBy.isNotBlank()) {
                    Text(
                        text = "Recorded by: $recordedBy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                    is DownloadEventsViewModel.DownloadState.ActivelyRecording -> {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\u26A0 Currently being recorded — download blocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828)
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
                    is DownloadEventsViewModel.DownloadState.ActivelyRecording -> {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = "Recording",
                            tint = Color(0xFFC62828),
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
