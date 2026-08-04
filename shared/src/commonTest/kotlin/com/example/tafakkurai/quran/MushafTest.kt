package com.example.tafakkurai.quran

import com.example.tafakkurai.spiritual.InMemorySpiritualPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MushafCatalogTest {

    @Test
    fun three_mushafs_are_offered_and_madani_is_the_default() {
        assertEquals(listOf("madani", "turkish", "indopak"), MUSHAFS.map { it.id })
        assertEquals("madani", DEFAULT_MUSHAF.id)
        assertEquals(BASMALA, DEFAULT_MUSHAF.basmala)
    }

    @Test
    fun unknown_ids_fall_back_rather_than_crash() {
        assertEquals(DEFAULT_MUSHAF, mushafOf(null))
        assertEquals(DEFAULT_MUSHAF, mushafOf("uthmani-tajweed"))
        assertEquals("indopak", mushafOf("indopak").id)
    }

    /** The whole point of the setting: each one prints different letters. */
    @Test
    fun every_mushaf_prints_the_basmala_differently() {
        val basmalas = MUSHAFS.map { it.basmala }
        assertEquals(basmalas.size, basmalas.toSet().size)
        val madani = mushafOf("madani").basmala
        val indopak = mushafOf("indopak").basmala
        assertTrue(madani.contains('ٱ'))    // uthmani alef wasla
        assertTrue(indopak.contains('ۡ'))   // indopak small high sukun
        assertNotEquals(madani, mushafOf("turkish").basmala)
    }

    @Test
    fun indopak_gets_more_line_height_than_uthmani() {
        assertTrue(mushafOf("indopak").lineHeight > mushafOf("madani").lineHeight)
    }
}

class MushafPreferenceTest {

    private fun store(persistence: InMemorySpiritualPersistence = InMemorySpiritualPersistence()) =
        QuranProgressStore(persistence, persistence, now = { 1_000L })

    @Test
    fun mushaf_defaults_to_madani() {
        assertEquals("madani", store().mushaf.id)
    }

    @Test
    fun chosen_mushaf_survives_a_reload() {
        val persistence = InMemorySpiritualPersistence()
        store(persistence).setMushaf("indopak")

        val reopened = store(persistence)
        assertEquals("indopak", reopened.mushafId)
        assertEquals(MushafSource.QuranCom("indopak"), reopened.mushaf.source)
    }

    @Test
    fun a_stored_id_that_no_longer_exists_reads_back_as_the_default() {
        val persistence = InMemorySpiritualPersistence()
        persistence.put(QuranProgressStore.KEY_MUSHAF, "warsh")

        assertEquals("madani", store(persistence).mushaf.id)
    }
}

/**
 * alquran.cloud prints the Basmala inside ayah 1; quran.com does not. Without this the
 * same line would show twice, and only in some mushafs.
 */
class BasmalaStrippingTest {

    private val uthmaniAyah1 = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ"
    private val imlaeiAyah1 = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ الم"

    @Test
    fun the_uthmani_basmala_is_dropped_from_ayah_one() {
        assertEquals("الٓمٓ", withoutLeadingBasmala(2, uthmaniAyah1))
    }

    @Test
    fun the_imlaei_basmala_is_dropped_too() {
        assertEquals("الم", withoutLeadingBasmala(2, imlaeiAyah1))
    }

    @Test
    fun the_indopak_basmala_is_dropped_too() {
        val ayah = "بِسۡمِ اللهِ الرَّحۡمٰنِ الرَّحِيۡمِ الٓمّٓ"
        assertEquals("الٓمّٓ", withoutLeadingBasmala(2, ayah))
    }

    @Test
    fun al_fatihah_keeps_its_basmala_because_that_is_its_first_ayah() {
        assertEquals(BASMALA, withoutLeadingBasmala(1, BASMALA))
    }

    @Test
    fun at_tawbah_is_left_alone() {
        val ayah = "بَرَآءَةٌ مِّنَ ٱللَّهِ وَرَسُولِهِۦٓ"
        assertEquals(ayah, withoutLeadingBasmala(9, ayah))
    }

    @Test
    fun an_ayah_that_merely_opens_with_the_name_of_Allah_is_untouched() {
        val ayah = "ٱللَّهُ ٱلصَّمَدُ"
        assertEquals(ayah, withoutLeadingBasmala(112, ayah))
    }

    @Test
    fun a_basmala_only_ayah_is_never_emptied() {
        assertEquals(BASMALA, withoutLeadingBasmala(112, BASMALA))
    }
}
