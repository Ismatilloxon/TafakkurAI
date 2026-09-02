package com.example.tafakkurai.balance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun scoreOf(scores: List<BalanceScore>, key: String): BalanceScore =
    scores.first { it.key == key }

class BalanceTest {

    @Test
    fun a_user_who_has_done_nothing_scores_zero_not_a_default() {
        val scores = computeBalance(BalanceInput())

        assertEquals(0, scoreOf(scores, "spiritual").percent)
        assertEquals(0, scoreOf(scores, "educational").percent)
        assertEquals(0, scoreOf(scores, "social").percent)
    }

    @Test
    fun marking_prayers_raises_the_spiritual_axis() {
        val none = scoreOf(computeBalance(BalanceInput()), "spiritual").value!!
        val some = scoreOf(computeBalance(BalanceInput(prayersOnTime = 3)), "spiritual").value!!
        val all = scoreOf(computeBalance(BalanceInput(prayersInJamaat = 5)), "spiritual").value!!

        assertTrue(some > none, "3 prayers should beat none")
        assertTrue(all > some, "5 in jamaat should beat 3 alone")
    }

    @Test
    fun jamaat_counts_for_more_than_praying_alone() {
        val alone = scoreOf(computeBalance(BalanceInput(prayersOnTime = 5)), "spiritual").value!!
        val jamaat = scoreOf(computeBalance(BalanceInput(prayersInJamaat = 5)), "spiritual").value!!

        assertTrue(jamaat > alone)
    }

    @Test
    fun reading_the_quran_moves_both_spiritual_and_educational() {
        val before = computeBalance(BalanceInput())
        val after = computeBalance(BalanceInput(ayahsRead = 120, surahsStarted = 4))

        assertTrue(scoreOf(after, "spiritual").value!! > scoreOf(before, "spiritual").value!!)
        assertTrue(scoreOf(after, "educational").value!! > scoreOf(before, "educational").value!!)
    }

    @Test
    fun gratitude_journalling_drives_the_social_axis() {
        val quiet = scoreOf(computeBalance(BalanceInput()), "social")
        val busy = scoreOf(
            computeBalance(BalanceInput(gratitudeDaysThisWeek = 7, gratitudeStreak = 14)),
            "social",
        )

        assertEquals(0, quiet.percent)
        assertEquals(100, busy.percent)
        assertTrue(quiet.detail.contains("nothing written"))
    }

    @Test
    fun untracked_dimensions_report_no_value_rather_than_a_plausible_number() {
        val scores = computeBalance(
            BalanceInput(prayersInJamaat = 5, ayahsRead = 300, gratitudeDaysThisWeek = 7),
        )

        assertNull(scoreOf(scores, "physical").value)
        assertNull(scoreOf(scores, "financial").value)
        assertTrue(scoreOf(scores, "physical").detail.contains("Not tracked yet"))
    }

    @Test
    fun recorded_sleep_and_finance_actions_make_their_axes_real() {
        val scores = computeBalance(
            BalanceInput(physicalActivitiesToday = 1, financialActivitiesToday = 2),
        )

        assertTrue(scoreOf(scores, "physical").value!! > 0f)
        assertTrue(scoreOf(scores, "financial").value!! > scoreOf(scores, "physical").value!!)
    }

    @Test
    fun no_axis_can_exceed_one_however_much_is_recorded() {
        val scores = computeBalance(
            BalanceInput(
                prayersOnTime = 5, prayersInJamaat = 5, dhikrProgress = 4f, adhkarProgress = 3f,
                ayahsRead = 6236, surahsStarted = 114, gratitudeDaysThisWeek = 30,
                gratitudeStreak = 400, cardsLearned = 9000,
            ),
        )

        scores.mapNotNull { it.value }.forEach { assertTrue(it <= 1f, "value $it above 1") }
    }

    @Test
    fun the_detail_line_states_the_activity_behind_the_number() {
        val spiritual = scoreOf(
            computeBalance(BalanceInput(prayersOnTime = 2, prayersInJamaat = 1, ayahsRead = 40)),
            "spiritual",
        )

        assertTrue(spiritual.detail.contains("3 of 5 prayers"))
        assertTrue(spiritual.detail.contains("40 ayahs"))
    }
}
