package com.example.tafakkurai.sleep

import com.example.tafakkurai.spiritual.dayNumberOf
import com.example.tafakkurai.spiritual.streakLength
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Trends, baselines, debt and streaks.
 *
 * Pure functions over a list of sessions, so every number on the statistics screen can be
 * unit-tested against a fixture instead of a device. Nothing here reads a clock: the caller
 * passes `todayKey`, which is also what makes "what will this look like next Tuesday"
 * answerable in a test.
 */
object SleepStatistics {

    /** Nights below this share of the target do not count toward a streak. */
    private const val STREAK_THRESHOLD = 0.85

    /**
     * Surplus sleep repays debt at half rate.
     *
     * You cannot bank sleep the way you accumulate a deficit — a ten-hour Saturday does not
     * undo five short weeknights, and a debt model that lets it cancel out tells people the
     * opposite of what the research says. Discounting the repayment is the honest compromise
     * between "surplus does nothing" (demoralising, and not quite true) and "surplus cancels"
     * (comfortable, and false).
     */
    private const val SURPLUS_REPAYMENT_RATE = 0.5

    // ────────────────────────────────────────────────────── baseline

    /**
     * The user's rolling personal baseline over the trailing [windowNights].
     *
     * Naps are excluded throughout: a 25-minute afternoon nap would drag the average night
     * length down and corrupt every comparison built on it.
     */
    fun baselineOf(
        sessions: List<SleepSession>,
        todayKey: String,
        windowNights: Int = SleepBaseline.WINDOW_NIGHTS,
        offsetHours: Double? = null,
    ): SleepBaseline {
        val offset = offsetHours ?: com.example.tafakkurai.prayer.deviceUtcOffsetHours()
        val today = dayNumberOf(todayKey) ?: return SleepBaseline.EMPTY

        val nights = sessions
            .filter { it.kind == SleepKind.NIGHT && it.isScorable }
            .filter { s ->
                val day = dayNumberOf(s.dateKey) ?: return@filter false
                day <= today && day > today - windowNights
            }
        if (nights.isEmpty()) return SleepBaseline.EMPTY

        val bedtimes = nights.map { SleepTime.signedMinuteOfDay(it.inBedStartMs, offset) }
        val wakes = nights.map { SleepTime.signedMinuteOfDay(it.inBedEndMs, offset) }

        val staged = nights.filter { it.hasStaging && it.asleepMinutes > 0 }

        return SleepBaseline(
            nights = nights.size,
            averageAsleepMinutes = nights.map { it.asleepMinutes }.average().roundToInt(),
            averageBedtimeMinutes = SleepTime.circularMeanMinutes(bedtimes) ?: 0,
            averageWakeMinutes = SleepTime.circularMeanMinutes(wakes) ?: 0,
            averageEfficiency = nights.map { it.efficiency }.average(),
            averageDeepShare = staged.map { it.deepMinutes.toDouble() / it.asleepMinutes }
                .averageOrZero(),
            averageRemShare = staged.map { it.remMinutes.toDouble() / it.asleepMinutes }
                .averageOrZero(),
            hrvMs = nights.mapNotNull { it.vitals.hrvMs }.averageOrNull(),
            restingHeartRate = nights.mapNotNull { it.vitals.restingHeartRate }.averageOrNull(),
            respirationRate = nights.mapNotNull { it.vitals.respirationRate }.averageOrNull(),
        )
    }

    // ────────────────────────────────────────────────────── debt

