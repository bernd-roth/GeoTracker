package at.co.netconsulting.geotracker

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.co.netconsulting.geotracker.composables.BottomSheetContent
import at.co.netconsulting.geotracker.composables.CompetitionsScreen
import at.co.netconsulting.geotracker.composables.EditEventScreen
import at.co.netconsulting.geotracker.composables.EventsScreen
import at.co.netconsulting.geotracker.composables.MapScreen
import at.co.netconsulting.geotracker.composables.SettingsScreen
import at.co.netconsulting.geotracker.composables.StatisticsScreen
import at.co.netconsulting.geotracker.data.LocationData
import at.co.netconsulting.geotracker.data.Metrics
import at.co.netconsulting.geotracker.data.GpsStatus
import at.co.netconsulting.geotracker.tools.GpsStatusEvaluator
import at.co.netconsulting.geotracker.reminder.ReminderManager
import at.co.netconsulting.geotracker.tools.AlarmPermissionHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint

class MainActivity : ComponentActivity() {
    private var latitudeState = mutableDoubleStateOf(-999.0)
    private var longitudeState = mutableDoubleStateOf(-999.0)
    private var horizontalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var altitudeState = mutableDoubleStateOf(0.0)
    private var speedState = mutableFloatStateOf(0.0f)
    private var speedAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var verticalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var coveredDistanceState = mutableDoubleStateOf(0.0)
    private val locationEventState = mutableStateOf<Metrics?>(null)
    private lateinit var satelliteInfoManager: SatelliteInfoManager

    // heart rate
    private var heartRateEventName by mutableStateOf("")
    private var heartRateMetrics by mutableStateOf<List<at.co.netconsulting.geotracker.domain.Metric>>(emptyList())

    // weather data
    private var weatherEventName by mutableStateOf("")
    private var weatherMetrics by mutableStateOf<List<at.co.netconsulting.geotracker.domain.Metric>>(emptyList())

    // barometer data
    private var barometerEventName by mutableStateOf("")
    private var barometerMetrics by mutableStateOf<List<at.co.netconsulting.geotracker.domain.Metric>>(emptyList())

    // altitude data
    private var altitudeEventName by mutableStateOf("")
    private var altitudeMetrics by mutableStateOf<List<at.co.netconsulting.geotracker.domain.Metric>>(emptyList())

    // Route navigation state
    private val routeToDisplay = mutableStateOf<List<GeoPoint>?>(null)

    // Navigation routes
    object Routes {
        const val EVENTS = "events"
        const val EDIT_EVENT = "edit_event/{eventId}"
        const val HEART_RATE_DETAIL = "heart_rate_detail"
        const val WEATHER_DETAIL = "weather_detail"
        const val BAROMETER_DETAIL = "barometer_detail"
        const val ALTITUDE_DETAIL = "altitude_detail"

        // Create actual navigation path with parameters
        fun editEvent(eventId: Int) = "edit_event/$eventId"
        fun heartRateDetail() = "heart_rate_detail"
        fun weatherDetail() = "weather_detail"
        fun barometerDetail() = "barometer_detail"
        fun altitudeDetail() = "altitude_detail"
    }

    // Permission constants
    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    private val FOREGROUND_PERMISSION_REQUEST_CODE = 1001
    private val BACKGROUND_PERMISSION_REQUEST_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize satellite info manager
        satelliteInfoManager = SatelliteInfoManager(this)

        requestPermissionsInSequence()

        // Initialize OSMDroid configuration
        val context = applicationContext
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_pref", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "GeoTracker/1.0"

        // Register for EventBus events
        EventBus.getDefault().register(this)

