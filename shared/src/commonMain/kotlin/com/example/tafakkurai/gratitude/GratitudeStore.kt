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
import com.example.tafakkurai.sync.syncJson
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
 * Every mutation writes SQLDelight and stops there. The sync platform notices the write
 * through a database trigger, so this store carries no queue, no payload and no retry —
 * entry written on a plane is real immediately and reaches the backend on the next
 * app enter. Reads are mirrored into a Compose [mutableStateListOf], which is what
 * makes the home card, history, search, jar and insights all agree the instant any
 * one of them writes.
 *
 * Writes fail closed: observable memory changes only after SQL commits. Qualifying
 * actions append their ActivityEvent inside that same SQLDelight transaction, while
 * favourites, journey membership and settings remain synced preferences rather than
 * fake activity that could award XP or complete Today's plan.
 */
class GratitudeStore(
    private val db: TafakkurDb,
    private val prefs: PrefPersistence? = null,
    private val todayKey: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = ::randomEntryId,
    private val activity: ActivityLog? = null,
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
        reload()
    }

    /** Rehydrate all observable state after a sync merge or account switch. */
    fun reload() {
        refresh()
        settings = GratSettings()
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
        source: String = "user",
    ): GratEntry? {
        val draft = normalizeGratitudeDraft(
            GratitudeDraft(
                mode = mode,
                text = text,
                items = items,
                why = why,
                category = category,
                mood = mood,
                emotions = emotions,
            ),
        ) ?: return null
        val stamp = now()
        val entry = GratEntry(
            id = newId(),
            mode = draft.mode.id,
            text = draft.text,
            items = draft.items,
            why = draft.why,
            category = draft.category ?: DEFAULT_CATEGORY,
            mood = draft.mood,
            emotions = draft.emotions,
            photoCount = photoCount.coerceAtLeast(0),
            hasAudio = hasAudio,
            hasVideo = hasVideo,
            locationLabel = locationLabel,
            entryDate = todayKey(),
            createdAt = stamp,
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                persist(entry)
                check(activity?.record(
                    feature = "gratitude",
                    type = "entry_created",
                    entityId = entry.id,
                    properties = buildJsonObject {
                        put("entry_id", entry.id)
                        put("mode", entry.mode)
                        put("category", entry.category)
                    },
                    source = source,
                ) ?: true) { "Could not append gratitude ActivityEvent" }
            }
        }.isSuccess
        if (!persisted) return null
        _entries.add(0, entry)
        return entry
    }

    fun toggleFavourite(id: String) {
        val index = _entries.indexOfFirst { it.id == id }
        if (index < 0) return
        val stamp = now()
        val updated = _entries[index].copy(favourite = !_entries[index].favourite, updatedAt = stamp)
        val persisted = runCatching {
            db.transaction {
                q.setFavourite(if (updated.favourite) 1L else 0L, stamp, id)
            }
        }.isSuccess
        if (persisted) _entries[index] = updated
    }

    /** Replaces an edited entry. Same id, so the backend upsert updates in place. */
    fun update(entry: GratEntry): Boolean {
        val index = _entries.indexOfFirst { it.id == entry.id }
        if (index < 0) return false
        val current = _entries[index]
        val normalized = entry.normalizedContent() ?: return false
        val updated = normalized.copy(
            id = current.id,
            entryDate = current.entryDate,
            createdAt = current.createdAt,
            updatedAt = now(),
            serverId = current.serverId,
            favourite = current.favourite,
            photoCount = current.photoCount,
            hasAudio = current.hasAudio,
            hasVideo = current.hasVideo,
            locationLabel = current.locationLabel,
        )
        val persisted = runCatching {
            db.transaction {
                persist(updated)
                check(activity?.record(
                    "gratitude",
                    "entry_updated",
                    buildJsonObject { put("entry_id", updated.id) },
                    entityId = updated.id,
                ) ?: true) { "Could not append gratitude ActivityEvent" }
            }
        }.isSuccess
        if (!persisted) return false
        _entries[index] = updated
        return true
    }

    /**
     * Removes an entry. The row is tombstoned rather than dropped so the delete can
     * still be delivered after the app restarts; it is purged once synced.
     */
    fun delete(id: String) {
        if (_entries.none { it.id == id }) return
        val persisted = runCatching {
            db.transaction {
                q.softDelete(now(), id)
                check(activity?.record(
                    "gratitude",
                    "entry_deleted",
                    buildJsonObject { put("entry_id", id) },
                    entityId = id,
                ) ?: true) { "Could not append gratitude ActivityEvent" }
            }
        }.isSuccess
        if (persisted) _entries.removeAll { it.id == id }
    }

    fun setChallengeJoined(challengeId: String, joined: Boolean) {
        if (gratitudeChallengeOf(challengeId) == null) return
        if (joinedChallenges.contains(challengeId) == joined) return
        val persisted = runCatching {
            db.transaction { cq.setJoined(challengeId, if (joined) 1L else 0L, now()) }
        }.isSuccess
        if (!persisted) return
        if (joined) {
            joinedChallenges.add(challengeId)
        } else {
            joinedChallenges.remove(challengeId)
        }
    }

    fun completeChallengeDay(challengeId: String, day: Int) {
        if (!isValidChallengeDay(challengeId, day)) return
        val current = challengeDays[challengeId].orEmpty()
        if (day in current) return
        val stamp = now()
        val date = todayKey()
        val persisted = runCatching {
            db.transaction {
                cq.setJoined(challengeId, 1L, stamp)
                cq.completeDay(challengeId, day.toLong(), stamp)
                check(activity?.record(
                    feature = "gratitude",
                    type = "challenge_day_completed",
                    properties = buildJsonObject {
                        put("challenge_id", challengeId)
                        put("day", day)
                    },
                    entityId = "gratitude-challenge:$challengeId:day:$day",
                    occurredAtOverride = stamp,
                    localDateOverride = date,
                ) ?: true) { "Could not append gratitude challenge ActivityEvent" }
            }
        }.isSuccess
        if (!persisted) return
        // Re-assign so Compose sees a new set instance and recomposes the day list.
        challengeDays[challengeId] = (current + day).toMutableSet()
        if (!joinedChallenges.contains(challengeId)) joinedChallenges.add(challengeId)
    }

    fun saveSettings(next: GratSettings) {
        if (settings == next) return
        val encoded = syncJson.encodeToString(next.toPayload())
        // A null preference port is used only by an ephemeral preview construction.
        val persisted = prefs == null || runCatching {
            db.transaction { db.appPrefQueries.put(PREF_SETTINGS, encoded) }
        }.isSuccess
        if (persisted) settings = next
    }

    /** Applies entries pulled from the backend without re-queueing them for upload. */
    fun mergeFromServer(remote: List<GratEntry>, deletedIds: Set<String> = emptySet()) {
        remote.forEach { raw ->
            val entry = raw.normalizedContent() ?: return@forEach
            val local = _entries.firstOrNull { it.id == entry.id }
            if (!shouldApplyRemoteEntry(local, entry)) return@forEach
            if (runCatching { persist(entry) }.isFailure) return@forEach
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

    private fun persist(e: GratEntry) {
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

private fun GratEntry.normalizedContent(): GratEntry? {
    val draft = normalizeGratitudeDraft(
        GratitudeDraft(
            mode = GratMode.from(mode),
            text = text,
            items = items,
            why = why,
            category = category,
            mood = mood,
            emotions = emotions,
        ),
    ) ?: return null
    return copy(
        mode = draft.mode.id,
        text = draft.text,
        items = draft.items,
        why = draft.why,
        category = draft.category ?: DEFAULT_CATEGORY,
        mood = draft.mood,
        emotions = draft.emotions,
        photoCount = photoCount.coerceAtLeast(0),
    )
}

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
