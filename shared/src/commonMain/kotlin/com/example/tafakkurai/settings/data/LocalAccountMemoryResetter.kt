package com.example.tafakkurai.settings.data

import com.example.tafakkurai.adhkar.AdhkarStore
import com.example.tafakkurai.dhikr.DhikrStore
import com.example.tafakkurai.finance.domain.FinanceRepository
import com.example.tafakkurai.gratitude.GratitudeStore
import com.example.tafakkurai.habits.HabitStore
import com.example.tafakkurai.journal.domain.JournalRepository
import com.example.tafakkurai.learning.domain.QuranQuizRepository
import com.example.tafakkurai.prayer.PrayerMarkStore
import com.example.tafakkurai.quran.QuranProgressStore
import com.example.tafakkurai.quran.hifz.HifzPlanner
import com.example.tafakkurai.sleep.SleepStore
import com.example.tafakkurai.social.domain.SocialRepository
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.tasks.data.TaskStore

/**
 * Clears observable singleton snapshots after their SQL rows have been wiped. Without this,
 * a second account in the same process could briefly inherit the first account's UI state even
 * though the database itself was empty.
 */
class LocalAccountMemoryResetter(
    private val activity: ActivityLog,
    private val finance: FinanceRepository,
    private val social: SocialRepository,
    private val tasks: TaskStore,
    private val habits: HabitStore,
    private val gratitude: GratitudeStore,
    private val prayer: PrayerMarkStore,
    private val quran: QuranProgressStore,
    private val dhikr: DhikrStore,
    private val adhkar: AdhkarStore,
    private val sleep: SleepStore,
    private val journal: JournalRepository,
    private val hifzPlanner: HifzPlanner,
    private val quranQuizzes: QuranQuizRepository,
) {
    fun reloadFromEmptyDatabase() {
        activity.refresh()
        finance.refresh()
        social.refresh()
        tasks.refresh()
        habits.refresh()
        gratitude.reload()
        prayer.refresh()
        quran.reload()
        dhikr.reload()
        adhkar.reload()
        sleep.reload()
        journal.refresh()
        hifzPlanner.reload()
        quranQuizzes.refresh()
    }
}
