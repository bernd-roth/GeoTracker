package at.co.netconsulting.geotracker

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.FOREGROUND_SERVICE
import android.Manifest.permission.FOREGROUND_SERVICE_LOCATION
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import at.co.netconsulting.geotracker.data.LocationEvent
import at.co.netconsulting.geotracker.service.BackgroundLocationService
import at.co.netconsulting.geotracker.service.ForegroundService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay


class MainActivity : ComponentActivity() {
    private var latitudeState = mutableDoubleStateOf(0.0)
    private var longitudeState = mutableDoubleStateOf(0.0)
    private var horizontalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var altitudeState = mutableDoubleStateOf(0.0)
    private var speedState = mutableFloatStateOf(0.0f)
    private var speedAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var verticalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var coveredDistanceState = mutableDoubleStateOf(0.0)
    private lateinit var mapView: MapView
    private lateinit var polyline: Polyline
    private lateinit var locationManager: LocationManager
    private var usedInFixCount = 0
    private var satelliteCount = 0
    private lateinit var marker: Marker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Initialize OSMDroid configuration
        val context = applicationContext
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_pref", MODE_PRIVATE))

        val intent = Intent(context, BackgroundLocationService::class.java)
        context.startService(intent)

        // Request location updates
        //requestLocationUpdates(context, locationManager, this, this)

        // Register EventBus
        EventBus.getDefault().register(this)

