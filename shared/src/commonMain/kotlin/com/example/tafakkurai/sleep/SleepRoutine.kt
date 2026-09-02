package com.example.tafakkurai.sleep

import kotlinx.serialization.Serializable

/**
 * Bedtime and morning routines.
 *
 * ### The rule this file exists to enforce
 *
 * **Nothing here is on by default and nothing here is required.** The Islamic steps live in
 * the same list as "dim the lights" and "put your phone across the room", carry no different
 * status in the data model, and a user who never enables them sees a complete, useful
 * wind-down routine with no religious content at all.
 *
 * That is a product decision, not a technical one: an app that switches on religious practice
 * for someone who did not ask has not respected them, and an app that treats faith as a
 * bolt-on module has not respected the people who did ask. Both groups get a first-class
 * routine, chosen by them.
 *
 * ### Why the steps carry their own evidence
 *
 * Each secular step names the mechanism it works through, so a user can judge it rather than
 * obey it. The religious steps cite their source for the same reason.
 */

/** Which routine a step belongs to. */
@Serializable
enum class RoutinePhase { BEDTIME, MORNING }

/**
 * A routine step.
 *
 * @param id stable across versions — it is the primary key of the completion record, so
 *  renaming one would silently reset a user's history.
 * @param optionalFaith true for the Islamic steps. Used only to group them behind a single
 *  opt-in toggle; it grants no special treatment anywhere else.
 * @param arabic present for the adhkar, so a user can read the original rather than only a
 *  translation. Rendered right-to-left by the UI.
 * @param estimatedSeconds drives the "your routine takes about N minutes" line, which is what
 *  stops a routine quietly growing into something nobody has time for.
 */
@Serializable
data class RoutineStep(
    val id: String,
    val phase: RoutinePhase,
    val icon: String,
    val colorToken: String,
    val estimatedSeconds: Int,
    val optionalFaith: Boolean = false,
    val arabic: String? = null,
    /** True when the localized catalog contains a translated meaning for this Arabic text. */
    val hasTranslation: Boolean = false,
    val source: String? = null,
    /** Deep link into the Qurʼan reader, when the step is a recitation. */
    val quranRoute: String? = null,
)

/** A step plus whether the user has done it today. */
@Serializable
data class RoutineStepState(
    val step: RoutineStep,
    val enabled: Boolean,
    val completed: Boolean,
)

/** Progress through one routine on one day. */
@Serializable
data class RoutineProgress(
    val phase: RoutinePhase,
    val dateKey: String,
    val steps: List<RoutineStepState>,
) {
    val enabledSteps: List<RoutineStepState> get() = steps.filter { it.enabled }
    val completedCount: Int get() = enabledSteps.count { it.completed }
    val totalCount: Int get() = enabledSteps.size
    val isComplete: Boolean get() = totalCount > 0 && completedCount == totalCount
    val fraction: Float get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount

    /** Estimated remaining time, for the "about 6 minutes left" line. */
    val remainingSeconds: Int
        get() = enabledSteps.filterNot { it.completed }.sumOf { it.step.estimatedSeconds }
}

/**
 * The step catalogue.
 *
 * Static content rather than a backend fetch: the bedtime routine has to work in aeroplane
 * mode at 23:40 in a hotel, which is exactly when a network call fails. Steps are added in
 * app releases; user choices about them sync.
 */
object SleepRoutineCatalog {

    // ───────────────────────────────────────────── bedtime · universal

    private val bedtimeUniversal = listOf(
        RoutineStep(
            id = "bed.winddown",
            phase = RoutinePhase.BEDTIME,
            icon = "moon",
            colorToken = "purple",
            estimatedSeconds = 30,
        ),
        RoutineStep(
            id = "bed.phone",
            phase = RoutinePhase.BEDTIME,
            icon = "lock",
            colorToken = "cyan",
            estimatedSeconds = 30,
        ),
        RoutineStep(
            id = "bed.temperature",
            phase = RoutinePhase.BEDTIME,
            icon = "leaf",
            colorToken = "green",
            estimatedSeconds = 60,
        ),
        RoutineStep(
            id = "bed.breathing",
            phase = RoutinePhase.BEDTIME,
            icon = "heart",
            colorToken = "rose",
            estimatedSeconds = 120,
        ),
        RoutineStep(
            id = "bed.tomorrow",
            phase = RoutinePhase.BEDTIME,
            icon = "penLine",
            colorToken = "gold",
            estimatedSeconds = 120,
        ),
    )

