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
import at.co.netconsulting.geotracker.domain.DeviceStatus
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.Weather
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
import java.util.concurrent.TimeUnit

class ForegroundService : Service() {
    private lateinit var job: Job
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private lateinit var height: String
    private lateinit var weight: String
    private lateinit var eventname: String
    private lateinit var eventdate: String
    private lateinit var artofsport: String
    private lateinit var comment: String
    private lateinit var clothing: String
    private var speed: Float = 0.0f
    private var eventId: Int = 0
    private var distance: Double = 0.0
    private var altitude: Double = 0.0
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var lap: Int = 0
    private var startTimeNanos: Long = 0L
    private var elapsedNanos: Long = 0L
    private var elapsedSeconds: Long = 0L
    private var hours: Int = 0
    private var minutes: Int = 0
    private var seconds: Int = 0
    private var formattedTime: String = ""

    override fun onCreate() {
        super.onCreate()
        loadSharedPreferences()
        //displayDatabaseContents()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun loadSharedPreferences() {
        val sharedPreferences = this.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        firstname = sharedPreferences.getString("firstname", "") ?: ""
        lastname = sharedPreferences.getString("lastname", "") ?: ""
        birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        height = sharedPreferences.getString("height", "") ?: ""
        weight = sharedPreferences.getString("weight", "") ?: ""
    }

    private val database: FitnessTrackerDatabase by lazy {
        FitnessTrackerDatabase.getInstance(applicationContext)
    }

    private fun createBackgroundCoroutine() {
        job = GlobalScope.launch(Dispatchers.IO) {
            val customLocationListener = CustomLocationListener(applicationContext)
            customLocationListener.startListener()
            val userId = database.userDao().getUserIdByFirstNameLastName("Bernd", "Roth")
            eventId = createNewEvent(database,userId)
            startTimeNanos = System.nanoTime()
            while (isActive) {
                delay(1000)
                if(speed>=2.5) {
                    insertDatabase(database)
                }
                elapsedNanos = System.nanoTime() - startTimeNanos
                elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(elapsedNanos)

                hours = (elapsedSeconds / 3600).toInt()
                minutes = ((elapsedSeconds % 3600) / 60).toInt()
                seconds = (elapsedSeconds % 60).toInt()
                formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                updateNotification(formattedTime + "\n" +
                    "Covered Distance: " + String.format("%.2f", distance/1000) + " Km" +
                    "\nSpeed: " + String.format("%.2f", speed) + " km/h" +
                    "\nAltitude: " + String.format("%.2f", altitude) + " meter" +
                    "\nLap: " + String.format("%2d", lap)
                )
            }
        }
    }

    private suspend fun createNewEvent(database: FitnessTrackerDatabase, userId: Int): Int {
        val newEvent = Event(
            userId = userId,
            eventName = eventname,
            eventDate = eventdate,
            artOfSport = artofsport,
            comment = comment
        )

        val eventId = database.eventDao().insertEvent(newEvent).toInt()
        println("New Event ID: $eventId")
        return eventId
    }

    private fun displayDatabaseContents() {
        GlobalScope.launch(Dispatchers.IO) {
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
        speed = event.speed
        altitude = event.altitude
        latitude = event.latitude
        longitude = event.longitude
        distance = event.coveredDistance
        lap = event.lap
    }

    private fun updateNotification(newContent: String) {
        runOnUiThread {
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(newContent))
            notificationManager.notify(1, notificationBuilder.build())
        }
    }

    private fun insertDatabase(database: FitnessTrackerDatabase) {
        GlobalScope.launch(Dispatchers.IO) {
            val metric = Metric(
                eventId = eventId,
                heartRate = 0,
                heartRateDevice = "Chest Strap",
                speed = speed,
                distance = distance,
                cadence = 0,
                lap = lap,
                timeInMilliseconds = System.currentTimeMillis(),
                unity = "metric"
            )
            database.metricDao().insertMetric(metric)

            // Example location data
            val location = Location(
                eventId = eventId,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude
            )
            database.locationDao().insertLocation(location)

            val weather = Weather(
                eventId = eventId,
                weatherRestApi = "OpenWeatherAPI",
                temperature = 0f,
                windSpeed = 0f,
                windDirection = "NE",
                relativeHumidity = 0
            )
            database.weatherDao().insertWeather(weather)

            val deviceStatus = DeviceStatus(
                eventId = eventId,
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
        eventname = intent?.getStringExtra("eventName") ?: "Unknown Event"
        eventdate = intent?.getStringExtra("eventDate") ?: "Unknown Date"
        artofsport = intent?.getStringExtra("artOfSport") ?: "Unknown Sport"
        comment = intent?.getStringExtra("comment") ?: "No Comment"
        clothing = intent?.getStringExtra("clothing") ?: "No Clothing Info"

        EventBus.getDefault().register(this)
        createNotificationChannel()
        createBackgroundCoroutine()

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTracker")
            .setContentText("Time, covered distance, ... will be shown here!")
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