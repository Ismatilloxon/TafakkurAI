package com.example.tafakkurai.gratitude

import com.example.tafakkurai.spiritual.dayNumberOf
import com.example.tafakkurai.spiritual.streakLength
import kotlinx.serialization.Serializable

/**
 * Gratitude (Shukr) domain — categories, moods, emotions, prompts, guided journeys
 * and the pure statistics behind the insights screens.
 *
 * Nothing here touches the database, the network or Compose, so every rule is unit
 * testable and the same vocabulary is shared by the local store, the sync payloads
 * and the backend (`internal/gratitude/domain.go` mirrors these keys).
 */

// ── Categories ───────────────────────────────────────────────────────────────

/** [color] is a token name resolved against the active palette via `TfColors.named`. */
data class GratCategory(val key: String, val label: String, val icon: String, val color: String)

val GRAT_CATEGORIES = listOf(
    GratCategory("people", "People", "users", "rose"),
    GratCategory("nature", "Nature", "leaf", "green"),
    GratCategory("home", "Home", "home", "cyan"),
    GratCategory("work", "Work", "briefcase", "gold"),
    GratCategory("learning", "Learning", "bookOpen", "purple"),
    GratCategory("mind", "Mind", "brain", "cyan"),
    GratCategory("health", "Health", "activity", "green"),
    GratCategory("moments", "Small moments", "sparkles", "gold"),
    GratCategory("faith", "Faith", "moonStars", "warn"),
    GratCategory("wins", "Achievements", "target", "teal"),
    GratCategory("hard", "Difficult", "heart", "danger"),
)

const val DEFAULT_CATEGORY = "moments"

fun categoryOf(key: String?): GratCategory =
    GRAT_CATEGORIES.firstOrNull { it.key == key } ?: GRAT_CATEGORIES.first { it.key == DEFAULT_CATEGORY }

// ── Moods ────────────────────────────────────────────────────────────────────

/** [curve] drives the mouth curvature of the drawn mood face (0.5 = flat). */
data class GratMood(val key: String, val label: String, val color: String, val curve: Float)

val GRAT_MOODS = listOf(
    GratMood("low", "Low", "cyan", 0.30f),
    GratMood("difficult", "Difficult", "purple", 0.42f),
    GratMood("okay", "Okay", "fg2", 0.50f),
    GratMood("good", "Good", "green", 0.63f),
    GratMood("wonderful", "Wonderful", "gold", 0.74f),
)

fun moodOf(key: String?): GratMood? = GRAT_MOODS.firstOrNull { it.key == key }

/** Index in [GRAT_MOODS], or -1 when nothing was logged. The weekly chart plots it. */
fun moodIndex(key: String?): Int = GRAT_MOODS.indexOfFirst { it.key == key }

val GRAT_EMOTIONS = listOf(
    "Peaceful", "Happy", "Loved", "Hopeful", "Calm",
    "Excited", "Proud", "Connected", "Blessed", "Grateful",
)

val GRAT_PROMPTS = listOf(
    "What is one small thing that made today better?",
    "What made you smile today?",
    "Who made your day better?",
    "What is something you often take for granted?",
    "What small moment felt meaningful today?",
    "What are you grateful for about yourself?",
    "What made today easier?",
    "What is something beautiful you noticed today?",
    "What comfort did you find on a difficult day?",
    "What is a lesson today quietly offered you?",
)

/** Composer modes: one line, three things, or free reflection. */
enum class GratMode(val id: String, val label: String) {
    QUICK("quick", "Quick"),
    THREE("three", "3 things"),
    REFLECT("reflect", "Reflect");

    companion object {
        fun from(id: String?): GratMode = entries.firstOrNull { it.id == id } ?: QUICK
    }
}

// ── Journeys ─────────────────────────────────────────────────────────────────

data class GratChallenge(val id: String, val title: String, val sub: String, val total: Int, val days: List<String>)

