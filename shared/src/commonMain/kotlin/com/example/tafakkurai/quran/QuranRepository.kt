package com.example.tafakkurai.quran

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The only thing the Qurʼan UI knows about where text comes from. Swapping the
 * bundled excerpt for a full mushaf API is a DI change, not a UI change.
 */
interface QuranRepository {
    suspend fun getSurahs(): List<Surah>

    /** Always returns exactly [ayahCountOf] entries; unavailable ones carry null text. */
    suspend fun getAyahs(surahId: Int): List<Ayah>

    suspend fun getReciters(): List<Reciter>
}

/**
 * Offline implementation. The surah index is complete (all 114), the *text* is the
 * verified excerpt in [BundledAyahText]; every other ayah comes back with null text
 * so the reader can say plainly that it has not been downloaded.
 *
 * [currentReciter] is a function, not a value, so changing qari immediately changes
 * the audio urls the next read produces without rebuilding the repository.
 */
class BundledQuranRepository(
    private val currentReciter: () -> Reciter = { DEFAULT_RECITER },
) : QuranRepository {

    override suspend fun getSurahs(): List<Surah> = SURAH_CATALOG

    override suspend fun getAyahs(surahId: Int): List<Ayah> = buildAyahs(surahId)

    override suspend fun getReciters(): List<Reciter> = RECITERS

    /**
     * The non-suspending core. Reading the bundle involves no I/O, and keeping it
     * separate lets unit tests exercise it directly — the common source set has no
     * `runBlocking`, so a suspend-only API would be untestable here.
     */
    fun buildAyahs(surahId: Int, reciter: Reciter = currentReciter()): List<Ayah> {
        val count = ayahCountOf(surahId)
        if (count == 0) return emptyList()
        return (1..count).map { n ->
            Ayah(
                id = Ayah.idOf(surahId, n),
                surahId = surahId,
                number = n,
                arabicText = BundledAyahText.arabicOf(surahId, n),
                translation = BundledAyahText.englishOf(surahId, n),
                audioUrl = QuranAudioSource.urlFor(reciter, surahId, n),
            )
        }
    }
}

/**
 * Network implementation over the public alquran.cloud mushaf API, with the bundled
 * excerpt as the fallback for every failure mode (offline, timeout, bad response).
 *
 * It deliberately builds its own [HttpClient] from the shared engine instead of reusing
 * the app's authenticated client — that client attaches the user's Tafakkur bearer token
 * to every request, which must never be sent to a third-party host.
 */
