package com.example.tafakkurai.sleep

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test fixtures. Every time is expressed against UTC so no test depends on a device clock. */
internal object Fx {

    const val UTC = 0.0

    /**
     * Builds a night.
     *
     * @param bedMinutes night-centred signed: −60 is 23:00 the previous evening.
     * @param wakeMinutes minutes after midnight of [dateKey].
     */
    fun night(
        dateKey: String = "2026-08-07",
        bedMinutes: Int = -60,
        wakeMinutes: Int = 420,
        latencyMinutes: Int = 10,
        deepMinutes: Int = 0,
        remMinutes: Int = 0,
        awakeMinutes: Int = 0,
        vitals: SleepVitals = SleepVitals.NONE,
        source: SleepSource = SleepSource.WATCH,
        kind: SleepKind = SleepKind.NIGHT,
        id: String = "n-$dateKey",
        externalId: String? = null,
    ): SleepSession {
        val midnight = SleepTime.startOfDayMs(dateKey, UTC)!!
        val inBedStart = midnight + bedMinutes * SleepTime.MS_PER_MINUTE
        val inBedEnd = midnight + wakeMinutes * SleepTime.MS_PER_MINUTE
        val start = inBedStart + latencyMinutes * SleepTime.MS_PER_MINUTE

        val totalMinutes = ((inBedEnd - start) / SleepTime.MS_PER_MINUTE).toInt()
        val segments = if (deepMinutes > 0 || remMinutes > 0 || awakeMinutes > 0) {
            buildSegments(start, totalMinutes, deepMinutes, remMinutes, awakeMinutes)
        } else {
            emptyList()
        }

        return SleepSession(
            id = id,
            kind = kind,
            source = source,
            externalId = externalId,
            startMs = start,
            endMs = inBedEnd,
            inBedStartMs = inBedStart,
            inBedEndMs = inBedEnd,
            segments = segments,
            vitals = vitals,
            dateKey = dateKey,
        )
    }

    /** Lays stages end to end across the asleep window; the remainder becomes Core. */
    private fun buildSegments(
        startMs: Long,
        totalMinutes: Int,
        deep: Int,
        rem: Int,
        awake: Int,
    ): List<SleepSegment> {
        val core = (totalMinutes - deep - rem - awake).coerceAtLeast(0)
        val plan = listOf(
            SleepStage.DEEP to deep,
            SleepStage.REM to rem,
            SleepStage.AWAKE to awake,
            SleepStage.CORE to core,
        ).filter { it.second > 0 }

        var cursor = startMs
        return plan.map { (stage, minutes) ->
            val end = cursor + minutes * SleepTime.MS_PER_MINUTE
            SleepSegment(stage, cursor, end).also { cursor = end }
        }
    }

    /** A baseline reliable enough for the physiological recovery terms to engage. */
    fun baseline(
        hrv: Double? = 60.0,
        rhr: Double? = 55.0,
        respiration: Double? = 14.0,
        nights: Int = 20,
    ) = SleepBaseline(
        nights = nights,
        averageAsleepMinutes = 450,
        averageBedtimeMinutes = -60,
        averageWakeMinutes = 420,
        averageEfficiency = 0.9,
        averageDeepShare = 0.18,
        averageRemShare = 0.22,
        hrvMs = hrv,
        restingHeartRate = rhr,
        respirationRate = respiration,
    )
}

class SleepScoringTest {

    // ── component curves ─────────────────────────────────────────────────────

    @Test
    fun `duration hits 100 at target and degrades below it`() {
        assertEquals(100, SleepScoring.durationScore(480, 480))
        assertEquals(78, SleepScoring.durationScore(420, 480))
        assertEquals(56, SleepScoring.durationScore(360, 480))
        assertEquals(0, SleepScoring.durationScore(0, 480))
    }

    @Test
    fun `oversleeping is not punished the way undersleeping is`() {
        // A recovery lie-in must not be scored as a bad night — that is how a sleep app
        // teaches people to under-sleep.
        assertEquals(100, SleepScoring.durationScore(560, 480)) // +17%
        assertTrue(SleepScoring.durationScore(600, 480) >= 90)  // +25%, mild
        assertTrue(SleepScoring.durationScore(360, 480) < 60)   // −25%, real cost
    }

    @Test
    fun `efficiency uses the clinical thresholds`() {
        assertEquals(100, SleepScoring.efficiencyScore(0.95))
        assertEquals(100, SleepScoring.efficiencyScore(0.92))
        assertEquals(76, SleepScoring.efficiencyScore(0.85))
        assertEquals(0, SleepScoring.efficiencyScore(0.50))
    }

    @Test
    fun `deep sleep scores full inside the healthy band`() {
        assertEquals(100, SleepScoring.deepScore(0.13))
        assertEquals(100, SleepScoring.deepScore(0.18))
        assertEquals(100, SleepScoring.deepScore(0.23))
        assertEquals(65, SleepScoring.deepScore(0.08))
    }

