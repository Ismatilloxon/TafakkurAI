package com.example.tafakkurai.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * The entire network surface of synchronisation: two calls, forever.
 *
 * There is deliberately no per-feature endpoint here. `POST /v1/sync/batch` carries any
 * feature's changes and `GET /v1/sync/changes` returns any feature's updates, because
 * both speak in `(feature, entity, entityId, payload)` rather than in domain types.
 */
class SyncApi(private val client: HttpClient) {

    suspend fun push(events: List<SyncEventDto>): BatchResponseDto =
        client.post("v1/sync/batch") { setBody(BatchRequestDto(events)) }.body()

    suspend fun pull(cursor: Long, limit: Int): ChangeSetDto =
        client.get("v1/sync/changes") {
            parameter("cursor", cursor)
            parameter("limit", limit)
        }.body()
}

/**
 * One queued change on the wire.
 *
 * `payload` is a [JsonElement], not a String: the server stores it as jsonb and would
 * receive a quoted string if this were typed as String, turning every row's payload
 * into an opaque blob that no server-side query could ever look inside.
 */
@Serializable
data class SyncEventDto(
    val id: String,
    val feature: String,
    val entity: String,
    val entityId: String,
    val operation: String,
    val payload: JsonElement = JsonNull,
    val metadata: JsonElement = JsonNull,
    val deviceTime: Long,
    val timezone: String,
)

@Serializable
data class BatchRequestDto(val events: List<SyncEventDto>)

@Serializable
data class BatchResponseDto(
    val results: List<EventResultDto> = emptyList(),
    val cursor: Long = 0,
    val stats: BatchStatsDto = BatchStatsDto(),
    // Typed as raw JSON so the sync platform does not depend on the gamification model.
    // Whoever cares about the streak decodes it; the queue does not.
    val gamification: JsonObject? = null,
)

/**
 * The server's verdict on one event.
 *
 * Four outcomes, and only one of them means "keep it":
 *
 *  - `applied`   — stored. Drop it locally.
 *  - `duplicate` — already stored from an earlier delivery. Drop it: retrying achieves
 *                  nothing, and treating it as a failure is how a queue wedges forever.
 *  - `stale`     — a newer version of this row is already on the server. Drop it.
 *  - `rejected`  — malformed and will never be accepted. Drop it and count it, because
 *                  a rising rejected count is a client bug that needs finding.
 */
@Serializable
data class EventResultDto(
    val id: String,
    val status: String,
    val reason: String = "",
)

@Serializable
data class BatchStatsDto(
    val received: Int = 0,
    val applied: Int = 0,
    val duplicates: Int = 0,
    val rejected: Int = 0,
    val stale: Int = 0,
)

@Serializable
data class ChangeSetDto(
    val changes: List<ChangeDto> = emptyList(),
    val cursor: Long = 0,
    val more: Boolean = false,
)

@Serializable
data class ChangeDto(
    val feature: String = "",
    val entity: String,
    @SerialName("entityId") val entityId: String,
    val payload: JsonElement = JsonNull,
    val deleted: Boolean = false,
    val seq: Long = 0,
)

/** Server-side status strings, kept as constants so a typo cannot silently requeue forever. */
internal object EventStatus {
    const val APPLIED = "applied"
    const val DUPLICATE = "duplicate"
    const val STALE = "stale"
    const val REJECTED = "rejected"

    /** Every terminal status: the event has done all it ever will and can be deleted. */
    val TERMINAL = setOf(APPLIED, DUPLICATE, STALE, REJECTED)
}
