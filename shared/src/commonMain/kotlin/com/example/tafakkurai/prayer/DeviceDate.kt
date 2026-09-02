package com.example.tafakkurai.prayer

/** Local calendar date for a given day offset (0 = today, -1 = yesterday, +1 = tomorrow). */
expect fun deviceDate(dayOffset: Int): CalendarDate

/** Device UTC offset in hours (e.g. Tashkent = 5.0), DST-aware. */
expect fun deviceUtcOffsetHours(): Double

/**
 * IANA timezone id for the device, e.g. "Asia/Tashkent".
 *
 * Travels with every sync event because a "day" is the unit almost every feature
 * aggregates by — prayers per day, dhikr per day, streaks — and the server stores UTC.
 * Without the zone it cannot tell whether a 21:30 Tashkent event belongs to that day
 * or, in UTC, to the one before.
 */
expect fun deviceTimeZoneId(): String
