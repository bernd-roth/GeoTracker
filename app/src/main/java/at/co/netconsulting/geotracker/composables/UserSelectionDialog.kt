package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.data.ActiveUser
import at.co.netconsulting.geotracker.service.FollowingService

@Composable
fun UserSelectionDialog(
    activeUsers: List<ActiveUser>,
    currentlyFollowing: List<String>,
    isLoading: Boolean,
    currentPrecisionMode: FollowingService.TrailPrecisionMode,
    onFollowUsers: (List<String>) -> Unit,
    onStopFollowing: () -> Unit,
    onRefreshUsers: () -> Unit,
    onPrecisionModeChanged: (FollowingService.TrailPrecisionMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedUsers by remember { mutableStateOf(currentlyFollowing.toSet()) }
    var selectedPrecisionMode by remember { mutableStateOf(currentPrecisionMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Follow Active Users",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(
                    onClick = onRefreshUsers,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Users"
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp) // Increased height to accommodate precision settings
            ) {
                item {
                    // Current following status
                    if (currentlyFollowing.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Currently following ${currentlyFollowing.size} user(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = {
                                    onStopFollowing()
                                    onDismiss()
                                }
                            ) {
                                Text("Stop Following All")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Trail Precision Settings
                    TrailPrecisionSection(
                        selectedPrecisionMode = selectedPrecisionMode,
                        onPrecisionModeChanged = { mode ->
                            selectedPrecisionMode = mode
                            onPrecisionModeChanged(mode)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Active Users Section Header
                    Text(
                        text = "Available Users",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (activeUsers.isEmpty() && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No active users found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(activeUsers) { user ->
                        UserSelectionItem(
                            user = user,
                            isSelected = user.sessionId in selectedUsers,
                            onToggle = { isSelected ->
                                selectedUsers = if (isSelected) {
                                    selectedUsers + user.sessionId
                                } else {
                                    selectedUsers - user.sessionId
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onFollowUsers(selectedUsers.toList())
                    onDismiss()
                },
                enabled = selectedUsers.isNotEmpty() && !isLoading
            ) {
                Text("Follow Selected (${selectedUsers.size})")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TrailPrecisionSection(
    selectedPrecisionMode: FollowingService.TrailPrecisionMode,
    onPrecisionModeChanged: (FollowingService.TrailPrecisionMode) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Trail Precision",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Three most useful precision modes
        val precisionModes = listOf(
            FollowingService.TrailPrecisionMode.EVERY_POINT,
            FollowingService.TrailPrecisionMode.MEDIUM_PRECISION,
            FollowingService.TrailPrecisionMode.LOW_PRECISION
        )

        precisionModes.forEach { mode ->
            PrecisionModeItem(
                mode = mode,
                isSelected = selectedPrecisionMode == mode,
                onSelected = { onPrecisionModeChanged(mode) }
            )
        }
    }
}

@Composable
private fun PrecisionModeItem(
    mode: FollowingService.TrailPrecisionMode,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelected,
                role = Role.RadioButton
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelected
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (mode) {
                    FollowingService.TrailPrecisionMode.EVERY_POINT -> "Maximum Detail"
                    FollowingService.TrailPrecisionMode.MEDIUM_PRECISION -> "Balanced"
                    FollowingService.TrailPrecisionMode.LOW_PRECISION -> "Simplified"
                    else -> mode.description
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when (mode) {
                    FollowingService.TrailPrecisionMode.EVERY_POINT -> "Shows every GPS point (most detailed)"
                    FollowingService.TrailPrecisionMode.MEDIUM_PRECISION -> "2m minimum distance (recommended)"
                    FollowingService.TrailPrecisionMode.LOW_PRECISION -> "5m minimum distance (less cluttered)"
                    else -> mode.description
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UserSelectionItem(
    user: ActiveUser,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .toggleable(
                value = isSelected,
                onValueChange = onToggle,
                role = Role.Checkbox
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle
            )

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user.person,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (user.eventName.isNotEmpty()) {
                    Text(
                        text = user.eventName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (user.lastUpdate.isNotEmpty()) {
                    Text(
                        text = "Last update: ${user.lastUpdate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}