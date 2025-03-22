package at.co.netconsulting.geotracker.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import at.co.netconsulting.geotracker.data.Metrics
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class CustomLocationListener: LocationListener {
    var startDateTime: LocalDateTime
    private var context: Context
    private var locationManager: LocationManager? = null
    private var oldLatitude: Double = -999.0
    private var oldLongitude: Double = -999.0
    private var coveredDistance: Double = 0.0
    private var lapCounter: Double = 0.0
    private var lap: Int = 0
    private var averageSpeed: Double = 0.0
    private var maxSpeed: Double = 0.0
    private var cumulativeElevationGain: Double = 0.0
    private var startingAltitude: Double? = null
    private var webSocket: WebSocket? = null
    private var websocketserver: String = "0.0.0.0"
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private var height: Float = 0f
    private var weight: Float = 0f
    private var sessionId: String = ""  // Added sessionId field
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)
    private var isReconnecting = false
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 5
    private val BASE_RECONNECT_DELAY_MS = 1000L

    constructor(context: Context) {
        this.context = context
        startDateTime = LocalDateTime.now()
    }

    fun cleanup() {
        stopLocationUpdates()
        job.cancel()
    }

    fun startListener() {
        createLocationManager()
        createLocationUpdates()
        loadSharedPreferences()
        loadSessionId()  // Load sessionId from SharedPreferences
        connectWebSocket()
        //sending data as a test to my websocket server
        //sendTestDataToWebSocketServer(context)
    }

    // Load sessionId from SharedPreferences
    private fun loadSessionId() {
        sessionId = context.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
            .getString("current_session_id", "") ?: ""

        if (sessionId.isEmpty()) {
            Log.e(TAG_WEBSOCKET, "No session ID found in SharedPreferences")
        } else {
            Log.d(TAG_WEBSOCKET, "Loaded session ID: $sessionId")
        }
    }

    fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(this)
            Log.d("CustomLocationListener", "Location updates stopped")

            webSocket?.let { socket ->
                socket.close(1000, "Location updates stopped")
                webSocket = null
            }
        } catch (e: Exception) {
            Log.e("CustomLocationListener", "Error stopping location updates", e)
        }
    }

    private fun loadSharedPreferences() {
        val sharedPreferences = this.context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        firstname = sharedPreferences.getString("firstname", "") ?: ""
        lastname = sharedPreferences.getString("lastname", "") ?: ""
        birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        height = sharedPreferences.getFloat("height", 0f)
        weight = sharedPreferences.getFloat("weight", 0f)
        websocketserver = sharedPreferences.getString("websocketserver", "0.0.0.0").toString()
    }

    private fun createLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this.context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this.context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this.context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Handler(Looper.getMainLooper()).post {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_BETWEEN_UPDATES, this)
        }
    }

    private fun createLocationManager() {
        locationManager = this.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun connectWebSocket(initialConnect: Boolean = true) {
        if (isReconnecting && !initialConnect) {
            reconnectAttempts++
            if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
                Log.e(TAG_WEBSOCKET, "Maximum reconnection attempts reached")
                return
            }
        }

        Log.d(TAG_WEBSOCKET, "Attempting to connect to WebSocket server: ws://$websocketserver:8011/runningtracker")

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://" + websocketserver + ":8011/runningtracker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                Timber.tag(TAG_WEBSOCKET).d("Received binary message: ${bytes.hex()}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Timber.tag(TAG_WEBSOCKET).d("Raw JSON: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Timber.tag(TAG_WEBSOCKET).e(t, "WebSocket connection failed")

                // Handle reconnection with exponential backoff
                if (!isReconnecting && job.isActive) {
                    isReconnecting = true
                    val delayMs = BASE_RECONNECT_DELAY_MS * (1 shl reconnectAttempts.coerceAtMost(10))

                    coroutineScope.launch {
                        delay(delayMs)
                        if (isActive) {
                            connectWebSocket(false)
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection closed: $reason")
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection opened")
                // Reset reconnection state
                isReconnecting = false
                reconnectAttempts = 0
            }
        })
    }

    override fun onLocationChanged(location: Location) {
        if (startingAltitude == null) {
            startingAltitude = location.altitude
            Log.d("LocationTracker", "Starting altitude set: $startingAltitude meters")
        }
        location?.let {
            Log.d("CustomLocationListener", "Latitude: ${location.latitude} / Longitude: ${location.longitude}")

            // Only calculate new metrics if speed is above threshold
            if (checkSpeed(it.speed)) {
                if (oldLatitude != location.latitude || oldLongitude != location.longitude) {
                    Log.d("CustomLocationListener", "New coordinates detected...")

                    val (newCoveredDistance, distanceIncrement) = calculateDistance(it)
                    coveredDistance = newCoveredDistance
                    averageSpeed = calculateAverageSpeed()
                    lap = calculateLap(distanceIncrement)
                    cumulativeElevationGain = calculateCumulativeElevationGain(it, startingAltitude!!)


                    // Update max speed if current speed is higher
                    val currentSpeedKmh = it.speed * 3.6
                    if (currentSpeedKmh > maxSpeed) {
                        maxSpeed = currentSpeedKmh
                    }

                    // Create Metrics object with all required fields
                    val metrics = Metrics(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speed = location.speed * 3.6f,
                        speedAccuracyMetersPerSecond = location.speedAccuracyMetersPerSecond,
                        altitude = location.altitude,
                        horizontalAccuracy = location.accuracy,
                        verticalAccuracyMeters = location.verticalAccuracyMeters,
                        coveredDistance = coveredDistance,
                        lap = lap,
                        startDateTime = startDateTime,
                        averageSpeed = averageSpeed,
                        maxSpeed = maxSpeed,
                        cumulativeElevationGain = cumulativeElevationGain,
                        sessionId = sessionId,
                        person = "$firstname"
                    )

                    // Send data to websocket server
                    sendDataToWebsocketServer(metrics)

                    // Also post through EventBus for UI updates
                    EventBus.getDefault().post(metrics)
                }
            }
        }
    }

    private fun calculateCumulativeElevationGain(location: Location, startingAltitude: Double): Double {
        val altitudeDifference = location.altitude - startingAltitude
        return altitudeDifference
    }

    private fun calculateAverageSpeed(): Double {
        // Get current time and calculate duration since start in seconds
        val currentTime = LocalDateTime.now()
        val durationSeconds = ChronoUnit.SECONDS.between(startDateTime, currentTime).toDouble()

        // Avoid division by zero
        if (durationSeconds <= 0) return 0.0

        // Calculate average speed in meters per second, then convert to km/h
        val avgSpeedMps = coveredDistance / durationSeconds
        val avgSpeedKmh = avgSpeedMps * 3.6

        return if (avgSpeedKmh > 0) avgSpeedKmh else 0.0
    }

    private fun sendDataToWebsocketServer(metrics: Metrics) {
        // Check if sessionId is available
        if (sessionId.isEmpty()) {
            Log.e(TAG_WEBSOCKET, "Cannot send data - missing sessionId")
            loadSessionId() // Try to reload the session ID
            return
        }

        // Convert metrics to JSON
        val gson = Gson()
        val jsonData = gson.toJson(metrics)

        // Send via WebSocket if connection is available
        webSocket?.let { socket ->
            val sent = socket.send(jsonData)
            if (!sent) {
                Log.e(TAG_WEBSOCKET, "Failed to send data through WebSocket")

                // Try to reconnect if send fails
                if (!isReconnecting) {
                    isReconnecting = true
                    coroutineScope.launch {
                        connectWebSocket(false)
                    }
                }
            }
        } ?: run {
            Log.e(TAG_WEBSOCKET, "WebSocket not connected, attempting to reconnect")
            if (!isReconnecting) {
                isReconnecting = true
                coroutineScope.launch {
                    connectWebSocket(false)
                }
            }
        }
    }

    private fun calculateDistance(location: Location): Pair<Double, Double> {
        val distanceIncrement: Double
        if (oldLatitude != -999.0 && oldLongitude != -999.0) {
            distanceIncrement = calculateDistanceBetweenOldLatLngNewLatLng(oldLatitude, oldLongitude, location.latitude, location.longitude)
            coveredDistance += distanceIncrement
        } else {
            distanceIncrement = 0.0
        }
        oldLatitude = location.latitude
        oldLongitude = location.longitude
        Log.d("CustomLocationListener", "Distance Increment: $distanceIncrement")
        return Pair(coveredDistance, distanceIncrement)
    }

    private fun calculateLap(distanceIncrement: Double): Int {
        lapCounter += distanceIncrement
        val lapsToAdd = (lapCounter / 1000).toInt()
        lap += lapsToAdd
        lapCounter -= lapsToAdd * 1000
        return lap
    }

    private fun calculateDistanceBetweenOldLatLngNewLatLng(
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

    private fun checkSpeed(speed: Float): Boolean {
        Log.d("CustomLocationListener", "Speed: $speed m/s")
        val thresholdInMetersPerSecond = MIN_SPEED_THRESHOLD / 3.6
        return speed >= thresholdInMetersPerSecond
    }

    fun sendTestDataToWebSocketServer(context: Context) {
        val websocketserver = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            .getString("websocketserver", "0.0.0.0") ?: "0.0.0.0"

        if (websocketserver == "0.0.0.0") {
            Log.e("WebSocketTest", "WebSocket server not configured")
            Toast.makeText(context, "WebSocket server not configured", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("WebSocketTest", "Attempting to connect to: ws://$websocketserver:8011/runningtracker")

        // Create test metrics data
        val testMetrics = Metrics(
            latitude = 48.2082,
            longitude = 16.3738,
            speed = 10.5f,
            speedAccuracyMetersPerSecond = 0.5f,
            altitude = 170.0,
            horizontalAccuracy = 3.0f,
            verticalAccuracyMeters = 5.0f,
            coveredDistance = 1500.0,
            lap = 1,
            startDateTime = LocalDateTime.now(),
            averageSpeed = 9.8,
            maxSpeed = 12.5,
            cumulativeElevationGain = 25.0,
            sessionId = "test_session_${System.currentTimeMillis()}",
            person = "Test User"
        )

        // Create OkHttp client
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        // Create request
        val request = Request.Builder()
            .url("ws://$websocketserver:8011/runningtracker")
            .build()

        // Connect to WebSocket and send data
        val testWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketTest", "WebSocket connection opened")

                // Send the test data as JSON
                val gson = Gson()
                val jsonData = gson.toJson(testMetrics)

                val sent = webSocket.send(jsonData)
                if (sent) {
                    Log.d("WebSocketTest", "Test data sent successfully: $jsonData")

                    // Show success toast on main thread
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Test data sent to server", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("WebSocketTest", "Failed to send test data")

                    // Show error toast on main thread
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Failed to send test data", Toast.LENGTH_SHORT).show()
                    }
                }

                // Close the WebSocket after sending data
                webSocket.close(1000, "Test completed")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketTest", "WebSocket error: ${t.message}")

                // Show error toast on main thread
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "WebSocket connection error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    companion object {
        private var MIN_TIME_BETWEEN_UPDATES: Long = 1000
        private var MIN_DISTANCE_BETWEEN_UPDATES: Float = 1f
        private const val MIN_SPEED_THRESHOLD: Double = 2.5 // km/h
        const val TAG_WEBSOCKET: String = "CustomLocationListener: WebSocketService"
    }
}