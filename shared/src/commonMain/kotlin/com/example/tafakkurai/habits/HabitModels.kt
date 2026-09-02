package com.example.tafakkurai.habits

import com.example.tafakkurai.spiritual.daysFromCivil
import com.example.tafakkurai.spiritual.parseDateKey

/**
 * The Habits domain: vocabulary, the category catalogue, and the four row types.
 *
 * These models are the sync payload. Every property below maps to a column in `Habit.sq`,
 * `HabitEntry.sq`, `HabitNote.sq` or `HabitAchievement.sq`, and the sync platform builds
 * its JSON straight from those columns — so the property names here, the column names
 * there and the json tags in `backend/internal/habits/domain.go` are one contract in three
 * places. Renaming any one of them without the other two does not fail loudly; the field
 * simply stops arriving.
 *
 * Enums carry an [id] string because that id is what is persisted. An unknown id decodes
 * to the nearest sane default rather than throwing: a row written by a newer build must
 * still open on an older one.
 */

// ── vocabulary ───────────────────────────────────────────────────────────────

enum class HabitSlot(val id: String, val labelKey: String, val icon: String) {
    MORNING("morning", "habits.slot.morning", "sunrise"),
    AFTERNOON("afternoon", "habits.slot.afternoon", "sun"),

    /** Not a time of day — the explicit "no particular moment" bucket. */
    ANYTIME("anytime", "habits.slot.anytime", "repeat"),
    EVENING("evening", "habits.slot.evening", "sunset");

    companion object {
        fun from(id: String?): HabitSlot = entries.firstOrNull { it.id == id } ?: ANYTIME
    }
}

enum class HabitDifficulty(val id: String, val labelKey: String, val dots: Int) {
    EASY("easy", "habits.difficulty.easy", 1),
    MEDIUM("medium", "habits.difficulty.medium", 2),
    HARD("hard", "habits.difficulty.hard", 3);

