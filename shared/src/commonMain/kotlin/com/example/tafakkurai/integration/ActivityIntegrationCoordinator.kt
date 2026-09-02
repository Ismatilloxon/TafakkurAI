package com.example.tafakkurai.integration

import com.example.tafakkurai.habits.HabitSlot
import com.example.tafakkurai.habits.HabitStore
import com.example.tafakkurai.i18n.Language
import com.example.tafakkurai.i18n.LanguageStore
import com.example.tafakkurai.i18n.Strings
import com.example.tafakkurai.learning.domain.QuranQuizRepository
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.sync.ActivityPlanProjection
import com.example.tafakkurai.sync.RecordedActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.jsonPrimitive

/**
 * Projects feature-owned ActivityEvents into connected habits.
 *
 * The source feature never reaches into HabitStore. It records its own state and one event;
 * this application-layer coordinator observes that event and applies the idempotent habit
 * projection. Replaying the full local stream is safe and repairs an interrupted projection.
 */
class ActivityIntegrationCoordinator(
    private val activity: ActivityLog,
    private val habits: HabitStore,
    private val languages: LanguageStore,
    private val quranQuizzes: QuranQuizRepository,
) {
    /** Long-lived collector owned by App's composition scope. */
    suspend fun run() {
        activity.events
            // Navigation/settings diagnostics cannot affect a plan, habit, or learning goal.
            // Filtering them before distinctUntilChanged prevents a harmless UI event from
            // replaying up to 1,000 facts and querying every retained date.
            .map { events -> events.filter(RecordedActivity::affectsConnectedSystem) }
            .distinctUntilChanged()
            .collect { events -> reconcile(events) }
    }

    /** Reconcile all retained dates, also used after an interface-language change. */
    fun reconcile() {
        reconcile(activity.events.value)
    }

    private fun reconcile(events: List<RecordedActivity>) {
        // Quiz availability is projected from immutable source events, not from the final
        // Today read model. Removing a Hifz plan later must not erase the fact that this exact
        // surah was studied, and replaying the stream only merges the two allowed sources.
        activity.learningScopeEvents().asSequence()
            .sortedWith(compareBy<RecordedActivity> { it.occurredAt }.thenBy { it.id })
            .mapNotNull(::quizSource)
            .forEach { (surahNumber, source) -> quranQuizzes.ensureAvailable(surahNumber, source) }

        val dates = events.mapTo(linkedSetOf()) { it.localDate }
        if (dates.isEmpty()) return
        for (date in dates.sorted()) {
            for (projection in activity.todayPlan(date)) {
                val spec = connectedHabitSpec(projection) ?: continue
                val currentStrings = Strings.of(languages.current)
                val translatedNames = Language.entries
                    .mapTo(linkedSetOf()) { spec.localizedName(Strings.of(it)) }
                if (habits.ensureConnectedHabit(
                    connectionKey = spec.key,
                    displayName = spec.localizedName(currentStrings),
                    knownTranslatedNames = translatedNames,
                    categoryId = spec.categoryId,
                    icon = spec.icon,
                    colorKey = spec.colorKey,
                    slot = spec.slot,
                ) == null) continue
                if (projection.completed) {
                    habits.completeConnectedHabit(spec.key, date)
                }
            }
        }
    }

}

private fun RecordedActivity.affectsConnectedSystem(): Boolean = feature in CONNECTED_FEATURES

private val CONNECTED_FEATURES = setOf(
    "gratitude", "hifz", "quran", "dhikr", "adhkar", "prayer", "sleep", "finance",
    "tasks", "productivity", "social", "thoughts", "journal", "tafakkur", "reflect",
    "flashcards", "learn", "learning", "sport", "physical",
)

private fun quizSource(event: RecordedActivity): Pair<Int, String>? {
    val source = when {
        event.feature == "quran" && event.type in QURAN_QUIZ_SOURCE_EVENTS -> "quran"
        event.feature == "hifz" && event.type in HIFZ_QUIZ_SOURCE_EVENTS -> "hifz"
        else -> return null
    }
    val surah = runCatching { event.properties["surah"]?.jsonPrimitive?.content }
        .getOrNull()
        ?.toIntOrNull()
        ?.takeIf { it in 1..114 }
        ?: return null
    return surah to source
}

