package com.example.tafakkurai.spiritual

import com.example.tafakkurai.adhkar.AdhkarCategory
import com.example.tafakkurai.adhkar.AdhkarStore
import com.example.tafakkurai.adhkar.EVENING_ADHKAR
import com.example.tafakkurai.adhkar.MORNING_ADHKAR
import com.example.tafakkurai.adhkar.adhkarFor
import com.example.tafakkurai.dhikr.DHIKR_CATALOG
import com.example.tafakkurai.dhikr.DhikrStore
import com.example.tafakkurai.dhikr.QUICK_TARGETS
import com.example.tafakkurai.dhikr.dhikrOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TODAY = "2026-07-28"

class DhikrStoreTest {

    private fun store(
        persistence: InMemorySpiritualPersistence = InMemorySpiritualPersistence(),
        today: String = TODAY,
    ) = DhikrStore(persistence, persistence, todayKey = { today })

    @Test
    fun increment_counts_up_by_one() {
        val subject = store()
        assertEquals(0, subject.count)
        subject.increment()
        subject.increment()
        subject.increment()
        assertEquals(3, subject.count)
    }

    @Test
    fun increment_reports_the_tap_that_reaches_the_target() {
        val subject = store()
        subject.setTarget(3)
        assertFalse(subject.increment())
        assertFalse(subject.increment())
        assertTrue(subject.increment())      // the third tap reaches 3
        assertTrue(subject.isTargetReached)
        assertFalse(subject.increment())     // past the target, not a fresh milestone
    }

    @Test
    fun progress_tracks_count_against_target_and_never_exceeds_one() {
        val subject = store()
        subject.setTarget(4)
        repeat(2) { subject.increment() }
        assertEquals(0.5f, subject.progress)

        repeat(10) { subject.increment() }
        assertEquals(1f, subject.progress)
    }

    @Test
    fun reset_clears_only_the_selected_dhikr() {
        val subject = store()
        repeat(5) { subject.increment() }                 // SubhanAllah
        subject.select("alhamdulillah")
        repeat(3) { subject.increment() }

        subject.reset()
        assertEquals(0, subject.countOf("alhamdulillah"))
        assertEquals(5, subject.countOf("subhanallah"))
        assertEquals(5, subject.todayTotal)
    }

    @Test
    fun switching_dhikr_keeps_separate_counts_and_adopts_its_sunnah_target() {
        val subject = store()
        repeat(7) { subject.increment() }
        assertEquals(33, subject.target)

        subject.select("lailahaillallah")
        assertEquals(0, subject.count)
        assertEquals(100, subject.target)
        subject.increment()

        assertEquals(7, subject.countOf("subhanallah"))
        assertEquals(1, subject.countOf("lailahaillallah"))
        assertEquals(8, subject.todayTotal)
    }

    @Test
    fun counts_and_selection_survive_a_restart() {
        val persistence = InMemorySpiritualPersistence()
        val first = store(persistence)
        first.select("astaghfirullah")
        repeat(12) { first.increment() }

        val reopened = store(persistence)
        assertEquals("astaghfirullah", reopened.selected.id)
        assertEquals(12, reopened.count)
        assertEquals(12, reopened.todayTotal)
    }

    @Test
    fun a_new_day_starts_the_counter_fresh_but_keeps_history() {
        val persistence = InMemorySpiritualPersistence()
        repeat(9) { store(persistence, today = "2026-07-27").increment() }

        val today = store(persistence, today = TODAY)
        assertEquals(0, today.count)
        assertEquals(0, today.todayTotal)
        // Yesterday's run is not broken just because today has not been counted yet.
        assertEquals(1, today.streak)
        today.increment()
        assertEquals(2, today.streak)
    }

    @Test
    fun streak_counts_consecutive_days_and_breaks_on_a_gap() {
        val persistence = InMemorySpiritualPersistence()
        persistence.put("2026-07-28", "subhanallah", 10)
        persistence.put("2026-07-27", "subhanallah", 10)
        persistence.put("2026-07-26", "subhanallah", 10)
        persistence.put("2026-07-24", "subhanallah", 10) // gap on the 25th
        assertEquals(3, store(persistence).streak)
    }

    @Test
    fun favourites_toggle_and_persist() {
        val persistence = InMemorySpiritualPersistence()
        val subject = store(persistence)
        assertTrue(subject.isFavourite("subhanallah"))

        subject.toggleFavourite("astaghfirullah")
        subject.toggleFavourite("subhanallah")

        val reopened = store(persistence)
        assertTrue(reopened.isFavourite("astaghfirullah"))
        assertFalse(reopened.isFavourite("subhanallah"))
        assertEquals(listOf("astaghfirullah"), reopened.favouriteDhikr.map { it.id })
    }

    @Test
    fun a_non_positive_target_is_rejected() {
        val subject = store()
        subject.setTarget(0)
        subject.setTarget(-5)
        assertEquals(33, subject.target)
    }

    @Test
    fun catalog_offers_the_five_core_adhkar_and_the_quick_targets() {
        assertEquals(
            listOf("subhanallah", "alhamdulillah", "allahuakbar", "lailahaillallah", "astaghfirullah"),
            DHIKR_CATALOG.map { it.id },
        )
        assertEquals(listOf(33, 66, 99, 100), QUICK_TARGETS)
        assertEquals("SubhanAllah", dhikrOf("subhanallah").transliteration)
        assertEquals("SubhanAllah", dhikrOf("nonexistent").transliteration) // safe fallback
    }
}

class AdhkarStoreTest {

