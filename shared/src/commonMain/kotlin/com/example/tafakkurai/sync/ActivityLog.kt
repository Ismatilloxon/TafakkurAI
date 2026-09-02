package com.example.tafakkurai.sync

import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.prayer.deviceTimeZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * The single SQL write path for append-only user activity.
 *
 * Every fact is first stored in `activityEvent`, because the delivery outbox deletes rows
 * after acknowledgement and therefore cannot power product features. The same transaction
 * appends an inline sync event for the backend. [SyncScheduler] remains the only component
 * that performs network work: recording an activity never starts a request.
 */
class ActivityLog(
    private val db: TafakkurDb,
    private val now: () -> Long = ::epochMillis,
    private val timezone: () -> String = ::deviceTimeZoneId,
    private val localDate: () -> String = ::deviceDateKey,
    private val newId: () -> String = ::activityUuidV4,
    remoteChanges: Flow<Set<String>> = emptyFlow(),
    scope: CoroutineScope? = null,
) {
    private val _events = MutableStateFlow(loadRecent())
    val events: StateFlow<List<RecordedActivity>> = _events.asStateFlow()

    init {
        // The sync engine remains feature-blind: it only names tables changed by a pull.
        // This app-wide singleton owns exactly one collector, using the same long-lived scope
        // as sync scheduling, so remote immutable facts immediately update every projection
        // already observing [events]. Plain/local constructions keep working via emptyFlow().
        // UNDISPATCHED subscribes before construction returns, so the first app-start pull
        // cannot slip between resolving this singleton and its collector actually starting.
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            remoteChanges.collect { tables ->
                if (ENTITY in tables) refresh()
            }
        }
    }

    /** Records one immutable activity in SQL. No network work happens on this call. */
    fun record(
        feature: String,
        type: String,
        properties: JsonObject = EMPTY,
        entityId: String? = null,
        source: String = SOURCE_USER,
        eventId: String? = null,
        occurredAtOverride: Long? = null,
        localDateOverride: String? = null,
        timezoneOverride: String? = null,
    ): Boolean {
        val id = eventId ?: newId()
        if (!UUID_PATTERN.matches(id)) return false

        val queuedAt = now()
        val occurredAt = occurredAtOverride?.takeIf { it > 0L } ?: queuedAt
        val zone = (timezoneOverride ?: timezone()).trim()
            .take(MAX_TIMEZONE_LENGTH)
            .ifEmpty { DEFAULT_TIMEZONE }
        val date = localDateOverride?.trim()?.takeIf(DATE_PATTERN::matches) ?: localDate()
        val event = RecordedActivity(
            id = id,
            userId = "",
            feature = feature.trim().take(MAX_FEATURE_LENGTH).ifEmpty { DEFAULT_FEATURE },
            type = type.trim().take(MAX_TYPE_LENGTH).ifEmpty { DEFAULT_TYPE },
            entityId = entityId?.trim()?.take(MAX_ENTITY_ID_LENGTH)?.takeIf { it.isNotEmpty() }
                ?: deriveEntityId(properties)
                ?: id,
            occurredAt = occurredAt,
            localDate = date,
            timezone = zone,
            source = source.trim().take(MAX_SOURCE_LENGTH).ifEmpty { SOURCE_USER },
            schemaVersion = SCHEMA_VERSION,
            properties = properties,
        )
        val payload = activityPayload(event)
        if (payload.length > MAX_PAYLOAD_CHARS) return false

        var stored: RecordedActivity? = null
        val durable = runCatching {
            db.transaction {
                val outbox = db.syncEventQueries
                val activity = db.activityEventQueries

                // A first-run install may act before SyncEngine attaches. Seed the session;
                // attach later adopts both the durable row and its queued delivery.
                outbox.seedSession()
                if (activity.selectById(id).executeAsOneOrNull() != null) return@transaction
                val user = outbox.currentUser().executeAsOneOrNull().orEmpty()
                val persistedEvent = event.copy(userId = user)
                stored = persistedEvent
                activity.insertEvent(
                    id = persistedEvent.id,
                    userId = persistedEvent.userId,
                    feature = persistedEvent.feature,
                    type = persistedEvent.type,
                    entityId = persistedEvent.entityId,
                    occurredAt = persistedEvent.occurredAt,
                    localDate = persistedEvent.localDate,
                    timezone = persistedEvent.timezone,
                    source = persistedEvent.source,
                    schemaVersion = persistedEvent.schemaVersion.toLong(),
                    properties = syncJson.encodeToString(JsonObject.serializer(), persistedEvent.properties),
                )
                enqueue(outbox, persistedEvent, payload, queuedAt)
            }
            true
        }.getOrDefault(false)

        if (!durable) {
            // A queued-only fact cannot power local Today/habit/balance projections and
            // would let feature stores commit half of a connected action. Fail closed so
            // the feature's outer transaction can roll its source write back atomically.
            return false
        }
        // Replaying an existing event id is a successful no-op. Feature stores use this
        // return value inside their own transaction, so reporting false here would turn a
        // harmless replay into a rolled-back source-of-truth write.
        val inserted = stored ?: return true
        _events.value = (listOf(inserted) + _events.value.filterNot { it.id == inserted.id })
            .take(MAX_IN_MEMORY_EVENTS)
        return true
    }

    /** Current local day's connected plan projection. */
    fun todayPlan(date: String = localDate()): List<ActivityPlanProjection> =
        projectTodayActivities(_events.value, date)

    /** Re-read after a remote merge or account switch. */
    fun refresh() {
        _events.value = loadRecent()
    }

    /** Full durable study history used to backfill quiz goals after a schema upgrade. */
    fun learningScopeEvents(): List<RecordedActivity> = runCatching {
        db.activityEventQueries.selectLearningScopeEvents(mapper = ::recordedActivityFromFields)
            .executeAsList()
    }.getOrElse {
        // The in-memory window still keeps new actions connected on an install whose
        // migration has not completed; the store reports its own load failure separately.
        _events.value.filter { event ->
            (event.feature == "quran" && event.type in QURAN_SCOPE_TYPES) ||
                (event.feature == "hifz" && event.type in HIFZ_SCOPE_TYPES)
        }
    }

    private fun loadRecent(): List<RecordedActivity> = runCatching {
        db.activityEventQueries.selectRecent(
            MAX_IN_MEMORY_EVENTS.toLong(),
            mapper = ::recordedActivityFromFields,
        ).executeAsList()
    }.getOrDefault(emptyList())

    private fun recordedActivityFromFields(
        id: String,
        userId: String,
        feature: String,
        type: String,
        entityId: String,
        occurredAt: Long,
        localDate: String,
        timezone: String,
        source: String,
        schemaVersion: Long,
        properties: String,
    ): RecordedActivity = RecordedActivity(
        id = id,
        userId = userId,
        feature = feature,
        type = type,
        entityId = entityId,
        occurredAt = occurredAt,
        localDate = localDate,
        timezone = timezone,
        source = source,
        schemaVersion = schemaVersion.toInt(),
        properties = runCatching {
            syncJson.parseToJsonElement(properties) as? JsonObject
        }.getOrNull() ?: EMPTY,
    )

    private fun enqueue(
        q: com.example.tafakkurai.db.SyncEventQueries,
        event: RecordedActivity,
        payload: String,
        queuedAt: Long,
    ) {
        q.enqueueActivity(
            event.id,
            event.feature,
            ENTITY,
            event.id,
            "insert",
            ENTITY,
            event.id,
            payload,
            event.occurredAt,
            event.timezone,
            queuedAt,
            queuedAt,
        )
    }

    private companion object {
        const val ENTITY = "activityEvent"
        const val DEFAULT_FEATURE = "app"
        const val DEFAULT_TYPE = "activity"
        const val DEFAULT_TIMEZONE = "UTC"
        const val SOURCE_USER = "user"
        const val SCHEMA_VERSION = 1
        const val MAX_FEATURE_LENGTH = 64
        const val MAX_TYPE_LENGTH = 64
        const val MAX_ENTITY_ID_LENGTH = 512
        const val MAX_SOURCE_LENGTH = 32
        const val MAX_TIMEZONE_LENGTH = 64
        const val MAX_PAYLOAD_CHARS = 48 * 1024
        const val MAX_IN_MEMORY_EVENTS = 1_000
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
        val EMPTY = JsonObject(emptyMap())
        val QURAN_SCOPE_TYPES = setOf("surah_started", "ayah_marked_read", "ayah_listened")
        val HIFZ_SCOPE_TYPES = setOf("ayahs_enrolled", "ayah_reviewed")
    }
}

