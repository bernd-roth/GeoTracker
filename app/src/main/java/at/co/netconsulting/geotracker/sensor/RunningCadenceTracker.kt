package at.co.netconsulting.geotracker.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/** Records running cadence from Android's low-power step detector. */
class RunningCadenceTracker(context: Context) : SensorEventListener {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val calculator = RunningCadenceCalculator()

    @Volatile
    var isListening: Boolean = false
        private set

    fun isAvailable(): Boolean = stepDetector != null

    fun start(): Boolean {
        if (isListening) return true
        if (!hasPermission() || stepDetector == null) return false

        calculator.reset()
        isListening = sensorManager.registerListener(
            this,
            stepDetector,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        Log.d(TAG, "Step detector listening=$isListening")
        return isListening
    }

    fun pause() {
        if (isListening) sensorManager.unregisterListener(this)
        isListening = false
        calculator.reset()
    }

    fun stop() = pause()

    /** Null means unavailable; zero means available but stationary/warming up. */
    fun currentCadence(): Int? {
        if (!isListening) return null
        return calculator.cadenceAt(SystemClock.elapsedRealtimeNanos())
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            calculator.addStep(event.timestamp)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "RunningCadenceTracker"
    }
}
