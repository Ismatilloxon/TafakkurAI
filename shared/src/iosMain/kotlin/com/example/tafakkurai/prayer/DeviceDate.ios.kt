package com.example.tafakkurai.prayer

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.localTimeZone
import platform.Foundation.secondsFromGMT

actual fun deviceDate(dayOffset: Int): CalendarDate {
    val cal = NSCalendar.currentCalendar
    val date = NSDate.dateWithTimeIntervalSinceNow(dayOffset * 86_400.0)
    val comps = cal.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        date,
    )
    return CalendarDate(comps.year.toInt(), comps.month.toInt(), comps.day.toInt())
}

actual fun deviceUtcOffsetHours(): Double =
    NSTimeZone.localTimeZone.secondsFromGMT / 3600.0

actual fun deviceTimeZoneId(): String = platform.Foundation.NSTimeZone.localTimeZone.name
