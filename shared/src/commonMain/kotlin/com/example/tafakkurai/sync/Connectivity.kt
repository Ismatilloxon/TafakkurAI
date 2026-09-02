package com.example.tafakkurai.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Network reachability, as the scheduler needs it: a current answer and a stream of
 * transitions.
 *
 * An interface rather than an `expect class` so a platform that has not implemented it
 * yet still gets a working app — [OptimisticConnectivity] simply tries, and a failed
 * attempt costs one request and is retried with backoff. Sync must never depend on
 * reachability being *correct*; reachability only makes it *cheaper*.
 */
interface Connectivity {
    fun isOnline(): Boolean

    /** Emits true when the device regains connectivity. The reconnect trigger. */
    val online: Flow<Boolean>
}

/**
 * The fallback: assume there is a network and find out by trying.
 *
 * Deliberately not a stub to be ashamed of. Reachability APIs lie in both directions —
 * a captive portal reports connected, a VPN handshake reports disconnected — so a sync
 * engine that refuses to try when told it is offline is strictly worse than one that
 * tries and backs off.
 */
class OptimisticConnectivity : Connectivity {
    override fun isOnline(): Boolean = true
    override val online: Flow<Boolean> = emptyFlow()
}
