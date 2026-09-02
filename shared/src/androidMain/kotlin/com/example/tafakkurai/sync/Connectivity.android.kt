package com.example.tafakkurai.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reachability from ConnectivityManager, used to fire the reconnect trigger the moment
 * a queue that filled up on the underground can leave.
 *
 * Everything is wrapped defensively and falls back to "assume online". Reachability is
 * an optimisation here, never a gate: if the callback cannot be registered — the
 * `ACCESS_NETWORK_STATE` permission is missing, or the OEM's implementation throws —
 * the engine still syncs on its timer and still backs off on failure. A sync platform
 * that stops working because a permission is missing would be a worse bug than the one
 * this class exists to avoid.
 *
 * Requires in AndroidManifest.xml:
 * ```xml
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
 * ```
 */
class AndroidConnectivity(context: Context) : Connectivity {

    private val manager = runCatching {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    override fun isOnline(): Boolean {
        val cm = manager ?: return true
        return runCatching {
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(true)
    }

    override val online: Flow<Boolean> = callbackFlow {
        val cm = manager
        if (cm == null) {
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                // A network that exists but has not validated yet (captive portal, or a
                // Wi-Fi association still completing) would fail every request. Waiting
                // for VALIDATED turns one wasted round trip into none.
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
        }

        val registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
        if (!registered) {
            close()
            return@callbackFlow
        }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
