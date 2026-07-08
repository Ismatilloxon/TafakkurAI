package com.example.tafakkurai.prayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrayerProgressCalculatorTest {

    // Minimal three-prayer day plus the wrap neighbours the service would supply.
    // fajr 300 (05:00), dhuhr 720 (12:00), isha 1320 (22:00).
    private val timeline = listOf(
        PrayerInstant("isha", "Isha", 1320f - 1440f),   // yesterday 22:00
        PrayerInstant("fajr", "Fajr", 300f),
        PrayerInstant("dhuhr", "Dhuhr", 720f),
        PrayerInstant("isha", "Isha", 1320f),
        PrayerInstant("fajr", "Fajr", 300f + 1440f),    // tomorrow 05:00
    )

    private fun frac(nowMin: Float) = PrayerProgressCalculator.progressAt(timeline, nowMin)

    @Test
    fun empty_immediately_after_a_prayer() {
        val p = frac(300f) // exactly Fajr
        assertEquals("fajr", p.current.key)
        assertEquals("dhuhr", p.next.key)
        assertEquals(0f, p.fraction, 0.0001f)
    }

    @Test
    fun half_filled_at_segment_midpoint() {
        val p = frac(510f) // halfway 05:00 -> 12:00
        assertEquals(0.5f, p.fraction, 0.0001f)
    }

    @Test
    fun full_at_next_prayer_arrival() {
        val p = frac(719.99f) // a breath before Dhuhr
        assertTrue(p.fraction > 0.99f, "expected ~1.0 but was ${p.fraction}")
    }

    @Test
    fun after_isha_targets_tomorrow_fajr() {
        val p = frac(1380f) // 23:00, past Isha
        assertEquals("isha", p.current.key)
        assertEquals("fajr", p.next.key)
        assertEquals(300f + 1440f, p.next.absoluteMinute)
        assertTrue(p.fraction in 0f..1f)
    }

    @Test
    fun before_fajr_uses_yesterday_isha_as_current() {
        val p = frac(120f) // 02:00
        assertEquals("isha", p.current.key)
        assertEquals(-120f, p.current.absoluteMinute) // yesterday 22:00
        assertEquals("fajr", p.next.key)
    }

    @Test
    fun minutes_to_next_counts_down() {
        val p = frac(660f) // 11:00, one hour before Dhuhr
        assertEquals(60f, p.minutesToNext, 0.0001f)
    }
}
