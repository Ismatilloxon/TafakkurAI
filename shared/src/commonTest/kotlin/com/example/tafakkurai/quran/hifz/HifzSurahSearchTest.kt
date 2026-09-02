package com.example.tafakkurai.quran.hifz

import com.example.tafakkurai.quran.SURAH_CATALOG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HifzSurahSearchTest {

    @Test
    fun blank_search_exposes_the_complete_mushaf_index() {
        val matches = searchHifzSurahs("")

        assertEquals(114, matches.size)
        assertEquals((1..114).toList(), matches.map { it.number })
        assertEquals(SURAH_CATALOG, matches)
    }

    @Test
    fun search_matches_number_transliteration_meaning_and_arabic() {
        assertEquals(listOf(112), searchHifzSurahs("112").map { it.number })
        assertEquals(listOf(1), searchHifzSurahs("fatihah").map { it.number })
        assertEquals(listOf(2), searchHifzSurahs("al baqarah").map { it.number })
        assertTrue(55 in searchHifzSurahs("beneficent").map { it.number })
        assertEquals(listOf(114), searchHifzSurahs("الناس").map { it.number })
    }
}
