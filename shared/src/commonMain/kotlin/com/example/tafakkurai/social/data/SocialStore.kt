package com.example.tafakkurai.social.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.social.domain.MAX_CHECK_IN_NOTE_LENGTH
import com.example.tafakkurai.social.domain.SOCIAL_CIRCLES
import com.example.tafakkurai.social.domain.SocialCheckIn
import com.example.tafakkurai.social.domain.SocialConnection
import com.example.tafakkurai.social.domain.SocialConnectionSummary
import com.example.tafakkurai.social.domain.SocialPostPreference
import com.example.tafakkurai.social.domain.SocialRepository
import com.example.tafakkurai.social.domain.normalizeConnectionName
import com.example.tafakkurai.social.domain.summarizeConnections
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** Private, local-first source of truth for circles, relationship check-ins and feed preferences. */
class SocialStore(
    private val db: TafakkurDb,
    private val activity: ActivityLog,
    private val today: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = ::randomSocialId,
) : SocialRepository {
    private val q get() = db.socialQueries
    private val memberships = mutableStateMapOf<String, Boolean>()
    private val _connections = mutableStateListOf<SocialConnection>()
    private val _checkIns = mutableStateListOf<SocialCheckIn>()
    private val postPreferences = mutableStateMapOf<String, SocialPostPreference>()

    override val connections: List<SocialConnection> get() = _connections
    override val checkIns: List<SocialCheckIn> get() = _checkIns

    override var revision by mutableStateOf(0)
        private set
    override var loadFailed by mutableStateOf(false)
        private set

    init {
        runCatching {
            listOf(
                "socialCircleMembership", "socialConnection", "socialCheckIn", "socialPostPreference",
            ).forEach { db.syncEventQueries.upsertPolicy(it, FEATURE, 1L) }
        }
        refresh()
    }

    override fun refresh() {
        val loaded = runCatching {
            LoadedSocialState(
                memberships = q.selectCircleMemberships().executeAsList(),
                connections = q.selectConnections().executeAsList().map(::connectionFromRow),
                checkIns = q.selectCheckIns().executeAsList().map(::checkInFromRow),
                preferences = q.selectPostPreferences().executeAsList().map(::preferenceFromRow),
            )
        }.getOrElse {
            loadFailed = true
            revision++
            return
        }
        memberships.clear()
        loaded.memberships.forEach { memberships[it.circleId] = it.joined == 1L }
        _connections.clear()
        _connections.addAll(loaded.connections)
        _checkIns.clear()
        _checkIns.addAll(loaded.checkIns)
        postPreferences.clear()
        loaded.preferences.associateByTo(postPreferences) { it.postId }
        loadFailed = false
        revision++
    }

    override fun currentDateKey(): String = today()

    override fun isJoined(circleId: String): Boolean = memberships[circleId] == true

    override fun setCircleJoined(circleId: String, joined: Boolean, source: String): Boolean {
        if (SOCIAL_CIRCLES.none { it.id == circleId }) return false
        if (isJoined(circleId) == joined) return true
        val stamp = now()
        val persisted = runCatching {
            db.transaction {
                q.setCircleMembership(circleId, if (joined) 1L else 0L, stamp)
                recordActivityOrThrow(
                    type = if (joined) "circle_joined" else "circle_left",
                    entityId = circleId,
                    source = source,
                ) {
                    put("circle_id", circleId)
                    put("joined", joined)
                }
            }
        }.isSuccess
        if (!persisted) return false
        memberships[circleId] = joined
        revision++
        return true
    }

    override fun addConnection(
        name: String,
        relationshipId: String,
        source: String,
    ): SocialConnection? {
        val safeName = normalizeConnectionName(name) ?: return null
        val safeRelationship = relationshipId.takeIf { it in RELATIONSHIPS } ?: "other"
        val stamp = now()
        val connection = SocialConnection(
            id = newId(),
            name = safeName,
            relationshipId = safeRelationship,
            lastContactDate = "",
            createdAt = stamp,
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                q.upsertConnection(
                    connection.id,
                    connection.name,
                    connection.relationshipId,
                    connection.lastContactDate,
                    connection.createdAt,
                    connection.updatedAt,
                    0L,
                )
                recordActivityOrThrow("connection_added", connection.id, source) {
                    put("connection_id", connection.id)
                    put("relationship", connection.relationshipId)
                }
            }
        }.isSuccess
        if (!persisted) return null
        _connections.add(connection)
        sortConnections()
        revision++
        return connection
    }

    override fun checkIn(
        connectionId: String,
        note: String,
        dateKey: String,
        source: String,
    ): SocialCheckIn? {
        val connectionIndex = _connections.indexOfFirst { it.id == connectionId }
        if (connectionIndex < 0 || !DATE_PATTERN.matches(dateKey)) return null
        _checkIns.firstOrNull { it.connectionId == connectionId && it.dateKey == dateKey }
            ?.let { return it }
        val stamp = now()
        val checkIn = SocialCheckIn(
            id = newId(),
            connectionId = connectionId,
            note = note.trim().take(MAX_CHECK_IN_NOTE_LENGTH),
            dateKey = dateKey,
            createdAt = stamp,
        )
        val connection = _connections[connectionIndex]
        val persisted = runCatching {
            db.transaction {
                q.insertCheckIn(
                    checkIn.id,
                    checkIn.connectionId,
                    checkIn.note,
                    checkIn.dateKey,
                    checkIn.createdAt,
                )
                q.markConnectionContacted(dateKey, stamp, connectionId)
                recordActivityOrThrow("relationship_check_in", checkIn.id, source) {
                    put("check_in_id", checkIn.id)
                    put("connection_id", connectionId)
                    put("relationship", connection.relationshipId)
                    put("date", dateKey)
                }
            }
        }.isSuccess
        if (!persisted) return null
        _checkIns.add(0, checkIn)
        _connections[connectionIndex] = connection.copy(lastContactDate = dateKey, updatedAt = stamp)
        sortConnections()
        revision++
        return checkIn
    }

    override fun deleteConnection(id: String, source: String): Boolean {
        val connection = _connections.firstOrNull { it.id == id } ?: return false
        val stamp = now()
        val persisted = runCatching {
            db.transaction {
                q.softDeleteConnection(stamp, id)
                q.softDeleteCheckInsForConnection(id)
                recordActivityOrThrow("connection_deleted", id, source) {
                    put("connection_id", id)
                    put("relationship", connection.relationshipId)
                }
            }
        }.isSuccess
        if (!persisted) return false
        _connections.removeAll { it.id == id }
        _checkIns.removeAll { it.connectionId == id }
        revision++
        return true
    }

    override fun connectionSummaries(): List<SocialConnectionSummary> =
        summarizeConnections(_connections, _checkIns)

    override fun postPreference(postId: String): SocialPostPreference = postPreferences[postId]
        ?: SocialPostPreference(postId, liked = false, bookmarked = false, updatedAt = 0L)

    override fun setPostLiked(postId: String, liked: Boolean, source: String): Boolean =
        updatePostPreference(postId, source, "post_like_changed") { it.copy(liked = liked) }

    override fun setPostBookmarked(postId: String, bookmarked: Boolean, source: String): Boolean =
        updatePostPreference(postId, source, "post_bookmark_changed") { it.copy(bookmarked = bookmarked) }

    override fun recordPublished(postId: String, source: String): Boolean {
        if (postId.isBlank()) return false
        return runCatching {
            val safeId = postId.trim().take(512)
            recordActivityOrThrow(
                type = "thought_published",
                entityId = safeId,
                source = source,
                eventId = safeId.takeIf(UUID_PATTERN::matches),
            ) {
                put("post_id", postId.take(512))
            }
            true
        }.getOrDefault(false)
    }

    private fun updatePostPreference(
        postId: String,
        source: String,
        eventType: String,
        transform: (SocialPostPreference) -> SocialPostPreference,
    ): Boolean {
        val safeId = postId.trim().take(512)
        if (safeId.isEmpty()) return false
        val stamp = now()
        val old = postPreference(safeId)
        val transformed = transform(old)
        if (old.updatedAt > 0L && transformed.liked == old.liked && transformed.bookmarked == old.bookmarked) {
            return true
        }
        val updated = transformed.copy(updatedAt = stamp)
        val persisted = runCatching {
            db.transaction {
                q.upsertPostPreference(
                    updated.postId,
                    if (updated.liked) 1L else 0L,
                    if (updated.bookmarked) 1L else 0L,
                    updated.updatedAt,
                )
                recordActivityOrThrow(eventType, safeId, source) {
                    put("post_id", safeId)
                    put("liked", updated.liked)
                    put("bookmarked", updated.bookmarked)
                }
            }
        }.isSuccess
        if (!persisted) return false
        postPreferences[safeId] = updated
        revision++
        return true
    }

    private fun recordActivityOrThrow(
        type: String,
        entityId: String,
        source: String,
        eventId: String? = null,
        properties: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) {
        check(
            activity.record(
                feature = FEATURE,
                type = type,
                entityId = entityId,
                source = source,
                eventId = eventId,
                properties = buildJsonObject(properties),
            ),
        ) { "Could not append social ActivityEvent" }
    }

    private fun sortConnections() {
        val sorted = _connections.sortedWith(
            compareBy<SocialConnection> { it.lastContactDate.isBlank() }
                .thenByDescending { it.lastContactDate }
                .thenBy { it.name.lowercase() },
        )
        _connections.clear()
        _connections.addAll(sorted)
    }

    private fun connectionFromRow(row: com.example.tafakkurai.db.SocialConnection) = SocialConnection(
        id = row.id,
        name = row.name,
        relationshipId = row.relationshipId,
        lastContactDate = row.lastContactDate,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun checkInFromRow(row: com.example.tafakkurai.db.SocialCheckIn) = SocialCheckIn(
        id = row.id,
        connectionId = row.connectionId,
        note = row.note,
        dateKey = row.dateKey,
        createdAt = row.createdAt,
    )

    private fun preferenceFromRow(row: com.example.tafakkurai.db.SocialPostPreference) =
        SocialPostPreference(
            postId = row.postId,
            liked = row.liked == 1L,
            bookmarked = row.bookmarked == 1L,
            updatedAt = row.updatedAt,
        )

    private companion object {
        const val FEATURE = "social"
        val RELATIONSHIPS = setOf("family", "friend", "mentor", "community", "other")
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        val UUID_PATTERN = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        )
    }

    private data class LoadedSocialState(
        val memberships: List<com.example.tafakkurai.db.SocialCircleMembership>,
        val connections: List<SocialConnection>,
        val checkIns: List<SocialCheckIn>,
        val preferences: List<SocialPostPreference>,
    )
}

private fun randomSocialId(): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "social-" + buildString { repeat(20) { append(alphabet[Random.nextInt(alphabet.length)]) } }
}
