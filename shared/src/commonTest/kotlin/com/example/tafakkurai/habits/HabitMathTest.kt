package com.example.tafakkurai.habits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The habit maths, and its agreement with the backend.
 *
 * These are deliberately the *same fixtures* as `backend/internal/habits/domain_test.go`.
 * The phone derives streaks locally so a screen never waits on a request, and the server
 * derives them again for the coach and the Telegram bot; a streak that reads 41 here and
 * 39 there is worse than either being wrong alone, because the user stops trusting both.
 * If one of these ever has to change, the Go test with the same name changes with it.
 */
class HabitMathTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun habit(
        createdKey: String,
        frequency: HabitFrequency = HabitFrequency.DAILY,
        state: HabitLifecycle = HabitLifecycle.ACTIVE,
        id: String = "h",
        goalType: HabitGoalType = HabitGoalType.CHECK,
        goalTarget: Int = 1,
    ) = Habit(
        id = id,
        name = "Test",
        frequency = frequency,
        state = state,
        goalType = goalType,
        goalTarget = goalTarget,
        createdAt = (dayNumberOfKey(createdKey) ?: 0L) * 86_400_000L,
    )

    private fun entries(vararg pairs: Pair<String, HabitStatus>): Map<String, HabitEntry> =
        pairs.associate { (date, status) ->
            date to HabitEntry(habitEntryId("h", date), "h", date, status)
        }

    private fun done(vararg dates: String): Map<String, HabitEntry> =
        entries(*dates.map { it to HabitStatus.DONE }.toTypedArray())

    private fun doneOn(habitId: String, date: String) =
        HabitEntry(habitEntryId(habitId, date), habitId, date, HabitStatus.DONE)

    private fun index(vararg rows: HabitEntry): Map<String, Map<String, HabitEntry>> =
        rows.groupBy { it.habitId }.mapValues { (_, list) -> list.associateBy { it.dateKey } }

    // ── date arithmetic ──────────────────────────────────────────────────────

    @Test
    fun dateKeyRoundTripsThroughDayNumber() {
        listOf("1970-01-01", "2026-08-07", "2026-01-01", "2024-02-29", "2099-12-31").forEach { key ->
            val n = dayNumberOfKey(key)
            assertTrue(n != null, "failed to parse $key")
            assertEquals(key, dateKeyOf(n), "round trip broke for $key")
        }
    }

    @Test
    fun weekdayIndexIsMondayFirst() {
        // 2026-08-03 is a Monday, 2026-08-09 a Sunday. The whole module's week starts on
        // Monday; getting this wrong silently rotates every weekday chart by a day.
        assertEquals(0, weekdayIndexOfKey("2026-08-03"))
        assertEquals(4, weekdayIndexOfKey("2026-08-07"))
        assertEquals(6, weekdayIndexOfKey("2026-08-09"))
    }

    @Test
    fun entryIdIsDerivedFromHabitAndDay() {
        // The idempotency contract. If this ever stops being deterministic, a retried
        // completion creates a second row instead of landing on the first.
        assertEquals("ha:2026-08-07", habitEntryId("ha", "2026-08-07"))
    }

    // ── streaks ──────────────────────────────────────────────────────────────

    @Test
    fun streakCountsConsecutiveDoneDays() {
        val stats = HabitMath.habitStats(
            habit("2026-08-01"),
            done("2026-08-05", "2026-08-06", "2026-08-07"),
            "2026-08-07",
        )
        assertEquals(3, stats.current)
        assertEquals(3, stats.longest)
    }

    @Test
    fun todayPendingDoesNotBreakTheStreak() {
        // The single most visible bug this module can have: the streak dropping to 0 at
        // midnight and only coming back once the user completes something.
        val stats = HabitMath.habitStats(
            habit("2026-08-05"),
            done("2026-08-05", "2026-08-06"),
            "2026-08-07",
        )
        assertEquals(2, stats.current, "yesterday's run should still stand")
        assertEquals(0, stats.missed, "today must not count as a miss")
        assertEquals(2, stats.scheduled, "today must stay out of the rate denominator")
    }

    @Test
    fun skipKeepsTheStreakAndIsNotAMiss() {
        val stats = HabitMath.habitStats(
            habit("2026-08-04"),
            entries(
                "2026-08-04" to HabitStatus.DONE,
                "2026-08-05" to HabitStatus.SKIPPED,
                "2026-08-06" to HabitStatus.DONE,
                "2026-08-07" to HabitStatus.DONE,
            ),
            "2026-08-07",
        )
        assertEquals(3, stats.current, "a skip must not break the run")
        assertEquals(0, stats.missed)
        assertEquals(1, stats.skipped)
        assertEquals(1f, stats.rate, "a skip must leave the denominator alone")
    }

    @Test
    fun missBreaksTheStreakAndCountsAgainstTheRate() {
        val stats = HabitMath.habitStats(
            habit("2026-08-02"),
            done("2026-08-02", "2026-08-03", "2026-08-06", "2026-08-07"),
            "2026-08-07",
        )
        assertEquals(2, stats.current)
        assertEquals(2, stats.longest)
        assertEquals(2, stats.missed) // 04 and 05
        assertEquals(6, stats.scheduled)
        assertEquals(4, stats.done)
    }

    @Test
    fun weekdayOnlyHabitSurvivesTheWeekend() {
        // 2026-08-07 is a Friday; 08 and 09 are the weekend, 10 is the Monday.
        val stats = HabitMath.habitStats(
            habit("2026-08-06", frequency = HabitFrequency.WEEKDAYS),
            done("2026-08-06", "2026-08-07", "2026-08-10"),
            "2026-08-10",
        )
        assertEquals(3, stats.current, "Saturday and Sunday are not scheduled")
        assertEquals(0, stats.missed, "the weekend must not count as missed")
    }

    @Test
    fun customFrequencyOnlyCountsChosenDays() {
        // Mondays and Wednesdays only.
        val h = habit("2026-08-03", frequency = HabitFrequency.CUSTOM).copy(weekdays = listOf(0, 2))
        val stats = HabitMath.habitStats(h, done("2026-08-03", "2026-08-05"), "2026-08-07")
        assertEquals(2, stats.current)
        assertEquals(0, stats.missed, "Tue/Thu/Fri were never due")
        assertEquals(2, stats.scheduled)
    }

    @Test
    fun nothingBeforeCreationCounts() {
        val stats = HabitMath.habitStats(habit("2026-08-06"), done("2026-08-06"), "2026-08-07")
        assertEquals(1, stats.scheduled)
        assertEquals(0, stats.missed)
        assertEquals(1f, stats.rate, "a one-day-old perfect habit should read 100%")
    }

    @Test
    fun pauseFreezesTheStreak() {
        val stats = HabitMath.habitStats(
            habit("2026-07-20", state = HabitLifecycle.PAUSED),
            done("2026-07-20", "2026-07-21", "2026-07-22"),
            "2026-08-07",
        )
        assertEquals(3, stats.current, "a pause must not accrue misses")
        assertEquals(0, stats.missed)
    }

    @Test
    fun partialProgressIsNotACompletion() {
        val h = habit("2026-08-07", goalType = HabitGoalType.COUNT, goalTarget = 8)
        val partial = mapOf(
            "2026-08-07" to HabitEntry(
                habitEntryId("h", "2026-08-07"), "h", "2026-08-07", HabitStatus.PARTIAL, value = 5, target = 8,
            )
        )
        val stats = HabitMath.habitStats(h, partial, "2026-08-07")
        assertEquals(HabitStatus.PARTIAL, stats.todayStatus)
        assertEquals(5, stats.todayValue)
        assertEquals(0, stats.current, "a partial day is not done")
        assertFalse(stats.isDoneToday)
    }

    // ── day rolls ────────────────────────────────────────────────────────────

    private fun twoHabits() = listOf(habit("2026-08-01", id = "a"), habit("2026-08-01", id = "b"))

    @Test
    fun rollDaysLevelsAndPerfect() {
        val rolls = HabitMath.rollDays(
            twoHabits(),
            index(doneOn("a", "2026-08-05"), doneOn("b", "2026-08-05"), doneOn("a", "2026-08-06")),
            "2026-08-05", "2026-08-07",
        )
        assertEquals(3, rolls.size)
        assertTrue(rolls[0].perfect)
        assertEquals(4, rolls[0].level)
        assertFalse(rolls[1].perfect)
        assertEquals(3, rolls[1].level, "half a day should shade at level 3")
        assertEquals(0, rolls[2].level)
    }

    @Test
    fun accountStreakIsAtLeastOneHabitPerDay() {
        // "At least one" rather than "all of them": an all-or-nothing account streak resets
        // on the first busy evening and then means nothing for the rest of the year.
        val rolls = HabitMath.rollDays(
            twoHabits(),
            index(doneOn("a", "2026-08-04"), doneOn("b", "2026-08-05"), doneOn("a", "2026-08-06")),
            "2026-08-01", "2026-08-07",
        )
        val (current, longest) = HabitMath.accountStreak(rolls)
        assertEquals(3, current, "today is still pending and must be stepped over")
        assertEquals(3, longest)
    }

    @Test
    fun longestPerfectRunAndDaysSinceMiss() {
        val rows = listOf("2026-08-03", "2026-08-04", "2026-08-05", "2026-08-06")
            .flatMap { listOf(doneOn("a", it), doneOn("b", it)) }
        val rolls = HabitMath.rollDays(twoHabits(), index(*rows.toTypedArray()), "2026-08-01", "2026-08-07")
        assertEquals(4, HabitMath.longestPerfectRun(rolls))
        // 08-01 and 08-02 were misses; 03..06 clean; 07 is today and does not count yet.
        assertEquals(4, HabitMath.daysSinceMiss(rolls))
    }

    @Test
    fun consistencyRewardsContinuityOverRawRate() {
        // Same completion rate, different streaks. If these were equal the score would just
        // be the completion percentage again, which the UI already shows next to it.
        assertTrue(HabitMath.consistencyScore(0.8f, 30) > HabitMath.consistencyScore(0.8f, 2))
        assertEquals(100, HabitMath.consistencyScore(1f, 21))
        assertEquals(0, HabitMath.consistencyScore(0f, 0))
    }

    @Test
    fun weekdayRatesPickBestAndWorst() {
        val mondays = listOf("2026-08-03", "2026-08-10", "2026-08-17")
            .flatMap { listOf(doneOn("a", it), doneOn("b", it)) }
        val rolls = HabitMath.rollDays(twoHabits(), index(*mondays.toTypedArray()), "2026-08-01", "2026-08-20")
        val (values, best, worst) = HabitMath.weekdayRates(rolls)
        assertEquals(0, best, "Monday should win")
        assertTrue(values[0] > values[worst])
    }

    @Test
    fun monthlyRatesReturnSixLabelledMonths() {
        val rolls = HabitMath.rollDays(twoHabits(), emptyMap(), "2026-03-01", "2026-08-07")
        val (values, labels) = HabitMath.monthlyRates(rolls, "2026-08-07")
        assertEquals(6, values.size)
        assertEquals(listOf("Mar", "Apr", "May", "Jun", "Jul", "Aug"), labels)
    }

    // ── achievements ─────────────────────────────────────────────────────────

    private fun view(views: List<HabitAchievementView>, id: String) = views.first { it.id == id }

    @Test
    fun achievementsUnlockOnThreshold() {
        val views = HabitAchievements.evaluate(
            HabitAchievementFacts(habitCount = 1, bestStreak = 30), emptyMap(), 1_700_000_000_000L,
        )
        assertTrue(view(views, "w7").unlocked)
        assertTrue(view(views, "w30").unlocked)
        assertFalse(view(views, "w100").unlocked)
        val progress = view(views, "w100").progress
        assertTrue(progress > 0.29f && progress < 0.31f, "w100 progress was $progress")
    }

    @Test
    fun anUnlockedAchievementIsNeverRelocked() {
        // The user deleted the habit that carried the streak, so today's facts say zero.
        // Taking a badge back is the most demoralising bug this screen can have.
        val stored = mapOf("w30" to HabitAchievementState("w30", unlockedAt = 1_700_000_000_000L))
        val views = HabitAchievements.evaluate(HabitAchievementFacts(), stored, 1_800_000_000_000L)
        val w30 = view(views, "w30")
        assertTrue(w30.unlocked)
        assertEquals(1f, w30.progress)
        assertEquals(1_700_000_000_000L, w30.unlockedAt, "the original unlock date must survive")
    }

    @Test
    fun legendCountsTheOtherBadgesOnly() {
        val everything = HabitAchievementFacts(
            habitCount = 3, bestStreak = 400, perfectRun = 40, monthRate = 1f,
            morningDone = 50, eveningDone = 50, daysSinceMiss = 90,
        )
        assertTrue(view(HabitAchievements.evaluate(everything, emptyMap(), 0L), "legend").unlocked)
        val empty = HabitAchievements.evaluate(HabitAchievementFacts(), emptyMap(), 0L)
        assertEquals(0f, view(empty, "legend").progress)
    }

    @Test
    fun newlyUnlockedOnlyReportsTransitions() {
        // Only transitions are persisted — rewriting twelve rows per visit would queue
        // twelve sync events and hand every other device a pointless delta pull.
        val stored = mapOf("first" to HabitAchievementState("first", unlockedAt = 1L))
        val views = HabitAchievements.evaluate(
            HabitAchievementFacts(habitCount = 1, bestStreak = 7), stored, 5L,
        )
        val fresh = HabitAchievements.newlyUnlocked(views, stored)
        assertFalse(fresh.any { it.id == "first" }, "an already-stored unlock was reported as new")
        assertTrue(fresh.any { it.id == "w7" }, "w7 crossed the threshold and was not reported")
    }

    // ── model normalisation ──────────────────────────────────────────────────

    @Test
    fun unknownEnumIdsFallBackInsteadOfThrowing() {
        // A row written by a newer build must still open on an older one.
        assertEquals(HabitSlot.ANYTIME, HabitSlot.from("whenever"))
        assertEquals(HabitDifficulty.MEDIUM, HabitDifficulty.from("impossible"))
        assertEquals(HabitGoalType.CHECK, HabitGoalType.from("vibes"))
        assertEquals(HabitFrequency.DAILY, HabitFrequency.from("fortnightly"))
        assertEquals(HabitLifecycle.ACTIVE, HabitLifecycle.from(null))
        assertEquals(DEFAULT_HABIT_CATEGORY, habitCategory("quantum-physics").id)
    }

    @Test
    fun timeBucketPrefersClockTimeOverSlot() {
        assertEquals("Night", Habit("x", "n", slot = HabitSlot.MORNING, timeLocal = "22:30").timeBucket)
        assertEquals("Midday", Habit("x", "n", slot = HabitSlot.ANYTIME).timeBucket)
        assertEquals("Morning", Habit("x", "n", slot = HabitSlot.EVENING, timeLocal = "05:10").timeBucket)
    }
}
