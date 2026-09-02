package com.example.tafakkurai.social.domain

/** Domain boundary for private relationship state and local feed preferences. */
interface SocialRepository {
    val connections: List<SocialConnection>
    val checkIns: List<SocialCheckIn>
    val revision: Int
    val loadFailed: Boolean

    fun refresh()
    fun currentDateKey(): String
    fun isJoined(circleId: String): Boolean
    fun setCircleJoined(circleId: String, joined: Boolean, source: String = "user"): Boolean

    fun addConnection(
        name: String,
        relationshipId: String = "other",
        source: String = "user",
    ): SocialConnection?

    fun checkIn(
        connectionId: String,
        note: String = "",
        dateKey: String = currentDateKey(),
        source: String = "user",
    ): SocialCheckIn?

    fun deleteConnection(id: String, source: String = "user"): Boolean
    fun connectionSummaries(): List<SocialConnectionSummary>
    fun postPreference(postId: String): SocialPostPreference
    fun setPostLiked(postId: String, liked: Boolean, source: String = "user"): Boolean
    fun setPostBookmarked(postId: String, bookmarked: Boolean, source: String = "user"): Boolean
    fun recordPublished(postId: String, source: String = "user"): Boolean
}
