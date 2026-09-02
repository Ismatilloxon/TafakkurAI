package com.example.tafakkurai.sleep

import kotlinx.serialization.Serializable

/**
 * The domain model for Sleep & Rest.
 *
 * Everything in this file is pure data with no platform, database or Compose dependency,
 * which is what lets the scoring engine ([SleepScoring]), the statistics engine
 * ([SleepStatistics]) and the coach ([SleepCoach]) be unit-tested without a device.
 *
 * ### One vocabulary for three sources
 *
 * Sleep arrives from HealthKit, Health Connect and from the app's own manual logging, and
 * all three name the stages differently. Rather than teach every screen the three dialects,
 * each bridge normalises into [SleepStage] at the edge, so a hypnogram drawn from an Apple
 * Watch night and one drawn from a Pixel Watch night are the same code path.
 */

// ─────────────────────────────────────────────────────────────── stages

/**
 * A normalised sleep stage.
 *
 * Named after Apple's vocabulary (Core rather than "light") because it is the one users
 * see most often, and because "light sleep" reads as a judgement — users read a big light
 * number as a bad night, when N1/N2 is most of a healthy night by design.
 *
 * [UNKNOWN] is deliberate and not a failure: a device that reports "asleep" without a
 * stage breakdown (most non-wearable sources) must still produce a valid session, and a
 * hypnogram that silently invented stages for it would be a lie.
 */
@Serializable
enum class SleepStage {
    AWAKE,
    REM,
    CORE,
    DEEP,
    UNKNOWN;

    /** True for the stages that count toward total sleep time. */
    val isAsleep: Boolean get() = this != AWAKE

    /** Lane index for the hypnogram, top (awake) to bottom (deep). */
    val lane: Int
        get() = when (this) {
            AWAKE -> 0
            REM -> 1
            CORE -> 2
            DEEP -> 3
            UNKNOWN -> 2
        }

    /** Stable wire/database token. Never localise this — it is persisted. */
    val token: String get() = name.lowercase()

