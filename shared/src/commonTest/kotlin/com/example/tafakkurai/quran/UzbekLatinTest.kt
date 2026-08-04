package com.example.tafakkurai.quran

import kotlin.test.Test
import kotlin.test.assertEquals

class UzbekLatinTest {

    @Test
    fun uzbek_specific_letters_use_the_official_latin_forms() {
        assertEquals("oʻgʻil", UzbekLatin.transliterate("ўғил"))
        assertEquals("qalb", UzbekLatin.transliterate("қалб"))
        assertEquals("halol", UzbekLatin.transliterate("ҳалол"))
    }

    @Test
    fun case_follows_the_source() {
        assertEquals("Oʻzbekiston", UzbekLatin.transliterate("Ўзбекистон"))
        assertEquals("Alloh", UzbekLatin.transliterate("Аллоҳ"))
    }

    @Test
    fun word_initial_ye_is_distinguished_from_medial_e() {
        assertEquals("Yer", UzbekLatin.transliterate("Ер"))
        assertEquals("keldi", UzbekLatin.transliterate("келди"))
    }

    @Test
    fun punctuation_digits_and_latin_pass_through_untouched() {
        assertEquals("2:255 — oyat, (Baqara)!", UzbekLatin.transliterate("2:255 — оят, (Baqara)!"))
    }

    @Test
    fun a_real_ayah_transliterates_word_for_word() {
        assertEquals(
            "Alloh Undan oʻzga iloh yoʻq zotdir.",
            UzbekLatin.transliterate("Аллоҳ Ундан ўзга илоҳ йўқ зотдир."),
        )
    }
}
