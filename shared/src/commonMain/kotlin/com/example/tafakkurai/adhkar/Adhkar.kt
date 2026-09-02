package com.example.tafakkurai.adhkar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.spiritual.AdhkarPersistence
import com.example.tafakkurai.spiritual.AdhkarWriteScope
import com.example.tafakkurai.spiritual.AdhkarWriteTransaction
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class AdhkarCategory(val id: String, val label: String) {
    MORNING("morning", "Morning"),
    EVENING("evening", "Evening");

    companion object {
        fun from(id: String?): AdhkarCategory = entries.firstOrNull { it.id == id } ?: MORNING
    }
}

data class AdhkarItem(
    val id: String,
    val title: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    /** Sunnah repetition count for this dhikr. */
    val repeat: Int,
    val reference: String,
)

private fun item(
    id: String,
    title: String,
    arabic: String,
    translit: String,
    meaning: String,
    repeat: Int = 1,
    reference: String,
) = AdhkarItem(id, title, arabic, translit, meaning, repeat, reference)

// Shared between both lists — the protective recitations and the general adhkar that
// the sunnah prescribes morning *and* evening.
private val AYAT_AL_KURSI = item(
    "ayat-al-kursi", "Ayat al-Kursi",
    "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلْحَىُّ ٱلْقَيُّومُ ۚ لَا تَأْخُذُهُۥ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُۥ مَا فِى ٱلسَّمَٰوَٰتِ وَمَا فِى ٱلْأَرْضِ",
    "Allahu la ilaha illa huwal-Hayyul-Qayyum…",
    "Allah — there is no deity except Him, the Ever-Living, the Sustainer of existence.",
    reference = "Al-Baqarah 2:255",
)
private val AL_IKHLAS = item(
    "al-ikhlas", "Surah Al-Ikhlas",
    "قُلْ هُوَ ٱللَّهُ أَحَدٌ",
    "Qul huwa Allahu ahad…",
    "Say, He is Allah, who is One.",
    repeat = 3, reference = "Al-Ikhlas 112",
)
private val AL_FALAQ = item(
    "al-falaq", "Surah Al-Falaq",
    "قُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ",
    "Qul a'udhu bi Rabbil-falaq…",
    "Say, I seek refuge in the Lord of daybreak.",
    repeat = 3, reference = "Al-Falaq 113",
)
private val AN_NAS = item(
    "an-nas", "Surah An-Nas",
    "قُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ",
    "Qul a'udhu bi Rabbin-nas…",
    "Say, I seek refuge in the Lord of mankind.",
    repeat = 3, reference = "An-Nas 114",
)
private val SAYYID_ISTIGHFAR = item(
    "sayyid-al-istighfar", "Sayyid al-Istighfar",
    "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَىٰ عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، " +
        "أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
    "Allahumma anta Rabbi la ilaha illa anta…",
    "O Allah, You are my Lord. There is no god but You. You created me and I am Your servant. Forgive me, for none forgives sins but You.",
    reference = "Al-Bukhari 6306",
)
private val RADEETU = item(
    "radeetu-billahi", "Contentment with Allah",
    "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ نَبِيًّا",
    "Radeetu billahi Rabban, wa bil-Islami dinan, wa bi Muhammadin nabiyyan",
    "I am pleased with Allah as my Lord, with Islam as my religion, and with Muhammad ﷺ as my Prophet.",
    repeat = 3, reference = "Abu Dawud 5072",
)
private val TASBIH_100 = item(
    "subhanallah-wa-bihamdih", "SubhanAllahi wa bihamdih",
    "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
    "SubhanAllahi wa bihamdih",
    "Glory be to Allah and praise be to Him.",
    repeat = 100, reference = "Muslim 2691",
)
private val BISMILLAH_PROTECTION = item(
    "bismillah-la-yadurru", "Protection with Allah's name",
    "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
    "Bismillahil-ladhi la yadurru ma'as-mihi shay'un…",
    "In the name of Allah, with whose name nothing on earth or in heaven can cause harm; He is the All-Hearing, the All-Knowing.",
    repeat = 3, reference = "Abu Dawud 5088",
)
private val HASBIYALLAH = item(
    "hasbiyallah", "Hasbiyallah",
    "حَسْبِيَ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
    "Hasbiyallahu la ilaha illa huwa, 'alayhi tawakkaltu…",
    "Allah is sufficient for me. There is no god but Him. In Him I put my trust, and He is the Lord of the Mighty Throne.",
    repeat = 7, reference = "Abu Dawud 5081",
)
private val TAHLIL_10 = item(
    "la-ilaha-illallah-wahdahu", "Tahlil",
    "لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ",
    "La ilaha illallahu wahdahu la sharika lah…",
    "There is no god but Allah alone, without partner. His is the dominion and His is the praise, and He is able to do all things.",
    repeat = 10, reference = "Al-Bukhari 6403",
)
private val AAFINI = item(
    "allahumma-afini", "Well-being of body and senses",
    "اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي، لَا إِلَٰهَ إِلَّا أَنْتَ",
    "Allahumma 'afini fi badani…",
    "O Allah, grant my body well-being; grant my hearing well-being; grant my sight well-being. There is no god but You.",
    repeat = 3, reference = "Abu Dawud 5090",
)
private val YA_HAYYU = item(
    "ya-hayyu-ya-qayyum", "Ya Hayyu ya Qayyum",
    "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ وَلَا تَكِلْنِي إِلَىٰ نَفْسِي طَرْفَةَ عَيْنٍ",
    "Ya Hayyu ya Qayyumu birahmatika astaghith…",
    "O Ever-Living, O Sustainer, by Your mercy I seek help. Put right all my affairs and do not leave me to myself for the blink of an eye.",
    reference = "Al-Hakim 1/545",
)
private val ISTIGHFAR_100 = item(
    "astaghfirullah", "Istighfar",
    "أَسْتَغْفِرُ اللَّهَ وَأَتُوبُ إِلَيْهِ",
    "Astaghfirullaha wa atubu ilayh",
    "I seek the forgiveness of Allah and turn to Him in repentance.",
    repeat = 100, reference = "Al-Bukhari 6307",
)
private val SALAWAT = item(
    "salawat", "Salawat on the Prophet ﷺ",
    "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَىٰ نَبِيِّنَا مُحَمَّدٍ",
    "Allahumma salli wa sallim 'ala nabiyyina Muhammad",
    "O Allah, send prayers and peace upon our Prophet Muhammad.",
    repeat = 10, reference = "At-Tabarani",
)

