package com.example.tafakkurai.sleep

import kotlin.math.roundToInt

/**
 * The sleep coach: a deterministic rules engine over the user's own numbers.
 *
 * ### Why rules rather than a model
 *
 * Every recommendation here has to survive the question "why am I being told this?", and a
 * rule can answer it by pointing at the number that fired it. That is also what makes the
 * whole engine unit-testable and what keeps it working with no network — the coach is most
 * useful at 23:40, which is exactly when nobody wants to wait on an API call.
 *
 * The backend `coach` module still handles open-ended conversation. This engine handles the
 * daily, structured advice, and it never fabricates a cause it cannot see: no rule claims
 * caffeine or screens *caused* anything, because the app cannot observe either. They are
 * offered as the cheapest things to try against a symptom that was measured.
 *
 * ### Ordering
 *
 * Rules emit a priority and the top [MAX_RECOMMENDATIONS] are shown. Showing everything at
 * once is how a coaching surface becomes wallpaper — three things a person might actually do
 * beats eleven things they will scroll past.
 */
object SleepCoach {

    const val MAX_RECOMMENDATIONS = 3

    /** What a recommendation is about. Drives the icon and the accent colour. */
    enum class Category {
        SCHEDULE,
        CONSISTENCY,
        ENVIRONMENT,
        CAFFEINE,
        SCREENS,
        EXERCISE,
        HYDRATION,
        LIGHT,
        BREATHING,
        MINDFULNESS,
        SPIRITUAL,
        RECOVERY,
    }

    /**
     * One piece of advice.
     *
     * @param evidence the number that fired the rule, shown verbatim so the advice can be
     *  audited by the person receiving it.
     */
    data class Recommendation(
        val id: String,
        val category: Category,
        val icon: String,
        val colorToken: String,
        val priority: Int,
        val actionRoute: String? = null,
        /** Numeric facts used to render the recommendation in the active language. */
        val titleArgs: List<Int> = emptyList(),
        val bodyArgs: List<Int> = emptyList(),
        val evidenceArgs: List<Int> = emptyList(),
    )

    /** Everything a rule may inspect. */
    data class Context(
        val lastNight: SleepSession?,
        val lastScore: SleepScore,
        val recovery: RecoveryScore,
        val stats: SleepStats,
        val baseline: SleepBaseline,
        val goals: SleepGoals,
        val plan: SmartBedtime.BedtimePlan,
        val socialJetlagMinutes: Int,
        /** True when the user has enabled the optional Islamic routine. */
        val islamicRoutineEnabled: Boolean,
    )

    /**
     * Produces today's advice, highest priority first.
     *
     * Deduplicated by category: two schedule rules firing at once would fill the whole surface
     * with variations on "go to bed earlier", crowding out the one different idea that might
     * actually be the useful one.
     */
    fun recommend(context: Context): List<Recommendation> =
        RULES.mapNotNull { it(context) }
            .sortedByDescending { it.priority }
            .distinctBy { it.category }
            .take(MAX_RECOMMENDATIONS)

    // ─────────────────────────────────────────────────────────── rules

    private val RULES: List<(Context) -> Recommendation?> = listOf(
        ::shortSleepRule,
        ::sleepDebtRule,
        ::inconsistentScheduleRule,
        ::socialJetlagRule,
        ::longLatencyRule,
        ::fragmentedNightRule,
        ::lowDeepSleepRule,
        ::lowRemRule,
        ::lowRecoveryRule,
        ::lateBedtimeDriftRule,
        ::morningLightRule,
        ::fajrRoutineRule,
        ::windDownRule,
    )

    /** The single biggest lever: not enough hours. Nothing else matters as much. */
    private fun shortSleepRule(c: Context): Recommendation? {
        val avg = c.stats.averageAsleepMinutes
        if (c.stats.nights < 3 || avg >= c.goals.sleepNeedMinutes - 20) return null
        val shortfall = c.goals.sleepNeedMinutes - avg
        return Recommendation(
            id = "short-sleep",
            category = Category.SCHEDULE,
            icon = "moon",
            colorToken = "purple",
            priority = 100 + (shortfall / 10),
            actionRoute = "sleep:goals",
            titleArgs = listOf(minOf(shortfall, 30)),
            bodyArgs = listOf(avg, c.goals.sleepNeedMinutes),
            evidenceArgs = listOf(shortfall, c.stats.nights),
        )
    }

