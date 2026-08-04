package com.example.tafakkurai.prayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Immutable snapshot the [com.example.tafakkurai.prayer.SalahProgressHero] renders. */
data class PrayerProgressUiState(
    val fraction: Float = 0f,
    val currentKey: String = "",
    val currentName: String = "",
    val nextKey: String = "",
    val nextName: String = "",
    val nextAt: String = "",
    val countdown: String = "",
)

/**
 * MVVM glue. Owns a 1 Hz tick that recomputes [state] from the wall clock, so the
 * ring always reflects the real current time. The loop lives in [viewModelScope],
 * which the framework cancels in [onCleared] — no dangling timer, no leak.
 *
 * @param service supplies the day-spanning timeline (API-agnostic).
 * @param now injectable clock (seconds since midnight) — tests pass a fixed lambda.
 */
class PrayerProgressViewModel(
    private val service: PrayerTimelineService = PrayerTimelineService(),
    private val now: () -> Int = ::currentSecondsOfDay,
) : ViewModel() {

    var state by mutableStateOf(compute())
        private set

    init {
        viewModelScope.launch {
            while (isActive) {
                state = compute()
                delay(TICK_MS)
            }
        }
    }

    /** Force an immediate recompute — call on return-from-background (ON_RESUME). */
    fun refresh() {
        state = compute()
    }

    private fun compute(): PrayerProgressUiState {
        val nowMinute = now() / 60f
        val p = PrayerProgressCalculator.progressAt(service.timeline(), nowMinute)
        return PrayerProgressUiState(
            fraction = p.fraction,
            currentKey = p.current.key,     // the prayer whose interval we're in — the one you'd complete
            currentName = p.current.name,
            nextKey = p.next.key,
            nextName = p.next.name,
            nextAt = formatClock(p.next.absoluteMinute),
            countdown = formatCountdown(p.minutesToNext),
        )
    }

    private fun formatClock(absoluteMinute: Float): String {
        val m = ((absoluteMinute.roundToInt() % 1440) + 1440) % 1440
        val hh = (m / 60).toString().padStart(2, '0')
        val mm = (m % 60).toString().padStart(2, '0')
        return "$hh:$mm"
    }

    private fun formatCountdown(minutes: Float): String {
        val total = minutes.roundToInt()
        val h = total / 60
        val m = total % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private companion object {
        const val TICK_MS = 1000L
    }
}
