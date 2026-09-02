package com.example.tafakkurai.sleep

import com.example.tafakkurai.spiritual.dayNumberOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TODAY = "2026-08-07"

/** Builds [count] consecutive nights ending on the day before [TODAY]. */
private fun nights(
    count: Int,
    asleepMinutes: Int = 480,
    bedMinutes: Int = -60,
    latency: Int = 0,
    deepMinutes: Int = 0,
    remMinutes: Int = 0,
    source: SleepSource = SleepSource.WATCH,
    bedJitter: (Int) -> Int = { 0 },
): List<SleepSession> {
    val today = dayNumberOf(TODAY)!!
    return (1..count).map { back ->
        val dateKey = SleepTime.dateKeyOfDayNumber(today - back)
        val bed = bedMinutes + bedJitter(back)
        Fx.night(
            dateKey = dateKey,
            bedMinutes = bed,
            wakeMinutes = bed + latency + asleepMinutes,
            latencyMinutes = latency,
            deepMinutes = deepMinutes,
            remMinutes = remMinutes,
            source = source,
            id = "n-$back",
        )
    }
}

class SleepStatisticsTest {

    // ── baseline ─────────────────────────────────────────────────────────────

    @Test
    fun `baseline needs a week of nights before it is reliable`() {
        val thin = SleepStatistics.baselineOf(nights(4), TODAY, offsetHours = Fx.UTC)
        assertFalse(thin.isReliable)

        val solid = SleepStatistics.baselineOf(nights(10), TODAY, offsetHours = Fx.UTC)
        assertTrue(solid.isReliable)
        assertEquals(10, solid.nights)
        assertEquals(480, solid.averageAsleepMinutes)
        assertEquals(-60, solid.averageBedtimeMinutes)
    }

    @Test
    fun `baseline ignores naps`() {
        val today = dayNumberOf(TODAY)!!
        val withNap = nights(10) + Fx.night(
            dateKey = SleepTime.dateKeyOfDayNumber(today - 1),
            bedMinutes = 780, wakeMinutes = 805, latencyMinutes = 0,
            kind = SleepKind.NAP, id = "nap-1",
        )
        val baseline = SleepStatistics.baselineOf(withNap, TODAY, offsetHours = Fx.UTC)
        // A 25-minute nap must not drag the average night length down.
        assertEquals(480, baseline.averageAsleepMinutes)
        assertEquals(10, baseline.nights)
    }

    @Test
    fun `baseline averages vitals only where present`() {
        val today = dayNumberOf(TODAY)!!
        val sessions = (1..8).map { back ->
            Fx.night(
                dateKey = SleepTime.dateKeyOfDayNumber(today - back),
                id = "v-$back",
                vitals = if (back <= 4) SleepVitals(hrvMs = 50.0) else SleepVitals.NONE,
            )
        }
        val baseline = SleepStatistics.baselineOf(sessions, TODAY, offsetHours = Fx.UTC)
        assertEquals(50.0, baseline.hrvMs!!, 0.001)
        // Nothing reported resting heart rate, so there is no baseline for it — not a zero.
        assertTrue(baseline.restingHeartRate == null)
    }

    // ── debt ─────────────────────────────────────────────────────────────────

    @Test
    fun `meeting the target leaves no debt`() {
        assertEquals(0, SleepStatistics.sleepDebt(nights(14, asleepMinutes = 480), TODAY, 480))
    }

    @Test
    fun `two hours short each night accumulates`() {
        val debt = SleepStatistics.sleepDebt(nights(14, asleepMinutes = 360), TODAY, 480)
        assertEquals(14 * 120, debt)
    }

    @Test
    fun `untracked nights count as a full deficit`() {
        // A user who stops tracking during a bad week must not watch their debt fall to zero.
        val debt = SleepStatistics.sleepDebt(emptyList(), TODAY, 480, days = 7)
        assertEquals(7 * 480, debt)
    }

    @Test
    fun `surplus repays debt at only half rate`() {
        // Six short nights (−120 each) then one long one (+120). Full cancelling would give
        // 600; half-rate repayment gives 720 − 60 = 660.
        val today = dayNumberOf(TODAY)!!
        val sessions = (1..7).map { back ->
            val minutes = if (back == 1) 600 else 360
            val bed = -60
            Fx.night(
                dateKey = SleepTime.dateKeyOfDayNumber(today - back),
                bedMinutes = bed, wakeMinutes = bed + minutes, latencyMinutes = 0,
                id = "d-$back",
            )
        }
        assertEquals(660, SleepStatistics.sleepDebt(sessions, TODAY, 480, days = 7))
    }

