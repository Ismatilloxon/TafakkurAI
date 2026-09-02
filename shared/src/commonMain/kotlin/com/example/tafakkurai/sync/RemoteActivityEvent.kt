package com.example.tafakkurai.sync

import com.example.tafakkurai.db.TafakkurDb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Applies the one append-only entity carried by the otherwise state-oriented sync feed.
 *
 * Mutable feature rows may use last-write-wins or another merge policy, but an activity is
 * a historical fact: the event UUID is its identity and receiving it twice must be a no-op.
 * A different fact with the same UUID is rejected instead of overwriting local history, so
 * devices cannot silently disagree about Today, Balance, habits, progression or learning.
 */
internal class RemoteActivityEventApplier(private val db: TafakkurDb) {

    fun apply(change: ChangeDto, userId: String) {
        val incoming = decode(change, userId)
        val existing = db.activityEventQueries.selectById(
            incoming.id,
            mapper = ::activityFromFields,
        ).executeAsOneOrNull()

        if (existing != null) {
            check(existing == incoming) {
                "remote activityEvent ${incoming.id} conflicts with an immutable local fact"
            }
            return
        }

        db.activityEventQueries.insertEvent(
            id = incoming.id,
            userId = incoming.userId,
            feature = incoming.feature,
            type = incoming.type,
            entityId = incoming.entityId,
            occurredAt = incoming.occurredAt,
            localDate = incoming.localDate,
            timezone = incoming.timezone,
            source = incoming.source,
            schemaVersion = incoming.schemaVersion.toLong(),
            properties = encodeProperties(incoming.properties),
        )

        // INSERT OR IGNORE closes the race between the read above and this write. Verify
        // what won rather than assuming a UUID collision contains the same immutable fact.
        val stored = db.activityEventQueries.selectById(
            incoming.id,
            mapper = ::activityFromFields,
        ).executeAsOneOrNull()
        check(stored == incoming) {
            "remote activityEvent ${incoming.id} was not stored exactly"
        }
    }

    private fun decode(change: ChangeDto, userId: String): RemoteActivity {
        check(change.entity == ENTITY)
        check(!change.deleted) { "activityEvent facts cannot be deleted" }
        check(UUID_PATTERN.matches(change.entityId)) { "activityEvent id is not a UUID" }
        check(userId.isNotBlank()) { "activityEvent has no authenticated owner" }
        check(change.feature.isNotBlank() && change.feature.length <= MAX_FEATURE_LENGTH) {
            "activityEvent feature is invalid"
        }

        val payload = change.payload as? JsonObject
            ?: error("activityEvent payload is not an object")
        val type = payload.string(TYPE)
        val entityId = payload.string(ENTITY_ID)
        val localDate = payload.string(LOCAL_DATE)
        val timezone = payload.string(TIMEZONE)
        val source = payload.string(SOURCE)
        val occurredAt = payload.long(AT)
        val schemaVersion = payload.int(SCHEMA_VERSION)

        check(type.isNotBlank() && type.length <= MAX_TYPE_LENGTH) {
            "activityEvent type is invalid"
        }
        check(entityId.isNotBlank() && entityId.length <= MAX_ENTITY_ID_LENGTH) {
            "activityEvent entityId is invalid"
        }
        check(occurredAt > 0L) { "activityEvent timestamp is invalid" }
        check(DATE_PATTERN.matches(localDate)) { "activityEvent localDate is invalid" }
        check(timezone.isNotBlank() && timezone.length <= MAX_TIMEZONE_LENGTH) {
            "activityEvent timezone is invalid"
        }
        check(source.isNotBlank() && source.length <= MAX_SOURCE_LENGTH) {
            "activityEvent source is invalid"
        }
        check(schemaVersion > 0) { "activityEvent schemaVersion is invalid" }

        return RemoteActivity(
            id = change.entityId,
            userId = userId,
            feature = change.feature,
            type = type,
            entityId = entityId,
            occurredAt = occurredAt,
            localDate = localDate,
            timezone = timezone,
            source = source,
            schemaVersion = schemaVersion,
            properties = JsonObject(payload.filterKeys { it !in RESERVED_FIELDS }),
        )
    }

    private fun activityFromFields(
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
    ): RemoteActivity = RemoteActivity(
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
        }.getOrNull() ?: JsonObject(emptyMap()),
    )

    private fun encodeProperties(properties: JsonObject): String =
        syncJson.encodeToString(JsonObject.serializer(), properties)

    private fun JsonObject.string(name: String): String =
        ((this[name] as? JsonPrimitive)?.contentOrNull)
            ?: error("activityEvent payload has no $name")

    private fun JsonObject.long(name: String): Long =
        string(name).toLongOrNull() ?: error("activityEvent payload has invalid $name")

    private fun JsonObject.int(name: String): Int =
        string(name).toIntOrNull() ?: error("activityEvent payload has invalid $name")

    private data class RemoteActivity(
        val id: String,
        val userId: String,
        val feature: String,
        val type: String,
        val entityId: String,
        val occurredAt: Long,
        val localDate: String,
        val timezone: String,
        val source: String,
        val schemaVersion: Int,
        val properties: JsonObject,
    )

    companion object {
        const val ENTITY = "activityEvent"

        private const val TYPE = "type"
        private const val AT = "at"
        private const val ENTITY_ID = "entityId"
        private const val LOCAL_DATE = "localDate"
        private const val TIMEZONE = "timezone"
        private const val SOURCE = "source"
        private const val SCHEMA_VERSION = "schemaVersion"

        private const val MAX_FEATURE_LENGTH = 64
        private const val MAX_TYPE_LENGTH = 64
        private const val MAX_ENTITY_ID_LENGTH = 512
        private const val MAX_SOURCE_LENGTH = 32
        private const val MAX_TIMEZONE_LENGTH = 64

        private val RESERVED_FIELDS = setOf(
            TYPE, AT, ENTITY_ID, LOCAL_DATE, TIMEZONE, SOURCE, SCHEMA_VERSION,
        )
        private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
    }
}