val MORNING_ADHKAR: List<AdhkarItem> = listOf(
    AYAT_AL_KURSI, AL_IKHLAS, AL_FALAQ, AN_NAS, SAYYID_ISTIGHFAR,
    item(
        "asbahna", "Asbahna wa asbahal-mulku lillah",
        "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ",
        "Asbahna wa asbahal-mulku lillah…",
        "We have entered the morning and so has the dominion of Allah. Praise be to Allah. There is no god but Allah alone, without partner.",
        reference = "Muslim 2723",
    ),
    item(
        "bika-asbahna", "Allahumma bika asbahna",
        "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ",
        "Allahumma bika asbahna wa bika amsayna…",
        "O Allah, by You we enter the morning and by You we enter the evening; by You we live and by You we die, and to You is the resurrection.",
        reference = "At-Tirmidhi 3391",
    ),
    RADEETU, TASBIH_100, BISMILLAH_PROTECTION, HASBIYALLAH, TAHLIL_10, AAFINI, YA_HAYYU,
    item(
        "ilman-nafian", "Asking for beneficial knowledge",
        "اللَّهُمَّ إِنِّي أَسْأَلُكَ عِلْمًا نَافِعًا، وَرِزْقًا طَيِّبًا، وَعَمَلًا مُتَقَبَّلًا",
        "Allahumma inni as'aluka 'ilman nafi'an…",
        "O Allah, I ask You for beneficial knowledge, wholesome provision, and deeds that are accepted.",
        reference = "Ibn Majah 925",
    ),
    ISTIGHFAR_100, SALAWAT,
)

val EVENING_ADHKAR: List<AdhkarItem> = listOf(
    AYAT_AL_KURSI, AL_IKHLAS, AL_FALAQ, AN_NAS, SAYYID_ISTIGHFAR,
    item(
        "amsayna", "Amsayna wa amsal-mulku lillah",
        "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ",
        "Amsayna wa amsal-mulku lillah…",
        "We have entered the evening and so has the dominion of Allah. Praise be to Allah. There is no god but Allah alone, without partner.",
        reference = "Muslim 2723",
    ),
    item(
        "bika-amsayna", "Allahumma bika amsayna",
        "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ",
        "Allahumma bika amsayna wa bika asbahna…",
        "O Allah, by You we enter the evening and by You we enter the morning; by You we live and by You we die, and to You is the return.",
        reference = "At-Tirmidhi 3391",
    ),
    RADEETU, TASBIH_100, BISMILLAH_PROTECTION, HASBIYALLAH, TAHLIL_10, AAFINI, YA_HAYYU,
    item(
        "audhu-kalimatillah", "Refuge in the perfect words of Allah",
        "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
        "A'udhu bikalimatillahit-tammati min sharri ma khalaq",
        "I seek refuge in the perfect words of Allah from the evil of what He has created.",
        repeat = 3, reference = "Muslim 2709",
    ),
    ISTIGHFAR_100, SALAWAT,
)

