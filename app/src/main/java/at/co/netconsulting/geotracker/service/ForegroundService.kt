package at.co.netconsulting.geotracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.data.LocationEvent
import at.co.netconsulting.geotracker.db.DeviceStatus
import at.co.netconsulting.geotracker.db.Event
import at.co.netconsulting.geotracker.db.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.db.Location
import at.co.netconsulting.geotracker.db.Metric
import at.co.netconsulting.geotracker.db.User
import at.co.netconsulting.geotracker.db.Weather
import at.co.netconsulting.geotracker.location.CustomLocationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ForegroundService : Service() {
    private lateinit var job: Job
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()
        displayDatabaseContents()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        EventBus.getDefault().register(this)
        createNotificationChannel()
        createBackgroundCoroutine()
    }

    private val database: FitnessTrackerDatabase by lazy {
        FitnessTrackerDatabase.getInstance(applicationContext)
    }

    private fun createBackgroundCoroutine() {
        job = GlobalScope.launch(Dispatchers.IO) {
            val customLocationListener = CustomLocationListener(applicationContext)
            customLocationListener.startListener()
            while (isActive) {
                delay(1000)
                insertDatabase(database)
            }
        }
    }

    private fun displayDatabaseContents() {
        GlobalScope.launch(Dispatchers.IO) {
            // Fetch data from User table
            val users = database.userDao().getAllUsers()
            users.forEach { user ->
                Log.d("DatabaseDebug", "User: $user")
            }
        }
    }

    // Publisher/Subscriber
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onLocationEvent(event: LocationEvent) {
        Log.d("ForegroundService",
            "Latitude: ${event.latitude}," +
                    " Longitude: ${event.longitude}," +
                    " Speed: ${event.speed}," +
                    " SpeedAccuracyInMeters: ${event.speedAccuracyMetersPerSecond}," +
                    " Altitude: ${event.altitude}," +
                    " HorizontalAccuracyInMeters: ${event.horizontalAccuracy}," +
                    " VerticalAccuracyInMeters: ${event.verticalAccuracyMeters}" +
                    " CoveredDistance: ${event.coveredDistance}")
        updateNotification(
            "Covered Distance: " + String.format("%.2f", event.coveredDistance/1000) + " Km" +
            "\nSpeed: " + String.format("%.2f", event.speed) + " km/h"+
            "\nAltitude: " + String.format("%.2f", event.altitude) + " meter")
    }

    private fun updateNotification(newContent: String) {
        runOnUiThread {
            notificationBuilder.setContentText(newContent)
            notificationManager.notify(1, notificationBuilder.build())
        }
    }

    private fun insertDatabase(database: FitnessTrackerDatabase) {
        GlobalScope.launch(Dispatchers.IO) {
            val user = User(
                userId = 0, // Auto-generate if applicable
                firstName = "John",
                lastName = "Doe",
                birthDate = "1990-01-01",
                75f,
                170f
            )
            val userId = database.userDao().insertUser(user)

            // Insert event and retrieve event ID
            val eventId = database.eventDao().insertEvent(
                Event(
                    userId = userId.toInt(),
                    eventName = "Cycling Session",
                    eventDate = "2024-11-18",
                    artOfSport = "Cycling",
                    comment = "Morning ride"
                )
            )

            // Example metric data
            val metric = Metric(
                eventId = eventId.toInt(),
                heartRate = 120,
                heartRateDevice = "Chest Strap",
                speed = 25.5f,
                distance = 1000.0f,
                cadence = 90,
                lap = 1,
                timeInMilliseconds = System.currentTimeMillis(),
                unity = "metric"
            )
            database.metricDao().insertMetric(metric)

            // Example location data
            val location = Location(
                eventId = eventId.toInt(),
                latitude = 40.7128,
                longitude = -74.0060,
                altitude = 15.0f
            )
            database.locationDao().insertLocation(location)

            // Example weather data
            val weather = Weather(
                eventId = eventId.toInt(),
                weatherRestApi = "OpenWeatherAPI",
                temperature = 18.0f,
                windSpeed = 5.5f,
                windDirection = "NE",
                relativeHumidity = 60
            )
            database.weatherDao().insertWeather(weather)

            // Example device status
            val deviceStatus = DeviceStatus(
                eventId = eventId.toInt(),
                numberOfSatellites = 8,
                sensorAccuracy = "High",
                signalStrength = "Strong",
                batteryLevel = "80%",
                connectionStatus = "Connected"
            )
            database.deviceStatusDao().insertDeviceStatus(deviceStatus)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTracker")
            .setContentText("Covered distance and altitude will be shown here!")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOnlyAlertOnce(true)
            //show notification on home screen to everyone
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            //without FOREGROUND_SERVICE_IMMEDIATE, notification can take up to 10 secs to be shown
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val notification = notificationBuilder.build()

        startForeground(1, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        // Stop infinite loop
        job.cancel()
        // Stop foreground mode and cancel the notification immediately
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GeoTracker",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
    }

    // Companion
    companion object {
        const val CHANNEL_ID = "ForegroundServiceChannel"
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}