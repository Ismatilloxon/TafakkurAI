package com.example.tafakkurai.sleep

import com.example.tafakkurai.sync.ActivityLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Activity events for Sleep & Rest.
 *
 * ### Why these are not rows in a feature table
 *
 * Everything else in this module is *state*: a night happened, a step was ticked. These are
 * *facts about behaviour* — a widget was tapped, a sync started — and they are append-only,
 * high-volume and never read back by the client. Putting them in a synced feature table would
 * mean the generic engine backfilling and upserting them like user data, which is the wrong
 * lifecycle entirely.
 *
 * They are written through the app-wide [ActivityLog] into `syncEvent` with an inline payload.
 * That reuses the outbox's batching, retry, dedupe and backoff without adding a feature table,
 * and the events ride the same `/v1/sync/batch` call everything else uses.
 *
 * ### The four properties the brief asks for
 *
 * - **Small** — a flat payload of scalars, never a nested session.
 * - **Cacheable** — immutable once written; an event never updates.
 * - **Serializable** — plain JSON via the same `syncJson` the outbox uses.
 * - **Idempotent** — the id is a v4 UUID generated at insert and is the server's idempotency
 *   key, so a redelivered batch is recognised and dropped rather than double-counted.
 *
 * Note the difference from the trigger-based path: feature tables are captured by triggers
 * that leave `payload` NULL and materialise it at flush time from the current row. An
 * activity event has no row to materialise from, so it carries its payload inline. That is
 * the one place in the app where the outbox is written to directly, and this comment is here
 * so the next person does not "fix" it into a table.
 */
@Serializable
enum class SleepActivityType {
    SLEEP_STARTED,
    SLEEP_ENDED,
    SLEEP_DETECTED,
    SLEEP_SESSION_DELETED,
    NAP_DETECTED,
    NAP_LOGGED,
    REMINDER_OPENED,
    REMINDER_COMPLETED,
    HEALTH_SYNC_STARTED,
    HEALTH_SYNC_FINISHED,
    HEALTH_PERMISSION_GRANTED,
    HEALTH_PERMISSION_DENIED,
    WATCH_CONNECTED,
    WATCH_DISCONNECTED,
    WIDGET_OPENED,
    WIDGET_TAPPED,
    LIVE_ACTIVITY_STARTED,
    LIVE_ACTIVITY_ENDED,
    RECOVERY_VIEWED,
    STATISTICS_VIEWED,
    TIMELINE_VIEWED,
    MORNING_ROUTINE_STARTED,
    MORNING_ROUTINE_FINISHED,
    BEDTIME_ROUTINE_STARTED,
    BEDTIME_ROUTINE_FINISHED,
    ROUTINE_STEP_COMPLETED,
    ROUTINE_STEP_UNCOMPLETED,
    RECOMMENDATION_SHOWN,
    RECOMMENDATION_ACTED,
    GOALS_CHANGED;

    /** Stable wire token. Never localise or rename — the backend aggregates on these. */
    val token: String get() = name.lowercase()
}

/**
 * Writes activity events into the sync outbox.
 *
 * Diagnostic events remain best-effort at their call sites. Source-of-truth actions use the
 * stable-identity helpers below inside the feature store's SQL transaction, where `false`
 * deliberately rolls the source write back rather than leaving the connected system stale.
 */
