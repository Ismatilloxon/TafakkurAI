package com.example.tafakkurai.ai.data

import com.example.tafakkurai.ai.domain.LocalActionIssue
import com.example.tafakkurai.ai.domain.LocalActionParser
import com.example.tafakkurai.ai.domain.LocalExecutionResult
import com.example.tafakkurai.ai.domain.LocalProposal
import com.example.tafakkurai.ai.domain.ParsedLocalIntent
import com.example.tafakkurai.ai.domain.ProposedAction
import com.example.tafakkurai.ai.domain.VocabularyCardDraft
import com.example.tafakkurai.ai.domain.parseVocabularySource
import com.example.tafakkurai.flashcards.FlashcardImportDraft
import com.example.tafakkurai.flashcards.FlashcardStore
import com.example.tafakkurai.gratitude.GratMode
import com.example.tafakkurai.gratitude.GratitudeStore
import com.example.tafakkurai.habits.HabitSlot
import com.example.tafakkurai.habits.HabitStore
import com.example.tafakkurai.habits.dateKeyOf
import com.example.tafakkurai.habits.dayNumberOfKey
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.quran.hifz.HifzStore
import com.example.tafakkurai.tasks.data.TaskStore
import com.example.tafakkurai.tasks.domain.CreateTaskCommand
import com.example.tafakkurai.tasks.domain.TaskOrigin

/**
 * Application-layer bridge from a schema-constrained local proposal to existing feature
 * use cases/stores. The coach never writes SQL and never bypasses feature validation.
 */
