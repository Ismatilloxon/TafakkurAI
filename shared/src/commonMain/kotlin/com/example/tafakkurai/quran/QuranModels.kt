package com.example.tafakkurai.quran

/**
 * Shared Qurʼan domain models. Deliberately free of Compose and of any single data
 * source: the same types come back from the bundled catalog and from a remote API,
 * so swapping [QuranRepository] implementations never touches the UI.
 */

enum class RevelationType { MECCAN, MEDINAN }

data class Surah(
    val id: Int,
    val number: Int,
    val nameArabic: String,
    val nameEnglish: String,
    /** English meaning of the name — "The Opening", "The Cow". */
    val nameTranslation: String,
    val revelationType: RevelationType,
    val ayahCount: Int,
)

/**
 * One ayah. [arabicText] and [translation] are nullable on purpose: the app ships a
 * verified bundled excerpt of the mushaf, and everything outside it must be fetched.
 * A null text renders an explicit "not available" state — never invented scripture.
 */
data class Ayah(
    val id: Int,
    val surahId: Int,
    val number: Int,
    val arabicText: String?,
    val translation: String?,
    val audioUrl: String?,
) {
    val hasText: Boolean get() = !arabicText.isNullOrBlank()

    companion object {
        /** Stable global id — surah 2 ayah 255 becomes 2255. Max ayah count is 286. */
        fun idOf(surahNumber: Int, ayahNumber: Int): Int = surahNumber * 1000 + ayahNumber
    }
}

data class Reciter(
    val id: String,
    val name: String,
    val nameArabic: String,
    /**
     * Folder on the everyayah.com ayah-by-ayah CDN. Audio urls are built as
     * `<base>/<folder>/<surah:3><ayah:3>.mp3`, so a different CDN only changes
     * [QuranAudioSource], not this model and not the UI.
     */
    val cdnFolder: String,
    val imageUrl: String? = null,
)

/**
 * One offered translation of the meaning.
 *
 * [editionCode] is the mushaf-API edition the text is fetched from; [toUzbekLatin] respells
 * a Cyrillic edition in Latin script through [UzbekLatin]. Two entries may therefore share
 * one [editionCode] — same translator, same words, different script.
 */
data class TranslationEdition(
    val id: String,
    val editionCode: String,
    val toUzbekLatin: Boolean = false,
) {
    /** Catalog keys keep both the edition name and translator in the active app language. */
    val labelKey: String get() = "quran.translation.$id.label"
    val translatorKey: String get() = "quran.translation.$id.translator"
}

/**
 * The four translations offered at launch. Only one Uzbek translation exists on the API
 * (Cyrillic), so the Latin entry is the same text transliterated rather than a second
 * translation of unknown provenance.
 */
val TRANSLATIONS: List<TranslationEdition> = listOf(
    TranslationEdition("en", "en.sahih"),
    TranslationEdition("ru", "ru.kuliev"),
    TranslationEdition("uz-cyrl", "uz.sodik"),
    TranslationEdition(
        "uz-latn", "uz.sodik", toUzbekLatin = true,
    ),
)

val DEFAULT_TRANSLATION: TranslationEdition = TRANSLATIONS.first()

fun translationOf(id: String?): TranslationEdition =
    TRANSLATIONS.firstOrNull { it.id == id } ?: DEFAULT_TRANSLATION

/**
 * Translation used while the reader has no explicit translation preference.
 *
 * App-language codes deliberately live at this boundary as strings: the Qurʼan domain does
 * not depend on the interface-language store, while the composition root can still provide
 * the active language. Unknown codes retain the established English fallback.
 */
fun translationForAppLanguage(languageCode: String?): TranslationEdition = when (languageCode) {
    "uz-Latn" -> translationOf("uz-latn")
    "uz-Cyrl" -> translationOf("uz-cyrl")
    "ru" -> translationOf("ru")
    "en" -> translationOf("en")
    else -> DEFAULT_TRANSLATION
}

// ── Mushaf (script) ──────────────────────────────────────────────────────────

/** Where a mushaf's Arabic text is fetched from. */
sealed interface MushafSource {
    /** An alquran.cloud edition — comes back in the same call as the translation. */
    data class Cloud(val editionCode: String) : MushafSource

    /** A quran.com v4 script (`text_<script>`) — a second call, overlaid on the first. */
    data class QuranCom(val script: String) : MushafSource
}

/**
 * A printed mushaf tradition, chosen in the reader's settings sheet.
 *
 * The three offered differ in *rasm* — which letters and marks are actually printed — never
 * in the words: Madani prints the Uthmani rasm of the King Fahd Complex (ٱ wasla alef,
 * superscript alef), Turkish prints the imlāʼī spelling Diyanet-style copies use, and
 * Indo-Pak prints the sub-continental rasm (small high sukun, اَ with its fatha written).
 * Choosing one changes the text the reader fetches, so the letters on the page change.
 *
 * [basmala] is per-mushaf for the same reason: the header line must be in the same script
 * as the ayahs under it.
 */
