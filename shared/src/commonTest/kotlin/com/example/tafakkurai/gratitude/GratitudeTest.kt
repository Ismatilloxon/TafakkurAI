package com.example.tafakkurai.gratitude

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GratitudeStatsTest {

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        val stats = computeGratStats(
            activeDays = listOf("2026-07-27", "2026-07-28", "2026-07-29"),
            total = 5,
            todayKey = "2026-07-29",
        )

        assertEquals(3, stats.streak)
        assertEquals(5, stats.total)
        assertTrue(stats.week.last(), "today should be marked in the trailing week")
    }

    /** The day is not over — a run that reached yesterday must still read as alive. */
    @Test
    fun streakSurvivesAnUnwrittenToday() {
        val stats = computeGratStats(listOf("2026-07-26", "2026-07-27", "2026-07-28"), 3, "2026-07-29")

        assertEquals(3, stats.streak)
    }

    @Test
    fun brokenStreakIsZeroButLongestRemembers() {
        val stats = computeGratStats(listOf("2026-07-20", "2026-07-21"), 2, "2026-07-29")

        assertEquals(0, stats.streak)
        assertEquals(2, stats.longest)
    }

    @Test
    fun longestRunAndMonthDaysAreScopedCorrectly() {
        val stats = computeGratStats(
            activeDays = listOf(
                "2026-06-01", "2026-06-02", "2026-06-03", "2026-06-04",
                "2026-07-10", "2026-07-11",
            ),
            total = 6,
            todayKey = "2026-07-29",
        )

        assertEquals(4, stats.longest)
        assertEquals(2, stats.monthDays, "only July counts toward this month")
    }

    @Test
    fun emptyJournalHasNoStreak() {
        val stats = computeGratStats(emptyList(), 0, "2026-07-29")

        assertEquals(0, stats.streak)
        assertEquals(0, stats.longest)
        assertEquals(List(7) { false }, stats.week)
    }
}

class GratitudeCategoryTest {

    @Test
    fun breakdownOrdersByShareAndTruncates() {
        val shares = categoryBreakdown(mapOf("people" to 5, "faith" to 3, "nature" to 1, "work" to 1), limit = 2)

        assertEquals(2, shares.size)
        assertEquals("people", shares[0].key)
        assertEquals(50, shares[0].percent)
        assertEquals("People", shares[0].label)
        assertEquals("faith", shares[1].key)
    }

    @Test
    fun breakdownOfNothingIsEmpty() {
        assertEquals(emptyList(), categoryBreakdown(emptyMap()))
    }

    @Test
    fun unknownCategoryFallsBackInsteadOfCrashing() {
        assertEquals(DEFAULT_CATEGORY, categoryOf("not-a-category").key)
        assertEquals("People", categoryOf("people").label)
    }

    @Test
    fun moodIndexKeepsLowToWonderfulOrder() {
        assertEquals(0, moodIndex("low"))
        assertEquals(4, moodIndex("wonderful"))
        assertEquals(-1, moodIndex(null), "a missing mood must be -1 so the chart can skip the day")
        assertNull(moodOf("nope"))
    }
}

class GratitudeSearchTest {

    private val entries = listOf(
        GratEntry(id = "1", text = "Evening walk after Maghrib", category = "nature", emotions = listOf("Calm"), entryDate = "2026-07-28"),
        GratEntry(id = "2", text = "Mother called to ask how I slept", category = "people", emotions = listOf("Loved"), entryDate = "2026-07-26"),
        GratEntry(id = "3", text = "Finished the refactor", why = "Relief after two weeks", category = "wins", entryDate = "2026-07-27"),
    )

    @Test
    fun searchMatchesText() {
        assertEquals(listOf("2"), searchEntries(entries, "mother", null).map { it.id })
    }

    @Test
    fun searchMatchesEmotionsAndWhyNote() {
        assertEquals(listOf("1"), searchEntries(entries, "calm", null).map { it.id })
        assertEquals(listOf("3"), searchEntries(entries, "relief", null).map { it.id })
    }

    @Test
    fun searchMatchesCategoryLabel() {
        assertEquals(listOf("2"), searchEntries(entries, "people", null).map { it.id })
    }

    @Test
    fun categoryFilterNarrowsWithoutAQuery() {
        assertEquals(listOf("1"), searchEntries(entries, "", "nature").map { it.id })
    }

    @Test
    fun emptyQueryAndNoFilterReturnsEverything() {
        assertEquals(3, searchEntries(entries, "  ", null).size)
    }
}

class GratitudeLabelTest {

    @Test
    fun todayAndYesterdayAreNamed() {
        assertEquals("Today", gratDateLabel("2026-07-29", "2026-07-29"))
        assertEquals("Yesterday", gratDateLabel("2026-07-28", "2026-07-29"))
    }

    @Test
    fun olderEntriesShowMonthAndDay() {
        assertEquals("Jul 20", gratDateLabel("2026-07-20", "2026-07-29"))
        assertEquals("Apr 29", gratDateLabel("2026-04-29", "2026-07-29"))
    }

    @Test
    fun relativeLabelRoundsToWeeksMonthsYears() {
        assertEquals("This week", gratRelativeLabel("2026-07-26", "2026-07-29"))
        assertEquals("2 weeks ago", gratRelativeLabel("2026-07-14", "2026-07-29"))
        assertEquals("3 months ago", gratRelativeLabel("2026-04-29", "2026-07-29"))
        assertEquals("1 year ago", gratRelativeLabel("2025-06-29", "2026-07-29"))
    }
}

class GratitudeChallengeTest {

    @Test
    fun currentDayIsTheFirstUncompletedOne() {
        val journey = GRAT_CHALLENGES.first { it.id == "c7" }
        val progress = GratChallengeProgress(journey, joined = true, doneDays = setOf(1, 2, 3))

        assertEquals(3, progress.done)
        assertEquals(4, progress.currentDay)
        assertEquals(3f / 7f, progress.fraction)
    }

    @Test
    fun finishedJourneyStaysOnItsLastDay() {
        val journey = GRAT_CHALLENGES.first { it.id == "c7" }
        val progress = GratChallengeProgress(journey, joined = true, doneDays = (1..7).toSet())

        assertEquals(7, progress.currentDay)
        assertEquals(1f, progress.fraction)
    }
}

class GratitudeReflectionTest {

    @Test
    fun emptyJournalStillGetsALine() {
        assertTrue(gratReflection(emptyList(), 0).isNotEmpty())
    }

    @Test
    fun topCategoryDrivesTheCopy() {
        val shares = listOf(GratCategoryShare("people", "People", 8, 40))

        assertTrue(gratReflection(shares, 20).contains("Relationships"))
    }
}
