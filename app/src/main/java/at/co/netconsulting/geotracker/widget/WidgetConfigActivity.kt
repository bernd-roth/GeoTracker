package at.co.netconsulting.geotracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.data.ActiveUser
import at.co.netconsulting.geotracker.service.FollowingService
import at.co.netconsulting.geotracker.service.WidgetFollowingService

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case the user backs out
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Invalid widget ID, finishing")
            finish()
            return
        }

        Log.d(TAG, "Configuring widget $appWidgetId")

        // Connect to get active users
        val followingService = FollowingService.getInstance(this)
        followingService.connect()

        setContent {
            MaterialTheme {
                WidgetConfigScreen(
                    followingService = followingService,
                    onUserSelected = { user -> onRunnerSelected(user) },
                    onStopFollowing = { onStopFollowing() },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun onRunnerSelected(user: ActiveUser) {
        Log.d(TAG, "Runner selected: ${user.person} (session: ${user.sessionId})")

        // Save config
        val prefs = getSharedPreferences(GeoTrackerWidget.WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("widget_${appWidgetId}_session_id", user.sessionId)
            .putString("widget_${appWidgetId}_person", user.person)
            .apply()

        // Start WidgetFollowingService
        val serviceIntent = Intent(this, WidgetFollowingService::class.java)
        startForegroundService(serviceIntent)

        // Set widget result
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private fun onStopFollowing() {
        Log.d(TAG, "Stop following selected for widget $appWidgetId")

        // Remove config
        val prefs = getSharedPreferences(GeoTrackerWidget.WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("widget_${appWidgetId}_session_id")
            .remove("widget_${appWidgetId}_person")
            .apply()

        // Check if any other widgets still have following configured
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(this, GeoTrackerWidget::class.java)
        )
        val hasFollowingWidgets = allWidgetIds.any { id ->
            id != appWidgetId && prefs.getString("widget_${id}_session_id", null) != null
        }
        if (!hasFollowingWidgets) {
            stopService(Intent(this, WidgetFollowingService::class.java))
        }

        // Reset widget to not-tracking state
        GeoTrackerWidget.updateWidgetNotTracking(this)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    companion object {
        private const val TAG = "WidgetConfigActivity"
    }
}

@Composable
private fun WidgetConfigScreen(
    followingService: FollowingService,
    onUserSelected: (ActiveUser) -> Unit,
    onStopFollowing: () -> Unit,
    onCancel: () -> Unit
) {
    val activeUsers by followingService.activeUsers.collectAsState()
    val isLoading by followingService.isLoading.collectAsState()
    val isConnected by followingService.connectionState.collectAsState()

    var hasRequestedUsers by remember { mutableStateOf(false) }

    // Request active users once connected
    DisposableEffect(isConnected) {
        if (isConnected && !hasRequestedUsers) {
            followingService.requestActiveUsers()
            hasRequestedUsers = true
        }
        onDispose { }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Runner to Follow",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Widget will show live statistics for the selected runner",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stop following option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStopFollowing() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Stop Following / None",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            when {
                !isConnected && !hasRequestedUsers -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Connecting to server...")
                        }
                    }
                }
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading active runners...")
                        }
                    }
                }
                activeUsers.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No active runners found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                followingService.requestActiveUsers()
                            }) {
                                Text("Refresh")
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn {
                        items(activeUsers) { user ->
                            ActiveUserItem(
                                user = user,
                                onClick = { onUserSelected(user) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveUserItem(
    user: ActiveUser,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = user.person,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (user.eventName.isNotEmpty()) {
                    Text(
                        text = user.eventName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