data class MushafEdition(
    val id: String,
    /** Shown in the picker: "Madani Mushaf". */
    val label: String,
    /** One line saying what the script is, so the choice is not a guess. */
    val note: String,
    val source: MushafSource,
    val basmala: String,
    /** Indo-Pak stacks marks above and below the line, so it needs more room. */
    val lineHeight: Float = 1.95f,
)

// ── Basmala ──────────────────────────────────────────────────────────────────

/** The Basmala, as it opens a surah. Identical to ayah 1 of Al-Fatihah. */
const val BASMALA: String = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

const val BASMALA_MEANING: String = "In the name of Allah, the Entirely Merciful, the Especially Merciful."

/**
 * The three mushafs offered. Madani is the default: it is the text the app has always
 * shown, the one the bundled offline excerpt is written in, and the most widely printed.
 */
val MUSHAFS: List<MushafEdition> = listOf(
    MushafEdition(
        id = "madani",
        label = "Madani Mushaf",
        note = "Uthmani rasm — King Fahd Complex, Madinah",
        source = MushafSource.Cloud("quran-uthmani"),
        basmala = BASMALA,
    ),
    MushafEdition(
        id = "turkish",
        label = "Turkish Mushaf",
        note = "Imlāʼī spelling — as Turkish copies print it",
        source = MushafSource.Cloud("quran-simple-enhanced"),
        basmala = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
    ),
    MushafEdition(
        id = "indopak",
        label = "Indo-Pak Mushaf",
        note = "Sub-continental rasm — Taj/Tajweedi style",
        source = MushafSource.QuranCom("indopak"),
        basmala = "بِسۡمِ اللهِ الرَّحۡمٰنِ الرَّحِيۡمِ",
        lineHeight = 2.3f,
    ),
)

val DEFAULT_MUSHAF: MushafEdition = MUSHAFS.first()

fun mushafOf(id: String?): MushafEdition = MUSHAFS.firstOrNull { it.id == id } ?: DEFAULT_MUSHAF

/**
 * Whether the reader should print the Basmala above a surah's first ayah.
 *
 * Every surah opens with it except two, and both exceptions are about not printing it
 * twice or where it does not belong: in Al-Fatihah it *is* ayah 1, so the ayah list
 * already shows it, and At-Tawbah (9) has no Basmala in the mushaf.
 */
fun showsBasmala(surahNumber: Int): Boolean = surahNumber != 1 && surahNumber != 9

/** Where the reader should resume. Persisted per-user by [QuranProgressStore]. */
data class LastRead(
    val surahNumber: Int,
    val surahName: String,
    val ayahNumber: Int,
)

/** Ayah-level progress for one surah. */
data class SurahProgress(
    val surahNumber: Int,
    val readCount: Int,
    val ayahCount: Int,
) {
    val fraction: Float get() = if (ayahCount <= 0) 0f else (readCount.toFloat() / ayahCount).coerceIn(0f, 1f)
    val percent: Int get() = (fraction * 100).toInt()
    val isComplete: Boolean get() = ayahCount > 0 && readCount >= ayahCount
}

// ── Reciters ─────────────────────────────────────────────────────────────────

/**
 * The five qurraʼ offered at launch. Folder names are the real everyayah.com
 * directories, so [QuranAudioSource.urlFor] produces playable mp3 urls.
 */
val RECITERS: List<Reciter> = listOf(
    Reciter("alafasy", "Mishary Alafasy", "مشاري العفاسي", "Alafasy_128kbps"),
    Reciter("sudais", "Abdul Rahman Al-Sudais", "عبد الرحمن السديس", "Abdurrahmaan_As-Sudais_192kbps"),
    Reciter("ghamdi", "Saad Al-Ghamdi", "سعد الغامدي", "Ghamadi_40kbps"),
    Reciter("muaiqly", "Maher Al-Muaiqly", "ماهر المعيقلي", "Maher_AlMuaiqly_64kbps"),
    Reciter("dosari", "Yasser Al-Dosari", "ياسر الدوسري", "Yasser_Ad-Dussary_128kbps"),
)

val DEFAULT_RECITER: Reciter = RECITERS.first()

fun reciterOf(id: String?): Reciter = RECITERS.firstOrNull { it.id == id } ?: DEFAULT_RECITER

/**
 * Builds ayah audio urls. Kept separate from the repository so the CDN can be
 * swapped (or pointed at a self-hosted mirror) without touching playback or UI.
 */
object QuranAudioSource {
    const val BASE_URL: String = "https://everyayah.com/data"

    private fun pad3(n: Int): String = when {
        n >= 100 -> "$n"
        n >= 10 -> "0$n"
        else -> "00$n"
    }

    /** Null for out-of-range input rather than a url that would 404 at play time. */
    fun urlFor(reciter: Reciter, surahNumber: Int, ayahNumber: Int): String? {
        if (surahNumber !in 1..114 || ayahNumber < 1) return null
        return "$BASE_URL/${reciter.cdnFolder}/${pad3(surahNumber)}${pad3(ayahNumber)}.mp3"
    }
}
