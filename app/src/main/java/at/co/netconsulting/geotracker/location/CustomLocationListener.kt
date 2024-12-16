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
import androidx.core.app.ActivityCompat
import at.co.netconsulting.geotracker.data.FellowRunner
import at.co.netconsulting.geotracker.data.LocationEvent
import at.co.netconsulting.geotracker.tools.Tools
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.math.pow

class CustomLocationListener: LocationListener {
    private lateinit var totalDateTime: LocalDateTime
    lateinit var startDateTime: LocalDateTime
    private var context: Context
    private var locationManager: LocationManager? = null
    private var oldLatitude: Double = -999.0
    private var oldLongitude: Double = -999.0
    var coveredDistance: Double = 0.0
    private var lapCounter: Double = 0.0
    private var lap: Int = 0
    private var averageSpeed: Double = 0.0
    private val latLngs = mutableListOf<LatLng>()
    private var webSocket: WebSocket? = null
    private var fellowRunnerPerson: String? = null
    private var fellowRunnerSessionId: String? = null
    private val currentLatitude: Double = 0.0
    private val currentLongitude: Double = 0.0
    private val altitude: Double = 0.0
    private var fellowRunnerLatitude: Double = 0.0
    private var fellowRunnerLongitude: Double = 0.0
    private var fellowRunnerCurrentSpeed: Double = 0.0
    private var person: String = ""
    private var fellowRunnerCoveredDistance: Float = 0.0f
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private var height: Float = 0f
    private var weight: Float = 0f

