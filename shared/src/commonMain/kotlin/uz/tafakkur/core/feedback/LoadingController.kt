package uz.tafakkur.core.feedback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global, reference-counted loading signal.
 *
 * Every backend call brackets its work with [begin]/[end]; [active] stays `true` while any
 * request is in flight and only flips back to `false` once the *last* one finishes. That is
 * what lets several simultaneous requests share one loader instead of fighting over it.
 *
 * The counter is the single source of truth — the anti-flicker debounce lives in the Compose
 * host so this stays a plain, testable state holder with no UI or timing concerns.
 */
interface LoadingController {
    /** Number of requests currently in flight (never negative). */
    val inFlight: StateFlow<Int>

    /** `true` while at least one request is running. */
    val active: StateFlow<Boolean>

    fun begin()
    fun end()
}

class DefaultLoadingController(scope: CoroutineScope) : LoadingController {

    private val _inFlight = MutableStateFlow(0)
    override val inFlight: StateFlow<Int> = _inFlight.asStateFlow()

    // Derived once and shared so every observer reads the same emissions.
    override val active: StateFlow<Boolean> =
        _inFlight
            .map { it > 0 }
            .stateIn(scope, SharingStarted.Eagerly, false)

    // StateFlow.update is a lock-free compare-and-set loop, so begin/end are safe to call
    // from any thread or coroutine without extra synchronization.
    override fun begin() {
        _inFlight.update { it + 1 }
    }

    override fun end() {
        _inFlight.update { (it - 1).coerceAtLeast(0) }
    }
}

/** Local, allocation-free equivalent of kotlinx' StateFlow.update (kept private to the module). */
private inline fun MutableStateFlow<Int>.update(transform: (Int) -> Int) {
    while (true) {
        val prev = value
        val next = transform(prev)
        if (compareAndSet(prev, next)) return
    }
}
