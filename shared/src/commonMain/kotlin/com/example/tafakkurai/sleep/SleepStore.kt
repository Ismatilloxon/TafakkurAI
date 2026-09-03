package com.example.tafakkurai.sleep

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.prayer.deviceUtcOffsetHours
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.sync.syncJson
import kotlinx.serialization.encodeToString
import kotlin.random.Random

private const val PREF_SETTINGS = "sleep.settings.v1"

private fun randomSessionId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "s-" + buildString { repeat(20) { append(chars[Random.nextInt(chars.length)]) } }
}

/**
 * Local-first, observable source of truth for Sleep & Rest.
 *
 * Every mutation writes SQLDelight and stops there. The sync platform notices the write
 * through a database trigger, so this store carries no queue, no payload and no retry — a nap
 * logged on a plane is real immediately and reaches the backend on the next app enter. Reads
 * are mirrored into Compose state, which is what makes the dashboard, the history list, the
 * statistics screen and the Today card all agree the instant any one of them writes.
 *
 * This is deliberately the same shape as `GratitudeStore`: one store, fail-closed mutations,
 * and a `mergeFromServer`-style import path that writes without inventing a second source of
 * truth. Observable memory changes only after the SQL write and qualifying ActivityEvent have
 * committed together.
 *
 * ### What this store does NOT do
 *
 * It does not score. [SleepScoring], [SleepStatistics] and [SleepCoach] are pure functions
 * over the sessions it holds, called by the ViewModel. Keeping computation out of the store
 * means the whole analytical surface is testable without a database, and means a scoring
 * change never risks the persistence layer.
 */
