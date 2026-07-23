package com.example.tafakkurai.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual class PrayerLocationManager(private val context: Context) {

    private fun granted(permission: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        else true

    actual suspend fun current(): Coordinates? {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // A cached fix is instant; a fresh install / cold emulator has none, so fall
        // back to asking the provider for one update.
        return lastKnown(lm, fine) ?: singleUpdate(lm, fine)
    }

    private fun lastKnown(lm: LocationManager, fine: Boolean): Coordinates? {
        val providers = buildList {
            if (fine) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        val loc = providers
            .asSequence()
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return null

        return Coordinates(loc.latitude, loc.longitude)
    }

    /** Requests one live fix. The caller applies the timeout. */
    private suspend fun singleUpdate(lm: LocationManager, fine: Boolean): Coordinates? {
        fun enabled(provider: String) =
            runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)

        val provider = when {
            enabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            fine && enabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }

        return suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(Coordinates(location.latitude, location.longitude))
                }

                override fun onProviderDisabled(provider: String) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }

                override fun onProviderEnabled(provider: String) = Unit
            }

            try {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }

            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
        }
    }
}