    // ───────────────────────────────────────────── bedtime · optional Islamic

    private val bedtimeFaith = listOf(
        RoutineStep(
            id = "bed.wudu",
            phase = RoutinePhase.BEDTIME,
            icon = "leaf",
            colorToken = "teal",
            estimatedSeconds = 180,
            optionalFaith = true,
            source = "Sahih al-Bukhari 247",
        ),
        RoutineStep(
            id = "bed.kursi",
            phase = RoutinePhase.BEDTIME,
            icon = "bookOpen",
            colorToken = "gold",
            estimatedSeconds = 60,
            optionalFaith = true,
            arabic = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ",
            hasTranslation = true,
            source = "Al-Baqarah 2:255",
            quranRoute = "surah:2",
        ),
        RoutineStep(
            id = "bed.three-quls",
            phase = RoutinePhase.BEDTIME,
            icon = "bookOpen",
            colorToken = "teal",
            estimatedSeconds = 120,
            optionalFaith = true,
            arabic = "قُلْ هُوَ اللَّهُ أَحَدٌ · قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ · قُلْ أَعُوذُ بِرَبِّ النَّاسِ",
            source = "Sahih al-Bukhari 5017",
            quranRoute = "surah:112",
        ),
        RoutineStep(
            id = "bed.baqarah-end",
            phase = RoutinePhase.BEDTIME,
            icon = "bookOpen",
            colorToken = "purple",
            estimatedSeconds = 90,
            optionalFaith = true,
            arabic = "آمَنَ الرَّسُولُ بِمَا أُنزِلَ إِلَيْهِ مِن رَّبِّهِ وَالْمُؤْمِنُونَ",
            source = "Al-Baqarah 2:285–286 · Sahih al-Bukhari 5009",
            quranRoute = "surah:2",
        ),
        RoutineStep(
            id = "bed.right-side",
            phase = RoutinePhase.BEDTIME,
            icon = "bedDouble",
            colorToken = "green",
            estimatedSeconds = 15,
            optionalFaith = true,
            source = "Sahih al-Bukhari 6314",
        ),
        RoutineStep(
            id = "bed.dua",
            phase = RoutinePhase.BEDTIME,
            icon = "moonStars",
            colorToken = "teal",
            estimatedSeconds = 30,
            optionalFaith = true,
            arabic = "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا",
            hasTranslation = true,
            source = "Sahih al-Bukhari 6324",
        ),
    )

    // ───────────────────────────────────────────── morning · universal

    private val morningUniversal = listOf(
        RoutineStep(
            id = "morning.light",
            phase = RoutinePhase.MORNING,
            icon = "sunrise",
            colorToken = "gold",
            estimatedSeconds = 600,
        ),
        RoutineStep(
            id = "morning.water",
            phase = RoutinePhase.MORNING,
            icon = "leaf",
            colorToken = "cyan",
            estimatedSeconds = 30,
        ),
        RoutineStep(
            id = "morning.no-snooze",
            phase = RoutinePhase.MORNING,
            icon = "zap",
            colorToken = "warn",
            estimatedSeconds = 15,
        ),
        RoutineStep(
            id = "morning.caffeine-delay",
            phase = RoutinePhase.MORNING,
            icon = "coffee",
            colorToken = "gold",
            estimatedSeconds = 15,
        ),
        RoutineStep(
            id = "morning.checkin",
            phase = RoutinePhase.MORNING,
            icon = "smile",
            colorToken = "rose",
            estimatedSeconds = 20,
        ),
    )

    // ───────────────────────────────────────────── morning · optional Islamic

