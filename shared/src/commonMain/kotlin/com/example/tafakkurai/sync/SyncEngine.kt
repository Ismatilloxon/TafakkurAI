package com.example.tafakkurai.sync

import app.cash.sqldelight.db.SqlDriver
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * The one synchronisation engine for the whole application.
 *
 * It has no feature-specific code and no registry to add features to. What it syncs is
 * whatever tables exist in the local database, discovered at [attach] time.
 *
 * ### The shape of one run
 *
 * ```
 * push  ── claim ≤100 pending ── materialise payloads ── POST /v1/sync/batch ── ack
 *   │        (repeat while the queue still has pending events)
 *   └─ pull  ── GET /v1/sync/changes?cursor=N ── apply locally ── save cursor
 *                (repeat while `more`)
 * ```
 *
 * Push runs before pull on purpose. Pulling first would apply the server's older copy
 * of a row over a local change that has not been uploaded yet; the local change would
 * then be overwritten in the database and its queued event would carry the server's own
 * value back. Push-then-pull means the server always has the newest version before it
 * is asked what the newest version is.
 */
class SyncEngine(
    private val driver: SqlDriver,
    private val db: TafakkurDb,
    private val outbox: SyncOutbox,
    private val api: SyncApi,
    private val metadata: String,
    private val now: () -> Long = ::epochMillis,
) {
    private val gate = Mutex()
    private val remoteActivityEvents = RemoteActivityEventApplier(db)

    /** Tables being captured, by name. Rebuilt on every [attach]. */
    private var tables: Map<String, SyncSchema.Table> = emptyMap()
    private var userId: String = ""

    private val _remoteChanges = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)

    /**
     * Emits the set of table names a pull just wrote to.
     *
     * This is how a screen learns to refresh without the engine knowing screens exist:
     * whoever owns a store subscribes and calls its own `refresh()`. Emitting table names
     * rather than domain events keeps the dependency pointing one way.
     */
    val remoteChanges: SharedFlow<Set<String>> = _remoteChanges.asSharedFlow()

    /**
     * Binds the engine to an account and arms capture. Safe to call again — on a fresh
     * launch, after sign-in, and after an account switch.
     *
     * Order matters:
     *  1. write the session row, because triggers read the user id from it;
     *  2. discover tables and (re)install triggers, so a table added by this app version
     *     starts being captured before anything writes to it;
     *  3. recover interrupted uploads;
     *  4. backfill rows that predate the triggers — once per account per table;
     *  5. only then turn capture on.
     */
    fun attach(userId: String, timezone: String, onSkipped: (String, String) -> Unit = { _, _ -> }) {
        if (userId.isBlank()) return
        this.userId = userId
        val q = db.syncEventQueries

        runCatching { q.seedSession() }
        runCatching { q.setCapture(0) }
        runCatching { q.setSession(userId, timezone) }
        runCatching { q.adoptAnonymousActivities(userId, userId, now()) }
        runCatching { db.activityEventQueries.adoptAnonymousUser(userId) }

        val policies: Map<String, SyncSchema.Policy> = runCatching {
            q.policies { entity, feature, enabled ->
                entity to SyncSchema.Policy(feature, enabled == 1L)
            }.executeAsList().toMap()
        }.getOrDefault(emptyMap())

        val discovered = SyncSchema.discover(driver)
        val captured = SyncSchema.installTriggers(
            driver = driver,
            tables = discovered,
            policy = { name -> policies[name] ?: SyncSchema.Policy(feature = name, enabled = true) },
            onSkipped = onSkipped,
        )
        tables = captured.associateBy { it.name }

        outbox.recoverInflight(now())
        outbox.dropExhausted(MAX_RETRIES)

        for (table in captured) {
            val key = "backfill:$userId:${table.name}"
            if (outbox.metrics(userId)[key] != null) continue
            SyncSchema.backfill(driver, table, policies[table.name]?.feature ?: table.name, userId)
            outbox.bump(key)
        }

        runCatching { q.setCapture(1) }
    }

    /**
     * Sign-out. Capture stops, and this account's queue and cursor are deleted so the
     * next account cannot inherit them.
     *
     * The feature tables themselves are cleared by [wipeLocalData], which is a separate
     * decision: on a shared device it must happen, on a single-user device keeping the
     * rows means the next launch is instant instead of blank.
     */
    fun detach(clearAccountQueue: Boolean = true) {
        runCatching {
            db.syncEventQueries.setCapture(0)
            db.syncEventQueries.setSession("", "UTC")
        }
        if (clearAccountQueue && userId.isNotBlank()) outbox.clearAccount(userId)
        userId = ""
        tables = emptyMap()
    }

    /**
     * Deletes every account-data table's rows without emitting a single sync event.
     *
     * For account switching on a shared device. Capture is off throughout, so the wipe
     * is local-only — deleting rows with capture on would queue a delete per row and
     * erase the user's data on the server, which is the worst possible bug in a sync
     * engine and the reason this is a deliberate, separate method. This includes private
     * device-only data such as profile photos and imported vocabulary sources, plus the
     * activity ledger whose inline events use the dedicated append-only sync path.
     */
    fun wipeLocalData() {
        val q = db.syncEventQueries
        runCatching { q.setCapture(0) }
        try {
            q.transaction {
                for (name in SyncSchema.localAccountTableNames(driver)) {
                    driver.exec("DELETE FROM \"$name\"")
                }
            }
        } finally {
            runCatching { q.setCapture(1) }
        }
    }

    /**
     * The correct sign-out sequence, in the one order that does not lose or leak data.
     *
     *  1. flush — best-effort. Everything the user did since the last sync is still only
     *     on this device; the session that could upload it is about to be destroyed.
     *  2. wipe — optional, and only meaningful with capture already handled inside
     *     [wipeLocalData]. Pass true on a shared device.
     *  3. detach — capture off, this account's queue and cursor deleted, so the next
     *     account cannot inherit either.
     *
     * Written as one method precisely because getting the order wrong is silent: wiping
     * before flushing loses the day's work, and detaching before wiping leaves rows the
     * next user can read.
     */
    suspend fun signOut(
        wipeLocal: Boolean = false,
        revokeRemoteSession: suspend () -> Unit = {},
    ) {
        runCatching { sync(SyncReason.Manual) }
        // Revoke while credentials and the old local rows still exist. The auth repository
        // clears secure tokens even when the network call fails; its mutation audit row is
        // deliberately created before the local wipe below, so no sign-out residue survives.
        runCatching { revokeRemoteSession() }
        try {
            if (wipeLocal) wipeLocalData()
        } finally {
            // A failed local wipe must never leave capture armed under a signed-out account.
            detach(clearAccountQueue = true)
        }
    }

    fun pendingCount(): Long = if (userId.isBlank()) 0 else outbox.pendingCount(userId)

    fun metrics(): Map<String, Long> = if (userId.isBlank()) emptyMap() else outbox.metrics(userId)

    /**
     * One full synchronisation. Serialised by a mutex: the scheduler has four independent
     * triggers and two of them firing together (backgrounding while the timer ticks) must
     * not produce two concurrent uploads of the same claimed events.
     *
     * Never throws. A sync failure is an expected condition — the device is on a train —
     * and the queue is designed to survive it, so the caller gets an outcome, not an
     * exception to handle at four different call sites.
     */
    suspend fun sync(reason: SyncReason): SyncOutcome = gate.withLock {
        val user = userId
        if (user.isBlank()) return SyncOutcome.NotSignedIn
        val started = now()
        outbox.bump(SyncOutbox.M_RUNS)

        var pushed = 0
        var pulled = 0
        var failure: String? = null

        try {
            pushed = push(user)
            pulled = pull(user)
        } catch (t: Throwable) {
            // Cancellation must propagate — it means the app is going away, not that
            // sync failed. Everything else is a network or server condition the queue
            // already knows how to survive.
            if (t is kotlinx.coroutines.CancellationException) throw t
            failure = t.message ?: t::class.simpleName ?: "sync failed"
            outbox.bump(SyncOutbox.M_FAILURES)
        }

        outbox.bump(SyncOutbox.M_MILLIS, now() - started)
        return if (failure == null) {
            SyncOutcome.Success(reason, pushed, pulled, outbox.pendingCount(user))
        } else {
            SyncOutcome.Failed(reason, failure, outbox.pendingCount(user))
        }
    }

    // ------------------------------------------------------------------ push

    private suspend fun push(user: String): Int {
        var total = 0
        var rounds = 0
        while (rounds < MAX_PUSH_ROUNDS) {
            rounds++
            val claimed = outbox.claim(user, BATCH_SIZE, now())
            if (claimed.isEmpty()) return total

            val outgoing = claimed.mapNotNull { event -> toDto(event)?.let { event to it } }
            val outgoingIds = outgoing.mapTo(hashSetOf()) { it.first.id }
            claimed.filterNot { it.id in outgoingIds }.forEach {
                // The referenced table disappeared and there is no inline activity payload.
                // It can never become deliverable, so do not wedge it in the retry queue.
                outbox.ack(it.id)
                outbox.bump(SyncOutbox.M_DROPPED)
            }
            val dtos = outgoing.map { it.second }
            if (dtos.isEmpty()) {
                continue
            }

            val response = try {
                api.push(dtos)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // Whole batch undelivered: put every claimed event back and stop. Retrying
                // immediately would hit the same dead network.
                outgoing.forEach { (event, _) -> outbox.requeue(event.id, t.message, now()) }
                outbox.bump(SyncOutbox.M_RETRIED, outgoing.size.toLong())
                throw t
            }

            outbox.bump(SyncOutbox.M_BATCHES)
            outbox.bump(SyncOutbox.M_PUSHED, dtos.size.toLong())

            val verdicts = response.results.associateBy { it.id }
            for ((event, _) in outgoing) {
                val verdict = verdicts[event.id]
                if (verdict != null && verdict.status in EventStatus.TERMINAL) {
                    outbox.ack(event.id)
                    when (verdict.status) {
                        EventStatus.APPLIED -> outbox.bump(SyncOutbox.M_APPLIED)
                        EventStatus.DUPLICATE -> outbox.bump(SyncOutbox.M_DUPLICATE)
                        EventStatus.STALE -> outbox.bump(SyncOutbox.M_STALE)
                        EventStatus.REJECTED -> outbox.bump(SyncOutbox.M_REJECTED)
                    }
                    total++
                } else {
                    // No verdict = the server never saw it (partial response, proxy cut).
                    // Requeue rather than assume; a duplicate delivery is free, a lost
                    // change is not.
                    outbox.requeue(event.id, verdict?.reason, now())
                    outbox.bump(SyncOutbox.M_RETRIED)
                }
            }

            // Do not advance the pull cursor to response.cursor here. That value is the
            // global account feed head, not proof that this device consumed everything
            // before it. Jumping to it can skip another device's unseen changes that were
            // written between our last pull and this upload. Pulling from the old cursor
            // may return our own writes too, but every local apply path is idempotent.
            if (claimed.size < BATCH_SIZE) return total
        }
        return total
    }

    /**
     * Builds the wire event, reading the row's current state rather than a snapshot taken
     * when the change happened.
     *
     * Sending current state is what makes a retry self-healing: an event queued three
     * times over a bad connection uploads the row as it is now, not as it was at the
     * first attempt. State events therefore keep the payload column empty. Append-only
     * activity events are the deliberate exception: they have no mutable feature row, so
     * their immutable payload is stored inline in the outbox.
     */
    private fun toDto(event: SyncOutbox.Claimed): SyncEventDto? {
        // Activity events are append-only facts, not mutable feature rows. They are stored
        // directly in the SQL outbox and carry their immutable payload inline. Handle this
        // before table lookup: by design there is no `activityEvent` feature table.
        event.payload?.let { raw ->
            return SyncEventDto(
                id = event.id,
                feature = event.feature,
                entity = event.entity,
                entityId = event.entityId,
                operation = event.operation,
                payload = runCatching { syncJson.parseToJsonElement(raw) }.getOrNull() ?: JsonNull,
                metadata = runCatching { syncJson.parseToJsonElement(metadata) as JsonObject }.getOrNull() ?: JsonNull,
                deviceTime = event.deviceTime,
                timezone = event.timezone,
            )
        }

        val table = tables[event.entity] ?: return null
        val deleting = event.operation == "delete"
        val payload = if (deleting) null else SyncPayload.materialize(driver, table, event.entityId)

        // The row vanished between the change and this flush. It is a delete now.
        val operation = if (!deleting && payload == null) "delete" else event.operation

        return SyncEventDto(
            id = event.id,
            feature = event.feature,
            entity = event.entity,
            entityId = event.entityId,
            operation = operation,
            payload = payload?.let { runCatching { syncJson.parseToJsonElement(it) }.getOrNull() } ?: JsonNull,
            metadata = runCatching { syncJson.parseToJsonElement(metadata) as JsonObject }.getOrNull() ?: JsonNull,
            deviceTime = event.deviceTime,
            timezone = event.timezone,
        )
    }

    // ------------------------------------------------------------------ pull

    private suspend fun pull(user: String): Int {
        var cursor = outbox.cursor(user)
        var applied = 0
        var pages = 0

        while (pages < MAX_PULL_PAGES) {
            val page = api.pull(cursor, PULL_LIMIT)
            pages++
            outbox.bump(SyncOutbox.M_PULL_PAGES)

            if (page.changes.isNotEmpty()) {
                applied += applyRemote(page.changes, user)
                outbox.bump(SyncOutbox.M_PULLED, page.changes.size.toLong())
            }
            // Save after every page. A pull interrupted at page 7 resumes at page 7 rather
            // than restarting, which matters on the first sync of an established account.
            if (page.cursor > cursor) {
                cursor = page.cursor
                outbox.saveCursor(user, cursor, now())
            }
            if (!page.more) break
        }
        return applied
    }

    /**
     * Writes server rows into their local tables with capture off.
     *
     * The capture flag is the whole reason this is safe. With it on, every applied row
     * would fire a trigger, queue an event, and be pushed straight back — a loop that
     * costs battery on the device, writes on the server, and never terminates. Activity
     * facts use their explicit immutable applier because their local table is deliberately
     * excluded from trigger capture.
     */
    private fun applyRemote(changes: List<ChangeDto>, user: String): Int {
        val q = db.syncEventQueries
        val touched = mutableSetOf<String>()
        var applied = 0

        runCatching { q.setCapture(0) }
        try {
            q.transaction {
                for (change in changes) {
                    if (change.entity == RemoteActivityEventApplier.ENTITY) {
                        remoteActivityEvents.apply(change, user)
                        touched += change.entity
                        applied++
                        continue
                    }
                    val table = tables[change.entity] ?: continue
                    if (change.deleted) {
                        SyncPayload.applyDelete(driver, table, change.entityId)
                    } else {
                        val obj = change.payload as? JsonObject ?: continue
                        SyncPayload.applyUpsert(driver, table, obj.toString())
                    }
                    touched += change.entity
                    applied++
                }
            }
        } finally {
            runCatching { q.setCapture(1) }
        }

        if (touched.isNotEmpty()) _remoteChanges.tryEmit(touched)
        return applied
    }

    companion object {
        /** Matches the server's cap. A longer queue is split across successive rounds. */
        const val BATCH_SIZE = 100

        /** One pull page. Larger than a push batch because reads are cheaper than writes. */
        const val PULL_LIMIT = 500

        /**
         * Bounds one run so a device with a huge backlog still finishes and yields —
         * the remaining events go out on the next trigger a few minutes later, rather
         * than holding a wake lock until the queue is empty.
         */
        const val MAX_PUSH_ROUNDS = 20
        const val MAX_PULL_PAGES = 20

        /** After this many failures an event is assumed permanently unacceptable. */
        const val MAX_RETRIES = 10
    }
}

/** Why a run started. Carried through to the outcome so logs explain themselves. */
enum class SyncReason { AppStart, Periodic, Threshold, Background, Reconnect, Manual, SignIn }

sealed interface SyncOutcome {
    data object NotSignedIn : SyncOutcome
    data class Success(val reason: SyncReason, val pushed: Int, val pulled: Int, val stillPending: Long) : SyncOutcome
    data class Failed(val reason: SyncReason, val error: String, val stillPending: Long) : SyncOutcome
}
