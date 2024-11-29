package at.co.netconsulting.geotracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.data.LocationEvent
import at.co.netconsulting.geotracker.domain.DeviceStatus
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.User
import at.co.netconsulting.geotracker.domain.Weather
import at.co.netconsulting.geotracker.location.CustomLocationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.System.currentTimeMillis
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class ForegroundService : Service() {
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var firstname: String
    private lateinit var lastname: String
    private lateinit var birthdate: String
    private var height: Float = 0f
    private var weight: Float = 0f
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
    private val oldLatitude: Double = -999.0
    private val oldLongitude: Double = -999.0
    private var lazySegmentStartTimeNanos: Long = 0L
    private var lazyTotalElapsedSeconds: Long = 0L
    private var lazyFormattedTime: String = "00:00:00"
    private var movementSegmentStartTimeNanos: Long = 0L
    private var movementTotalElapsedSeconds: Long = 0L
    private var movementFormattedTime: String = "00:00:00"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var wakeLock: PowerManager.WakeLock
    private var lastUpdateTimestamp: Long = currentTimeMillis()

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
        height = sharedPreferences.getFloat("height", 0f)
        weight = sharedPreferences.getFloat("weight", 0f)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeoTracker:ForegroundService:WakeLock")
        wakeLock.acquire()
    }

    private fun releaseWakeLock() {
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    private val database: FitnessTrackerDatabase by lazy {
        FitnessTrackerDatabase.getInstance(applicationContext)
    }

    private fun createBackgroundCoroutine() {
        serviceScope.launch {
            val customLocationListener = CustomLocationListener(applicationContext)
            customLocationListener.startDateTime = LocalDateTime.now()
            customLocationListener.startListener()
            val userId = database.userDao().insertUser(User(0, firstname, lastname, birthdate, weight, height))
            //val userId = database.userDao().getUserIdByFirstNameLastName(firstname, lastname)
            eventId = createNewEvent(database,userId)
            while (isActive) {
                val currentTime = currentTimeMillis()
                if (currentTime - lastUpdateTimestamp > EVENT_TIMEOUT_MS) {
                    resetValues()
                }
                // if speed is higher than some value,
                // we do not need to compare latitude and longitude (method compareLatitudeLongitude)
                // anymore because you cannot move without changing your position
                if (speed >= MIN_SPEED_THRESHOLD) {
                    showStopWatch()
                } else {
                    showLazyStopWatch()
                }
                showNotification()
                //displayDatabaseContents()
                delay(1000)
            }
        }
    }

    private fun resetValues() {
        speed = 0.0F
        showNotification()
    }

    private fun showNotification() {
        updateNotification(
            "Activity: " + movementFormattedTime +
            "\nCovered Distance: " + String.format("%.2f", distance / 1000) + " Km" +
            "\nSpeed: " + String.format("%.2f", speed) + " km/h" +
            "\nAltitude: " + String.format("%.2f", altitude) + " meter" +
            "\nLap: " + String.format("%2d", lap) +
            "\nInactivity: " + lazyFormattedTime
        )
    }

    private fun showStopWatch() {
        if (speed >= 2.5) {
            if (movementSegmentStartTimeNanos == 0L) {
                movementSegmentStartTimeNanos = System.nanoTime()
            }

            val movementSegmentElapsedNanos = System.nanoTime() - movementSegmentStartTimeNanos
            val movementSegmentElapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(movementSegmentElapsedNanos)

            if (movementSegmentElapsedSeconds > 0) {
                movementTotalElapsedSeconds += movementSegmentElapsedSeconds
                movementSegmentStartTimeNanos = System.nanoTime()
            }

            val movementHours = (movementTotalElapsedSeconds / 3600).toInt()
            val movementMinutes = ((movementTotalElapsedSeconds % 3600) / 60).toInt()
            val movementSeconds = (movementTotalElapsedSeconds % 60).toInt()
            movementFormattedTime = String.format("%02d:%02d:%02d", movementHours, movementMinutes, movementSeconds)
        } else {
            movementSegmentStartTimeNanos = 0L
        }
    }

    private fun showLazyStopWatch() {
        if (speed < 2.5) {
            if (lazySegmentStartTimeNanos == 0L) {
                lazySegmentStartTimeNanos = System.nanoTime()
            }

            val lazySegmentElapsedNanos = System.nanoTime() - lazySegmentStartTimeNanos
            val lazySegmentElapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(lazySegmentElapsedNanos)

            if (lazySegmentElapsedSeconds > 0) {
                lazyTotalElapsedSeconds += lazySegmentElapsedSeconds
                lazySegmentStartTimeNanos = System.nanoTime()
            }
        } else {
            lazySegmentStartTimeNanos = 0L
        }

        // Format total inactivity time
        val lazyHours = (lazyTotalElapsedSeconds / 3600).toInt()
        val lazyMinutes = ((lazyTotalElapsedSeconds % 3600) / 60).toInt()
        val lazySeconds = (lazyTotalElapsedSeconds % 60).toInt()
        lazyFormattedTime = String.format("%02d:%02d:%02d", lazyHours, lazyMinutes, lazySeconds)
    }

    private fun compareLatitudeLongitude(): Boolean {
        val res: Int = when {
            oldLatitude < latitude -> -1
            oldLongitude > latitude -> 1
            else -> 0
        }
        return res == 0
    }

    private suspend fun createNewEvent(database: FitnessTrackerDatabase, userId: Long): Int {
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
            val events = database.eventDao().getAllEvents()
            events.forEach { event ->
                Log.d("DatabaseDebug", "Event: $event")
            }
            val metrics = database.metricDao().getAllMetrics()
            metrics.forEach { metric ->
                Log.d("DatabaseDebug", "Metric: $metric")
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
        lastUpdateTimestamp = currentTimeMillis()
        speed = event.speed
        altitude = event.altitude
        latitude = event.latitude
        longitude = event.longitude
        distance = event.coveredDistance
        lap = event.lap
        insertDatabase(database)
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

        acquireWakeLock()

        EventBus.getDefault().register(this)
        createNotificationChannel()
        createBackgroundCoroutine()

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTracker")
//            .setContentText("Time, covered distance, ... will be shown here!")
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
        releaseWakeLock()
        EventBus.getDefault().unregister(this)
        // Stop infinite loop
        serviceJob.cancel()
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
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val MIN_SPEED_THRESHOLD: Double = 2.5
        private const val EVENT_TIMEOUT_MS = 2000
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
}