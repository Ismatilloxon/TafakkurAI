package com.example.tafakkurai.sleep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.formatJournalDate
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.screens.HPAD
import com.example.tafakkurai.screens.InsetCard
import com.example.tafakkurai.screens.ScrollBody
import com.example.tafakkurai.screens.StatTile
import com.example.tafakkurai.sleep.*
import com.example.tafakkurai.sleep.health.HealthPermissionState
import com.example.tafakkurai.ui.*

/**
 * Sleep & Rest screens.
 *
 * All five read one [SleepViewModel], so they cannot disagree about a night. Each is a plain
 * composable taking the ViewModel plus navigation lambdas — the same shape every other screen
 * in this app uses, so routing stays in `App.kt` and nothing here knows what a route is.
 */

// ══════════════════════════════════════════════════════════ dashboard

/**
 * The dashboard.
 *
 * Ordered by what a half-awake person needs first: last night's score, then the timeline that
 * explains it, then tonight's plan, then advice. Statistics and history are one tap away
 * rather than on this screen — a morning dashboard that opens on a 90-day chart is answering
 * a question nobody asked at 07:00.
 */
@Composable
fun SleepScreen(
    vm: SleepViewModel,
    onBack: () -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenRoutine: (RoutinePhase) -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onOpenQuran: (String) -> Unit = {},
) {
    val c = LocalTf.current
    val night = vm.lastNight
    val use24 = vm.settings.use24HourClock
    val settingsDescription = tr("sleep.a11y.open_settings")

    LaunchedEffect(Unit) {
        vm.refreshPermissionState()
        vm.onDashboardShown()
        vm.syncHealth(historical = false)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(
            title = tr("sleep.title"),
            onBack = onBack,
            right = {
                Box(
                    Modifier
                        .semantics { contentDescription = settingsDescription; role = Role.Button }
                        .then(pressScale(onOpenGoals, 0.9f))
                        .padding(6.dp),
                ) {
                    TfIcon("settings", size = 19, color = c.accent)
                }
            },
        )

        ScrollBody {
            if (vm.isImporting && vm.isEmpty) {
                SleepLoadingState()
                return@ScrollBody
            }

            vm.syncError?.let { message ->
                SleepErrorState(message) { vm.syncHealth(historical = false) }
            }

            if (vm.isEmpty) {
                SleepEmptyState(
                    healthAvailable = vm.permissionState != HealthPermissionState.UNAVAILABLE,
                    onConnectHealth = vm::requestHealthAccess,
                    onLogManually = onOpenHistory,
                )
                TonightCard(vm, use24, onOpenGoals)
                RoutineCard(vm.routine(RoutinePhase.BEDTIME), tr("sleep.routine.bedtime")) {
                    onOpenRoutine(RoutinePhase.BEDTIME)
                }
                return@ScrollBody
            }

            // ── hero ────────────────────────────────────────────────────────
            InsetCard(pad = 18) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DualScoreRing(vm.sleepScore, vm.recoveryScore, size = 156)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TfText(
                            sleepDuration(night?.asleepMinutes ?: 0),
                            color = c.fg, size = 26f, weight = FontWeight.Bold, family = Fonts.display,
                        )
                        Pill(
                            bandLabel(vm.sleepScore.band),
                            bandColor(vm.sleepScore.band),
                            bandColor(vm.sleepScore.band).soft(0.16f),
                        )
                        if (night != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SleepMetaRow(tr("sleep.meta.bedtime"), SleepTime.formatClockAt(night.inBedStartMs, use24))
                            SleepMetaRow(tr("sleep.meta.wake"), SleepTime.formatClockAt(night.inBedEndMs, use24))
                            SleepMetaRow(tr("sleep.meta.efficiency"), "${(night.efficiency * 100).toInt()}%")
                            }
                        }
                    }
                }
            }

            // A concise recovery verdict derived from the same measured inputs as the score.
            TfText(
                localizedMorningSummary(vm),
                color = c.fg2, size = 13.5f, lineHeight = 1.5f,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            // ── timeline ────────────────────────────────────────────────────
            if (night != null) {
                SectionTitle(tr("sleep.last_night"), action = tr("sleep.history.title"), onAction = onOpenHistory)
                InsetCard {
                    SleepTimeline(night, use24Hour = use24)
                    SleepTimelineLegend(showUnknown = !night.hasStaging)
                    if (!night.hasStaging) {
                        TfText(
                            tr("sleep.stage.not_reported"),
                            color = c.fg3, size = 11.5f, lineHeight = 1.4f,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                // ── stage breakdown ─────────────────────────────────────────
                if (night.hasStaging && night.asleepMinutes > 0) {
                    Column(
                        Modifier.padding(horizontal = HPAD.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StageCard(SleepStage.DEEP, night.deepMinutes, night.deepMinutes.toDouble() / night.asleepMinutes, 13..23)
                        StageCard(SleepStage.REM, night.remMinutes, night.remMinutes.toDouble() / night.asleepMinutes, 20..25)
                        StageCard(SleepStage.CORE, night.coreMinutes, night.coreMinutes.toDouble() / night.asleepMinutes, null)
                        if (night.awakeMinutes > 0) {
                            StageCard(SleepStage.AWAKE, night.awakeMinutes, night.awakeMinutes.toDouble() / night.asleepMinutes, null)
                        }
                    }
                }

                // ── vitals ──────────────────────────────────────────────────
                if (!night.vitals.isEmpty) {
                    Overline(tr("sleep.overnight"), color = c.fg3, modifier = Modifier.padding(horizontal = 20.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        night.vitals.restingHeartRate?.let {
                            StatTile("${it.toInt()}", tr("sleep.vitals.resting_hr"), c.rose)
                        }
                        night.vitals.hrvMs?.let {
                            StatTile(tr("sleep.vitals.hrv_value", it.toInt()), tr("sleep.vitals.hrv"), c.cyan)
                        }
                        night.vitals.respirationRate?.let {
                            StatTile("${(it * 10).toInt() / 10.0}", tr("sleep.vitals.breaths_min"), c.teal)
                        }
                    }
                }
            }

            // ── tonight ─────────────────────────────────────────────────────
            TonightCard(vm, use24, onOpenGoals)

            // ── routines ────────────────────────────────────────────────────
            RoutineCard(vm.routine(RoutinePhase.BEDTIME), tr("sleep.routine.bedtime")) {
                onOpenRoutine(RoutinePhase.BEDTIME)
            }
            RoutineCard(vm.routine(RoutinePhase.MORNING), tr("sleep.routine.morning")) {
                onOpenRoutine(RoutinePhase.MORNING)
            }

            // ── coach ───────────────────────────────────────────────────────
            val recommendations = vm.recommendations
            if (recommendations.isNotEmpty()) {
                SectionTitle(tr("sleep.for_you"))
                Column(
                    Modifier.padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    recommendations.forEach { recommendation ->
                        RecommendationCard(
                            recommendation,
                            // Named: `modifier` is the LAST parameter, so a trailing lambda
                            // binds to it rather than to onAction.
                            onAction = {
                                vm.onRecommendationTapped(recommendation)
                                when (recommendation.actionRoute) {
                                    "sleep:goals" -> onOpenGoals()
                                    "sleep:routine" -> onOpenRoutine(RoutinePhase.BEDTIME)
                                }
                            },
                        )
                    }
                }
            }

            // ── week at a glance ────────────────────────────────────────────
            SectionTitle(tr("sleep.this_week"), action = tr("sleep.statistics.title"), onAction = onOpenStats)
            InsetCard {
                SleepDurationChart(vm.stats.series, vm.goals.sleepNeedMinutes)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf(
                        tr("sleep.weekday.mon"), tr("sleep.weekday.tue"), tr("sleep.weekday.wed"),
                        tr("sleep.weekday.thu"), tr("sleep.weekday.fri"), tr("sleep.weekday.sat"), tr("sleep.weekday.sun"),
                    ).forEach {
                        TfText(it, color = c.fg3, size = 10f, family = Fonts.mono)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile(sleepDuration(vm.stats.averageAsleepMinutes), tr("sleep.average"), c.teal)
                StatTile("${vm.stats.consistencyScore}", tr("sleep.consistency"), c.cyan)
                StatTile(sleepDuration(vm.sleepDebtMinutes), tr("sleep.sleep_debt"), c.warn)
            }

            // ── naps ────────────────────────────────────────────────────────
            val naps = vm.todaysNaps
            if (naps.isNotEmpty()) {
                SectionTitle(tr("sleep.naps_today"))
                Column(
                    Modifier.padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    naps.forEach { nap -> NapRow(nap, vm.napVerdict(nap), use24) }
                }
            }

            PrivacyNote()
        }
    }
}

/** Tonight's plan: recommended bedtime, wind-down, and the countdown. */
@Composable
private fun ColumnScope.TonightCard(vm: SleepViewModel, use24: Boolean, onOpenGoals: () -> Unit) {
    val c = LocalTf.current
    val plan = vm.bedtimePlan
    val until = vm.minutesUntilBedtime

    SectionTitle(tr("sleep.tonight"), action = tr("sleep.adjust"), onAction = onOpenGoals)
    InsetCard(pad = 16) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(c.purple.soft(0.16f)),
                contentAlignment = Alignment.Center,
            ) { TfIcon("moonStars", size = 22, color = c.purple) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TfText(
                    tr("sleep.plan.bed_at", SleepTime.formatClock(plan.recommendedBedtimeMinutes, use24)),
                    color = c.fg, size = 16f, weight = FontWeight.SemiBold,
                )
                TfText(
                    tr(
                        "sleep.plan.up_at",
                        SleepTime.formatClock(plan.targetWakeMinutes, use24),
                        plan.cycles,
                        sleepDuration(plan.expectedSleepMinutes),
                    ),
                    color = c.fg2, size = 12.5f, family = Fonts.mono,
                )
            }
            if (until in 1..720) {
                Column(horizontalAlignment = Alignment.End) {
                    TfText(tr("sleep.plan.in"), color = c.fg3, size = 10f)
                    TfText(sleepDuration(until), color = c.accent, size = 14f, weight = FontWeight.Bold, family = Fonts.mono)
                }
            }
        }
        TfText(
            localizedPlanRationale(vm, use24),
            color = c.fg3, size = 12f, lineHeight = 1.5f,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (plan.alignedWithFajr) {
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TfIcon("sunrise", size = 13, color = c.teal)
                TfText(tr("sleep.plan.aligned_fajr"), color = c.teal, size = 11.5f, weight = FontWeight.Medium)
            }
        }
    }

    // "If you sleep now" — cycle-aligned options, the Sleep Cycle idea.
    if (vm.isWindingDown || until <= 0) {
        InsetCard(pad = 14) {
            Overline(tr("sleep.plan.if_sleep_now"))
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.wakeOptionsNow.take(4).forEach { option ->
                    val tint = if (option.recommended) c.accent else c.fg2
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (option.recommended) c.accent.soft(0.16f) else c.card3)
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        TfText(SleepTime.formatClock(option.wakeMinutes, use24), color = tint, size = 13.5f, weight = FontWeight.SemiBold, family = Fonts.mono)
                        TfText(tr("sleep.plan.cycles", option.cycles), color = c.fg3, size = 10f)
                    }
                }
            }
        }
    }
}