val GRAT_CHALLENGES = listOf(
    GratChallenge(
        id = "c7",
        title = "7-Day Gratitude Journey",
        sub = "A gentle week of noticing",
        total = 7,
        days = listOf(
            "Small things", "People", "Yourself", "Nature",
            "Growth", "Difficult moments", "Looking back",
        ),
    ),
    GratChallenge("c30", "30-Day Gratitude Journey", "Deepen the habit", 30, emptyList()),
)

data class GratChallengeProgress(val challenge: GratChallenge, val joined: Boolean, val doneDays: Set<Int>) {
    private val validDoneDays: Set<Int> get() = doneDays.filterTo(mutableSetOf()) { it in 1..challenge.total }
    val done: Int get() = validDoneDays.size
    val fraction: Float get() = if (challenge.total == 0) 0f else done.toFloat() / challenge.total
    /** 1-based index of the day the user is on now, capped at the journey length. */
    val currentDay: Int get() = (1..challenge.total).firstOrNull { it !in validDoneDays } ?: challenge.total
}

fun gratitudeChallengeOf(id: String): GratChallenge? = GRAT_CHALLENGES.firstOrNull { it.id == id }

fun isValidChallengeDay(challengeId: String, day: Int): Boolean =
    gratitudeChallengeOf(challengeId)?.let { day in 1..it.total } == true

// ── Entry ────────────────────────────────────────────────────────────────────

/**
 * One journal entry. [id] is client-generated and is what the backend stores as
 * `client_id`, so re-sending an entry after a failed request updates the same row
 * instead of duplicating it.
 */
@Serializable
data class GratEntry(
    val id: String,
    val mode: String = GratMode.QUICK.id,
    val text: String,
    val items: List<String> = emptyList(),
    val why: String? = null,
    val category: String = DEFAULT_CATEGORY,
    val mood: String? = null,
    val emotions: List<String> = emptyList(),
    val photoCount: Int = 0,
    val hasAudio: Boolean = false,
    val hasVideo: Boolean = false,
    val locationLabel: String? = null,
    val favourite: Boolean = false,
    val entryDate: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val serverId: String? = null,
)

/**
 * User-authored fields before persistence. Keeping cleanup and vocabulary validation in
 * the domain makes manual composition, AI-assisted drafts, imports and edits obey the
 * same rules without depending on a particular screen.
 */
data class GratitudeDraft(
    val mode: GratMode = GratMode.QUICK,
    val text: String = "",
    val items: List<String> = emptyList(),
    val why: String? = null,
    val category: String? = null,
    val mood: String? = null,
    val emotions: List<String> = emptyList(),
)

/** Returns null when the draft has no user-authored content. */
fun normalizeGratitudeDraft(draft: GratitudeDraft): GratitudeDraft? {
    val items = draft.items.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .take(3)
        .toList()
    val text = when (draft.mode) {
        GratMode.THREE -> items.joinToString(" · ")
        else -> draft.text.trim()
    }
    if (text.isBlank()) return null

    val emotions = draft.emotions.mapNotNull { candidate ->
        GRAT_EMOTIONS.firstOrNull { it.equals(candidate.trim(), ignoreCase = true) }
    }.distinct()

    return draft.copy(
        text = text,
        items = if (draft.mode == GratMode.THREE) items else emptyList(),
        why = draft.why?.trim()?.takeIf(String::isNotEmpty),
        category = categoryOf(draft.category).key,
        mood = moodOf(draft.mood)?.key,
        emotions = emotions,
    )
}

/** A pulled row may only replace an equal-or-older local version. */
fun shouldApplyRemoteEntry(local: GratEntry?, remote: GratEntry): Boolean =
    local == null || remote.updatedAt >= local.updatedAt

// ── Statistics (pure) ────────────────────────────────────────────────────────

data class GratStats(
    val streak: Int = 0,
    val longest: Int = 0,
    val total: Int = 0,
    val monthDays: Int = 0,
    val week: List<Boolean> = List(7) { false }, // oldest → today
)

/**
 * Derives the streak card from the distinct days the user wrote on.
 *
 * The current streak reuses [streakLength], so "yesterday but not yet today" still
 * counts — the day is not over, and a journal should never punish someone at 09:00.
 */