class SleepActivityLog(
    private val activity: ActivityLog,
) {

    /** Records one event. [properties] must stay flat and small. */
    fun record(type: SleepActivityType, properties: JsonObject = EMPTY): Boolean =
        activity.record(FEATURE, type.token, properties)

    // ── typed helpers, so call sites cannot misspell a property name ─────────

    fun sleepStarted(source: SleepSource) = record(
        SleepActivityType.SLEEP_STARTED,
        buildJsonObject { put("source", source.token) },
    )

    fun sleepEnded(session: SleepSession, score: Int? = null): Boolean = activity.record(
        feature = FEATURE,
        type = SleepActivityType.SLEEP_ENDED.token,
        entityId = session.id,
        source = session.source.token,
        occurredAtOverride = session.endMs,
        localDateOverride = session.dateKey,
        properties = buildJsonObject {
            put("session_id", session.id)
            put("source", session.source.token)
            put("kind", session.kind.token)
            put("asleep_minutes", session.asleepMinutes)
            put("efficiency", (session.efficiency * 100).toInt())
            if (score != null) put("score", score)
            put("has_staging", session.hasStaging)
        },
    )

    /**
     * Records a newly discovered current-day health-store session.
     *
     * The platform record UUID is reused as the event id when possible, making a replay a
     * no-op. Its wake instant/date are preserved instead of pretending a morning session
     * occurred later when the user opened the app.
     */
    fun sleepDetected(session: SleepSession): Boolean = activity.record(
        feature = FEATURE,
        type = if (session.kind == SleepKind.NAP) {
            SleepActivityType.NAP_DETECTED.token
        } else {
            SleepActivityType.SLEEP_DETECTED.token
        },
        entityId = session.id,
        source = session.source.token,
        eventId = session.externalId?.takeIf(EVENT_UUID_PATTERN::matches),
        occurredAtOverride = session.endMs,
        localDateOverride = session.dateKey,
        properties = buildJsonObject {
            put("session_id", session.id)
            put("source", session.source.token)
            put("kind", session.kind.token)
            put("asleep_minutes", session.asleepMinutes)
            put("has_staging", session.hasStaging)
        },
    )

    fun napLogged(session: SleepSession, verdict: NapVerdict): Boolean = activity.record(
        feature = FEATURE,
        type = SleepActivityType.NAP_LOGGED.token,
        entityId = session.id,
        source = session.source.token,
        occurredAtOverride = session.endMs,
        localDateOverride = session.dateKey,
        properties = buildJsonObject {
            put("session_id", session.id)
            put("source", session.source.token)
            put("minutes", session.asleepMinutes)
            put("verdict", verdict.name.lowercase())
        },
    )

    /** Compensates the session's earlier completion while preserving immutable history. */
    fun sessionDeleted(session: SleepSession, deletedAt: Long): Boolean = activity.record(
        feature = FEATURE,
        type = SleepActivityType.SLEEP_SESSION_DELETED.token,
        entityId = session.id,
        occurredAtOverride = deletedAt,
        localDateOverride = session.dateKey,
        properties = buildJsonObject {
            put("session_id", session.id)
            put("kind", session.kind.token)
            put("original_source", session.source.token)
        },
    )

    fun healthSyncStarted(platform: String) = record(
        SleepActivityType.HEALTH_SYNC_STARTED,
        buildJsonObject { put("platform", platform) },
    )

    fun healthSyncFinished(platform: String, imported: Int, durationMs: Long, error: String? = null) = record(
        SleepActivityType.HEALTH_SYNC_FINISHED,
        buildJsonObject {
            put("platform", platform)
            put("imported", imported)
            put("duration_ms", durationMs)
            put("ok", error == null)
            // Platform exception text can contain implementation details and is neither
            // stable analytics nor user-facing copy. The boolean is enough for projection;
            // diagnostics stay in process-local logging rather than the synced activity log.
            if (error != null) put("error_code", "health_read_failed")
        },
    )

    /** One checklist tap, keyed to the routine/day so its latest state wins in Today. */
    fun routineStepChanged(
        step: RoutineStep,
        dateKey: String,
        completed: Boolean,
        completedCount: Int,
        totalCount: Int,
        changedAt: Long,
    ): Boolean = activity.record(
        feature = FEATURE,
        type = if (completed) {
            SleepActivityType.ROUTINE_STEP_COMPLETED.token
        } else {
            SleepActivityType.ROUTINE_STEP_UNCOMPLETED.token
        },
        entityId = routineEntityId(step.phase, dateKey),
        occurredAtOverride = changedAt,
        localDateOverride = dateKey,
        properties = buildJsonObject {
            put("step_id", step.id)
            put("phase", step.phase.name.lowercase())
            put("completed", completed)
            put("completed_count", completedCount)
            put("total_count", totalCount)
            put("routine_complete", totalCount > 0 && completedCount == totalCount)
            // Whether the user runs the optional Islamic steps is aggregate-only signal;
            // the step id already identifies it, and no free text ever leaves the device.
            put("optional_faith", step.optionalFaith)
        },
    )

    fun routineFinished(
        phase: RoutinePhase,
        dateKey: String,
        completed: Int,
        total: Int,
        changedAt: Long,
    ): Boolean = activity.record(
        feature = FEATURE,
        type = when (phase) {
            RoutinePhase.BEDTIME -> SleepActivityType.BEDTIME_ROUTINE_FINISHED.token
            RoutinePhase.MORNING -> SleepActivityType.MORNING_ROUTINE_FINISHED.token
        },
        entityId = routineEntityId(phase, dateKey),
        occurredAtOverride = changedAt,
        localDateOverride = dateKey,
        properties = buildJsonObject {
            put("completed", completed)
            put("total", total)
        },
    )

    fun routineStarted(phase: RoutinePhase, dateKey: String, changedAt: Long): Boolean = activity.record(
        feature = FEATURE,
        type = when (phase) {
            RoutinePhase.BEDTIME -> SleepActivityType.BEDTIME_ROUTINE_STARTED.token
            RoutinePhase.MORNING -> SleepActivityType.MORNING_ROUTINE_STARTED.token
        },
        entityId = routineEntityId(phase, dateKey),
        occurredAtOverride = changedAt,
        localDateOverride = dateKey,
    )

    fun screenViewed(type: SleepActivityType, range: StatsRange? = null) = record(
        type,
        buildJsonObject { if (range != null) put("range", range.name.lowercase()) },
    )

    fun widgetTapped(widget: String) = record(
        SleepActivityType.WIDGET_TAPPED,
        buildJsonObject { put("widget", widget) },
    )

    fun recommendationShown(ids: List<String>) = record(
        SleepActivityType.RECOMMENDATION_SHOWN,
        buildJsonObject { put("ids", ids.joinToString(",")) },
    )

    fun recommendationActed(id: String) = record(
        SleepActivityType.RECOMMENDATION_ACTED,
        buildJsonObject { put("id", id) },
    )

    fun goalsChanged(goals: SleepGoals) = record(
        SleepActivityType.GOALS_CHANGED,
        buildJsonObject {
            put("need_minutes", goals.sleepNeedMinutes)
            put("bedtime_minutes", goals.targetBedtimeMinutes)
            put("wake_minutes", goals.targetWakeMinutes)
            put("align_fajr", goals.alignWakeWithFajr)
        },
    )

    fun watchConnected(model: String) = record(
        SleepActivityType.WATCH_CONNECTED,
        buildJsonObject { put("model", model) },
    )

    private companion object {
        const val FEATURE = "sleep"
        val EMPTY = JsonObject(emptyMap())
        val EVENT_UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )

        fun routineEntityId(phase: RoutinePhase, dateKey: String): String =
            "routine:${phase.name.lowercase()}:$dateKey"
    }
}
