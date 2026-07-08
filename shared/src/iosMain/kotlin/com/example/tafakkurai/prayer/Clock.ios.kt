package com.example.tafakkurai.prayer

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSDate

actual fun currentSecondsOfDay(): Int {
    val cal = NSCalendar.currentCalendar
    val comps = cal.components(
        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond,
        NSDate(),
    )
    return (comps.hour * 3600 + comps.minute * 60 + comps.second).toInt()
}
