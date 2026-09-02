package com.example.tafakkurai.social.presentation

import com.example.tafakkurai.social.domain.SOCIAL_CIRCLES
import com.example.tafakkurai.social.domain.SocialCircle
import com.example.tafakkurai.social.domain.SocialConnection
import com.example.tafakkurai.social.domain.SocialConnectionSummary
import com.example.tafakkurai.social.domain.SocialPostPreference
import com.example.tafakkurai.social.domain.SocialRepository
import com.example.tafakkurai.social.domain.normalizeConnectionName

data class SocialCircleState(
    val circle: SocialCircle,
    val joined: Boolean,
)

data class SocialUiState(
    val revision: Int,
    val circles: List<SocialCircleState>,
    val connections: List<SocialConnectionSummary>,
    val loadFailed: Boolean,
    val currentDateKey: String,
)

enum class SocialMutationResult {
    SUCCESS,
    INVALID_NAME,
    SAVE_FAILED,
}

/** Presentation command/state boundary for both Community surfaces. */
class SocialStateHolder(private val repository: SocialRepository) {
    val revision: Int get() = repository.revision

    fun state(): SocialUiState = SocialUiState(
        revision = repository.revision,
        circles = SOCIAL_CIRCLES.map { SocialCircleState(it, repository.isJoined(it.id)) },
        connections = repository.connectionSummaries(),
        loadFailed = repository.loadFailed,
        currentDateKey = repository.currentDateKey(),
    )

    fun refresh() = repository.refresh()

    fun setCircleJoined(circleId: String, joined: Boolean): SocialMutationResult =
        if (repository.setCircleJoined(circleId, joined)) {
            SocialMutationResult.SUCCESS
        } else {
            SocialMutationResult.SAVE_FAILED
        }

    fun addConnection(name: String, relationshipId: String): SocialMutationResult {
        if (normalizeConnectionName(name) == null) return SocialMutationResult.INVALID_NAME
        return if (repository.addConnection(name, relationshipId) != null) {
            SocialMutationResult.SUCCESS
        } else {
            SocialMutationResult.SAVE_FAILED
        }
    }

    fun checkIn(connectionId: String, note: String = ""): SocialMutationResult =
        if (repository.checkIn(connectionId, note) != null) {
            SocialMutationResult.SUCCESS
        } else {
            SocialMutationResult.SAVE_FAILED
        }

    fun deleteConnection(connection: SocialConnection): SocialMutationResult =
        if (repository.deleteConnection(connection.id)) {
            SocialMutationResult.SUCCESS
        } else {
            SocialMutationResult.SAVE_FAILED
        }

    fun postPreference(postId: String): SocialPostPreference = repository.postPreference(postId)

    fun setPostBookmarked(postId: String, bookmarked: Boolean): SocialMutationResult =
        if (repository.setPostBookmarked(postId, bookmarked)) {
            SocialMutationResult.SUCCESS
        } else {
            SocialMutationResult.SAVE_FAILED
        }
}
