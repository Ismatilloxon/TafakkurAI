package com.example.tafakkurai.dhikr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.spiritual.DhikrPersistence
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.spiritual.streakLength

data class Dhikr(
    val id: String,
    val arabic: String,
    val transliteration: String,
    val meaning: String,
    val defaultTarget: Int,
)

val DHIKR_CATALOG: List<Dhikr> = listOf(
    Dhikr("subhanallah", "سُبْحَانَ ٱللَّٰه", "SubhanAllah", "Glory be to Allah", 33),
    Dhikr("alhamdulillah", "ٱلْحَمْدُ لِلَّٰه", "Alhamdulillah", "All praise is due to Allah", 33),
    Dhikr("allahuakbar", "ٱللَّٰهُ أَكْبَر", "Allahu Akbar", "Allah is the Greatest", 34),
    Dhikr("lailahaillallah", "لَا إِلَٰهَ إِلَّا ٱللَّٰه", "La ilaha illallah", "There is no god but Allah", 100),
    Dhikr("astaghfirullah", "أَسْتَغْفِرُ ٱللَّٰه", "Astaghfirullah", "I seek forgiveness from Allah", 100),
)

/** Quick-set targets under the counter. */
val QUICK_TARGETS: List<Int> = listOf(33, 66, 99, 100)

fun dhikrOf(id: String?): Dhikr = DHIKR_CATALOG.firstOrNull { it.id == id } ?: DHIKR_CATALOG.first()

/**
 * Tasbih state. Counts are per day *and* per dhikr, so "today's count" on the Today
 * dashboard, the per-dhikr ring, and the streak all read from one table. The day key is
 * re-checked on every mutation, which makes the counter roll over at midnight on its own.
 */
class DhikrStore(
    private val storage: DhikrPersistence,
    private val prefs: PrefPersistence,
    private val todayKey: () -> String = ::deviceDateKey,
) {
    private var counts by mutableStateOf<Map<String, Int>>(emptyMap())
    private var loadedFor = ""
    private var selectedId by mutableStateOf(DHIKR_CATALOG.first().id)
    private var targetValue by mutableStateOf(DHIKR_CATALOG.first().defaultTarget)
    private var favourites by mutableStateOf<Set<String>>(emptySet())

    init { reload() }

    fun reload() {
        val key = todayKey()
        counts = storage.countsFor(key)
        loadedFor = key
        selectedId = prefs.get(KEY_SELECTED)?.takeIf { id -> DHIKR_CATALOG.any { it.id == id } }
            ?: DHIKR_CATALOG.first().id
        targetValue = prefs.get(KEY_TARGET)?.toIntOrNull()?.takeIf { it > 0 }
            ?: dhikrOf(selectedId).defaultTarget
        favourites = prefs.get(KEY_FAVOURITES)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: setOf(DHIKR_CATALOG.first().id)
    }

    private fun ensureToday() {
        if (todayKey() != loadedFor) reload()
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    val selected: Dhikr get() = dhikrOf(selectedId)

    val target: Int get() = targetValue

    val count: Int get() = countOf(selectedId)

    fun countOf(id: String): Int = counts[id] ?: 0

    /** Every dhikr counted today, across all types — the Today "Dhikr" number. */
    val todayTotal: Int get() = counts.values.sum()

    val progress: Float get() = if (targetValue <= 0) 0f else (count.toFloat() / targetValue).coerceIn(0f, 1f)

    val isTargetReached: Boolean get() = targetValue > 0 && count >= targetValue

    fun isFavourite(id: String): Boolean = id in favourites

    val favouriteDhikr: List<Dhikr> get() = DHIKR_CATALOG.filter { it.id in favourites }

    /** Consecutive days ending today on which any dhikr was counted. */
    val streak: Int
        get() = streakLength(
            storage.dailyTotals().filter { it.second > 0 }.map { it.first },
            todayKey(),
        )

    // ── Writes ───────────────────────────────────────────────────────────────

    /** +1 on the selected dhikr. Returns true when this tap *reached* the target. */
    fun increment(): Boolean {
        ensureToday()
        val next = count + 1
        write(selectedId, next)
        return targetValue > 0 && next == targetValue
    }

    /** Clears today's count for the selected dhikr only — other dhikr keep theirs. */
    fun reset() {
        ensureToday()
        write(selectedId, 0)
    }

    fun select(id: String) {
        ensureToday()
        val dhikr = dhikrOf(id)
        selectedId = dhikr.id
        prefs.put(KEY_SELECTED, dhikr.id)
        // A fresh dhikr starts at its own sunnah target unless the user pinned one.
        setTarget(dhikr.defaultTarget)
    }

    fun setTarget(value: Int) {
        if (value <= 0) return
        targetValue = value
        prefs.put(KEY_TARGET, value.toString())
    }

    fun toggleFavourite(id: String) {
        val dhikr = dhikrOf(id)
        favourites = if (dhikr.id in favourites) favourites - dhikr.id else favourites + dhikr.id
        prefs.put(KEY_FAVOURITES, favourites.joinToString(","))
    }

    private fun write(id: String, value: Int) {
        counts = counts + (id to value)
        storage.put(loadedFor, id, value)
    }

    companion object {
        const val KEY_SELECTED: String = "dhikr.selected"
        const val KEY_TARGET: String = "dhikr.target"
        const val KEY_FAVOURITES: String = "dhikr.favourites"
    }
}