fun adhkarFor(category: AdhkarCategory): List<AdhkarItem> = when (category) {
    AdhkarCategory.MORNING -> MORNING_ADHKAR
    AdhkarCategory.EVENING -> EVENING_ADHKAR
}

/**
 * Per-day completion of the morning and evening adhkar. A row exists only for a
 * completed item, so progress is a row count and unchecking is a delete — which keeps
 * "12 / 21" on this screen and on Today reading from exactly the same rows.
 */
class AdhkarStore(
    private val storage: AdhkarPersistence,
    private val todayKey: () -> String = ::deviceDateKey,
    private val activity: ActivityLog? = null,
    private val writes: AdhkarWriteTransaction? = null,
    private val now: () -> Long = ::epochMillis,
) {
    private var marks by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    private var loadedFor = ""
    private var lastActivityAt = 0L

    init { reload() }

    fun reload() {
        val key = todayKey()
        marks = storage.marksFor(key)
        loadedFor = key
    }

    private fun ensureToday() {
        if (todayKey() != loadedFor) reload()
    }

    fun isDone(category: AdhkarCategory, itemId: String): Boolean =
        itemId in marks[category.id].orEmpty()

    fun doneCount(category: AdhkarCategory): Int {
        val ids = adhkarFor(category).map { it.id }.toSet()
        return marks[category.id].orEmpty().count { it in ids }
    }

    fun totalCount(category: AdhkarCategory): Int = adhkarFor(category).size

    fun progressOf(category: AdhkarCategory): Float {
        val total = totalCount(category)
        return if (total == 0) 0f else (doneCount(category).toFloat() / total).coerceIn(0f, 1f)
    }

    fun percentOf(category: AdhkarCategory): Int = (progressOf(category) * 100).toInt()

    fun toggle(category: AdhkarCategory, itemId: String) {
        ensureToday()
        if (adhkarFor(category).none { it.id == itemId }) return
        val date = loadedFor
        val current = marks[category.id].orEmpty()
        val completed = itemId !in current
        val properties = buildJsonObject {
            put("date", date)
            put("category", category.id)
            put("item_id", itemId)
            put("completed", completed)
        }
        if (!commitToggle(
                completed = completed,
                date = date,
                category = category,
                itemId = itemId,
                properties = properties,
            )
        ) return
        marks = marks + (category.id to if (completed) current + itemId else current - itemId)
    }

    /**
     * The same entity is used for completion and undo, so projection replay always lets
     * the later compensating event clear only the item the user actually unchecked.
     */
    private fun commitToggle(
        completed: Boolean,
        date: String,
        category: AdhkarCategory,
        itemId: String,
        properties: JsonObject,
    ): Boolean {
        val strictWrite: AdhkarWriteScope.() -> Unit = {
            if (completed) mark(date, category.id, itemId) else unmark(date, category.id, itemId)
        }
        val fallbackWrite = {
            if (completed) storage.mark(date, category.id, itemId)
            else storage.unmark(date, category.id, itemId)
        }
        val transaction = writes
        if (transaction != null) {
            return transaction.commit {
                strictWrite()
                check(
                    activity?.record(
                        feature = "adhkar",
                        type = "adhkar_item_toggled",
                        properties = properties,
                        entityId = "adhkar:$date:${category.id}:$itemId",
                        occurredAtOverride = nextActivityTimestamp(),
                        localDateOverride = date,
                    ) ?: true,
                ) { "Could not append adhkar ActivityEvent" }
            }
        }
        if (activity != null) return false
        return runCatching(fallbackWrite).isSuccess
    }

    /** Millisecond ties must never let an uncheck sort before the check it compensates. */
    private fun nextActivityTimestamp(): Long = maxOf(now(), lastActivityAt + 1L).also {
        lastActivityAt = it
    }
}
