package com.mwagent.system.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val provider: String,
    val ageMillis: Long,
)

/** Reads the phone's position from the framework LocationManager, so no Google Play services are needed. */
class DeviceLocation(private val context: Context) {

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** True when the system location switch is on and at least one provider is enabled. */
    fun isEnabled(): Boolean = enabledProviders().isNotEmpty()

    /** A fresh fix when one can be taken in time, otherwise the newest cached fix that is not too old. */
    @SuppressLint("MissingPermission")
    suspend fun current(): LocationFix? {
        val manager = manager() ?: return null
        val providers = enabledProviders()
        if (providers.isEmpty()) return null
        for (provider in providers) {
            val live = withTimeoutOrNull(FIX_TIMEOUT_MS) { requestSingleFix(manager, provider) }
            if (live != null) return live.toFix(provider)
        }
        val cached = providers.mapNotNull { provider -> manager.getLastKnownLocation(provider)?.let { it to provider } }
            .maxByOrNull { (location, _) -> location.time }
            ?: return null
        return cached.first.toFix(cached.second)
    }

    /** The first line of the nearest street address, or null when the on-device geocoder has nothing for the spot. */
    @Suppress("DEPRECATION")
    suspend fun placeName(fix: LocationFix): String? = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                Geocoder(context).getFromLocation(fix.latitude, fix.longitude, 1)?.firstOrNull()?.let { line(it) }
            }.getOrNull()
        }
    }

    private fun line(address: Address): String? =
        address.getAddressLine(0)?.takeIf { it.isNotBlank() }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                }
            }
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        }

    private fun enabledProviders(): List<String> {
        val manager = manager() ?: return emptyList()
        return PROVIDERS.filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
    }

    private fun manager(): LocationManager? = context.getSystemService(LocationManager::class.java)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toFix(provider: String) = LocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        provider = provider,
        ageMillis = (System.currentTimeMillis() - time).coerceAtLeast(0L),
    )

    private companion object {
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        const val FIX_TIMEOUT_MS = 15_000L
        const val GEOCODE_TIMEOUT_MS = 5_000L
    }
}