    /**
     * Rolling sleep debt in minutes over the trailing [days]. Positive means owed.
     *
     * A missing night counts as a full night's deficit rather than being skipped. Skipping it
     * would let a user who stopped tracking during a bad week watch their debt fall to zero,
     * which is precisely backwards.
     */
    fun sleepDebt(
        sessions: List<SleepSession>,
        todayKey: String,
        needMinutes: Int = SleepGoals.DEFAULT_SLEEP_NEED_MINUTES,
        days: Int = 14,
    ): Int {
        val today = dayNumberOf(todayKey) ?: return 0
        val byDay = sessions
            .filter { it.kind == SleepKind.NIGHT }
            .mapNotNull { s -> dayNumberOf(s.dateKey)?.let { it to s } }
            .groupBy({ it.first }, { it.second })

        var debt = 0.0
        // Yesterday backwards: today's night has not happened yet at most hours of the day,
        // and counting it as missing would show every user a fresh eight-hour debt each morning.
        for (i in 1..days) {
            val day = today - i
            val asleep = byDay[day]?.sumOf { it.asleepMinutes } ?: 0
            val delta = needMinutes - asleep
            debt += if (delta >= 0) delta.toDouble() else delta * SURPLUS_REPAYMENT_RATE
        }
        return debt.roundToInt().coerceAtLeast(0)
    }

    // ────────────────────────────────────────────────────── consistency

    /**
     * Circadian consistency, 0..100, from the spread of bed and wake times.
     *
     * Wake time is weighted more heavily than bedtime because it is the stronger circadian
     * anchor: morning light exposure at a fixed hour is what actually entrains the clock,
     * whereas bedtime largely follows from it. Rewarding a fixed wake time is therefore both
     * better science and better advice.
     *
     * Uses [SleepTime.circularDeviationMinutes], so times either side of midnight are measured
     * as the few minutes apart they really are.
     */
    fun consistencyScore(
        sessions: List<SleepSession>,
        todayKey: String,
        days: Int = 14,
        offsetHours: Double? = null,
    ): Int {
        val offset = offsetHours ?: com.example.tafakkurai.prayer.deviceUtcOffsetHours()
        val today = dayNumberOf(todayKey) ?: return 0
        val nights = sessions
            .filter { it.kind == SleepKind.NIGHT && it.isScorable }
            .filter { s ->
                val day = dayNumberOf(s.dateKey) ?: return@filter false
                day <= today && day > today - days
            }
        if (nights.size < 3) return 0

        val bedDeviation = SleepTime.circularDeviationMinutes(
            nights.map { SleepTime.signedMinuteOfDay(it.inBedStartMs, offset) },
        )
        val wakeDeviation = SleepTime.circularDeviationMinutes(
            nights.map { SleepTime.signedMinuteOfDay(it.inBedEndMs, offset) },
        )

        val bedScore = (100.0 - bedDeviation * 0.5).coerceIn(0.0, 100.0)
        val wakeScore = (100.0 - wakeDeviation * 0.5).coerceIn(0.0, 100.0)
        return (bedScore * 0.45 + wakeScore * 0.55).roundToInt()
    }

    // ────────────────────────────────────────────────────── series

    /**
     * A continuous day-by-day series from [days] ago to [todayKey], gaps included.
     *
     * Gaps are emitted as points with `hasSession = false` rather than omitted, because a
     * chart that silently closes over missing nights draws a flattering line through exactly
     * the days the user should be looking at.
     */
    fun series(
        sessions: List<SleepSession>,
        todayKey: String,
        days: Int,
        goals: SleepGoals = SleepGoals(),
        baseline: SleepBaseline = SleepBaseline.EMPTY,
        offsetHours: Double? = null,
    ): List<SleepDayPoint> {
        val offset = offsetHours ?: com.example.tafakkurai.prayer.deviceUtcOffsetHours()
        val today = dayNumberOf(todayKey) ?: return emptyList()

        val byDay = sessions
            .filter { it.kind == SleepKind.NIGHT }
            .mapNotNull { s -> dayNumberOf(s.dateKey)?.let { it to s } }
            .groupBy({ it.first }, { it.second })

        return (days - 1 downTo 0).map { back ->
            val day = today - back
            val key = SleepTime.dateKeyOfDayNumber(day)
            // More than one night filed under a day (a nap promoted, a re-import) — take the
            // longest, which is the one the user means by "last night".
            val night = byDay[day]?.maxByOrNull { it.asleepMinutes }
            if (night == null) {
                SleepDayPoint(
                    dateKey = key,
                    asleepMinutes = 0,
                    score = 0,
                    bedtimeMinutes = null,
                    wakeMinutes = null,
                    hasSession = false,
                )
            } else {
                SleepDayPoint(
                    dateKey = key,
                    asleepMinutes = night.asleepMinutes,
                    score = SleepScoring.score(night, goals, baseline, offset).value,
                    bedtimeMinutes = SleepTime.signedMinuteOfDay(night.inBedStartMs, offset),
                    wakeMinutes = SleepTime.signedMinuteOfDay(night.inBedEndMs, offset),
                    hasSession = true,
                )
            }
        }
    }

