package com.example.tafakkurai.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard rail behind "no untranslated text remains".
 *
 * A new string can only be added to one table and forgotten in the others for as long as it
 * takes to run the tests — [every_language_defines_every_key] fails with the missing keys
 * listed by language, so a half-translated release cannot ship quietly.
 */
class StringsTest {

    private val tables: Map<Language, Map<String, String>> = mapOf(
        Language.ENGLISH to EN,
        Language.RUSSIAN to RU,
        Language.UZBEK_LATIN to UZ_LATN,
        Language.UZBEK_CYRILLIC to UZ_CYRL,
    )

    @Test
    fun every_language_defines_every_key() {
        val allKeys = tables.values.flatMap { it.keys }.toSet()
        val missing = tables.mapValues { (_, table) -> (allKeys - table.keys).sorted() }
            .filterValues { it.isNotEmpty() }

        assertTrue(
            missing.isEmpty(),
            "Untranslated keys:\n" + missing.entries.joinToString("\n") { (language, keys) ->
                "  ${language.code}: ${keys.joinToString(", ")}"
            },
        )
    }

    @Test
    fun no_string_is_left_blank() {
        tables.forEach { (language, table) ->
            table.forEach { (key, value) ->
                assertTrue(value.isNotBlank(), "${language.code} has an empty value for $key")
            }
        }
    }

    @Test
    fun placeholders_match_across_languages() {
        val placeholder = Regex("\\{\\d+}")
        EN.forEach { (key, english) ->
            val expected = placeholder.findAll(english).map { it.value }.toSet()
            tables.forEach { (language, table) ->
                val translated = table[key] ?: return@forEach
                val actual = placeholder.findAll(translated).map { it.value }.toSet()
                assertEquals(
                    expected, actual,
                    "$key in ${language.code} must use the same placeholders as English",
                )
            }
        }
    }

    @Test
    fun a_missing_translation_falls_back_to_english_rather_than_the_raw_key() {
        // Simulates a language that has not been given this key yet.
        val partial = Strings(Language.RUSSIAN, mapOf("lang.title" to "Выберите язык"))

        assertEquals("Выберите язык", partial["lang.title"])
        assertEquals(EN.getValue("common.retry"), partial["common.retry"])
    }

    @Test
    fun positional_arguments_are_substituted_in_each_languages_word_order() {
        assertEquals(
            "A 6-digit code was sent to +998 90 123 45 67.",
            Strings.of(Language.ENGLISH).format("otp.sent_to", "90 123 45 67"),
        )
        assertEquals(
            "6 xonali kod +998 90 123 45 67 raqamiga yuborildi.",
            Strings.of(Language.UZBEK_LATIN).format("otp.sent_to", "90 123 45 67"),
        )
    }

    @Test
    fun uzbek_latin_uses_the_official_modifier_letters_not_ascii_apostrophes() {
        val offenders = UZ_LATN.filter { (_, value) -> value.contains("'") || value.contains("’") }
        assertTrue(offenders.isEmpty(), "use ʻ/ʼ (U+02BB/U+02BC): ${offenders.keys}")
    }

    @Test
    fun the_language_list_names_each_language_in_its_own_script() {
        assertEquals("Oʻzbekcha", Language.UZBEK_LATIN.nativeName)
        assertEquals("Ўзбекча", Language.UZBEK_CYRILLIC.nativeName)
        assertEquals("Русский", Language.RUSSIAN.nativeName)
        assertEquals(Language.UZBEK_LATIN, Language.from("uz-Latn"))
        assertEquals(Language.DEFAULT, Language.from("klingon"))
    }
}
