package com.example.tafakkurai.i18n

import com.example.tafakkurai.adhkar.EVENING_ADHKAR
import com.example.tafakkurai.adhkar.MORNING_ADHKAR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdhkarLocalizationTest {

    private val tables = mapOf(
        Language.ENGLISH to EN,
        Language.UZBEK_LATIN to UZ_LATN,
        Language.UZBEK_CYRILLIC to UZ_CYRL,
        Language.RUSSIAN to RU,
    )

    private val items = (MORNING_ADHKAR + EVENING_ADHKAR).distinctBy { it.id }

    private val uiKeys = setOf(
        "adhkar.tab",
        "adhkar.category.morning",
        "adhkar.category.evening",
        "adhkar.progress.morning",
        "adhkar.progress.evening",
        "adhkar.hint.morning",
        "adhkar.hint.evening",
        "adhkar.footer",
        "adhkar.action.mark_done",
        "adhkar.action.mark_not_done",
        "adhkar.state.completed",
        "adhkar.state.not_completed",
        "adhkar.state.expanded",
        "adhkar.state.collapsed",
        "adhkar.label.arabic",
        "adhkar.label.transliteration",
        "adhkar.label.meaning",
    )

    @Test
    fun every_catalog_item_has_localized_title_transliteration_and_meaning() {
        assertEquals(20, items.size, "Update the localization catalog when the Adhkar catalog changes")
        val itemKeys = items.flatMap { item ->
            listOf(
                "adhkar.item.${item.id}.title",
                "adhkar.item.${item.id}.transliteration",
                "adhkar.item.${item.id}.meaning",
            )
        }.toSet()
        val required = uiKeys + itemKeys

        tables.forEach { (language, table) ->
            val missing = required - table.keys
            assertTrue(missing.isEmpty(), "${language.code} is missing Adhkar keys: ${missing.sorted()}")
        }
    }

    @Test
    fun meanings_do_not_silently_fall_back_to_english() {
        val translatedTables = tables - Language.ENGLISH

        translatedTables.forEach { (language, table) ->
            items.forEach { item ->
                val key = "adhkar.item.${item.id}.meaning"
                assertTrue(
                    table.getValue(key) != EN.getValue(key),
                    "$key in ${language.code} still contains the English fallback",
                )
            }
        }
    }

    @Test
    fun cyrillic_readers_receive_cyrillic_transliterations() {
        val cyrillic = Regex("[А-Яа-яЁёЎўҚқҒғҲҳ]")

        listOf(UZ_CYRL, RU).forEach { table ->
            items.forEach { item ->
                val value = table.getValue("adhkar.item.${item.id}.transliteration")
                assertTrue(cyrillic.containsMatchIn(value), "Cyrillic transliteration missing for ${item.id}")
            }
        }
    }
}
