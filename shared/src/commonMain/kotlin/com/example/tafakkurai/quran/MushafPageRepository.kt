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

// ─────────────────────────────────────────────────────────────────────────────
// The printed page.
//
// The ayah reader draws one ayah per row, which is a reading surface, not a mushaf.
// This layer models the other thing: the 604-page Madani mushaf as it is actually
// printed — fifteen lines to a page, the same words on the same lines, ayah numbers
// inside their end-of-verse roundels, surah banners and Basmalas taking the lines
// they take in print.
//
// The layout is *fetched*, never guessed: quran.com carries a `line_number` and a
// `page_number` on every word of the King Fahd Complex text, so a page here is that
// metadata grouped, not our own line-breaking of a paragraph.
// ─────────────────────────────────────────────────────────────────────────────

/** One word as printed — or the roundel that closes an ayah. */
data class MushafWord(
    val text: String,
    /** Ayah this word belongs to, for tap-to-play and for highlighting. */
    val ayahNumber: Int,
    val surahNumber: Int,
    /** True for the end-of-ayah roundel; [text] is then the number inside it. */
    val isEndMarker: Boolean,
)

/** What a line of the page carries. Banners and Basmalas occupy real printed lines. */
sealed interface MushafLine {
    val number: Int

    /** Words, right-to-left, justified across the full measure like the print. */
    data class Verse(override val number: Int, val words: List<MushafWord>) : MushafLine

    /** The ornamented band naming a surah that begins on this page. */
    data class SurahBanner(override val number: Int, val surahNumber: Int) : MushafLine

    /** The Basmala line under a banner — centred, never justified. */
    data class Basmala(override val number: Int, val surahNumber: Int) : MushafLine
}

/** One page of the mushaf, lines 1..[LINES_PER_PAGE] in printed order. */
data class MushafPage(
    val number: Int,
    val lines: List<MushafLine>,
    val juzNumber: Int,
    /** Every surah with at least one word on this page, in order. */
    val surahNumbers: List<Int>,
) {
    val ayahRange: IntRange?
        get() {
            val verses = lines.filterIsInstance<MushafLine.Verse>().flatMap { it.words }
            val first = verses.firstOrNull() ?: return null
            return first.ayahNumber..verses.last().ayahNumber
        }
}

const val LINES_PER_PAGE: Int = 15
const val MUSHAF_PAGE_COUNT: Int = 604

interface MushafPageRepository {
    /** The page range a surah spans, first..last, in the Madani mushaf. */
    suspend fun pagesOf(surahNumber: Int): IntRange

    /** One page, laid out. Throws if it cannot be fetched — the caller decides the fallback. */
    suspend fun getPage(pageNumber: Int): MushafPage
}

/**
 * quran.com v4 implementation.
 *
 * Builds its own [HttpClient] off the shared engine for the same reason
 * [RemoteQuranRepository] does: the app's client carries the user's Tafakkur bearer
 * token, which must never leave our host.
 */
class QuranComMushafPageRepository(
    engine: HttpClientEngine,
    private val baseUrl: String = RemoteQuranRepository.QURAN_COM_BASE_URL,
) : MushafPageRepository {

    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    // Pages are immutable print, so a page fetched once is a page forever.
    private val pages = mutableMapOf<Int, MushafPage>()
    private val ranges = mutableMapOf<Int, IntRange>()

    override suspend fun pagesOf(surahNumber: Int): IntRange {
        ranges[surahNumber]?.let { return it }
        val body: ChapterResponse = client.get("$baseUrl/chapters/$surahNumber").body()
        val span = body.chapter.pages
        val range = (span.firstOrNull() ?: 1)..(span.getOrNull(1) ?: span.firstOrNull() ?: 1)
        ranges[surahNumber] = range
        return range
    }

    override suspend fun getPage(pageNumber: Int): MushafPage {
        pages[pageNumber]?.let { return it }
        val url = "$baseUrl/verses/by_page/$pageNumber" +
            "?words=true&word_fields=line_number,text_uthmani&fields=text_uthmani&per_page=300"
        val body: PageResponse = client.get(url).body()
        val page = layout(pageNumber, body.verses)
        pages[pageNumber] = page
        return page
    }

    /** Warms a page without making the caller care whether it succeeded. */
    suspend fun prefetch(pageNumber: Int) {
        if (pageNumber in 1..MUSHAF_PAGE_COUNT) runCatching { getPage(pageNumber) }
    }
}

