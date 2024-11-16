package at.co.netconsulting.geotracker.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationManager.GPS_PROVIDER
import android.location.LocationManager.NETWORK_PROVIDER
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.data.LocationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

class ForegroundService : Service(), LocationListener {
    private var mLocation: Location? = null
    private var locationManager: LocationManager? = null
    private lateinit var job: Job

    override fun onCreate() {
        super.onCreate()
        initializeLocation(mLocation)
        createLocationManager()
        createLocationUpdates()
        createNotificationChannel()
        createBackgroundCoroutine()
    }

    private fun initializeLocation(location: Location?) {
        mLocation = location
    }

    private fun createLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationManager?.requestLocationUpdates(GPS_PROVIDER, 1000, 1f, this)
    }

    private fun createLocationManager() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun createBackgroundCoroutine() {
        job = GlobalScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)

                mLocation?.let {
                    Log.d("ForegroundService", "Latitude: ${mLocation!!.latitude} / Longitude: ${mLocation!!.longitude}")
                    EventBus.getDefault().post(LocationEvent(it.latitude, it.longitude, it.speed, it.speedAccuracyMetersPerSecond, it.altitude, it.accuracy, it.verticalAccuracyMeters))
                } ?: run {
                    Log.e("ForegroundService", "mLocation is null, event not posted")
                }
                insertDatabase()
            }
        }
    }

    private fun insertDatabase() {
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTracker Service")
            .setContentText("Recording location...")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOnlyAlertOnce(true)
            //show notification on home screen to everyone
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            //without FOREGROUND_SERVICE_IMMEDIATE, notification can take up to 10 secs to be shown
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        startForeground(1, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop infinite loop
        job.cancel()
        // Stop foreground mode and cancel the notification immediately
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    //LocationListener
    override fun onLocationChanged(location: android.location.Location) {
        mLocation = location
    }

    // Companion
    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
    }
}