package at.co.netconsulting.geotracker.data

import java.util.Locale

data class SportMetadata(
    val family: String,
    val discipline: String? = null,
    val eventFormat: String? = null
) {
    /**
     * Compatibility value for older app and server versions that only understand artOfSport.
     */
    fun legacySportType(): String = when {
        SportCatalog.legacyModeFormats.any { it.equals(eventFormat, ignoreCase = true) } -> eventFormat.orEmpty()
        !discipline.isNullOrBlank() -> discipline
        else -> family
    }
}

data class SportFamilyDefinition(
    val name: String,
    val disciplines: List<String> = emptyList()
)

object SportCatalog {
    const val RUNNING = "Running"

    val runningEventFormats = listOf(
        "1 km",
        "5 km",
        "10 km",
        "Half marathon",
        "Marathon",
        "50 km",
        "100 km",
        "6 h",
        "12 h",
        "24 h",
        "48 h",
        "72 h",
        "6 days",
        "Backyard Ultra",
        "Wings for Life Run",
        "Ultramarathon (other)"
    )

    val legacyModeFormats = setOf("Backyard Ultra", "Wings for Life Run")

    val families = listOf(
        SportFamilyDefinition(RUNNING, listOf("Road Running", "Trail Running", "Orienteering")),
        SportFamilyDefinition("Cycling", listOf("Gravel Bike", "E-Bike", "Racing Bicycle", "Mountain Bike")),
        SportFamilyDefinition("Water Sports", listOf("Swimming - Open Water", "Pool Swimming", "Kayaking", "Canoeing", "Stand Up Paddleboarding")),
        SportFamilyDefinition("Winter Sport", listOf("Ski", "Snowboard", "Cross Country Skiing", "Ski Touring", "Biathlon", "Sledding", "Snowshoeing", "Ice Hockey")),
        SportFamilyDefinition("Skating", listOf("Inline Skating")),
        SportFamilyDefinition("Walking", listOf("Nordic Walking", "Urban Walking")),
        SportFamilyDefinition("Hiking", listOf("Mountain Hiking", "Forest Hiking")),
        SportFamilyDefinition("Motorsport", listOf("Car", "Motorcycle")),
        SportFamilyDefinition("Multisport Race", listOf("Duathlon", "Triathlon", "Ultratriathlon")),
        SportFamilyDefinition("Fitness Test", listOf("Lactate Threshold (30min TT)"))
    )

    private val disciplineToFamily = families
        .flatMap { family -> family.disciplines.map { normalizedKey(it) to family.name } }
        .toMap()

    fun resolve(
        legacySport: String,
        family: String?,
        discipline: String?,
        eventFormat: String?
    ): SportMetadata {
        if (!family.isNullOrBlank()) {
            return SportMetadata(
                family = family.trim(),
                discipline = discipline?.trim()?.takeIf(String::isNotEmpty),
                eventFormat = eventFormat?.trim()?.takeIf(String::isNotEmpty)
            )
        }
        return fromLegacy(legacySport)
    }

    fun fromLegacy(legacySport: String): SportMetadata {
        val original = legacySport.trim().ifEmpty { RUNNING }
        val normalized = normalizedKey(original)

        return when (normalized) {
            "run", "running" -> SportMetadata(RUNNING)
            "road", "road running" -> SportMetadata(RUNNING, "Road Running")
            "trail", "trail running" -> SportMetadata(RUNNING, "Trail Running")
            "orienteering" -> SportMetadata(RUNNING, "Orienteering")
            "half marathon", "halfmarathon" -> SportMetadata(RUNNING, "Road Running", "Half marathon")
            "marathon" -> SportMetadata(RUNNING, "Road Running", "Marathon")
            "ultra", "ultramarathon", "ultra marathon", "ultramarathon (other)" ->
                SportMetadata(RUNNING, eventFormat = "Ultramarathon (other)")
            "backyard ultra" -> SportMetadata(RUNNING, eventFormat = "Backyard Ultra")
            "wings for life", "wings for life run" -> SportMetadata(RUNNING, eventFormat = "Wings for Life Run")
            "swim", "swimming" -> SportMetadata("Water Sports")
            "open water", "open water swimming", "swimming open water" ->
                SportMetadata("Water Sports", "Swimming - Open Water")
            "pool", "pool swimming" -> SportMetadata("Water Sports", "Pool Swimming")
            else -> {
                val canonicalDiscipline = families
                    .flatMap { it.disciplines }
                    .firstOrNull { it.equals(original, ignoreCase = true) }
                val inferredFamily = disciplineToFamily[normalized]
                val canonicalFamily = families.firstOrNull { it.name.equals(original, ignoreCase = true) }?.name

                when {
                    canonicalDiscipline != null && inferredFamily != null ->
                        SportMetadata(inferredFamily, canonicalDiscipline)
                    canonicalFamily != null -> SportMetadata(canonicalFamily)
                    else -> SportMetadata(original)
                }
            }
        }
    }

    private fun normalizedKey(value: String): String =
        value.lowercase(Locale.ROOT).replace("-", " ").replace(Regex("\\s+"), " ").trim()
}
