package com.example.tafakkurai.prayer

import com.example.tafakkurai.PrayerTime
import com.example.tafakkurai.TPRAYERS

/**
 * The app's prayer-schedule source. This is the seam where a real network/API
 * client plugs in: return that day's [PrayerTime]s (with `min` = minutes of day).
 *
 * @param dayOffset 0 = today, -1 = yesterday, +1 = tomorrow.
 */
interface PrayerScheduleRepository {
    fun schedule(dayOffset: Int): List<PrayerTime>
}

/**
 * Default repository backed by the bundled static schedule ([TPRAYERS]). Swap this
 * for an API-backed implementation without touching the calculator or ViewModel.
 */
class StaticPrayerScheduleRepository : PrayerScheduleRepository {
    override fun schedule(dayOffset: Int): List<PrayerTime> = TPRAYERS
}

/**
 * Builds the absolute-minute timeline the calculator consumes. Stitches
 * yesterday → today → tomorrow so any "now" in [00:00, 24:00) lands inside a
 * segment, and the prayer *after Isha* is automatically tomorrow's Fajr — giving
 * the requested "switch to tomorrow after Isha" behaviour for free.
 */
class PrayerTimelineService(
    private val repository: PrayerScheduleRepository = StaticPrayerScheduleRepository(),
) {
    fun timeline(): List<PrayerInstant> {
        val yesterday = repository.schedule(-1).map { it.toInstant(-1440f) }
        val today = repository.schedule(0).map { it.toInstant(0f) }
        val tomorrow = repository.schedule(1).map { it.toInstant(1440f) }
        return (yesterday + today + tomorrow).sortedBy { it.absoluteMinute }
    }

    private fun PrayerTime.toInstant(dayShift: Float) =
        PrayerInstant(key = key, name = name, absoluteMinute = min + dayShift)
}