    companion object {
        fun fromToken(raw: String?): SleepStage =
            entries.firstOrNull { it.token.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** One contiguous run of a single stage. Half-open interval: `[startMs, endMs)`. */
@Serializable
data class SleepSegment(
    val stage: SleepStage,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val minutes: Int get() = (durationMs / 60_000L).toInt()
}

// ─────────────────────────────────────────────────────────────── sources

/**
 * Where a session came from.
 *
 * Ordered by trust, which is the whole point: when two sources describe the same night,
 * [SleepMerge] keeps the higher-ranked one. A watch measured the night; a phone inferred it
 * from the fact that nobody picked it up; a human typed it in afterwards from memory.
 */
@Serializable
enum class SleepSource(val rank: Int) {
    /** Apple Watch / Wear OS / Oura / Whoop — real staging from a worn sensor. */
    WATCH(400),

    /** HealthKit or Health Connect aggregate written by another app. */
    HEALTH_STORE(300),

    /** Inferred on-device from phone-idle + alarm + scheduled bedtime. */
    AUTOMATIC(200),

    /** The user pressed "I'm going to sleep" in the app. */
    MANUAL(100);

    val token: String get() = name.lowercase()

    companion object {
        fun fromToken(raw: String?): SleepSource =
            entries.firstOrNull { it.token.equals(raw?.trim(), ignoreCase = true) } ?: MANUAL
    }
}

/** A night versus a nap. Naps are scored differently and never break a bedtime streak. */
@Serializable
enum class SleepKind {
    NIGHT,
    NAP;

    val token: String get() = name.lowercase()

    companion object {
        fun fromToken(raw: String?): SleepKind =
            entries.firstOrNull { it.token.equals(raw?.trim(), ignoreCase = true) } ?: NIGHT
    }
}

// ─────────────────────────────────────────────────────────── vitals

/**
 * Overnight physiology. Every field is nullable because every field is a separate
 * permission on both platforms, and a user who granted sleep but not heart rate must get a
 * working sleep screen rather than an error.
 *
 * @param hrvMs overnight average HRV (SDNN, milliseconds) — the single strongest recovery signal.
 * @param restingHeartRate lowest sustained BPM of the night.
 * @param respirationRate breaths per minute.
 * @param spo2 blood-oxygen percentage (0..100).
 * @param skinTemperatureDeltaC wrist temperature deviation from the personal baseline, in °C.
 */
@Serializable
data class SleepVitals(
    val hrvMs: Double? = null,
    val restingHeartRate: Double? = null,
    val averageHeartRate: Double? = null,
    val respirationRate: Double? = null,
    val spo2: Double? = null,
    val skinTemperatureDeltaC: Double? = null,
) {
    val isEmpty: Boolean
        get() = hrvMs == null && restingHeartRate == null && averageHeartRate == null &&
            respirationRate == null && spo2 == null && skinTemperatureDeltaC == null

    companion object {
        val NONE = SleepVitals()
    }
}

// ─────────────────────────────────────────────────────────── session

/**
 * One recorded sleep, night or nap.
 *
 * ### Why [dateKey] is the *wake* day
 *
 * A night that starts 23:40 Tuesday and ends 07:10 Wednesday belongs to Wednesday, because
 * that is the morning it produced and the day the user judges it by. Every sleep product
 * that files it under the bedtime day ends up explaining itself in a support article.
 *
 * A nap files under the day it happened, which is the same rule — the day it affects.
 *
 * ### Identity
 *
 * [id] is client-generated and is what the backend stores as `client_id`, exactly like
 * `gratitudeEntry`. That makes an outbox replay idempotent end to end. [externalId] is the
 * HealthKit/Health Connect record id, which is how a re-import recognises a night it has
 * already seen rather than duplicating it.
 */
@Serializable
data class SleepSession(
    val id: String,
    val kind: SleepKind = SleepKind.NIGHT,
    val source: SleepSource = SleepSource.MANUAL,
    val externalId: String? = null,

    /** First moment actually asleep (or best estimate). */
    val startMs: Long,
    /** Last moment asleep. */
    val endMs: Long,

    /** Time in bed, which brackets [startMs]/[endMs] and drives sleep efficiency. */
    val inBedStartMs: Long = startMs,
    val inBedEndMs: Long = endMs,

    val segments: List<SleepSegment> = emptyList(),
    val vitals: SleepVitals = SleepVitals.NONE,

    /** Local calendar day this session is filed under — the WAKE day. "yyyy-MM-dd". */
    val dateKey: String,

    /** Optional user annotations from the morning check-in. */
    val moodOnWaking: String? = null,
    val note: String? = null,
    val tags: List<String> = emptyList(),

    val createdAt: Long = startMs,
    val updatedAt: Long = endMs,
) {
    /** Wall-clock span from falling asleep to waking, interruptions included. */
    val sleepWindowMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    val inBedMs: Long get() = (inBedEndMs - inBedStartMs).coerceAtLeast(0L)
    val inBedMinutes: Int get() = (inBedMs / 60_000L).toInt()

    /**
     * Minutes actually asleep.
     *
     * Measured from the segments when they exist, because a night with three 20-minute
     * wakings is not the same night as an unbroken one even though the window is identical.
     * Falls back to the window for sources that report no staging at all.
     */
    val asleepMinutes: Int
        get() = if (segments.isEmpty()) {
            (sleepWindowMs / 60_000L).toInt()
        } else {
            segments.filter { it.stage.isAsleep }.sumOf { it.minutes }
        }

    val awakeMinutes: Int
        get() = segments.filter { it.stage == SleepStage.AWAKE }.sumOf { it.minutes }

    fun minutesIn(stage: SleepStage): Int = segments.filter { it.stage == stage }.sumOf { it.minutes }

    val deepMinutes: Int get() = minutesIn(SleepStage.DEEP)
    val remMinutes: Int get() = minutesIn(SleepStage.REM)
    val coreMinutes: Int get() = minutesIn(SleepStage.CORE)

    /** True when the source gave a real hypnogram rather than a single "asleep" block. */
    val hasStaging: Boolean
        get() = segments.any { it.stage == SleepStage.DEEP || it.stage == SleepStage.REM }

    /**
     * Sleep efficiency — asleep ÷ in bed, as a 0..1 fraction.
     *
     * Returns 1.0 rather than 0.0 when in-bed is unknown: a source that reports only the
     * asleep window has not told us the user lay awake, and inventing 0% efficiency would
     * crater a perfectly good night's score on missing data alone.
     */
    val efficiency: Double
        get() {
            val bed = inBedMinutes
            if (bed <= 0) return 1.0
            return (asleepMinutes.toDouble() / bed).coerceIn(0.0, 1.0)
        }

    /** Number of distinct wakings — the fragmentation signal behind "restfulness". */
    val interruptions: Int
        get() = segments.count { it.stage == SleepStage.AWAKE && it.minutes >= MIN_INTERRUPTION_MINUTES }

    /**
     * Minutes from lying down to falling asleep.
     *
     * Long latency is the earliest and most actionable insomnia signal the app can see
     * without a clinician, which is why the coach watches it directly.
     */
    val sleepLatencyMinutes: Int
        get() = ((startMs - inBedStartMs) / 60_000L).toInt().coerceAtLeast(0)

    /** True when this session looks substantial enough to score. */
    val isScorable: Boolean
        get() = when (kind) {
            SleepKind.NIGHT -> asleepMinutes >= MIN_NIGHT_MINUTES
            SleepKind.NAP -> asleepMinutes >= MIN_NAP_MINUTES
        }

    companion object {
        /** Below this, an "awake" segment is a turn-over, not a waking. */
        const val MIN_INTERRUPTION_MINUTES = 5

        /** Under three hours it is a long nap, not a night, and must not be scored as one. */
        const val MIN_NIGHT_MINUTES = 180

        /** Under ten minutes it is resting, not sleeping. */
        const val MIN_NAP_MINUTES = 10

        /** Anything longer than this is a stuck recorder, not a night. */
        const val MAX_SESSION_MINUTES = 20 * 60
    }
}

// ─────────────────────────────────────────────────────────── scores

/**
 * A 0..100 sub-score with the reasoning kept alongside it.
 *
 * [SleepScoring] returns these rather than bare numbers so the UI can explain a score
 * without recomputing it and without hard-coding a second copy of the thresholds. A score
 * a user cannot interrogate is a score they stop trusting.
 */
@Serializable
data class ScoreComponent(
    val key: String,
    val value: Int,
    val weight: Double,
    /** Stable numeric facts interpreted and localized by the presentation layer. */
    val detailArgs: List<Double>,
) {
    val weighted: Double get() = value * weight

    /** Stable diagnostic value retained for callers that only verify an explanation exists. */
    val detail: String get() = key
}

/** Qualitative band for a 0..100 score. Drives colour and copy everywhere. */
@Serializable
enum class ScoreBand {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR;

    companion object {
        fun of(score: Int): ScoreBand = when {
            score >= 85 -> EXCELLENT
            score >= 70 -> GOOD
            score >= 50 -> FAIR
            else -> POOR
        }
    }
}

/** The headline sleep-quality number plus every component that produced it. */
@Serializable
data class SleepScore(
    val value: Int,
    val components: List<ScoreComponent>,
    /** False when the source gave no staging, so the score leans on duration and timing. */
    val hasFullData: Boolean,
) {
    val band: ScoreBand get() = ScoreBand.of(value)

    companion object {
        val NONE = SleepScore(0, emptyList(), hasFullData = false)
    }
}

/**
 * How ready the body is today — the Whoop/Oura-style counterpart to sleep quality.
 *
 * Deliberately separate from [SleepScore]: a good night after a hard week still leaves you
 * under-recovered, and collapsing the two hides exactly the signal that matters most.
 */
@Serializable
data class RecoveryScore(
    val value: Int,
    val components: List<ScoreComponent>,
    /** True when enough personal baseline exists for the physiological terms to mean anything. */
    val hasBaseline: Boolean,
) {
    val band: ScoreBand get() = ScoreBand.of(value)

    companion object {
        val NONE = RecoveryScore(0, emptyList(), hasBaseline = false)
    }
}

/**
 * The user's rolling personal baseline.
 *
 * Every physiological score is relative — an HRV of 45 ms is excellent for one person and a
 * warning for another — so absolute thresholds are useless and this is what replaces them.
 * Built from a trailing window by [SleepStatistics.baselineOf].
 */
@Serializable
data class SleepBaseline(
    val nights: Int,
    val averageAsleepMinutes: Int,
    /** Circular mean bedtime, minutes from midnight; may be negative for pre-midnight. */
    val averageBedtimeMinutes: Int,
    val averageWakeMinutes: Int,
    val averageEfficiency: Double,
    val averageDeepShare: Double,
    val averageRemShare: Double,
    val hrvMs: Double? = null,
    val restingHeartRate: Double? = null,
    val respirationRate: Double? = null,
) {
    /** Under a week of nights, personalised comparisons are noise. */
    val isReliable: Boolean get() = nights >= MIN_NIGHTS_FOR_BASELINE

    companion object {
        const val MIN_NIGHTS_FOR_BASELINE = 7

        /** Trailing window the baseline is computed over. */
        const val WINDOW_NIGHTS = 30

        val EMPTY = SleepBaseline(
            nights = 0,
            averageAsleepMinutes = 0,
            averageBedtimeMinutes = 0,
            averageWakeMinutes = 0,
            averageEfficiency = 0.0,
            averageDeepShare = 0.0,
            averageRemShare = 0.0,
        )
    }
}

// ─────────────────────────────────────────────────────────── goals

/**
 * What the user is aiming for. Everything here is user-editable; nothing is prescriptive.
 *
 * @param sleepNeedMinutes nightly target. Default 8h — the midpoint of the 7–9h adult range.
 * @param targetBedtimeMinutes minutes from midnight, negative meaning before midnight
 *  (-30 = 23:30). Storing it signed avoids the modular comparison bug that makes 23:30 look
 *  23 hours later than 00:30.
 * @param windDownMinutes how long before bedtime the routine and reminders begin.
 */
@Serializable
data class SleepGoals(
    val sleepNeedMinutes: Int = DEFAULT_SLEEP_NEED_MINUTES,
    val targetBedtimeMinutes: Int = DEFAULT_TARGET_BEDTIME,
    val targetWakeMinutes: Int = DEFAULT_TARGET_WAKE,
    val windDownMinutes: Int = 45,
    /** Align the wake target with Fajr when the prayer schedule is available. */
    val alignWakeWithFajr: Boolean = false,
    /** Minutes before Fajr to wake when [alignWakeWithFajr] is on — room for wudu and prayer. */
    val fajrLeadMinutes: Int = 25,
) {
    companion object {
        const val DEFAULT_SLEEP_NEED_MINUTES = 8 * 60
        /** 23:00. */
        const val DEFAULT_TARGET_BEDTIME = -60
        /** 07:00. */
        const val DEFAULT_TARGET_WAKE = 7 * 60

        /** The clinically supported adult range, used to bound the personalisation. */
        const val MIN_SLEEP_NEED_MINUTES = 6 * 60
        const val MAX_SLEEP_NEED_MINUTES = 10 * 60
    }
}

// ─────────────────────────────────────────────────────────── aggregates

/** One day in a trend series — the shape every chart and heatmap consumes. */
@Serializable
data class SleepDayPoint(
    val dateKey: String,
    val asleepMinutes: Int,
    val score: Int,
    val bedtimeMinutes: Int?,
    val wakeMinutes: Int?,
    val hasSession: Boolean,
)

/** Direction of a trend, computed by comparing the two halves of a window. */
@Serializable
enum class TrendDirection { IMPROVING, STABLE, DECLINING }

/** A full statistics roll-up for one period. Backs the statistics screen and widgets. */
@Serializable
data class SleepStats(
    val nights: Int,
    val averageAsleepMinutes: Int,
    val averageScore: Int,
    val averageBedtimeMinutes: Int,
    val averageWakeMinutes: Int,
    val averageEfficiency: Double,
    val deepSharePercent: Int,
    val remSharePercent: Int,
    val consistencyScore: Int,
    val sleepDebtMinutes: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val bestDay: SleepDayPoint?,
    val worstDay: SleepDayPoint?,
    val trend: TrendDirection,
    val series: List<SleepDayPoint>,
) {
    companion object {
        val EMPTY = SleepStats(
            nights = 0,
            averageAsleepMinutes = 0,
            averageScore = 0,
            averageBedtimeMinutes = 0,
            averageWakeMinutes = 0,
            averageEfficiency = 0.0,
            deepSharePercent = 0,
            remSharePercent = 0,
            consistencyScore = 0,
            sleepDebtMinutes = 0,
            currentStreak = 0,
            longestStreak = 0,
            bestDay = null,
            worstDay = null,
            trend = TrendDirection.STABLE,
            series = emptyList(),
        )
    }
}

/** Window a statistics screen or chart is showing. */
@Serializable
enum class StatsRange(val days: Int) {
    WEEK(7),
    MONTH(30),
    QUARTER(90),
    YEAR(365),
}
