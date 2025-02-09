package at.co.netconsulting.geotracker.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import at.co.netconsulting.geotracker.data.LocationEvent
import at.co.netconsulting.geotracker.location.CustomLocationListener
import at.co.netconsulting.geotracker.tools.getTotalAscent
import at.co.netconsulting.geotracker.tools.getTotalDescent
import com.google.android.gms.maps.model.LatLng
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.greenrobot.eventbus.EventBus
import java.time.Duration
import java.time.LocalDateTime

class BackgroundLocationService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var startDateTime: LocalDateTime
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private var height: Float = 0f
    private var weight: Float = 0f
    private var webSocket: WebSocket? = null
    private var oldLatitude: Double = -999.0
    private var oldLongitude: Double = -999.0
    private var coveredDistance: Double = 0.0
    private var averageSpeed: Double = 0.0
    private lateinit var totalDateTime: LocalDateTime

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startLocationUpdates()
        startDateTime = LocalDateTime.now()
        loadSharedPreferences()
        //initializeWebsocket()
    }

    private fun initializeWebsocket() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("ws://62.178.111.184:8011/runningtracker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error : " + t.message)
            }
        })
    }

/*    private fun sendToWebsocket(location: Location, coveredDistance: Double) {
        val json: String = Gson().toJson(
            FellowRunner(
                firstname,
                firstname,
                location.latitude,
                location.longitude,
                coveredDistance.toString(),
                (location.speed / 1000) * 3600,
                location.altitude.toString(),
                formattedTimestamp = Tools().formatCurrentTimestamp(),
                averageSpeed,
                getTotalAscent(),
                getTotalDescent()
            )
        )
        //send json via websocket to server
        webSocket!!.send(json)
    }*/

    private fun loadSharedPreferences() {
        val sharedPreferences = getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        firstname = sharedPreferences.getString("firstname", "") ?: ""
        lastname = sharedPreferences.getString("lastname", "") ?: ""
        birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        height = sharedPreferences.getFloat("height", 0f)
        weight = sharedPreferences.getFloat("weight", 0f)
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
        } catch (e: SecurityException) {
            Log.e("BackgroundService", "Missing permissions for location updates.", e)
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d(
            "BackgroundService", "Location Updated: Latitude: ${location.latitude}, " +
                    "Longitude: ${location.longitude}, Accuracy: ${location.accuracy}"
        )
        val latLngs = listOf(LatLng(location.latitude, location.longitude))

        EventBus.getDefault().post(latLngs.let {
            CustomLocationListener.LocationChangeEvent(it)
        }?.let {
            LocationEvent(
                location.latitude,
                location.longitude,
                location.speed,
                location.speedAccuracyMetersPerSecond,
                location.altitude,
                location.accuracy,
                location.verticalAccuracyMeters,
                coveredDistance = coveredDistance,
                lap = 0,
                startDateTime = startDateTime,
                averageSpeed = averageSpeed,
                it,
                getTotalAscent(),
                getTotalDescent()
            )
        })
        val (newCoveredDistance, distanceIncrement) = calculateDistance(location)
        coveredDistance = newCoveredDistance
        averageSpeed = calculateAverageSpeed(coveredDistance)

        //For now we do not want to see the user on the website
        //sendToWebsocket(location, coveredDistance)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d("BackgroundService", "Status changed for provider $provider: $status")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d("BackgroundService", "Provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d("BackgroundService", "Provider disabled: $provider")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Remove location updates to prevent memory leaks
        locationManager.removeUpdates(this)
    }

    private fun calculateDistance(location: Location): Pair<Double, Double> {
        val distanceIncrement: Double
        if (checkSpeed(location)) {
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
        } else {
            return Pair(0.0, 0.0)
        }
    }

    private fun checkSpeed(location: Location): Boolean {
        var speed = location.speed
        val thresholdInMetersPerSecond = MIN_SPEED_THRESHOLD / 3.6
        return speed >= thresholdInMetersPerSecond
    }

    private fun calculateAverageSpeed(coveredDistance: Double): Double {
        return try {
            totalDateTime = LocalDateTime.now()
            val duration = Duration.between(startDateTime, totalDateTime)
            val durationSeconds = duration.toNanos() / 1_000_000_000.0

            // Check for division by zero or very small values
            if (durationSeconds > 0.001) {  // Using a small threshold instead of exactly 0
                (coveredDistance / durationSeconds) * 3.6
            } else {
                0.0  // Return 0 if duration is too small
            }
        } catch (e: Exception) {
            Log.e("BackgroundService", "Error calculating average speed", e)
            0.0  // Return 0 in case of any error
        }
    }

    private fun calculateDistanceBetweenOldLatLngNewLatLng(
        oldLatitude: Double,
        oldLongitude: Double,
        newLatitude: Double,
        newLongitude: Double
    ): Double {
        val result = FloatArray(1)
        Location.distanceBetween(
            //older location
            oldLatitude,
            oldLongitude,
            //current location
            newLatitude,
            newLongitude,
            result
        );
        return result[0].toDouble()
    }

    companion object {
        private const val MIN_TIME_BETWEEN_UPDATES: Long = 1000
        private const val MIN_DISTANCE_BETWEEN_UPDATES: Float = 1f
        private const val TAG_WEBSOCKET: String = "BackgroundLocationService: WebSocketService"
        private val MIN_SPEED_THRESHOLD: Double = 2.5 // km/h
    }
}