        setContent {
            MainScreen()
        }
    }

    // Publisher/Subscriber
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLocationEvent(event: LocationEvent) {
        Log.d("MainActivity",
            "Latitude: ${event.latitude}," +
                " Longitude: ${event.longitude}," +
                " Speed: ${event.speed}," +
                " SpeedAccuracyInMeters: ${event.speedAccuracyMetersPerSecond}," +
                " Altitude: ${event.altitude}," +
                " HorizontalAccuracyInMeters: ${event.horizontalAccuracy}," +
                " VerticalAccuracyInMeters: ${event.verticalAccuracyMeters}" +
                " CoveredDistance: ${event.coveredDistance}")
        latitudeState.value = event.latitude
        longitudeState.value = event.longitude
        speedState.value = event.speed
        speedAccuracyInMetersState.value = event.speedAccuracyMetersPerSecond
        altitudeState.value = event.altitude
        verticalAccuracyInMetersState.value = event.verticalAccuracyMeters
        coveredDistanceState.value = event.coveredDistance

        if(isServiceRunning("at.co.netconsulting.geotracker.service.ForegroundService")) {
            drawPolyline(latitudeState.value, longitudeState.value)
        } else {
            mapView.controller.setCenter(GeoPoint(latitudeState.value, longitudeState.value))
            mapView.invalidate()
        }
    }

    private fun isServiceRunning(serviceName: String): Boolean {
        var serviceRunning = false
        val am = this.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val l = am.getRunningServices(50)
        val i: Iterator<ActivityManager.RunningServiceInfo> = l.iterator()
        while (i.hasNext()) {
            val runningServiceInfo = i
                .next()

            if (runningServiceInfo.service.className == serviceName) {
                serviceRunning = true

                if (runningServiceInfo.foreground) {
                    //service run in foreground
                }
            }
        }
        return serviceRunning
    }

    private fun drawPolyline(latitude: Double, longitude: Double) {
        val size = polyline.actualPoints.size

        if(size==1) {
            createMarker()
            //mapView.controller.setZoom(17.0)
            mapView.controller.setCenter(GeoPoint(latitude, longitude))
            polyline.addPoint(GeoPoint(latitude, longitude))
        } else {
            //mapView.controller.setZoom(17.0)
            mapView.controller.setCenter(GeoPoint(latitude, longitude))
            polyline.addPoint(GeoPoint(latitude, longitude))
        }
        mapView.invalidate()
    }

    private fun createMarker() {
        marker.position = polyline.actualPoints[0]
        marker.title = getString(R.string.marker_title)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.startflag)
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    // LocationListener
//    override fun onLocationChanged(location: Location) {
//        // Update state variables with new latitude and longitude
//        latitudeState.value = location.latitude
//        longitudeState.value = location.longitude
//        horizontalAccuracyInMetersState.value = location.accuracy
//        verticalAccuracyInMetersState.value = location.verticalAccuracyMeters
//        altitudeState.value = location.altitude
//        speedState.value = location.speed
//        speedAccuracyInMetersState.value = location.speedAccuracyMetersPerSecond
//
//        Log.d("MainActivity: onLocationChanged: ",
//            "Latitude: ${latitudeState.value}," +
//                 " Longitude: ${longitudeState.value}," +
//                 " Speed: ${speedState.value}," +
//                 " SpeedAccuracyInMeters: ${speedAccuracyInMetersState.value}," +
//                 " Altitude: ${altitudeState.value}," +
//                 " VerticalAccuracyInMeters: ${verticalAccuracyInMetersState.value}")
//
//        //rotation overlay
////        val rotationGestureOverlay = RotationGestureOverlay(mapView)
////        rotationGestureOverlay.isEnabled
////        mapView.setMultiTouchControls(true)
////        mapView.overlays.add(rotationGestureOverlay)
//
//        // Update map center to the latest location
//        mapView.controller.setCenter(GeoPoint(latitudeState.value, longitudeState.value))
//
//        //zoom to new latitude and longitude
//        mapView.controller.setZoom(18.0)
//
//        // Refresh the map view to show the updated polyline
//        mapView.invalidate()
//    }

    private fun requestLocationUpdates(
        context: Context,
        locationManager: LocationManager,
        listener: LocationListener,
        activity: Activity
    ) {
        if (ActivityCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.registerGnssStatusCallback(object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    super.onSatelliteStatusChanged(status)

                    satelliteCount = status.satelliteCount
                    usedInFixCount = (0 until satelliteCount).count { status.usedInFix(it) }

                    Log.d("MainActivty: requestLocationUpdates(): SatelliteInfo", "Visible Satellites: $satelliteCount")
                    Log.d("MainActivity: requestLocationUpdates(): SatelliteInfo", "Satellites Used in Fix: $usedInFixCount")
                }
            },null)
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                1000,
                1f,
                listener)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        // Coroutine scope to auto-hide the top bar after a delay
        val coroutineScope = rememberCoroutineScope()

        // Create a scaffold state for controlling the bottom sheet
        val scaffoldState = rememberBottomSheetScaffoldState()

        // Remember the selected tab index
        var selectedTabIndex by remember { mutableStateOf(0) }

        // List of tab titles
        val tabs = listOf(getString(R.string.map), getString(R.string.statistics), getString(R.string.settings))

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                BottomSheetContent(
                    latitude = latitudeState.value,
                    longitude = longitudeState.value,
                    speed = speedState.value,
                    speedAccuracyInMeters = speedState.value,
                    altitude = altitudeState.value,
                    verticalAccuracyInMeters = verticalAccuracyInMetersState.value,
                    horizontalAccuracyInMeters = horizontalAccuracyInMetersState.value,
                    numberOfSatellites = satelliteCount,
                    usedNumberOfSatellites = usedInFixCount,
                    coveredDistance = coveredDistanceState.value
                )
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
                            0 -> MapScreen() // The actual map
                            1 -> StatisticsScreen() // Statistics content
                            2 -> SettingsScreen() // Settings content
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun MapScreen() {
        Column {
            OpenStreetMapView() // The map view
        }
    }

    @Composable
    fun StatisticsScreen() {
        // Example UI for statistics
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "Statistics", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            // Add actual statistics content here
            Text("Speed: 0.0 km/h")
            Text("Covered Distance: 0.0 km")
        }
    }

    @Composable
    fun SettingsScreen() {
        // Example UI for settings
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "Settings", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            // Add actual settings content here
            Text("Setting 1")
            Text("Setting 2")
        }
    }

    @Composable
    fun BottomSheetContent(
        latitude: Double,
        longitude: Double,
        speed: Float,
        speedAccuracyInMeters: Float,
        altitude: Double,
        verticalAccuracyInMeters: Float,
        horizontalAccuracyInMeters: Float,
        numberOfSatellites: Int,
        usedNumberOfSatellites: Int,
        coveredDistance: Double
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            //Text(text = "Latitude: ${"%.2f".format(latitude)} Longitude: ${"%.2f".format(longitude)}", fontSize = 10.sp, color = Color.Black)
            Text(text = "Latitude: $latitude Longitude: $longitude", fontSize = 10.sp, color = Color.Black)
            Text("Speed: $speed km/h", fontSize = 10.sp, color = Color.Black)
            Text("Speed accuracy: ±${"%.2f".format(speedAccuracyInMeters)} m/s", fontSize = 10.sp, color = Color.Black)
            Text("Altitude: ${"%.2f".format(altitude)} meter", fontSize = 10.sp, color = Color.Black)
            Text("Horizontal accuracy: ±${"%.2f".format(horizontalAccuracyInMeters)} meter", fontSize = 10.sp, color = Color.Black)
            Text("Vertical accuracy: ±${"%.2f".format(verticalAccuracyInMeters)} meter", fontSize = 10.sp, color = Color.Black)
            Text("Satellites: $usedNumberOfSatellites/$numberOfSatellites ", fontSize = 10.sp, color = Color.Black)
            Text("Covered distance: ${"%.2f".format(coveredDistance/1000)} Km", fontSize = 10.sp, color = Color.Black)
            //Text("Vertical accuracy: ${"%.2f".format(verticalAccuracyInMeters)} meter", fontSize = 18.sp, color = Color.Black)
        }
    }

    @Composable
    fun OpenStreetMapView() {
        val context = LocalContext.current

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = {
                mapView = MapView(context).apply {
                    setTileSource(customTileSource)
                    setMultiTouchControls(true)
                    controller.setZoom(9.0)
                    controller.setCenter(GeoPoint(0.0, 0.0))

                    // Initialize polyline for tracking locations
                    polyline = Polyline().apply {
                        width = 10f
                        color = context.resources.getColor(android.R.color.holo_purple, null)
                    }
                    overlays.add(polyline)
                }
                marker = Marker(mapView)
                //compass
    //            var compassOverlay = CompassOverlay(applicationContext, InternalCompassOrientationProvider(applicationContext), mapView)
    //            compassOverlay.enableCompass()
    //            compassOverlay.orientation
    //            mapView.overlays.add(compassOverlay)

                //scaleBar
                val dm : DisplayMetrics = applicationContext.resources.displayMetrics
                val scaleBarOverlay = ScaleBarOverlay(mapView)
                scaleBarOverlay.setCentred(true)
                scaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 2000)
                mapView.overlays.add(scaleBarOverlay)

                //my location overlay
                var mLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
                mLocationOverlay.enableMyLocation()
                mapView.overlays.add(mLocationOverlay)
                mapView
            }, modifier = Modifier.fillMaxSize())
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-16).dp, y = (-180).dp)
                    .padding(16.dp)
                    .size(50.dp),
                shape = CircleShape,
                color = Color.Red,
                shadowElevation = 8.dp,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            // Recording action here
                            Toast
                                .makeText(context, "Recording started!", Toast.LENGTH_SHORT)
                                .show()
                            val stopIntent = Intent(context, BackgroundLocationService::class.java)
                            context.stopService(stopIntent)
                            val intent = Intent(context, ForegroundService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                            // Remove listener from MainActivity
                            // because LocationListener is implemented in CustomLocationListener
                            //locationManager.removeUpdates(this@MainActivity)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow, // Use mic icon or any other
                        contentDescription = "Start Recording",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // Override
    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(this, ForegroundService::class.java)
        stopService(intent)
        EventBus.getDefault().unregister(this)
    }
}