package at.co.netconsulting.geotracker.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.MainActivity
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
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.System.currentTimeMillis
import java.time.Duration
import java.time.LocalDateTime

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
    private var lazyFormattedTime: String = "00:00:00"
    private var movementFormattedTime: String = "00:00:00"
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var wakeLock: PowerManager.WakeLock
    private var lastUpdateTimestamp: Long = currentTimeMillis()
    private var isCurrentlyMoving = false
    private val movementState = StopwatchState()
    private val lazyState = StopwatchState()
    private val heartbeatJob = Job()
    private val heartbeatScope = CoroutineScope(Dispatchers.IO + heartbeatJob)
    private var isHeartbeatActive = false
    private var customLocationListener: CustomLocationListener? = null

    data class HeartbeatEvent(val timestamp: Long)

    override fun onCreate() {
        super.onCreate()
        loadSharedPreferences()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GeoTracker")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        requestHighPriority()
        startHeartbeat()
    }

    private fun requestHighPriority() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Request maximum priority
            startForeground(1, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

            // Request ignore battery optimizations
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }

    private fun startHeartbeat() {
        if (!isHeartbeatActive) {
            isHeartbeatActive = true
            heartbeatScope.launch {
                while (isActive) {
                    try {
                        // Send heartbeat event
                        EventBus.getDefault().post(HeartbeatEvent(currentTimeMillis()))
                        // Check if location updates are still coming
                        checkLocationUpdates()
                        delay(HEARTBEAT_INTERVAL)
                    } catch (e: Exception) {
                        Log.e("Heartbeat", "Error in heartbeat", e)
                    }
                }
            }
        }
    }

    private fun checkLocationUpdates() {
        val currentTime = currentTimeMillis()
        if (currentTime - lastUpdateTimestamp > LOCATION_UPDATE_TIMEOUT) {
            Log.w("LocationMonitor", "No location updates received for ${LOCATION_UPDATE_TIMEOUT/1000} seconds")
            serviceScope.launch {
                try {
                    customLocationListener?.cleanup()
                    customLocationListener = CustomLocationListener(applicationContext).also {
                        it.startDateTime = LocalDateTime.now()
                        it.startListener()
                    }
                } catch (e: Exception) {
                    Log.e("LocationMonitor", "Failed to restart location updates", e)
                }
            }
        }
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

    private class TimeSegment(
        var startTime: LocalDateTime = LocalDateTime.now(),
        var endTime: LocalDateTime? = null,
        var totalDuration: Duration = Duration.ZERO
    )

    private class StopwatchState {
        private val segments = mutableListOf<TimeSegment>()
        private var currentSegment: TimeSegment? = null

        fun start() {
            currentSegment = TimeSegment()
            segments.add(currentSegment!!)
        }

        fun stop() {
            currentSegment?.let {
                it.endTime = LocalDateTime.now()
                it.totalDuration = Duration.between(it.startTime, it.endTime)
                currentSegment = null
            }
        }

        fun getTotalDuration(): Duration {
            val completedDuration = segments.sumOf {
                it.totalDuration.seconds
            }

            val currentDuration = currentSegment?.let {
                Duration.between(it.startTime, LocalDateTime.now()).seconds
            } ?: 0

            return Duration.ofSeconds(completedDuration + currentDuration)
        }

        fun getCurrentSegment(): TimeSegment? = currentSegment
    }

    private fun createBackgroundCoroutine() {
        serviceScope.launch {
            // Store the reference instead of just local variable
            customLocationListener = CustomLocationListener(applicationContext).also {
                it.startDateTime = LocalDateTime.now()
                it.startListener()
            }

            val userId = database.userDao().insertUser(User(0, firstname, lastname, birthdate, weight, height))
            eventId = createNewEvent(database, userId)

            while (isActive) {
                val currentTime = currentTimeMillis()
                if (currentTime - lastUpdateTimestamp > EVENT_TIMEOUT_MS) {
                    resetValues()
                }
                if (speed >= MIN_SPEED_THRESHOLD) {
                    //triggerLocationChange(customLocationListener!!)
                    showStopWatch()
                } else {
                    showLazyStopWatch()
                }

                showNotification()
                insertDatabase(database)
                delay(1000)
            }
        }
    }

    private fun showStopWatch() {
        if (speed >= MIN_SPEED_THRESHOLD) {
            if (!isCurrentlyMoving) {
                lazyState.stop()
                movementState.start()
                isCurrentlyMoving = true
            }
        } else {
            if (isCurrentlyMoving) {
                movementState.stop()
            }
        }

        val duration = movementState.getTotalDuration()
        val movementHours = duration.toHours()
        val movementMinutes = (duration.toMinutes() % 60)
        val movementSeconds = (duration.seconds % 60)
        movementFormattedTime = String.format("%02d:%02d:%02d", movementHours, movementMinutes, movementSeconds)
    }

    private fun showLazyStopWatch() {
        Log.d("StopWatch", "Speed: $speed, isCurrentlyMoving: $isCurrentlyMoving")

        if (speed < MIN_SPEED_THRESHOLD) {
            Log.d("StopWatch", "Speed below threshold")
            if (isCurrentlyMoving) {
                Log.d("StopWatch", "Transitioning from moving to lazy")
                movementState.stop()
                lazyState.start()
                isCurrentlyMoving = false
            } else if (lazyState.getCurrentSegment() == null) {
                Log.d("StopWatch", "Starting new lazy segment")
                lazyState.start()
            }
        } else {
            if (!isCurrentlyMoving) {
                Log.d("StopWatch", "Stopping lazy state")
                lazyState.stop()
            }
        }

        val duration = lazyState.getTotalDuration()
        Log.d("StopWatch", "Lazy duration - Hours: ${duration.toHours()}, " +
                "Minutes: ${duration.toMinutes() % 60}, " +
                "Seconds: ${duration.seconds % 60}")

        val lazyHours = duration.toHours()
        val lazyMinutes = (duration.toMinutes() % 60)
        val lazySeconds = (duration.seconds % 60)
        lazyFormattedTime = String.format("%02d:%02d:%02d", lazyHours, lazyMinutes, lazySeconds)
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
/*            val metrics = database.metricDao().getAllMetrics()
            metrics.forEach { metric ->
                Log.d("DatabaseDebug", "Metric: $metric")
            }*/
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
    }

    private fun updateNotification(newContent: String) {
        runOnUiThread {
            notificationBuilder
                .setStyle(NotificationCompat.BigTextStyle().bigText(newContent))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setAutoCancel(false)
            notificationManager.notify(1, notificationBuilder.build())
        }
    }

    private suspend fun insertDatabase(database: FitnessTrackerDatabase) {
        withContext(Dispatchers.IO) {
            val metric = Metric(
                eventId = eventId,
                heartRate = 0,
                heartRateDevice = "Chest Strap",
                speed = speed,
                distance = distance,
                cadence = 0,
                lap = lap,
                timeInMilliseconds = currentTimeMillis(),
                unity = "metric",
                elevation = 0f,
                elevationGain = 0f,
                elevationLoss = 0f
            )
            database.metricDao().insertMetric(metric)
            Log.d("ForegroundService: ", metric.timeInMilliseconds.toString())

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
        createBackgroundCoroutine()
        startForeground(1, notificationBuilder.build())

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called, isStoppingIntentionally: $isStoppingIntentionally")
        customLocationListener?.cleanup()
        customLocationListener = null
        isHeartbeatActive = false
        heartbeatJob.cancel()
        releaseWakeLock()
        EventBus.getDefault().unregister(this)
        serviceJob.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Schedule service restart
        val restartIntent = Intent(applicationContext, ForegroundService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            currentTimeMillis() + 1000,
            pendingIntent
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GeoTracker",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                enableLights(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMemoryPressureEvent(event: MainActivity.MemoryPressureEvent) {
        when (event.level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                System.gc()
                Log.w("ForegroundService", "Handling critical memory pressure")
                EventBus.getDefault().post(CustomLocationListener.AdjustLocationFrequencyEvent(true))
            }
        }
    }

     companion object {
         private const val CHANNEL_ID = "ForegroundServiceChannel"
         private const val MIN_SPEED_THRESHOLD = 2.5f
         private const val EVENT_TIMEOUT_MS = 2000
         private const val HEARTBEAT_INTERVAL = 30_000L
         private const val LOCATION_UPDATE_TIMEOUT = 60_000L
         private var isStoppingIntentionally = false
         private const val TAG = "ForegroundService"

         fun isServiceRunning(context: Context): Boolean {
             val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
             return manager.getRunningServices(Integer.MAX_VALUE)
                 .any { it.service.className == ForegroundService::class.java.name }
         }

         private fun triggerLocationChange(customLocationListener: CustomLocationListener) {
             val mockLocationStart = android.location.Location(LocationManager.GPS_PROVIDER).apply {
                 latitude = 48.181894
                 longitude = 16.360820
                 speed = 3.0f
                 altitude = 10.0
                 accuracy = 5.0f
             }

             val mockLocationEnd = android.location.Location(LocationManager.GPS_PROVIDER).apply {
                 latitude = 48.1989050245536
                 longitude = 16.94202690892697
                 speed = 3.0f
                 altitude = 10.0
                 accuracy = 5.0f
             }

             customLocationListener.onLocationChanged(mockLocationStart)
             customLocationListener.onLocationChanged(mockLocationEnd)
         }
     }
    fun stopService() {
        Log.d(TAG, "stopService called")
        isStoppingIntentionally = true
        stopSelf()
    }
    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStopServiceEvent(event: MainActivity.StopServiceEvent) {
        Log.d(TAG, "Received stop event")
        isStoppingIntentionally = true
        customLocationListener?.cleanup()
        customLocationListener = null
        isHeartbeatActive = false
        heartbeatJob.cancel()
        serviceJob.cancel()
        EventBus.getDefault().unregister(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}