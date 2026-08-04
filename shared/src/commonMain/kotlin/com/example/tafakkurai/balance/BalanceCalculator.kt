package com.example.tafakkurai.balance

import com.example.tafakkurai.adhkar.AdhkarCategory
import com.example.tafakkurai.adhkar.AdhkarStore
import com.example.tafakkurai.dhikr.DhikrStore
import com.example.tafakkurai.gratitude.GratitudeStore
import com.example.tafakkurai.prayer.PrayerMark
import com.example.tafakkurai.prayer.PrayerMarkStore
import com.example.tafakkurai.quran.QuranProgressStore

/**
 * Reads the app's stores and produces the balance radar.
 *
 * Deliberately thin: every store it touches already keeps its state in Compose state, so a
 * screen that calls [current] inside composition recomposes the moment a prayer is marked,
 * a dhikr counted or an ayah read — which is the whole point of the radar.
 *
 * All arithmetic lives in [computeBalance]; this class only gathers.
 */
class BalanceCalculator(
    private val prayers: PrayerMarkStore,
    private val quran: QuranProgressStore,
    private val dhikr: DhikrStore,
    private val adhkar: AdhkarStore,
    private val gratitude: GratitudeStore,
) {
    fun current(): List<BalanceScore> = computeBalance(input())

    fun input(): BalanceInput {
        val marks = PRAYER_KEYS.map { prayers.stateOf(it) }
        val stats = gratitude.stats()
        return BalanceInput(
            prayersOnTime = marks.count { it == PrayerMark.ONTIME },
            prayersInJamaat = marks.count { it == PrayerMark.JAMAAT },
            dhikrProgress = dhikr.progress,
            dhikrStreak = dhikr.streak,
            adhkarProgress = AdhkarCategory.entries
                .map { adhkar.progressOf(it) }
                .let { if (it.isEmpty()) 0f else it.sum() / it.size },
            ayahsRead = quran.totalRead,
            surahsStarted = quran.startedSurahCount,
            gratitudeDaysThisWeek = stats.week.count { it },
            gratitudeStreak = stats.streak,
            // Flashcards live in their own database and are read separately by the caller
            // when a learning score is wanted; the radar works without them.
            cardsLearned = 0,
            cardsDue = 0,
        )
    }

    private companion object {
        val PRAYER_KEYS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
    }
}
