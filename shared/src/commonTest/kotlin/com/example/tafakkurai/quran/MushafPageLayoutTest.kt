package com.example.tafakkurai.quran

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The page layout is the one piece of mushaf rendering with a right answer: a word
 * belongs to the line the print puts it on, and a surah opening leaves room for its
 * banner and Basmala. These check the reconstruction, not the network.
 */
class MushafPageLayoutTest {

    private fun word(text: String, line: Int, type: String = "word", page: Int? = 582) =
        WireWord(charType = type, lineNumber = line, pageNumber = page, textUthmani = text)

    @Test
    fun `groups words onto the lines the mushaf prints them on`() {
        val page = layout(
            582,
            listOf(
                WireVerse("78:1", 30, listOf(word("عَمَّ", 3), word("يَتَسَآءَلُونَ", 3), word("١", 3, "end"))),
                WireVerse("78:2", 30, listOf(word("عَنِ", 3), word("ٱلنَّبَإِ", 4))),
            ),
        )
        val lines = page.lines.filterIsInstance<MushafLine.Verse>().associateBy { it.number }
        assertEquals(4, lines.getValue(3).words.size)
        assertEquals(1, lines.getValue(4).words.size)
        assertEquals(30, page.juzNumber)
        assertEquals(listOf(78), page.surahNumbers)
    }

    @Test
    fun `ayah numbers are printed in arabic-indic digits inside the roundel`() {
        val page = layout(582, listOf(WireVerse("78:12", 30, listOf(word("x", 1), word("", 1, "end")))))
        val marker = page.lines.filterIsInstance<MushafLine.Verse>()
            .flatMap { it.words }.single { it.isEndMarker }
        assertEquals("١٢", marker.text)
        assertEquals(12, marker.ayahNumber)
    }

    @Test
    fun `a surah opening takes the banner and basmala lines above its first verse`() {
        // An-Naba begins on line 3, so lines 1 and 2 are the banner and the Basmala.
        val page = layout(582, listOf(WireVerse("78:1", 30, listOf(word("عَمَّ", 3)))))
        assertTrue(page.lines[0] is MushafLine.SurahBanner)
        assertEquals(78, (page.lines[0] as MushafLine.SurahBanner).surahNumber)
        assertTrue(page.lines[1] is MushafLine.Basmala)
    }

    @Test
    fun `at-tawbah opens with a banner and no basmala`() {
        val page = layout(187, listOf(WireVerse("9:1", 10, listOf(word("بَرَآءَةٌ", 2, page = 187)))))
        assertTrue(page.lines[0] is MushafLine.SurahBanner)
        assertTrue(page.lines.none { it is MushafLine.Basmala })
    }

    @Test
    fun `words carried onto the next page are not drawn on this one`() {
        val page = layout(
            582,
            listOf(WireVerse("78:40", 30, listOf(word("هُنَا", 15), word("هُنَاكَ", 1, page = 583)))),
        )
        val words = page.lines.filterIsInstance<MushafLine.Verse>().flatMap { it.words }
        assertEquals(listOf("هُنَا"), words.map { it.text })
    }
}
