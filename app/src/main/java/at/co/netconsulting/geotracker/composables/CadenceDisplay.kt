package at.co.netconsulting.geotracker.composables

internal data class CadenceDisplay(
    val multiplier: Int,
    val unit: String,
    val isRunning: Boolean
) {
    fun value(rawCadence: Int): Int = rawCadence * multiplier
    fun value(rawCadence: Float): Float = rawCadence * multiplier
}

internal fun cadenceDisplayFor(sportType: String): CadenceDisplay {
    val normalized = sportType.trim().lowercase()
    val isRunning = normalized.contains("run") ||
        normalized.contains("marathon") ||
        normalized.contains("backyard ultra") ||
        normalized.contains("lactate threshold")
    return if (isRunning) {
        CadenceDisplay(multiplier = 2, unit = "spm", isRunning = true)
    } else {
        CadenceDisplay(multiplier = 1, unit = "cycles/min", isRunning = false)
    }
}
