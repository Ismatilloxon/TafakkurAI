package com.example.tafakkurai.journal.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.journal.domain.JournalDraft
import com.example.tafakkurai.journal.domain.JournalMood
import com.example.tafakkurai.journal.domain.JournalOrigin
import com.example.tafakkurai.journal.domain.JournalPrompt
import com.example.tafakkurai.journal.domain.JournalReflection
import com.example.tafakkurai.journal.domain.JournalRepository
import com.example.tafakkurai.journal.domain.normalized
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** SQLDelight-backed single source of truth for private Tafakkur reflections. */
class JournalStore(
    private val db: TafakkurDb,
    private val activity: ActivityLog,
    private val today: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = ::randomJournalId,
    remoteChanges: Flow<Set<String>> = emptyFlow(),
    scope: CoroutineScope? = null,
) : JournalRepository {
    private val q get() = db.tafakkurJournalEntryQueries
    private val items = mutableStateListOf<JournalReflection>()

    override val entries: List<JournalReflection> get() = items.toList()

    override var revision by mutableStateOf(0)
        private set

    override var hasLoadError by mutableStateOf(false)
        private set

    init {
        // Generic sync stays feature-blind; this label only groups the table with Journal.
        runCatching { db.syncEventQueries.upsertPolicy(TABLE, FEATURE, 1L) }
        refresh()
        scope?.launch {
            remoteChanges.collect { tables ->
                if (TABLE in tables) refresh()
            }
        }
    }

    override fun refresh(): Boolean {
        val loaded = runCatching { q.selectAll().executeAsList().map(::toDomain) }
        loaded.onSuccess { rows ->
            items.clear()
            items.addAll(rows)
            sortInPlace()
            hasLoadError = false
        }.onFailure {
            // A failed read must not masquerade as a genuinely empty private journal.
            // Keep the last good snapshot visible, but make the screen read-only until retry.
            hasLoadError = true
        }
        revision++
        return loaded.isSuccess
    }

    override fun create(draft: JournalDraft): JournalReflection? {
        val safe = draft.normalized() ?: return null
        val stamp = now()
        val entry = JournalReflection(
            id = uniqueId() ?: return null,
            prompt = safe.prompt,
            mood = safe.mood,
            body = safe.body,
            entryDate = today(),
            origin = safe.origin,
            createdAt = stamp,
            updatedAt = stamp,
        )
        val stored = runCatching {
            db.transaction {
                persist(entry)
                recordOrThrow("reflection_created", entry, safe.origin)
            }
        }.isSuccess
        if (!stored) return null
        items.add(entry)
        sortInPlace()
        revision++
        return entry
    }

    override fun update(id: String, draft: JournalDraft): JournalReflection? {
        val safe = draft.normalized() ?: return null
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return null
        val original = items[index]
        val updated = original.copy(
            prompt = safe.prompt,
            mood = safe.mood,
            body = safe.body,
            updatedAt = now(),
        )
        val stored = runCatching {
            db.transaction {
                persist(updated)
                recordOrThrow("reflection_updated", updated, safe.origin)
            }
        }.isSuccess
        if (!stored) return null
        items[index] = updated
        sortInPlace()
        revision++
        return updated
    }

    override fun delete(id: String, actionOrigin: JournalOrigin): Boolean {
        val entry = items.firstOrNull { it.id == id } ?: return false
        val stamp = now()
        val deleted = runCatching {
            db.transaction {
                q.softDelete(stamp, id)
                recordOrThrow("reflection_deleted", entry.copy(updatedAt = stamp), actionOrigin)
            }
        }.isSuccess
        if (!deleted) return false
        items.removeAll { it.id == id }
        revision++
        return true
    }

    private fun persist(entry: JournalReflection) {
        q.upsert(
            id = entry.id,
            promptId = entry.prompt.id,
            moodId = entry.mood.id,
            body = entry.body,
            entryDate = entry.entryDate,
            origin = entry.origin.id,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
            deleted = 0L,
        )
    }

    private fun recordOrThrow(type: String, entry: JournalReflection, source: JournalOrigin) {
        check(
            activity.record(
                feature = FEATURE,
                type = type,
                properties = journalActivityProperties(entry),
                entityId = entry.id,
                source = source.id,
            ),
        ) { "Could not append Journal ActivityEvent" }
    }

    private fun uniqueId(): String? {
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = newId()
            if (items.none { it.id == candidate } &&
                runCatching { q.selectById(candidate).executeAsOneOrNull() == null }.getOrDefault(false)
            ) return candidate
        }
        return null
    }

    private fun sortInPlace() {
        val sorted = items.sortedWith(
            compareByDescending<JournalReflection> { it.entryDate }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        )
        items.clear()
        items.addAll(sorted)
    }

    private fun toDomain(row: com.example.tafakkurai.db.TafakkurJournalEntry) = JournalReflection(
        id = row.id,
        prompt = JournalPrompt.from(row.promptId),
        mood = JournalMood.from(row.moodId),
        body = row.body,
        entryDate = row.entryDate,
        origin = JournalOrigin.from(row.origin),
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private companion object {
        const val TABLE = "tafakkurJournalEntry"
        const val FEATURE = "journal"
        const val MAX_ID_ATTEMPTS = 8
    }
}

/**
 * Activity projections receive useful provenance and counts, never the private reflection.
 * Keeping this mapper pure also makes the privacy boundary mechanically testable.
 */
internal fun journalActivityProperties(entry: JournalReflection): JsonObject = buildJsonObject {
    put("entry_id", entry.id)
    put("entry_date", entry.entryDate)
    put("character_count", entry.body.length)
}

private fun randomJournalId(): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "journal-" + buildString {
        repeat(20) { append(alphabet[Random.nextInt(alphabet.length)]) }
    }
}
