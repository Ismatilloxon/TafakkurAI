package com.example.tafakkurai.quran

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.spiritual.QuranPersistence

/**
 * Local-first, observable source of truth for Qurʼan reading progress — the same shape
 * as `PrayerMarkStore`: persisted through a storage port, mirrored in Compose state so
 * the surah list, the reader, the Qurʼan tab header and the Today dashboard all
 * recompose together and can never show different numbers.
 *
 * Every percentage is counted from individual read ayahs. Completing a surah is a
 * consequence of having read its ayahs, never a separate flag.
 */
class QuranProgressStore(
    private val storage: QuranPersistence,
    private val prefs: PrefPersistence,
    private val now: () -> Long = ::epochMillis,
) {
    private var readAyahIds by mutableStateOf<Set<Int>>(emptySet())

    /** surahNumber → ayahs read, kept in step with [readAyahIds] so list rows are O(1). */
    private var countsBySurah by mutableStateOf<Map<Int, Int>>(emptyMap())

    private var lastReadPref by mutableStateOf<LastRead?>(null)
    private var reciterPref by mutableStateOf(DEFAULT_RECITER.id)
    private var translationPref by mutableStateOf(DEFAULT_TRANSLATION.id)

    /** Which printed rasm the reader draws. One choice for the whole mushaf, not per surah. */
    private var mushafPref by mutableStateOf(DEFAULT_MUSHAF.id)

    /**
     * Whether the reader prints the meaning under each ayah. Off is the mushaf-style
     * Arabic-only read — which translation is selected stays remembered while it is off.
     */
    private var showTranslationPref by mutableStateOf(true)

    /** surahNumber → last ayah whose recitation was started, so playback resumes there. */
    private var lastListenedPref by mutableStateOf<Map<Int, Int>>(emptyMap())

    init { reload() }

    /** Re-read everything from storage. Call on app resume. */
    fun reload() {
        readAyahIds = storage.readAyahs()
        countsBySurah = readAyahIds.groupingBy { it / 1000 }.eachCount()
        reciterPref = prefs.get(KEY_RECITER) ?: DEFAULT_RECITER.id
        translationPref = translationOf(prefs.get(KEY_TRANSLATION)).id
        mushafPref = mushafOf(prefs.get(KEY_MUSHAF)).id
        // Absent means never toggled — the meaning shows by default.
        showTranslationPref = prefs.get(KEY_SHOW_TRANSLATION) != "0"
        lastReadPref = prefs.get(KEY_LAST_READ)?.let(::decodeLastRead)
        lastListenedPref = decodeLastListened(prefs.get(KEY_LAST_LISTENED))
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    fun isRead(surahNumber: Int, ayahNumber: Int): Boolean =
        Ayah.idOf(surahNumber, ayahNumber) in readAyahIds

    fun readCountOf(surahNumber: Int): Int = countsBySurah[surahNumber] ?: 0

    fun progressOf(surahNumber: Int): SurahProgress =
        SurahProgress(surahNumber, readCountOf(surahNumber), ayahCountOf(surahNumber))

    /** Total ayahs read across the whole mushaf. */
    val totalRead: Int get() = readAyahIds.size

    /**
     * Overall Qurʼan progress, ayah-level: total read ÷ 6,236. Reading 7 of Al-Fatihah
     * and 100 of Al-Baqarah gives 107/6236, not "1 of 114 surahs".
     */
    val overallProgress: Float
        get() = if (TOTAL_AYAH_COUNT == 0) 0f else (totalRead.toFloat() / TOTAL_AYAH_COUNT).coerceIn(0f, 1f)

    val overallPercent: Int get() = (overallProgress * 100).toInt()

    val completedSurahCount: Int
        get() = SURAH_CATALOG.count { (countsBySurah[it.number] ?: 0) >= it.ayahCount }

    val startedSurahCount: Int get() = countsBySurah.count { it.value > 0 }

    val lastRead: LastRead? get() = lastReadPref

    val reciterId: String get() = reciterPref

    val reciter: Reciter get() = reciterOf(reciterPref)

    val translationId: String get() = translationPref

    val translation: TranslationEdition get() = translationOf(translationPref)

    val mushafId: String get() = mushafPref

    val mushaf: MushafEdition get() = mushafOf(mushafPref)

    val showTranslation: Boolean get() = showTranslationPref

    /** Where recitation of this surah left off, if it was ever played. */
    fun lastListenedOf(surahNumber: Int): Int? = lastListenedPref[surahNumber]

    // ── Writes ───────────────────────────────────────────────────────────────

    /** Marks an ayah read and moves the resume point. What tapping an ayah calls. */
    fun markRead(surahNumber: Int, ayahNumber: Int) {
        if (!isValid(surahNumber, ayahNumber)) return
        val id = Ayah.idOf(surahNumber, ayahNumber)
        if (id !in readAyahIds) {
            readAyahIds = readAyahIds + id
            countsBySurah = countsBySurah + (surahNumber to (readCountOf(surahNumber) + 1))
            storage.markRead(surahNumber, ayahNumber, now())
        }
        setLastRead(surahNumber, ayahNumber)
    }

    fun unmarkRead(surahNumber: Int, ayahNumber: Int) {
        if (!isValid(surahNumber, ayahNumber)) return
        val id = Ayah.idOf(surahNumber, ayahNumber)
        if (id in readAyahIds) {
            readAyahIds = readAyahIds - id
            countsBySurah = countsBySurah + (surahNumber to (readCountOf(surahNumber) - 1).coerceAtLeast(0))
            storage.unmarkRead(surahNumber, ayahNumber)
        }
    }

    fun toggleRead(surahNumber: Int, ayahNumber: Int) {
        if (isRead(surahNumber, ayahNumber)) unmarkRead(surahNumber, ayahNumber)
        else markRead(surahNumber, ayahNumber)
    }

    fun setLastRead(surahNumber: Int, ayahNumber: Int) {
        val surah = surahOf(surahNumber) ?: return
        val value = LastRead(surahNumber, surah.nameEnglish, ayahNumber)
        lastReadPref = value
        prefs.put(KEY_LAST_READ, "$surahNumber:$ayahNumber")
    }

    /** Records the ayah being recited so the reader can resume it in a later session. */
    fun setLastListened(surahNumber: Int, ayahNumber: Int) {
        if (!isValid(surahNumber, ayahNumber)) return
        lastListenedPref = lastListenedPref + (surahNumber to ayahNumber)
        prefs.put(KEY_LAST_LISTENED, encodeLastListened(lastListenedPref))
    }

    fun setReciter(id: String) {
        val resolved = reciterOf(id)
        reciterPref = resolved.id
        prefs.put(KEY_RECITER, resolved.id)
    }

    fun setTranslation(id: String) {
        val resolved = translationOf(id)
        translationPref = resolved.id
        prefs.put(KEY_TRANSLATION, resolved.id)
    }

    fun setMushaf(id: String) {
        val resolved = mushafOf(id)
        mushafPref = resolved.id
        prefs.put(KEY_MUSHAF, resolved.id)
    }

    fun setShowTranslation(on: Boolean) {
        showTranslationPref = on
        prefs.put(KEY_SHOW_TRANSLATION, if (on) "1" else "0")
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun isValid(surahNumber: Int, ayahNumber: Int): Boolean =
        ayahNumber in 1..ayahCountOf(surahNumber)

    private fun decodeLastRead(raw: String): LastRead? {
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val surahNumber = parts[0].toIntOrNull() ?: return null
        val ayahNumber = parts[1].toIntOrNull() ?: return null
        val surah = surahOf(surahNumber) ?: return null
        if (ayahNumber !in 1..surah.ayahCount) return null
        return LastRead(surahNumber, surah.nameEnglish, ayahNumber)
    }

    /** "2:10,18:3" — one entry per surah ever played. Bad entries are dropped, not fatal. */
    private fun encodeLastListened(map: Map<Int, Int>): String =
        map.entries.joinToString(",") { "${it.key}:${it.value}" }

    private fun decodeLastListened(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val surahNumber = parts[0].toIntOrNull() ?: return@mapNotNull null
            val ayahNumber = parts[1].toIntOrNull() ?: return@mapNotNull null
            if (!isValid(surahNumber, ayahNumber)) return@mapNotNull null
            surahNumber to ayahNumber
        }.toMap()
    }

    companion object {
        const val KEY_LAST_READ: String = "quran.lastRead"
        const val KEY_LAST_LISTENED: String = "quran.lastListened"
        const val KEY_RECITER: String = "quran.reciter"
        const val KEY_TRANSLATION: String = "quran.translation"
        const val KEY_MUSHAF: String = "quran.mushaf"
        const val KEY_SHOW_TRANSLATION: String = "quran.showTranslation"
    }
}
