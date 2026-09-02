package com.example.tafakkurai.ai

import com.example.tafakkurai.ai.domain.parseVocabularySource
import kotlin.test.Test
import kotlin.test.assertEquals

class VocabularyParserTest {
    @Test
    fun extractsOnlyExplicitPairsInsideRequestedThemes() {
        val result = parseVocabularySource(
            """
            Theme 1
            abandon — tark etmoq
            This explanatory sentence is not a card.
            brief: qisqa

            Theme 2
            cautious = ehtiyotkor

            Theme 11
            outside — kiritilmasin
            """.trimIndent(),
            fromTheme = 1,
            toTheme = 2,
        )

        assertEquals(setOf(1, 2), result.foundThemes)
        assertEquals(emptySet(), result.missingThemes)
        assertEquals(listOf("abandon", "brief", "cautious"), result.cards.map { it.front })
        assertEquals(listOf(1, 1, 2), result.cards.map { it.theme })
    }

    @Test
    fun deduplicatesFrontsWithoutInventingDefinitions() {
        val result = parseVocabularySource(
            """
            Unit 3
            benefit - foyda
            BENEFIT — boshqa izoh
            unsupported prose only
            """.trimIndent(),
            fromTheme = 3,
            toTheme = 3,
        )

        assertEquals(1, result.cards.size)
        assertEquals("benefit", result.cards.single().front)
        assertEquals("foyda", result.cards.single().back)
    }

    @Test
    fun headingWithoutAValidPairIsReportedMissing() {
        val result = parseVocabularySource(
            """
            Theme 1
            valid — haqiqiy
            Theme 2
            prose without an explicit separator
            """.trimIndent(),
            fromTheme = 1,
            toTheme = 2,
        )

        assertEquals(setOf(1), result.foundThemes)
        assertEquals(setOf(2), result.missingThemes)
    }
}
