package com.example.tafakkurai.quran

import com.example.tafakkurai.spiritual.InMemorySpiritualPersistence
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurahCatalogTest {

    @Test
    fun catalog_has_all_114_surahs() {
        assertEquals(114, SURAH_CATALOG.size)
        assertEquals((1..114).toList(), SURAH_CATALOG.map { it.number })
    }

    @Test
    fun ayah_counts_match_the_hafs_mushaf() {
        assertEquals(7, ayahCountOf(1))
        assertEquals(286, ayahCountOf(2))
        assertEquals(200, ayahCountOf(3))
        assertEquals(176, ayahCountOf(4))
        assertEquals(227, ayahCountOf(26))   // longest by ayah count
        assertEquals(3, ayahCountOf(103))    // shortest
        assertEquals(6, ayahCountOf(114))
    }

    @Test
    fun total_ayah_count_is_6236() {
        assertEquals(6236, TOTAL_AYAH_COUNT)
    }

    @Test
    fun unknown_surah_numbers_are_rejected_not_guessed() {
        assertNull(surahOf(0))
        assertNull(surahOf(115))
        assertEquals(0, ayahCountOf(200))
    }
}

class QuranProgressStoreTest {

    private fun store(
        persistence: InMemorySpiritualPersistence = InMemorySpiritualPersistence(),
    ) = QuranProgressStore(persistence, persistence, now = { 1_000L })

    @Test
    fun surah_progress_is_counted_from_read_ayahs() {
        val subject = store()
        repeat(100) { subject.markRead(2, it + 1) }

        val progress = subject.progressOf(2)
        assertEquals(100, progress.readCount)
        assertEquals(286, progress.ayahCount)
        // 100 / 286 = 34.9% -> 34 when truncated
        assertEquals(34, progress.percent)
        assertFalse(progress.isComplete)
    }

    @Test
    fun surah_completes_only_when_every_ayah_is_read() {
        val subject = store()
        repeat(6) { subject.markRead(1, it + 1) }
        assertFalse(subject.progressOf(1).isComplete)
        assertEquals(0, subject.completedSurahCount)

        subject.markRead(1, 7)
        assertTrue(subject.progressOf(1).isComplete)
        assertEquals(1, subject.completedSurahCount)
    }

    @Test
    fun overall_progress_is_ayah_level_across_surahs() {
        val subject = store()
        repeat(7) { subject.markRead(1, it + 1) }      // Al-Fatihah, complete
        repeat(100) { subject.markRead(2, it + 1) }    // Al-Baqarah, partial

        // 107 ayahs of 6236 — not "1 of 114 surahs".
        assertEquals(107, subject.totalRead)
        assertEquals(1, subject.overallPercent)
        assertTrue(abs(subject.overallProgress - 107f / 6236f) < 1e-6f)
    }

    @Test
    fun marking_the_same_ayah_twice_does_not_double_count() {
        val subject = store()
        subject.markRead(2, 255)
        subject.markRead(2, 255)
        assertEquals(1, subject.totalRead)
        assertEquals(1, subject.readCountOf(2))
    }

    @Test
    fun out_of_range_ayahs_are_ignored() {
        val subject = store()
        subject.markRead(1, 8)     // Al-Fatihah has 7
        subject.markRead(1, 0)
        subject.markRead(115, 1)   // no such surah
        assertEquals(0, subject.totalRead)
    }

    @Test
    fun unmarking_decrements_both_totals() {
        val subject = store()
        subject.markRead(112, 1)
        subject.markRead(112, 2)
        assertEquals(2, subject.readCountOf(112))

        subject.unmarkRead(112, 1)
        assertEquals(1, subject.readCountOf(112))
        assertEquals(1, subject.totalRead)
        assertFalse(subject.isRead(112, 1))
        assertTrue(subject.isRead(112, 2))
    }

    @Test
    fun toggle_flips_read_state() {
        val subject = store()
        subject.toggleRead(114, 1)
        assertTrue(subject.isRead(114, 1))
        subject.toggleRead(114, 1)
        assertFalse(subject.isRead(114, 1))
    }

    @Test
    fun last_read_position_survives_a_restart() {
        val persistence = InMemorySpiritualPersistence()
        store(persistence).markRead(2, 255)

        // A fresh store over the same storage is what an app relaunch looks like.
        val reopened = store(persistence)
        val last = assertNotNull(reopened.lastRead)
        assertEquals(2, last.surahNumber)
        assertEquals(255, last.ayahNumber)
        assertEquals("Al-Baqarah", last.surahName)
        assertEquals(1, reopened.totalRead)
        assertTrue(reopened.isRead(2, 255))
    }

    @Test
    fun selected_reciter_survives_a_restart() {
        val persistence = InMemorySpiritualPersistence()
        assertEquals(DEFAULT_RECITER.id, store(persistence).reciterId)

        store(persistence).setReciter("sudais")

        assertEquals("sudais", store(persistence).reciterId)
        assertEquals("Abdul Rahman Al-Sudais", store(persistence).reciter.name)
    }

    @Test
    fun an_unknown_reciter_id_falls_back_rather_than_breaking_playback() {
        val subject = store()
        subject.setReciter("not-a-qari")
        assertEquals(DEFAULT_RECITER.id, subject.reciterId)
    }

