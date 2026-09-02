package com.example.tafakkurai.journal

import com.example.tafakkurai.journal.domain.JournalDraft
import com.example.tafakkurai.journal.domain.JournalMood
import com.example.tafakkurai.journal.domain.JournalPrompt
import com.example.tafakkurai.journal.domain.JournalReflection
import com.example.tafakkurai.journal.domain.filterReflections
import com.example.tafakkurai.journal.domain.journalSummary
import com.example.tafakkurai.journal.domain.nextPrompt
import com.example.tafakkurai.journal.domain.normalized
import com.example.tafakkurai.journal.domain.promptForDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalModelsTest {

    @Test
    fun draftsAreTrimmedBoundedAndBlankWritingIsRejected() {
        assertNull(JournalDraft(JournalPrompt.LESSON, JournalMood.CALM, "   ").normalized())

        val normalized = JournalDraft(
            JournalPrompt.LESSON,
            JournalMood.HOPEFUL,
            "  " + "a".repeat(20_100) + "  ",
        ).normalized()!!

        assertEquals(20_000, normalized.body.length)
        assertTrue(normalized.body.all { it == 'a' })
    }

    @Test
    fun searchMatchesBodyStableIdsAndLocalizedPromptOrMood() {
        val entries = listOf(
            reflection("one", JournalPrompt.PATIENCE, JournalMood.TESTED, "A quiet lesson", "2026-08-28"),
            reflection("two", JournalPrompt.GRATITUDE, JournalMood.CALM, "Family called", "2026-08-29"),
        )
        val uzbek = mapOf(
            JournalPrompt.PATIENCE.titleKey to "Sabringiz qayerda sinaldi?",
            JournalMood.TESTED.titleKey to "Sinovda",
        )

        assertEquals(listOf("one"), filterReflections(entries, "QUIET").map { it.id })
        assertEquals(listOf("one"), filterReflections(entries, "patience").map { it.id })
        val localized: (String) -> String = { key -> uzbek[key] ?: key }
        assertEquals(listOf("one"), filterReflections(entries, "sabr", localized).map { it.id })
        assertEquals(listOf("one"), filterReflections(entries, "sinov", localized).map { it.id })
        assertEquals(listOf("two", "one"), filterReflections(entries, "").map { it.id })
    }

    @Test
    fun summaryUsesDistinctLocalDatesAndARealConsecutiveStreak() {
        val entries = listOf(
            reflection("a", entryDate = "2026-08-29"),
            reflection("b", entryDate = "2026-08-29"),
            reflection("c", entryDate = "2026-08-28"),
            reflection("d", entryDate = "2026-08-26"),
            reflection("old", entryDate = "2026-08-20"),
        )

        val summary = journalSummary(entries, "2026-08-29")

        assertEquals(5, summary.entryCount)
        assertEquals(3, summary.activeDaysInLastSeven)
        assertEquals(2, summary.streakDays)
    }

    @Test
    fun dailyPromptIsStableAndCyclingVisitsEveryPrompt() {
        assertEquals(promptForDate("2026-08-29"), promptForDate("2026-08-29"))

        val start = JournalPrompt.GRATITUDE
        val visited = buildList {
            var current = start
            repeat(JournalPrompt.entries.size) {
                add(current)
                current = nextPrompt(current)
            }
            assertEquals(start, current)
        }

        assertEquals(JournalPrompt.entries.toSet(), visited.toSet())
    }

    private fun reflection(
        id: String,
        prompt: JournalPrompt = JournalPrompt.LESSON,
        mood: JournalMood = JournalMood.CALM,
        body: String = id,
        entryDate: String,
    ) = JournalReflection(
        id = id,
        prompt = prompt,
        mood = mood,
        body = body,
        entryDate = entryDate,
        createdAt = id.hashCode().toLong(),
        updatedAt = id.hashCode().toLong(),
    )
}
