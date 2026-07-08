package com.example.tafakkurai.prayer

/**
 * One prayer pinned on an absolute minute axis. [absoluteMinute] is minutes from
 * *today's* midnight, so it may be negative (yesterday) or >1440 (tomorrow). This
 * lets the calculator treat the day boundary and the after-Isha rollover as plain
 * arithmetic instead of special cases.
 */
data class PrayerInstant(val key: String, val name: String, val absoluteMinute: Float)

/**
 * Result of placing "now" between two adjacent prayers.
 *
 * [fraction] is the clock fill 0..1 where:
 *   progress = (now - current) / (next - current)
 * 0 right after [current] begins, 1 the moment [next] arrives.
 */
data class PrayerProgress(
    val current: PrayerInstant,
    val next: PrayerInstant,
    val fraction: Float,
    val minutesToNext: Float,
)

/**
 * Pure, framework-free progress math. No clock, no I/O — caller supplies the
 * timeline and the current minute, which makes every branch unit-testable.
 */
object PrayerProgressCalculator {

    /**
     * @param timeline at least two [PrayerInstant]s; must straddle [nowMinute]
     *        (the [PrayerTimelineService] guarantees yesterday→tomorrow coverage).
     * @param nowMinute minutes since today's midnight (may be fractional for smooth fill).
     */
    fun progressAt(timeline: List<PrayerInstant>, nowMinute: Float): PrayerProgress {
        require(timeline.size >= 2) { "timeline needs at least two instants" }
        val sorted = timeline.sortedBy { it.absoluteMinute }

        // Index of the segment [i, i+1) that contains now; clamp to the ends so a
        // now outside the covered range still yields a sane (saturated) result.
        var i = 0
        for (k in 0 until sorted.size - 1) {
            if (nowMinute >= sorted[k].absoluteMinute) i = k else break
        }

        val current = sorted[i]
        val next = sorted[i + 1]
        val span = (next.absoluteMinute - current.absoluteMinute).coerceAtLeast(EPS)
        val fraction = ((nowMinute - current.absoluteMinute) / span).coerceIn(0f, 1f)
        val minutesToNext = (next.absoluteMinute - nowMinute).coerceAtLeast(0f)
        return PrayerProgress(current, next, fraction, minutesToNext)
    }

    private const val EPS = 0.0001f
}
