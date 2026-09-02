package com.example.tafakkurai.sleep

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Smart bedtime — working backwards from the morning.
 *
 * ### Why backwards
 *
 * Asking "when should I go to bed?" in isolation produces advice nobody follows. The fixed
 * point in a real day is the morning: an alarm, a commute, or Fajr. So the plan starts at the
 * wake time the user cannot move, subtracts the sleep they need and the time they typically
 * take to fall asleep, and reports the bedtime that falls out. That number is derived rather
 * than aspirational, which is why people act on it.
 *
 * ### Cycle alignment
 *
 * Sleep runs in roughly 90-minute cycles, and waking mid slow-wave is what produces the
 * groggy, worse-than-no-sleep morning. Where the user has room, the plan nudges the schedule
 * onto a cycle boundary — the idea Sleep Cycle built a company on.
 *
 * The nudge is deliberately capped ([MAX_CYCLE_NUDGE_MINUTES]). Ninety minutes is a
 * population average with real per-person and per-night variance, so treating it as precise
 * enough to justify cutting an hour of sleep would trade a certain loss for a speculative
 * gain. Duration wins; alignment is a tiebreak.
 */
object SmartBedtime {

    /** Population-average sleep cycle. Personal cycles vary roughly 70–120 minutes. */
    const val CYCLE_MINUTES = 90

    /** Default time to fall asleep, used until the user's own latency is known. */
    const val DEFAULT_LATENCY_MINUTES = 15

    /**
     * The most the schedule will shift to land on a cycle boundary.
     *
     * Beyond this the cost in lost sleep outweighs a benefit the 90-minute average is not
     * precise enough to promise.
     */
    const val MAX_CYCLE_NUDGE_MINUTES = 25

    /**
     * A full bedtime plan for tonight.
     *
     * @param recommendedBedtimeMinutes when to be in bed, night-centred signed minutes.
     * @param windDownStartMinutes when the routine and the "put the phone down" nudge begin.
     * @param cycles complete 90-minute cycles the plan allows for.
     * @param expectedSleepMinutes sleep the plan yields, latency already subtracted.
     * @param alignedWithFajr true when the wake target was derived from the prayer schedule.
     */
    data class BedtimePlan(
        val recommendedBedtimeMinutes: Int,
        val windDownStartMinutes: Int,
        val targetWakeMinutes: Int,
        val cycles: Int,
        val expectedSleepMinutes: Int,
        val latencyMinutes: Int,
        val alignedWithFajr: Boolean,
    )

    /**
     * Builds tonight's plan.
     *
     * @param fajrMinutes Fajr as night-centred signed minutes, when the prayer schedule is
     *  available. Only consulted if the user turned [SleepGoals.alignWakeWithFajr] on — the
     *  religious framing is opt-in everywhere in this module, never assumed.
     * @param baseline supplies the user's real sleep latency; without it the default is used.
     */
    fun plan(
        goals: SleepGoals = SleepGoals(),
        baseline: SleepBaseline = SleepBaseline.EMPTY,
        fajrMinutes: Int? = null,
        observedLatencyMinutes: Int? = null,
    ): BedtimePlan {
        val latency = (observedLatencyMinutes ?: DEFAULT_LATENCY_MINUTES).coerceIn(0, 90)

        val alignFajr = goals.alignWakeWithFajr && fajrMinutes != null
        val wake = if (alignFajr) {
            // Wake early enough for wudu and the prayer itself, not at the adhan.
            fajrMinutes - goals.fajrLeadMinutes
        } else {
            goals.targetWakeMinutes
        }

        val need = goals.sleepNeedMinutes.coerceIn(
            SleepGoals.MIN_SLEEP_NEED_MINUTES,
            SleepGoals.MAX_SLEEP_NEED_MINUTES,
        )

        // Naive bedtime: wake, minus the sleep needed, minus the time it takes to drop off.
        val naiveBedtime = wake - need - latency

        // Nudge onto a cycle boundary if it costs less than MAX_CYCLE_NUDGE_MINUTES.
        val alignedNeed = nearestCycleDuration(need)
        val bedtime = if (abs(alignedNeed - need) <= MAX_CYCLE_NUDGE_MINUTES) {
            wake - alignedNeed - latency
        } else {
            naiveBedtime
        }

        val actualSleep = wake - bedtime - latency
        val cycles = (actualSleep.toDouble() / CYCLE_MINUTES).roundToInt().coerceAtLeast(1)

        return BedtimePlan(
            recommendedBedtimeMinutes = normalise(bedtime),
            windDownStartMinutes = normalise(bedtime - goals.windDownMinutes),
            targetWakeMinutes = normalise(wake),
            cycles = cycles,
            expectedSleepMinutes = actualSleep.coerceAtLeast(0),
            latencyMinutes = latency,
            alignedWithFajr = alignFajr,
        )
    }

