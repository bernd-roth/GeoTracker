package at.co.netconsulting.geotracker

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.FOREGROUND_SERVICE
import android.Manifest.permission.FOREGROUND_SERVICE_LOCATION
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import at.co.netconsulting.geotracker.service.ForegroundService
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : ComponentActivity(), LocationListener {
    private var latitudeState = mutableDoubleStateOf(0.0)
    private var longitudeState = mutableDoubleStateOf(0.0)
    private var horizontalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var altitudeState = mutableDoubleStateOf(0.0)
    private var speedState = mutableFloatStateOf(0.0f)
    private var speedAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private var verticalAccuracyInMetersState = mutableFloatStateOf(0.0f)
    private lateinit var mapView: MapView
    private lateinit var polyline: Polyline
    private lateinit var locationManager: LocationManager
    private var usedInFixCount = 0
    private var satelliteCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Initialize OSMDroid configuration
        val context = applicationContext
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_pref", MODE_PRIVATE))

        // Request location updates
        requestLocationUpdates(context, locationManager, this, this)

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
                " VerticalAccuracyInMeters: ${event.verticalAccuracyMeters}")
        latitudeState.value = event.latitude
        longitudeState.value = event.longitude
        speedState.value = event.speed
        speedAccuracyInMetersState.value = event.speedAccuracyMetersPerSecond
        altitudeState.value = event.altitude
        verticalAccuracyInMetersState.value = event.verticalAccuracyMeters
    }

    // LocationListener
    override fun onLocationChanged(location: Location) {
        // Update state variables with new latitude and longitude
        latitudeState.value = location.latitude
        longitudeState.value = location.longitude
        horizontalAccuracyInMetersState.value = location.accuracy
        verticalAccuracyInMetersState.value = location.verticalAccuracyMeters
        altitudeState.value = location.altitude
        speedState.value = location.speed
        speedAccuracyInMetersState.value = location.speedAccuracyMetersPerSecond

        Log.d("MainActivity: onLocationChanged: ",
            "Latitude: ${latitudeState.value}," +
                 " Longitude: ${longitudeState.value}," +
                 " Speed: ${speedState.value}," +
                 " SpeedAccuracyInMeters: ${speedAccuracyInMetersState.value}," +
                 " Altitude: ${altitudeState.value}," +
                 " VerticalAccuracyInMeters: ${verticalAccuracyInMetersState.value}")

        //rotation overlay
//        val rotationGestureOverlay = RotationGestureOverlay(mapView)
//        rotationGestureOverlay.isEnabled
//        mapView.setMultiTouchControls(true)
//        mapView.overlays.add(rotationGestureOverlay)

        // Add new location to polyline
        polyline.addPoint(GeoPoint(latitudeState.value, longitudeState.value))

        // Update map center to the latest location
        mapView.controller.setCenter(GeoPoint(latitudeState.value, longitudeState.value))

        //zoom to new latitude and longitude
        mapView.controller.setZoom(18.0)

        // Refresh the map view to show the updated polyline
        mapView.invalidate()
    }

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
                LocationManager.GPS_PROVIDER,
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

    // Composable
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        // Coroutine scope to auto-hide the top bar after a delay
        val coroutineScope = rememberCoroutineScope()

        // Create a scaffold state for controlling the bottom sheet
        val scaffoldState = rememberBottomSheetScaffoldState()

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
                    usedNumberOfSatellites = usedInFixCount
                )
            },
            sheetPeekHeight = 20.dp,
            sheetContentColor = Color.Transparent,
            sheetContainerColor = Color.Transparent
        ) {
            Column {
                OpenStreetMapView()
            }
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
        usedNumberOfSatellites: Int
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
                    controller.setZoom(5.0)
                    controller.setCenter(GeoPoint(0.0, 0.0)) // Center map on (0, 0)

                    // Initialize polyline for tracking locations
                    polyline = Polyline().apply {
                        width = 10f
                        color = context.resources.getColor(android.R.color.holo_purple, null)
                    }
                    overlays.add(polyline)
                }

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
                            Toast.makeText(context, "Recording started!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(context, ForegroundService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                            // Remove listener from MainActivity
                            // because LocationListener is implemented in ForegroundService
                            locationManager.removeUpdates(this@MainActivity)
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