    // ── consistency ──────────────────────────────────────────────────────────

    @Test
    fun `a fixed schedule scores near perfect consistency`() {
        val score = SleepStatistics.consistencyScore(nights(14), TODAY, offsetHours = Fx.UTC)
        assertTrue(score >= 95, "got $score")
    }

    @Test
    fun `bedtimes either side of midnight are still consistent`() {
        // 23:50 / 00:10 alternating. The whole point of the circular statistics: this user is
        // extremely regular and must not be told otherwise.
        val sessions = nights(14, bedMinutes = -10, bedJitter = { back -> if (back % 2 == 0) 20 else 0 })
        val score = SleepStatistics.consistencyScore(sessions, TODAY, offsetHours = Fx.UTC)
        assertTrue(score >= 85, "expected a high score for a 20-minute spread, got $score")
    }

    @Test
    fun `a chaotic schedule scores low`() {
        val sessions = nights(14, bedJitter = { back -> (back * 97) % 400 - 200 })
        val score = SleepStatistics.consistencyScore(sessions, TODAY, offsetHours = Fx.UTC)
        assertTrue(score < 70, "got $score")
    }

    @Test
    fun `consistency needs at least three nights`() {
        assertEquals(0, SleepStatistics.consistencyScore(nights(2), TODAY, offsetHours = Fx.UTC))
    }

    // ── series ───────────────────────────────────────────────────────────────

    @Test
    fun `series fills gaps rather than closing over them`() {
        val today = dayNumberOf(TODAY)!!
        val sparse = listOf(
            Fx.night(dateKey = SleepTime.dateKeyOfDayNumber(today - 1), id = "a"),
            Fx.night(dateKey = SleepTime.dateKeyOfDayNumber(today - 5), id = "b"),
        )
        val series = SleepStatistics.series(sparse, TODAY, 7, offsetHours = Fx.UTC)

        assertEquals(7, series.size)
        assertEquals(2, series.count { it.hasSession })
        assertEquals(5, series.count { !it.hasSession })
        // Oldest first, ending today.
        assertEquals(TODAY, series.last().dateKey)
    }

    @Test
    fun `series takes the longest night when a day has more than one`() {
        val today = dayNumberOf(TODAY)!!
        val key = SleepTime.dateKeyOfDayNumber(today - 1)
        val duplicated = listOf(
            Fx.night(dateKey = key, bedMinutes = -60, wakeMinutes = 300, latencyMinutes = 0, id = "short"),
            Fx.night(dateKey = key, bedMinutes = -60, wakeMinutes = 420, latencyMinutes = 0, id = "long"),
        )
        val series = SleepStatistics.series(duplicated, TODAY, 3, offsetHours = Fx.UTC)
        val day = series.first { it.dateKey == key }
        assertEquals(480, day.asleepMinutes)
    }

    // ── roll-up ──────────────────────────────────────────────────────────────

    @Test
    fun `stats roll a clean week up correctly`() {
        val stats = SleepStatistics.stats(
            nights(7, deepMinutes = 80, remMinutes = 100),
            TODAY, StatsRange.WEEK, SleepGoals(), Fx.UTC,
        )
        assertEquals(7, stats.nights)
        assertEquals(480, stats.averageAsleepMinutes)
        assertEquals(-60, stats.averageBedtimeMinutes)
        assertEquals(17, stats.deepSharePercent)  // 80 / 480
        assertEquals(21, stats.remSharePercent)   // 100 / 480
        assertNotNull(stats.bestDay)
        assertNotNull(stats.worstDay)
    }

    @Test
    fun `an empty history rolls up to EMPTY rather than to zeros with a fake trend`() {
        assertEquals(SleepStats.EMPTY, SleepStatistics.stats(emptyList(), TODAY, StatsRange.WEEK, SleepGoals(), Fx.UTC))
    }

    @Test
    fun `streaks count nights that met the goal — not nights that were logged`() {
        // Tracked but far too short: a streak of these would be a vanity metric.
        val stats = SleepStatistics.stats(
            nights(7, asleepMinutes = 300),
            TODAY, StatsRange.WEEK, SleepGoals(), Fx.UTC,
        )
        assertEquals(0, stats.currentStreak)
    }

