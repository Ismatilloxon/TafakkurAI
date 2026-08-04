package com.example.tafakkurai.prayer

import com.example.tafakkurai.location.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the port against islom.uz's own published table for Tashkent, so a future
 * refactor that drifts from the app's numbers fails loudly. Values cross-checked
 * with the app's screenshot (Asr 17:37, Sunrise 05:09, Isha 21:23) — within a
 * minute, the residue being the exact device coordinates islom.uz used.
 */
class IslomUzPrayerCalculatorTest {

    // Tashkent, 2026-07-23, UTC+5.
    private val tashkent = Coordinates(latitude = 41.31, longitude = 69.24)
    private val date = CalendarDate(2026, 7, 23)
    private val calc = IslomUzPrayerCalculator()

    private fun times() = calc.timesFor(tashkent, date, utcOffsetHours = 5.0)
        .associate { it.key to it.time }

    @Test
    fun matches_islom_uz_tashkent_table() {
        val t = times()
        assertEquals("03:30", t["fajr"])      // islom.uz 03:32 (−5 min applied)
        assertEquals("05:09", t["sunrise"])   // islom.uz 05:09
        assertEquals("12:30", t["dhuhr"])     // islom.uz 12:29
        assertEquals("17:37", t["asr"])       // islom.uz 17:37 — the Hanafi fix
        assertEquals("19:55", t["maghrib"])   // islom.uz 19:54 (+5 min applied)
        assertEquals("21:24", t["isha"])      // islom.uz 21:23
    }

    @Test
    fun hanafi_asr_is_later_than_shafii() {
        fun asrMin(ratio: Double) = IslomUzPrayerCalculator(IslomUzParams(asrShadowRatio = ratio))
            .timesFor(tashkent, date, 5.0).first { it.key == "asr" }.min
        assertTrue(asrMin(2.0) > asrMin(1.0), "Hanafi (shadow 2) Asr must fall after Shafi'i (shadow 1)")
    }

    @Test
    fun fifteen_degree_fajr_is_later_than_eighteen() {
        fun fajrMin(angle: Double) = IslomUzPrayerCalculator(IslomUzParams(fajrAngle = angle))
            .timesFor(tashkent, date, 5.0).first { it.key == "fajr" }.min
        // Shallower depression → Fajr closer to sunrise → later clock time.
        assertTrue(fajrMin(15.0) > fajrMin(18.0))
    }

    @Test
    fun convention_mapping_defaults_to_islom_uz() {
        val p = IslomUzParams.forConvention(calcMethod = "Uzbekistan", madhab = "hanafi")
        assertEquals(15.0, p.fajrAngle)
        assertEquals(15.0, p.ishaAngle)
        assertEquals(2.0, p.asrShadowRatio)
    }
}
