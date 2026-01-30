package at.co.netconsulting.geotracker.sync

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * Garmin Connect API Client
 *
 * Before using, you must register your app at https://developer.garmin.com
 * and configure CONSUMER_KEY and CONSUMER_SECRET
 *
 * Note: Garmin Connect API is more restricted and may require business partnership
 * for full API access. This implementation uses basic authentication.
 */
class GarminConnectApiClient(private val context: Context) {

    companion object {
        private const val TAG = "GarminConnectAPI"

        // TODO: Replace these with your Garmin developer credentials
        private const val CONSUMER_KEY = "YOUR_GARMIN_CONSUMER_KEY"
        private const val CONSUMER_SECRET = "YOUR_GARMIN_CONSUMER_SECRET"

        private const val SSO_LOGIN_URL = "https://sso.garmin.com/sso/signin"
        private const val UPLOAD_URL = "https://connectapi.garmin.com/upload-service/upload"

        private const val PREF_NAME = "garmin_auth"
        private const val KEY_USERNAME = "username"
        private const val KEY_SESSION_COOKIE = "session_cookie"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val okHttpClient = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val cookieStore = mutableMapOf<String, List<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    /**
     * Check if user is authenticated with Garmin Connect
     */
    fun isAuthenticated(): Boolean {
        val username = prefs.getString(KEY_USERNAME, null)
        val sessionCookie = prefs.getString(KEY_SESSION_COOKIE, null)
        return !username.isNullOrEmpty() && !sessionCookie.isNullOrEmpty()
    }

    /**
     * Authenticate with username and password
     * Note: This is a simplified implementation. For production, consider using OAuth 2.0
     */
    suspend fun authenticate(username: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Garmin Connect authentication is complex and requires multiple steps
            // For a complete implementation, you would need to:
            // 1. Get login ticket from SSO
            // 2. Submit credentials
            // 3. Handle MFA if enabled
            // 4. Extract session cookies

            // This is a placeholder - actual implementation would be much more complex
            Log.w(TAG, "Garmin Connect authentication requires SSO flow - not fully implemented")

            // Save credentials (in production, never store plain passwords!)
            prefs.edit().apply {
                putString(KEY_USERNAME, username)
                apply()
            }

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Authentication error", e)
            Result.failure(e)
        }
    }

    /**
     * Disconnect from Garmin Connect
     */
    fun disconnect() {
        prefs.edit().clear().apply()
    }

    /**
     * Upload activity to Garmin Connect
     *
     * @param gpxFile GPX file to upload
     * @param activityName Name of the activity
     * @param activityType Type of activity
     * @return Activity ID if successful
     */
    suspend fun uploadActivity(
        gpxFile: File,
        activityName: String,
        activityType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isAuthenticated()) {
                return@withContext Result.failure(Exception("Not authenticated"))
            }

            // Garmin Connect expects FIT files preferably, but also accepts GPX
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    gpxFile.name,
                    gpxFile.asRequestBody("application/gpx+xml".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Upload response: $responseBody")

                // Parse response to get activity ID
                try {
                    val json = JSONObject(responseBody)
                    val detailedImportResult = json.getJSONObject("detailedImportResult")
                    val activityId = detailedImportResult.getLong("activityId").toString()
                    Result.success(activityId)
                } catch (e: Exception) {
                    // Response might not be JSON
                    Result.success("uploaded")
                }
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Upload failed: ${response.code} - $errorBody")
                Result.failure(Exception("Upload failed: ${response.code}"))
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
     * Map GeoTracker activity type to Garmin activity type
     */
    private fun mapToGarminActivityType(artOfSport: String): String {
        return when (artOfSport.lowercase()) {
            "running", "jogging", "marathon", "trail running", "ultramarathon" -> "running"
            "cycling", "bicycle", "bike", "biking", "gravel bike", "racing bicycle" -> "cycling"
            "mountain bike" -> "mountain_biking"
            "hiking", "mountain hiking", "forest hiking" -> "hiking"
            "walking", "urban walking" -> "walking"
            "swimming - open water" -> "open_water_swimming"
            "swimming - pool" -> "lap_swimming"
            "kayaking" -> "kayaking"
            "canoeing" -> "canoeing"
            "ski", "cross country skiing" -> "cross_country_skiing"
            "snowboard" -> "snowboarding"
            "duathlon", "triathlon", "ultratriathlon", "multisport race" -> "multi_sport"
            else -> "other"
        }
    }
}
