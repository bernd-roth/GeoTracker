package at.co.netconsulting.geotracker.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import at.co.netconsulting.geotracker.data.BarometerData
import org.greenrobot.eventbus.EventBus
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.ln

/**
 * Enhanced Barometer Sensor Service with advanced features:
 * - Data smoothing and filtering
 * - GPS-based calibration
 * - Pressure validation
 * - Multiple altitude calculation methods
 * - Automatic error recovery
 */
class BarometerSensorService private constructor(private val context: Context) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private var isListening = false
    private var currentPressure = 0f
    private var currentAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private var seaLevelPressure = STANDARD_SEA_LEVEL_PRESSURE

    // Smoothing filter for pressure readings
    private val pressureBuffer = mutableListOf<Float>()
    private val bufferSize = 5
    private var smoothedPressure = 0f

    // Advanced filtering
    private val longTermBuffer = mutableListOf<Float>()
    private val longTermBufferSize = 20
    private var lastValidPressure = 0f
    private var consecutiveInvalidReadings = 0

    // Calibration
    private var isCalibrated = false
    private var pressureOffset = 0f
    private var calibrationTimestamp = 0L
    private var calibrationAltitude = 0f

    // Statistics
    private var totalReadings = 0L
    private var validReadings = 0L
    private var lastReadingTimestamp = 0L
    private var minPressure = Float.MAX_VALUE
    private var maxPressure = Float.MIN_VALUE

    // Error handling
    private var sensorErrorCount = 0
    private var lastErrorTimestamp = 0L
    private val maxConsecutiveErrors = 5

    companion object {
        private const val TAG = "EnhancedBarometerService"
        private var INSTANCE: BarometerSensorService? = null

        // Constants
        private const val STANDARD_SEA_LEVEL_PRESSURE = 1013.25f
        private const val MIN_REALISTIC_PRESSURE = 870f  // Strong low pressure system
        private const val MAX_REALISTIC_PRESSURE = 1085f // Strong high pressure system
        private const val MAX_PRESSURE_CHANGE_PER_SECOND = 2.0f // hPa/s (unrealistic if exceeded)
        private const val CALIBRATION_VALIDITY_HOURS = 24 // Calibration valid for 24 hours
        private const val MIN_CALIBRATION_ALTITUDE = -500f // Dead Sea level
        private const val MAX_CALIBRATION_ALTITUDE = 9000f // Reasonable max for hiking/climbing

        fun getInstance(context: Context): BarometerSensorService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BarometerSensorService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        initializeSensor()
        loadCalibrationData()
        loadStatistics()
    }

    private fun initializeSensor() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        if (pressureSensor == null) {
            Log.w(TAG, "Barometer sensor not available on this device")
        } else {
            Log.d(TAG, "Barometer sensor initialized: ${pressureSensor?.name}")
            Log.d(TAG, "Sensor details:")
            Log.d(TAG, "  - Range: ${pressureSensor?.maximumRange} hPa")
            Log.d(TAG, "  - Resolution: ${pressureSensor?.resolution} hPa")
            Log.d(TAG, "  - Power: ${pressureSensor?.power} mA")
            Log.d(TAG, "  - Vendor: ${pressureSensor?.vendor}")
        }
    }

    private fun loadCalibrationData() {
        val prefs = context.getSharedPreferences("BarometerCalibration", Context.MODE_PRIVATE)
        pressureOffset = prefs.getFloat("pressure_offset", 0f)
        isCalibrated = prefs.getBoolean("is_calibrated", false)
        calibrationTimestamp = prefs.getLong("calibration_timestamp", 0L)
        calibrationAltitude = prefs.getFloat("calibration_altitude", 0f)

        // Check if calibration is still valid (within 24 hours)
        val currentTime = System.currentTimeMillis()
        val calibrationAge = (currentTime - calibrationTimestamp) / (1000 * 60 * 60) // hours

        if (isCalibrated && calibrationAge > CALIBRATION_VALIDITY_HOURS) {
            Log.w(TAG, "Calibration expired (${calibrationAge} hours old), marking as uncalibrated")
            isCalibrated = false
        }

        if (isCalibrated) {
            Log.d(TAG, "Loaded valid calibration: offset=$pressureOffset hPa, altitude=${calibrationAltitude}m, age=${calibrationAge}h")
        } else {
            Log.d(TAG, "No valid calibration found")
        }
    }

    private fun saveCalibrationData() {
        val prefs = context.getSharedPreferences("BarometerCalibration", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("pressure_offset", pressureOffset)
            .putBoolean("is_calibrated", isCalibrated)
            .putLong("calibration_timestamp", calibrationTimestamp)
            .putFloat("calibration_altitude", calibrationAltitude)
            .apply()
        Log.d(TAG, "Saved calibration: offset=$pressureOffset hPa, altitude=${calibrationAltitude}m")
    }

    private fun loadStatistics() {
        val prefs = context.getSharedPreferences("BarometerStats", Context.MODE_PRIVATE)
        totalReadings = prefs.getLong("total_readings", 0L)
        validReadings = prefs.getLong("valid_readings", 0L)
        minPressure = prefs.getFloat("min_pressure", Float.MAX_VALUE)
        maxPressure = prefs.getFloat("max_pressure", Float.MIN_VALUE)

        if (totalReadings > 0) {
            Log.d(TAG, "Loaded statistics: $validReadings/$totalReadings valid readings, range: ${minPressure}-${maxPressure} hPa")
        }
    }

    private fun saveStatistics() {
        val prefs = context.getSharedPreferences("BarometerStats", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("total_readings", totalReadings)
            .putLong("valid_readings", validReadings)
            .putFloat("min_pressure", if (minPressure != Float.MAX_VALUE) minPressure else 0f)
            .putFloat("max_pressure", if (maxPressure != Float.MIN_VALUE) maxPressure else 0f)
            .apply()
    }

    fun startListening(): Boolean {
        if (pressureSensor == null) {
            Log.e(TAG, "Cannot start listening - barometer sensor not available")
            return false
        }

        if (isListening) {
            Log.d(TAG, "Already listening to barometer sensor")
            return true
        }

        val success = sensorManager?.registerListener(
            this,
            pressureSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        ) ?: false

        if (success) {
            isListening = true
            sensorErrorCount = 0
            Log.d(TAG, "Started listening to barometer sensor")

            // Reset some statistics
            lastReadingTimestamp = System.currentTimeMillis()
        } else {
            Log.e(TAG, "Failed to register barometer sensor listener")
        }

        return success
    }

    fun stopListening() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
            saveStatistics()
            Log.d(TAG, "Stopped listening to barometer sensor")
        }
    }

    fun isAvailable(): Boolean = pressureSensor != null

    fun isListening(): Boolean = isListening

    fun getCurrentPressure(): Float = currentPressure

    fun getCurrentAccuracy(): Int = currentAccuracy

    fun getSmoothedPressure(): Float = smoothedPressure

    fun isCalibrated(): Boolean = isCalibrated

    fun getCalibrationInfo(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val calibrationAge = if (calibrationTimestamp > 0) {
            (currentTime - calibrationTimestamp) / (1000 * 60 * 60) // hours
        } else 0L

        return mapOf(
            "is_calibrated" to isCalibrated,
            "pressure_offset" to pressureOffset,
            "calibration_altitude" to calibrationAltitude,
            "calibration_age_hours" to calibrationAge,
            "is_calibration_valid" to (isCalibrated && calibrationAge < CALIBRATION_VALIDITY_HOURS)
        )
    }

    fun getStatistics(): Map<String, Any> {
        val accuracy = if (totalReadings > 0) {
            (validReadings * 100.0 / totalReadings)
        } else 0.0

        return mapOf(
            "total_readings" to totalReadings,
            "valid_readings" to validReadings,
            "accuracy_percentage" to accuracy,
            "min_pressure" to if (minPressure != Float.MAX_VALUE) minPressure else 0f,
            "max_pressure" to if (maxPressure != Float.MIN_VALUE) maxPressure else 0f,
            "pressure_range" to if (minPressure != Float.MAX_VALUE && maxPressure != Float.MIN_VALUE)
                (maxPressure - minPressure) else 0f,
            "sensor_error_count" to sensorErrorCount,
            "current_accuracy" to getAccuracyString(currentAccuracy)
        )
    }

    private fun getAccuracyString(accuracy: Int): String {
        return when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
            else -> "Unknown"
        }
    }

    fun setSeaLevelPressure(pressure: Float): Boolean {
        return if (isRealisticPressure(pressure)) {
            seaLevelPressure = pressure
            Log.d(TAG, "Sea level pressure updated to $pressure hPa")
            true
        } else {
            Log.w(TAG, "Invalid sea level pressure: $pressure hPa")
            false
        }
    }

    fun getSeaLevelPressure(): Float = seaLevelPressure

    /**
     * Calibrate the barometer using GPS altitude
     * @param gpsAltitude The accurate GPS altitude in meters
     * @return true if calibration was successful
     */
    fun calibrateWithGpsAltitude(gpsAltitude: Float): Boolean {
        if (currentPressure <= 0) {
            Log.w(TAG, "Cannot calibrate - no valid pressure reading available")
            return false
        }

        if (gpsAltitude < MIN_CALIBRATION_ALTITUDE || gpsAltitude > MAX_CALIBRATION_ALTITUDE) {
            Log.w(TAG, "GPS altitude out of realistic range: ${gpsAltitude}m")
            return false
        }

        try {
            // Calculate what the pressure should be at the GPS altitude
            val expectedPressure = calculatePressureAtAltitude(gpsAltitude, seaLevelPressure)

            // Calculate the offset needed
            pressureOffset = expectedPressure - currentPressure
            isCalibrated = true
            calibrationTimestamp = System.currentTimeMillis()
            calibrationAltitude = gpsAltitude

            saveCalibrationData()

            Log.d(TAG, "Calibrated barometer with GPS altitude ${gpsAltitude}m")
            Log.d(TAG, "  - Raw pressure: ${currentPressure} hPa")
            Log.d(TAG, "  - Expected pressure: ${expectedPressure} hPa")
            Log.d(TAG, "  - Offset: ${pressureOffset} hPa")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error during GPS calibration", e)
            return false
        }
    }

    /**
     * Manual calibration with known pressure at current location
     */
    fun calibrateWithKnownPressure(knownPressure: Float): Boolean {
        if (currentPressure <= 0) {
            Log.w(TAG, "Cannot calibrate - no valid pressure reading available")
            return false
        }

        if (!isRealisticPressure(knownPressure)) {
            Log.w(TAG, "Known pressure is not realistic: ${knownPressure} hPa")
            return false
        }

        pressureOffset = knownPressure - currentPressure
        isCalibrated = true
        calibrationTimestamp = System.currentTimeMillis()
        calibrationAltitude = calculateAltitudeFromPressure(knownPressure, seaLevelPressure)

        saveCalibrationData()

        Log.d(TAG, "Manually calibrated barometer with known pressure ${knownPressure} hPa. Offset: ${pressureOffset} hPa")
        return true
    }

    /**
     * Reset calibration
     */
    fun resetCalibration() {
        isCalibrated = false
        pressureOffset = 0f
        calibrationTimestamp = 0L
        calibrationAltitude = 0f

        saveCalibrationData()
        Log.d(TAG, "Barometer calibration reset")
    }

    private fun isRealisticPressure(pressure: Float): Boolean {
        return pressure in MIN_REALISTIC_PRESSURE..MAX_REALISTIC_PRESSURE
    }

    private fun isValidPressureChange(newPressure: Float): Boolean {
        if (lastValidPressure == 0f) return true

        val currentTime = System.currentTimeMillis()
        val timeDiffSeconds = (currentTime - lastReadingTimestamp) / 1000.0f

        if (timeDiffSeconds > 0) {
            val pressureChange = abs(newPressure - lastValidPressure)
            val changeRate = pressureChange / timeDiffSeconds

            return changeRate <= MAX_PRESSURE_CHANGE_PER_SECOND
        }

        return true
    }

    private fun applySmoothingFilter(newPressure: Float): Float {
        pressureBuffer.add(newPressure)

        // Keep only the last bufferSize readings
        if (pressureBuffer.size > bufferSize) {
            pressureBuffer.removeAt(0)
        }

        // Calculate weighted moving average (more weight to recent readings)
        var weightedSum = 0f
        var totalWeight = 0f

        for (i in pressureBuffer.indices) {
            val weight = i + 1f // Linear weighting
            weightedSum += pressureBuffer[i] * weight
            totalWeight += weight
        }

        return weightedSum / totalWeight
    }

    private fun applyAdvancedFilter(newPressure: Float): Float {
        longTermBuffer.add(newPressure)

        if (longTermBuffer.size > longTermBufferSize) {
            longTermBuffer.removeAt(0)
        }

        // Calculate median for outlier detection
        val sortedBuffer = longTermBuffer.sorted()
        val median = if (sortedBuffer.size % 2 == 0) {
            (sortedBuffer[sortedBuffer.size / 2 - 1] + sortedBuffer[sortedBuffer.size / 2]) / 2f
        } else {
            sortedBuffer[sortedBuffer.size / 2]
        }

        // If current reading is too far from median, use filtered value
        val deviation = abs(newPressure - median)
        val threshold = 5.0f // hPa threshold for outlier detection

        return if (deviation > threshold && longTermBuffer.size >= 5) {
            Log.d(TAG, "Outlier detected: ${newPressure} hPa (median: ${median} hPa), using filtered value")
            median
        } else {
            newPressure
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            if (sensorEvent.sensor.type == Sensor.TYPE_PRESSURE) {
                try {
                    totalReadings++
                    val currentTime = System.currentTimeMillis()

                    var rawPressure = sensorEvent.values[0]

                    // Basic validation
                    if (!isRealisticPressure(rawPressure)) {
                        Log.w(TAG, "Unrealistic pressure reading: ${rawPressure} hPa")
                        consecutiveInvalidReadings++
                        handleInvalidReading()
                        return
                    }

                    // Rate of change validation
                    if (!isValidPressureChange(rawPressure)) {
                        Log.w(TAG, "Pressure change too rapid: ${rawPressure} hPa")
                        consecutiveInvalidReadings++
                        handleInvalidReading()
                        return
                    }

                    // Apply calibration offset if available
                    if (isCalibrated) {
                        rawPressure += pressureOffset
                    }

                    // Apply advanced filtering for outlier detection
                    val filteredPressure = applyAdvancedFilter(rawPressure)

                    // Apply smoothing filter
                    smoothedPressure = applySmoothingFilter(filteredPressure)
                    currentPressure = smoothedPressure

                    // Update statistics
                    validReadings++
                    consecutiveInvalidReadings = 0
                    sensorErrorCount = 0
                    lastValidPressure = currentPressure
                    lastReadingTimestamp = currentTime

                    if (currentPressure < minPressure) minPressure = currentPressure
                    if (currentPressure > maxPressure) maxPressure = currentPressure

                    // Calculate altitude from pressure
                    val altitudeFromPressure = calculateAltitudeFromPressure(currentPressure, seaLevelPressure)

                    // Create barometer data
                    val barometerData = BarometerData(
                        pressure = currentPressure,
                        accuracy = currentAccuracy,
                        altitudeFromPressure = altitudeFromPressure,
                        seaLevelPressure = seaLevelPressure,
                        timestamp = currentTime,
                        isAvailable = true
                    )

                    // Post to EventBus
                    EventBus.getDefault().post(barometerData)

                    Log.d(TAG, "Barometer reading: ${String.format("%.2f", currentPressure)} hPa, " +
                            "altitude: ${String.format("%.1f", altitudeFromPressure)}m, " +
                            "accuracy: ${getAccuracyString(currentAccuracy)}")

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing sensor data", e)
                    handleSensorError()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_PRESSURE) {
            currentAccuracy = accuracy
            val accuracyStr = getAccuracyString(accuracy)
            Log.d(TAG, "Barometer sensor accuracy changed: $accuracyStr ($accuracy)")

            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                Log.w(TAG, "Barometer sensor accuracy is unreliable")
            }
        }
    }

    private fun handleInvalidReading() {
        if (consecutiveInvalidReadings >= maxConsecutiveErrors) {
            Log.e(TAG, "Too many consecutive invalid readings, stopping sensor")
            stopListening()

            // Try to restart after a delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!isListening) {
                    Log.d(TAG, "Attempting to restart barometer sensor")
                    startListening()
                }
            }, 5000) // 5 second delay
        }
    }

    private fun handleSensorError() {
        sensorErrorCount++
        lastErrorTimestamp = System.currentTimeMillis()

        if (sensorErrorCount >= maxConsecutiveErrors) {
            Log.e(TAG, "Too many sensor errors, stopping sensor")
            stopListening()
        }
    }

    /**
     * Calculate pressure at a given altitude using barometric formula
     */
    private fun calculatePressureAtAltitude(altitude: Float, seaLevelPressure: Float): Float {
        return seaLevelPressure * (1f - 0.0065f * altitude / 288.15f).pow(5.255f)
    }

    /**
     * Calculate altitude from atmospheric pressure using the barometric formula
     * Formula: h = 44330 * (1 - (P/P0)^(1/5.255))
     */
    private fun calculateAltitudeFromPressure(currentPressure: Float, seaLevelPressure: Float): Float {
        return if (currentPressure > 0 && seaLevelPressure > 0) {
            44330f * (1f - (currentPressure / seaLevelPressure).pow(1f / 5.255f))
        } else {
            0f
        }
    }

    /**
     * Alternative altitude calculation using logarithmic formula
     * More accurate for small altitude changes
     */
    fun calculateAltitudeLogarithmic(currentPressure: Float, seaLevelPressure: Float): Float {
        return if (currentPressure > 0 && seaLevelPressure > 0) {
            18400f * ln(seaLevelPressure / currentPressure)
        } else {
            0f
        }
    }

    /**
     * Calculate pressure altitude (aviation standard)
     */
    fun calculatePressureAltitude(currentPressure: Float): Float {
        return 44330f * (1f - (currentPressure / STANDARD_SEA_LEVEL_PRESSURE).pow(0.190284f))
    }

    /**
     * Calculate density altitude (pressure altitude corrected for temperature)
     */
    fun calculateDensityAltitude(pressureAltitude: Float, temperatureCelsius: Float): Float {
        val standardTemp = 15f // Standard temperature at sea level in Celsius
        val tempRatio = (temperatureCelsius + 273.15f) / (standardTemp + 273.15f)
        return pressureAltitude + (120f * (tempRatio - 1f))
    }

    fun cleanup() {
        stopListening()
        saveStatistics()
        saveCalibrationData()
        INSTANCE = null
        Log.d(TAG, "EnhancedBarometerSensorService cleaned up")
    }
}