private fun deriveEntityId(properties: JsonObject): String? {
    val keys = listOf(
        "entry_id", "habit_id", "challenge_id", "deck_id", "card_id", "item_id",
        "session_id", "task_id", "goal_id", "post_id", "surah", "prayer",
    )
    for (key in keys) {
        val value = runCatching { properties[key]?.jsonPrimitive?.content }.getOrNull()
        if (!value.isNullOrBlank()) return value.take(512)
    }
    return null
}

private fun activityPayload(event: RecordedActivity): String =
    syncJson.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            for ((key, value) in event.properties) put(key, value)
            // Reserved envelope fields always win over caller-supplied properties.
            put("type", event.type)
            put("at", event.occurredAt)
            put("entityId", event.entityId)
            put("localDate", event.localDate)
            put("timezone", event.timezone)
            put("source", event.source)
            put("schemaVersion", event.schemaVersion)
        },
    )

internal fun activityPayload(type: String, at: Long, properties: JsonObject): String =
    syncJson.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            for ((key, value) in properties) put(key, value)
            // Reserved envelope fields always win over caller-supplied properties.
            put("type", type)
            put("at", at)
        },
    )

/** UUID v4 without a platform UUID dependency. */
internal fun activityUuidV4(): String {
    val bytes = ByteArray(16) { Random.nextInt(256).toByte() }
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val hex = bytes.joinToString("") { b ->
        val v = b.toInt() and 0xFF
        val chars = "0123456789abcdef"
        "${chars[v shr 4]}${chars[v and 0x0F]}"
    }
    return buildString {
        append(hex, 0, 8); append('-')
        append(hex, 8, 12); append('-')
        append(hex, 12, 16); append('-')
        append(hex, 16, 20); append('-')
        append(hex, 20, 32)
    }
}
