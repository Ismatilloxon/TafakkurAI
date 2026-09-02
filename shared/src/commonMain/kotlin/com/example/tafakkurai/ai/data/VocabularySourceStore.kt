package com.example.tafakkurai.ai.data

import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

data class VocabularySourceDocument(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Private local catalog of source text explicitly supplied by the user. */
class VocabularySourceStore(
    private val db: TafakkurDb,
    private val activity: ActivityLog? = null,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = ::sourceId,
) {
    private val q get() = db.vocabularySourceQueries

    fun find(title: String): VocabularySourceDocument? {
        val normalized = normalizedSourceTitle(title)
        if (normalized.isBlank()) return null
        return runCatching { q.selectByNormalizedTitle(normalized).executeAsOneOrNull() }
            .getOrNull()?.let { row ->
                VocabularySourceDocument(
                    id = row.id,
                    title = row.title,
                    content = row.content,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                )
            }
    }

    fun save(title: String, content: String): VocabularySourceDocument? {
        val safeTitle = title.trim().replace(WHITESPACE, " ").take(MAX_TITLE)
        val safeContent = content.trim()
        if (safeTitle.isBlank() || safeContent.isBlank() || safeContent.length > MAX_CONTENT_CHARS) {
            return null
        }
        val normalized = normalizedSourceTitle(safeTitle)
        val existing = find(safeTitle)
        val stamp = now()
        val document = VocabularySourceDocument(
            id = existing?.id ?: newId(),
            title = safeTitle,
            content = safeContent,
            createdAt = existing?.createdAt ?: stamp,
            updatedAt = stamp,
        )
        val saved = runCatching {
            db.transaction {
                q.upsert(
                    document.id,
                    normalized,
                    document.title,
                    document.content,
                    document.createdAt,
                    document.updatedAt,
                )
                check(
                    activity?.record(
                        feature = "learning_sources",
                        type = "vocabulary_source_saved",
                        entityId = document.id,
                        source = "user",
                        properties = buildJsonObject {
                            put("source_title", document.title)
                            put("characters", document.content.length)
                        },
                    ) ?: true,
                ) { "Could not append vocabulary-source ActivityEvent" }
            }
        }.isSuccess
        if (!saved) return null
        return document
    }

    fun delete(id: String): Boolean = runCatching { q.deleteById(id) }.isSuccess

    companion object {
        const val MAX_CONTENT_CHARS = 4 * 1024 * 1024
    }
}

fun normalizedSourceTitle(title: String): String =
    title.trim().lowercase().replace(WHITESPACE, " ").take(MAX_TITLE)

private const val MAX_TITLE = 200
private val WHITESPACE = Regex("\\s+")

private fun sourceId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "source-" + buildString { repeat(20) { append(chars[Random.nextInt(chars.length)]) } }
}
