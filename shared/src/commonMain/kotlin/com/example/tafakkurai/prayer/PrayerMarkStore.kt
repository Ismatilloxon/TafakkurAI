package com.example.tafakkurai.prayer

import androidx.compose.runtime.mutableStateMapOf
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The state a salah can be in for a given day. [id] matches the existing `PRAYER_STATES`
 * / tracker vocabulary so the ring, card and tracker all render from one enum. Tapping a
 * prayer walks [CYCLE]: not-prayed → on-time → jamaat → not-prayed.
 */
enum class PrayerMark(val id: String) {
    NONE("upcoming"),
    ONTIME("ontime"),
    JAMAAT("jamaat");

    fun next(): PrayerMark = CYCLE[(CYCLE.indexOf(this) + 1) % CYCLE.size]

    companion object {
        val CYCLE = listOf(NONE, ONTIME, JAMAAT)
        fun from(id: String?): PrayerMark = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** Today's local calendar day as "yyyy-MM-dd", from the device clock. */
fun deviceDateKey(): String {
    val d = deviceDate(0)
    fun p(n: Int) = if (n < 10) "0$n" else "$n"
    return "${d.year}-${p(d.month)}-${p(d.day)}"
}

/**
 * Local-first, observable source of truth for prayer completion. Backed by SQLDelight
 * ([com.example.tafakkurai.db.prayerMark]) so a mark survives restarts, and mirrored in a
 * Compose [mutableStateMapOf] so every screen that reads it — the hero ring button, the
 * Today card, the prayer tracker — recomposes the instant any one of them changes it.
 *
 * A single Koin instance is shared app-wide, which is what makes the three surfaces agree.
 * Each tap commits the mark row and its immutable activity in one transaction; observable
 * state is changed only after that transaction succeeds.
 */
class PrayerMarkStore(
    private val db: TafakkurDb,
    private val todayKey: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val activity: ActivityLog? = null,
) {
    private val q get() = db.prayerMarkQueries

    /** prayerKey → mark for [loadedFor]. Absent key = [PrayerMark.NONE]. */
    private val marks = mutableStateMapOf<String, PrayerMark>()
    private var loadedFor = ""

    init { refresh() }

    /**
     * Reload today's marks from the DB. Call on app-resume / date rollover. DB access is
     * guarded so a pre-migration install (table not created yet) degrades to in-memory
     * marks for the session instead of crashing — reinstall/clear-data restores persistence.
     */
    fun refresh() {
        val key = todayKey()
        marks.clear()
        runCatching {
            q.selectByDate(key).executeAsList().forEach { row ->
                marks[row.prayer] = PrayerMark.from(row.state)
            }
        }
        loadedFor = key
    }

    /** Observed read — a composable calling this recomposes when the mark changes. */
    fun stateOf(prayer: String): PrayerMark = marks[prayer] ?: PrayerMark.NONE

    /** Advance a prayer through the cycle and write through to the DB. No-op for blanks. */
    fun cycle(prayer: String) {
        val prayerKey = prayer.trim().lowercase()
        if (prayerKey.isBlank()) return
        val key = todayKey()
        if (key != loadedFor) refresh()
        val next = stateOf(prayerKey).next()
        val stamp = now()
        val eventState = if (next == PrayerMark.NONE) "none" else next.id
        val persisted = runCatching {
            db.transaction {
                if (next == PrayerMark.NONE) {
                    q.deleteMark(key, prayerKey)
                } else {
                    q.upsert(key, prayerKey, next.id, stamp)
                }
                check(
                    activity?.record(
                        feature = "prayer",
                        type = "prayer_mark_changed",
                        properties = buildJsonObject {
                            put("date", key)
                            put("prayer", prayerKey)
                            put("state", eventState)
                        },
                        entityId = "prayer:$key:$prayerKey",
                        occurredAtOverride = stamp,
                        localDateOverride = key,
                    ) ?: true,
                ) { "Could not append prayer ActivityEvent" }
            }
        }.isSuccess
        if (!persisted) return
        if (next == PrayerMark.NONE) marks.remove(prayerKey) else marks[prayerKey] = next
    }
}
