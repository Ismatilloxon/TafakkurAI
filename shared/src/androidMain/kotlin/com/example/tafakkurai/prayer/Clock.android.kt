package com.example.tafakkurai.prayer

import java.util.Calendar

actual fun currentSecondsOfDay(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 3600 + c.get(Calendar.MINUTE) * 60 + c.get(Calendar.SECOND)
}