fun computeGratStats(activeDays: Collection<String>, total: Int, todayKey: String): GratStats {
    if (activeDays.isEmpty()) return GratStats(total = total)
    // Sorted distinct day numbers. `toSortedSet` is JVM-only — this stays common code.
    val nums = activeDays.mapNotNull(::dayNumberOf).distinct().sorted()
    val today = dayNumberOf(todayKey) ?: return GratStats(total = total)

    var longest = 1
    var run = 1
    var previous: Long? = null
    for (n in nums) {
        if (previous != null && n == previous + 1) run++ else run = 1
        if (run > longest) longest = run
        previous = n
    }

    val monthPrefix = todayKey.substring(0, 7) // "yyyy-MM"
    return GratStats(
        streak = streakLength(activeDays, todayKey),
        longest = longest,
        total = total,
        monthDays = activeDays.distinct().count { it.startsWith(monthPrefix) },
        week = (0..6).map { day -> nums.any { it == today - 6 + day } },
    )
}

data class GratCategoryShare(val key: String, val label: String, val count: Int, val percent: Int)

/** Top [limit] categories by share, high → low, ties broken by key so it is stable. */
fun categoryBreakdown(counts: Map<String, Int>, limit: Int = 5): List<GratCategoryShare> {
    val total = counts.values.sum()
    if (total == 0) return emptyList()
    return counts.entries
        .map { (key, count) ->
            GratCategoryShare(
                key = key,
                label = categoryOf(key).label,
                count = count,
                percent = ((count.toFloat() / total) * 100f + 0.5f).toInt(),
            )
        }
        .sortedWith(compareByDescending<GratCategoryShare> { it.count }.thenBy { it.key })
        .take(limit)
}

/**
 * The short reflection line on the insights screen. Derived from the user's own
 * entries rather than generated: it costs nothing, cannot hallucinate, and reads
 * identically offline.
 */
fun gratReflection(cats: List<GratCategoryShare>, entries: Int): String = when {
    entries == 0 -> "Your journal is still empty. One line today is enough to begin."
    cats.isEmpty() -> "You have been writing steadily. Keep noticing the small things."
    else -> when (cats.first().key) {
        "people" -> "You often mention the people close to you. Relationships seem to be one of the strongest sources of gratitude in your journal this month."
        "faith" -> "Much of your gratitude returns to faith and quiet worship — a steady anchor running through the month."
        "learning" -> "Learning shows up again and again. Growth appears to be where you find most of your gratitude right now."
        "nature", "moments" -> "Small moments and the world around you carry most of your gratitude — you notice what many people walk past."
        "hard" -> "You have been finding gratitude inside difficult days. That is the harder kind, and you have done it ${cats.first().count} times."
        else -> "Most of your gratitude this month gathers around ${cats.first().label.lowercase()}."
    }
}

/** Free-text + category filter, matching the backend's ILIKE search field for field. */
fun searchEntries(
    entries: List<GratEntry>,
    query: String,
    category: String?,
    categoryLabel: (String) -> String = { categoryOf(it).label },
): List<GratEntry> {
    val q = query.trim().lowercase()
    return entries.filter { e ->
        val okCategory = category == null || e.category == category
        val okQuery = q.isEmpty() ||
            e.text.lowercase().contains(q) ||
            (e.why?.lowercase()?.contains(q) == true) ||
            e.emotions.any { it.lowercase().contains(q) } ||
            categoryLabel(e.category).lowercase().contains(q)
        okCategory && okQuery
    }
}

// ── Settings ─────────────────────────────────────────────────────────────────

data class GratSettings(
    val reminderSlot: String = "evening", // off | morning | afternoon | evening
    val reminderTime: String = "19:30",
    val faceId: Boolean = false,
    val appLock: Boolean = false,
    val hidePreview: Boolean = true,
    val cloudSync: Boolean = true,
)

val REMINDER_SLOTS = listOf(
    Triple("morning", "sunrise", "Morning"),
    Triple("afternoon", "sun", "Afternoon"),
    Triple("evening", "sunset", "Evening"),
)
