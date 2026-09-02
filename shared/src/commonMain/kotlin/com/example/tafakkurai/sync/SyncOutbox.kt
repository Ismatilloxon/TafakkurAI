package com.example.tafakkurai.sync

import com.example.tafakkurai.db.TafakkurDb

/**
 * The queue, and nothing else. It knows how events move between states; it does not
 * know what an event means, how it travels, or when to send one.
 *
 * States are deliberately only two:
 *
 *   pending  → waiting. New local changes coalesce onto a pending event.
 *   inflight → handed to the uploader. A new local change during upload creates a
 *              SEPARATE pending event rather than mutating this one, so acknowledging
 *              the upload cannot discard an edit the user made while it was in the air.
 *
 * There is no `synced` state: a delivered event is deleted. Keeping delivered rows to
 * "reclaim later" is how outboxes grow without bound and how a housekeeping bug turns
 * into a resend storm.
 */
class SyncOutbox(private val db: TafakkurDb) {

    private val q get() = db.syncEventQueries

    /**
     * How many changes are waiting. Read by the scheduler for its threshold trigger and
     * worth surfacing in a debug screen: a number that never returns to zero is the
     * single clearest symptom that sync is broken.
     */
    fun pendingCount(userId: String): Long =
        runCatching { q.pendingCount(userId).executeAsOne() }.getOrDefault(0L)

    /**
     * Moves up to [limit] of the oldest pending events into flight and returns them.
     *
     * Oldest-first matters for a row whose identity changed hands: applying an older
     * event after a newer one would be resolved by the server's last-write-wins guard,
     * but sending them in order means that guard almost never has to fire.
     */
    fun claim(userId: String, limit: Int, now: Long): List<Claimed> {
        runCatching { q.claim(now, userId, limit.toLong()) }
        return runCatching {
            q.selectInflight(
                userId = userId,
                mapper = { id, _, feature, entity, entityId, operation, payload, metadata, deviceTime, timezone, retryCount ->
                    Claimed(id, feature, entity, entityId, operation, payload, metadata, deviceTime, timezone, retryCount)
                },
            ).executeAsList()
        }.getOrDefault(emptyList())
    }

    /** The event is finished — delivered, duplicate, stale or permanently rejected. */
    fun ack(id: String) {
        runCatching { q.ack(id) }
    }

    /**
     * Not delivered. Put it back unless a newer pending event for the same row already
     * exists, in which case this one is redundant and is dropped: two pending rows with
     * one dedupeKey would violate the partial unique index and wedge every later write
     * to that row.
     */
    fun requeue(id: String, error: String?, now: Long) {
        runCatching { q.requeueSupersededDrop(id) }
        runCatching { q.requeue(error, now, id) }
    }

    /**
     * Startup recovery. Anything still inflight was interrupted by a crash or a kill
     * mid-upload; it may or may not have reached the server, so it goes back to pending
     * and relies on the server's idempotency to be harmless if it was in fact delivered.
     */
    fun recoverInflight(now: Long) {
        runCatching { q.recoverInflightDrop() }
        runCatching { q.recoverInflight(now) }
    }

    /**
     * Discards events that have failed [maxRetries] times.
     *
     * Without this, one permanently-rejected event is retried on every flush for the
     * life of the install. The row it described is still in the local database and will
     * be re-emitted by the next backfill, so this loses a delivery attempt, not data.
     */
    fun dropExhausted(maxRetries: Int) {
        runCatching { q.dropExhausted(maxRetries.toLong()) }
    }

    /** Sign-out: this account's queue and cursor leave with it. */
    fun clearAccount(userId: String) {
        runCatching { q.deleteForUser(userId) }
        runCatching { q.deleteCursorFor(userId) }
        // Backfill bookkeeping keys contain the account id. Metrics are diagnostic and
        // disposable, so reset them rather than exposing the previous account identifier.
        runCatching { q.resetMetrics() }
    }

    fun cursor(userId: String): Long =
        runCatching { q.cursorFor(userId).executeAsOneOrNull() }.getOrNull() ?: 0L

    fun saveCursor(userId: String, cursor: Long, now: Long) {
        runCatching { q.saveCursor(userId, cursor, now) }
    }

    // ------------------------------------------------------------------ metrics

    fun bump(metric: String, by: Long = 1) {
        if (by == 0L) return
        runCatching {
            q.ensureMetric(metric)
            q.bumpMetric(by, metric)
        }
    }

    /**
     * Everything the queue knows about its own health, for a debug screen or a support
     * log. Paired with the server's `/v1/sync/stats` this answers "is the problem the
     * device or the backend" without guessing.
     */
    fun metrics(userId: String): Map<String, Long> {
        val stored: Map<String, Long> = runCatching {
            q.allMetrics { name, value -> name to value }.executeAsList().toMap()
        }.getOrDefault(emptyMap())
        return stored + mapOf("queue.pending" to pendingCount(userId))
    }

    data class Claimed(
        val id: String,
        val feature: String,
        val entity: String,
        val entityId: String,
        val operation: String,
        val payload: String?,
        val metadata: String?,
        val deviceTime: Long,
        val timezone: String,
        val retryCount: Long,
    )

    companion object {
        /** Metric keys. Strings rather than an enum so adding one needs no migration. */
        const val M_PUSHED = "push.events"
        const val M_BATCHES = "push.batches"
        const val M_APPLIED = "push.applied"
        const val M_DUPLICATE = "push.duplicate"
        const val M_STALE = "push.stale"
        const val M_REJECTED = "push.rejected"
        const val M_RETRIED = "push.retried"
        const val M_DROPPED = "push.dropped"
        const val M_PULLED = "pull.changes"
        const val M_PULL_PAGES = "pull.pages"
        const val M_FAILURES = "sync.failures"
        const val M_RUNS = "sync.runs"
        const val M_MILLIS = "sync.millis"
    }
}