    /**
     * Wake times to offer when the user is going to bed *now* — the "if you sleep now" card.
     *
     * Each option is a whole number of cycles after falling asleep, so every one of them lands
     * in light sleep. Returned oldest-first with the recommended option flagged: the shortest
     * option that still clears the user's need, or the longest available if none does.
     */
    fun wakeOptionsIfSleepingNow(
        nowMinutes: Int,
        goals: SleepGoals = SleepGoals(),
        observedLatencyMinutes: Int? = null,
        maxCycles: Int = 6,
    ): List<WakeOption> {
        val latency = (observedLatencyMinutes ?: DEFAULT_LATENCY_MINUTES).coerceIn(0, 90)
        val asleepAt = nowMinutes + latency

        val options = (3..maxCycles).map { cycles ->
            val sleep = cycles * CYCLE_MINUTES
            WakeOption(
                wakeMinutes = normalise(asleepAt + sleep),
                cycles = cycles,
                sleepMinutes = sleep,
                recommended = false,
            )
        }
        val best = options.firstOrNull { it.sleepMinutes >= goals.sleepNeedMinutes }
            ?: options.lastOrNull()
        return options.map { it.copy(recommended = it === best) }
    }

    /** One cycle-aligned wake option. */
    data class WakeOption(
        val wakeMinutes: Int,
        val cycles: Int,
        val sleepMinutes: Int,
        val recommended: Boolean,
    )

    /**
     * How far ahead of the recommended bedtime we are, in minutes.
     *
     * Negative means the bedtime has passed. Used by the countdown widget, the Live Activity
     * and the bedtime reminder, all of which need the same answer and must not disagree.
     */
    fun minutesUntilBedtime(nowMinutes: Int, plan: BedtimePlan): Int =
        SleepTime.circularDeltaMinutes(nowMinutes, plan.recommendedBedtimeMinutes)

    /** True inside the wind-down window: past wind-down start, not yet past bedtime. */
    fun isWindingDown(nowMinutes: Int, plan: BedtimePlan): Boolean {
        val toBedtime = SleepTime.circularDeltaMinutes(nowMinutes, plan.recommendedBedtimeMinutes)
        val toWindDown = SleepTime.circularDeltaMinutes(nowMinutes, plan.windDownStartMinutes)
        return toWindDown <= 0 && toBedtime > 0
    }

    /**
     * Rounds a duration to the nearest whole number of cycles, never below three.
     *
     * Three cycles (4h30m) is the floor because below it the advice stops being "sleep well"
     * and starts being "sleep badly, tidily" — cycle alignment cannot rescue a night that
     * short, and offering it as a target would legitimise it.
     */
    internal fun nearestCycleDuration(minutes: Int): Int {
        val cycles = (minutes.toDouble() / CYCLE_MINUTES).roundToInt().coerceAtLeast(3)
        return cycles * CYCLE_MINUTES
    }

    /** Keeps a computed time inside the night-centred signed range. */
    private fun normalise(signed: Int): Int = SleepTime.toSigned(SleepTime.toMinuteOfDay(signed))

}