class SleepStore(
    private val db: TafakkurDb,
    private val prefs: PrefPersistence? = null,
    private val activity: SleepActivityLog,
    private val today: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = ::randomSessionId,
    private val offsetHours: () -> Double = ::deviceUtcOffsetHours,
) {
    private val q get() = db.sleepSessionQueries
    private val rq get() = db.sleepRoutineMarkQueries

    /** Newest first. Observed — a composable reading this recomposes on any write. */
    private val _sessions = mutableStateListOf<SleepSession>()
    val sessions: List<SleepSession> get() = _sessions

    /** dateKey → set of completed step ids. */
    private val routineMarks = mutableStateMapOf<String, MutableSet<String>>()

    var settings by mutableStateOf(SleepSettings())
        private set

    /** True while a health-store import is running; drives the sync chip in the header. */
    private var _importing by mutableStateOf(false)
    val importing: Boolean get() = _importing

    var lastImportAt by mutableStateOf(0L)
        private set

    init {
        reload()
    }

    /** Rehydrate sessions and preferences after a sync merge or account switch. */
    fun reload() {
        refresh()
        settings = SleepSettings()
        loadSettings()
    }

    // ── reads ────────────────────────────────────────────────────────────────

    fun refresh() {
        _sessions.clear()
        runCatching { q.selectAll().executeAsList().map(::toDomain) }
            .getOrDefault(emptyList())
            .let(_sessions::addAll)

        routineMarks.clear()
        runCatching { rq.selectBetween("0000-00-00", "9999-99-99").executeAsList() }
            .getOrDefault(emptyList())
            .forEach { row ->
                routineMarks.getOrPut(row.dateKey) { mutableSetOf() }.add(row.stepId)
            }
    }

    fun byId(id: String): SleepSession? = _sessions.firstOrNull { it.id == id }

    fun onDate(dateKey: String): List<SleepSession> = _sessions.filter { it.dateKey == dateKey }

    /** The night the dashboard opens on. Naps never take this slot. */
    fun latestNight(): SleepSession? =
        _sessions.firstOrNull { it.kind == SleepKind.NIGHT && it.isScorable }

    fun nights(): List<SleepSession> = _sessions.filter { it.kind == SleepKind.NIGHT }

    fun naps(dateKey: String): List<SleepSession> =
        _sessions.filter { it.kind == SleepKind.NAP && it.dateKey == dateKey }

    fun activeDays(): Set<String> = nights().map { it.dateKey }.toSet()

    // ── writes ───────────────────────────────────────────────────────────────

    /**
     * Records a session and returns it.
     *
     * The id is generated here so the caller can navigate straight to the saved session
     * without waiting for a network round trip — the optimistic-update contract the rest of
     * the app already uses.
     */
    fun add(
        startMs: Long,
        endMs: Long,
        kind: SleepKind = SleepKind.NIGHT,
        source: SleepSource = SleepSource.MANUAL,
        inBedStartMs: Long = startMs,
        inBedEndMs: Long = endMs,
        segments: List<SleepSegment> = emptyList(),
        vitals: SleepVitals = SleepVitals.NONE,
        externalId: String? = null,
        note: String? = null,
        tags: List<String> = emptyList(),
    ): SleepSession? {
        val stamp = now()
        val session = SleepSession(
            id = newId(),
            kind = kind,
            source = source,
            externalId = externalId,
            startMs = startMs,
            endMs = endMs,
            inBedStartMs = minOf(inBedStartMs, startMs),
            inBedEndMs = maxOf(inBedEndMs, endMs),
            segments = segments.sortedBy { it.startMs },
            vitals = vitals,
            // Filed under the WAKE day — see SleepSession's docs for why.
            dateKey = SleepTime.dateKeyOf(endMs, offsetHours()),
            note = note,
            tags = tags,
            createdAt = stamp,
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                persist(session)
                val recorded = when (kind) {
                    SleepKind.NIGHT -> activity.sleepEnded(session)
                    SleepKind.NAP -> activity.napLogged(
                        session,
                        SleepScoring.napVerdict(session, offsetHours()),
                    )
                }
                check(recorded) { "Could not append sleep ActivityEvent" }
            }
        }.isSuccess
        if (!persisted) return null
        insertSorted(session)
        return session
    }

    /** Replaces an edited session. Same id, so the backend upsert updates in place. */
    fun update(session: SleepSession) {
        val updated = session.copy(updatedAt = now())
        persist(updated)
        val index = _sessions.indexOfFirst { it.id == updated.id }
        if (index >= 0) _sessions[index] = updated else insertSorted(updated)
    }

    fun setMood(id: String, mood: String?) {
        val index = _sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        val stamp = now()
        val updated = _sessions[index].copy(moodOnWaking = mood, updatedAt = stamp)
        val persisted = runCatching { db.transaction { q.setMood(mood, stamp, id) } }.isSuccess
        if (persisted) _sessions[index] = updated
    }

    fun setNote(id: String, note: String?) {
        val index = _sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        val stamp = now()
        val updated = _sessions[index].copy(note = note, updatedAt = stamp)
        val persisted = runCatching { db.transaction { q.setNote(note, stamp, id) } }.isSuccess
        if (persisted) _sessions[index] = updated
    }

    /**
     * Removes a session. Tombstoned rather than dropped so the delete can still be delivered
     * after the app restarts; it is purged once synced.
     */
    fun delete(id: String): Boolean {
        val session = byId(id) ?: return false
        val stamp = now()
        val persisted = runCatching {
            db.transaction {
                q.softDelete(stamp, id)
                check(activity.sessionDeleted(session, stamp)) {
                    "Could not append sleep deletion ActivityEvent"
                }
            }
        }.isSuccess
        if (persisted) _sessions.removeAll { it.id == id }
        return persisted
    }

    // ── health-store import ──────────────────────────────────────────────────

    /**
     * Applies sessions read from HealthKit or Health Connect.
     *
     * The whole import is one transaction so a failure halfway cannot leave the history in a
     * state where a night exists but its stages do not.
     *
     * Conflict resolution is [SleepMerge.resolve]'s job. The short version: a record already
     * seen by `externalId` updates in place, an overlapping record from a more trusted source
     * replaces the one held, and an overlapping record from a less trusted source is dropped.
     * Without that last rule, a user with both a watch and phone-based detection accumulates
     * two of every night and every average they are shown is wrong.
     *
     * @return how many sessions were actually written.
     */
    fun importFromHealth(incoming: List<SleepSession>): Int {
        if (incoming.isEmpty()) return 0
        var written = 0
        val activityDate = today()
        db.transaction {
            for (candidate in incoming) {
                when (val outcome = resolveImport(candidate)) {
                    is SleepMerge.Outcome.Insert -> {
                        persist(outcome.session)
                        if (outcome.session.dateKey == activityDate) {
                            check(activity.sleepDetected(outcome.session)) {
                                "Could not append detected sleep ActivityEvent"
                            }
                        }
                        written++
                    }
                    is SleepMerge.Outcome.Replace -> {
                        // Tombstone the weaker record rather than hard-deleting it, so the
                        // removal propagates to the user's other devices too. Its compensating
                        // event prevents a later deletion of the replacement from resurrecting
                        // the weaker session's completed Today row.
                        val stamp = now()
                        val replaced = q.selectById(outcome.replacedId).executeAsOneOrNull()?.let(::toDomain)
                        q.softDelete(stamp, outcome.replacedId)
                        if (replaced != null && replaced.dateKey == activityDate) {
                            check(activity.sessionDeleted(replaced, stamp)) {
                                "Could not append replaced-sleep ActivityEvent"
                            }
                        }
                        persist(outcome.session)
                        if (outcome.session.dateKey == activityDate) {
                            check(activity.sleepDetected(outcome.session)) {
                                "Could not append detected sleep ActivityEvent"
                            }
                        }
                        written++
                    }
                    SleepMerge.Outcome.Skip -> Unit
                }
            }
        }
        if (written > 0) refresh()
        lastImportAt = now()
        return written
    }

    private fun resolveImport(candidate: SleepSession): SleepMerge.Outcome {
        // Already imported: same record, seen again on a later pass.
        val existingByExternal: SleepSession? = candidate.externalId?.let { external ->
            q.selectByExternalId(external).executeAsOneOrNull()
                ?.let { row: com.example.tafakkurai.db.SelectByExternalId -> toDomain(row) }
        }

        val overlapping = q.selectOverlapping(candidate.inBedEndMs, candidate.inBedStartMs)
            .executeAsList()
            .map(::toDomain)

        return SleepMerge.resolve(
            candidate = candidate.copy(
                id = existingByExternal?.id ?: candidate.id.ifBlank { newId() },
                dateKey = SleepTime.dateKeyOf(candidate.endMs, offsetHours()),
                createdAt = existingByExternal?.createdAt ?: candidate.createdAt,
                updatedAt = now(),
            ),
            existingByExternalId = existingByExternal,
            overlapping = overlapping,
        )
    }

    fun setImporting(value: Boolean) {
        _importing = value
    }

    /**
     * Applies sessions pulled from the backend without re-queueing them for upload.
     *
     * Mirrors `GratitudeStore.mergeFromServer`. The generic sync engine already writes pulled
     * rows directly with capture disabled, so this exists for the one case it does not cover:
     * a feature-level restore, where the client asks for history it never had.
     */
    fun mergeFromServer(remote: List<SleepSession>, deletedIds: Set<String> = emptySet()) {
        remote.forEach { session ->
            persist(session)
            val index = _sessions.indexOfFirst { it.id == session.id }
            if (index >= 0) _sessions[index] = session else _sessions.add(session)
        }
        deletedIds.forEach { id ->
            val persisted = runCatching { db.transaction { q.softDelete(now(), id) } }.isSuccess
            if (persisted) _sessions.removeAll { it.id == id }
        }
        _sessions.sortByDescending { it.startMs }
    }

    // ── routine ──────────────────────────────────────────────────────────────

    /** Steps for [phase] on [dateKey], with the user's enabled set and today's ticks applied. */
    fun routine(phase: RoutinePhase, dateKey: String): RoutineProgress =
        routine(phase, dateKey, routineMarks[dateKey].orEmpty())

    private fun routine(phase: RoutinePhase, dateKey: String, done: Set<String>): RoutineProgress {
        return RoutineProgress(
            phase = phase,
            dateKey = dateKey,
            steps = settings.visibleSteps(phase).map { step ->
                RoutineStepState(
                    step = step,
                    enabled = settings.isEnabled(step.id),
                    completed = step.id in done,
                )
            },
        )
    }

    fun isStepCompleted(dateKey: String, stepId: String): Boolean =
        routineMarks[dateKey]?.contains(stepId) == true

    fun setStepCompleted(dateKey: String, step: RoutineStep, completed: Boolean): Boolean {
        val markId = "$dateKey|${step.id}"
        val current = routineMarks[dateKey].orEmpty()
        if ((step.id in current) == completed) return true
        val next = current.toMutableSet().apply {
            if (completed) add(step.id) else remove(step.id)
        }
        val progress = routine(step.phase, dateKey, next)
        val stamp = now()
        val persisted = runCatching {
            db.transaction {
                if (completed) {
                    rq.complete(markId, step.id, step.phase.name.lowercase(), dateKey, stamp)
                } else {
                    rq.uncomplete(markId)
                }
                check(
                    activity.routineStepChanged(
                        step = step,
                        dateKey = dateKey,
                        completed = completed,
                        completedCount = progress.completedCount,
                        totalCount = progress.totalCount,
                        changedAt = stamp,
                    ),
                ) { "Could not append routine-step ActivityEvent" }
            }
        }.isSuccess
        if (!persisted) return false
        if (next.isEmpty()) {
            routineMarks.remove(dateKey)
        } else {
            // Re-assign so Compose sees a new instance and recomposes the checklist.
            routineMarks[dateKey] = next
        }
        return true
    }

    /** Days on which every enabled step of [phase] was completed — the routine streak. */
    fun routineCompletedDays(phase: RoutinePhase): Set<String> {
        val required = settings.visibleSteps(phase).filter { settings.isEnabled(it.id) }.map { it.id }.toSet()
        if (required.isEmpty()) return emptySet()
        return routineMarks.entries
            .filter { (_, done) -> done.containsAll(required) }
            .map { it.key }
            .toSet()
    }

    // ── settings ─────────────────────────────────────────────────────────────

    fun saveSettings(next: SleepSettings) {
        settings = next
        runCatching { prefs?.put(PREF_SETTINGS, syncJson.encodeToString(next)) }
    }

    private fun loadSettings() {
        val raw = runCatching { prefs?.get(PREF_SETTINGS) }.getOrNull() ?: return
        runCatching { syncJson.decodeFromString(SleepSettings.serializer(), raw) }
            .getOrNull()?.let { settings = it }
    }

    // ── plumbing ─────────────────────────────────────────────────────────────

    private fun insertSorted(session: SleepSession) {
        val index = _sessions.indexOfFirst { it.startMs < session.startMs }
        if (index < 0) _sessions.add(session) else _sessions.add(index, session)
    }

    private fun persist(s: SleepSession) {
        q.upsert(
            id = s.id,
            serverId = null,
            kind = s.kind.token,
            source = s.source.token,
            externalId = s.externalId,
            startMs = s.startMs,
            endMs = s.endMs,
            inBedStartMs = s.inBedStartMs,
            inBedEndMs = s.inBedEndMs,
            segments = syncJson.encodeToString(s.segments),
            vitals = syncJson.encodeToString(s.vitals),
            dateKey = s.dateKey,
            moodOnWaking = s.moodOnWaking,
            note = s.note,
            tags = syncJson.encodeToString(s.tags),
            createdAt = s.createdAt,
            updatedAt = s.updatedAt,
            deleted = 0L,
        )
    }

    private fun toDomain(row: com.example.tafakkurai.db.SleepSession) = SleepSession(
        id = row.id,
        kind = SleepKind.fromToken(row.kind),
        source = SleepSource.fromToken(row.source),
        externalId = row.externalId,
        startMs = row.startMs,
        endMs = row.endMs,
        inBedStartMs = row.inBedStartMs,
        inBedEndMs = row.inBedEndMs,
        segments = decodeSegments(row.segments),
        vitals = decodeVitals(row.vitals),
        dateKey = row.dateKey,
        moodOnWaking = row.moodOnWaking,
        note = row.note,
        tags = decodeTags(row.tags),
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    // SQLDelight narrows `externalId` from String? to String for the
    // `externalId IS NOT NULL` query, so it generates a query-specific result type rather
    // than the table's SleepSession type. Keep the conversion explicit so both generated
    // shapes map to the same domain model on JVM and Kotlin/Native.
    private fun toDomain(row: com.example.tafakkurai.db.SelectByExternalId) = SleepSession(
        id = row.id,
        kind = SleepKind.fromToken(row.kind),
        source = SleepSource.fromToken(row.source),
        externalId = row.externalId,
        startMs = row.startMs,
        endMs = row.endMs,
        inBedStartMs = row.inBedStartMs,
        inBedEndMs = row.inBedEndMs,
        segments = decodeSegments(row.segments),
        vitals = decodeVitals(row.vitals),
        dateKey = row.dateKey,
        moodOnWaking = row.moodOnWaking,
        note = row.note,
        tags = decodeTags(row.tags),
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun decodeSegments(raw: String): List<SleepSegment> =
        runCatching { syncJson.decodeFromString<List<SleepSegment>>(raw) }.getOrDefault(emptyList())

    private fun decodeVitals(raw: String): SleepVitals =
        runCatching { syncJson.decodeFromString<SleepVitals>(raw) }.getOrDefault(SleepVitals.NONE)

    private fun decodeTags(raw: String): List<String> =
        runCatching { syncJson.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
}

/**
 * Import conflict resolution.
 *
 * Extracted from the store so it can be unit-tested exhaustively without a database — this is
 * the logic most likely to silently corrupt a user's history, and the least likely to be
 * noticed doing it, because the symptom is "my averages look a bit off" months later.
 */
object SleepMerge {

    sealed interface Outcome {
        /** No conflict — write it. */
        data class Insert(val session: SleepSession) : Outcome

        /** Supersedes a weaker record, which is tombstoned. */
        data class Replace(val session: SleepSession, val replacedId: String) : Outcome

        /** A better record is already held. */
        data object Skip : Outcome
    }

    /**
     * Decides what to do with one incoming session.
     *
     * The rules, in order:
     *
     *  1. **Known record wins by identity.** If the same `externalId` is already stored, this
     *     is an update to a night we already have, not a new one. Health stores revise
     *     sessions after the fact — Apple Watch commonly rewrites staging the following
     *     morning — so an update must land on the existing row.
     *  2. **Higher-ranked source wins an overlap.** A watch measured the night; phone-based
     *     detection guessed it. When both describe the same window, keep the measurement.
     *  3. **Equal rank: the more detailed record wins.** Between two watch records for the
     *     same night, prefer the one with real staging, then the longer one.
     *  4. **Otherwise skip.** Never store two sessions for one night.
     */
    fun resolve(
        candidate: SleepSession,
        existingByExternalId: SleepSession?,
        overlapping: List<SleepSession>,
    ): Outcome {
        // 1. Same record, seen again.
        if (existingByExternalId != null) {
            return Outcome.Insert(candidate.copy(id = existingByExternalId.id))
        }

        // Ignore an overlap with the record this candidate already is.
        val rivals = overlapping.filter { it.id != candidate.id && it.externalId != candidate.externalId }
        if (rivals.isEmpty()) return Outcome.Insert(candidate)

        val incumbent = rivals.maxByOrNull { strength(it) } ?: return Outcome.Insert(candidate)

        return when {
            strength(candidate) > strength(incumbent) ->
                Outcome.Replace(candidate, incumbent.id)
            else -> Outcome.Skip
        }
    }

    /**
     * How much a record is worth, as a single comparable number.
     *
     * Source rank dominates, then staging, then duration. Encoding it this way keeps the
     * comparison total — a partial ordering here would make the outcome depend on the order
     * the health store happened to return records in.
     */
    internal fun strength(session: SleepSession): Long {
        var score = session.source.rank.toLong() * 1_000_000L
        if (session.hasStaging) score += 500_000L
        if (!session.vitals.isEmpty) score += 100_000L
        score += session.asleepMinutes.toLong()
        return score
    }
}