    @Test
    fun listening_position_is_kept_per_surah_and_survives_a_restart() {
        val persistence = InMemorySpiritualPersistence()
        val subject = store(persistence)
        subject.setLastListened(2, 10)
        subject.setLastListened(18, 3)

        val reopened = store(persistence)
        assertEquals(10, reopened.lastListenedOf(2))
        assertEquals(3, reopened.lastListenedOf(18))
        assertNull(reopened.lastListenedOf(114))
    }

    @Test
    fun corrupt_listening_positions_are_discarded_entry_by_entry() {
        val persistence = InMemorySpiritualPersistence()
        persistence.put(QuranProgressStore.KEY_LAST_LISTENED, "2:9999,nonsense,18:3")
        val subject = store(persistence)
        assertNull(subject.lastListenedOf(2))
        assertEquals(3, subject.lastListenedOf(18))
    }

    @Test
    fun corrupt_last_read_preference_is_discarded() {
        val persistence = InMemorySpiritualPersistence()
        persistence.put(QuranProgressStore.KEY_LAST_READ, "2:9999")
        assertNull(store(persistence).lastRead)

        persistence.put(QuranProgressStore.KEY_LAST_READ, "nonsense")
        assertNull(store(persistence).lastRead)
    }
}

class QuranAudioSourceTest {

    @Test
    fun url_encodes_surah_and_ayah_as_three_digits() {
        val url = QuranAudioSource.urlFor(reciterOf("alafasy"), 2, 255)
        assertEquals("https://everyayah.com/data/Alafasy_128kbps/002255.mp3", url)

        assertEquals(
            "https://everyayah.com/data/Alafasy_128kbps/001001.mp3",
            QuranAudioSource.urlFor(reciterOf("alafasy"), 1, 1),
        )
        assertEquals(
            "https://everyayah.com/data/Alafasy_128kbps/114006.mp3",
            QuranAudioSource.urlFor(reciterOf("alafasy"), 114, 6),
        )
    }

    @Test
    fun changing_reciter_changes_the_audio_source() {
        val alafasy = QuranAudioSource.urlFor(reciterOf("alafasy"), 2, 255)
        val sudais = QuranAudioSource.urlFor(reciterOf("sudais"), 2, 255)
        val ghamdi = QuranAudioSource.urlFor(reciterOf("ghamdi"), 2, 255)

        assertTrue(alafasy!!.contains("Alafasy_128kbps"))
        assertTrue(sudais!!.contains("Abdurrahmaan_As-Sudais_192kbps"))
        assertTrue(ghamdi!!.contains("Ghamadi_40kbps"))
        assertEquals(3, setOf(alafasy, sudais, ghamdi).size)
    }

    @Test
    fun five_reciters_are_offered_and_all_are_distinct() {
        assertEquals(5, RECITERS.size)
        assertEquals(5, RECITERS.map { it.id }.toSet().size)
        assertEquals(5, RECITERS.map { it.cdnFolder }.toSet().size)
    }

    @Test
    fun out_of_range_input_yields_no_url_instead_of_a_404() {
        assertNull(QuranAudioSource.urlFor(DEFAULT_RECITER, 0, 1))
        assertNull(QuranAudioSource.urlFor(DEFAULT_RECITER, 115, 1))
        assertNull(QuranAudioSource.urlFor(DEFAULT_RECITER, 1, 0))
    }
}

class BundledQuranRepositoryTest {

    private val repository = BundledQuranRepository { DEFAULT_RECITER }

    @Test
    fun returns_one_entry_per_ayah_even_when_text_is_missing() {
        val ayahs = repository.buildAyahs(2)

        assertEquals(286, ayahs.size)
        assertEquals((1..286).toList(), ayahs.map { it.number })
        // Every ayah is playable even where the text is not bundled.
        assertTrue(ayahs.all { it.audioUrl != null })
    }

    @Test
    fun bundled_text_is_present_and_unbundled_text_is_null_never_invented() {
        val fatihah = repository.buildAyahs(1)
        assertEquals(7, fatihah.size)
        assertTrue(fatihah.all { it.hasText })
        assertTrue(fatihah.all { !it.translation.isNullOrBlank() })

        val baqarah = repository.buildAyahs(2)
        assertTrue(baqarah.first { it.number == 255 }.hasText)    // Ayat al-Kursi is bundled
        assertNull(baqarah.first { it.number == 100 }.arabicText) // the rest is not faked
    }

    @Test
    fun unknown_surah_returns_empty() {
        assertTrue(repository.buildAyahs(0).isEmpty())
        assertTrue(repository.buildAyahs(115).isEmpty())
    }

    @Test
    fun ayah_ids_are_unique_across_the_mushaf() {
        val ids = listOf(1, 2, 114).flatMap { repository.buildAyahs(it) }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(2255, Ayah.idOf(2, 255))
    }

    @Test
    fun switching_reciter_restamps_every_audio_url() {
        val alafasy = repository.buildAyahs(114, reciterOf("alafasy"))
        val dosari = repository.buildAyahs(114, reciterOf("dosari"))

        assertTrue(alafasy.all { it.audioUrl!!.contains("Alafasy_128kbps") })
        assertTrue(dosari.all { it.audioUrl!!.contains("Yasser_Ad-Dussary_128kbps") })
    }
}