    // ────────────────────────────────────────────────────── roll-up

    /** The full statistics roll-up backing the statistics screen, widgets and the coach. */
    fun stats(
        sessions: List<SleepSession>,
        todayKey: String,
        range: StatsRange = StatsRange.WEEK,
        goals: SleepGoals = SleepGoals(),
        offsetHours: Double? = null,
    ): SleepStats {
        val offset = offsetHours ?: com.example.tafakkurai.prayer.deviceUtcOffsetHours()
        val baseline = baselineOf(sessions, todayKey, offsetHours = offset)
        val points = series(sessions, todayKey, range.days, goals, baseline, offset)
        val logged = points.filter { it.hasSession }
        if (logged.isEmpty()) return SleepStats.EMPTY

        val today = dayNumberOf(todayKey)
        val windowNights = sessions
            .filter { it.kind == SleepKind.NIGHT && it.isScorable }
            .filter { s ->
                val day = dayNumberOf(s.dateKey) ?: return@filter false
                today != null && day <= today && day > today - range.days
            }

        val stagedTotal = windowNights.filter { it.hasStaging }
        val totalAsleep = stagedTotal.sumOf { it.asleepMinutes }

        // Streak days: nights that actually met the goal, not merely nights that were logged.
        // A streak of tracked-but-terrible nights is a vanity metric.
        val qualifying = windowNights
            .filter { it.asleepMinutes >= goals.sleepNeedMinutes * STREAK_THRESHOLD }
            .map { it.dateKey }
            .toSet()
        val allQualifying = sessions
            .filter { it.kind == SleepKind.NIGHT && it.asleepMinutes >= goals.sleepNeedMinutes * STREAK_THRESHOLD }
            .map { it.dateKey }
            .toSet()

        return SleepStats(
            nights = logged.size,
            averageAsleepMinutes = logged.map { it.asleepMinutes }.average().roundToInt(),
            averageScore = logged.map { it.score }.average().roundToInt(),
            averageBedtimeMinutes = SleepTime.circularMeanMinutes(logged.mapNotNull { it.bedtimeMinutes }) ?: 0,
            averageWakeMinutes = SleepTime.circularMeanMinutes(logged.mapNotNull { it.wakeMinutes }) ?: 0,
            averageEfficiency = windowNights.map { it.efficiency }.averageOrZero(),
            deepSharePercent = sharePercent(stagedTotal.sumOf { it.deepMinutes }, totalAsleep),
            remSharePercent = sharePercent(stagedTotal.sumOf { it.remMinutes }, totalAsleep),
            consistencyScore = consistencyScore(sessions, todayKey, minOf(range.days, 14), offset),
            sleepDebtMinutes = sleepDebt(sessions, todayKey, goals.sleepNeedMinutes),
            currentStreak = streakLength(qualifying, todayKey),
            longestStreak = longestStreak(allQualifying),
            bestDay = logged.maxByOrNull { it.score },
            worstDay = logged.minByOrNull { it.score },
            trend = trendOf(logged),
            series = points,
        )
    }

