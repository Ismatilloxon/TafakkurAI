package com.example.tafakkurai.sleep

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceUtcOffsetHours
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.sleep.health.HealthSleepBridge
import com.example.tafakkurai.sleep.health.HealthPermissionState
import kotlinx.coroutines.launch

/**
 * The one ViewModel behind every sleep screen.
 *
 * ### Why one rather than five
 *
 * The dashboard, timeline, history, statistics and routine screens are five views of a single
 * night and a single history. Splitting them across five ViewModels would mean five copies of
 * the derivation chain (sessions → baseline → score → recovery → stats) and five chances for
 * two screens to disagree about the same night, which is the specific bug users notice fastest
 * and trust least.
 *
 * ### Why derived state is computed, not cached
 *
 * [SleepStore] mirrors its rows into Compose state, so reading `store.sessions` inside a
 * computed property makes that property automatically recompose when anything writes. The
 * scoring pass is a few hundred arithmetic operations over at most a year of nights — cheaper
 * than the invalidation bookkeeping a cache would need, and impossible to leave stale.
 *
 * The one exception is [stats], which is memoised on its inputs: it scores the whole window
 * per call, and the statistics screen reads it from several composables at once.
 */
class SleepViewModel(
    private val store: SleepStore,
    private val activity: SleepActivityLog,
    private val health: HealthSleepBridge,
    private val fajrMinutesProvider: () -> Int? = { null },
    private val now: () -> Long = ::epochMillis,
    private val todayKey: () -> String = ::deviceDateKey,
    private val offsetHours: () -> Double = ::deviceUtcOffsetHours,
) : ViewModel() {

    // ── raw state ────────────────────────────────────────────────────────────

    val settings: SleepSettings get() = store.settings
    val goals: SleepGoals get() = store.settings.goals

    var selectedRange by mutableStateOf(StatsRange.WEEK)
        private set

    /** Session the detail sheet is showing; null when it is closed. */
    var selectedSessionId by mutableStateOf<String?>(null)
        private set

    var permissionState by mutableStateOf(HealthPermissionState.UNKNOWN)
        private set

    var syncError by mutableStateOf<String?>(null)
        private set

    val isImporting: Boolean get() = store.importing

    /** True before the first night exists — drives the empty state rather than a zeroed one. */
    val isEmpty: Boolean get() = store.nights().none { it.isScorable }

    // ── derived: last night ──────────────────────────────────────────────────

    val lastNight: SleepSession? get() = store.latestNight()

    val baseline: SleepBaseline
        get() = SleepStatistics.baselineOf(store.sessions, todayKey(), offsetHours = offsetHours())

    val sleepScore: SleepScore
        get() = lastNight?.let { SleepScoring.score(it, goals, baseline, offsetHours()) } ?: SleepScore.NONE

    val recoveryScore: RecoveryScore
        get() = lastNight?.let {
            SleepScoring.recovery(it, sleepScore, baseline, sleepDebtMinutes)
        } ?: RecoveryScore.NONE

    val sleepDebtMinutes: Int
        get() = SleepStatistics.sleepDebt(store.sessions, todayKey(), goals.sleepNeedMinutes)

    val socialJetlagMinutes: Int
        get() = SleepStatistics.socialJetlagMinutes(store.sessions, todayKey(), offsetHours = offsetHours())

    // ── derived: statistics, memoised ────────────────────────────────────────

    private var statsCache: Pair<StatsKey, SleepStats>? = null

    private data class StatsKey(val range: StatsRange, val sessions: Int, val day: String, val revision: Long)

    val stats: SleepStats
        get() {
            // `revision` folds in the newest updatedAt, so an EDIT to an existing session
            // invalidates the cache even though the session count has not changed.
            val key = StatsKey(
                range = selectedRange,
                sessions = store.sessions.size,
                day = todayKey(),
                revision = store.sessions.maxOfOrNull { it.updatedAt } ?: 0L,
            )
            statsCache?.let { (cached, value) -> if (cached == key) return value }
            val computed = SleepStatistics.stats(store.sessions, todayKey(), selectedRange, goals, offsetHours())
            statsCache = key to computed
            return computed
        }

    // ── derived: tonight ─────────────────────────────────────────────────────

    val bedtimePlan: SmartBedtime.BedtimePlan
        get() = SmartBedtime.plan(
            goals = goals,
            baseline = baseline,
            fajrMinutes = fajrMinutesProvider(),
            observedLatencyMinutes = baseline.takeIf { it.isReliable }?.let { lastNight?.sleepLatencyMinutes },
        )

    val nowMinutes: Int get() = SleepTime.signedMinuteOfDay(now(), offsetHours())

    val minutesUntilBedtime: Int get() = SmartBedtime.minutesUntilBedtime(nowMinutes, bedtimePlan)

    val isWindingDown: Boolean get() = SmartBedtime.isWindingDown(nowMinutes, bedtimePlan)

    /** Cycle-aligned wake options for the "if you sleep now" card. */
    val wakeOptionsNow: List<SmartBedtime.WakeOption>
        get() = SmartBedtime.wakeOptionsIfSleepingNow(nowMinutes, goals)

    // ── derived: coach ───────────────────────────────────────────────────────

    val recommendations: List<SleepCoach.Recommendation>
        get() = SleepCoach.recommend(
            SleepCoach.Context(
                lastNight = lastNight,
                lastScore = sleepScore,
                recovery = recoveryScore,
                stats = stats,
                baseline = baseline,
                goals = goals,
                plan = bedtimePlan,
                socialJetlagMinutes = socialJetlagMinutes,
                islamicRoutineEnabled = settings.islamicRoutineEnabled,
            ),
        )

    // ── derived: routine ─────────────────────────────────────────────────────

    fun routine(phase: RoutinePhase): RoutineProgress = store.routine(phase, todayKey())

    val routineStreak: Int
        get() = com.example.tafakkurai.spiritual.streakLength(
            store.routineCompletedDays(RoutinePhase.BEDTIME),
            todayKey(),
        )

    // ── derived: timeline & history ──────────────────────────────────────────

    val todaysNaps: List<SleepSession> get() = store.naps(todayKey())

    fun history(limit: Int = 60): List<SleepSession> = store.nights().take(limit)

    fun sessionsOn(dateKey: String): List<SleepSession> = store.onDate(dateKey)

    val selectedSession: SleepSession? get() = selectedSessionId?.let(store::byId)

    fun scoreOf(session: SleepSession): SleepScore =
        SleepScoring.score(session, goals, baseline, offsetHours())

    /** Calendar heatmap source: the last [days] scored, gaps included. */
    fun heatmap(days: Int = 91): List<SleepDayPoint> =
        SleepStatistics.series(store.sessions, todayKey(), days, goals, baseline, offsetHours())

    val weekdayShape: List<Int> get() = SleepStatistics.byWeekday(stats.series)

    // ── actions ──────────────────────────────────────────────────────────────

    fun selectRange(range: StatsRange) {
        if (range == selectedRange) return
        selectedRange = range
        activity.screenViewed(SleepActivityType.STATISTICS_VIEWED, range)
    }

    fun openSession(id: String?) {
        selectedSessionId = id
        if (id != null) activity.screenViewed(SleepActivityType.TIMELINE_VIEWED)
    }

    fun onDashboardShown() {
        activity.screenViewed(SleepActivityType.RECOVERY_VIEWED)
        val shown = recommendations.map { it.id }
        if (shown.isNotEmpty()) activity.recommendationShown(shown)
    }

    fun onRecommendationTapped(recommendation: SleepCoach.Recommendation) {
        activity.recommendationActed(recommendation.id)
    }

    // ── actions · manual logging ─────────────────────────────────────────────

    /**
     * Logs a night the user entered by hand.
     *
     * Manual entries carry [SleepSource.MANUAL], which is the lowest trust rank, so a watch
     * record for the same night automatically supersedes this one on the next import rather
     * than sitting beside it as a duplicate.
     */
    fun logNight(startMs: Long, endMs: Long, note: String? = null): SleepSession? {
        if (endMs <= startMs) return null
        if ((endMs - startMs) / 60_000L > SleepSession.MAX_SESSION_MINUTES) return null
        return store.add(
            startMs = startMs,
            endMs = endMs,
            kind = SleepKind.NIGHT,
            source = SleepSource.MANUAL,
            note = note,
        )
    }

    /**
     * Logs the most recent night from user-selected wall-clock times.
     *
     * This is the manual fallback behind the empty-state action. It never invents stages,
     * vitals, or a HealthKit source: the record remains explicitly [SleepSource.MANUAL].
     */
    fun logLastNight(bedtimeMinutes: Int, wakeMinutes: Int): SleepSession? {
        val current = now()
        val offset = offsetHours()
        val localDayStart = SleepTime.startOfDayMs(todayKey(), offset) ?: return null
        var wakeAt = localDayStart + SleepTime.toMinuteOfDay(wakeMinutes) * SleepTime.MS_PER_MINUTE
        if (wakeAt > current) wakeAt -= SleepTime.MS_PER_DAY
        var bedAt = localDayStart + bedtimeMinutes * SleepTime.MS_PER_MINUTE
        if (wakeAt < localDayStart) bedAt -= SleepTime.MS_PER_DAY
        if (bedAt >= wakeAt) bedAt -= SleepTime.MS_PER_DAY
        val wakeDate = SleepTime.dateKeyOf(wakeAt, offset)
        if (store.onDate(wakeDate).any { it.kind == SleepKind.NIGHT }) return null
        return logNight(bedAt, wakeAt)
    }

    fun logNap(startMs: Long, endMs: Long): SleepSession? {
        if (endMs <= startMs) return null
        return store.add(
            startMs = startMs,
            endMs = endMs,
            kind = SleepKind.NAP,
            source = SleepSource.MANUAL,
        )
    }

    fun napVerdict(session: SleepSession): NapVerdict = SleepScoring.napVerdict(session, offsetHours())

    fun setMood(sessionId: String, mood: String?) = store.setMood(sessionId, mood)

    fun setNote(sessionId: String, note: String?) = store.setNote(sessionId, note)

    fun deleteSession(id: String) {
        if (store.delete(id) && selectedSessionId == id) selectedSessionId = null
    }

    // ── actions · routine ────────────────────────────────────────────────────

    fun toggleStep(phase: RoutinePhase, step: RoutineStep) {
        if (step.phase != phase) return
        val day = todayKey()
        val wasCompleted = store.isStepCompleted(day, step.id)
        store.setStepCompleted(day, step, !wasCompleted)
    }

    // ── actions · settings ───────────────────────────────────────────────────

    fun saveGoals(goals: SleepGoals) {
        store.saveSettings(settings.copy(goals = goals))
        activity.goalsChanged(goals)
    }

    fun setIslamicRoutine(enabled: Boolean) {
        store.saveSettings(settings.withIslamicRoutine(enabled))
    }

    fun setStepEnabled(stepId: String, enabled: Boolean) {
        store.saveSettings(settings.withStep(stepId, enabled))
    }

    fun saveSettings(next: SleepSettings) = store.saveSettings(next)

    // ── actions · health ─────────────────────────────────────────────────────

    /**
     * Requests health permissions, then imports.
     *
     * Failure is reported through [permissionState] and [syncError] rather than thrown: a
     * denied permission is a normal state the UI has a screen for, not an error condition.
     */
    fun requestHealthAccess() {
        viewModelScope.launch {
            permissionState = runCatching { health.requestPermissions() }
                .getOrElse { HealthPermissionState.DENIED }
            when (permissionState) {
                HealthPermissionState.GRANTED -> {
                    activity.record(SleepActivityType.HEALTH_PERMISSION_GRANTED)
                    syncHealth(historical = true)
                }
                HealthPermissionState.DENIED ->
                    activity.record(SleepActivityType.HEALTH_PERMISSION_DENIED)
                else -> Unit
            }
        }
    }

    fun refreshPermissionState() {
        viewModelScope.launch {
            permissionState = runCatching { health.permissionState() }
                .getOrDefault(HealthPermissionState.UNKNOWN)
        }
    }

    /**
     * Pulls sleep from the platform health store.
     *
     * @param historical true for the first-run backfill (a year), false for the incremental
     *  pass run on every foreground. The incremental window deliberately overlaps the last
     *  few days rather than starting where the previous pass stopped: health stores revise
     *  recent sessions after the fact — Apple Watch commonly rewrites last night's staging
     *  the following morning — and a strictly forward-only cursor would never see the revision.
     */
    fun syncHealth(historical: Boolean = false) {
        if (!settings.healthSyncEnabled || store.importing) return
        viewModelScope.launch {
            val started = now()
            store.setImporting(true)
            syncError = null
            activity.healthSyncStarted(health.platformName)

            val outcome = runCatching {
                val from = if (historical) {
                    started - HISTORICAL_WINDOW_DAYS * SleepTime.MS_PER_DAY
                } else {
                    started - INCREMENTAL_OVERLAP_DAYS * SleepTime.MS_PER_DAY
                }
                val sessions = health.readSessions(fromMs = from, toMs = started)
                    .filter { settings.napTrackingEnabled || it.kind != SleepKind.NAP }
                store.importFromHealth(sessions)
            }

            val imported = outcome.getOrDefault(0)
            syncError = outcome.exceptionOrNull()?.message

            store.setImporting(false)
            activity.healthSyncFinished(
                platform = health.platformName,
                imported = imported,
                durationMs = now() - started,
                error = syncError,
            )
        }
    }

    private companion object {
        /** First-run backfill window. A year is enough for every trend the app draws. */
        const val HISTORICAL_WINDOW_DAYS = 365L

        /** Re-read window on each foreground, sized to catch after-the-fact revisions. */
        const val INCREMENTAL_OVERLAP_DAYS = 4L
    }
}
