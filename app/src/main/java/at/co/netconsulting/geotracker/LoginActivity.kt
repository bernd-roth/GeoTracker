package at.co.netconsulting.geotracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.auth.KeycloakAuthManager
import timber.log.Timber

class LoginActivity : ComponentActivity() {

    private lateinit var authManager: KeycloakAuthManager
    private var isLoggingIn by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            authManager.handleAuthorizationResponse(
                intent = data,
                onSuccess = { accessToken ->
                    Timber.d("Login successful, token: ${accessToken.take(20)}...")
                    navigateToMain()
                },
                onError = { error ->
                    Timber.e("Login failed: $error")
                    isLoggingIn = false
                    errorMessage = "Login failed: $error"
                }
            )
        } else {
            isLoggingIn = false
            errorMessage = "Login cancelled"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = KeycloakAuthManager(this)

        // Check if already authenticated
        if (authManager.isAuthenticated()) {
            Timber.d("User already authenticated, navigating to main")
            navigateToMain()
            return
        }

        setContent {
            MaterialTheme {
                LoginScreen(
                    isLoggingIn = isLoggingIn,
                    errorMessage = errorMessage,
                    onLoginClick = { startLogin() },
                    onRegisterClick = { startRegister() }
                )
            }
        }
    }

    private fun startLogin() {
        isLoggingIn = true
        errorMessage = null
        val authIntent = authManager.getAuthorizationIntent()
        authLauncher.launch(authIntent)
    }

    private fun startRegister() {
        // Open Keycloak registration page in browser
        val registerUrl = "https://geotracker.duckdns.org/auth/realms/geotracker/protocol/openid-connect/registrations?client_id=geotracker-android&response_type=code&redirect_uri=at.co.netconsulting.geotracker:/oauth2redirect"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(registerUrl))
        startActivity(intent)

        // After registration, user can come back and login
        errorMessage = "After registering, please click 'Login' to sign in"
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun LoginScreen(
    isLoggingIn: Boolean,
    errorMessage: String?,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon/Logo
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "GeoTracker Logo",
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Title
            Text(
                text = "GeoTracker",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Track your fitness journey",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Login Button
            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoggingIn
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logging in...")
                } else {
                    Text("Login", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Register Button
            OutlinedButton(
                onClick = onRegisterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLoggingIn
            ) {
                Text("Register New Account", fontSize = 16.sp)
            }

            // Error Message
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Info Text
            Text(
                text = "By logging in, you agree to our Terms of Service and Privacy Policy",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
