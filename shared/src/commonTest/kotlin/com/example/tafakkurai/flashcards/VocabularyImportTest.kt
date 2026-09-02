package com.example.tafakkurai.flashcards

import kotlin.test.Test
import kotlin.test.assertEquals

class VocabularyImportTest {
    @Test
    fun existingAndRepeatedFrontsAreSkippedCaseInsensitively() {
        val result = deduplicateImportDrafts(
            existingFronts = setOf("  Existing   Word "),
            drafts = listOf(
                FlashcardImportDraft("existing word", "old", 1),
                FlashcardImportDraft("Benefit", "foyda", 1),
                FlashcardImportDraft(" benefit ", "duplicate", 2),
                FlashcardImportDraft("Cautious", "ehtiyotkor", 2),
            ),
        )

        assertEquals(listOf("Benefit", "Cautious"), result.map { it.front })
        assertEquals(listOf(1, 2), result.map { it.theme })
    }

    @Test
    fun validationTrimsBoundsAndHonoursBulkLimit() {
        val result = deduplicateImportDrafts(
            existingFronts = emptySet(),
            drafts = listOf(
                FlashcardImportDraft("  first  ", "  one  ", 1),
                FlashcardImportDraft("", "missing front", 1),
                FlashcardImportDraft("second", "two", 1),
            ),
            limit = 2,
        )

        assertEquals(1, result.size)
        assertEquals("first", result.single().front)
        assertEquals("one", result.single().back)
    }
}
