package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A widget that displays the status of automatic backups
 */
@Composable
fun BackupStatusWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isBackupEnabled by remember { mutableStateOf(false) }
    var nextBackupTime by remember { mutableStateOf<String?>(null) }
    var lastBackupTime by remember { mutableStateOf<String?>(null) }
    var lastBackupCount by remember { mutableStateOf(0) }

    // Load backup status information
    LaunchedEffect(Unit) {
        val userPrefs = context.getSharedPreferences("UserSettings", android.content.Context.MODE_PRIVATE)
        val backupPrefs = context.getSharedPreferences("BackupPrefs", android.content.Context.MODE_PRIVATE)

        isBackupEnabled = userPrefs.getBoolean("autoBackupEnabled", false)
        nextBackupTime = backupPrefs.getString("nextBackupTime", null)

        val lastBackupTimeMs = backupPrefs.getLong("lastBackupTime", 0L)
        lastBackupCount = backupPrefs.getInt("lastBackupEventCount", 0)

        if (lastBackupTimeMs > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            lastBackupTime = dateFormat.format(Date(lastBackupTimeMs))
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Backup Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Auto Backup:",
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isBackupEnabled) "Enabled" else "Disabled",
                    color = if (isBackupEnabled) Color.Green else Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isBackupEnabled && nextBackupTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Next backup:",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = nextBackupTime ?: "Not scheduled",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (lastBackupTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Last backup:",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$lastBackupTime ($lastBackupCount runs)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}