    /** Accumulated debt, which is what makes a "fine" night still feel terrible. */
    private fun sleepDebtRule(c: Context): Recommendation? {
        val debt = c.stats.sleepDebtMinutes
        if (debt < 120) return null
        return Recommendation(
            id = "sleep-debt",
            category = Category.RECOVERY,
            icon = "activity",
            colorToken = "warn",
            priority = 85 + (debt / 60),
            titleArgs = listOf(debt),
            evidenceArgs = listOf(debt),
        )
    }

    /** Irregularity costs more than most people expect — often more than an hour of duration. */
    private fun inconsistentScheduleRule(c: Context): Recommendation? {
        if (c.stats.nights < 5 || c.stats.consistencyScore >= 70) return null
        return Recommendation(
            id = "consistency",
            category = Category.CONSISTENCY,
            icon = "clock",
            colorToken = "cyan",
            priority = 90 - c.stats.consistencyScore / 2,
            actionRoute = "sleep:goals",
            evidenceArgs = listOf(c.stats.consistencyScore),
        )
    }

    /** Weekend drift, named properly so it stops looking like a harmless treat. */
    private fun socialJetlagRule(c: Context): Recommendation? {
        if (c.socialJetlagMinutes < 90) return null
        return Recommendation(
            id = "social-jetlag",
            category = Category.CONSISTENCY,
            icon = "calendar",
            colorToken = "gold",
            priority = 70 + c.socialJetlagMinutes / 30,
            titleArgs = listOf(c.socialJetlagMinutes),
            evidenceArgs = listOf(c.socialJetlagMinutes),
        )
    }

    /** Long sleep latency — the earliest actionable insomnia signal available without a clinician. */
    private fun longLatencyRule(c: Context): Recommendation? {
        val latency = c.lastNight?.sleepLatencyMinutes ?: return null
        if (latency < 30) return null
        return Recommendation(
            id = "latency",
            category = Category.SCREENS,
            icon = "bedDouble",
            colorToken = "rose",
            priority = 60 + latency / 5,
            titleArgs = listOf(latency),
            evidenceArgs = listOf(latency),
        )
    }

    /** Fragmentation, the reason a "full" night can leave someone wrecked. */
    private fun fragmentedNightRule(c: Context): Recommendation? {
        val night = c.lastNight ?: return null
        if (night.interruptions < 4) return null
        return Recommendation(
            id = "fragmented",
            category = Category.HYDRATION,
            icon = "activity",
            colorToken = "cyan",
            priority = 55 + night.interruptions * 3,
            titleArgs = listOf(night.interruptions),
            evidenceArgs = listOf(night.interruptions, night.asleepMinutes),
        )
    }

    /** Deep sleep is where physical repair happens — and it is exercise-responsive. */
    private fun lowDeepSleepRule(c: Context): Recommendation? {
        val night = c.lastNight ?: return null
        if (!night.hasStaging || night.asleepMinutes <= 0) return null
        val share = night.deepMinutes.toDouble() / night.asleepMinutes
        if (share >= 0.13) return null
        return Recommendation(
            id = "deep-sleep",
            category = Category.EXERCISE,
            icon = "dumbbell",
            colorToken = "green",
            priority = 50 + ((0.13 - share) * 200).roundToInt(),
            evidenceArgs = listOf((share * 100).roundToInt()),
        )
    }

    /** REM is memory and emotional processing, and it is the stage alcohol and late caffeine hit hardest. */
    private fun lowRemRule(c: Context): Recommendation? {
        val night = c.lastNight ?: return null
        if (!night.hasStaging || night.asleepMinutes <= 0) return null
        val share = night.remMinutes.toDouble() / night.asleepMinutes
        if (share >= 0.18) return null
        return Recommendation(
            id = "rem",
            category = Category.CAFFEINE,
            icon = "coffee",
            colorToken = "gold",
            priority = 48 + ((0.18 - share) * 200).roundToInt(),
            evidenceArgs = listOf((share * 100).roundToInt()),
        )
    }