private val QURAN_QUIZ_SOURCE_EVENTS = setOf("surah_started", "ayah_marked_read", "ayah_listened")
private val HIFZ_QUIZ_SOURCE_EVENTS = setOf("ayahs_enrolled", "ayah_reviewed")

internal data class ConnectedHabitSpec(
    val key: String,
    val titleKey: String,
    val categoryId: String,
    val icon: String,
    val colorKey: String,
    val slot: HabitSlot = HabitSlot.ANYTIME,
    val titleArgument: String? = null,
) {
    fun localizedName(strings: Strings): String = titleArgument?.let {
        strings.format(titleKey, it)
    } ?: strings[titleKey]
}

internal fun connectedHabitSpec(route: String): ConnectedHabitSpec? = CONNECTED_HABITS[route]

/** Learning intentions keep their exact surah boundary instead of sharing one generic habit. */
internal fun connectedHabitSpec(projection: ActivityPlanProjection): ConnectedHabitSpec? {
    val habitRoute = if (projection.route.startsWith("quran:quiz:")) "learn" else projection.route
    val base = connectedHabitSpec(habitRoute) ?: return null
    val surah = projection.titleArgument?.takeIf { value ->
        value.toIntOrNull()?.let { it in 1..114 } == true
    }
        ?: return base
    return when (projection.route) {
        "hifz" -> base.copy(
            key = "hifz:surah:$surah",
            titleKey = "habit.connected.hifz_surah",
            titleArgument = surah,
        )
        "quran" -> base.copy(
            key = "quran:surah:$surah",
            titleKey = "habit.connected.quran_surah",
            titleArgument = surah,
        )
        else -> base
    }
}

private val CONNECTED_HABITS = mapOf(
    "gratitude" to ConnectedHabitSpec(
        "gratitude", "habit.connected.gratitude", "mindfulness", "leaf", "gold", HabitSlot.EVENING,
    ),
    "hifz" to ConnectedHabitSpec(
        "hifz", "habit.connected.hifz", "islam", "bookOpen", "gold", HabitSlot.MORNING,
    ),
    "quran" to ConnectedHabitSpec(
        "quran", "habit.connected.quran", "islam", "bookOpen", "green", HabitSlot.MORNING,
    ),
    "dhikr" to ConnectedHabitSpec(
        "dhikr", "habit.connected.dhikr", "islam", "repeat", "teal", HabitSlot.ANYTIME,
    ),
    "adhkar" to ConnectedHabitSpec(
        "adhkar", "habit.connected.adhkar", "islam", "sunrise", "gold", HabitSlot.MORNING,
    ),
    "prayer" to ConnectedHabitSpec(
        "prayer", "habit.connected.prayer", "islam", "moonStars", "green", HabitSlot.ANYTIME,
    ),
    "sleep" to ConnectedHabitSpec(
        "sleep", "habit.connected.sleep", "sleep", "bedDouble", "purple", HabitSlot.EVENING,
    ),
    "finance" to ConnectedHabitSpec(
        "finance", "habit.connected.finance", "business", "lineChart", "gold",
    ),
    "social" to ConnectedHabitSpec(
        "social", "habit.connected.social", "family", "users", "rose",
    ),
    "focus" to ConnectedHabitSpec(
        "focus", "habit.connected.focus", "custom", "target", "teal",
    ),
    "tafakkur" to ConnectedHabitSpec(
        "tafakkur", "habit.connected.tafakkur", "mindfulness", "penLine", "green", HabitSlot.EVENING,
    ),
    "learn" to ConnectedHabitSpec(
        "learning", "habit.connected.learning", "study", "graduationCap", "cyan",
    ),
    "sport" to ConnectedHabitSpec(
        "sport", "habit.connected.sport", "workout", "dumbbell", "rose",
    ),
)
