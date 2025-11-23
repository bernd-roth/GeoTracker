package at.co.netconsulting.geotracker.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.*
import okhttp3.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Strava API Client for OAuth authentication and activity upload
 *
 * Before using, you must register your app at https://www.strava.com/settings/api
 * and configure CLIENT_ID, CLIENT_SECRET, and REDIRECT_URI
 */
class StravaApiClient(private val context: Context) {

    companion object {
        private const val TAG = "StravaApiClient"

        // TODO: Replace these with your Strava app credentials from https://www.strava.com/settings/api
        private const val CLIENT_ID = "185662"
        private const val CLIENT_SECRET = "d66f914e8840deaea63bfc116b05858db5854512"
        private const val REDIRECT_URI = "http://localhost/exchange_token"

        private const val AUTH_ENDPOINT = "https://www.strava.com/oauth/authorize"
        private const val TOKEN_ENDPOINT = "https://www.strava.com/oauth/token"
        private const val UPLOAD_ENDPOINT = "https://www.strava.com/api/v3/uploads"

        private const val PREF_NAME = "strava_auth"
        private const val KEY_ACCESS_TOKEN = "9dc49b4e2480970bd7c7c09e0b3915b3e6a00d21"
        private const val KEY_REFRESH_TOKEN = "997d5c47904fc8035ebdc4e62a050051887548f4"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val okHttpClient = OkHttpClient()

    /**
     * Check if user is authenticated with Strava
     */
    fun isAuthenticated(): Boolean {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
        return !accessToken.isNullOrEmpty() && System.currentTimeMillis() < expiry
    }

    /**
     * Get stored access token (refreshes if needed)
     */
    suspend fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)

        // If token is expired, try to refresh
        if (token != null && System.currentTimeMillis() >= expiry) {
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            if (refreshToken != null) {
                return refreshAccessToken(refreshToken)
            }
        }

