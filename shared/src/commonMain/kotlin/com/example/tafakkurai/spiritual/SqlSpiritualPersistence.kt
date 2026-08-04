package com.example.tafakkurai.spiritual

import com.example.tafakkurai.db.TafakkurDb

/**
 * SQLDelight implementations of the spiritual storage ports.
 *
 * Every DB call is wrapped in `runCatching`, matching `PrayerMarkStore`: an install that
 * has not run the v3 → v4 migration yet degrades to in-session state instead of crashing
 * on a missing table, and recovers as soon as the migration lands.
 */
class SqlQuranPersistence(private val db: TafakkurDb) : QuranPersistence {
    private val q get() = db.quranReadQueries

    override fun readAyahs(): Set<Int> = runCatching {
        q.selectAll().executeAsList().map { (it.surah * 1000L + it.ayah).toInt() }.toSet()
    }.getOrDefault(emptySet())

    override fun markRead(surah: Int, ayah: Int, at: Long) {
        runCatching { q.markRead(surah.toLong(), ayah.toLong(), at) }
    }

    override fun unmarkRead(surah: Int, ayah: Int) {
        runCatching { q.unmarkRead(surah.toLong(), ayah.toLong()) }
    }
}

class SqlPrefPersistence(private val db: TafakkurDb) : PrefPersistence {
    private val q get() = db.appPrefQueries

    override fun get(key: String): String? = runCatching {
        q.selectOne(key).executeAsOneOrNull()
    }.getOrNull()

    override fun put(key: String, value: String) {
        runCatching { q.put(key, value) }
    }
}

class SqlDhikrPersistence(private val db: TafakkurDb) : DhikrPersistence {
    private val q get() = db.dhikrCountQueries

    override fun countsFor(dateKey: String): Map<String, Int> = runCatching {
        q.selectByDate(dateKey).executeAsList().associate { it.dhikrId to it.total.toInt() }
    }.getOrDefault(emptyMap())

    override fun put(dateKey: String, dhikrId: String, total: Int) {
        runCatching { q.put(dateKey, dhikrId, total.toLong()) }
    }

    override fun dailyTotals(): List<Pair<String, Int>> = runCatching {
        q.selectDailyTotals().executeAsList().map { it.dateKey to (it.dayTotal?.toInt() ?: 0) }
    }.getOrDefault(emptyList())
}

class SqlAdhkarPersistence(private val db: TafakkurDb) : AdhkarPersistence {
    private val q get() = db.adhkarMarkQueries

    override fun marksFor(dateKey: String): Map<String, Set<String>> = runCatching {
        q.selectByDate(dateKey).executeAsList()
            .groupBy({ it.category }, { it.itemId })
            .mapValues { (_, ids) -> ids.toSet() }
    }.getOrDefault(emptyMap())

    override fun mark(dateKey: String, category: String, itemId: String) {
        runCatching { q.mark(dateKey, category, itemId) }
    }

    override fun unmark(dateKey: String, category: String, itemId: String) {
        runCatching { q.unmark(dateKey, category, itemId) }
    }
}
