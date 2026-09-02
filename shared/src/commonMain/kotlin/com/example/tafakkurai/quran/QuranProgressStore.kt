package com.example.tafakkurai.quran

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.spiritual.QuranPersistence
import com.example.tafakkurai.spiritual.QuranWriteScope
import com.example.tafakkurai.spiritual.QuranWriteTransaction
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.sync.activityUuidV4
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    private val activity: ActivityLog? = null,
    private val writes: QuranWriteTransaction? = null,
    private val currentLanguageCode: () -> String? = { null },
) {
    private var readAyahIds by mutableStateOf<Set<Int>>(emptySet())

    /** surahNumber → ayahs read, kept in step with [readAyahIds] so list rows are O(1). */
    private var countsBySurah by mutableStateOf<Map<Int, Int>>(emptyMap())

    private var lastReadPref by mutableStateOf<LastRead?>(null)
    private var reciterPref by mutableStateOf(DEFAULT_RECITER.id)
    /** Null means the user has not chosen an edition, so the app-language default remains live. */
    private var translationPref by mutableStateOf<String?>(null)

    /** Which printed rasm the reader draws. One choice for the whole mushaf, not per surah. */
    private var mushafPref by mutableStateOf(DEFAULT_MUSHAF.id)

    /**
     * Whether the reader prints the meaning under each ayah. Off is the mushaf-style
     * Arabic-only read — which translation is selected stays remembered while it is off.
     */
    private var showTranslationPref by mutableStateOf(true)

    /** surahNumber → last ayah whose recitation was started, so playback resumes there. */
    private var lastListenedPref by mutableStateOf<Map<Int, Int>>(emptyMap())

    /** Surahs intentionally opened as a learning scope, independent of completed ayahs. */
    private var startedSurahsPref by mutableStateOf<Set<Int>>(emptySet())

    init { reload() }

    /** Re-read everything from storage. Call on app resume. */
    fun reload() {
        readAyahIds = storage.readAyahs()
        countsBySurah = readAyahIds.groupingBy { it / 1000 }.eachCount()
        reciterPref = prefs.get(KEY_RECITER) ?: DEFAULT_RECITER.id
        // Absence is meaningful: keep following the active app language until the user taps
        // an edition. Never persist a derived default or replace an existing valid choice.
        translationPref = prefs.get(KEY_TRANSLATION)
            ?.takeIf { stored -> TRANSLATIONS.any { it.id == stored } }
        mushafPref = mushafOf(prefs.get(KEY_MUSHAF)).id
        // Absent means never toggled — the meaning shows by default.
        showTranslationPref = prefs.get(KEY_SHOW_TRANSLATION) != "0"
        lastReadPref = prefs.get(KEY_LAST_READ)?.let(::decodeLastRead)
        lastListenedPref = decodeLastListened(prefs.get(KEY_LAST_LISTENED))
        startedSurahsPref = decodeStartedSurahs(prefs.get(KEY_STARTED_SURAHS))
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

    val translationId: String
        get() = translationPref ?: translationForAppLanguage(currentLanguageCode()).id

    val translation: TranslationEdition get() = translationOf(translationId)

    val mushafId: String get() = mushafPref

    val mushaf: MushafEdition get() = mushafOf(mushafPref)

    val showTranslation: Boolean get() = showTranslationPref

    /** Where recitation of this surah left off, if it was ever played. */
    fun lastListenedOf(surahNumber: Int): Int? = lastListenedPref[surahNumber]

    // ── Writes ───────────────────────────────────────────────────────────────

    /** Starts one durable exact-surah learning intention and its event atomically. */
    fun startSurah(surahNumber: Int) {
        if (surahOf(surahNumber) == null || surahNumber in startedSurahsPref) return
        val idKey = "$KEY_START_EVENT_PREFIX$surahNumber"
        val eventId = prefs.get(idKey) ?: activityUuidV4()
        val started = startedSurahsPref + surahNumber
        val encoded = started.sorted().joinToString(",")
        val stamp = now()
        val recorded = commitActivity(
            type = "surah_started",
            properties = buildJsonObject { put("surah", surahNumber) },
            entityId = "surah:$surahNumber",
            eventId = eventId,
            occurredAt = stamp,
            strictWrite = {
                putPreference(idKey, eventId)
                putPreference(KEY_STARTED_SURAHS, encoded)
            },
            fallbackWrite = {
                prefs.put(idKey, eventId)
                prefs.put(KEY_STARTED_SURAHS, encoded)
            },
        )
        if (!recorded) return
        startedSurahsPref = started
    }

    /** Marks an ayah read and moves the resume point. What tapping an ayah calls. */
    fun markRead(surahNumber: Int, ayahNumber: Int) {
        if (!isValid(surahNumber, ayahNumber)) return
        val id = Ayah.idOf(surahNumber, ayahNumber)
        if (id in readAyahIds) {
            setLastRead(surahNumber, ayahNumber)
            return
        }
        val surah = surahOf(surahNumber) ?: return
        val lastRead = LastRead(surahNumber, surah.nameEnglish, ayahNumber)
        val encodedLastRead = "$surahNumber:$ayahNumber"
        val stamp = now()
        val recorded = commitActivity(
            type = "ayah_marked_read",
            properties = ayahProperties(surahNumber, ayahNumber),
            entityId = "ayah:$surahNumber:$ayahNumber",
            occurredAt = stamp,
            strictWrite = {
                markRead(surahNumber, ayahNumber, stamp)
                putPreference(KEY_LAST_READ, encodedLastRead)
            },
            fallbackWrite = {
                storage.markRead(surahNumber, ayahNumber, stamp)
                prefs.put(KEY_LAST_READ, encodedLastRead)
            },
        )
        if (!recorded) return
        readAyahIds = readAyahIds + id
        countsBySurah = countsBySurah + (surahNumber to (readCountOf(surahNumber) + 1))
        lastReadPref = lastRead
    }

    fun unmarkRead(surahNumber: Int, ayahNumber: Int) {
        if (!isValid(surahNumber, ayahNumber)) return
        val id = Ayah.idOf(surahNumber, ayahNumber)
        if (id !in readAyahIds) return
        val stamp = now()
        val recorded = commitActivity(
            type = "ayah_unmarked_read",
            properties = ayahProperties(surahNumber, ayahNumber),
            entityId = "ayah:$surahNumber:$ayahNumber",
            occurredAt = stamp,
            strictWrite = { unmarkRead(surahNumber, ayahNumber) },
            fallbackWrite = { storage.unmarkRead(surahNumber, ayahNumber) },
        )
        if (!recorded) return
        readAyahIds = readAyahIds - id
        countsBySurah = countsBySurah +
            (surahNumber to (readCountOf(surahNumber) - 1).coerceAtLeast(0))
    }

    fun toggleRead(surahNumber: Int, ayahNumber: Int) {
        if (isRead(surahNumber, ayahNumber)) unmarkRead(surahNumber, ayahNumber)
        else markRead(surahNumber, ayahNumber)
    }

    fun setLastRead(surahNumber: Int, ayahNumber: Int) {
        val surah = surahOf(surahNumber) ?: return
        if (ayahNumber !in 1..surah.ayahCount) return
        val value = LastRead(surahNumber, surah.nameEnglish, ayahNumber)
        if (lastReadPref == value) return
        val encoded = "$surahNumber:$ayahNumber"
        if (!commitSourceWrite(
                strictWrite = { putPreference(KEY_LAST_READ, encoded) },
                fallbackWrite = { prefs.put(KEY_LAST_READ, encoded) },
            )
        ) return
        lastReadPref = value
    }

    /** Records the ayah being recited so the reader can resume it in a later session. */
    fun setLastListened(surahNumber: Int, ayahNumber: Int) {
        if (!isValid(surahNumber, ayahNumber)) return
        if (lastListenedPref[surahNumber] == ayahNumber) return
        val updated = lastListenedPref + (surahNumber to ayahNumber)
        val encoded = encodeLastListened(updated)
        val stamp = now()
        val recorded = commitActivity(
            type = "ayah_listened",
            properties = ayahProperties(surahNumber, ayahNumber),
            entityId = "ayah:$surahNumber:$ayahNumber",
            occurredAt = stamp,
            strictWrite = { putPreference(KEY_LAST_LISTENED, encoded) },
            fallbackWrite = { prefs.put(KEY_LAST_LISTENED, encoded) },
        )
        if (recorded) lastListenedPref = updated
    }

    fun setReciter(id: String) {
        val resolved = reciterOf(id)
        if (reciterPref == resolved.id) return
        val stamp = now()
        val recorded = commitActivity(
            type = "reciter_changed",
            properties = buildJsonObject { put("reciter_id", resolved.id) },
            entityId = "quran:settings:reciter",
            occurredAt = stamp,
            strictWrite = { putPreference(KEY_RECITER, resolved.id) },
            fallbackWrite = { prefs.put(KEY_RECITER, resolved.id) },
        )
        if (!recorded) return
        reciterPref = resolved.id
    }

    fun setTranslation(id: String) {
        val resolved = translationOf(id)
        // Comparing the saved preference (not the effective locale-derived value) matters:
        // explicitly choosing the currently shown default must pin that choice for later
        // app-language changes.
        if (translationPref == resolved.id) return
        val stamp = now()
        val recorded = commitActivity(
            type = "translation_changed",
            properties = buildJsonObject { put("translation_id", resolved.id) },
            entityId = "quran:settings:translation",
            occurredAt = stamp,
            strictWrite = { putPreference(KEY_TRANSLATION, resolved.id) },
            fallbackWrite = { prefs.put(KEY_TRANSLATION, resolved.id) },
        )
        if (!recorded) return
        translationPref = resolved.id
    }

    fun setMushaf(id: String) {
        val resolved = mushafOf(id)
        if (mushafPref == resolved.id) return
        val stamp = now()
        val recorded = commitActivity(
            type = "mushaf_changed",
            properties = buildJsonObject { put("mushaf_id", resolved.id) },
            entityId = "quran:settings:mushaf",
            occurredAt = stamp,
            strictWrite = { putPreference(KEY_MUSHAF, resolved.id) },
            fallbackWrite = { prefs.put(KEY_MUSHAF, resolved.id) },
        )
        if (!recorded) return
        mushafPref = resolved.id
    }

    fun setShowTranslation(on: Boolean) {
        if (showTranslationPref == on) return
        val encoded = if (on) "1" else "0"
        val stamp = now()
        val recorded = commitActivity(
            type = "translation_visibility_changed",
            properties = buildJsonObject { put("visible", on) },
            entityId = "quran:settings:translation-visibility",
            occurredAt = stamp,
            strictWrite = { putPreference(KEY_SHOW_TRANSLATION, encoded) },
            fallbackWrite = { prefs.put(KEY_SHOW_TRANSLATION, encoded) },
        )
        if (!recorded) return
        showTranslationPref = on
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /**
     * Uses the production SQL transaction port when present. A store wired to an
     * [ActivityLog] without that port fails closed because best-effort storage plus a durable
     * activity would split one action into two truths. Port-only constructions remain useful
     * for pure in-memory tests and previews where no activity log exists.
     */
    private fun commitSourceWrite(
        strictWrite: QuranWriteScope.() -> Unit,
        fallbackWrite: () -> Unit,
    ): Boolean {
        val transaction = writes
        if (transaction != null) return transaction.commit(strictWrite)
        if (activity != null) return false
        return runCatching(fallbackWrite).isSuccess
    }

    private fun commitActivity(
        type: String,
        properties: JsonObject,
        entityId: String,
        occurredAt: Long,
        eventId: String? = null,
        strictWrite: QuranWriteScope.() -> Unit,
        fallbackWrite: () -> Unit,
    ): Boolean {
        val transaction = writes
        if (transaction != null) {
            return transaction.commit {
                strictWrite()
                check(
                    activity?.record(
                        feature = "quran",
                        type = type,
                        properties = properties,
                        entityId = entityId,
                        eventId = eventId,
                        occurredAtOverride = occurredAt,
                    ) ?: true,
                ) { "Could not append Qurʼan ActivityEvent" }
            }
        }
        if (activity != null) return false
        return runCatching(fallbackWrite).isSuccess
    }

    private fun isValid(surahNumber: Int, ayahNumber: Int): Boolean =
        ayahNumber in 1..ayahCountOf(surahNumber)

    private fun ayahProperties(surahNumber: Int, ayahNumber: Int) = buildJsonObject {
        put("surah", surahNumber)
        put("ayah", ayahNumber)
    }

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

    private fun decodeStartedSurahs(raw: String?): Set<Int> = raw
        ?.split(',')
        ?.mapNotNull { it.toIntOrNull() }
        ?.filterTo(linkedSetOf()) { it in 1..114 }
        .orEmpty()

    companion object {
        const val KEY_LAST_READ: String = "quran.lastRead"
        const val KEY_LAST_LISTENED: String = "quran.lastListened"
        const val KEY_RECITER: String = "quran.reciter"
        const val KEY_TRANSLATION: String = "quran.translation"
        const val KEY_MUSHAF: String = "quran.mushaf"
        const val KEY_SHOW_TRANSLATION: String = "quran.showTranslation"
        const val KEY_STARTED_SURAHS: String = "quran.startedSurahs"
        const val KEY_START_EVENT_PREFIX: String = "quran.startEvent."
    }
}
