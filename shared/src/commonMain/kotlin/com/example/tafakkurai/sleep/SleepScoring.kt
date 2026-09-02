package com.example.tafakkurai.sleep

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The scoring engine: one night in, an explained 0..100 out.
 *
 * ### Why weights are redistributed rather than defaulted
 *
 * Most sleep apps score a missing signal as zero, which is why a phone-only user — no watch,
 * therefore no deep/REM breakdown — sees a permanent 60 and concludes the app is broken.
 * They are not sleeping badly; they are being marked down for hardware they do not own.
 *
 * Here every component declares a weight and is only added when it can actually be computed.
 * [Components.build] then normalises whatever was added back to 1.0, so a night with no
 * staging is scored fairly on duration, efficiency, restfulness and timing, and [SleepScore]
 * carries `hasFullData = false` so the UI can say what it is missing instead of the score
 * quietly lying.
 *
 * ### Where the thresholds come from
 *
 * The bands are the mainstream sleep-medicine consensus figures, not invented:
 *  - 7–9 h nightly for adults; 8 h is the default target.
 *  - Sleep efficiency ≥ 85% is the standard clinical cutoff for "good".
 *  - Slow-wave (deep) sleep ≈ 13–23% of total sleep time in healthy adults.
 *  - REM ≈ 20–25% of total sleep time.
 *  - One brief waking per night is normal and is not penalised.
 *
 * Everything physiological ([recovery]) is scored against the user's own rolling baseline
 * rather than a population constant, because an HRV of 45 ms is a strong night for one person
 * and a warning sign for another. Absolute HRV thresholds are the single most common error in
 * consumer recovery scoring.
 */
object SleepScoring {

    // ─────────────────────────────────────────────────── sleep score

    private const val W_DURATION = 0.35
    private const val W_EFFICIENCY = 0.15
    private const val W_DEEP = 0.15
    private const val W_REM = 0.15
    private const val W_RESTFULNESS = 0.10
    private const val W_TIMING = 0.10

    /**
     * Scores one session.
     *
     * @param baseline the user's rolling personal baseline. When it is not yet reliable the
     *  timing component falls back to the user's declared target bedtime, so a brand-new
     *  install still produces a meaningful score on night one.
     */
    fun score(
        session: SleepSession,
        goals: SleepGoals = SleepGoals(),
        baseline: SleepBaseline = SleepBaseline.EMPTY,
        offsetHours: Double? = null,
    ): SleepScore {
        if (!session.isScorable) return SleepScore.NONE

        val components = Components()
        val asleep = session.asleepMinutes

        // ── duration ──────────────────────────────────────────────────────
        val need = goals.sleepNeedMinutes.coerceIn(
            SleepGoals.MIN_SLEEP_NEED_MINUTES,
            SleepGoals.MAX_SLEEP_NEED_MINUTES,
        )
        val durationValue = durationScore(asleep, need)
        components.add(
            key = "duration",
            weight = W_DURATION,
            value = durationValue,
            detailArgs = listOf(asleep.toDouble(), need.toDouble()),
        )

        // ── efficiency ────────────────────────────────────────────────────
        // Only meaningful when the source actually reported time in bed. A source that gave
        // only the asleep window has not observed restlessness, and scoring it as perfect
        // would be as wrong as scoring it as zero — so it is omitted and reweighted.
        if (session.inBedMinutes > asleep) {
            val efficiencyValue = efficiencyScore(session.efficiency)
            components.add(
                key = "efficiency",
                weight = W_EFFICIENCY,
                value = efficiencyValue,
                detailArgs = listOf(
                    (session.efficiency * 100).roundToInt().toDouble(),
                    session.inBedMinutes.toDouble(),
                ),
            )
        }

        // ── stage architecture ────────────────────────────────────────────
        if (session.hasStaging && asleep > 0) {
            val deepShare = session.deepMinutes.toDouble() / asleep
            components.add(
                key = "deep",
                weight = W_DEEP,
                value = deepScore(deepShare),
                detailArgs = listOf(session.deepMinutes.toDouble(), (deepShare * 100).roundToInt().toDouble()),
            )

            val remShare = session.remMinutes.toDouble() / asleep
            components.add(
                key = "rem",
                weight = W_REM,
                value = remScore(remShare),
                detailArgs = listOf(session.remMinutes.toDouble(), (remShare * 100).roundToInt().toDouble()),
            )
        }

        // ── restfulness ───────────────────────────────────────────────────
        val restfulnessValue = restfulnessScore(session.interruptions, session.sleepLatencyMinutes)
        components.add(
            key = "restfulness",
            weight = W_RESTFULNESS,
            value = restfulnessValue,
            detailArgs = listOf(session.interruptions.toDouble(), session.sleepLatencyMinutes.toDouble()),
        )

        // ── timing / circadian alignment ──────────────────────────────────
        val bedtime = SleepTime.signedMinuteOfDay(
            session.inBedStartMs,
            offsetHours ?: com.example.tafakkurai.prayer.deviceUtcOffsetHours(),
        )
        val reference = if (baseline.isReliable) baseline.averageBedtimeMinutes else goals.targetBedtimeMinutes
        val drift = SleepTime.circularDeltaMinutes(reference, bedtime)
        components.add(
            key = "timing",
            weight = W_TIMING,
            value = timingScore(drift),
            detailArgs = listOf(drift.toDouble()),
        )

        return SleepScore(
            value = components.build(),
            components = components.list(),
            hasFullData = session.hasStaging && session.inBedMinutes > asleep,
        )
    }

