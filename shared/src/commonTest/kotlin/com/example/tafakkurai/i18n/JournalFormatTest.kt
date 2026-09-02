package com.example.tafakkurai.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class JournalFormatTest {

    @Test
    fun englishAndUzbekJournalUnitsUseNaturalSingularAndPluralLabels() {
        val english = Strings.of(Language.ENGLISH)
        assertEquals("entry", english.journalEntriesUnit(1))
        assertEquals("entries", english.journalEntriesUnit(2))
        assertEquals("day in 7", english.journalActiveDaysUnit(1))
        assertEquals("day streak", english.journalStreakUnit(7))

        val uzbek = Strings.of(Language.UZBEK_LATIN)
        assertEquals("ta qayd", uzbek.journalEntriesUnit(1))
        assertEquals("ta qayd", uzbek.journalEntriesUnit(12))
        assertEquals("kun ketma-ket", uzbek.journalStreakUnit(3))
    }

    @Test
    fun russianJournalUnitsFollowOneFewManyGrammar() {
        val russian = Strings.of(Language.RUSSIAN)

        assertEquals("запись", russian.journalEntriesUnit(1))
        assertEquals("записи", russian.journalEntriesUnit(2))
        assertEquals("записей", russian.journalEntriesUnit(5))
        assertEquals("записей", russian.journalEntriesUnit(11))
        assertEquals("запись", russian.journalEntriesUnit(21))
        assertEquals("дня подряд", russian.journalStreakUnit(22))
    }

    @Test
    fun persistedDateKeysRenderWithoutLeakingRawFormattingIntoNormalDates() {
        val english = Strings.of(Language.ENGLISH)

        assertEquals("Today", english.formatJournalDate("2026-08-29", "2026-08-29"))
        assertEquals("Yesterday", english.formatJournalDate("2026-08-28", "2026-08-29"))
        assertEquals("August 27, 2026", english.formatJournalDate("2026-08-27", "2026-08-29"))
        assertEquals("not-a-date", english.formatJournalDate("not-a-date", "2026-08-29"))
    }
}