    @Test
    fun `longest streak finds the longest run anywhere in history`() {
        val keys = setOf(
            "2026-01-01", "2026-01-02", "2026-01-03",   // 3
            "2026-02-01",                                // 1
            "2026-03-01", "2026-03-02", "2026-03-03", "2026-03-04", "2026-03-05", // 5
        )
        assertEquals(5, SleepStatistics.longestStreak(keys))
    }

    @Test
    fun `longest streak of nothing is zero`() {
        assertEquals(0, SleepStatistics.longestStreak(emptySet()))
    }

    // ── trend ────────────────────────────────────────────────────────────────

    @Test
    fun `trend has a dead band so the arrow does not flicker on noise`() {
        val flat = (0 until 8).map { SleepDayPoint("d$it", 480, 80, -60, 420, true) }
        assertEquals(TrendDirection.STABLE, SleepStatistics.trendOf(flat))

        val rising = (0 until 8).map { SleepDayPoint("d$it", 480, 50 + it * 6, -60, 420, true) }
        assertEquals(TrendDirection.IMPROVING, SleepStatistics.trendOf(rising))

        val falling = (0 until 8).map { SleepDayPoint("d$it", 480, 90 - it * 6, -60, 420, true) }
        assertEquals(TrendDirection.DECLINING, SleepStatistics.trendOf(falling))
    }

    // ── social jetlag ────────────────────────────────────────────────────────

    @Test
    fun `social jetlag detects a weekend shift`() {
        val today = dayNumberOf(TODAY)!!
        val sessions = (1..28).map { back ->
            val day = today - back
            val weekend = (((day + 3) % 7) + 7) % 7 >= 5
            val bed = if (weekend) 60 else -60   // 01:00 at weekends, 23:00 on weekdays
            Fx.night(
                dateKey = SleepTime.dateKeyOfDayNumber(day),
                bedMinutes = bed, wakeMinutes = bed + 480, latencyMinutes = 0,
                id = "j-$back",
            )
        }
        val jetlag = SleepStatistics.socialJetlagMinutes(sessions, TODAY, offsetHours = Fx.UTC)
        assertTrue(jetlag in 100..140, "expected about two hours, got $jetlag")
    }

    @Test
    fun `a uniform schedule has no social jetlag`() {
        assertEquals(0, SleepStatistics.socialJetlagMinutes(nights(28), TODAY, offsetHours = Fx.UTC))
    }
}

// ═══════════════════════════════════════════════════════════ smart bedtime

class SmartBedtimeTest {

    @Test
    fun `plan works backwards from the wake time`() {
        val plan = SmartBedtime.plan(SleepGoals(), SleepBaseline.EMPTY, observedLatencyMinutes = 15)
        assertEquals(420, plan.targetWakeMinutes)
        // 07:00 − 8h − 15m latency = 22:45.
        assertEquals(-75, plan.recommendedBedtimeMinutes)
        assertEquals(480, plan.expectedSleepMinutes)
    }

    @Test
    fun `wind-down starts before the recommended bedtime`() {
        val plan = SmartBedtime.plan(SleepGoals(windDownMinutes = 45), observedLatencyMinutes = 15)
        assertEquals(plan.recommendedBedtimeMinutes - 45, plan.windDownStartMinutes)
    }

    @Test
    fun `a target already on a cycle boundary is left alone`() {
        val plan = SmartBedtime.plan(SleepGoals(sleepNeedMinutes = 450), observedLatencyMinutes = 15)
        assertEquals(450, plan.expectedSleepMinutes)
        assertEquals(5, plan.cycles)
    }

    @Test
    fun `cycle alignment never costs more than the cap`() {
        // Rounding 8h to 5 cycles would cost 30 minutes of sleep, over the 25-minute cap, so
        // duration wins and the plan keeps the full eight hours.
        val plan = SmartBedtime.plan(SleepGoals(sleepNeedMinutes = 480), observedLatencyMinutes = 15)
        assertEquals(480, plan.expectedSleepMinutes)
    }

    @Test
    fun `fajr alignment leaves room for wudu`() {
        val goals = SleepGoals(alignWakeWithFajr = true, fajrLeadMinutes = 25)
        val plan = SmartBedtime.plan(goals, fajrMinutes = 300) // Fajr 05:00
        assertTrue(plan.alignedWithFajr)
        assertEquals(275, plan.targetWakeMinutes) // 04:35
    }