    private val morningFaith = listOf(
        RoutineStep(
            id = "morning.wake-dua",
            phase = RoutinePhase.MORNING,
            icon = "sunrise",
            colorToken = "teal",
            estimatedSeconds = 30,
            optionalFaith = true,
            arabic = "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ",
            hasTranslation = true,
            source = "Sahih al-Bukhari 6312",
        ),
        RoutineStep(
            id = "morning.fajr",
            phase = RoutinePhase.MORNING,
            icon = "sunrise",
            colorToken = "teal",
            estimatedSeconds = 420,
            optionalFaith = true,
        ),
        RoutineStep(
            id = "morning.adhkar",
            phase = RoutinePhase.MORNING,
            icon = "sparkles",
            colorToken = "gold",
            estimatedSeconds = 300,
            optionalFaith = true,
            arabic = "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ",
            hasTranslation = true,
            source = "Sahih Muslim 2723",
        ),
    )

    /** Every step, both phases, faith steps included. Filtering is the caller's job. */
    val all: List<RoutineStep> =
        bedtimeUniversal + bedtimeFaith + morningUniversal + morningFaith

    fun forPhase(phase: RoutinePhase): List<RoutineStep> = all.filter { it.phase == phase }

    fun byId(id: String): RoutineStep? = all.firstOrNull { it.id == id }

    /**
     * The steps enabled by default for a fresh install.
     *
     * Universal steps only. A new user's routine is short, secular and complete; the Islamic
     * steps appear as an offer in settings, never as something to switch off.
     */
    val defaultEnabledIds: Set<String> =
        (bedtimeUniversal + morningUniversal).map { it.id }.toSet()

    /** Ids of every faith step, so one toggle can enable or disable the whole group. */
    val faithStepIds: Set<String> =
        (bedtimeFaith + morningFaith).map { it.id }.toSet()
}

/**
 * User settings for the module.
 *
 * Held as one serialisable object in the preference store rather than as a column per flag,
 * because it changes shape most releases and a JSON blob migrates by tolerating missing keys.
 */
@Serializable
data class SleepSettings(
    val goals: SleepGoals = SleepGoals(),
    val enabledStepIds: Set<String> = SleepRoutineCatalog.defaultEnabledIds,
    /** The single opt-in that reveals every Islamic step. Off on a fresh install. */
    val islamicRoutineEnabled: Boolean = false,

    // notifications — each independently controllable
    val bedtimeReminder: Boolean = true,
    val windDownReminder: Boolean = true,
    val phoneAwayReminder: Boolean = false,
    val morningSummary: Boolean = true,
    val consistencyNudge: Boolean = true,
    val recoveryWarning: Boolean = true,
    val weekendNudge: Boolean = false,

    // integrations
    val healthSyncEnabled: Boolean = true,
    val healthWriteEnabled: Boolean = false,
    val napTrackingEnabled: Boolean = true,

    // presentation
    val use24HourClock: Boolean = true,
) {
    /** Steps the user will actually see, faith steps included only when opted in. */
    fun visibleSteps(phase: RoutinePhase): List<RoutineStep> =
        SleepRoutineCatalog.forPhase(phase)
            .filter { islamicRoutineEnabled || !it.optionalFaith }

    fun isEnabled(stepId: String): Boolean = stepId in enabledStepIds

    /**
     * Turns the Islamic routine on or off as a group.
     *
     * Enabling adds every faith step; disabling removes them and leaves the universal steps
     * exactly as they were, so toggling twice is a no-op rather than a quiet reset of choices
     * the user made about the other steps.
     */
    fun withIslamicRoutine(enabled: Boolean): SleepSettings = copy(
        islamicRoutineEnabled = enabled,
        enabledStepIds = if (enabled) {
            enabledStepIds + SleepRoutineCatalog.faithStepIds
        } else {
            enabledStepIds - SleepRoutineCatalog.faithStepIds
        },
    )

    fun withStep(stepId: String, enabled: Boolean): SleepSettings = copy(
        enabledStepIds = if (enabled) enabledStepIds + stepId else enabledStepIds - stepId,
    )
}
