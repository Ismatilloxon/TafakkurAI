package com.example.tafakkurai.spiritual

/**
 * Day arithmetic over the "yyyy-MM-dd" keys the local stores use.
 *
 * The project does not depend on kotlinx-datetime — the prayer engine rolls its own
 * civil-date helpers — so streaks use the same approach: a proleptic Gregorian
 * days-from-civil conversion, which is pure integer maths and needs no clock, no
 * timezone database and no platform code. That also makes it directly unit-testable.
 */

/** Days since 1970-01-01 for a proleptic Gregorian date. */
fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400                                            // [0, 399]
    val mp = (month + 9) % 12                                          // Mar = 0
    val doy = (153L * mp + 2) / 5 + day - 1                            // [0, 365]
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy                    // [0, 146096]
    return era * 146097L + doe - 719468L
}

/** Parses "yyyy-MM-dd". Returns null for anything else rather than throwing. */
fun parseDateKey(key: String): Triple<Int, Int, Int>? {
    val parts = key.split("-")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return Triple(y, m, d)
}

/** Day number for a "yyyy-MM-dd" key, or null when it does not parse. */
fun dayNumberOf(key: String): Long? = parseDateKey(key)?.let { (y, m, d) -> daysFromCivil(y, m, d) }

/**
 * Length of the run of consecutive days ending today.
 *
 * A streak still counts when today has not been logged yet but yesterday was — the
 * day is not over, so the run should not read as broken until it actually is.
 */
fun streakLength(activeDays: Collection<String>, todayKey: String): Int {
    val today = dayNumberOf(todayKey) ?: return 0
    val days = activeDays.mapNotNull(::dayNumberOf).toSet()
    if (days.isEmpty()) return 0
    var cursor = when {
        days.contains(today) -> today
        days.contains(today - 1) -> today - 1
        else -> return 0
    }
    var length = 0
    while (days.contains(cursor)) {
        length++
        cursor--
    }
    return length
}
