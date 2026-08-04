package com.example.tafakkurai.gratitude

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.spiritual.dayNumberOf
import com.example.tafakkurai.spiritual.parseDateKey
import com.example.tafakkurai.sync.Outbox
import com.example.tafakkurai.sync.syncJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.random.Random

/** Outbox payload for a gratitude change. The client id keeps a replay idempotent. */
@Serializable
data class GratEntrySyncPayload(val entry: GratEntry)

/** Outbox payload for a favourite toggle or a delete — id is all the backend needs. */
@Serializable
data class GratEntryRefPayload(val id: String, val favourite: Boolean = false)

/** Outbox payload for journey progress. */
@Serializable
data class GratChallengeSyncPayload(val challengeId: String, val joined: Boolean, val day: Int = 0)

/** Outbox payload for the settings screen. */
@Serializable
data class GratSettingsSyncPayload(
    val reminderSlot: String,
    val reminderTime: String,
    val faceId: Boolean,
    val appLock: Boolean,
    val hidePreview: Boolean,
    val cloudSync: Boolean,
)

private const val PREF_SETTINGS = "gratitude.settings.v1"

private fun randomEntryId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "g-" + buildString { repeat(20) { append(chars[Random.nextInt(chars.length)]) } }
}

/**
 * Local-first, observable source of truth for the Gratitude journal.
 *
 * Every mutation writes SQLDelight first and appends to the [Outbox] second, so an
 * entry written on a plane is real immediately and reaches the backend on the next
 * app enter. Reads are mirrored into a Compose [mutableStateListOf], which is what
 * makes the home card, history, search, jar and insights all agree the instant any
 * one of them writes.
 *
 * Every DB call is wrapped in `runCatching`, matching `PrayerMarkStore`: an install
 * that has not run the v4 → v5 migration degrades to in-session state instead of
 * crashing a tap, and recovers as soon as the migration lands.
 */
