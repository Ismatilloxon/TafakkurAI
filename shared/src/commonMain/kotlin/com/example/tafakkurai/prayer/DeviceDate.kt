package com.example.tafakkurai.prayer

/** Local calendar date for a given day offset (0 = today, -1 = yesterday, +1 = tomorrow). */
expect fun deviceDate(dayOffset: Int): CalendarDate

/** Device UTC offset in hours (e.g. Tashkent = 5.0), DST-aware. */
expect fun deviceUtcOffsetHours(): Double