    /**
     * Duration against personal need.
     *
     * Asymmetric on purpose. Falling an hour short of eight is a real cost; sleeping an hour
     * over is not, so the curve plateaus at 100 through a 20% overshoot and only then declines
     * gently — long sleep correlates with poor outcomes but is rarely the cause, and marking
     * a recovery lie-in as a bad night is how a sleep app teaches people to under-sleep.
     */
    internal fun durationScore(asleepMinutes: Int, needMinutes: Int): Int {
        if (needMinutes <= 0) return 0
        val ratio = asleepMinutes.toDouble() / needMinutes
        val raw = when {
            ratio in 1.0..1.2 -> 100.0
            ratio > 1.2 -> 100.0 - (ratio - 1.2) * 100.0
            else -> 100.0 - (1.0 - ratio) * 175.0
        }
        return raw.coerceIn(0.0, 100.0).roundToInt()
    }

    /** Sleep efficiency. 92% and above is a full score; 85% is the clinical "good" line. */
    internal fun efficiencyScore(efficiency: Double): Int {
        val raw = if (efficiency >= 0.92) 100.0 else 100.0 - (0.92 - efficiency) * 350.0
        return raw.coerceIn(0.0, 100.0).roundToInt()
    }

    /**
     * Deep-sleep share against the healthy 13–23% band.
     *
     * The penalty above the band is deliberately shallow and floored at 80: an unusually deep
     * night is almost always slow-wave rebound after a deficit, which is the body repaying a
     * debt correctly, not a problem to flag.
     */
    internal fun deepScore(share: Double): Int {
        val raw = when {
            share in 0.13..0.23 -> 100.0
            share < 0.13 -> 100.0 - (0.13 - share) * 700.0
            else -> (100.0 - (share - 0.23) * 200.0).coerceAtLeast(80.0)
        }
        return raw.coerceIn(0.0, 100.0).roundToInt()
    }

    /** REM share against the healthy 20–25% band, with the same asymmetry as [deepScore]. */
    internal fun remScore(share: Double): Int {
        val raw = when {
            share in 0.20..0.25 -> 100.0
            share < 0.20 -> 100.0 - (0.20 - share) * 500.0
            else -> (100.0 - (share - 0.25) * 150.0).coerceAtLeast(85.0)
        }
        return raw.coerceIn(0.0, 100.0).roundToInt()
    }