        // Set content to our Compose UI - ALWAYS use light theme for the app UI
        setContent {
            // Always use light color scheme for the app UI
            // Only the map tiles will change to dark mode based on the setting
            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                MainScreen()
            }
        }
        ensureAlarmsAreScheduled()
        openedFromReminderNotification()
        checkServiceStateAndUpdateUI()
    }

    // Add GPS Status evaluation method
    @Composable
    fun getCurrentGpsStatus(): GpsStatus {
        val satelliteInfo by satelliteInfoManager.currentSatelliteInfo.collectAsState()

        return GpsStatusEvaluator.evaluateGpsStatus(
            latitude = latitudeState.value,
            longitude = longitudeState.value,
            horizontalAccuracy = horizontalAccuracyInMetersState.value,
            verticalAccuracy = verticalAccuracyInMetersState.value,
            speedAccuracy = speedAccuracyInMetersState.value,
            satelliteCount = satelliteInfo.totalSatellites,
            usedSatelliteCount = satelliteInfo.visibleSatellites
        )
    }

    private fun ensureAlarmsAreScheduled() {
        // Check and ensure all alarms are properly scheduled
        val reminderManager = ReminderManager(this)
        reminderManager.ensureAlarmsAreScheduled()

        // Also check exact alarm permission and request if needed
        if (!AlarmPermissionHelper.checkExactAlarmPermission(this)) {
            Log.w("MainActivity", "Exact alarm permission not granted")
        }
    }

    private fun openedFromReminderNotification() {
        intent?.let { intent ->
            if (intent.getBooleanExtra("navigate_to_competitions", false)) {
                Log.d("MainActivity", "App opened from competition reminder notification")

                // Store navigation flag in SharedPreferences to handle it in MainScreen
                getSharedPreferences("Navigation", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("navigate_to_competitions", true)
                    .putInt("competition_id", intent.getIntExtra("competition_id", -1))
                    .apply()
            }
        }
    }

    private fun checkServiceStateAndUpdateUI() {
        // Check if service is running
        val isServiceRunning = isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")

        // Update shared preferences to match actual service state
        getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_recording", isServiceRunning)
            .apply()

        if (isServiceRunning) {
            Log.d("MainActivity", "ForegroundService is running, updated recording state to true")
        } else {
            Log.d("MainActivity", "ForegroundService is not running, updated recording state to false")
        }
    }

    private fun requestPermissionsInSequence() {
        // Only request permissions that are actually needed and runtime permissions
        val runtimePermissionsToRequest = getRuntimePermissionsToRequest()

        if (runtimePermissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                runtimePermissionsToRequest.toTypedArray(),
                FOREGROUND_PERMISSION_REQUEST_CODE
            )
        } else {
            // All runtime permissions already granted, check background permission
            checkAndRequestBackgroundPermission()
        }
    }

    private fun getRuntimePermissionsToRequest(): List<String> {
        val permissionsToRequest = mutableListOf<String>()

        // Core location permissions - always needed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // Storage permission - only for Android 9 and below
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Notification permission - only for Android 13+, but don't treat as critical
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Always include these if not granted (they're automatically granted but good to check)
        listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        ).forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        return permissionsToRequest
    }

    private fun getCriticalPermissions(): List<String> {
        // Only permissions that are critical for core functionality
        return listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private fun areForegroundPermissionsGranted(): Boolean {
        return getCriticalPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    backgroundLocationPermission
                ) != PackageManager.PERMISSION_GRANTED) {

                // Show explanation dialog first
                AlertDialog.Builder(this)
                    .setTitle("Background Location Permission Required")
                    .setMessage("This app needs background location access to track your location even when the app is closed. Please grant this permission on the next screen.")
                    .setPositiveButton("OK") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(backgroundLocationPermission),
                            BACKGROUND_PERMISSION_REQUEST_CODE
                        )
                    }
                    .create()
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            FOREGROUND_PERMISSION_REQUEST_CODE -> {
                // Check only critical permissions for the error message
                val criticalPermissions = getCriticalPermissions()
                val deniedCriticalPermissions = permissions.zip(grantResults.toList())
                    .filter { (permission, result) ->
                        permission in criticalPermissions && result != PackageManager.PERMISSION_GRANTED
                    }

                if (deniedCriticalPermissions.isNotEmpty()) {
                    Toast.makeText(
                        this,
                        "Location permissions are required for core functionality.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // All critical permissions granted, check non-critical ones
                    val deniedNonCriticalPermissions = permissions.zip(grantResults.toList())
                        .filter { (permission, result) ->
                            permission !in criticalPermissions && result != PackageManager.PERMISSION_GRANTED
                        }

                    if (deniedNonCriticalPermissions.isNotEmpty()) {
                        // Only show info about non-critical permissions if user wants to know
                        val deniedNames = deniedNonCriticalPermissions.map { it.first }
                        if (deniedNames.contains(Manifest.permission.POST_NOTIFICATIONS)) {
                            Toast.makeText(
                                this,
                                "Notifications disabled - you won't receive tracking alerts.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    // Proceed to background permission regardless
                    checkAndRequestBackgroundPermission()
                }
            }
            BACKGROUND_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Background location permission granted
                    Log.d("Permissions", "Background location permission granted")
                } else {
                    // Background location permission denied
                    Toast.makeText(
                        this,
                        "Background location permission denied. Location tracking will only work when the app is open.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // arePermissionsGranted() needs to be updated to check both foreground and background
    private fun arePermissionsGranted(): Boolean {
        val foregroundGranted = areForegroundPermissionsGranted()
        val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, backgroundLocationPermission) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Background permission not needed for Android 9 and below
        }

        return foregroundGranted && backgroundGranted
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        // Create a scaffold state for the bottom sheet
        val scaffoldState = rememberBottomSheetScaffoldState()

        // Remember the selected tab index
        var selectedTabIndex by remember { mutableStateOf(0) }

        // Main navigation controller for edit event navigation
        val mainNavController = rememberNavController()

        // Updated list of tab titles to include Competitions
        val tabs = listOf(
            getString(R.string.map),
            getString(R.string.statistics),
            getString(R.string.events),
            getString(R.string.competitions),
            getString(R.string.settings),
        )

        // Satellite info
        val satelliteInfo by satelliteInfoManager.currentSatelliteInfo.collectAsState()

        // Get current GPS status
        val currentGpsStatus = getCurrentGpsStatus()

        // Check for navigation from notification
        LaunchedEffect(Unit) {
            val navigationPrefs = getSharedPreferences("Navigation", Context.MODE_PRIVATE)
            if (navigationPrefs.getBoolean("navigate_to_competitions", false)) {
                selectedTabIndex = 3 // Navigate to competitions tab
                // Clear the flag
                navigationPrefs.edit()
                    .remove("navigate_to_competitions")
                    .remove("competition_id")
                    .apply()
            }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                if(latitudeState.value != -999.0 && longitudeState.value != -999.0) {
                    BottomSheetContent(
                        latitude = latitudeState.value,
                        longitude = longitudeState.value,
                        speed = speedState.value,
                        speedAccuracyInMeters = speedAccuracyInMetersState.value,
                        altitude = altitudeState.value,
                        verticalAccuracyInMeters = verticalAccuracyInMetersState.value,
                        horizontalAccuracyInMeters = horizontalAccuracyInMetersState.value,
                        numberOfSatellites = satelliteInfo.totalSatellites,
                        usedNumberOfSatellites = satelliteInfo.visibleSatellites,
                        coveredDistance = coveredDistanceState.value
                    )
                } else {
                    BottomSheetContent(
                        latitude = Double.NaN,
                        longitude = Double.NaN,
                        speed = speedState.value,
                        speedAccuracyInMeters = speedAccuracyInMetersState.value,
                        altitude = altitudeState.value,
                        verticalAccuracyInMeters = verticalAccuracyInMetersState.value,
                        horizontalAccuracyInMeters = horizontalAccuracyInMetersState.value,
                        numberOfSatellites = satelliteInfo.totalSatellites,
                        usedNumberOfSatellites = satelliteInfo.visibleSatellites,
                        coveredDistance = coveredDistanceState.value
                    )
                }
            },
            sheetPeekHeight = 20.dp,
            sheetContentColor = Color.Transparent,
            sheetContainerColor = Color.Transparent
        ) {
            Scaffold(
                topBar = {
                    // TabRow for navigation
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                },
                content = { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (selectedTabIndex) {
                            0 -> MapScreen(
                                onNavigateToSettings = {
                                    selectedTabIndex = 4 // Navigate to Settings tab (now index 4)
                                },
                                routeToDisplay = routeToDisplay.value,
                                onRouteDisplayed = {
                                    // Clear the route after it's been displayed
                                    routeToDisplay.value = null
                                },
                                getCurrentGpsStatus = { currentGpsStatus } // Pass GPS status function
                            )
                            1 -> StatisticsScreen()
                            2 -> AppNavHost(
                                mainNavController,
                                onNavigateToMapWithRoute = { locationPoints ->
                                    // Set the route to display and switch to map tab
                                    routeToDisplay.value = locationPoints
                                    selectedTabIndex = 0
                                }
                            )
                            3 -> CompetitionsScreen()
                            4 -> SettingsScreen()
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun AppNavHost(
        navController: NavHostController,
        onNavigateToMapWithRoute: (List<GeoPoint>) -> Unit = {}
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.EVENTS
        ) {
            // Events list screen
            composable(Routes.EVENTS) {
                Log.d("MainActivity", "Navigated to Events screen")
                EventsScreen(
                    onEditEvent = { eventId ->
                        Log.d("MainActivity", "Navigating to edit event with ID: $eventId")
                        navController.navigate(Routes.editEvent(eventId))
                    },
                    onNavigateToHeartRateDetail = { eventName, metrics ->
                        Log.d("MainActivity", "Navigating to heart rate detail for event: $eventName")
                        // Store the data for the heart rate screen
                        heartRateEventName = eventName
                        heartRateMetrics = metrics
                        navController.navigate(Routes.heartRateDetail())
                    },
                    onNavigateToWeatherDetail = { eventName, metrics ->
                        Log.d("MainActivity", "Navigating to weather detail for event: $eventName")
                        // Store the data for the weather screen
                        weatherEventName = eventName
                        weatherMetrics = metrics
                        navController.navigate(Routes.weatherDetail())
                    },
                    onNavigateToBarometerDetail = { eventName, metrics ->
                        Log.d("MainActivity", "Navigating to barometer detail for event: $eventName")
                        // Store the data for the barometer screen
                        barometerEventName = eventName
                        barometerMetrics = metrics
                        navController.navigate(Routes.barometerDetail())
                    },
                    onNavigateToAltitudeDetail = { eventName, metrics ->
                        Log.d("MainActivity", "Navigating to altitude detail for event: $eventName")
                        // Store the data for the altitude screen
                        altitudeEventName = eventName
                        altitudeMetrics = metrics
                        navController.navigate(Routes.altitudeDetail())
                    },
                    onNavigateToMapWithRoute = onNavigateToMapWithRoute
                )
            }

            // Edit event screen
            composable(
                route = Routes.EDIT_EVENT,
                arguments = listOf(
                    navArgument("eventId") {
                        type = NavType.IntType
                    }
                )
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getInt("eventId") ?: 0
                Log.d("MainActivity", "Showing edit event screen with ID: $eventId")

                EditEventScreen(
                    eventId = eventId,
                    onNavigateBack = {
                        Log.d("MainActivity", "Navigating back from edit event")
                        navController.popBackStack()
                    }
                )
            }

            // Heart rate detail screen
            composable(Routes.HEART_RATE_DETAIL) {
                Log.d("MainActivity", "Showing heart rate detail screen")

                // Import the HeartRateDetailScreen here
                at.co.netconsulting.geotracker.composables.HeartRateDetailScreen(
                    eventName = heartRateEventName,
                    metrics = heartRateMetrics,
                    onBackClick = {
                        Log.d("MainActivity", "Navigating back from heart rate detail")
                        navController.popBackStack()
                        // Clear the data when navigating back
                        heartRateEventName = ""
                        heartRateMetrics = emptyList()
                    }
                )
            }

            // Weather detail screen
            composable(Routes.WEATHER_DETAIL) {
                Log.d("MainActivity", "Showing weather detail screen")

                // Import the WeatherDetailScreen here
                at.co.netconsulting.geotracker.composables.WeatherDetailScreen(
                    eventName = weatherEventName,
                    metrics = weatherMetrics,
                    onBackClick = {
                        Log.d("MainActivity", "Navigating back from weather detail")
                        navController.popBackStack()
                        // Clear the data when navigating back
                        weatherEventName = ""
                        weatherMetrics = emptyList()
                    }
                )
            }

            // Barometer detail screen - Add this new composable
            composable(Routes.BAROMETER_DETAIL) {
                Log.d("MainActivity", "Showing barometer detail screen")

                // Import the BarometerDetailScreen here
                at.co.netconsulting.geotracker.composables.BarometerDetailScreen(
                    eventName = barometerEventName,
                    metrics = barometerMetrics,
                    onBackClick = {
                        Log.d("MainActivity", "Navigating back from barometer detail")
                        navController.popBackStack()
                        // Clear the data when navigating back
                        barometerEventName = ""
                        barometerMetrics = emptyList()
                    }
                )
            }

            composable(Routes.ALTITUDE_DETAIL) {
                Log.d("MainActivity", "Showing altitude detail screen")

                // Import the AltitudeDetailScreen here
                at.co.netconsulting.geotracker.composables.AltitudeDetailScreen(
                    eventName = altitudeEventName,
                    metrics = altitudeMetrics,
                    onBackClick = {
                        Log.d("MainActivity", "Navigating back from altitude detail")
                        navController.popBackStack()
                        // Clear the data when navigating back
                        altitudeEventName = ""
                        altitudeMetrics = emptyList()
                    }
                )
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMetricsEvent(metrics: Metrics) {
        Log.d(
            "MainActivity",
            "Location update received: " +
                    "Lat=${metrics.latitude}, " +
                    "Lon=${metrics.longitude}, " +
                    "Speed=${metrics.speed}, " +
                    "Distance=${metrics.coveredDistance}"
        )

        // Update state with new location data
        locationEventState.value = metrics
        latitudeState.value = metrics.latitude
        longitudeState.value = metrics.longitude

        // Only update speed if recording (service is running)
        if (isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")) {
            speedState.value = metrics.speed
            speedAccuracyInMetersState.value = metrics.speedAccuracyMetersPerSecond
        } else {
            // Reset speed when not recording
            speedState.value = 0.0f
            speedAccuracyInMetersState.value = 0.0f
        }

        // Always update these values
        altitudeState.value = metrics.altitude
        horizontalAccuracyInMetersState.value = metrics.horizontalAccuracy
        verticalAccuracyInMetersState.value = metrics.verticalAccuracyMeters
        coveredDistanceState.value = metrics.coveredDistance
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLocationDataEvent(locationData: LocationData) {
        Log.d(
            "MainActivity",
            "LocationData update received: " +
                    "Lat=${locationData.latitude}, " +
                    "Lon=${locationData.longitude}, " +
                    "Speed=${locationData.speed}, " +
                    "Distance=${locationData.coveredDistance}"
        )

        // Update state with new location data
        latitudeState.value = locationData.latitude
        longitudeState.value = locationData.longitude

        // Update speed with new location data
        speedState.value = locationData.speed
        speedAccuracyInMetersState.value = locationData.speedAccuracyMetersPerSecond

        // Always update these values
        altitudeState.value = locationData.altitude
        horizontalAccuracyInMetersState.value = locationData.horizontalAccuracy
        verticalAccuracyInMetersState.value = locationData.verticalAccuracy
        coveredDistanceState.value = locationData.coveredDistance
    }

    private fun isServiceRunning(serviceName: String): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { service ->
                serviceName == service.service.className && service.foreground
            }
    }

    override fun onResume() {
        super.onResume()
        satelliteInfoManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        satelliteInfoManager.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }
}