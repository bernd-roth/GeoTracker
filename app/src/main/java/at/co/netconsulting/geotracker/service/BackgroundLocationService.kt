package at.co.netconsulting.geotracker.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import at.co.netconsulting.geotracker.data.LocationData
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.greenrobot.eventbus.EventBus
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * BackgroundLocationService provides location tracking when the app is not actively recording.
 * It sends location updates through EventBus to update the UI (BottomSheet).
 */
class BackgroundLocationService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var startDateTime: LocalDateTime
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private var height: Float = 0f
    private var weight: Float = 0f
    private var websocketserver: String = "0.0.0.0"
    private var webSocket: WebSocket? = null
    private var oldLatitude: Double = -999.0
    private var oldLongitude: Double = -999.0
    private var coveredDistance: Double = 0.0
    private var lap: Int = 0
    private var lapCounter: Double = 0.0
    private var averageSpeed: Double = 0.0
    private var maxSpeed: Double = 0.0
    private var cumulativeElevationGain: Double = 0.0
    private var startingAltitude: Double? = null
    private lateinit var totalDateTime: LocalDateTime
    private var sessionId: String = ""
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isReconnecting = false
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 5
    private val BASE_RECONNECT_DELAY_MS = 1000L
    private var isServiceStarted = false
    private var numberOfSatellites: Int = 0
    private var usedNumberOfSatellites: Int = 0

    override fun onCreate() {
        super.onCreate()
        startDateTime = LocalDateTime.now()
        loadSharedPreferences()
        createSessionId()
        createLocationManager()
        startLocationUpdates()
        //isServiceStarted = true
        //connectWebSocket()

        // Log service start
        Log.d(TAG, "BackgroundLocationService started with sessionId: $sessionId")

        // Start a keep-alive coroutine to ensure regular updates to UI
        startKeepAliveCoroutine()
    }

    private fun createSessionId() {
        // Generate a simple session ID
        sessionId = "bg_${System.currentTimeMillis()}"
        Log.d(TAG, "Created new session ID: $sessionId")
    }

    private fun startKeepAliveCoroutine() {
        serviceScope.launch {
            while (isActive) {
                try {
                    // Send a keep-alive event to ensure UI stays updated
                    // This is helpful when the device is stationary
                    if (oldLatitude != -999.0 && oldLongitude != -999.0) {
                        val locationData = createLocationData()
                        EventBus.getDefault().post(locationData)
                        Log.d(TAG, "Sent keep-alive location update")
                    }
                    delay(KEEP_ALIVE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in keep-alive coroutine", e)
                }
            }
        }
    }

    private fun createLocationData(): LocationData {
        return LocationData(
            latitude = oldLatitude,
            longitude = oldLongitude,
            speed = 0f,
            speedAccuracyMetersPerSecond = 0f,
            altitude = startingAltitude ?: 0.0,
            horizontalAccuracy = 0f,
            verticalAccuracy = 0f,
            coveredDistance = coveredDistance,
            numberOfSatellites = numberOfSatellites,
            usedNumberOfSatellites = usedNumberOfSatellites
        )
    }

    private fun loadSharedPreferences() {
        val sharedPreferences = getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        firstname = sharedPreferences.getString("firstname", "") ?: ""
        lastname = sharedPreferences.getString("lastname", "") ?: ""
        birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        height = sharedPreferences.getFloat("height", 0f)
        weight = sharedPreferences.getFloat("weight", 0f)
        websocketserver = sharedPreferences.getString("websocketserver", "0.0.0.0").toString()
    }

    private fun createLocationManager() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_BETWEEN_UPDATES,
                this
            )
            Log.d(TAG, "Location updates requested")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permissions for location updates.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    private fun connectWebSocket() {
        if (websocketserver == "0.0.0.0") {
            Log.d(TAG, "WebSocket server not configured, skipping connection")
            return
        }

        Log.d(TAG, "Attempting to connect to WebSocket server: ws://$websocketserver:8011/runningtracker")

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://$websocketserver:8011/runningtracker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened")
                // Reset reconnection state
                isReconnecting = false
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket connection failed: ${t.message}")

                // Handle reconnection with exponential backoff
                if (!isReconnecting && serviceJob.isActive) {
                    isReconnecting = true
                    val delayMs = BASE_RECONNECT_DELAY_MS * (1 shl reconnectAttempts.coerceAtMost(10))

                    serviceScope.launch {
                        delay(delayMs)
                        if (isActive) {
                            connectWebSocket()
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket connection closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket connection closed: $reason")
            }
        })
    }

    override fun onLocationChanged(location: Location) {
        try {
            Log.d(
                TAG, "Location Updated: Latitude: ${location.latitude}, " +
                        "Longitude: ${location.longitude}, Accuracy: ${location.accuracy}"
            )

            if (startingAltitude == null) {
                startingAltitude = location.altitude
                Log.d(TAG, "Starting altitude set: $startingAltitude meters")
            }

            // Calculate distance and update metrics
            val (newCoveredDistance, distanceIncrement) = calculateDistance(location)
            coveredDistance = newCoveredDistance
            averageSpeed = calculateAverageSpeed()
            lap = calculateLap(distanceIncrement)

            // Track max speed
            val currentSpeedKmh = location.speed * 3.6
            if (currentSpeedKmh > maxSpeed) {
                maxSpeed = currentSpeedKmh
            }

            // Calculate elevation gain
            if (startingAltitude != null) {
                cumulativeElevationGain = calculateElevationGain(location, startingAltitude!!)
            }

            // Update satellite info - use a simpler approach
            // Modern Android versions no longer expose direct satellite counts easily
            // We'll use location properties instead
            updateSatelliteInfo(location)

            // Create location data object
            val locationData = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                speed = location.speed * 3.6f,
                speedAccuracyMetersPerSecond = location.speedAccuracyMetersPerSecond,
                altitude = location.altitude,
                horizontalAccuracy = location.accuracy,
                verticalAccuracy = location.verticalAccuracyMeters,
                coveredDistance = coveredDistance,
                numberOfSatellites = numberOfSatellites,
                usedNumberOfSatellites = usedNumberOfSatellites
            )

            // Post to EventBus for UI updates
            EventBus.getDefault().post(locationData)

            // Send to WebSocket if configured
//            if (websocketserver != "0.0.0.0") {
//                sendDataToWebsocketServer(locationData)
//            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing location update", e)
        }
    }

    /**
     * Update satellite information based on location quality
     */
    private fun updateSatelliteInfo(location: Location) {
        try {
            // Use location accuracy as a proxy for satellite quality
            // This is more compatible across Android versions
            val accuracy = location.accuracy

            // Estimate satellite information based on location accuracy
            when {
                accuracy < 4 -> {
                    numberOfSatellites = 12
                    usedNumberOfSatellites = 8
                }
                accuracy < 10 -> {
                    numberOfSatellites = 8
                    usedNumberOfSatellites = 5
                }
                accuracy < 20 -> {
                    numberOfSatellites = 6
                    usedNumberOfSatellites = 3
                }
                else -> {
                    numberOfSatellites = 4
                    usedNumberOfSatellites = 0
                }
            }

            // If we have extra information from location extras, use it
            location.extras?.let { extras ->
                if (extras.containsKey("satellites")) {
                    val satCount = extras.getInt("satellites", -1)
                    if (satCount > 0) {
                        numberOfSatellites = satCount
                        // Estimate used satellites as 2/3 of visible ones
                        usedNumberOfSatellites = (satCount * 2 / 3).coerceAtLeast(1)
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error estimating satellite info", e)
            // Default values if we can't get satellite info
            numberOfSatellites = 4
            usedNumberOfSatellites = 0
        }
    }

    private fun sendDataToWebsocketServer(locationData: LocationData) {
        webSocket?.let { socket ->
            try {
                // Convert to JSON
                val gson = Gson()
                val jsonData = gson.toJson(locationData)

                // Send via WebSocket
                val sent = socket.send(jsonData)
                if (!sent) {
                    Log.e(TAG, "Failed to send data through WebSocket")

                    // Try to reconnect if send fails
                    if (!isReconnecting) {
                        isReconnecting = true
                        serviceScope.launch {
                            connectWebSocket()
                        }
                    } else {

                    }
                } else {

                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data to WebSocket", e)
            }
        }
    }

    private fun calculateElevationGain(location: Location, startingAltitude: Double): Double {
        val altitudeDifference = location.altitude - startingAltitude
        return if (altitudeDifference > 0) altitudeDifference else 0.0
    }

    private fun calculateLap(distanceIncrement: Double): Int {
        lapCounter += distanceIncrement
        val lapsToAdd = (lapCounter / 1000).toInt()
        lap += lapsToAdd
        lapCounter -= lapsToAdd * 1000
        return lap
    }

    private fun calculateAverageSpeed(): Double {
        return try {
            val currentTime = LocalDateTime.now()
            val durationSeconds = Duration.between(startDateTime, currentTime).seconds.toDouble()

            // Check for division by zero or very small values
            if (durationSeconds > 0.001) {  // Using a small threshold instead of exactly 0
                (coveredDistance / durationSeconds) * 3.6
            } else {
                0.0  // Return 0 if duration is too small
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating average speed", e)
            0.0  // Return 0 in case of any error
        }
    }

    private fun calculateDistance(location: Location): Pair<Double, Double> {
        val distanceIncrement: Double
        if (checkSpeed(location)) {
            if (oldLatitude != -999.0 && oldLongitude != -999.0) {
                distanceIncrement = calculateDistanceBetweenCoordinates(
                    oldLatitude,
                    oldLongitude,
                    location.latitude,
                    location.longitude
                )
                coveredDistance += distanceIncrement
            } else {
                distanceIncrement = 0.0
            }
            oldLatitude = location.latitude
            oldLongitude = location.longitude
            Log.d(TAG, "Distance Increment: $distanceIncrement")
            return Pair(coveredDistance, distanceIncrement)
        } else {
            // Still update the coordinates even if we're not moving fast enough
            oldLatitude = location.latitude
            oldLongitude = location.longitude
            return Pair(coveredDistance, 0.0)
        }
    }

    private fun checkSpeed(location: Location): Boolean {
        val speed = location.speed
        val thresholdInMetersPerSecond = MIN_SPEED_THRESHOLD / 3.6
        return speed >= thresholdInMetersPerSecond
    }

    private fun calculateDistanceBetweenCoordinates(
        oldLatitude: Double,
        oldLongitude: Double,
        newLatitude: Double,
        newLongitude: Double
    ): Double {
        val result = FloatArray(1)
        Location.distanceBetween(
            oldLatitude,
            oldLongitude,
            newLatitude,
            newLongitude,
            result
        )
        return result[0].toDouble()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(TAG, "Status changed for provider $provider: $status")
        Log.d(TAG, "Provider status code: $status")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "Provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "Provider disabled: $provider")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "BackgroundLocationService destroying")

        try {
            // Remove location updates
            locationManager.removeUpdates(this)
            Log.d(TAG, "Location updates removed")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }

        try {
            // Close WebSocket
            webSocket?.let { socket ->
                socket.close(1000, "Service shutting down")
                webSocket = null
                Log.d(TAG, "WebSocket closed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        }

        try {
            // Cancel all coroutines
            serviceScope.cancel()
            Log.d(TAG, "Service scope cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling service scope", e)
        }

        isServiceStarted = false
    }

    companion object {
        private const val MIN_TIME_BETWEEN_UPDATES: Long = 1000
        private const val MIN_DISTANCE_BETWEEN_UPDATES: Float = 1f
        private const val TAG: String = "BackgroundLocationService"
        private const val MIN_SPEED_THRESHOLD: Double = 2.5 // km/h
        private const val KEEP_ALIVE_INTERVAL: Long = 5000 // 5 seconds
    }
}