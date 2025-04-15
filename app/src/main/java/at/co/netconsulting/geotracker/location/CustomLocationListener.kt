package at.co.netconsulting.geotracker.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.tools.LocalDateTimeAdapter
import com.google.gson.GsonBuilder
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
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

class CustomLocationListener: LocationListener {
    var startDateTime: LocalDateTime = LocalDateTime.now()
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
    private var sessionId: String = ""
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)
    private var isReconnecting = false
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 5
    private val BASE_RECONNECT_DELAY_MS = 1000L

    //moving average speed
    private val speedBuffer = LinkedList<Double>()
    private val SPEED_BUFFER_SIZE = 5
    private var movingAverageSpeed: Double = 0.0

    // Satellite information
    private var numberOfSatellites: Int = 0
    private var usedNumberOfSatellites: Int = 0

    // reconnection logic, if Internet connection gets lost
    private var lastMessageTime: Long = System.currentTimeMillis()
    private var healthCheckJob: Job? = null
    private var isWebSocketConnected = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Location update parameters from settings
    private var minTimeBetweenUpdates: Long = 1000 // Default 1 second
    private var minDistanceBetweenUpdates: Float = 1f // Default 1 meter

    // Voice announcement parameters
    private var voiceAnnouncementInterval: Int = 1
    private var lastAnnouncedKilometer: Int = 0
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    constructor(context: Context) {
        this.context = context
        startDateTime = LocalDateTime.now()
        Log.d(TAG_WEBSOCKET, "CustomLocationListener created with startDateTime: $startDateTime")

        // Initialize Text-to-Speech
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG_VOICE_ANNOUNCEMENT, "Language not supported for TTS")
                } else {
                    isTtsInitialized = true
                    Log.d(TAG_VOICE_ANNOUNCEMENT, "TTS initialized successfully")
                }
            } else {
                Log.e(TAG_VOICE_ANNOUNCEMENT, "TTS initialization failed with status: $status")
            }
        }
    }

    fun cleanup() {
        stopLocationUpdates()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                networkCallback?.let {
                    connectivityManager?.unregisterNetworkCallback(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_WEBSOCKET, "Error unregistering network callback", e)
        }

        // Shutdown TTS
        textToSpeech?.stop()
        textToSpeech?.shutdown()

        healthCheckJob?.cancel()
        job.cancel()
    }

    fun startListener() {
        createLocationManager()
        loadSharedPreferences() // Load preferences including update settings
        createLocationUpdates()
        loadSessionId()  // Load sessionId from SharedPreferences
        registerNetworkCallback()
        connectWebSocket()
        //sending data as a test to my websocket server
        //sendTestDataToWebSocketServer(context)
    }

    fun reloadSettings() {
        // Load updated settings
        loadSharedPreferences()

        // Stop current updates
        try {
            locationManager?.removeUpdates(this)
        } catch (e: Exception) {
            Log.e(TAG_WEBSOCKET, "Error removing location updates", e)
        }

        // Restart with new settings
        createLocationUpdates()

        Log.d(
            TAG_WEBSOCKET,
            "Location settings reloaded: minTime=$minTimeBetweenUpdates ms, minDistance=$minDistanceBetweenUpdates m"
        )
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
        val sharedPreferences =
            this.context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        firstname = sharedPreferences.getString("firstname", "") ?: ""
        lastname = sharedPreferences.getString("lastname", "") ?: ""
        birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        height = sharedPreferences.getFloat("height", 0f)
        weight = sharedPreferences.getFloat("weight", 0f)
        websocketserver = sharedPreferences.getString("websocketserver", "0.0.0.0").toString()

        // Load location update settings from preferences
        val minTimeSeconds = sharedPreferences.getInt("minTimeSeconds", 1)
        val minDistanceMeters = sharedPreferences.getInt("minDistanceMeters", 1)

        // Load voice announcement interval
        voiceAnnouncementInterval = sharedPreferences.getInt("voiceAnnouncementInterval", 1)

        // Reset the last announced kilometer when loading settings
        lastAnnouncedKilometer = 0

        // Convert to appropriate units
        minTimeBetweenUpdates = minTimeSeconds * 1000L
        minDistanceBetweenUpdates = minDistanceMeters.toFloat()

        Log.d(
            TAG_WEBSOCKET,
            "Loaded location update settings: minTime=$minTimeBetweenUpdates ms, minDistance=$minDistanceBetweenUpdates m"
        )
        Log.d(
            TAG_VOICE_ANNOUNCEMENT,
            "Loaded voice announcement interval: $voiceAnnouncementInterval km"
        )
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
            // Use the values loaded from SharedPreferences
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minTimeBetweenUpdates,
                minDistanceBetweenUpdates,
                this
            )
            Log.d(
                TAG_WEBSOCKET,
                "Started location updates with minTime=$minTimeBetweenUpdates ms, minDistance=$minDistanceBetweenUpdates m"
            )
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

        Log.d(
            TAG_WEBSOCKET,
            "Attempting to connect to WebSocket server: ws://$websocketserver:8011/geotracker"
        )

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://" + websocketserver + ":8011/geotracker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                Timber.tag(TAG_WEBSOCKET).d("Received binary message: ${bytes.hex()}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                lastMessageTime = System.currentTimeMillis() // Update last message time
                Timber.tag(TAG_WEBSOCKET).d("Raw JSON: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                isWebSocketConnected = false
                Timber.tag(TAG_WEBSOCKET).e(t, "WebSocket connection failed")

                // Handle reconnection with exponential backoff
                reconnectWebSocket()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                isWebSocketConnected = false
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection closed: $reason")
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isWebSocketConnected = true
                lastMessageTime = System.currentTimeMillis()
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection opened")
                // Reset reconnection state
                isReconnecting = false
                reconnectAttempts = 0

                // Start the health check when connection opens
                startWebSocketHealthCheck()
            }
        })
    }

    override fun onLocationChanged(location: Location) {
        if (startingAltitude == null) {
            startingAltitude = location.altitude
            Log.d("LocationTracker", "Starting altitude set: $startingAltitude meters")
        }

        // Update satellite information with each location update
        updateSatelliteInfo(location)

        val satelliteCount = if (location.extras != null) {
            location.extras!!.getInt("satellites", 0)
        } else {
            0
        }

        location?.let {
            Log.d(
                "CustomLocationListener",
                "Latitude: ${location.latitude} / Longitude: ${location.longitude}"
            )

            // Calculate distance only if speed is above threshold
            var distanceIncrement = 0.0
            if (checkSpeed(it.speed)) {
                if (oldLatitude != -999.0 && oldLongitude != -999.0 &&
                    (oldLatitude != location.latitude || oldLongitude != location.longitude)
                ) {
                    Log.d("CustomLocationListener", "New coordinates detected...")

                    // Calculate distance increment
                    distanceIncrement = calculateDistanceBetweenOldLatLngNewLatLng(
                        oldLatitude, oldLongitude, location.latitude, location.longitude
                    )
                    coveredDistance += distanceIncrement
                    lap = calculateLap(distanceIncrement)

                    // Check for distance milestone announcement
                    checkDistanceMilestone()
                }
            }

            // Always update coordinates for next calculation
            oldLatitude = location.latitude
            oldLongitude = location.longitude

            // Always calculate these metrics regardless of speed
            averageSpeed = calculateAverageSpeed()
            cumulativeElevationGain = calculateCumulativeElevationGain(it, startingAltitude!!)

            // Update max speed if current speed is higher
            val currentSpeedKmh = it.speed * 3.6
            if (currentSpeedKmh > maxSpeed) {
                maxSpeed = currentSpeedKmh
            }

            // Calculate moving average speed
            movingAverageSpeed = calculateMovingAverageSpeed(currentSpeedKmh)

            // Create Metrics object with all required fields including satellite info
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
                movingAverageSpeed = movingAverageSpeed,
                cumulativeElevationGain = cumulativeElevationGain,
                sessionId = sessionId,
                person = firstname,
                numberOfSatellites = numberOfSatellites,
                usedNumberOfSatellites = usedNumberOfSatellites,
                satellites = satelliteCount
            )

            // Send data to websocket server (always, regardless of speed)
            sendDataToWebsocketServer(metrics)

            // Also post through EventBus for UI updates (always, regardless of speed)
            EventBus.getDefault().post(metrics)
        }
    }

    /**
     * Checks if the current distance has reached a milestone based on the voice announcement interval
     * and makes a voice announcement if needed
     */
    private fun checkDistanceMilestone() {
        if (voiceAnnouncementInterval <= 0 || !isTtsInitialized) return

        // Convert total distance to kilometers
        val totalDistanceKm = coveredDistance / 1000.0

        // Calculate current milestone (how many intervals have passed)
        val currentMilestone = (totalDistanceKm / voiceAnnouncementInterval).toInt()

        // If we've reached a new milestone
        if (currentMilestone > lastAnnouncedKilometer) {
            lastAnnouncedKilometer = currentMilestone
            val reachedDistance = currentMilestone * voiceAnnouncementInterval

            // Announce the milestone
            announceMessage("You've reached $reachedDistance kilometers")

            Log.d(TAG_VOICE_ANNOUNCEMENT, "Distance milestone announced: $reachedDistance km")
        }
    }

    /**
     * Uses TextToSpeech to speak the given message
     */
    private fun announceMessage(message: String) {
        if (!isTtsInitialized) return

        val queueMode = TextToSpeech.QUEUE_FLUSH  // Interrupt any current TTS
        textToSpeech?.speak(message, queueMode, null, "milestone_${System.currentTimeMillis()}")

        // Also show a Toast message for visual feedback
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateCumulativeElevationGain(
        location: Location,
        startingAltitude: Double
    ): Double {
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

        // Convert metrics to JSON using a properly configured Gson instance
        val gson = GsonBuilder()
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()

        val jsonData = gson.toJson(metrics)

        Log.d(TAG_WEBSOCKET, "Sending data: $jsonData")

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
            distanceIncrement = calculateDistanceBetweenOldLatLngNewLatLng(
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
        Log.d("CustomLocationListener", "Distance Increment: ${result[0]}")
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

        Log.d("WebSocketTest", "Attempting to connect to: ws://$websocketserver:8011/geotracker")

        // Create test metrics data
        val testMetrics = Metrics(
            latitude = 48.4855,
            longitude = 18.3738,
            speed = 11.5f,
            speedAccuracyMetersPerSecond = 0.5f,
            altitude = 170.0,
            horizontalAccuracy = 3.0f,
            verticalAccuracyMeters = 5.0f,
            coveredDistance = 1500.0,
            lap = 1,
            startDateTime = LocalDateTime.now(),
            averageSpeed = 9.8,
            maxSpeed = 12.5,
            movingAverageSpeed = 10.0,
            cumulativeElevationGain = 25.0,
            sessionId = "test_session_${System.currentTimeMillis()}",
            person = "Test_User"
        )

        // Create OkHttp client
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        // Create request
        val request = Request.Builder()
            .url("ws://$websocketserver:8011/geotracker")
            .build()

        // Connect to WebSocket and send data
        val testWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketTest", "WebSocket connection opened")

                // Send the test data as JSON
                val gson = GsonBuilder()
                    .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
                    .create()
                val jsonData = gson.toJson(testMetrics)

                val sent = webSocket.send(jsonData)
                if (sent) {
                    Log.d("WebSocketTest", "Test data sent successfully: $jsonData")

                    // Show success toast on main thread
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Test data sent to server", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Log.e("WebSocketTest", "Failed to send test data")

                    // Show error toast on main thread
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Failed to send test data", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                // Close the WebSocket after sending data
                webSocket.close(1000, "Test completed")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketTest", "WebSocket error: ${t.message}")

                // Show error toast on main thread
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "WebSocket connection error: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    //moving average speed calculation
    private fun calculateMovingAverageSpeed(currentSpeed: Double): Double {
        speedBuffer.add(currentSpeed)

        if (speedBuffer.size > SPEED_BUFFER_SIZE) {
            speedBuffer.removeFirst()
        }

        val average = if (speedBuffer.isNotEmpty()) {
            speedBuffer.sum() / speedBuffer.size
        } else {
            0.0
        }
        return average
    }

    private fun updateSatelliteInfo(location: Location) {
        try {
            // Use location accuracy as a proxy for satellite quality
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

            Log.d(
                TAG_WEBSOCKET,
                "Updated satellite info: $numberOfSatellites visible, $usedNumberOfSatellites used"
            )

        } catch (e: Exception) {
            Log.e(TAG_WEBSOCKET, "Error estimating satellite info", e)
            // Default values if we can't get satellite info
            numberOfSatellites = 4
            usedNumberOfSatellites = 0
        }
    }

    private fun startWebSocketHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = coroutineScope.launch {
            while (isActive) {
                try {
                    delay(WEBSOCKET_HEALTH_CHECK_INTERVAL)

                    // Check if we haven't received a message for too long
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastMessageTime > WEBSOCKET_HEALTH_CHECK_INTERVAL * 2) {
                        Log.w(
                            TAG_WEBSOCKET,
                            "WebSocket may be disconnected - no messages received recently"
                        )
                        if (isWebSocketConnected) {
                            Log.d(TAG_WEBSOCKET, "Reconnecting due to inactivity...")
                            isWebSocketConnected = false
                            reconnectWebSocket()
                        }
                    }

                    // Send a ping to keep the connection alive
                    webSocket?.let { socket ->
                        val sent = socket.send("ping")
                        if (!sent) {
                            Log.w(TAG_WEBSOCKET, "Failed to send ping, connection may be lost")
                            isWebSocketConnected = false
                            reconnectWebSocket()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG_WEBSOCKET, "Error in WebSocket health check", e)
                }
            }
        }
    }

    private fun reconnectWebSocket() {
        if (job.isActive) {
            // If we've tried too many times in a row, reset the counter but keep trying
            if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS_BEFORE_RESET) {
                Log.w(TAG_WEBSOCKET, "Reset reconnection counter after $reconnectAttempts attempts")
                reconnectAttempts = 0
            }

            isReconnecting = true
            val delayMs = BASE_RECONNECT_DELAY_MS * (1 shl reconnectAttempts.coerceAtMost(10))

            Log.d(
                TAG_WEBSOCKET,
                "Will attempt to reconnect in ${delayMs}ms (attempt #${reconnectAttempts + 1})"
            )

            coroutineScope.launch {
                delay(delayMs)
                if (isActive && !isWebSocketConnected) {
                    Log.d(TAG_WEBSOCKET, "Attempting reconnection #${reconnectAttempts + 1}")
                    connectWebSocket(false)
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val networkRequest = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d(TAG_WEBSOCKET, "Network available, checking WebSocket connection")

                        // Check if we need to reconnect
                        if (!isWebSocketConnected) {
                            Log.d(TAG_WEBSOCKET, "Network restored, reconnecting WebSocket")
                            reconnectAttempts = 0  // Reset counter on new network
                            isReconnecting = false
                            connectWebSocket(true)
                        }
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.d(TAG_WEBSOCKET, "Network lost")
                        isWebSocketConnected = false
                    }
                }

                connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
                Log.d(TAG_WEBSOCKET, "Registered network callback")
            } else {
                // For older Android versions
                val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                context.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val isConnected =
                            connectivityManager?.activeNetworkInfo?.isConnected == true
                        Log.d(TAG_WEBSOCKET, "Network connectivity changed: connected=$isConnected")

                        if (isConnected && !isWebSocketConnected) {
                            Log.d(TAG_WEBSOCKET, "Network restored, reconnecting WebSocket")
                            reconnectAttempts = 0
                            isReconnecting = false
                            connectWebSocket(true)
                        }
                    }
                }, intentFilter)
            }
        } catch (e: Exception) {
            Log.e(TAG_WEBSOCKET, "Error registering network callback", e)
        }
    }

    fun hasValidSession(): Boolean {
        // Check if sessionId is valid and connection is active
        return sessionId.isNotEmpty() && isWebSocketConnected
    }

    /**
     * Test the voice announcement system
     * This can be called from outside to verify that TTS is working
     */
    fun testVoiceAnnouncement() {
        if (!isTtsInitialized) {
            // Try to initialize if not already done
            initTextToSpeech()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Initializing Text-to-Speech...", Toast.LENGTH_SHORT).show()
            }

            // Wait a moment for initialization
            Handler(Looper.getMainLooper()).postDelayed({
                if (isTtsInitialized) {
                    announceMessage("Voice announcement system is working. You'll receive updates every $voiceAnnouncementInterval kilometers.")
                } else {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }, 1000)
        } else {
            announceMessage("Voice announcement system is working. You'll receive updates every $voiceAnnouncementInterval kilometers.")
        }
    }

    companion object {
        // These static variables are no longer used directly
        private const val MIN_SPEED_THRESHOLD: Double = 2.5 // km/h
        const val TAG_WEBSOCKET: String = "CustomLocationListener: WebSocketService"

        private const val MAX_RECONNECT_ATTEMPTS_BEFORE_RESET = 10
        private const val WEBSOCKET_HEALTH_CHECK_INTERVAL = 30_000L // 30 seconds
        private const val TAG_VOICE_ANNOUNCEMENT = "Voice message" // 30 seconds
    }
}