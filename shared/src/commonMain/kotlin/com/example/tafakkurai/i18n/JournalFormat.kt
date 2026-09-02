package com.example.tafakkurai.i18n

import com.example.tafakkurai.spiritual.dayNumberOf
import com.example.tafakkurai.spiritual.parseDateKey

/** Locale-aware label for a persisted journal date key. */
fun Strings.formatJournalDate(entryDate: String, todayKey: String): String {
    val today = dayNumberOf(todayKey)
    val day = dayNumberOf(entryDate)
    if (today != null && day != null) {
        when (today - day) {
            0L -> return get("journal.date.today")
            1L -> return get("journal.date.yesterday")
        }
    }
    val parsed = parseDateKey(entryDate) ?: return entryDate
    val (year, month, dayOfMonth) = parsed
    return format("journal.date.full", dayOfMonth, get("date.month.$month"), year)
}

fun Strings.journalEntriesUnit(count: Int): String = journalPlural("journal.stats.entries", count)

fun Strings.journalActiveDaysUnit(count: Int): String = journalPlural("journal.stats.days", count)

fun Strings.journalStreakUnit(count: Int): String = journalPlural("journal.stats.streak", count)

/** Journal statistics need real locale grammar even though their number and unit are separate. */
private fun Strings.journalPlural(baseKey: String, rawCount: Int): String {
    val count = rawCount.coerceAtLeast(0)
    val suffix = when (language) {
        Language.ENGLISH -> if (count == 1) "one" else "many"
        Language.RUSSIAN -> {
            val mod10 = count % 10
            val mod100 = count % 100
            when {
                mod10 == 1 && mod100 != 11 -> "one"
                mod10 in 2..4 && mod100 !in 12..14 -> "few"
                else -> "many"
            }
        }
        // Uzbek nouns do not change after a number, but keeping the same three keys in
        // every catalog lets StringsTest continue proving exact key parity.
        Language.UZBEK_LATIN, Language.UZBEK_CYRILLIC -> "many"
    }
    return get("$baseKey.$suffix")
}