/**
 * Turns a page's verses into its printed lines.
 *
 * Two things are reconstructed rather than read off the wire. The line a word sits on is
 * given; the *banner* and *Basmala* lines are not — but the print reserves lines for them,
 * so a surah that begins on this page always leaves a gap above its first verse line.
 * Filling that gap top-down (banner, then Basmala where the surah carries one) reproduces
 * the printed page exactly, and a surah continuing from the previous page leaves no gap
 * and so gets neither.
 *
 * Internal rather than private so the layout can be tested without a network.
 */
internal fun layout(pageNumber: Int, verses: List<WireVerse>): MushafPage {
    val byLine = sortedMapOfLines()
    val surahs = mutableListOf<Int>()
    var juz = 0

    for (verse in verses) {
        val (surahNumber, ayahNumber) = verseKey(verse.verseKey) ?: continue
        if (surahs.lastOrNull() != surahNumber) surahs.add(surahNumber)
        if (juz == 0) juz = verse.juzNumber
        for (word in verse.words) {
            // A word can spill onto the next page mid-verse; keep only this page's.
            if (word.pageNumber != null && word.pageNumber != pageNumber) continue
            val isEnd = word.charType == "end"
            val text = word.textUthmani?.takeIf { it.isNotBlank() }
                ?: if (isEnd) arabicIndic(ayahNumber) else continue
            byLine.getOrPut(word.lineNumber) { mutableListOf() } += MushafWord(
                text = if (isEnd) arabicIndic(ayahNumber) else text,
                ayahNumber = ayahNumber,
                surahNumber = surahNumber,
                isEndMarker = isEnd,
            )
        }
    }

    val lines = mutableListOf<MushafLine>()
    // Which surahs open on this page, and on which line their first verse is printed.
    val openings: Map<Int, Int> = verses
        .filter { verseKey(it.verseKey)?.second == 1 }
        .mapNotNull { verse ->
            val surahNumber = verseKey(verse.verseKey)?.first ?: return@mapNotNull null
            val firstLine = verse.words.firstOrNull { it.charType == "word" }?.lineNumber
                ?: return@mapNotNull null
            firstLine to surahNumber
        }.toMap()

    var line = 1
    while (line <= LINES_PER_PAGE) {
        val words = byLine[line]
        if (words != null) {
            lines += MushafLine.Verse(line, words)
            line++
            continue
        }
        // An empty line only ever precedes a surah that opens here. Walk forward to the
        // opening it belongs to, then fill the gap with the banner and, if the surah
        // carries one, the Basmala directly under it.
        val nextOpening = openings.entries
            .filter { it.key > line }
            .minByOrNull { it.key }
        if (nextOpening == null) { line++; continue }
        val surahNumber = nextOpening.value
        val gap = nextOpening.key - line
        // Al-Fatihah and At-Tawbah print no separate Basmala line; every other opening does.
        val wantsBasmala = showsBasmala(surahNumber)
        lines += MushafLine.SurahBanner(line, surahNumber)
        if (gap >= 2 && wantsBasmala) lines += MushafLine.Basmala(line + 1, surahNumber)
        line = nextOpening.key
    }

    return MushafPage(
        number = pageNumber,
        lines = lines,
        juzNumber = juz,
        surahNumbers = surahs,
    )
}

private fun sortedMapOfLines(): MutableMap<Int, MutableList<MushafWord>> = linkedMapOf()

private fun verseKey(key: String): Pair<Int, Int>? {
    val surah = key.substringBefore(':').toIntOrNull() ?: return null
    val ayah = key.substringAfter(':').toIntOrNull() ?: return null
    return surah to ayah
}

/** 123 → "١٢٣". The number printed inside an end-of-ayah roundel. */
fun arabicIndic(value: Int): String =
    value.toString().map { ARABIC_INDIC_DIGITS[it - '0'] }.joinToString("")

private val ARABIC_INDIC_DIGITS = listOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

// ── Wire format (quran.com v4) ───────────────────────────────────────────────

@Serializable
internal data class PageResponse(val verses: List<WireVerse>)

@Serializable
internal data class WireVerse(
    @SerialName("verse_key") val verseKey: String,
    @SerialName("juz_number") val juzNumber: Int = 0,
    val words: List<WireWord> = emptyList(),
)

@Serializable
internal data class WireWord(
    @SerialName("char_type_name") val charType: String = "word",
    @SerialName("line_number") val lineNumber: Int = 1,
    @SerialName("page_number") val pageNumber: Int? = null,
    @SerialName("text_uthmani") val textUthmani: String? = null,
)

@Serializable
private data class ChapterResponse(val chapter: WireChapter)

@Serializable
private data class WireChapter(val id: Int, val pages: List<Int> = emptyList())