    @Test
    fun `fajr alignment is ignored when the setting is off`() {
        val plan = SmartBedtime.plan(SleepGoals(alignWakeWithFajr = false), fajrMinutes = 300)
        assertFalse(plan.alignedWithFajr)
        assertEquals(420, plan.targetWakeMinutes)
    }

    @Test
    fun `every sleep-now option is a whole number of cycles`() {
        val options = SmartBedtime.wakeOptionsIfSleepingNow(-60, SleepGoals(), observedLatencyMinutes = 15)
        assertTrue(options.isNotEmpty())
        assertTrue(options.all { it.sleepMinutes % SmartBedtime.CYCLE_MINUTES == 0 })
        assertEquals(1, options.count { it.recommended })
    }

    @Test
    fun `the recommended option is the shortest that still clears the target`() {
        val options = SmartBedtime.wakeOptionsIfSleepingNow(-60, SleepGoals(sleepNeedMinutes = 480), observedLatencyMinutes = 15)
        val recommended = options.first { it.recommended }
        assertTrue(recommended.sleepMinutes >= 480)
        assertTrue(options.none { it.sleepMinutes in 480 until recommended.sleepMinutes })
    }

    @Test
    fun `cycle rounding never proposes fewer than three cycles`() {
        assertEquals(270, SmartBedtime.nearestCycleDuration(60))
    }

    @Test
    fun `bedtime countdown is signed and takes the short way round`() {
        val plan = SmartBedtime.plan(SleepGoals(), observedLatencyMinutes = 15) // bedtime 22:45
        assertEquals(45, SmartBedtime.minutesUntilBedtime(-120, plan))  // 22:00 → 45 min to go
        assertTrue(SmartBedtime.minutesUntilBedtime(-30, plan) < 0)     // 23:30 → overdue
    }

    @Test
    fun `wind-down window is detected`() {
        val plan = SmartBedtime.plan(SleepGoals(windDownMinutes = 45), observedLatencyMinutes = 15)
        assertTrue(SmartBedtime.isWindingDown(-100, plan))   // 22:20, inside
        assertFalse(SmartBedtime.isWindingDown(-200, plan))  // 20:40, too early
        assertFalse(SmartBedtime.isWindingDown(-30, plan))   // 23:30, past bedtime
    }
}

// ═══════════════════════════════════════════════════════════ import merge

class SleepMergeTest {

    @Test
    fun `a known external id updates the existing row rather than duplicating it`() {
        val stored = Fx.night(id = "stored", externalId = "hk-1")
        val incoming = Fx.night(id = "fresh", externalId = "hk-1")

        val outcome = SleepMerge.resolve(incoming, existingByExternalId = stored, overlapping = emptyList())
        val insert = outcome as SleepMerge.Outcome.Insert
        assertEquals("stored", insert.session.id)
    }

    @Test
    fun `a watch record supersedes an overlapping phone estimate`() {
        val estimate = Fx.night(id = "phone", source = SleepSource.AUTOMATIC)
        val measured = Fx.night(id = "watch", source = SleepSource.WATCH, deepMinutes = 80, remMinutes = 100)

        val outcome = SleepMerge.resolve(measured, null, listOf(estimate))
        val replace = outcome as SleepMerge.Outcome.Replace
        assertEquals("phone", replace.replacedId)
        assertEquals("watch", replace.session.id)
    }

    @Test
    fun `a phone estimate never overwrites a watch record`() {
        // The regression that silently degrades a multi-device history to phone estimates.
        val measured = Fx.night(id = "watch", source = SleepSource.WATCH, deepMinutes = 80, remMinutes = 100)
        val estimate = Fx.night(id = "phone", source = SleepSource.AUTOMATIC)

        assertEquals(SleepMerge.Outcome.Skip, SleepMerge.resolve(estimate, null, listOf(measured)))
    }

    @Test
    fun `a non-overlapping session is simply inserted`() {
        val yesterday = Fx.night(dateKey = "2026-08-06", id = "a")
        val today = Fx.night(dateKey = "2026-08-07", id = "b")
        val outcome = SleepMerge.resolve(today, null, emptyList())
        assertTrue(outcome is SleepMerge.Outcome.Insert)
        assertTrue(yesterday.id != today.id)
    }

