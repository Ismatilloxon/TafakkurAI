package com.example.tafakkurai.balance

import com.example.tafakkurai.adhkar.AdhkarCategory
import com.example.tafakkurai.adhkar.AdhkarStore
import com.example.tafakkurai.dhikr.DhikrStore
import com.example.tafakkurai.gratitude.GratitudeStore
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.prayer.PrayerMark
import com.example.tafakkurai.prayer.PrayerMarkStore
import com.example.tafakkurai.quran.QuranProgressStore
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.sync.RecordedActivity

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
    private val activity: ActivityLog? = null,
    private val localDate: () -> String = ::deviceDateKey,
) {
    fun current(): List<BalanceScore> = computeBalance(input())

    fun input(): BalanceInput {
        val marks = PRAYER_KEYS.map { prayers.stateOf(it) }
        val stats = gratitude.stats()
        val date = localDate()
        val connected = activity?.todayPlan(date).orEmpty().filter { it.completed }
        val educationalActivities = activity?.events?.value
            ?.asSequence()
            ?.filter { it.localDate == date && it.contributesToEducationalBalance() }
            // Activity ids are idempotency keys. Counting them once keeps a replay or
            // remote merge from inflating the radar.
            ?.distinctBy { it.id }
            ?.count()
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
            educationalActivitiesToday = educationalActivities,
            spiritualActivitiesToday = connected.count { it.aspect == "spiritual" }.takeIf { it > 0 },
            physicalActivitiesToday = connected.count { it.aspect == "physical" }.takeIf { it > 0 },
            socialActivitiesToday = connected.count { it.aspect == "social" }.takeIf { it > 0 },
            financialActivitiesToday = connected.count { it.aspect == "financial" }.takeIf { it > 0 },
        )
    }

    private companion object {
        val PRAYER_KEYS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
    }
}

/**
 * Explicit allow-list of meaningful, persisted learning facts. Presentation events, opening a
 * screen, and mutable plan rows cannot affect balance. Imports count once as the recorded import
 * action; their claimed card count is deliberately not trusted as extra activity.
 */
private fun RecordedActivity.contributesToEducationalBalance(): Boolean = when (feature) {
    "learn", "learning" -> type in LEARNING_BALANCE_EVENT_TYPES
    "hifz" -> type == "ayah_reviewed"
    "flashcards" -> type == "card_reviewed" || type == "cards_imported"
    else -> false
}

private val LEARNING_BALANCE_EVENT_TYPES = setOf(
    "lesson_started",
    "lesson_completed",
    "quiz_completed",
)