    private fun store(
        persistence: InMemorySpiritualPersistence = InMemorySpiritualPersistence(),
        today: String = TODAY,
    ) = AdhkarStore(persistence, todayKey = { today })

    @Test
    fun both_lists_are_populated_and_ids_are_unique_within_each() {
        assertTrue(MORNING_ADHKAR.size >= 10)
        assertTrue(EVENING_ADHKAR.size >= 10)
        assertEquals(MORNING_ADHKAR.size, MORNING_ADHKAR.map { it.id }.toSet().size)
        assertEquals(EVENING_ADHKAR.size, EVENING_ADHKAR.map { it.id }.toSet().size)
        assertEquals(MORNING_ADHKAR, adhkarFor(AdhkarCategory.MORNING))
        assertEquals(EVENING_ADHKAR, adhkarFor(AdhkarCategory.EVENING))
    }

    @Test
    fun marking_complete_and_unmarking_flips_the_same_item() {
        val subject = store()
        val item = MORNING_ADHKAR.first().id
        assertFalse(subject.isDone(AdhkarCategory.MORNING, item))

        subject.toggle(AdhkarCategory.MORNING, item)
        assertTrue(subject.isDone(AdhkarCategory.MORNING, item))
        assertEquals(1, subject.doneCount(AdhkarCategory.MORNING))

        subject.toggle(AdhkarCategory.MORNING, item)
        assertFalse(subject.isDone(AdhkarCategory.MORNING, item))
        assertEquals(0, subject.doneCount(AdhkarCategory.MORNING))
    }

    @Test
    fun progress_is_done_over_total() {
        val subject = store()
        val total = subject.totalCount(AdhkarCategory.MORNING)
        MORNING_ADHKAR.take(total / 2).forEach { subject.toggle(AdhkarCategory.MORNING, it.id) }

        assertEquals(total / 2, subject.doneCount(AdhkarCategory.MORNING))
        assertEquals((total / 2).toFloat() / total, subject.progressOf(AdhkarCategory.MORNING))
        assertEquals(((total / 2).toFloat() / total * 100).toInt(), subject.percentOf(AdhkarCategory.MORNING))
    }

    @Test
    fun morning_and_evening_are_tracked_independently() {
        val subject = store()
        // Ayat al-Kursi appears in both lists — completing it in the morning must not
        // silently tick the evening list too.
        val shared = "ayat-al-kursi"
        subject.toggle(AdhkarCategory.MORNING, shared)

        assertTrue(subject.isDone(AdhkarCategory.MORNING, shared))
        assertFalse(subject.isDone(AdhkarCategory.EVENING, shared))
        assertEquals(1, subject.doneCount(AdhkarCategory.MORNING))
        assertEquals(0, subject.doneCount(AdhkarCategory.EVENING))
    }

    @Test
    fun completions_survive_a_restart_and_reset_the_next_day() {
        val persistence = InMemorySpiritualPersistence()
        val item = EVENING_ADHKAR.first().id
        store(persistence).toggle(AdhkarCategory.EVENING, item)

        assertTrue(store(persistence).isDone(AdhkarCategory.EVENING, item))
        assertFalse(store(persistence, today = "2026-07-29").isDone(AdhkarCategory.EVENING, item))
    }

    @Test
    fun category_ids_round_trip_and_fall_back_safely() {
        assertEquals(AdhkarCategory.MORNING, AdhkarCategory.from("morning"))
        assertEquals(AdhkarCategory.EVENING, AdhkarCategory.from("evening"))
        assertEquals(AdhkarCategory.MORNING, AdhkarCategory.from("whatever"))
        assertEquals(AdhkarCategory.MORNING, AdhkarCategory.from(null))
    }
}

class DateKeysTest {

    @Test
    fun days_from_civil_matches_the_unix_epoch() {
        assertEquals(0L, daysFromCivil(1970, 1, 1))
        assertEquals(1L, daysFromCivil(1970, 1, 2))
        assertEquals(-1L, daysFromCivil(1969, 12, 31))
    }

    @Test
    fun consecutive_days_differ_by_one_across_month_and_year_ends() {
        assertEquals(1L, daysFromCivil(2026, 7, 28) - daysFromCivil(2026, 7, 27))
        assertEquals(1L, daysFromCivil(2026, 8, 1) - daysFromCivil(2026, 7, 31))
        assertEquals(1L, daysFromCivil(2027, 1, 1) - daysFromCivil(2026, 12, 31))
        // 2028 is a leap year
        assertEquals(1L, daysFromCivil(2028, 3, 1) - daysFromCivil(2028, 2, 29))
    }

    @Test
    fun malformed_date_keys_are_rejected() {
        assertNull(parseDateKey("2026-13-01"))
        assertNull(parseDateKey("not-a-date"))
        assertNull(parseDateKey("2026-07"))
        assertNull(dayNumberOf("oops"))
    }

    @Test
    fun streak_counts_back_from_today() {
        val days = listOf("2026-07-28", "2026-07-27", "2026-07-26")
        assertEquals(3, streakLength(days, TODAY))
    }

    @Test
    fun streak_survives_a_day_that_has_not_been_logged_yet() {
        // Today is still in progress — yesterday's run should not read as broken.
        val days = listOf("2026-07-27", "2026-07-26")
        assertEquals(2, streakLength(days, TODAY))
    }

    @Test
    fun streak_is_zero_once_the_run_is_actually_broken() {
        assertEquals(0, streakLength(listOf("2026-07-26", "2026-07-25"), TODAY))
        assertEquals(0, streakLength(emptyList(), TODAY))
    }
}
