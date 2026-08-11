package at.co.netconsulting.geotracker.tools

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class LocationInfo(
    val city: String?,
    val country: String?,
    val address: String? // Full street address
)

object GeocodingHelper {
    private const val TAG = "GeocodingHelper"
    internal const val GEOCODING_TIMEOUT_MS = 10_000L

    private val emptyLocationInfo = LocationInfo(null, null, null)

    suspend fun getLocationInfo(context: Context, latitude: Double, longitude: Double): LocationInfo {
        val locationInfo = runWithGeocodingTimeout {
            try {
                if (!Geocoder.isPresent()) {
                    Log.w(TAG, "Geocoder is not present on this device")
                    return@runWithGeocodingTimeout emptyLocationInfo
                }

                val geocoder = Geocoder(context, Locale.getDefault())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use the async API for Android 13+
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            // The platform callback can arrive after our timeout.
                            // A cancelled continuation must not be resumed.
                            if (continuation.isActive) {
                                if (addresses.isNotEmpty()) {
                                    val locationInfo = extractLocationInfo(addresses[0])
                                    continuation.resume(locationInfo)
                                } else {
                                    Log.w(TAG, "No address found for coordinates: $latitude, $longitude")
                                    continuation.resume(emptyLocationInfo)
                                }
                            }
                        }
                    }
                } else {
                    // Use the synchronous API for older Android versions
                    @Suppress("DEPRECATION")
                    val addresses = runInterruptible(Dispatchers.IO) {
                        geocoder.getFromLocation(latitude, longitude, 1)
                    }
                    if (!addresses.isNullOrEmpty()) {
                        extractLocationInfo(addresses[0])
                    } else {
                        Log.w(TAG, "No address found for coordinates: $latitude, $longitude")
                        emptyLocationInfo
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during reverse geocoding", e)
                emptyLocationInfo
            }
        }

        if (locationInfo == null) {
            Log.w(
                TAG,
                "Reverse geocoding timed out after ${GEOCODING_TIMEOUT_MS}ms " +
                    "for coordinates: $latitude, $longitude"
            )
        }
        return locationInfo ?: emptyLocationInfo
    }

    internal suspend fun <T> runWithGeocodingTimeout(
        timeoutMs: Long = GEOCODING_TIMEOUT_MS,
        block: suspend () -> T
    ): T? = withTimeoutOrNull(timeoutMs) { block() }

    private fun extractLocationInfo(address: Address): LocationInfo {
        val city = address.locality ?: address.subAdminArea ?: address.adminArea
        val country = address.countryName

        // Build full street address
        val fullAddress = buildFullAddress(address)

        Log.d(TAG, "Geocoding result: city=$city, country=$country, address=$fullAddress")
        return LocationInfo(city, country, fullAddress)
    }

    private fun buildFullAddress(address: Address): String? {
        // Try to get the formatted address line first
        val addressLine = address.getAddressLine(0)
        if (!addressLine.isNullOrBlank()) {
            return addressLine
        }

        // Otherwise, build it manually from components
        val parts = mutableListOf<String>()

        // Street address (number + street)
        val streetNumber = address.subThoroughfare
        val streetName = address.thoroughfare
        if (!streetName.isNullOrBlank()) {
            if (!streetNumber.isNullOrBlank()) {
                parts.add("$streetName $streetNumber")
            } else {
                parts.add(streetName)
            }
        }

        // Postal code and city
        val postalCode = address.postalCode
        val city = address.locality ?: address.subAdminArea
        if (!postalCode.isNullOrBlank() && !city.isNullOrBlank()) {
            parts.add("$postalCode $city")
        } else if (!city.isNullOrBlank()) {
            parts.add(city)
        }

        // Country
        val country = address.countryName
        if (!country.isNullOrBlank()) {
            parts.add(country)
        }

        return if (parts.isNotEmpty()) parts.joinToString(", ") else null
    }
}
