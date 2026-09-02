package com.example.tafakkurai.spiritual

/**
 * Storage ports for the Qurʼan / dhikr / adhkar stores.
 *
 * The stores hold all the arithmetic — progress percentages, counter targets, streaks —
 * and talk to storage only through narrow interfaces. That keeps every rule
 * unit-testable against [InMemorySpiritualPersistence] with no SQLite driver, while the
 * app runs on the SQLDelight implementations in `SqlSpiritualPersistence`.
 */

/** Ayah-level read tracking. Keys are packed as `surah * 1000 + ayah`. */
interface QuranPersistence {
    fun readAyahs(): Set<Int>
    fun markRead(surah: Int, ayah: Int, at: Long)
    fun unmarkRead(surah: Int, ayah: Int)
}

/**
 * Strict write scope used by Qurʼan progress in production.
 *
 * The read/settings ports above remain convenient for isolated tests. This scope exists
 * because a meaningful Qurʼan action can touch both `quranRead` and `appPref`, and its
 * ActivityEvent must be committed in that same database transaction rather than through
 * two best-effort port calls.
 */
interface QuranWriteScope {
    fun markRead(surah: Int, ayah: Int, at: Long)
    fun unmarkRead(surah: Int, ayah: Int)
    fun putPreference(key: String, value: String)
    fun removePreference(key: String)
}

interface QuranWriteTransaction {
    /** Returns false and rolls the whole write back when any statement or event append fails. */
    fun commit(block: QuranWriteScope.() -> Unit): Boolean
}

/** Single-value settings: last-read position, selected qari, dhikr target. */
interface PrefPersistence {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

interface DhikrPersistence {
    fun countsFor(dateKey: String): Map<String, Int>
    fun put(dateKey: String, dhikrId: String, total: Int)

    /** (dateKey, total) for every day that has any dhikr, newest first. Drives the streak. */
    fun dailyTotals(): List<Pair<String, Int>>
}

/** Strict production scope for a tasbih count and its related preferences. */
interface DhikrWriteScope {
    fun putCount(dateKey: String, dhikrId: String, total: Int)
    fun putPreference(key: String, value: String)
}

interface DhikrWriteTransaction {
    /** Returns false and rolls the source write back when an ActivityEvent append fails. */
    fun commit(block: DhikrWriteScope.() -> Unit): Boolean
}

interface AdhkarPersistence {
    /** category → completed item ids for that day. */
    fun marksFor(dateKey: String): Map<String, Set<String>>
    fun mark(dateKey: String, category: String, itemId: String)
    fun unmark(dateKey: String, category: String, itemId: String)
}

/** Strict production scope for one morning/evening adhkar completion toggle. */
interface AdhkarWriteScope {
    fun mark(dateKey: String, category: String, itemId: String)
    fun unmark(dateKey: String, category: String, itemId: String)
}

interface AdhkarWriteTransaction {
    /** Returns false and rolls the source write back when an ActivityEvent append fails. */
    fun commit(block: AdhkarWriteScope.() -> Unit): Boolean
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

    override fun remove(key: String) {
        prefs.remove(key)
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