    @Test
    fun `excess deep sleep is treated as rebound — not as a fault`() {
        // Slow-wave rebound after a deficit is the body repaying a debt correctly.
        assertTrue(SleepScoring.deepScore(0.35) >= 80)
    }

    @Test
    fun `rem scores full inside the healthy band`() {
        assertEquals(100, SleepScoring.remScore(0.20))
        assertEquals(100, SleepScoring.remScore(0.25))
        assertEquals(50, SleepScoring.remScore(0.10))
        assertTrue(SleepScoring.remScore(0.32) >= 85)
    }

    @Test
    fun `the first waking is free`() {
        assertEquals(100, SleepScoring.restfulnessScore(0, 10))
        assertEquals(100, SleepScoring.restfulnessScore(1, 10))
        assertEquals(82, SleepScoring.restfulnessScore(4, 10))
    }

    @Test
    fun `latency is free up to twenty minutes`() {
        assertEquals(100, SleepScoring.restfulnessScore(1, 20))
        assertEquals(64, SleepScoring.restfulnessScore(1, 50))
    }

    @Test
    fun `timing tolerates twenty minutes of drift`() {
        assertEquals(100, SleepScoring.timingScore(0))
        assertEquals(100, SleepScoring.timingScore(-20))
        assertEquals(64, SleepScoring.timingScore(80))
    }

    // ── the whole score ──────────────────────────────────────────────────────

    @Test
    fun `a textbook night scores excellent`() {
        val session = Fx.night(
            bedMinutes = -60, wakeMinutes = 420, latencyMinutes = 10,
            deepMinutes = 80, remMinutes = 100,
        )
        val score = SleepScoring.score(session, SleepGoals(), Fx.baseline(), Fx.UTC)
        assertTrue(score.value >= 85, "expected excellent, got ${score.value}")
        assertEquals(ScoreBand.EXCELLENT, score.band)
        assertTrue(score.hasFullData)
    }

    @Test
    fun `a short broken night scores poor`() {
        val session = Fx.night(
            bedMinutes = 60, wakeMinutes = 330, latencyMinutes = 45,
            deepMinutes = 15, remMinutes = 20, awakeMinutes = 40,
        )
        val score = SleepScoring.score(session, SleepGoals(), Fx.baseline(), Fx.UTC)
        assertTrue(score.value < 60, "expected a low score, got ${score.value}")
    }

    /**
     * The regression this module was designed around: a phone-only user must not be
     * permanently marked down for hardware they do not own.
     */
    @Test
    fun `a night with no staging is scored fairly — not penalised`() {
        val staged = Fx.night(deepMinutes = 80, remMinutes = 100)
        val unstaged = Fx.night(deepMinutes = 0, remMinutes = 0)

        val stagedScore = SleepScoring.score(staged, SleepGoals(), Fx.baseline(), Fx.UTC)
        val unstagedScore = SleepScoring.score(unstaged, SleepGoals(), Fx.baseline(), Fx.UTC)

        assertFalse(unstagedScore.hasFullData)
        assertTrue(
            unstagedScore.value >= stagedScore.value - 5,
            "unstaged ${unstagedScore.value} should be close to staged ${stagedScore.value}",
        )
        assertTrue(unstagedScore.value >= 85, "got ${unstagedScore.value}")
    }

    @Test
    fun `missing components are reweighted so the published weights still total one`() {
        val unstaged = Fx.night(deepMinutes = 0, remMinutes = 0)
        val score = SleepScoring.score(unstaged, SleepGoals(), Fx.baseline(), Fx.UTC)

        assertTrue(score.components.none { it.key == "deep" })
        assertTrue(score.components.none { it.key == "rem" })
        val total = score.components.sumOf { it.weight }
        assertEquals(1.0, total, 0.0001)
    }

    @Test
    fun `every component carries an explanation`() {
        val score = SleepScoring.score(Fx.night(deepMinutes = 80, remMinutes = 100), SleepGoals(), Fx.baseline(), Fx.UTC)
        assertTrue(score.components.isNotEmpty())
        assertTrue(score.components.all { it.detail.isNotBlank() })
        assertTrue(score.components.all { it.value in 0..100 })
    }

    @Test
    fun `a session too short to be a night is not scored`() {
        val catnap = Fx.night(bedMinutes = 60, wakeMinutes = 150, latencyMinutes = 0)
        assertEquals(SleepScore.NONE, SleepScoring.score(catnap, SleepGoals(), Fx.baseline(), Fx.UTC))
    }

