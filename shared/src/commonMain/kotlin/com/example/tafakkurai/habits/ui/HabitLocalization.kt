package com.example.tafakkurai.habits.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.example.tafakkurai.habits.MONTH_SHORT_KEYS
import com.example.tafakkurai.habits.dayNumberOfKey
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.spiritual.parseDateKey

@Composable
@ReadOnlyComposable
internal fun localizedHabitDay(dateKey: String, todayKey: String): String {
    val today = dayNumberOfKey(todayKey)
    val day = dayNumberOfKey(dateKey)
    if (today != null && day != null) {
        when (today - day) {
            0L -> return tr("habits.day.today")
            1L -> return tr("habits.day.yesterday")
        }
    }
    val parsed = parseDateKey(dateKey) ?: return dateKey
    val (_, month, dayOfMonth) = parsed
    return tr("habits.day.month_day", tr(MONTH_SHORT_KEYS[month - 1]), dayOfMonth)
}

@Composable
@ReadOnlyComposable
internal fun localizedHabitMonth(year: Int, month: Int): String =
    tr("habits.calendar.month_year", tr(MONTH_SHORT_KEYS[month - 1]), year)

@Composable
@ReadOnlyComposable
internal fun habitDays(count: Int): String =
    tr(if (count == 1) "habits.unit.day.one" else "habits.unit.day.other", count)

