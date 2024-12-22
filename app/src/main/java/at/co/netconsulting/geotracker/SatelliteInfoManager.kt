package at.co.netconsulting.geotracker

import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import at.co.netconsulting.geotracker.data.SatelliteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SatelliteInfoManager(private val context: Context) {
    private var _currentSatelliteInfo = MutableStateFlow(SatelliteInfo())
    val currentSatelliteInfo: StateFlow<SatelliteInfo> = _currentSatelliteInfo.asStateFlow()

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var visibleCount = 0
            val totalCount = status.satelliteCount

            // Count satellites being used in fix
            for (i in 0 until totalCount) {
                if (status.usedInFix(i)) {
                    visibleCount++
                }
            }

            _currentSatelliteInfo.value = SatelliteInfo(
                visibleSatellites = visibleCount,
                totalSatellites = totalCount
            )
        }
    }

    fun startListening() {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.registerGnssStatusCallback(gnssCallback, null)
        }
    }

    fun stopListening() {
        locationManager.unregisterGnssStatusCallback(gnssCallback)
    }
}