    private fun sharePercent(part: Int, whole: Int): Int =
        if (whole <= 0) 0 else ((part.toDouble() / whole) * 100).roundToInt()

    /**
     * Direction of travel, from the two halves of the window.
     *
     * A three-point dead band stops the arrow flickering between "improving" and "declining"
     * on noise, which is what makes a trend indicator worth showing at all.
     */
    internal fun trendOf(points: List<SleepDayPoint>): TrendDirection {
        if (points.size < 4) return TrendDirection.STABLE
        val half = points.size / 2
        val older = points.take(half).map { it.score }.average()
        val newer = points.drop(points.size - half).map { it.score }.average()
        val delta = newer - older
        return when {
            delta > 3.0 -> TrendDirection.IMPROVING
            delta < -3.0 -> TrendDirection.DECLINING
            else -> TrendDirection.STABLE
        }
    }

    /** Longest run of consecutive qualifying days anywhere in the history. */
    internal fun longestStreak(dateKeys: Set<String>): Int {
        // `toSortedSet` is JVM-only. This file is commonMain and also compiles for
        // Kotlin/Native, where a sorted immutable list gives us the same ordering.
        val days: List<Long> = dateKeys.mapNotNull(::dayNumberOf).sorted()
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        var previous: Long? = null
        for (day in days) {
            val prior = previous
            if (prior != null && day == prior + 1L) {
                run++
            } else if (prior != null) {
                run = 1
            }
            if (run > best) best = run
            previous = day
        }
        return best
    }

    // ────────────────────────────────────────────────────── weekday shape

    /**
     * Average sleep per weekday over the window, Monday-first.
     *
     * This is what surfaces social jetlag: a user whose weekend average sits two hours later
     * than their weekday average is giving themselves a transatlantic flight every Friday, and
     * that pattern is invisible in a plain nightly chart.
     */
    fun byWeekday(points: List<SleepDayPoint>): List<Int> {
        val buckets = Array(7) { mutableListOf<Int>() }
        for (p in points) {
            if (!p.hasSession) continue
            val day = dayNumberOf(p.dateKey) ?: continue
            // 1970-01-01 was a Thursday; shift so Monday is index 0.
            val weekday = (((day + 3) % 7) + 7) % 7
            buckets[weekday.toInt()] += p.asleepMinutes
        }
        return buckets.map { if (it.isEmpty()) 0 else it.average().roundToInt() }
    }

    /**
     * Social jetlag in minutes: how much later the user sleeps on free days than work days.
     *
     * Computed on midpoints of sleep rather than bedtimes, which is the established definition
     * and is robust to a weekend lie-in that starts at the same hour but runs longer.
     */
    fun socialJetlagMinutes(
        sessions: List<SleepSession>,
        todayKey: String,
        days: Int = 28,
        offsetHours: Double? = null,
    ): Int {
        val offset = offsetHours ?: com.example.tafakkurai.prayer.deviceUtcOffsetHours()
        val today = dayNumberOf(todayKey) ?: return 0
        val weekday = mutableListOf<Int>()
        val weekend = mutableListOf<Int>()

        for (s in sessions) {
            if (s.kind != SleepKind.NIGHT || !s.isScorable) continue
            val day = dayNumberOf(s.dateKey) ?: continue
            if (day > today || day <= today - days) continue
            val midpoint = SleepTime.signedMinuteOfDay((s.startMs + s.endMs) / 2, offset)
            // Wake day Saturday (5) or Sunday (6) — the mornings without an alarm.
            val index = (((day + 3) % 7) + 7) % 7
            if (index >= 5) weekend += midpoint else weekday += midpoint
        }
        if (weekday.size < 2 || weekend.size < 1) return 0

        val work = SleepTime.circularMeanMinutes(weekday) ?: return 0
        val free = SleepTime.circularMeanMinutes(weekend) ?: return 0
        return abs(SleepTime.circularDeltaMinutes(work, free))
    }

    // ────────────────────────────────────────────────────── helpers

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
