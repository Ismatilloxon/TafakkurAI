package com.example.tafakkurai.i18n

import com.example.tafakkurai.prayer.CalendarDate

/**
 * Weekday for a proleptic-Gregorian date, 0 = Sunday … 6 = Saturday (Sakamoto's algorithm).
 *
 * Computed here rather than added to the `deviceDate` expect/actual: it is pure arithmetic, so
 * keeping it in commonMain means one implementation and a test that runs without a simulator.
 */
fun weekdayIndex(date: CalendarDate): Int {
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    var year = date.year
    if (date.month < 3) year -= 1
    return (year + year / 4 - year / 100 + year / 400 + offsets[date.month - 1] + date.day) % 7
}

/**
 * The Today header's date line — "Dushanba, 3-avgust", "Monday, August 3", "Понедельник, 3 августа".
 *
 * Word order and the day/month join differ per language, so the whole pattern lives in the
 * catalogs as `date.header` with {0} = weekday, {1} = day, {2} = month.
 */
fun Strings.formatHeaderDate(date: CalendarDate): String = format(
    "date.header",
    get("date.weekday.${weekdayIndex(date)}"),
    date.day,
    get("date.month.${date.month}"),
)

/**
 * Greeting bucket for a wall-clock time, as a catalog key.
 *
 * The header used to be a fixed "Xayrli tong" (good morning) from the mock user, which read as
 * a bug at any other hour — it greeted the evening tester with "good morning".
 */
fun greetingKey(secondsOfDay: Int): String = when (secondsOfDay / 3600) {
    in 5..11 -> "today.greeting.morning"
    in 12..16 -> "today.greeting.afternoon"
    in 17..21 -> "today.greeting.evening"
    else -> "today.greeting.night"
}