class GratitudeStore(
    private val db: TafakkurDb,
    private val outbox: Outbox? = null,
    private val prefs: PrefPersistence? = null,
    private val todayKey: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = ::randomEntryId,
) {
    private val q get() = db.gratitudeEntryQueries
    private val cq get() = db.gratitudeChallengeQueries

    /** Newest first. Observed — a composable reading this recomposes on any write. */
    private val _entries = mutableStateListOf<GratEntry>()
    val entries: List<GratEntry> get() = _entries

    /** Journey id → completed day indices. */
    private val challengeDays = mutableStateMapOfInt()
    private val joinedChallenges = mutableStateListOf<String>()

    var settings by mutableStateOf(GratSettings())
        private set

    init {
        refresh()
        loadSettings()
    }

    // ── reads ────────────────────────────────────────────────────────────────

    fun refresh() {
        _entries.clear()
        runCatching {
            q.selectAll().executeAsList().map(::toDomain)
        }.getOrDefault(emptyList()).let(_entries::addAll)

        joinedChallenges.clear()
        challengeDays.clear()
        runCatching { cq.selectAll().executeAsList() }.getOrDefault(emptyList())
            .filter { it.joined == 1L }
            .forEach { joinedChallenges.add(it.challengeId) }
        runCatching { cq.selectDays().executeAsList() }.getOrDefault(emptyList())
            .forEach { row ->
                val set = challengeDays.getOrPut(row.challengeId) { mutableSetOf() }
                set.add(row.dayIndex.toInt())
            }
    }

    fun byId(id: String): GratEntry? = _entries.firstOrNull { it.id == id }

    fun onDate(dateKey: String): List<GratEntry> = _entries.filter { it.entryDate == dateKey }

    fun today(): List<GratEntry> = onDate(todayKey())

    /** Distinct days with at least one entry — the calendar dots and the streak. */
    fun activeDays(): Set<String> = _entries.map { it.entryDate }.toSet()

    fun stats(): GratStats = computeGratStats(activeDays(), _entries.size, todayKey())

    /** Category shares for the current calendar month. */
    fun categoryShares(limit: Int = 5): List<GratCategoryShare> {
        val prefix = todayKey().substring(0, 7)
        val counts = _entries.filter { it.entryDate.startsWith(prefix) }
            .groupingBy { it.category }.eachCount()
        return categoryBreakdown(counts, limit)
    }

    /** Mood index per day for the trailing week, oldest → today. -1 = nothing logged. */
    fun moodWeek(): List<Int> {
        val today = dayNumberOf(todayKey()) ?: return List(7) { -1 }
        val latestPerDay = _entries.filter { it.mood != null }
            .groupBy { it.entryDate }
            .mapValues { (_, list) -> list.maxByOrNull { it.createdAt }?.mood }
        return (0..6).map { i ->
            val key = latestPerDay.keys.firstOrNull { dayNumberOf(it) == today - 6 + i }
            moodIndex(latestPerDay[key])
        }
    }

    /** A random memory for the jar. Null only when the journal is empty. */
    fun randomMemory(): GratEntry? = _entries.randomOrNull()

    /** "A memory to revisit": prefers favourites, at least 30 days old. */
    fun throwback(): GratEntry? {
        val today = dayNumberOf(todayKey()) ?: return null
        val old = _entries.filter { (dayNumberOf(it.entryDate) ?: today) <= today - 30 }
        return old.filter { it.favourite }.randomOrNull() ?: old.randomOrNull()
    }

    fun challengeProgress(): List<GratChallengeProgress> = GRAT_CHALLENGES.map { c ->
        GratChallengeProgress(
            challenge = c,
            joined = joinedChallenges.contains(c.id),
            doneDays = challengeDays[c.id]?.toSet() ?: emptySet(),
        )
    }

    // ── writes ───────────────────────────────────────────────────────────────

    /**
     * Saves a new entry and returns it. The id is generated here so the composer can
     * navigate straight to the saved entry without waiting for any network round trip.
     */
    fun add(
        text: String,
        mode: GratMode = GratMode.QUICK,
        items: List<String> = emptyList(),
        why: String? = null,
        category: String? = null,
        mood: String? = null,
        emotions: List<String> = emptyList(),
        photoCount: Int = 0,
        hasAudio: Boolean = false,
        hasVideo: Boolean = false,
        locationLabel: String? = null,
    ): GratEntry {
        val stamp = now()
        val entry = GratEntry(
            id = newId(),
            mode = mode.id,
            text = text.trim(),
            items = items,
            why = why?.takeIf { it.isNotBlank() },
            category = category ?: DEFAULT_CATEGORY,
            mood = mood,
            emotions = emotions,
            photoCount = photoCount,
            hasAudio = hasAudio,
            hasVideo = hasVideo,
            locationLabel = locationLabel,
            entryDate = todayKey(),
            createdAt = stamp,
            updatedAt = stamp,
        )
        persist(entry)
        _entries.add(0, entry)
        enqueue("gratitudeEntry", "create", syncJson.encodeToString(GratEntrySyncPayload(entry)))
        return entry
    }

    fun toggleFavourite(id: String) {
        val index = _entries.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = _entries[index].copy(favourite = !_entries[index].favourite, updatedAt = now())
        _entries[index] = updated
        runCatching { q.setFavourite(if (updated.favourite) 1L else 0L, updated.updatedAt, id) }
        enqueue("gratitudeEntry", "favourite", syncJson.encodeToString(GratEntryRefPayload(id, updated.favourite)))
    }

    /** Replaces an edited entry. Same id, so the backend upsert updates in place. */
    fun update(entry: GratEntry) {
        val index = _entries.indexOfFirst { it.id == entry.id }
        val updated = entry.copy(updatedAt = now())
        if (index >= 0) _entries[index] = updated else _entries.add(0, updated)
        persist(updated)
        enqueue("gratitudeEntry", "update", syncJson.encodeToString(GratEntrySyncPayload(updated)))
    }

    /**
     * Removes an entry. The row is tombstoned rather than dropped so the delete can
     * still be delivered after the app restarts; it is purged once synced.
     */
    fun delete(id: String) {
        _entries.removeAll { it.id == id }
        runCatching { q.softDelete(now(), id) }
        enqueue("gratitudeEntry", "delete", syncJson.encodeToString(GratEntryRefPayload(id)))
    }

    fun setChallengeJoined(challengeId: String, joined: Boolean) {
        if (joined) {
            if (!joinedChallenges.contains(challengeId)) joinedChallenges.add(challengeId)
        } else {
            joinedChallenges.remove(challengeId)
        }
        runCatching { cq.setJoined(challengeId, if (joined) 1L else 0L, now()) }
        enqueue("gratitudeChallenge", "join", syncJson.encodeToString(GratChallengeSyncPayload(challengeId, joined)))
    }

    fun completeChallengeDay(challengeId: String, day: Int) {
        val set = challengeDays.getOrPut(challengeId) { mutableSetOf() }
        if (!set.add(day)) return
        // Re-assign so Compose sees a new set instance and recomposes the day list.
        challengeDays[challengeId] = set.toMutableSet()
        if (!joinedChallenges.contains(challengeId)) joinedChallenges.add(challengeId)
        runCatching {
            cq.setJoined(challengeId, 1L, now())
            cq.completeDay(challengeId, day.toLong(), now())
        }
        enqueue(
            "gratitudeChallenge",
            "complete",
            syncJson.encodeToString(GratChallengeSyncPayload(challengeId, true, day)),
        )
    }

    fun saveSettings(next: GratSettings) {
        settings = next
        prefs?.put(PREF_SETTINGS, syncJson.encodeToString(next.toPayload()))
        enqueue("gratitudeSettings", "save", syncJson.encodeToString(next.toPayload()))
    }

    /** Applies entries pulled from the backend without re-queueing them for upload. */
    fun mergeFromServer(remote: List<GratEntry>, deletedIds: Set<String> = emptySet()) {
        remote.forEach { entry ->
            persist(entry)
            val index = _entries.indexOfFirst { it.id == entry.id }
            if (index >= 0) _entries[index] = entry else _entries.add(entry)
        }
        deletedIds.forEach { id ->
            _entries.removeAll { it.id == id }
            runCatching { q.softDelete(now(), id) }
        }
        _entries.sortWith(compareByDescending<GratEntry> { it.entryDate }.thenByDescending { it.createdAt })
    }

    // ── plumbing ─────────────────────────────────────────────────────────────

    private fun enqueue(entity: String, op: String, payload: String) {
        outbox?.enqueue(entity, op, payload)
    }

    private fun persist(e: GratEntry) {
        runCatching {
            q.upsert(
                id = e.id,
                serverId = e.serverId,
                mode = e.mode,
                body = e.text,
                items = syncJson.encodeToString(e.items),
                why = e.why,
                category = e.category,
                mood = e.mood,
                emotions = syncJson.encodeToString(e.emotions),
                photoCount = e.photoCount.toLong(),
                hasAudio = if (e.hasAudio) 1L else 0L,
                hasVideo = if (e.hasVideo) 1L else 0L,
                locationLabel = e.locationLabel,
                favourite = if (e.favourite) 1L else 0L,
                entryDate = e.entryDate,
                createdAt = e.createdAt,
                updatedAt = e.updatedAt,
                deleted = 0L,
            )
        }
    }

    private fun loadSettings() {
        val raw = prefs?.get(PREF_SETTINGS) ?: return
        runCatching { syncJson.decodeFromString(GratSettingsSyncPayload.serializer(), raw) }
            .getOrNull()?.let { settings = it.toSettings() }
    }

    private fun toDomain(row: com.example.tafakkurai.db.GratitudeEntry) = GratEntry(
        id = row.id,
        mode = row.mode,
        text = row.body,
        items = decodeList(row.items),
        why = row.why,
        category = row.category,
        mood = row.mood,
        emotions = decodeList(row.emotions),
        photoCount = row.photoCount.toInt(),
        hasAudio = row.hasAudio == 1L,
        hasVideo = row.hasVideo == 1L,
        locationLabel = row.locationLabel,
        favourite = row.favourite == 1L,
        entryDate = row.entryDate,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
        serverId = row.serverId,
    )

    private fun decodeList(raw: String): List<String> =
        runCatching { syncJson.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
}

private fun GratSettings.toPayload() =
    GratSettingsSyncPayload(reminderSlot, reminderTime, faceId, appLock, hidePreview, cloudSync)

private fun GratSettingsSyncPayload.toSettings() =
    GratSettings(reminderSlot, reminderTime, faceId, appLock, hidePreview, cloudSync)

/** Small helper so the challenge map is Compose-observable without a custom type. */
private fun mutableStateMapOfInt() = androidx.compose.runtime.mutableStateMapOf<String, MutableSet<Int>>()

// ── date labels ──────────────────────────────────────────────────────────────

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "Today" / "Yesterday" / "Jul 27" — the label every entry card shows. */
fun gratDateLabel(entryDate: String, todayKey: String): String {
    val today = dayNumberOf(todayKey)
    val day = dayNumberOf(entryDate)
    if (today != null && day != null) {
        when (today - day) {
            0L -> return "Today"
            1L -> return "Yesterday"
        }
    }
    val parsed = parseDateKey(entryDate) ?: return entryDate
    val (_, month, dayOfMonth) = parsed
    return "${MONTHS.getOrElse(month - 1) { "" }} $dayOfMonth"
}

/** "3 months ago" / "2 weeks ago" — the throwback card's relative stamp. */
fun gratRelativeLabel(entryDate: String, todayKey: String): String {
    val today = dayNumberOf(todayKey) ?: return ""
    val day = dayNumberOf(entryDate) ?: return ""
    val diff = today - day
    return when {
        diff < 7 -> "This week"
        diff < 30 -> "${diff / 7} ${if (diff / 7 == 1L) "week" else "weeks"} ago"
        diff < 365 -> "${diff / 30} ${if (diff / 30 == 1L) "month" else "months"} ago"
        else -> "${diff / 365} ${if (diff / 365 == 1L) "year" else "years"} ago"
    }
}
