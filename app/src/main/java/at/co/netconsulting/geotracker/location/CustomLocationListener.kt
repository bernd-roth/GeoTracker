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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.greenrobot.eventbus.EventBus
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime

class CustomLocationListener: LocationListener {
    private lateinit var totalDateTime: LocalDateTime
    lateinit var startDateTime: LocalDateTime
    private var context: Context
    private var locationManager: LocationManager? = null
    private var oldLatitude: Double = -999.0
    private var oldLongitude: Double = -999.0
    var coveredDistance: Double = 0.0
    private var lapCounter : Double = 0.0
    private var lap : Int = 0
    private var averageSpeed : Double = 0.0
    private val latLngs = mutableListOf<LatLng>()
    data class LocationChangeEvent(val latLngs: List<LatLng>)
    private var webSocket: WebSocket? = null
    private var fellowRunnerPerson: String? = null
    private var fellowRunnerSessionId: String? = null
    private val currentLatitude : Double = 0.0
    private val currentLongitude : Double = 0.0
    private val altitude : Double = 0.0
    private var fellowRunnerLatitude : Double = 0.0
    private var fellowRunnerLongitude : Double = 0.0
    private var fellowRunnerCurrentSpeed : Double = 0.0
    private var person : String = ""
    private var fellowRunnerCoveredDistance : Float = 0.0f
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private var height: Float = 0f
    private var weight: Float = 0f

    constructor(context: Context) {
        this.context = context
    }

    fun startListener() {
        createLocationManager()
        createLocationUpdates()
        loadSharedPreferences()
        getLatitudeLongitudeFromOtherRunner()
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
            ) != PackageManager.PERMISSION_GRANTED  && ActivityCompat.checkSelfPermission(
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
                    EventBus.getDefault().post(
                        LocationEvent(
                            it.latitude,
                            it.longitude,
                            (it.speed / 1000) * 3600,
                            it.speedAccuracyMetersPerSecond,
                            it.altitude,
                            it.accuracy,
                            it.verticalAccuracyMeters,
                            coveredDistance,
                            lap,
                            startDateTime,
                            averageSpeed,
                            LocationChangeEvent(latLngs)
                        )
                    )
                    //send data to websocketserver
                    val json: String = Gson().toJson(
                        FellowRunner(
                            firstname,
                            firstname,
                            location.latitude,
                            location.longitude,
                            coveredDistance.toString(),
                            (it.speed / 1000) * 3600,
                            it.altitude.toString(),
                            formattedTimestamp = Tools().formatCurrentTimestamp()
                        )
                    )
                    //send json via websocket to server
                    webSocket!!.send(json)
                } else {
                    Log.d("CustomLocationListener", "Duplicate coordinates ignored")
                }
            }
        }
    }

    private fun calculateAverageSpeed(coveredDistance: Double): Double{
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

    // calculateLaps calculates one lap per at least 1000 meters
    // however, if it should ever happen that you run/cycle or swim faster than 2000 meters per second
    // the lap will be calculated based on this number
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
            //older location
            oldLatitude,
            oldLongitude,
            //current location
            newLatitude,
            newLongitude,
            result);
        return result[0].toDouble()
    }

    /**
     *
     * This method checks whether speed is greater than 2.5 km/h.
     * Since GPS fluctuates all the time,
     * distance would sum up over time,
     * therefore if speed is less than 2.5 km/h
     * no calculation of distance must be made
     *
     * @param float
     *
     */
    private fun checkSpeed(speed: Float): Boolean {
        Log.d("CustomLocationListener", "Speed: $speed m/s")
        val thresholdInMetersPerSecond = MIN_SPEED_THRESHOLD / 3.6
        return speed >= thresholdInMetersPerSecond
    }

    private fun getLatitudeLongitudeFromOtherRunner() {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("ws://62.178.111.184:8011/runningtracker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                Timber.tag(TAG_WEBSOCKET).d("Received binary message: ${bytes.hex()}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)

                Timber.tag(TAG_WEBSOCKET).d("Raw JSON: $text")

                val fellowRunner = Gson().fromJson(text, FellowRunner::class.java)

                fellowRunnerPerson = fellowRunner.person
                if (fellowRunnerPerson != person) {
                    fellowRunnerSessionId = fellowRunner.sessionId.toString()
                    fellowRunnerLatitude = fellowRunner.latitude
                    fellowRunnerLongitude = fellowRunner.longitude
                    fellowRunnerCoveredDistance = fellowRunner.distance.toFloat()
                    fellowRunnerCurrentSpeed = fellowRunner.speed.toDouble()
                }

                Timber.tag(TAG_WEBSOCKET).d("""
                    Fellow runner:
                    Person: ${fellowRunner.person}
                    sessionId: ${fellowRunner.sessionId}
                    Latitude: ${fellowRunner.latitude}
                    Longitude: ${fellowRunner.longitude}
                    Distance: ${fellowRunner.distance}
                    Current Speed: ${fellowRunner.speed}
                    Altitude: ${fellowRunner.altitude}
                    Timestamp: ${fellowRunner.formattedTimestamp}
                """.trimIndent())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                Timber.tag(TAG_WEBSOCKET).e(t, "WebSocket connection failed")
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
            }
        })
    }

    companion object {
        private const val MIN_TIME_BETWEEN_UPDATES: Long = 1000
        private const val MIN_DISTANCE_BETWEEN_UPDATES: Float = 1f
        private const val MIN_SPEED_THRESHOLD: Double = 2.5 // km/h
        const val TAG_WEBSOCKET: String = "CustomLocationListener: WebSocketService"
    }
}