class OnDeviceActionRouter(
    private val parser: LocalActionParser,
    private val tasks: TaskStore,
    private val habits: HabitStore,
    private val gratitude: GratitudeStore,
    private val hifz: HifzStore,
    private val flashcards: FlashcardStore,
    private val sources: VocabularySourceStore,
    private val today: () -> String = ::deviceDateKey,
) {
    fun propose(text: String): LocalProposal {
        val date = today()
        val tomorrow = dayNumberOfKey(date)?.let { dateKeyOf(it + 1) } ?: date
        return when (val parsed = parser.parse(text, date, tomorrow)) {
            ParsedLocalIntent.NotLocalAction -> LocalProposal.NotLocalAction
            is ParsedLocalIntent.Invalid -> LocalProposal.Invalid(parsed.issue, parsed.arguments)
            is ParsedLocalIntent.Action -> resolve(parsed.value)
        }
    }

    fun resolve(action: ProposedAction): LocalProposal {
        if (action !is ProposedAction.CreateFlashcards) return LocalProposal.Ready(action)
        val document = sources.find(action.sourceTitle) ?: return LocalProposal.NeedsSource(action)
        val parsed = parseVocabularySource(document.content, action.fromTheme, action.toTheme)
        if (parsed.cards.isEmpty()) {
            return LocalProposal.Invalid(
                LocalActionIssue.SOURCE_HAS_NO_SCOPED_PAIRS,
                listOf(action.sourceTitle, action.fromTheme.toString(), action.toTheme.toString()),
            )
        }
        if (parsed.missingThemes.isNotEmpty()) {
            return LocalProposal.Invalid(
                LocalActionIssue.SOURCE_MISSING_THEMES,
                listOf(parsed.missingThemes.sorted().joinToString(", ")),
            )
        }
        val importable = flashcards.importableVocabularyCount(
            action.subjectId,
            action.deckName,
            parsed.cards.toImportDrafts(),
        )
        if (importable == 0) return LocalProposal.Invalid(LocalActionIssue.NO_NEW_CARDS)
        return LocalProposal.Ready(action, itemCount = importable)
    }

    fun saveSource(action: ProposedAction.CreateFlashcards, content: String): LocalProposal {
        if (content.length > VocabularySourceStore.MAX_CONTENT_CHARS) {
            return LocalProposal.Invalid(LocalActionIssue.SOURCE_TOO_LARGE)
        }
        if (sources.save(action.sourceTitle, content) == null) {
            return LocalProposal.Invalid(LocalActionIssue.ACTION_FAILED)
        }
        return resolve(action)
    }

    fun execute(action: ProposedAction): LocalExecutionResult = runCatching {
        when (action) {
            is ProposedAction.CreateTask -> {
                val task = tasks.create(
                    CreateTaskCommand(
                        title = action.title,
                        categoryId = action.categoryId,
                        priority = action.priority,
                        dueDate = action.dueDate,
                        dueTime = action.dueTime,
                        origin = TaskOrigin.AI,
                    ),
                ) ?: return LocalExecutionResult.Failure(LocalActionIssue.ACTION_FAILED)
                LocalExecutionResult.Success(action, if (task.id.isNotBlank()) 1 else 0)
            }

            is ProposedAction.LogActivity -> {
                val task = tasks.create(
                    CreateTaskCommand(
                        title = action.title,
                        notes = action.durationMinutes?.let { "duration_minutes=$it" }.orEmpty(),
                        categoryId = action.categoryId,
                        dueDate = action.dueDate,
                        origin = TaskOrigin.AI,
                    ),
                    completed = true,
                ) ?: return LocalExecutionResult.Failure(LocalActionIssue.ACTION_FAILED)
                LocalExecutionResult.Success(action, if (task.completed) 1 else 0)
            }

            is ProposedAction.CreateHabit -> {
                val visual = habitVisual(action.categoryId)
                habits.create(
                    name = action.name,
                    categoryId = action.categoryId,
                    icon = visual.first,
                    colorKey = visual.second,
                    slot = HabitSlot.ANYTIME,
                    source = "ai",
                )
                LocalExecutionResult.Success(action, 1)
            }

            is ProposedAction.SaveGratitude -> {
                gratitude.add(action.text, mode = GratMode.QUICK, source = "ai")
                    ?: return LocalExecutionResult.Failure(LocalActionIssue.ACTION_FAILED)
                LocalExecutionResult.Success(action, 1)
            }

            is ProposedAction.StartHifz -> {
                val added = hifz.addRange(action.surahNumber, source = "ai")
                LocalExecutionResult.Success(action, added)
            }

            is ProposedAction.CreateFlashcards -> {
                val document = sources.find(action.sourceTitle)
                    ?: return LocalExecutionResult.Failure(LocalActionIssue.MISSING_SOURCE)
                val parsed = parseVocabularySource(document.content, action.fromTheme, action.toTheme)
                if (parsed.cards.isEmpty()) {
                    return LocalExecutionResult.Failure(LocalActionIssue.SOURCE_HAS_NO_SCOPED_PAIRS)
                }
                if (parsed.missingThemes.isNotEmpty()) {
                    return LocalExecutionResult.Failure(
                        LocalActionIssue.SOURCE_MISSING_THEMES,
                        listOf(parsed.missingThemes.sorted().joinToString(", ")),
                    )
                }
                val result = flashcards.importVocabulary(
                    subjectId = action.subjectId,
                    deckName = action.deckName,
                    sourceTitle = action.sourceTitle,
                    drafts = parsed.cards.toImportDrafts(),
                    actionSource = "ai",
                ) ?: return LocalExecutionResult.Failure(LocalActionIssue.ACTION_FAILED)
                if (result.added == 0) return LocalExecutionResult.Failure(LocalActionIssue.NO_NEW_CARDS)
                LocalExecutionResult.Success(action, result.added)
            }
        }
    }.getOrElse { LocalExecutionResult.Failure(LocalActionIssue.ACTION_FAILED) }
}

private fun List<VocabularyCardDraft>.toImportDrafts() =
    map { card -> FlashcardImportDraft(card.front, card.back, card.theme) }

private fun habitVisual(category: String): Pair<String, String> = when (category) {
    "health" -> "activity" to "green"
    "family", "social" -> "users" to "rose"
    "study", "learning" -> "bookOpen" to "cyan"
    else -> "sparkles" to "teal"
}
