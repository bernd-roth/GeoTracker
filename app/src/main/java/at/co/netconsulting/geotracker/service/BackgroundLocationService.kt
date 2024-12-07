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
import com.google.android.gms.maps.model.LatLng
import org.greenrobot.eventbus.EventBus
import java.time.LocalDateTime

class BackgroundLocationService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private lateinit var startDateTime: LocalDateTime

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        startLocationUpdates()
        startDateTime = LocalDateTime.now()
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
                coveredDistance = 0.0,
                lap = 0,
                startDateTime = startDateTime,
                averageSpeed = 0.0, // Assuming 0.0 for averageSpeed here
                it
            )
        })
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

    companion object {
        private const val MIN_TIME_BETWEEN_UPDATES: Long = 1000
        private const val MIN_DISTANCE_BETWEEN_UPDATES: Float = 1f
    }
}