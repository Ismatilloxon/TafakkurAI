package com.example.tafakkurai.social.domain

data class SocialCircle(
    val id: String,
    val titleKey: String,
    val descriptionKey: String,
    val icon: String,
)

/** Curated circle definitions contain no fabricated member counts or activity claims. */
val SOCIAL_CIRCLES: List<SocialCircle> = listOf(
    SocialCircle("learning", "social.circle.learning", "social.circle.learning_desc", "bookOpen"),
    SocialCircle("reflection", "social.circle.reflection", "social.circle.reflection_desc", "moon"),
    SocialCircle("language", "social.circle.language", "social.circle.language_desc", "languages"),
)

data class SocialConnection(
    val id: String,
    val name: String,
    val relationshipId: String,
    val lastContactDate: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SocialCheckIn(
    val id: String,
    val connectionId: String,
    val note: String,
    val dateKey: String,
    val createdAt: Long,
)

data class SocialPostPreference(
    val postId: String,
    val liked: Boolean,
    val bookmarked: Boolean,
    val updatedAt: Long,
)

data class SocialConnectionSummary(
    val connection: SocialConnection,
    val checkInCount: Int,
)

fun summarizeConnections(
    connections: List<SocialConnection>,
    checkIns: List<SocialCheckIn>,
): List<SocialConnectionSummary> {
    val counts = checkIns.groupingBy { it.connectionId }.eachCount()
    return connections.map { SocialConnectionSummary(it, counts[it.id] ?: 0) }
        .sortedWith(
            compareBy<SocialConnectionSummary> { it.connection.lastContactDate.isBlank() }
                .thenByDescending { it.connection.lastContactDate }
                .thenBy { it.connection.name.lowercase() },
        )
}

fun normalizeConnectionName(value: String): String? = value.trim()
    .replace(Regex("\\s+"), " ")
    .take(MAX_NAME_LENGTH)
    .takeIf { it.isNotBlank() }

const val MAX_NAME_LENGTH = 80
const val MAX_CHECK_IN_NOTE_LENGTH = 500
const val MAX_SOCIAL_POST_LENGTH = 500
