package com.example.tafakkurai.prayer

import java.util.Calendar
import java.util.TimeZone

actual fun deviceDate(dayOffset: Int): CalendarDate {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, dayOffset)
    // Calendar.MONTH is 0-based; calculator expects 1-based.
    return CalendarDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

actual fun deviceUtcOffsetHours(): Double {
    val tz = TimeZone.getDefault()
    return tz.getOffset(System.currentTimeMillis()) / 3_600_000.0
}
