package com.example.tafakkurai.habits

import kotlin.math.min

/**
 * The badge catalogue and the rules that score it.
 *
 * Mirrors `backend/internal/habits/achievements.go`. The catalogue is code on both sides
 * because it is content — every user sees the same twelve, and a new badge ships with the
 * release whose copy describes it. Only per-user unlock state is data, and that lives in
 * `habitAchievement` so it syncs like anything else.
 */
data class HabitAchievement(
    val id: String,
    val nameKey: String,
    val subKey: String,
    val icon: String,
    val color: String,
    /** The threshold in words, shown on a locked badge so "70%" means something. */
    val goalKey: String,
)

val HABIT_ACHIEVEMENTS = listOf(
    HabitAchievement("first", "habits.achievement.first.name", "habits.achievement.first.detail", "sparkles", "teal", "habits.achievement.first.goal"),
    HabitAchievement("w7", "habits.achievement.w7.name", "habits.achievement.w7.detail", "flame", "gold", "habits.achievement.w7.goal"),
    HabitAchievement("w30", "habits.achievement.w30.name", "habits.achievement.w30.detail", "flame", "gold", "habits.achievement.w30.goal"),
    HabitAchievement("morning", "habits.achievement.morning.name", "habits.achievement.morning.detail", "sunrise", "gold", "habits.achievement.morning.goal"),
    HabitAchievement("perfectw", "habits.achievement.perfect_week.name", "habits.achievement.perfect_week.detail", "checkCircle", "green", "habits.achievement.perfect_week.goal"),
    HabitAchievement("w100", "habits.achievement.w100.name", "habits.achievement.w100.detail", "flame", "rose", "habits.achievement.w100.goal"),
    HabitAchievement("consist", "habits.achievement.consistency.name", "habits.achievement.consistency.detail", "target", "cyan", "habits.achievement.consistency.goal"),
    HabitAchievement("night", "habits.achievement.night.name", "habits.achievement.night.detail", "moon", "purple", "habits.achievement.night.goal"),
    HabitAchievement("perfectm", "habits.achievement.perfect_month.name", "habits.achievement.perfect_month.detail", "calendar", "green", "habits.achievement.perfect_month.goal"),
    HabitAchievement("nomiss", "habits.achievement.no_miss.name", "habits.achievement.no_miss.detail", "shieldCheck", "cyan", "habits.achievement.no_miss.goal"),
    HabitAchievement("w365", "habits.achievement.w365.name", "habits.achievement.w365.detail", "star", "gold", "habits.achievement.w365.goal"),
    HabitAchievement("legend", "habits.achievement.legend.name", "habits.achievement.legend.detail", "zap", "purple", "habits.achievement.legend.goal"),
)

/** Everything the catalogue is scored against — assembled once, then pure arithmetic. */
data class HabitAchievementFacts(
    val habitCount: Int = 0,
    /** Longest run any single habit has ever held. */
    val bestStreak: Int = 0,
    /** Longest run of days where every scheduled habit was done. */
    val perfectRun: Int = 0,
    val monthRate: Float = 0f,
    val morningDone: Int = 0,
    val eveningDone: Int = 0,
    val daysSinceMiss: Int = 0,
)

data class HabitAchievementView(
    val achievement: HabitAchievement,
    val unlocked: Boolean,
    val unlockedAt: Long,
    val progress: Float,
) {
    val id: String get() = achievement.id
}

object HabitAchievements {

    /**
     * Scores the catalogue and folds in what the user has already unlocked.
     *
     * Two rules that are easy to get wrong and matter a lot:
     *
     *  - An unlocked badge stays unlocked. Progress is recomputed on every read, and
     *    deleting a habit can legitimately drop today's numbers below yesterday's
     *    threshold. Re-locking something somebody earned is the most demoralising bug
     *    this screen can have.
     *  - "Legend" is scored against the other eleven, never itself, or it would be
     *    self-referential and unreachable.
     */
    fun evaluate(
        facts: HabitAchievementFacts,
        stored: Map<String, HabitAchievementState>,
        nowMillis: Long,
    ): List<HabitAchievementView> {
        val progress = mutableMapOf(
            "first" to ratio(facts.habitCount, 1),
            "w7" to ratio(facts.bestStreak, 7),
            "w30" to ratio(facts.bestStreak, 30),
            "w100" to ratio(facts.bestStreak, 100),
            "w365" to ratio(facts.bestStreak, 365),
            "morning" to ratio(facts.morningDone, 20),
            "night" to ratio(facts.eveningDone, 30),
            "perfectw" to ratio(facts.perfectRun, 7),
            "perfectm" to ratio(facts.perfectRun, 30),
            "consist" to clamp01(facts.monthRate / 0.9f),
            "nomiss" to ratio(facts.daysSinceMiss, 60),
        )

        val earned = HABIT_ACHIEVEMENTS.count { a ->
            a.id != "legend" && ((progress[a.id] ?: 0f) >= 1f || (stored[a.id]?.unlockedAt ?: 0L) > 0L)
        }
        progress["legend"] = ratio(earned, HABIT_ACHIEVEMENTS.size - 1)

        return HABIT_ACHIEVEMENTS.map { a ->
            val p = clamp01(progress[a.id] ?: 0f)
            val storedAt = stored[a.id]?.unlockedAt ?: 0L
            when {
                storedAt > 0L -> HabitAchievementView(a, true, storedAt, 1f)
                p >= 1f -> HabitAchievementView(a, true, nowMillis, 1f)
                else -> HabitAchievementView(a, false, 0L, p)
            }
        }
    }

    /** The badges that crossed the line in this evaluation — the only ones worth writing. */
    fun newlyUnlocked(
        views: List<HabitAchievementView>,
        stored: Map<String, HabitAchievementState>,
    ): List<HabitAchievementView> =
        views.filter { it.unlocked && (stored[it.id]?.unlockedAt ?: 0L) == 0L }

    private fun ratio(have: Int, want: Int): Float =
        if (want <= 0) 1f else clamp01(have.toFloat() / want)

    private fun clamp01(v: Float): Float = when {
        v.isNaN() || v < 0f -> 0f
        else -> min(1f, v)
    }
}
