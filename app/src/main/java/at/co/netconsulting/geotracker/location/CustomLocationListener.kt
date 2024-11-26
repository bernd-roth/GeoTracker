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
import at.co.netconsulting.geotracker.data.LocationEvent
import org.greenrobot.eventbus.EventBus
import java.time.Duration
import java.time.LocalDateTime

class CustomLocationListener: LocationListener {
    private lateinit var totalDateTime: LocalDateTime
    lateinit var startDateTime: LocalDateTime
    private var context: Context
    private var locationManager: LocationManager? = null
    private var oldLatitude: Double = 0.0
    private var oldLongitude: Double = 0.0
    private var coveredDistance: Double = 0.0
    private var lapCounter : Double = 0.0
    private var lap : Int = 0
    private var averageSpeed : Double = 0.0

    constructor(context: Context) {
        this.context = context
    }

    fun startListener() {
        createLocationManager()
        createLocationUpdates()
    }

    private fun createLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this.context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this.context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Handler(Looper.getMainLooper()).post {
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, this)
        }
    }

    private fun createLocationManager() {
        locationManager = this.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onLocationChanged(location: Location) {
        location?.let {
            Log.d("CustomLocationListener", "Latitude: ${location!!.latitude} / Longitude: ${location!!.longitude}")
            checkSpeedAndCalculateDistance(it)
            averageSpeed = calculateAverageSpeed()
            EventBus.getDefault().post(LocationEvent(it.latitude, it.longitude, it.speed, it.speedAccuracyMetersPerSecond, it.altitude, it.accuracy, it.verticalAccuracyMeters, coveredDistance, lap, startDateTime, averageSpeed))
        }
    }

    private fun calculateAverageSpeed() : Double{
        totalDateTime = LocalDateTime.now()
        var duration = Duration.between(startDateTime, totalDateTime)
        return coveredDistance / (duration.toNanos()/1_000_000_000.0)
    }

    private fun checkSpeedAndCalculateDistance(it: Location) {
        if(oldLatitude!=0.0 && oldLongitude!=0.0) {
            if(checkSpeed(it.speed)) {
                coveredDistance = calculateDistance(oldLatitude, oldLongitude, it.latitude, it.longitude)
                calculateLap(coveredDistance)
                oldLatitude = it.latitude
                oldLongitude = it.longitude
            }
        } else {
            oldLatitude = it.latitude
            oldLongitude = it.longitude
        }
    }

    private fun calculateLap(coveredDistance: Double) {
        lapCounter += coveredDistance;
        if(lapCounter>=1000) {
            lap+=1;
            lapCounter=0.0;
        }
    }

    private fun calculateDistance(
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
        coveredDistance += result[0]
        return coveredDistance
    }

    private fun checkSpeed(speed: Float): Boolean {
        return if(speed>=2.5) {
            true
        } else {
            false
        }
    }
}