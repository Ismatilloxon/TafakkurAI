package com.example.tafakkurai.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Decides *when* [SyncEngine] runs. The engine decides what happens; this decides how
 * often — which is the difference between an app that syncs and an app that drains the
 * battery.
 *
 * Five triggers, and nothing else:
 *
 * | Trigger    | Why it exists                                                          |
 * |------------|------------------------------------------------------------------------|
 * | Periodic   | Bounded staleness. Jittered so a million installs do not align.        |
 * | Threshold  | A heavy session should not wait out the timer with 400 events queued.  |
 * | Background | The moment the user stops using the app is the cheapest moment to send. |
 * | Reconnect  | A queue that filled up offline should leave as soon as it can.         |
 * | Manual     | Pull-to-refresh. The user asked; the user waits.                       |
 *
 * There is deliberately no per-action trigger. Syncing on every write is what turns a
 * dhikr counter into a thousand HTTP requests, and it is the single most expensive
 * mistake a client can make.
 */
class SyncScheduler(
    private val engine: SyncEngine,
    private val scope: CoroutineScope,
    private val connectivity: Connectivity = OptimisticConnectivity(),
    private val config: Config = Config(),
    private val log: (String) -> Unit = {},
) {
    /**
     * @param periodSecondsMin/Max the periodic window. A random point in it is chosen for
     *  every tick rather than a fixed interval: fixed intervals make every device that
     *  launched during the same minute hit the backend in the same second, forever.
     * @param thresholdEvents queue depth that forces an early run.
     * @param queueCheckMillis how often the queue depth is read. A local COUNT on an
     *  indexed table, foreground only — cheaper than the wake-up it prevents.
     * @param retryBaseMillis first backoff step after a failed run, doubled per failure.
     */
    data class Config(
        val periodSecondsMin: Long = 200,
        val periodSecondsMax: Long = 300,
        val thresholdEvents: Long = 50,
        val queueCheckMillis: Long = 30_000,
        val retryBaseMillis: Long = 15_000,
        val retryMaxMillis: Long = 15 * 60_000,
    )

    private var periodic: Job? = null
    private var watcher: Job? = null
    private var reconnect: Job? = null
    private var consecutiveFailures = 0

    private val _state = MutableStateFlow<SyncOutcome?>(null)

    /** The last run's outcome, for a status line or a debug screen. */
    val lastOutcome: StateFlow<SyncOutcome?> = _state.asStateFlow()

    /** Starts the timers. Idempotent — calling it twice does not double the cadence. */
    fun start() {
        if (periodic != null) return

        periodic = scope.launch {
            while (true) {
                val window = config.periodSecondsMax - config.periodSecondsMin
                val seconds = config.periodSecondsMin + if (window > 0) Random.nextLong(window) else 0
                delay(seconds * 1000)
                run(SyncReason.Periodic)
            }
        }

        watcher = scope.launch {
            while (true) {
                delay(config.queueCheckMillis)
                if (engine.pendingCount() >= config.thresholdEvents) run(SyncReason.Threshold)
            }
        }

        reconnect = scope.launch {
            connectivity.online.filter { it }.collect { run(SyncReason.Reconnect) }
        }
    }

    fun stop() {
        periodic?.cancel(); periodic = null
        watcher?.cancel(); watcher = null
        reconnect?.cancel(); reconnect = null
    }

    /**
     * The app is going to the background. The best moment there is to sync: the user is
     * not waiting for anything, the radio is likely still warm from whatever they just
     * did, and a queue left unsent might not get another chance for hours.
     *
     * The platform layer is responsible for keeping the process alive long enough — a
     * `beginBackgroundTask` on iOS, a `goAsync`/WorkManager hand-off on Android. Without
     * that this is a best-effort attempt, which is still better than not trying.
     */
    fun onEnterBackground() = run(SyncReason.Background)

    fun onEnterForeground() = run(SyncReason.AppStart)

    /** Pull-to-refresh, or the tap after signing in. */
    fun syncNow(reason: SyncReason = SyncReason.Manual) = run(reason)

    private fun run(reason: SyncReason) {
        scope.launch {
            val outcome = engine.sync(reason)
            _state.value = outcome
            when (outcome) {
                is SyncOutcome.Success -> {
                    consecutiveFailures = 0
                    log("sync ${reason.name}: pushed=${outcome.pushed} pulled=${outcome.pulled} pending=${outcome.stillPending}")
                    // A run that hit its round cap still has work queued. Go again rather
                    // than leaving the backlog until the next timer.
                    if (outcome.stillPending >= config.thresholdEvents) {
                        delay(config.retryBaseMillis)
                        run(SyncReason.Threshold)
                    }
                }
                is SyncOutcome.Failed -> {
                    consecutiveFailures++
                    if (consecutiveFailures > MAX_RETRY_CHAIN) {
                        // Give up on this chain and let the periodic timer or a genuine
                        // reconnect start the next one. Without this cap a device that is
                        // offline for a day would run an unbroken retry chain all day —
                        // backed off, but never stopping, and never letting the process idle.
                        log("sync ${reason.name} failed ${consecutiveFailures}x; waiting for next trigger")
                        return@launch
                    }
                    val backoff = (config.retryBaseMillis shl (consecutiveFailures - 1).coerceAtMost(6))
                        .coerceAtMost(config.retryMaxMillis)
                    log("sync ${reason.name} failed (${outcome.error}); retry in ${backoff}ms")
                    // Exponential backoff is the reconnect trigger's understudy: a device
                    // whose platform cannot report reachability still recovers, just a
                    // little later.
                    delay(backoff)
                    run(SyncReason.Reconnect)
                }
                SyncOutcome.NotSignedIn -> Unit
            }
        }
    }

    private companion object {
        /** How many back-to-back failures a single retry chain is allowed before it stops. */
        const val MAX_RETRY_CHAIN = 6
    }
}
