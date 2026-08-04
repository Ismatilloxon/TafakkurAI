package com.example.tafakkurai.sync

import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Shared JSON for outbox payloads. Lenient so a schema bump never breaks an old queued op. */
val syncJson: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun randomOpId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "op-" + buildString { repeat(20) { append(chars[Random.nextInt(chars.length)]) } }
}

/**
 * Append-only write path onto the [com.example.tafakkurai.db.syncOp] outbox. Stores call
 * this right after their local write, so the local DB stays the source of truth and the
 * network is a downstream consequence, never a prerequisite. DB access is guarded: a
 * failed enqueue (e.g. pre-migration table) degrades gracefully instead of crashing a tap.
 */
class Outbox(
    private val db: TafakkurDb,
    private val now: () -> Long = ::epochMillis,
    private val id: () -> String = ::randomOpId,
) {
    private val q get() = db.syncOpQueries

    fun enqueue(entity: String, op: String, payload: String) {
        runCatching { q.insert(id(), entity, op, payload, now()) }
    }

    fun pendingCount(): Long = runCatching { q.pendingCount().executeAsOne() }.getOrDefault(0L)
}

/** Sends one queued op to the backend. Throwing = "not delivered", the op stays pending. */
fun interface SyncHandler {
    suspend fun handle(op: String, payload: String)
}

/** Ops synced and ops still failing after this pass — surfaced for logging/UI, not required. */
data class SyncResult(val synced: Int, val failed: Int)

/**
 * Drains the outbox on demand (call on app enter). For each pending op it looks up the
 * handler for that entity and delivers it; on success the op is marked synced, on failure
 * the error is recorded and the op is left for the next launch (at-least-once delivery,
 * ordering preserved by createdAt). An unknown entity is dropped so a stale op can't wedge
 * the queue forever. Every DB call is guarded so a partial/absent schema never crashes.
 */
class SyncEngine(
    private val db: TafakkurDb,
    private val handlers: Map<String, SyncHandler>,
) {
    private val q get() = db.syncOpQueries

    suspend fun flush(): SyncResult {
        val pending = runCatching { q.selectPending().executeAsList() }.getOrDefault(emptyList())
        var synced = 0
        var failed = 0
        for (row in pending) {
            val handler = handlers[row.entity]
            if (handler == null) {
                runCatching { q.markSynced(row.id) } // no handler → drop, don't block the queue
                continue
            }
            runCatching { handler.handle(row.op, row.payload) }
                .onSuccess { runCatching { q.markSynced(row.id) }; synced++ }
                .onFailure { e -> runCatching { q.recordFailure(e.message ?: "sync error", row.id) }; failed++ }
        }
        runCatching { q.deleteSynced() } // housekeeping: reclaim delivered rows
        return SyncResult(synced, failed)
    }
}
