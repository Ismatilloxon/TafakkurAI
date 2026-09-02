package com.example.tafakkurai.prayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.example.tafakkurai.i18n.tr

@Composable
@ReadOnlyComposable
fun localizedPrayerName(key: String): String = tr("prayer.name.$key")

@Composable
@ReadOnlyComposable
fun localizedPrayerCountdown(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val hours = safe / 60
    val minutes = safe % 60
    return if (hours > 0) {
        tr("prayer.countdown.hours_minutes", hours, minutes)
    } else {
        tr("prayer.countdown.minutes", minutes)
    }
}