@Composable
private fun NapRow(nap: SleepSession, verdict: NapVerdict, use24: Boolean) {
    val c = LocalTf.current
    val (label, tint) = when (verdict) {
        NapVerdict.OPTIMAL -> tr("sleep.nap.ideal") to c.green
        NapVerdict.FULL_CYCLE -> tr("sleep.nap.full_cycle") to c.teal
        NapVerdict.TOO_LONG -> tr("sleep.nap.too_long") to c.warn
        NapVerdict.TOO_LATE -> tr("sleep.nap.too_late") to c.warn
        NapVerdict.SHORT -> tr("sleep.nap.short") to c.fg3
    }
    Card(Modifier.fillMaxWidth(), pad = 13) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge("bedDouble", tint, size = 38, iconSize = 18)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TfText(sleepDuration(nap.asleepMinutes), color = c.fg, size = 14.5f, weight = FontWeight.SemiBold)
                TfText(
                    "${SleepTime.formatClockAt(nap.startMs, use24)} – ${SleepTime.formatClockAt(nap.endMs, use24)}",
                    color = c.fg3, size = 12f, family = Fonts.mono,
                )
            }
            Pill(label, tint, tint.soft(0.16f))
        }
    }
}

@Composable
private fun ColumnScope.PrivacyNote() {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TfIcon("lock", size = 13, color = c.fg3)
        TfText(
            tr("sleep.privacy"),
            color = c.fg3, size = 11.5f, lineHeight = 1.4f,
        )
    }
}

