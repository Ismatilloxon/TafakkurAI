package com.example.tafakkurai.ai.domain

import com.example.tafakkurai.tasks.domain.TaskPriority

/** Schema-constrained mutations the local coach may preview and execute. */
sealed interface ProposedAction {
    data class CreateTask(
        val title: String,
        val dueDate: String,
        val dueTime: String = "",
        val priority: TaskPriority = TaskPriority.MEDIUM,
        val categoryId: String = "personal",
    ) : ProposedAction

    data class LogActivity(
        val title: String,
        val categoryId: String,
        val durationMinutes: Int? = null,
        val dueDate: String,
    ) : ProposedAction

    data class CreateHabit(val name: String, val categoryId: String = "custom") : ProposedAction

    data class SaveGratitude(val text: String) : ProposedAction

    data class StartHifz(val surahNumber: Int, val surahName: String) : ProposedAction

    data class CreateFlashcards(
        val sourceTitle: String,
        val fromTheme: Int,
        val toTheme: Int,
        val subjectId: String = LOCAL_VOCABULARY_SUBJECT_ID,
    ) : ProposedAction {
        val deckName: String get() = "$sourceTitle · $fromTheme–$toTheme"
    }
}

/** Stable local collection exposed directly from Learn, independent of backend subject ids. */
const val LOCAL_VOCABULARY_SUBJECT_ID = "s-en"

enum class LocalActionIssue {
    MISSING_TITLE,
    MISSING_SOURCE,
    MISSING_SCOPE,
    SOURCE_TOO_LARGE,
    SOURCE_HAS_NO_SCOPED_PAIRS,
    SOURCE_MISSING_THEMES,
    NO_NEW_CARDS,
    UNKNOWN_SURAH,
    ACTION_FAILED,
    UNSUPPORTED_ACTION,
}

sealed interface ParsedLocalIntent {
    data object NotLocalAction : ParsedLocalIntent
    data class Action(val value: ProposedAction) : ParsedLocalIntent
    data class Invalid(
        val issue: LocalActionIssue,
        val arguments: List<String> = emptyList(),
    ) : ParsedLocalIntent
}

sealed interface LocalProposal {
    data class Ready(
        val action: ProposedAction,
        val itemCount: Int = 1,
        val missingThemes: Set<Int> = emptySet(),
    ) : LocalProposal

    data class NeedsSource(val action: ProposedAction.CreateFlashcards) : LocalProposal
    data class Invalid(
        val issue: LocalActionIssue,
        val arguments: List<String> = emptyList(),
    ) : LocalProposal

    data object NotLocalAction : LocalProposal
}

sealed interface LocalExecutionResult {
    data class Success(val action: ProposedAction, val affected: Int) : LocalExecutionResult
    data class Failure(
        val issue: LocalActionIssue,
        val arguments: List<String> = emptyList(),
    ) : LocalExecutionResult
}
