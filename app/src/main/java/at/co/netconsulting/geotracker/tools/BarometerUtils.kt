package at.co.netconsulting.geotracker.tools

import kotlin.math.abs
import kotlin.math.pow

object BarometerUtils {

    /**
     * Validate if pressure reading is realistic
     */
    fun isRealisticPressure(pressure: Float): Boolean {
        // Typical atmospheric pressure ranges from 870 hPa (strong low pressure)
        // to 1085 hPa (strong high pressure)
        return pressure in 870f..1085f
    }

    /**
     * Convert pressure units
     */
    fun hPaToInHg(hPa: Float): Float = hPa * 0.02953f
    fun hPaToPsi(hPa: Float): Float = hPa * 0.01450f
    fun hPaToTorr(hPa: Float): Float = hPa * 0.75006f

    /**
     * Determine weather trend based on pressure change
     */
    fun getWeatherTrend(currentPressure: Float, previousPressure: Float): String {
        val change = currentPressure - previousPressure
        return when {
            change > 2.0f -> "Rising rapidly (Improving weather)"
            change > 0.5f -> "Rising slowly (Fair weather)"
            change > -0.5f -> "Steady (Current conditions continue)"
            change > -2.0f -> "Falling slowly (Possible rain)"
            else -> "Falling rapidly (Storm approaching)"
        }
    }

    /**
     * Calculate pressure altitude (used in aviation)
     * Standard atmospheric pressure: 1013.25 hPa
     */
    fun calculatePressureAltitude(currentPressure: Float): Float {
        return (1f - (currentPressure / 1013.25f).pow(0.190284f)) * 145366.45f * 0.3048f
    }

    /**
     * Calculate density altitude (pressure altitude corrected for temperature)
     */
    fun calculateDensityAltitude(pressureAltitude: Float, temperatureCelsius: Float): Float {
        val standardTemp = 15f // Standard temperature at sea level in Celsius
        val tempRatio = (temperatureCelsius + 273.15f) / (standardTemp + 273.15f)
        return pressureAltitude + (120f * (tempRatio - 1f))
    }

    /**
     * Estimate GPS altitude accuracy based on barometer comparison
     */
    fun estimateGpsAltitudeAccuracy(gpsAltitude: Float, barometerAltitude: Float): String {
        val difference = abs(gpsAltitude - barometerAltitude)
        return when {
            difference < 5f -> "Excellent GPS altitude accuracy"
            difference < 15f -> "Good GPS altitude accuracy"
            difference < 30f -> "Fair GPS altitude accuracy"
            else -> "Poor GPS altitude accuracy - consider calibration"
        }
    }
}