    @Test
    fun `efficiency is omitted when the source never reported time in bed`() {
        val night = Fx.night(latencyMinutes = 0)
        // inBed == asleep exactly, so the source told us nothing about restlessness.
        val score = SleepScoring.score(night, SleepGoals(), Fx.baseline(), Fx.UTC)
        assertTrue(score.components.none { it.key == "efficiency" })
    }

    // ── recovery ─────────────────────────────────────────────────────────────

    @Test
    fun `recovery is relative to the personal baseline — not an absolute threshold`() {
        val night = Fx.night(deepMinutes = 80, remMinutes = 100, vitals = SleepVitals(hrvMs = 60.0, restingHeartRate = 55.0))
        val sleepScore = SleepScoring.score(night, SleepGoals(), Fx.baseline(), Fx.UTC)

        // The same 60 ms HRV is excellent against a 60 ms baseline...
        val strong = SleepScoring.recovery(night, sleepScore, Fx.baseline(hrv = 60.0), 0)
        // ...and a warning against an 85 ms one.
        val weak = SleepScoring.recovery(night, sleepScore, Fx.baseline(hrv = 85.0), 0)

        assertTrue(strong.value > weak.value + 10, "strong=${strong.value} weak=${weak.value}")
    }

    @Test
    fun `recovery without a baseline reports that it has none`() {
        val night = Fx.night(vitals = SleepVitals(hrvMs = 60.0))
        val sleepScore = SleepScoring.score(night, SleepGoals(), SleepBaseline.EMPTY, Fx.UTC)
        val recovery = SleepScoring.recovery(night, sleepScore, SleepBaseline.EMPTY, 0)

        assertFalse(recovery.hasBaseline)
        assertTrue(recovery.components.none { it.key == "hrv" })
        // Still a usable number, built from sleep quality and debt.
        assertTrue(recovery.value > 0)
    }

    @Test
    fun `an elevated resting heart rate lowers recovery`() {
        val calm = Fx.night(deepMinutes = 80, remMinutes = 100, vitals = SleepVitals(hrvMs = 60.0, restingHeartRate = 55.0))
        val stressed = Fx.night(deepMinutes = 80, remMinutes = 100, vitals = SleepVitals(hrvMs = 60.0, restingHeartRate = 63.0))
        val score = SleepScoring.score(calm, SleepGoals(), Fx.baseline(), Fx.UTC)

        val calmRecovery = SleepScoring.recovery(calm, score, Fx.baseline(), 0)
        val stressedRecovery = SleepScoring.recovery(stressed, score, Fx.baseline(), 0)
        assertTrue(stressedRecovery.value < calmRecovery.value)
    }

    @Test
    fun `sleep debt drags recovery down`() {
        val night = Fx.night(deepMinutes = 80, remMinutes = 100, vitals = SleepVitals(hrvMs = 60.0, restingHeartRate = 55.0))
        val score = SleepScoring.score(night, SleepGoals(), Fx.baseline(), Fx.UTC)

        val rested = SleepScoring.recovery(night, score, Fx.baseline(), sleepDebtMinutes = 0)
        val indebted = SleepScoring.recovery(night, score, Fx.baseline(), sleepDebtMinutes = 600)
        assertTrue(indebted.value < rested.value)
    }

    @Test
    fun `debt score decays with hours owed`() {
        assertEquals(100, SleepScoring.debtScore(0))
        assertEquals(100, SleepScoring.debtScore(15))
        assertEquals(60, SleepScoring.debtScore(300))
        assertEquals(20, SleepScoring.debtScore(600))
    }

    // ── naps ─────────────────────────────────────────────────────────────────

    @Test
    fun `a short early nap is optimal and a long one is not`() {
        val optimal = Fx.night(bedMinutes = 780, wakeMinutes = 805, latencyMinutes = 0, kind = SleepKind.NAP)
        assertEquals(NapVerdict.OPTIMAL, SleepScoring.napVerdict(optimal, Fx.UTC))

        val tooLong = Fx.night(bedMinutes = 780, wakeMinutes = 840, latencyMinutes = 0, kind = SleepKind.NAP)
        assertEquals(NapVerdict.TOO_LONG, SleepScoring.napVerdict(tooLong, Fx.UTC))
    }

    @Test
    fun `a late nap is flagged whatever its length`() {
        val late = Fx.night(bedMinutes = 1020, wakeMinutes = 1045, latencyMinutes = 0, kind = SleepKind.NAP)
        assertEquals(NapVerdict.TOO_LATE, SleepScoring.napVerdict(late, Fx.UTC))
    }

    @Test
    fun `a full cycle nap is recognised`() {
        val cycle = Fx.night(bedMinutes = 780, wakeMinutes = 870, latencyMinutes = 0, kind = SleepKind.NAP)
        assertEquals(NapVerdict.FULL_CYCLE, SleepScoring.napVerdict(cycle, Fx.UTC))
    }
}