    /**
     * Fragmentation and sleep latency.
     *
     * The first waking is free — brief arousals between cycles are normal physiology, and an
     * app that penalises them trains users to worry about healthy sleep. Latency is free up to
     * 20 minutes, the conventional "normal" threshold.
     */
    internal fun restfulnessScore(interruptions: Int, latencyMinutes: Int): Int {
        var raw = 100.0
        if (interruptions > 1) raw -= (interruptions - 1) * 6.0
        if (latencyMinutes > 20) raw -= (latencyMinutes - 20) * 1.2
        return raw.coerceIn(0.0, 100.0).roundToInt()
    }

    /** Bedtime drift from the personal (or target) bedtime. Twenty minutes either way is free. */
    internal fun timingScore(driftMinutes: Int): Int {
        val drift = abs(driftMinutes)
        val raw = if (drift <= 20) 100.0 else 100.0 - (drift - 20) * 0.6
        return raw.coerceIn(0.0, 100.0).roundToInt()
    }

    // ─────────────────────────────────────────────────── recovery score

    private const val W_HRV = 0.40
    private const val W_RHR = 0.25
    private const val W_SLEEP = 0.20
    private const val W_DEBT = 0.10
    private const val W_RESPIRATION = 0.05

    /**
     * How ready the body is today.
     *
     * Physiological terms are scored as a **deviation from the user's own baseline**, never
     * against a population constant. When no reliable baseline exists yet, those terms are
     * simply not added — the score becomes sleep quality plus accumulated debt, and
     * [RecoveryScore.hasBaseline] is false so the UI can say "still learning your baseline"
     * rather than presenting a confident number built on nothing.
     *
     * @param sleepDebtMinutes rolling debt from [SleepStatistics.sleepDebt]; positive means owed.
     */
    fun recovery(
        session: SleepSession,
        sleepScore: SleepScore,
        baseline: SleepBaseline = SleepBaseline.EMPTY,
        sleepDebtMinutes: Int = 0,
    ): RecoveryScore {
        val components = Components()
        val vitals = session.vitals
        val usableBaseline = baseline.isReliable

        // ── HRV, the strongest single signal ──────────────────────────────
        val hrv = vitals.hrvMs
        val hrvBase = baseline.hrvMs
        if (usableBaseline && hrv != null && hrvBase != null && hrvBase > 0) {
            val ratio = hrv / hrvBase
            val raw = if (ratio >= 1.0) 100.0 else 100.0 - (1.0 - ratio) * 200.0
            components.add(
                key = "hrv",
                weight = W_HRV,
                value = raw.coerceIn(0.0, 100.0).roundToInt(),
                detailArgs = listOf(hrv.roundToInt().toDouble(), hrvBase.roundToInt().toDouble()),
            )
        }

        // ── resting heart rate ────────────────────────────────────────────
        val rhr = vitals.restingHeartRate
        val rhrBase = baseline.restingHeartRate
        if (usableBaseline && rhr != null && rhrBase != null && rhrBase > 0) {
            val delta = rhr - rhrBase
            val raw = if (delta <= 0) 100.0 else 100.0 - delta * 7.0
            components.add(
                key = "rhr",
                weight = W_RHR,
                value = raw.coerceIn(0.0, 100.0).roundToInt(),
                detailArgs = listOf(rhr.roundToInt().toDouble(), delta.roundToInt().toDouble()),
            )
        }

        // ── last night's quality ──────────────────────────────────────────
        components.add(
            key = "sleep",
            weight = W_SLEEP,
            value = sleepScore.value,
            detailArgs = listOf(sleepScore.value.toDouble()),
        )

        // ── accumulated debt ──────────────────────────────────────────────
        val debtValue = debtScore(sleepDebtMinutes)
        components.add(
            key = "debt",
            weight = W_DEBT,
            value = debtValue,
            detailArgs = listOf(sleepDebtMinutes.toDouble()),
        )

        // ── respiratory rate ──────────────────────────────────────────────
        val respiration = vitals.respirationRate
        val respirationBase = baseline.respirationRate
        if (usableBaseline && respiration != null && respirationBase != null && respirationBase > 0) {
            val delta = abs(respiration - respirationBase)
            val raw = if (delta <= 0.5) 100.0 else 100.0 - (delta - 0.5) * 40.0
            components.add(
                key = "respiration",
                weight = W_RESPIRATION,
                value = raw.coerceIn(0.0, 100.0).roundToInt(),
                detailArgs = listOf((respiration * 10).roundToInt() / 10.0),
            )
        }

        return RecoveryScore(
            value = components.build(),
            components = components.list(),
            hasBaseline = usableBaseline,
        )
    }