    companion object {
        fun from(id: String?): HabitDifficulty = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class HabitGoalType(val id: String, val labelKey: String) {
    CHECK("check", "habits.goal_type.simple"),
    COUNT("count", "habits.goal_type.count"),
    DURATION("duration", "habits.goal_type.duration");

    companion object {
        fun from(id: String?): HabitGoalType = entries.firstOrNull { it.id == id } ?: CHECK
    }
}

enum class HabitFrequency(val id: String, val labelKey: String) {
    DAILY("daily", "habits.frequency.daily"),
    WEEKDAYS("weekdays", "habits.frequency.weekdays"),
    CUSTOM("custom", "habits.frequency.custom");

    companion object {
        fun from(id: String?): HabitFrequency = entries.firstOrNull { it.id == id } ?: DAILY
    }
}

enum class HabitLifecycle(val id: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    ARCHIVED("archived");

    companion object {
        fun from(id: String?): HabitLifecycle = entries.firstOrNull { it.id == id } ?: ACTIVE
    }
}

/**
 * What a day says about a habit.
 *
 * There is no MISSED: a miss is the absence of a row. That is what lets "undo" be a
 * delete, and it is why "never tried" and "tried and undone" cannot drift apart.
 */
enum class HabitStatus(val id: String) {
    DONE("done"),
    SKIPPED("skipped"),
    PARTIAL("partial");

    companion object {
        fun from(id: String?): HabitStatus? = entries.firstOrNull { it.id == id }
    }
}

// ── categories ───────────────────────────────────────────────────────────────

/**
 * [color] is a palette token, never a hex value: the app has seven themes and the same
 * habit has to read as "gold" in all of them. [axis] folds the category onto one of the
 * five balance-radar spokes on the statistics screen.
 */
data class HabitCategory(
    val id: String,
    val labelKey: String,
    val icon: String,
    val color: String,
    val axis: String,
)

const val AXIS_ISLAM = "Islam"
const val AXIS_HEALTH = "Health"
const val AXIS_LEARN = "Learn"
const val AXIS_FOCUS = "Focus"
const val AXIS_REST = "Rest"

/** Five, because more than five spokes is unreadable at phone width. */
val HABIT_RADAR_AXES = listOf(AXIS_ISLAM, AXIS_HEALTH, AXIS_LEARN, AXIS_FOCUS, AXIS_REST)

val HABIT_CATEGORIES: List<HabitCategory> = listOf(
    HabitCategory("islam", "habits.category.islam", "moon", "gold", AXIS_ISLAM),
    HabitCategory("programming", "habits.category.programming", "terminal", "cyan", AXIS_FOCUS),
    HabitCategory("english", "habits.category.english", "languages", "purple", AXIS_LEARN),
    HabitCategory("reading", "habits.category.reading", "book", "teal", AXIS_LEARN),
    HabitCategory("workout", "habits.category.workout", "dumbbell", "rose", AXIS_HEALTH),
    HabitCategory("health", "habits.category.health", "heart", "green", AXIS_HEALTH),
    HabitCategory("sleep", "habits.category.sleep", "bedDouble", "purple", AXIS_REST),
    HabitCategory("mindfulness", "habits.category.mindfulness", "leaf", "green", AXIS_REST),
    HabitCategory("study", "habits.category.study", "graduationCap", "cyan", AXIS_LEARN),
    HabitCategory("family", "habits.category.family", "users", "rose", AXIS_REST),
    HabitCategory("business", "habits.category.business", "briefcase", "gold", AXIS_FOCUS),
    HabitCategory("custom", "habits.category.custom", "sparkles", "teal", AXIS_FOCUS),
)

const val DEFAULT_HABIT_CATEGORY = "custom"

private val categoriesById = HABIT_CATEGORIES.associateBy { it.id }

/** Never null: an unknown id (a newer build's category) falls back to Custom. */
fun habitCategory(id: String?): HabitCategory =
    categoriesById[id] ?: categoriesById.getValue(DEFAULT_HABIT_CATEGORY)

/** The icon grid in Create/Edit. Superset of the category icons plus common alternatives. */
val HABIT_ICONS = listOf(
    "moon", "book", "terminal", "languages", "dumbbell", "heart",
    "bedDouble", "leaf", "graduationCap", "users", "briefcase", "sunrise",
    "coffee", "activity", "penLine", "target", "repeat", "bookOpen",
    "zap", "sparkles",
)

/** The colour swatches in Create/Edit — palette tokens, resolved per theme. */
val HABIT_COLORS = listOf("teal", "gold", "green", "cyan", "purple", "rose")

// ── rows ─────────────────────────────────────────────────────────────────────

/**
 * A habit's definition.
 *
 * Note what is absent: streak, longest and completion rate. They are derived from entries
 * by [HabitMath] on every read. A stored streak goes wrong the first time an entry syncs
 * out of order, and once overwritten there is nothing left to recompute it from.
 */
data class Habit(
    val id: String,
    val name: String,
    val description: String = "",
    val categoryId: String = DEFAULT_HABIT_CATEGORY,
    val colorKey: String = "teal",
    val icon: String = "sparkles",
    val slot: HabitSlot = HabitSlot.ANYTIME,
    val timeLocal: String = "",
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "",
    val smartTiming: Boolean = false,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    /** 0 = Monday … 6 = Sunday. Only meaningful when [frequency] is CUSTOM. */
    val weekdays: List<Int> = emptyList(),
    val difficulty: HabitDifficulty = HabitDifficulty.MEDIUM,
    val goalType: HabitGoalType = HabitGoalType.CHECK,
    val goalTarget: Int = 1,
    val goalUnit: String = "",
    val motivation: String = "",
    val state: HabitLifecycle = HabitLifecycle.ACTIVE,
    val pausedUntil: String = "",
    val sortIndex: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val category: HabitCategory get() = habitCategory(categoryId)

    /** Is this habit due on [dateKey]? Nothing before creation is ever due. */
    fun scheduledOn(dateKey: String): Boolean {
        val day = dayNumberOfKey(dateKey) ?: return false
        if (createdAt > 0) {
            val created = dayNumberOfMillis(createdAt)
            if (day < created) return false
        }
        return when (frequency) {
            HabitFrequency.DAILY -> true
            HabitFrequency.WEEKDAYS -> weekdayIndexOf(day) <= 4
            HabitFrequency.CUSTOM -> weekdays.contains(weekdayIndexOf(day))
        }
    }

    /**
     * Which of the four Statistics rows this habit belongs to. Read from the clock time
     * when there is one, and from the slot when there is not.
     */
    val timeBucket: String
        get() {
            val hour = timeLocal.substringBefore(':').toIntOrNull()
            if (hour != null) {
                return when {
                    hour < 11 -> "morning"
                    hour < 17 -> "midday"
                    hour < 21 -> "evening"
                    else -> "night"
                }
            }
            return when (slot) {
                HabitSlot.MORNING -> "morning"
                HabitSlot.AFTERNOON -> "midday"
                HabitSlot.EVENING -> "evening"
                HabitSlot.ANYTIME -> "midday"
            }
        }
}

/**
 * One habit on one day. [id] is derived — see [habitEntryId] — which is what makes a
 * completion idempotent on (habit, day) locally, in the outbox and on the server.
 */
data class HabitEntry(
    val id: String,
    val habitId: String,
    val dateKey: String,
    val status: HabitStatus,
    val value: Int = 0,
    val target: Int = 1,
    val skipReason: String = "",
    val completedAt: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** The single place the derived key is built. The Go backend has the same one-liner. */
fun habitEntryId(habitId: String, dateKey: String): String = "$habitId:$dateKey"

data class HabitNote(
    val id: String,
    val habitId: String,
    val dateKey: String,
    val text: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Per-user unlock state. `unlockedAt == 0` means still locked. */
data class HabitAchievementState(
    val id: String,
    val unlockedAt: Long = 0,
    val progress: Float = 0f,
    val updatedAt: Long = 0,
)

// ── templates (the empty state) ──────────────────────────────────────────────

data class HabitTemplate(
    val nameKey: String,
    val categoryId: String,
    val slot: HabitSlot,
    val timeLocal: String = "",
    val goalType: HabitGoalType = HabitGoalType.CHECK,
    val goalTarget: Int = 1,
    val goalUnit: String = "",
    val difficulty: HabitDifficulty = HabitDifficulty.MEDIUM,
)

val HABIT_TEMPLATES = listOf(
    HabitTemplate("habits.template.morning_adhkar", "islam", HabitSlot.MORNING, "05:10", difficulty = HabitDifficulty.EASY),
    HabitTemplate("habits.template.read_pages", "reading", HabitSlot.EVENING, "21:00", HabitGoalType.COUNT, 20, "pg"),
    HabitTemplate("habits.template.workout", "workout", HabitSlot.MORNING, "07:00", HabitGoalType.DURATION, 45, "min", HabitDifficulty.HARD),
    HabitTemplate("habits.template.code_hour", "programming", HabitSlot.MORNING, "09:00", HabitGoalType.DURATION, 60, "min", HabitDifficulty.HARD),
    HabitTemplate("habits.template.drink_water", "health", HabitSlot.ANYTIME, "", HabitGoalType.COUNT, 8, "glass", HabitDifficulty.EASY),
    HabitTemplate("habits.template.sleep_by", "sleep", HabitSlot.EVENING, "23:00"),
)

/** Reasons offered by the Skip sheet. Ordered by how often they are the true one. */
val HABIT_SKIP_REASON_KEYS = listOf(
    "habits.skip.reason.rest", "habits.skip.reason.travel", "habits.skip.reason.unwell",
    "habits.skip.reason.busy", "habits.skip.reason.ramadan",
)

/** Durations offered by the Pause sheet. `days = 0` means "until I resume". */
val HABIT_PAUSE_OPTIONS = listOf(
    3 to "habits.pause.option.3_days",
    7 to "habits.pause.option.1_week",
    14 to "habits.pause.option.2_weeks",
    0 to "habits.pause.option.until_resume",
)

/** Quick-pick times in Create/Edit and the Reminder sheet. */
val HABIT_TIME_CHOICES = listOf("05:00", "06:45", "07:00", "09:00", "12:00", "18:00", "21:00", "22:00")

// ── civil-date helpers ───────────────────────────────────────────────────────
//
// Integer day arithmetic rather than a date library, matching `spiritual/DateKeys.kt`:
// "consecutive day" becomes plain subtraction, and no DST transition can make a streak
// gain or lose a day. The project deliberately has no kotlinx-datetime dependency.

/** Day number for a "yyyy-MM-dd" key, or null when it does not parse. */
fun dayNumberOfKey(key: String): Long? =
    parseDateKey(key)?.let { (y, m, d) -> daysFromCivil(y, m, d) }

/** Day number for an epoch-millis instant, using UTC — the same basis as [daysFromCivil]. */
fun dayNumberOfMillis(millis: Long): Long = floorDiv(millis, 86_400_000L)

private fun floorDiv(a: Long, b: Long): Long {
    val q = a / b
    return if (a % b != 0L && (a xor b) < 0) q - 1 else q
}

/**
 * "yyyy-MM-dd" for a day number — the inverse of [daysFromCivil].
 *
 * Howard Hinnant's civil_from_days, the same algorithm `DateKeys.kt` inverts. Written out
 * rather than looped because a loop over years is O(years) and this is called once per
 * cell of a 371-day heatmap.
 */
fun dateKeyOf(dayNumber: Long): String {
    val z = dayNumber + 719_468L
    val era = (if (z >= 0) z else z - 146_096L) / 146_097L
    val doe = z - era * 146_097L                                  // [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365  // [0, 399]
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)             // [0, 365]
    val mp = (5 * doy + 2) / 153                                  // [0, 11], Mar = 0
    val d = doy - (153 * mp + 2) / 5 + 1                          // [1, 31]
    val m = if (mp < 10) mp + 3 else mp - 9                       // [1, 12]
    val year = if (m <= 2) y + 1 else y
    return "$year-${pad2(m.toInt())}-${pad2(d.toInt())}"
}

private fun pad2(n: Int): String = if (n < 10) "0$n" else "$n"

/** Monday-first weekday (0=Mon … 6=Sun). 1970-01-01 was a Thursday, hence the +3. */
fun weekdayIndexOf(dayNumber: Long): Int = ((dayNumber + 3).mod(7L)).toInt()

fun weekdayIndexOfKey(key: String): Int = dayNumberOfKey(key)?.let(::weekdayIndexOf) ?: 0

val WEEKDAY_INITIAL_KEYS = (1..7).map { "habits.weekday.$it.short" }
val WEEKDAY_NAME_KEYS = (1..7).map { "habits.weekday.$it.full" }
val MONTH_SHORT_KEYS = (1..12).map { "habits.month.$it.short" }
