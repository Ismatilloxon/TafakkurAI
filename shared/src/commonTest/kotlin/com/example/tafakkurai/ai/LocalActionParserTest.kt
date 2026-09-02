package com.example.tafakkurai.ai

import com.example.tafakkurai.ai.domain.LocalActionIssue
import com.example.tafakkurai.ai.domain.LocalActionParser
import com.example.tafakkurai.ai.domain.ParsedLocalIntent
import com.example.tafakkurai.ai.domain.ProposedAction
import com.example.tafakkurai.i18n.Language
import com.example.tafakkurai.i18n.Strings
import com.example.tafakkurai.tasks.domain.TaskPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalActionParserTest {
    private val parser = LocalActionParser()

    @Test
    fun destinationB2RequestProducesExactSourceAndThemeScope() {
        val parsed = parser.parse(
            "add flashcards to new vocabulary words from book Destination B2 book's 1-10 themes",
            TODAY,
            TOMORROW,
        )

        val action = assertIs<ProposedAction.CreateFlashcards>(
            assertIs<ParsedLocalIntent.Action>(parsed).value,
        )
        assertEquals("Destination B2", action.sourceTitle)
        assertEquals(1, action.fromTheme)
        assertEquals(10, action.toTheme)
    }

    @Test
    fun genericBookTitleStopsBeforePossessiveThemeRange() {
        val parsed = parser.parse(
            "create flashcards from book English File book's 3-5 themes",
            TODAY,
            TOMORROW,
        )

        val action = assertIs<ProposedAction.CreateFlashcards>(
            assertIs<ParsedLocalIntent.Action>(parsed).value,
        )
        assertEquals("English File", action.sourceTitle)
        assertEquals(3, action.fromTheme)
        assertEquals(5, action.toTheme)
    }

    @Test
    fun englishTaskExtractsMetadataWithoutPollutingTitle() {
        val parsed = parser.parse(
            "Please add task Read chapter 4 tomorrow at 18:30 high priority",
            TODAY,
            TOMORROW,
        )

        val action = assertIs<ProposedAction.CreateTask>(
            assertIs<ParsedLocalIntent.Action>(parsed).value,
        )
        assertEquals("Read chapter 4", action.title)
        assertEquals(TOMORROW, action.dueDate)
        assertEquals("18:30", action.dueTime)
        assertEquals(TaskPriority.HIGH, action.priority)
    }

    @Test
    fun uzbekNounFirstTaskCommandWorksOnDevice() {
        val parsed = parser.parse(
            "Vazifa qoʻsh: kitob oʻqish ertaga soat 07:15 yuqori",
            TODAY,
            TOMORROW,
        )

        val action = assertIs<ProposedAction.CreateTask>(
            assertIs<ParsedLocalIntent.Action>(parsed).value,
        )
        assertEquals("kitob oʻqish", action.title)
        assertEquals(TOMORROW, action.dueDate)
        assertEquals("07:15", action.dueTime)
        assertEquals(TaskPriority.HIGH, action.priority)
    }

    @Test
    fun localizedUzbekSuggestionsRemainExecutableCommands() {
        assertIs<ProposedAction.StartHifz>(
            assertIs<ParsedLocalIntent.Action>(
                parser.parse("Hifzni boshla: sura 36", TODAY, TOMORROW),
            ).value,
        )
        assertIs<ProposedAction.StartHifz>(
            assertIs<ParsedLocalIntent.Action>(
                parser.parse("Ҳифзни бошла: сура 36", TODAY, TOMORROW),
            ).value,
        )
        val cards = assertIs<ProposedAction.CreateFlashcards>(
            assertIs<ParsedLocalIntent.Action>(
                parser.parse("Карточка қўш: Destination B2 1–10 мавзу", TODAY, TOMORROW),
            ).value,
        )
        assertEquals(1, cards.fromTheme)
        assertEquals(10, cards.toTheme)
    }

    @Test
    fun everyLocalizedSuggestionIsAnExecutableLocalProposal() {
        val keys = listOf(
            "coach.suggestion.task",
            "coach.suggestion.habit",
            "coach.suggestion.hifz",
            "coach.suggestion.flashcards",
        )
        Language.entries.forEach { language ->
            val strings = Strings.of(language)
            keys.forEach { key ->
                val parsed = parser.parse(strings[key], TODAY, TOMORROW)
                assertTrue(
                    parsed is ParsedLocalIntent.Action,
                    "${language.code} $key must remain executable, got $parsed",
                )
            }
        }
    }

    @Test
    fun hifzCanResolveAnySurahByNumber() {
        val parsed = parser.parse("start hifz surah 36", TODAY, TOMORROW)

        val action = assertIs<ProposedAction.StartHifz>(
            assertIs<ParsedLocalIntent.Action>(parsed).value,
        )
        assertEquals(36, action.surahNumber)
        assertEquals("Ya-Sin", action.surahName)
    }

    @Test
    fun explanationIsNotMisclassifiedAsMutation() {
        assertIs<ParsedLocalIntent.NotLocalAction>(
            parser.parse("Explain how to create tasks", TODAY, TOMORROW),
        )
    }

    @Test
    fun flashcardRequestRequiresBoundedThemeScope() {
        val parsed = parser.parse(
            "create flashcards from Destination B2 themes 0-500",
            TODAY,
            TOMORROW,
        )

        val invalid = assertIs<ParsedLocalIntent.Invalid>(parsed)
        assertEquals(LocalActionIssue.MISSING_SCOPE, invalid.issue)
    }

    private companion object {
        const val TODAY = "2026-08-28"
        const val TOMORROW = "2026-08-29"
    }
}