    /** Physiology says back off — the one recommendation that tells the user to do less. */
    private fun lowRecoveryRule(c: Context): Recommendation? {
        if (!c.recovery.hasBaseline || c.recovery.value >= 40) return null
        return Recommendation(
            id = "low-recovery",
            category = Category.BREATHING,
            icon = "heart",
            colorToken = "rose",
            priority = 95 - c.recovery.value,
            actionRoute = "sleep:routine",
            evidenceArgs = listOf(c.recovery.value),
        )
    }

    /** Bedtime creeping later, caught before it becomes the new normal. */
    private fun lateBedtimeDriftRule(c: Context): Recommendation? {
        if (!c.baseline.isReliable) return null
        val drift = SleepTime.circularDeltaMinutes(
            c.goals.targetBedtimeMinutes,
            c.baseline.averageBedtimeMinutes,
        )
        if (drift < 45) return null
        return Recommendation(
            id = "bedtime-drift",
            category = Category.SCHEDULE,
            icon = "moonStars",
            colorToken = "purple",
            priority = 45 + drift / 10,
            actionRoute = "sleep:goals",
            titleArgs = listOf(drift),
            bodyArgs = listOf(c.baseline.averageBedtimeMinutes, c.goals.targetBedtimeMinutes),
            evidenceArgs = listOf(c.baseline.averageBedtimeMinutes),
        )
    }

    /** Morning light is the strongest circadian lever there is, and it is free. */
    private fun morningLightRule(c: Context): Recommendation? {
        if (c.stats.nights < 5 || c.stats.consistencyScore >= 60) return null
        return Recommendation(
            id = "morning-light",
            category = Category.LIGHT,
            icon = "sunrise",
            colorToken = "gold",
            priority = 40,
            evidenceArgs = listOf(c.stats.consistencyScore),
        )
    }

    /**
     * Fajr framing — only when the user asked for it.
     *
     * Gated on [Context.islamicRoutineEnabled] so the module never assumes religious practice.
     * The advice is scheduling advice; it happens to be phrased around a fixed early wake time.
     */
    private fun fajrRoutineRule(c: Context): Recommendation? {
        if (!c.islamicRoutineEnabled || !c.goals.alignWakeWithFajr) return null
        if (!c.baseline.isReliable) return null
        val wakeTarget = c.plan.targetWakeMinutes
        val actual = c.baseline.averageWakeMinutes
        val drift = SleepTime.circularDeltaMinutes(wakeTarget, actual)
        if (drift < 30) return null
        return Recommendation(
            id = "fajr-routine",
            category = Category.SPIRITUAL,
            icon = "sunrise",
            colorToken = "teal",
            priority = 75,
            actionRoute = "sleep:routine",
            titleArgs = listOf(drift),
            bodyArgs = listOf(c.plan.recommendedBedtimeMinutes),
            evidenceArgs = listOf(actual, wakeTarget),
        )
    }

    /** A wind-down ritual, suggested only once there is a measured problem to solve. */
    private fun windDownRule(c: Context): Recommendation? {
        if (c.stats.nights < 4) return null
        if (c.stats.averageScore >= 70) return null
        val latency = c.baseline.takeIf { it.isReliable }?.let { c.lastNight?.sleepLatencyMinutes } ?: 0
        if (latency >= 30) return null // longLatencyRule owns that case and says more.
        return Recommendation(
            id = "wind-down",
            category = Category.MINDFULNESS,
            icon = "sparkles",
            colorToken = "teal",
            priority = 35,
            actionRoute = "sleep:routine",
            titleArgs = listOf(c.goals.windDownMinutes),
            evidenceArgs = listOf(c.stats.averageScore, c.stats.nights),
        )
    }

    // ─────────────────────────────────────────────────────── summaries

}
