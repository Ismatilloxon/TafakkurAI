package com.example.tafakkurai.prayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.tafakkurai.PrayerTime
import org.koin.mp.KoinPlatform.getKoin

/**
 * Today's (or [dayOffset]'s) prayer schedule for the current device location.
 * Backed by the singleton [PrayerScheduleRepository], which holds its coordinates
 * in Compose state — so a screen calling this recomposes automatically once the
 * location permission is granted and the first fix arrives.
 *
 * @param dayOffset 0 = today, -1 = yesterday, +1 = tomorrow.
 */
@Composable
fun rememberPrayerSchedule(dayOffset: Int = 0): List<PrayerTime> {
    val repository: PrayerScheduleRepository = remember { getKoin().get() }
    return repository.schedule(dayOffset)
}

/** Same schedule keyed by prayer key ("fajr", "dhuhr", …) → "HH:mm". */
@Composable
fun rememberPrayerTimes(dayOffset: Int = 0): Map<String, String> =
    rememberPrayerSchedule(dayOffset).associate { it.key to it.time }
