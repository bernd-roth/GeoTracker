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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import at.co.netconsulting.geotracker.composables.BottomSheetContent
import at.co.netconsulting.geotracker.composables.EditEventScreen
import at.co.netconsulting.geotracker.composables.EventsScreen
import at.co.netconsulting.geotracker.composables.MapScreen
import at.co.netconsulting.geotracker.composables.SettingsScreen
import at.co.netconsulting.geotracker.composables.StatisticsScreen
import at.co.netconsulting.geotracker.data.LocationData
import at.co.netconsulting.geotracker.data.Metrics
import androidx.navigation.NavType
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.osmdroid.config.Configuration

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

    // Navigation routes
    object Routes {
        const val EVENTS = "events"
        const val EDIT_EVENT = "edit_event/{eventId}"

        // Create actual navigation path with parameters
        fun editEvent(eventId: Int) = "edit_event/$eventId"
    }

    // Permissions
    private val foregroundPermissions = listOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.WAKE_LOCK
    )
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

        // Set content to our Compose UI
        setContent {
            MainScreen()
        }

        checkServiceStateAndUpdateUI()
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
        // First, check and request foreground permissions
        if (!areForegroundPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                foregroundPermissions.toTypedArray(),
                FOREGROUND_PERMISSION_REQUEST_CODE
            )
        } else {
            // Only if foreground permissions are granted, check and request background location
            checkAndRequestBackgroundPermission()
        }
    }

    private fun areForegroundPermissionsGranted(): Boolean {
        return foregroundPermissions.all { permission ->
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
                // If foreground permissions are granted, proceed to background permission
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    checkAndRequestBackgroundPermission()
                } else {
                    // Some foreground permissions denied
                    val deniedPermissions = permissions.zip(grantResults.toList())
                        .filter { it.second != PackageManager.PERMISSION_GRANTED }
                        .map { it.first }

                    if (deniedPermissions.isNotEmpty()) {
                        Toast.makeText(
                            this,
                            "Some permissions were denied. Functionality may be limited.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
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

        // List of tab titles
        val tabs = listOf(
            getString(R.string.map),
            getString(R.string.statistics),
            getString(R.string.events),
            getString(R.string.settings),
        )

        // Satellite info
        val satelliteInfo by satelliteInfoManager.currentSatelliteInfo.collectAsState()

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
                                text = { Text(title) }
                            )
                        }
                    }
                },
                content = { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        when (selectedTabIndex) {
                            0 -> MapScreen(
                                onNavigateToSettings = {
                                    selectedTabIndex = 3 // Navigate to Settings tab
                                }
                            )
                            1 -> StatisticsScreen()
                            2 -> AppNavHost(mainNavController)
                            3 -> SettingsScreen()
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun AppNavHost(navController: NavHostController) {
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
                    }
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