class RemoteQuranRepository(
    engine: HttpClientEngine,
    private val fallback: BundledQuranRepository,
    private val currentReciter: () -> Reciter = { DEFAULT_RECITER },
    private val currentTranslation: () -> TranslationEdition = { DEFAULT_TRANSLATION },
    private val currentMushaf: () -> MushafEdition = { DEFAULT_MUSHAF },
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val scriptBaseUrl: String = QURAN_COM_BASE_URL,
) : QuranRepository {

    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    /** "surah/translation/mushaf" → ayahs. Text never changes, so a plain memo is enough. */
    private val cache = mutableMapOf<String, List<Ayah>>()

    override suspend fun getSurahs(): List<Surah> = SURAH_CATALOG

    override suspend fun getReciters(): List<Reciter> = RECITERS

    override suspend fun getAyahs(surahId: Int): List<Ayah> {
        val count = ayahCountOf(surahId)
        if (count == 0) return emptyList()
        val translation = currentTranslation()
        val mushaf = currentMushaf()
        val key = "$surahId/${translation.id}/${mushaf.id}"
        cache[key]?.let { return withCurrentAudio(it) }

        val fetched = runCatching { fetch(surahId, count, translation, mushaf) }.getOrNull()
        if (fetched != null) {
            cache[key] = fetched
            return withCurrentAudio(fetched)
        }
        // Offline or the endpoint changed shape — fall back to what is bundled. The
        // reader still renders, and unbundled ayahs surface an honest empty state. The
        // bundled excerpt is English only, so any other choice shows no translation
        // rather than a translation in the wrong language.
        val bundled = fallback.getAyahs(surahId)
        return if (translation.editionCode == ENGLISH_EDITION) bundled
        else bundled.map { it.copy(translation = null) }
    }

    private suspend fun fetch(
        surahId: Int,
        count: Int,
        translation: TranslationEdition,
        mushaf: MushafEdition,
    ): List<Ayah> {
        // A mushaf served by alquran.cloud rides the translation call; one served elsewhere
        // still needs *some* Arabic edition here, and Uthmani is the safe base to overlay.
        val arabicEdition = (mushaf.source as? MushafSource.Cloud)?.editionCode ?: ARABIC_EDITION
        val url = "$baseUrl/surah/$surahId/editions/$arabicEdition,${translation.editionCode}"
        val body: CloudResponse = client.get(url).body()
        val arabic = body.data.firstOrNull()?.ayahs.orEmpty()
            .associateBy({ it.numberInSurah }, { withoutLeadingBasmala(surahId, it.text) })
        val meaning = body.data.getOrNull(1)?.ayahs.orEmpty().associateBy({ it.numberInSurah }, { it.text })
        if (arabic.isEmpty()) error("no ayah text in response for surah $surahId")
        // Scripts this API does not carry are fetched separately. A failure here is not
        // fatal — the ayahs still render in the Uthmani text already in hand.
        val script = (mushaf.source as? MushafSource.QuranCom)
            ?.let { runCatching { fetchScript(surahId, it.script) }.getOrNull() }
            .orEmpty()
        val reciter = currentReciter()
        val bundledMeaning: (Int) -> String? =
            if (translation.editionCode == ENGLISH_EDITION) { n -> BundledAyahText.englishOf(surahId, n) }
            else { _ -> null }
        return (1..count).map { n ->
            val text = meaning[n] ?: bundledMeaning(n)
            Ayah(
                id = Ayah.idOf(surahId, n),
                surahId = surahId,
                number = n,
                arabicText = script[n] ?: arabic[n] ?: BundledAyahText.arabicOf(surahId, n),
                translation = text?.let { if (translation.toUzbekLatin) UzbekLatin.transliterate(it) else it },
                audioUrl = QuranAudioSource.urlFor(reciter, surahId, n),
            )
        }
    }

    /**
     * One surah in a quran.com script. The text field is named after the script
     * (`text_indopak`), so the response is read as raw JSON rather than through a data
     * class per script — the shape is otherwise identical for all of them.
     */
    private suspend fun fetchScript(surahId: Int, script: String): Map<Int, String> {
        val url = "$scriptBaseUrl/quran/verses/$script?chapter_number=$surahId"
        val body: JsonObject = client.get(url).body()
        val verses = body["verses"]?.jsonArray.orEmpty()
        return verses.mapNotNull { element ->
            val verse = element.jsonObject
            val key = verse["verse_key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val number = key.substringAfter(':').toIntOrNull() ?: return@mapNotNull null
            val text = verse["text_$script"]?.jsonPrimitive?.contentOrNull?.trim()
            if (text.isNullOrBlank()) null else number to text
        }.toMap()
    }

    /** Re-stamps cached ayahs with the qari selected right now. */
    private fun withCurrentAudio(ayahs: List<Ayah>): List<Ayah> {
        val reciter = currentReciter()
        return ayahs.map { it.copy(audioUrl = QuranAudioSource.urlFor(reciter, it.surahId, it.number)) }
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "https://api.alquran.cloud/v1"

        /** Second, unauthenticated host — the only source for the Indo-Pak rasm. */
        const val QURAN_COM_BASE_URL: String = "https://api.quran.com/api/v4"
        private const val ARABIC_EDITION = "quran-uthmani"
        private const val ENGLISH_EDITION = "en.sahih"
    }
}

// ── Basmala normalisation ────────────────────────────────────────────────────

/**
 * Drops the Basmala from the front of an ayah.
 *
 * alquran.cloud prints it as part of ayah 1 of every surah that opens with one, quran.com
 * does not, and the reader draws it in the surah header either way. Left alone it would
 * appear twice — and only in some mushafs, which would look like the script picker was
 * changing the text of the ayah rather than its rasm.
 *
 * Matching is done on letters alone: the same four words are spelled differently in every
 * rasm (ٱ vs ا, written vs superscript alef), so comparing marked text would miss.
 */
internal fun withoutLeadingBasmala(surahNumber: Int, text: String): String {
    // Al-Fatihah's Basmala *is* ayah 1, and At-Tawbah has none to remove.
    if (!showsBasmala(surahNumber)) return text
    val words = text.trim().split(' ').filter { it.isNotBlank() }
    if (words.size <= BASMALA_LETTERS.size) return text
    val head = words.take(BASMALA_LETTERS.size).map(::arabicLettersOf)
    if (head != BASMALA_LETTERS) return text
    return words.drop(BASMALA_LETTERS.size).joinToString(" ")
}

private val BASMALA_LETTERS: List<String> = listOf("بسم", "الله", "الرحمن", "الرحيم")

/** The consonantal skeleton of a word: harakat, quranic annotation and bidi marks gone. */
private fun arabicLettersOf(word: String): String = buildString {
    for (ch in word) {
        when (ch.code) {
            0x0671 -> append('ا')          // alef wasla → plain alef
            0x0640 -> Unit                      // tatweel
            0x0670 -> Unit                      // superscript (dagger) alef
            in 0x064B..0x065F -> Unit           // harakat, shadda, sukun
            in 0x06D6..0x06ED -> Unit           // small high/low quranic marks
            0x200D, 0x200E, 0x200F -> Unit      // zero-width joiner, bidi marks
            else -> append(ch)
        }
    }
}

// ── Wire format (alquran.cloud) ──────────────────────────────────────────────

@Serializable
private data class CloudResponse(val code: Int, val data: List<CloudEdition>)

@Serializable
private data class CloudEdition(val number: Int, val ayahs: List<CloudAyah>)

@Serializable
private data class CloudAyah(
    @SerialName("numberInSurah") val numberInSurah: Int,
    val text: String,
)