    private var retryCount = 0
    private var isRetrying = false
    private val job = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)
    private var lastMessageTimestamp = AtomicLong(System.currentTimeMillis())
    private var isConnectionHealthy = AtomicBoolean(false)
    private var connectionMonitorJob: Job? = null

    data class LocationChangeEvent(val latLngs: List<LatLng>)
    data class AdjustLocationFrequencyEvent(val reduceFrequency: Boolean)

    constructor(context: Context) {
        EventBus.getDefault().register(this)
        this.context = context
    }

    fun cleanup() {
        EventBus.getDefault().unregister(this)
        stopLocationUpdates()
        connectionMonitorJob?.cancel()
        job.cancel()
    }

    fun startListener() {
        createLocationManager()
        createLocationUpdates()
        loadSharedPreferences()
        getLatitudeLongitudeFromOtherRunner()
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

    private fun startConnectionMonitoring() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val timeSinceLastMessage = System.currentTimeMillis() - lastMessageTimestamp.get()

                    if (timeSinceLastMessage > CONNECTION_TIMEOUT && isConnectionHealthy.get()) {
                        Timber.tag(TAG_WEBSOCKET).w("No messages received for ${timeSinceLastMessage}ms, connection appears dead")
                        isConnectionHealthy.set(false)
                        retryCount = 0
                        webSocket?.cancel()
                        connectWebSocket(initialConnect = false)
                    }

                    webSocket?.let { socket ->
                        if (!socket.send("ping")) {
                            Timber.tag(TAG_WEBSOCKET).w("Failed to send ping, connection might be dead")
                            isConnectionHealthy.set(false)
                            socket.cancel()
                            connectWebSocket(initialConnect = false)
                        }
                    }

                    delay(CONNECTION_CHECK_INTERVAL)
                } catch (e: Exception) {
                    Timber.tag(TAG_WEBSOCKET).e(e, "Error in connection monitoring")
                }
            }
        }
    }

    private fun loadSharedPreferences() {
        val sharedPreferences = this.context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        firstname = sharedPreferences.getString("firstname", "") ?: ""
        lastname = sharedPreferences.getString("lastname", "") ?: ""
        birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        height = sharedPreferences.getFloat("height", 0f)
        weight = sharedPreferences.getFloat("weight", 0f)
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

    private fun getLatitudeLongitudeFromOtherRunner() {
        connectWebSocket()
    }

    private fun connectWebSocket(initialConnect: Boolean = true) {
        if (initialConnect) {
            retryCount = 0
            isRetrying = false
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("ws://62.178.111.184:8011/runningtracker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                updateConnectionHealth()
                Timber.tag(TAG_WEBSOCKET).d("Received binary message: ${bytes.hex()}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                updateConnectionHealth()

                if (text == "pong") {
                    return
                }

                Timber.tag(TAG_WEBSOCKET).d("Raw JSON: $text")

                try {
                    val fellowRunner = Gson().fromJson(text, FellowRunner::class.java)
                    processFellowRunnerData(fellowRunner)
                } catch (e: Exception) {
                    Timber.tag(TAG_WEBSOCKET).e(e, "Error processing message")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                isConnectionHealthy.set(false)
                Timber.tag(TAG_WEBSOCKET).e(t, "WebSocket connection failed")

                if (retryCount < MAX_RETRY_ATTEMPTS && !isRetrying) {
                    retryWithBackoff()
                } else if (retryCount >= MAX_RETRY_ATTEMPTS) {
                    Timber.tag(TAG_WEBSOCKET).e("Max retry attempts reached")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                isConnectionHealthy.set(false)
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                isConnectionHealthy.set(false)
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection closed: $reason")
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Timber.tag(TAG_WEBSOCKET).d("WebSocket connection opened")
                updateConnectionHealth()
                startConnectionMonitoring()
            }
        })
    }

    override fun onLocationChanged(location: Location) {
        location?.let {
            Log.d("CustomLocationListener", "Latitude: ${location!!.latitude} / Longitude: ${location!!.longitude}")
            if(checkSpeed(it.speed)) {
                if(oldLatitude != location.latitude || oldLongitude != location.longitude) {
                    Log.d("CustomLocationListener", "New coordinates detected...")
                    val (coveredDistance, distanceIncrement) = calculateDistance(it)
                    averageSpeed = calculateAverageSpeed(coveredDistance)
                    lap = calculateLap(distanceIncrement)
                    latLngs.add(LatLng(location.latitude, location.longitude))
                    sendDataToEventBus(LocationEvent(it.latitude,it.longitude,(it.speed / 1000) * 3600,it.speedAccuracyMetersPerSecond,it.altitude,it.accuracy,it.verticalAccuracyMeters,coveredDistance,lap,startDateTime,averageSpeed,LocationChangeEvent(latLngs)))
                    sendDataToWebsocketServer(Gson().toJson(FellowRunner(firstname,firstname,location.latitude,location.longitude,coveredDistance.toString(),(it.speed / 1000) * 3600,it.altitude.toString(),formattedTimestamp = Tools().formatCurrentTimestamp(),averageSpeed)))
                } else {
                    Log.d("CustomLocationListener", "Duplicate coordinates ignored")
                }
            } else {
                Log.d("CustomLocationListener", "Speed is 0.0 km/h")
                sendDataToEventBus(LocationEvent(it.latitude,it.longitude,0F,it.speedAccuracyMetersPerSecond,it.altitude,it.accuracy,it.verticalAccuracyMeters,coveredDistance,lap,startDateTime,averageSpeed,LocationChangeEvent(latLngs)))
                sendDataToWebsocketServer(Gson().toJson(FellowRunner(firstname,firstname,location.latitude,location.longitude,coveredDistance.toString(),0F,it.altitude.toString(),formattedTimestamp = Tools().formatCurrentTimestamp(),averageSpeed)))
            }
        }
    }

    private fun updateConnectionHealth() {
        lastMessageTimestamp.set(System.currentTimeMillis())
        isConnectionHealthy.set(true)
        retryCount = 0
        isRetrying = false
    }

    private fun processFellowRunnerData(fellowRunner: FellowRunner) {
        fellowRunnerPerson = fellowRunner.person
        if (fellowRunnerPerson != person) {
            fellowRunnerSessionId = fellowRunner.sessionId.toString()
            fellowRunnerLatitude = fellowRunner.latitude
            fellowRunnerLongitude = fellowRunner.longitude
            fellowRunnerCoveredDistance = fellowRunner.distance.toFloat()
            fellowRunnerCurrentSpeed = fellowRunner.speed.toDouble()
        }
    }

    private fun sendDataToEventBus(locationEvent: LocationEvent) {
        EventBus.getDefault().post(locationEvent)
    }

    private fun sendDataToWebsocketServer(toJson: String?) {
        if (toJson != null) {
            webSocket?.send(toJson)
        }
    }

    private fun calculateAverageSpeed(coveredDistance: Double): Double {
        var mCoveredDistance = coveredDistance
        totalDateTime = LocalDateTime.now()
        var duration = Duration.between(startDateTime, totalDateTime)
        return mCoveredDistance / (duration.toNanos()/1_000_000_000.0)
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

    private fun retryWithBackoff() {
        isRetrying = true
        coroutineScope.launch {
            val backoffMs = min(
                INITIAL_BACKOFF_MS * (2.0.pow(retryCount.toDouble())).toLong(),
                MAX_BACKOFF_MS
            )

            Timber.tag(TAG_WEBSOCKET).d("Retrying connection in ${backoffMs}ms (attempt ${retryCount + 1}/$MAX_RETRY_ATTEMPTS)")

            delay(backoffMs)
            retryCount++

            withContext(Dispatchers.Main) {
                connectWebSocket(initialConnect = false)
            }
        }
    }

    fun adjustUpdateFrequency(reduceFrequency: Boolean) {
        if (reduceFrequency) {
            MIN_TIME_BETWEEN_UPDATES = 2000L
            MIN_DISTANCE_BETWEEN_UPDATES = 2f
        } else {
            MIN_TIME_BETWEEN_UPDATES = 1000L
            MIN_DISTANCE_BETWEEN_UPDATES = 1f
        }
        stopLocationUpdates()
        createLocationUpdates()
    }

    fun getCurrentLatLngs(): List<LatLng> {
        return latLngs.toList()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdjustFrequencyEvent(event: AdjustLocationFrequencyEvent) {
        adjustUpdateFrequency(event.reduceFrequency)
    }

    companion object {
        private var MIN_TIME_BETWEEN_UPDATES: Long = 1000
        private var MIN_DISTANCE_BETWEEN_UPDATES: Float = 1f
        private const val MIN_SPEED_THRESHOLD: Double = 2.5 // km/h
        const val TAG_WEBSOCKET: String = "CustomLocationListener: WebSocketService"
        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 32000L
        // Connection monitoring
        private const val CONNECTION_CHECK_INTERVAL = 30000L // Check every 30 seconds
        private const val CONNECTION_TIMEOUT = 60000L // Consider connection dead after 60 seconds of no messages
    }
}