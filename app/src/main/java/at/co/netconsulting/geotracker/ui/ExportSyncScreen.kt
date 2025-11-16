package at.co.netconsulting.geotracker.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.co.netconsulting.geotracker.sync.ExportSyncViewModel
import at.co.netconsulting.geotracker.sync.SyncPlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExportSyncViewModel = viewModel()
) {
    val context = LocalContext.current
    val authStatus by viewModel.authStatus.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // State for Garmin login dialog
    var showGarminLoginDialog by remember { mutableStateOf(false) }

    // State for manual URL paste dialog
    var showPasteUrlDialog by remember { mutableStateOf(false) }
    var pasteUrlPlatform by remember { mutableStateOf<SyncPlatform?>(null) }

    // OAuth launchers for Strava and TrainingPeaks
    val stravaAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                viewModel.handleAuthorizationResponse(SyncPlatform.STRAVA, intent)
            }
        }
    }

    val trainingPeaksAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                viewModel.handleAuthorizationResponse(SyncPlatform.TRAINING_PEAKS, intent)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export & Sync") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Connect your accounts to automatically sync your activities",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Error message
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, "Dismiss")
                        }
                    }
                }
            }

            // Strava
            PlatformConnectionCard(
                platform = SyncPlatform.STRAVA,
                platformName = "Strava",
                platformDescription = "The social network for athletes",
                isConnected = authStatus[SyncPlatform.STRAVA] == true,
                onConnect = {
                    viewModel.getAuthorizationIntent(SyncPlatform.STRAVA)?.let { intent ->
                        stravaAuthLauncher.launch(intent)
                    }
                },
                onDisconnect = { viewModel.disconnect(SyncPlatform.STRAVA) },
                onPasteUrl = {
                    pasteUrlPlatform = SyncPlatform.STRAVA
                    showPasteUrlDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Garmin Connect
            PlatformConnectionCard(
                platform = SyncPlatform.GARMIN,
                platformName = "Garmin Connect",
                platformDescription = "Your Garmin fitness data hub",
                isConnected = authStatus[SyncPlatform.GARMIN] == true,
                onConnect = { showGarminLoginDialog = true },
                onDisconnect = { viewModel.disconnect(SyncPlatform.GARMIN) },
                note = "Note: Garmin integration may require additional API access"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // TrainingPeaks
            PlatformConnectionCard(
                platform = SyncPlatform.TRAINING_PEAKS,
                platformName = "TrainingPeaks",
                platformDescription = "Plan, track, and analyze your training",
                isConnected = authStatus[SyncPlatform.TRAINING_PEAKS] == true,
                onConnect = {
                    viewModel.getAuthorizationIntent(SyncPlatform.TRAINING_PEAKS)?.let { intent ->
                        trainingPeaksAuthLauncher.launch(intent)
                    }
                },
                onDisconnect = { viewModel.disconnect(SyncPlatform.TRAINING_PEAKS) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Setup instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Setup Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "To use these integrations, you need to register your app with each platform and configure the API credentials in the source code:\n\n" +
                                "• Strava: https://www.strava.com/settings/api\n" +
                                "• Garmin: https://developer.garmin.com\n" +
                                "• TrainingPeaks: https://developer.trainingpeaks.com\n\n" +
                                "Update the CLIENT_ID and CLIENT_SECRET constants in the respective API client files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }

    // Garmin login dialog
    if (showGarminLoginDialog) {
        GarminLoginDialog(
            onDismiss = { showGarminLoginDialog = false },
            onLogin = { username, password ->
                viewModel.authenticateGarmin(username, password)
                showGarminLoginDialog = false
            }
        )
    }

    if (showPasteUrlDialog) {
        PasteUrlDialog(
            platformName = when (pasteUrlPlatform) {
                SyncPlatform.STRAVA -> "Strava"
                SyncPlatform.TRAINING_PEAKS -> "TrainingPeaks"
                else -> "Platform"
            },
            onDismiss = { showPasteUrlDialog = false },
            onPaste = { url ->
                pasteUrlPlatform?.let { platform ->
                    viewModel.handleManualAuthorizationUrl(platform, url)
                }
                showPasteUrlDialog = false
            }
        )
    }
}

@Composable
fun PlatformConnectionCard(
    platform: SyncPlatform,
    platformName: String,
    platformDescription: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPasteUrl: (() -> Unit)? = null,
    note: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platformName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = platformDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isConnected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Connected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (note != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConnect,
                        modifier = if (onPasteUrl != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect")
                    }

                    if (onPasteUrl != null) {
                        OutlinedButton(
                            onClick = onPasteUrl,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste URL")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GarminLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (username: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Garmin Connect Login") },
        text = {
            Column {
                Text(
                    text = "Enter your Garmin Connect credentials",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Email/Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Note: Your credentials are stored locally and never shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank()
            ) {
                Text("Login")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PasteUrlDialog(
    platformName: String,
    onDismiss: () -> Unit,
    onPaste: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste $platformName Authorization URL") },
        text = {
            Column {
                Text(
                    text = "After authorizing on $platformName, you'll see 'This site can't be reached'. Copy the full URL from your browser and paste it here:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Authorization URL") },
                    placeholder = { Text("http://localhost/exchange_token?code=...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPaste(url) },
                enabled = url.contains("code=")
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