    @Test
    fun `between equal sources the more detailed record wins`() {
        val plain = Fx.night(id = "plain", source = SleepSource.WATCH)
        val detailed = Fx.night(id = "detailed", source = SleepSource.WATCH, deepMinutes = 80, remMinutes = 100)

        val outcome = SleepMerge.resolve(detailed, null, listOf(plain))
        assertTrue(outcome is SleepMerge.Outcome.Replace)
        assertEquals(SleepMerge.Outcome.Skip, SleepMerge.resolve(plain, null, listOf(detailed)))
    }

    @Test
    fun `strength ranks source above every other signal`() {
        // A bare watch record must still outrank a manual entry carrying staging and vitals,
        // or the ordering stops meaning "this was measured".
        val watch = Fx.night(id = "w", source = SleepSource.WATCH)
        val manual = Fx.night(
            id = "m", source = SleepSource.MANUAL,
            deepMinutes = 80, remMinutes = 100,
            vitals = SleepVitals(hrvMs = 50.0),
        )
        assertTrue(SleepMerge.strength(watch) > SleepMerge.strength(manual))
    }
}

// ═══════════════════════════════════════════════════════════ routine

class SleepRoutineTest {

    @Test
    fun `a fresh install has no religious content enabled`() {
        val settings = SleepSettings()
        assertFalse(settings.islamicRoutineEnabled)
        assertTrue(settings.visibleSteps(RoutinePhase.BEDTIME).none { it.optionalFaith })
        assertTrue(settings.visibleSteps(RoutinePhase.MORNING).none { it.optionalFaith })
    }

    @Test
    fun `a fresh install still gets a complete secular routine`() {
        val settings = SleepSettings()
        val bedtime = settings.visibleSteps(RoutinePhase.BEDTIME)
        assertTrue(bedtime.size >= 4, "expected a usable routine, got ${bedtime.size} steps")
        assertTrue(bedtime.all { settings.isEnabled(it.id) })
    }

    @Test
    fun `opting in reveals the islamic steps`() {
        val settings = SleepSettings().withIslamicRoutine(true)
        assertTrue(settings.islamicRoutineEnabled)
        val faith = settings.visibleSteps(RoutinePhase.BEDTIME).filter { it.optionalFaith }
        assertTrue(faith.isNotEmpty())
        assertTrue(faith.all { settings.isEnabled(it.id) })
    }

    @Test
    fun `toggling the islamic routine twice preserves other choices`() {
        // Turning it on and off again must not quietly reset decisions about other steps.
        val customised = SleepSettings().withStep("bed.breathing", false)
        val roundTripped = customised.withIslamicRoutine(true).withIslamicRoutine(false)

        assertFalse(roundTripped.isEnabled("bed.breathing"))
        assertEquals(customised.enabledStepIds, roundTripped.enabledStepIds)
    }

    @Test
    fun `every catalogue step id is unique`() {
        // Ids are the primary key of the completion record; a duplicate would silently merge
        // two steps' histories.
        val ids = SleepRoutineCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every faith step is marked optional and every default step is not`() {
        assertTrue(SleepRoutineCatalog.faithStepIds.all { SleepRoutineCatalog.byId(it)!!.optionalFaith })
        assertTrue(SleepRoutineCatalog.defaultEnabledIds.none { SleepRoutineCatalog.byId(it)!!.optionalFaith })
    }

    @Test
    fun `routine progress reports completion honestly`() {
        val steps = SleepRoutineCatalog.forPhase(RoutinePhase.BEDTIME).take(3)
        val progress = RoutineProgress(
            phase = RoutinePhase.BEDTIME,
            dateKey = TODAY,
            steps = steps.mapIndexed { index, step ->
                RoutineStepState(step, enabled = true, completed = index < 2)
            },
        )
        assertEquals(2, progress.completedCount)
        assertEquals(3, progress.totalCount)
        assertFalse(progress.isComplete)
        assertEquals(steps[2].estimatedSeconds, progress.remainingSeconds)
    }

    @Test
    fun `disabled steps do not count toward completion`() {
        val steps = SleepRoutineCatalog.forPhase(RoutinePhase.BEDTIME).take(3)
        val progress = RoutineProgress(
            phase = RoutinePhase.BEDTIME,
            dateKey = TODAY,
            steps = listOf(
                RoutineStepState(steps[0], enabled = true, completed = true),
                RoutineStepState(steps[1], enabled = false, completed = false),
                RoutineStepState(steps[2], enabled = false, completed = false),
            ),
        )
        assertEquals(1, progress.totalCount)
        assertTrue(progress.isComplete)
    }
}