        return token
    }

    /**
     * Create OAuth authorization intent
     */
    fun createAuthorizationIntent(): Intent {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(AUTH_ENDPOINT),
            Uri.parse(TOKEN_ENDPOINT)
        )

        val authRequest = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI)
        ).setScope("activity:write,activity:read_all")
            .build()

        val authService = AuthorizationService(context)
        return authService.getAuthorizationRequestIntent(authRequest)
    }

    /**
     * Handle authorization response and exchange code for token
     */
    suspend fun handleAuthorizationResponse(intent: Intent): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = AuthorizationResponse.fromIntent(intent)
            val error = AuthorizationException.fromIntent(intent)

            if (error != null) {
                Log.e(TAG, "Authorization error: ${error.message}")
                return@withContext Result.failure(Exception("Authorization failed: ${error.message}"))
            }

            if (response == null) {
                return@withContext Result.failure(Exception("No authorization response"))
            }

            // Exchange authorization code for access token
            val authService = AuthorizationService(context)
            val tokenRequest = response.createTokenExchangeRequest()

            try {
                val tokenResponse = exchangeCodeForToken(authService, tokenRequest)
                tokenResponse?.let {
                    saveTokens(it.accessToken!!, it.refreshToken, it.accessTokenExpirationTime!!)
                    Result.success(it.accessToken!!)
                } ?: Result.failure(Exception("Token exchange failed"))
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange error", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling authorization", e)
            Result.failure(e)
        }
    }

    /**
     * Handle manual authorization URL (when redirect doesn't work)
     */
    suspend fun handleManualAuthorizationUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Parse the URL to extract the authorization code
            val uri = Uri.parse(url)
            val code = uri.getQueryParameter("code")
                ?: return@withContext Result.failure(Exception("No authorization code found in URL"))

            // Exchange code for access token using direct HTTP request
            val requestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code", code)
                .add("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", "")
                val expiresAt = json.getLong("expires_at") * 1000 // Convert to milliseconds

                saveTokens(accessToken, refreshToken, expiresAt)
                Log.d(TAG, "Manual authorization successful")
                Result.success(accessToken)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Token exchange failed: ${response.code} - $errorBody")
                Result.failure(Exception("Token exchange failed: ${response.code} - $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling manual authorization", e)
            Result.failure(e)
        }
    }

    /**
     * Exchange authorization code for token (blocking call)
     */
    private suspend fun exchangeCodeForToken(
        authService: AuthorizationService,
        tokenRequest: TokenRequest
    ): TokenResponse? = withContext(Dispatchers.IO) {
        try {
            var result: TokenResponse? = null
            var error: Exception? = null

            val latch = java.util.concurrent.CountDownLatch(1)

            authService.performTokenRequest(tokenRequest) { response, exception ->
                result = response
                error = exception?.let { Exception(it.message) }
                latch.countDown()
            }

            latch.await()

            if (error != null) {
                throw error!!
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            null
        }
    }

    /**
     * Refresh access token using refresh token
     */
    private suspend fun refreshAccessToken(refreshToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val accessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                val expiresAt = json.getLong("expires_at") * 1000 // Convert to milliseconds

                saveTokens(accessToken, newRefreshToken, expiresAt)
                accessToken
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            null
        }
    }

    /**
     * Save authentication tokens to SharedPreferences
     */
    private fun saveTokens(accessToken: String, refreshToken: String?, expiryTime: Long) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
            putLong(KEY_TOKEN_EXPIRY, expiryTime)
            apply()
        }
    }

    /**
     * Disconnect from Strava (clear tokens)
     */
    fun disconnect() {
        prefs.edit().clear().apply()
    }

    /**
     * Upload activity to Strava
     *
     * @param gpxFile GPX file to upload
     * @param activityName Name of the activity
     * @param activityType Type of activity (e.g., "Run", "Ride", "Hike")
     * @param description Optional description
     * @return Upload ID if successful
     */
    suspend fun uploadActivity(
        gpxFile: File,
        activityName: String,
        activityType: String,
        description: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            // Map activity type to Strava activity type
            val stravaType = mapToStravaActivityType(activityType)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    gpxFile.name,
                    gpxFile.asRequestBody("application/gpx+xml".toMediaTypeOrNull())
                )
                .addFormDataPart("name", activityName)
                .addFormDataPart("activity_type", stravaType)
                .apply {
                    description?.let { addFormDataPart("description", it) }
                }
                .addFormDataPart("data_type", "gpx")
                .build()

            val request = Request.Builder()
                .url(UPLOAD_ENDPOINT)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val uploadId = json.optString("id_str", json.optString("id", "unknown"))
                Log.d(TAG, "Upload accepted! Upload ID: $uploadId - checking status...")

                // Poll upload status to check if Strava accepted it
                val statusResult = checkUploadStatus(uploadId)
                return@withContext statusResult
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Upload failed: ${response.code} - $errorBody")
                Result.failure(Exception("Upload failed: ${response.code} - $errorBody"))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during upload", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading activity", e)
            Result.failure(e)
        }
    }

    /**
     * Check upload status by polling Strava's upload endpoint
     * Strava processes uploads asynchronously, so we need to check the status
     */
    private suspend fun checkUploadStatus(uploadId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            // Poll up to 30 times with 2 second intervals (60 seconds total)
            repeat(30) { attempt ->
                kotlinx.coroutines.delay(2000) // Wait 2 seconds between checks

                val request = Request.Builder()
                    .url("$UPLOAD_ENDPOINT/$uploadId")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val status = json.optString("status", "")
                    val activityId = json.optString("activity_id", "")
                    val error = json.optString("error", "")

                    Log.d(TAG, "Upload status check #$attempt: status=$status, activityId=$activityId, error=$error")

                    when (status) {
                        "Your activity is ready." -> {
                            Log.d(TAG, "Upload successful! Activity ID: $activityId")
                            return@withContext Result.success(activityId)
                        }
                        "Your activity is still being processed." -> {
                            // Continue polling
                            Log.d(TAG, "Still processing... (attempt ${attempt + 1}/30)")
                        }
                        "There was an error processing your activity." -> {
                            val errorMsg = if (error.isNotEmpty()) error else "Strava rejected the activity during processing"
                            Log.e(TAG, "Strava processing error: $errorMsg")
                            return@withContext Result.failure(Exception("Strava error: $errorMsg"))
                        }
                        else -> {
                            Log.w(TAG, "Unknown status: $status")
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to check status: ${response.code}")
                }
            }

            // Timeout after 30 attempts
            Result.failure(Exception("Upload status check timed out after 60 seconds"))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking upload status", e)
            Result.failure(e)
        }
    }

    /**
     * Map GeoTracker activity type to Strava activity type
     */
    private fun mapToStravaActivityType(artOfSport: String): String {
        return when (artOfSport.lowercase()) {
            "running", "jogging", "marathon", "trail running", "ultramarathon", "road running" -> "Run"
            "cycling", "bicycle", "bike", "biking", "gravel bike", "racing bicycle" -> "Ride"
            "mountain bike" -> "MountainBikeRide"
            "e-bike" -> "EBikeRide"
            "hiking", "mountain hiking", "forest hiking" -> "Hike"
            "walking", "urban walking" -> "Walk"
            "nordic walking" -> "NordicSki"
            "swimming - open water" -> "Swim"
            "swimming - pool" -> "Swim"
            "kayaking" -> "Kayaking"
            "canoeing" -> "Canoeing"
            "stand up paddleboarding" -> "StandUpPaddling"
            "ski", "cross country skiing" -> "NordicSki"
            "ski touring" -> "BackcountrySki"
            "snowboard" -> "Snowboard"
            "ice skating" -> "IceSkate"
            "snowshoeing" -> "Snowshoe"
            "soccer" -> "Soccer"
            "tennis" -> "Tennis"
            "basketball" -> "Basketball"
            "volleyball" -> "Volleyball"
            else -> "Workout" // Default fallback
        }
    }
}
