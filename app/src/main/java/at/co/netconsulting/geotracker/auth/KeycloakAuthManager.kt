package at.co.netconsulting.geotracker.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import net.openid.appauth.*
import timber.log.Timber

/**
 * Manages Keycloak authentication using AppAuth library
 */
class KeycloakAuthManager(private val context: Context) {

    companion object {
        // Keycloak Configuration
        private const val KEYCLOAK_URL = "https://geotracker.duckdns.org/auth"
        private const val REALM = "geotracker"
        private const val CLIENT_ID = "geotracker-android"
        private const val REDIRECT_URI = "at.co.netconsulting.geotracker:/oauth2redirect"

        // Endpoints
        private const val AUTH_ENDPOINT = "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/auth"
        private const val TOKEN_ENDPOINT = "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token"
        private const val END_SESSION_ENDPOINT = "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/logout"

        // SharedPreferences keys
        private const val PREFS_NAME = "keycloak_auth"
        private const val KEY_AUTH_STATE = "auth_state"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var authState: AuthState? = null

    init {
        // Restore auth state from SharedPreferences
        restoreAuthState()
    }

    /**
     * Create authorization request and return intent for login
     */
    fun getAuthorizationIntent(): Intent {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(AUTH_ENDPOINT),
            Uri.parse(TOKEN_ENDPOINT),
            null,
            Uri.parse(END_SESSION_ENDPOINT)
        )

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        ).setScopes("openid", "profile", "email")
            .build()

        val authService = AuthorizationService(context)
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handle authorization response from Keycloak
     */
    fun handleAuthorizationResponse(
        intent: Intent,
        onSuccess: (accessToken: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val authResponse = AuthorizationResponse.fromIntent(intent)
        val authException = AuthorizationException.fromIntent(intent)

        if (authResponse != null) {
            Timber.d("Authorization successful, exchanging code for token...")

            val authService = AuthorizationService(context)
            authState = AuthState(authResponse, authException)

            authService.performTokenRequest(authResponse.createTokenExchangeRequest()) { tokenResponse, tokenException ->
                if (tokenResponse != null) {
                    authState?.update(tokenResponse, tokenException)
                    saveAuthState()

                    val accessToken = tokenResponse.accessToken
                    if (accessToken != null) {
                        Timber.d("Access token obtained successfully")
                        onSuccess(accessToken)
                    } else {
                        onError("Access token is null")
                    }
                } else {
                    Timber.e(tokenException, "Token exchange failed")
                    onError(tokenException?.message ?: "Token exchange failed")
                }
            }
        } else {
            val errorMessage = authException?.message ?: "Unknown authorization error"
            Timber.e("Authorization failed: $errorMessage")
            onError(errorMessage)
        }
    }

    /**
     * Get current access token, refreshing if necessary
     */
    fun getAccessToken(callback: (String?) -> Unit) {
        Timber.d("getAccessToken called")
        Timber.d("authState is ${if (authState != null) "NOT NULL" else "NULL"}")

        authState?.let { state ->
            Timber.d("authState.isAuthorized = ${state.isAuthorized}")
            Timber.d("authState.accessToken = ${if (state.accessToken != null) "present (${state.accessToken?.length} chars)" else "NULL"}")
            Timber.d("authState.refreshToken = ${if (state.refreshToken != null) "present" else "NULL"}")

            if (!state.isAuthorized) {
                Timber.w("Not authorized, need to login first")
                callback(null)
                return
            }

            state.performActionWithFreshTokens(AuthorizationService(context)) { accessToken, _, exception ->
                if (exception != null) {
                    Timber.e(exception, "Failed to get fresh token")
                    callback(null)
                } else {
                    Timber.d("Got fresh token: ${accessToken?.take(20)}...")
                    saveAuthState()
                    callback(accessToken)
                }
            }
        } ?: run {
            Timber.w("No auth state, need to login first")
            // Check if SharedPreferences has the data
            val savedState = prefs.getString(KEY_AUTH_STATE, null)
            Timber.w("SharedPreferences KEY_AUTH_STATE is ${if (savedState != null) "present (${savedState.length} chars)" else "NULL"}")
            callback(null)
        }
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return authState?.isAuthorized == true
    }

    /**
     * Get user info from token
     */
    fun getUserInfo(): Map<String, String>? {
        return authState?.idToken?.let { idToken ->
            try {
                // Parse JWT token (basic parsing without verification)
                val parts = idToken.split(".")
                if (parts.size == 3) {
                    val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                    Timber.d("Token payload: $payload")
                    // You can parse this JSON to extract user info
                    mapOf("raw" to payload)
                }
                else null
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse ID token")
                null
            }
        }
    }

    /**
     * Logout and clear auth state
     */
    fun logout() {
        authState = null
        prefs.edit().remove(KEY_AUTH_STATE).apply()
        Timber.d("Logged out and cleared auth state")
    }

    /**
     * Save auth state to SharedPreferences
     */
    private fun saveAuthState() {
        authState?.let { state ->
            val stateJson = state.jsonSerializeString()
            val success = prefs.edit()
                .putString(KEY_AUTH_STATE, stateJson)
                .commit()  // Use commit() instead of apply() to ensure sync write
            Timber.d("Auth state saved: success=$success, length=${stateJson.length}")
        }
    }

    /**
     * Restore auth state from SharedPreferences
     */
    private fun restoreAuthState() {
        val stateJson = prefs.getString(KEY_AUTH_STATE, null)
        Timber.d("restoreAuthState: stateJson is ${if (stateJson != null) "present (${stateJson.length} chars)" else "NULL"}")

        if (stateJson != null) {
            try {
                authState = AuthState.jsonDeserialize(stateJson)
                Timber.d("Auth state restored: isAuthorized=${authState?.isAuthorized}, hasAccessToken=${authState?.accessToken != null}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore auth state")
                prefs.edit().remove(KEY_AUTH_STATE).apply()
            }
        } else {
            Timber.d("No auth state found in SharedPreferences")
        }
    }
}