    /** Rolling sleep debt. Roughly one point per eight minutes owed, floored at zero. */
    internal fun debtScore(debtMinutes: Int): Int {
        if (debtMinutes <= 15) return 100
        val raw = 100.0 - (debtMinutes / 60.0) * 8.0
        return raw.coerceIn(0.0, 100.0).roundToInt()
    }

    // ─────────────────────────────────────────────────── nap scoring

    /**
     * Whether a nap helped or will cost tonight's sleep.
     *
     * The 20–30 minute window avoids waking mid slow-wave, which is where grogginess comes
     * from; a full 90-minute cycle is the other safe landing point. Anything after roughly
     * 15:00 eats into the adenosine pressure that makes falling asleep at night easy — which
     * is why the app would rather say "this one may cost you tonight" than silently log it.
     */
    fun napVerdict(session: SleepSession, offsetHours: Double? = null): NapVerdict {
        val minutes = session.asleepMinutes
        val startMinute = SleepTime.toMinuteOfDay(
            SleepTime.signedMinuteOfDay(
                session.startMs,
                offsetHours ?: com.example.tafakkurai.prayer.deviceUtcOffsetHours(),
            ),
        )
        val late = startMinute >= LATE_NAP_MINUTE
        return when {
            minutes in 20..30 && !late -> NapVerdict.OPTIMAL
            minutes in 80..100 && !late -> NapVerdict.FULL_CYCLE
            late -> NapVerdict.TOO_LATE
            minutes > 45 -> NapVerdict.TOO_LONG
            else -> NapVerdict.SHORT
        }
    }

    /** 15:00 — after this a nap starts competing with the night. */
    private const val LATE_NAP_MINUTE = 15 * 60

    // ─────────────────────────────────────────────────── component builder

    /**
     * Collects the components that could actually be computed and normalises their weights.
     *
     * This is the piece that makes a phone-only user's score honest. If deep and REM are
     * absent, the remaining 0.70 of weight is scaled back up to 1.0 rather than the missing
     * 0.30 being scored as zero.
     */
    internal class Components {
        private val items = mutableListOf<ScoreComponent>()

        fun add(key: String, weight: Double, value: Int, detailArgs: List<Double>) {
            items += ScoreComponent(
                key = key,
                value = value.coerceIn(0, 100),
                weight = weight,
                detailArgs = detailArgs,
            )
        }

        fun list(): List<ScoreComponent> {
            val total = items.sumOf { it.weight }
            if (total <= 0.0) return items
            // Publish the normalised weight, so a UI breakdown adds up to 100% on screen.
            return items.map { it.copy(weight = it.weight / total) }
        }

        fun build(): Int {
            val total = items.sumOf { it.weight }
            if (total <= 0.0) return 0
            val weighted = items.sumOf { it.value * it.weight }
            return (weighted / total).roundToInt().coerceIn(0, 100)
        }
    }
}

/** How a nap landed. Drives the one line of copy shown after logging one. */
enum class NapVerdict {
    /** 20–30 minutes before mid-afternoon: alertness without grogginess. */
    OPTIMAL,

    /** A complete ~90-minute cycle, which also avoids waking mid slow-wave. */
    FULL_CYCLE,

    /** Long enough to land in deep sleep and wake groggy. */
    TOO_LONG,

    /** Late enough to reduce tonight's sleep pressure. */
    TOO_LATE,

    /** Brief rest — no harm, limited benefit. */
    SHORT,
}