// ══════════════════════════════════════════════════════════ routine

/**
 * A routine, bedtime or morning.
 *
 * The Islamic steps appear here only when the user enabled them, in the same rows as
 * everything else. The offer to enable them lives at the bottom as one line — visible enough
 * to find, quiet enough that a user who does not want it is never asked twice.
 */
@Composable
fun SleepRoutineScreen(
    vm: SleepViewModel,
    phase: RoutinePhase,
    onBack: () -> Unit,
    onOpenQuran: (String) -> Unit = {},
) {
    val c = LocalTf.current
    val progress = vm.routine(phase)
    val title = if (phase == RoutinePhase.BEDTIME) tr("sleep.routine.bedtime") else tr("sleep.routine.morning")

    Column(Modifier.fillMaxSize()) {
        BackBar(title, onBack)
        ScrollBody {
            InsetCard(pad = 18) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SleepRing(
                        score = (progress.fraction * 100).toInt(),
                        band = if (progress.isComplete) ScoreBand.EXCELLENT else ScoreBand.FAIR,
                        size = 96,
                        strokeWidth = 8f,
                        caption = tr("sleep.routine.done"),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        TfText(
                            if (progress.isComplete) tr("sleep.routine.complete") else tr("sleep.routine.count", progress.completedCount, progress.totalCount),
                            color = c.fg, size = 20f, weight = FontWeight.Bold, family = Fonts.display,
                        )
                        TfText(
                            if (progress.isComplete) {
                                tr("sleep.routine.all_done")
                            } else {
                                tr("sleep.routine.minutes_left", progress.remainingSeconds / 60)
                            },
                            color = c.fg2, size = 13f, lineHeight = 1.45f,
                        )
                        if (phase == RoutinePhase.BEDTIME && vm.routineStreak > 0) {
                            Pill(tr("sleep.routine.day_streak", vm.routineStreak), c.gold, c.gold.soft(0.16f))
                        }
                    }
                }
            }

            if (progress.enabledSteps.isEmpty()) {
                InsetCard(pad = 18) {
                    TfText(
                        tr("sleep.routine.none_enabled_body"),
                        color = c.fg2, size = 13.5f, lineHeight = 1.5f, align = TextAlign.Center,
                    )
                }
            }

            Column(
                Modifier.padding(horizontal = HPAD.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                progress.enabledSteps.forEach { state ->
                    RoutineStepRow(
                        state = state,
                        onToggle = { vm.toggleStep(phase, state.step) },
                        onOpenQuran = onOpenQuran,
                    )
                }
            }

            // ── the opt-in, stated once and plainly ─────────────────────────
            SectionTitle(tr("sleep.routine.customise"))
            InsetCard(pad = 16) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        TfText(tr("sleep.routine.islamic"), color = c.fg, size = 14.5f, weight = FontWeight.SemiBold)
                        TfText(
                            tr("sleep.routine.islamic_body"),
                            color = c.fg2, size = 12.5f, lineHeight = 1.45f,
                        )
                    }
                    SleepToggle(vm.settings.islamicRoutineEnabled) { vm.setIslamicRoutine(it) }
                }
            }

            // Per-step enable/disable for everything visible in this phase.
            Column(
                Modifier.padding(horizontal = HPAD.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.settings.visibleSteps(phase).forEach { step ->
                    Card(Modifier.fillMaxWidth(), pad = 13) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TfIcon(step.icon, size = 17, color = c.named(step.colorToken))
                            TfText(tr("sleep.routine.step.${step.id}.title"), color = c.fg2, size = 13.5f, modifier = Modifier.weight(1f))
                            SleepToggle(vm.settings.isEnabled(step.id)) { vm.setStepEnabled(step.id, it) }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════ statistics

/** Trends, consistency and the heatmap. */
@Composable
fun SleepStatsScreen(vm: SleepViewModel, onBack: () -> Unit) {
    val c = LocalTf.current
    val strings = LocalStrings.current
    val todayKey = deviceDateKey()
    val stats = vm.stats
    val use24 = vm.settings.use24HourClock

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("sleep.statistics.title"), onBack)
        ScrollBody {
            Box(Modifier.padding(horizontal = HPAD.dp)) {
                Segmented(
                    tabs = StatsRange.entries.map { it.name to tr("sleep.range.${it.name.lowercase()}") },
                    value = vm.selectedRange.name,
                    onChange = { vm.selectRange(StatsRange.valueOf(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (stats.nights == 0) {
                InsetCard(pad = 20) {
                    TfText(
                        tr("sleep.statistics.empty"),
                        color = c.fg2, size = 13.5f, align = TextAlign.Center,
                    )
                }
                return@ScrollBody
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatisticCard(
                    tr("sleep.statistics.avg_sleep"),
                    sleepDuration(stats.averageAsleepMinutes),
                    tr("sleep.statistics.nights", stats.nights),
                    c.teal,
                    stats.trend,
                    Modifier.weight(1f),
                )
                StatisticCard(
                    tr("sleep.statistics.avg_score"),
                    "${stats.averageScore}",
                    bandLabel(ScoreBand.of(stats.averageScore)),
                    bandColor(ScoreBand.of(stats.averageScore)),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatisticCard(
                    tr("sleep.statistics.bedtime"),
                    SleepTime.formatClock(stats.averageBedtimeMinutes, use24),
                    tr("sleep.average"),
                    c.purple,
                    modifier = Modifier.weight(1f),
                )
                StatisticCard(
                    tr("sleep.statistics.wake"),
                    SleepTime.formatClock(stats.averageWakeMinutes, use24),
                    tr("sleep.average"),
                    c.gold,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatisticCard(tr("sleep.statistics.deep"), "${stats.deepSharePercent}%", tr("sleep.statistics.of_total"), c.purple, modifier = Modifier.weight(1f))
                StatisticCard(tr("sleep.statistics.rem"), "${stats.remSharePercent}%", tr("sleep.statistics.of_total"), c.cyan, modifier = Modifier.weight(1f))
                StatisticCard(tr("sleep.statistics.efficiency"), "${(stats.averageEfficiency * 100).toInt()}%", tr("sleep.statistics.asleep_in_bed"), c.green, modifier = Modifier.weight(1f))
            }

            SectionTitle(tr("sleep.statistics.duration"))
            InsetCard {
                SleepDurationChart(stats.series, vm.goals.sleepNeedMinutes, height = 120)
            }

            SectionTitle(tr("sleep.consistency"))
            InsetCard {
                BedtimeConsistencyChart(stats.series)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TfText(tr("sleep.statistics.consistency_score", stats.consistencyScore), color = c.fg2, size = 12f, family = Fonts.mono)
                    if (vm.socialJetlagMinutes >= 60) {
                        TfText(
                            tr("sleep.statistics.social_jetlag", sleepDuration(vm.socialJetlagMinutes)),
                            color = c.warn, size = 12f, family = Fonts.mono,
                        )
                    }
                }
            }

            SectionTitle(tr("sleep.statistics.last_13_weeks"))
            InsetCard {
                SleepCalendarHeatmap(vm.heatmap(91))
                TfText(
                    tr("sleep.statistics.heatmap_help"),
                    color = c.fg3, size = 11.5f,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            SectionTitle(tr("sleep.statistics.streaks_extremes"))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile("${stats.currentStreak}", tr("sleep.statistics.current_streak"), c.gold)
                StatTile("${stats.longestStreak}", tr("sleep.statistics.longest_streak"), c.green)
            }
            stats.bestDay?.let { best ->
                InsetCard(pad = 14) {
                    SleepMetaRow(
                        tr("sleep.statistics.best_night"),
                        "${strings.formatJournalDate(best.dateKey, todayKey)} · ${best.score}",
                        c.green,
                    )
                    stats.worstDay?.let { worst ->
                        Spacer(Modifier.height(8.dp))
                        SleepMetaRow(
                            tr("sleep.statistics.hardest_night"),
                            "${strings.formatJournalDate(worst.dateKey, todayKey)} · ${worst.score}",
                            c.danger,
                        )
                    }
                }
            }

            // Score breakdown, so the headline number is auditable.
            if (vm.sleepScore.components.isNotEmpty()) {
                SectionTitle(tr("sleep.statistics.sleep_breakdown"))
                InsetCard(pad = 16) {
                    ScoreBreakdown(vm.sleepScore.components)
                }
            }
            if (vm.recoveryScore.components.isNotEmpty()) {
                SectionTitle(tr("sleep.statistics.recovery_breakdown"))
                InsetCard(pad = 16) {
                    if (!vm.recoveryScore.hasBaseline) {
                        TfText(
                            tr("sleep.statistics.baseline_learning"),
                            color = c.fg3, size = 12f, lineHeight = 1.45f,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    ScoreBreakdown(vm.recoveryScore.components)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════ history

/** Every recorded night, newest first, with a manual-entry path. */
@Composable
fun SleepHistoryScreen(
    vm: SleepViewModel,
    onBack: () -> Unit,
    onOpenSession: ((String) -> Unit)? = null,
) {
    val c = LocalTf.current
    val strings = LocalStrings.current
    val todayKey = deviceDateKey()
    val use24 = vm.settings.use24HourClock
    val history = vm.history()
    var manualBedtime by remember(vm.goals.targetBedtimeMinutes) { mutableStateOf(vm.goals.targetBedtimeMinutes) }
    var manualWake by remember(vm.goals.targetWakeMinutes) { mutableStateOf(vm.goals.targetWakeMinutes) }
    var manualMessage by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("sleep.history.title"), onBack)
        ScrollBody {
            SectionTitle(tr("sleep.manual.title"))
            InsetCard(pad = 16) {
                SleepStepper(
                    tr("sleep.meta.bedtime"),
                    SleepTime.formatClock(manualBedtime, use24),
                    onMinus = { manualBedtime -= 15 },
                    onPlus = { manualBedtime += 15 },
                )
                Spacer(Modifier.height(12.dp))
                SleepStepper(
                    tr("sleep.meta.wake"),
                    SleepTime.formatClock(manualWake, use24),
                    onMinus = { manualWake -= 15 },
                    onPlus = { manualWake += 15 },
                )
                Spacer(Modifier.height(14.dp))
                SleepPrimaryButton(tr("sleep.manual.save_last_night"), {
                    manualMessage = if (vm.logLastNight(manualBedtime, manualWake) != null) {
                        "sleep.manual.saved"
                    } else {
                        "sleep.manual.not_saved"
                    }
                })
                manualMessage?.let { key ->
                    TfText(tr(key), color = if (key == "sleep.manual.saved") c.green else c.warn, size = 12f, modifier = Modifier.padding(top = 10.dp))
                }
                TfText(tr("sleep.manual.provenance"), color = c.fg3, size = 11.5f, lineHeight = 1.4f, modifier = Modifier.padding(top = 10.dp))
            }

            if (history.isEmpty()) {
                InsetCard(pad = 20) {
                    TfText(tr("sleep.history.empty"), color = c.fg2, size = 13.5f, align = TextAlign.Center)
                }
                return@ScrollBody
            }

            Column(
                Modifier.padding(horizontal = HPAD.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                history.forEach { session ->
                    val score = vm.scoreOf(session)
                    Card(
                        Modifier.fillMaxWidth(),
                        pad = 14,
                        onClick = onOpenSession?.let { open -> { open(session.id) } },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            SleepRing(score.value, score.band, size = 52, strokeWidth = 5f, caption = "")
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                TfText(
                                    strings.formatJournalDate(session.dateKey, todayKey),
                                    color = c.fg,
                                    size = 14.5f,
                                    weight = FontWeight.SemiBold,
                                )
                                TfText(
                                    "${SleepTime.formatClockAt(session.inBedStartMs, use24)} – ${SleepTime.formatClockAt(session.inBedEndMs, use24)} · ${sleepDuration(session.asleepMinutes)}",
                                    color = c.fg2, size = 12f, family = Fonts.mono,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill(tr("sleep.source.${session.source.token}"), c.fg3, c.card3)
                                    if (session.hasStaging) Pill(tr("sleep.stage.staged"), c.teal, c.teal.soft(0.14f))
                                }
                            }
                            if (onOpenSession != null) {
                                TfIcon("chevronRight", size = 17, color = c.fg3)
                            }
                        }
                        SleepTimeline(session, height = 56, use24Hour = use24, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════ goals & settings

/** Goals, routine preferences and integrations. */
@Composable
fun SleepGoalsScreen(vm: SleepViewModel, onBack: () -> Unit) {
    val c = LocalTf.current
    var goals by remember(vm.goals) { mutableStateOf(vm.goals) }
    val settings = vm.settings

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("sleep.settings.title"), onBack)
        ScrollBody {
            SectionTitle(tr("sleep.settings.target"))
            InsetCard(pad = 16) {
                SleepStepper(
                    tr("sleep.settings.sleep_need"),
                    sleepDuration(goals.sleepNeedMinutes),
                    onMinus = { goals = goals.copy(sleepNeedMinutes = (goals.sleepNeedMinutes - 15).coerceAtLeast(SleepGoals.MIN_SLEEP_NEED_MINUTES)) },
                    onPlus = { goals = goals.copy(sleepNeedMinutes = (goals.sleepNeedMinutes + 15).coerceAtMost(SleepGoals.MAX_SLEEP_NEED_MINUTES)) },
                )
                Spacer(Modifier.height(12.dp))
                SleepStepper(
                    tr("sleep.settings.target_bedtime"),
                    SleepTime.formatClock(goals.targetBedtimeMinutes, settings.use24HourClock),
                    onMinus = { goals = goals.copy(targetBedtimeMinutes = goals.targetBedtimeMinutes - 15) },
                    onPlus = { goals = goals.copy(targetBedtimeMinutes = goals.targetBedtimeMinutes + 15) },
                )
                Spacer(Modifier.height(12.dp))
                SleepStepper(
                    tr("sleep.settings.target_wake"),
                    SleepTime.formatClock(goals.targetWakeMinutes, settings.use24HourClock),
                    onMinus = { goals = goals.copy(targetWakeMinutes = goals.targetWakeMinutes - 15) },
                    onPlus = { goals = goals.copy(targetWakeMinutes = goals.targetWakeMinutes + 15) },
                )
                Spacer(Modifier.height(12.dp))
                SleepStepper(
                    tr("sleep.settings.wind_down"),
                    tr("sleep.duration.minutes", goals.windDownMinutes),
                    onMinus = { goals = goals.copy(windDownMinutes = (goals.windDownMinutes - 5).coerceAtLeast(0)) },
                    onPlus = { goals = goals.copy(windDownMinutes = (goals.windDownMinutes + 5).coerceAtMost(180)) },
                )
                Spacer(Modifier.height(14.dp))
                SleepPrimaryButton(tr("sleep.settings.save_target"), { vm.saveGoals(goals) }, enabled = goals != vm.goals)
            }

            SectionTitle(tr("sleep.settings.prayer"))
            InsetCard(pad = 16) {
                SettingRow(
                    tr("sleep.settings.align_fajr"),
                    tr("sleep.settings.align_fajr_body"),
                    goals.alignWakeWithFajr,
                ) { goals = goals.copy(alignWakeWithFajr = it); vm.saveGoals(goals) }
            }

            SectionTitle(tr("sleep.settings.health"))
            InsetCard(pad = 16) {
                SettingRow(tr("sleep.settings.sync_automatically"), tr("sleep.settings.sync_automatically_body"), settings.healthSyncEnabled) {
                    vm.saveSettings(settings.copy(healthSyncEnabled = it))
                }
                SettingRow(tr("sleep.settings.track_naps"), tr("sleep.settings.track_naps_body"), settings.napTrackingEnabled) {
                    vm.saveSettings(settings.copy(napTrackingEnabled = it))
                }
                Spacer(Modifier.height(12.dp))
                when (vm.permissionState) {
                    HealthPermissionState.GRANTED ->
                        SleepMetaRow(tr("sleep.health.access"), tr("sleep.health.granted"), c.green)
                    HealthPermissionState.PARTIAL ->
                        SleepMetaRow(tr("sleep.health.access"), tr("sleep.health.sleep_only"), c.warn)
                    HealthPermissionState.UNAVAILABLE ->
                        SleepMetaRow(tr("sleep.health.access"), tr("sleep.health.unavailable"), c.fg3)
                    else -> SleepPrimaryButton(tr("sleep.health.connect"), vm::requestHealthAccess)
                }
            }

            SectionTitle(tr("sleep.settings.display"))
            InsetCard(pad = 16) {
                SettingRow(tr("sleep.settings.clock_24"), tr("sleep.settings.clock_24_body"), settings.use24HourClock) {
                    vm.saveSettings(settings.copy(use24HourClock = it))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════ small controls

@Composable
private fun SettingRow(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TfText(title, color = c.fg, size = 14f, weight = FontWeight.Medium)
            TfText(detail, color = c.fg3, size = 11.5f, lineHeight = 1.4f)
        }
        SleepToggle(value, onChange)
    }
}

/** Platform-neutral switch, drawn from theme tokens so it works in all seven palettes. */
@Composable
fun SleepToggle(value: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalTf.current
    val state = tr(if (value) "sleep.a11y.on" else "sleep.a11y.off")
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (value) c.accent else c.card3)
            .semantics { role = Role.Switch; stateDescription = state }
            .then(pressScale({ onChange(!value) }, 0.94f)),
        contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun SleepStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = LocalTf.current
    val decrease = tr("sleep.a11y.decrease", label)
    val increase = tr("sleep.a11y.increase", label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TfText(label, color = c.fg, size = 14.5f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c.card3)
                    .semantics { contentDescription = decrease; role = Role.Button }
                    .then(pressScale(onMinus, 0.9f)),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(width = 11.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(c.fg2)) }
            TfText(
                value, color = c.fg, size = 14.5f, family = Fonts.mono, align = TextAlign.Center,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c.card3)
                    .semantics { contentDescription = increase; role = Role.Button }
                    .then(pressScale(onPlus, 0.9f)),
                contentAlignment = Alignment.Center,
            ) { TfIcon("plus", size = 15, color = c.fg2) }
        }
    }
}

@Composable
private fun localizedMorningSummary(vm: SleepViewModel): String {
    val night = vm.lastNight ?: return tr("sleep.summary.none")
    val quality = tr("sleep.summary.quality.${vm.sleepScore.band.name.lowercase()}")
    val first = tr("sleep.summary.sleep", sleepDuration(night.asleepMinutes), quality, vm.sleepScore.value)
    if (!vm.recoveryScore.hasBaseline) return first
    val recovery = tr(
        "sleep.summary.recovery.${vm.recoveryScore.band.name.lowercase()}",
        vm.recoveryScore.value,
    )
    return tr("sleep.summary.combined", first, recovery)
}

@Composable
private fun localizedPlanRationale(vm: SleepViewModel, use24: Boolean): String {
    val plan = vm.bedtimePlan
    return tr(
        if (plan.alignedWithFajr) "sleep.plan.rationale_fajr" else "sleep.plan.rationale",
        SleepTime.formatClock(plan.targetWakeMinutes, use24),
        sleepDuration(plan.expectedSleepMinutes),
        plan.cycles,
    )
}
