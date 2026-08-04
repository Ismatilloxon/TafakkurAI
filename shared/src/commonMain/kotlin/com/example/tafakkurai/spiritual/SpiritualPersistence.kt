package com.example.tafakkurai.spiritual

/**
 * Storage ports for the Qurʼan / dhikr / adhkar stores.
 *
 * The stores hold all the arithmetic — progress percentages, counter targets, streaks —
 * and talk to storage only through these four narrow interfaces. That keeps every rule
 * unit-testable against [InMemorySpiritualPersistence] with no SQLite driver, while the
 * app runs on the SQLDelight implementations in `SqlSpiritualPersistence`.
 */

/** Ayah-level read tracking. Keys are packed as `surah * 1000 + ayah`. */
interface QuranPersistence {
    fun readAyahs(): Set<Int>
    fun markRead(surah: Int, ayah: Int, at: Long)
    fun unmarkRead(surah: Int, ayah: Int)
}

/** Single-value settings: last-read position, selected qari, dhikr target. */
interface PrefPersistence {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

interface DhikrPersistence {
    fun countsFor(dateKey: String): Map<String, Int>
    fun put(dateKey: String, dhikrId: String, total: Int)

    /** (dateKey, total) for every day that has any dhikr, newest first. Drives the streak. */
    fun dailyTotals(): List<Pair<String, Int>>
}

interface AdhkarPersistence {
    /** category → completed item ids for that day. */
    fun marksFor(dateKey: String): Map<String, Set<String>>
    fun mark(dateKey: String, category: String, itemId: String)
    fun unmark(dateKey: String, category: String, itemId: String)
}

// ── In-memory implementations (tests, previews, and a safe degraded mode) ─────

class InMemorySpiritualPersistence :
    QuranPersistence, PrefPersistence, DhikrPersistence, AdhkarPersistence {

    private val read = mutableSetOf<Int>()
    private val prefs = mutableMapOf<String, String>()
    private val dhikr = mutableMapOf<String, MutableMap<String, Int>>()
    private val adhkar = mutableMapOf<String, MutableMap<String, MutableSet<String>>>()

    override fun readAyahs(): Set<Int> = read.toSet()

    override fun markRead(surah: Int, ayah: Int, at: Long) {
        read += surah * 1000 + ayah
    }

    override fun unmarkRead(surah: Int, ayah: Int) {
        read -= surah * 1000 + ayah
    }

    override fun get(key: String): String? = prefs[key]

    override fun put(key: String, value: String) {
        prefs[key] = value
    }

    override fun countsFor(dateKey: String): Map<String, Int> = dhikr[dateKey].orEmpty().toMap()

    override fun put(dateKey: String, dhikrId: String, total: Int) {
        dhikr.getOrPut(dateKey) { mutableMapOf() }[dhikrId] = total
    }

    override fun dailyTotals(): List<Pair<String, Int>> =
        dhikr.map { (day, byDhikr) -> day to byDhikr.values.sum() }
            .sortedByDescending { it.first }

    override fun marksFor(dateKey: String): Map<String, Set<String>> =
        adhkar[dateKey].orEmpty().mapValues { (_, ids) -> ids.toSet() }

    override fun mark(dateKey: String, category: String, itemId: String) {
        adhkar.getOrPut(dateKey) { mutableMapOf() }.getOrPut(category) { mutableSetOf() } += itemId
    }

    override fun unmark(dateKey: String, category: String, itemId: String) {
        adhkar[dateKey]?.get(category)?.remove(itemId)
    }
}
