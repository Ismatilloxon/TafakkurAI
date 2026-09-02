package com.example.tafakkurai.sleep

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Clock arithmetic.
 *
 * The midnight-wraparound cases are the reason this file exists. They are the bugs that make a
 * sleep app quietly tell its most consistent users they are chaotic, and they never show up in
 * manual testing because everyone tests with a 23:00 bedtime on a Tuesday afternoon.
 */
class SleepTimeTest {

    // ── signed representation ────────────────────────────────────────────────

    @Test
    fun `evening times are negative — morning times positive`() {
        assertEquals(-10, SleepTime.toSigned(1430))  // 23:50
        assertEquals(-60, SleepTime.toSigned(1380))  // 23:00
        assertEquals(10, SleepTime.toSigned(10))     // 00:10
        assertEquals(420, SleepTime.toSigned(420))   // 07:00
    }

    @Test
    fun `signed round-trips through minute of day`() {
        for (m in 0 until SleepTime.MINUTES_PER_DAY) {
            assertEquals(m, SleepTime.toMinuteOfDay(SleepTime.toSigned(m)))
        }
    }

    @Test
    fun `noon is the boundary between morning and evening`() {
        assertEquals(719, SleepTime.toSigned(719))    // 11:59 stays positive
        assertEquals(-720, SleepTime.toSigned(720))   // 12:00 flips
    }

    // ── circular mean ────────────────────────────────────────────────────────

    @Test
    fun `mean of times either side of midnight is midnight — not midday`() {
        // The bug this whole representation exists to prevent: the arithmetic mean of
        // 23:50 and 00:10 is 12:00, which is the exact opposite of the right answer.
        val mean = SleepTime.circularMeanMinutes(listOf(1430, 10))
        assertEquals(0, mean)
    }

    @Test
    fun `mean works with the signed representation too`() {
        assertEquals(0, SleepTime.circularMeanMinutes(listOf(-10, 10)))
    }

    @Test
    fun `mean of a normal cluster of bedtimes`() {
        // 22:45, 23:00, 23:15 → 23:00, i.e. −60.
        val mean = SleepTime.circularMeanMinutes(listOf(-75, -60, -45))
        assertEquals(-60, mean)
    }

    @Test
    fun `mean of morning wake times`() {
        // 06:30, 07:00, 07:30 → 07:00.
        assertEquals(420, SleepTime.circularMeanMinutes(listOf(390, 420, 450)))
    }

    @Test
    fun `mean of an empty list is null`() {
        assertNull(SleepTime.circularMeanMinutes(emptyList()))
    }

    @Test
    fun `mean of times spread evenly round the clock is null — not an artefact`() {
        // Four times 6 hours apart cancel exactly. There is no mean direction, and inventing
        // one would be reporting floating-point noise as a fact.
        assertNull(SleepTime.circularMeanMinutes(listOf(0, 360, 720, 1080)))
    }

    // ── circular deviation ───────────────────────────────────────────────────

    @Test
    fun `deviation across midnight is small — not enormous`() {
        // 23:50 and 00:10 are 20 minutes apart. A naive stddev over 0..1439 reports ~700.
        val deviation = SleepTime.circularDeviationMinutes(listOf(1430, 10))
        assertTrue(deviation < 30, "expected a small deviation, got $deviation")
    }

    @Test
    fun `identical times have zero deviation`() {
        assertEquals(0.0, SleepTime.circularDeviationMinutes(listOf(-60, -60, -60)), 0.001)
    }

    @Test
    fun `a scattered schedule has a large deviation`() {
        val tight = SleepTime.circularDeviationMinutes(listOf(-60, -55, -65, -58))
        val loose = SleepTime.circularDeviationMinutes(listOf(-60, 120, -240, 30))
        assertTrue(loose > tight * 4, "loose=$loose tight=$tight")
    }

    @Test
    fun `deviation is capped at half a day`() {
        val uniform = SleepTime.circularDeviationMinutes(listOf(0, 360, 720, 1080))
        assertTrue(uniform <= SleepTime.MINUTES_PER_DAY / 2.0)
    }

    // ── circular delta ───────────────────────────────────────────────────────

    @Test
    fun `delta takes the short way round`() {
        assertEquals(20, SleepTime.circularDeltaMinutes(1430, 10))   // 23:50 → 00:10
        assertEquals(-20, SleepTime.circularDeltaMinutes(10, 1430))  // 00:10 → 23:50
    }

    @Test
    fun `delta never exceeds half a day`() {
        for (from in 0 until 1440 step 37) {
            for (to in 0 until 1440 step 53) {
                val delta = SleepTime.circularDeltaMinutes(from, to)
                assertTrue(abs(delta) <= 720, "from=$from to=$to delta=$delta")
            }
        }
    }

    // ── date keys ────────────────────────────────────────────────────────────

    @Test
    fun `day number and date key are exact inverses`() {
        // civil_from_days must invert daysFromCivil, or every gap-filled chart row is
        // labelled with the wrong day.
        val keys = listOf("1970-01-01", "1999-12-31", "2000-02-29", "2026-08-07", "2100-03-01")
        for (key in keys) {
            val day = com.example.tafakkurai.spiritual.dayNumberOf(key)!!
            assertEquals(key, SleepTime.dateKeyOfDayNumber(day))
        }
    }

    @Test
    fun `date key of an instant respects the utc offset`() {
        // 2026-08-07 22:00 UTC is already the 8th in Tashkent (UTC+5).
        val instant = SleepTime.startOfDayMs("2026-08-07", 0.0)!! + 22 * 60 * SleepTime.MS_PER_MINUTE
        assertEquals("2026-08-07", SleepTime.dateKeyOf(instant, 0.0))
        assertEquals("2026-08-08", SleepTime.dateKeyOf(instant, 5.0))
    }

    @Test
    fun `start of day round-trips`() {
        val start = SleepTime.startOfDayMs("2026-08-07", 0.0)!!
        assertEquals("2026-08-07", SleepTime.dateKeyOf(start, 0.0))
        assertEquals(0, SleepTime.minuteOfDay(start, 0.0))
    }

    // ── formatting ───────────────────────────────────────────────────────────

    @Test
    fun `duration formatting drops empty parts`() {
        assertEquals("7h 24m", SleepTime.formatDuration(444))
        assertEquals("8h", SleepTime.formatDuration(480))
        assertEquals("45m", SleepTime.formatDuration(45))
        assertEquals("0m", SleepTime.formatDuration(0))
        assertEquals("0m", SleepTime.formatDuration(-10))
    }

    @Test
    fun `clock formatting handles both conventions`() {
        assertEquals("23:30", SleepTime.formatClock(-30, use24Hour = true))
        assertEquals("11:30 PM", SleepTime.formatClock(-30, use24Hour = false))
        assertEquals("00:15", SleepTime.formatClock(15, use24Hour = true))
        assertEquals("12:15 AM", SleepTime.formatClock(15, use24Hour = false))
        assertEquals("12:00 PM", SleepTime.formatClock(-720, use24Hour = false))
    }

    @Test
    fun `delta formatting reads as English`() {
        assertEquals("on time", SleepTime.formatDelta(0))
        assertEquals("on time", SleepTime.formatDelta(3))
        assertEquals("42m earlier", SleepTime.formatDelta(-42))
        assertEquals("1h 5m later", SleepTime.formatDelta(65))
    }
}
