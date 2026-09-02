package com.example.tafakkurai.flashcards

import com.example.tafakkurai.db.Deck
import com.example.tafakkurai.db.Flashcard
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** Which cards a study session draws from. */
enum class ReviewFilter { DUE, ALL, NEW, HARD, STARRED }

/** Front→back, back→front, or a random mix per card. */
enum class ReviewDirection { FRONT_BACK, BACK_FRONT, MIXED }

data class DeckStats(val total: Long, val newCount: Long, val dueCount: Long, val hardCount: Long)

data class VocabularyImportResult(
    val deckId: String,
    val added: Int,
    val skipped: Int,
)

/** Feature-owned input model; callers map their parsed source into this boundary. */
data class FlashcardImportDraft(
    val front: String,
    val back: String,
    val theme: Int,
)

/** Local-first flashcard persistence + FSRS grading on top of SQLDelight. */
class FlashcardStore(
    private val db: TafakkurDb,
    private val now: () -> Long = ::epochMillis,
    private val activity: ActivityLog? = null,
) {
    private val q get() = db.flashcardsQueries

    private fun newId() = buildString {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        append("fc-")
        repeat(16) { append(chars[Random.nextInt(chars.length)]) }
    }

    // decks ------------------------------------------------------------
    fun decks(subjectId: String): List<Deck> = q.decksBySubject(subjectId).executeAsList()

    fun createDeck(subjectId: String, name: String): String {
        val id = newId()
        db.transaction {
            q.insertDeck(id, subjectId, name, now())
            recordActivityOrThrow("deck_created", id) {
                put("deck_id", id)
                put("subject_id", subjectId)
            }
        }
        return id
    }

    fun renameDeck(id: String, name: String) {
        db.transaction {
            q.renameDeck(name, id)
            recordActivityOrThrow("deck_renamed", id) { put("deck_id", id) }
        }
    }

    fun deleteDeck(id: String) {
        db.transaction {
            q.deleteDeck(id)
            recordActivityOrThrow("deck_deleted", id) { put("deck_id", id) }
        }
    }

    fun stats(deckId: String): DeckStats {
        val row = q.deckCounts(now(), deckId).executeAsOne()
        return DeckStats(row.total, row.new ?: 0, row.due ?: 0, row.hard ?: 0)
    }

    // cards ------------------------------------------------------------
    fun cards(deckId: String): List<Flashcard> = q.cardsByDeck(deckId).executeAsList()

    fun addCard(deckId: String, front: String, back: String, tags: String = ""): String {
        val id = newId()
        db.transaction {
            q.insertCard(id, deckId, front, back, tags, now())
            recordActivityOrThrow("card_created", id) {
                put("card_id", id)
                put("deck_id", deckId)
            }
        }
        return id
    }

    /**
     * Imports an exact, previewed set in one transaction. Existing fronts are skipped,
     * so confirming the same proposal twice cannot duplicate a deck's vocabulary.
     */
    fun importableVocabularyCount(
        subjectId: String,
        deckName: String,
        drafts: List<FlashcardImportDraft>,
    ): Int {
        val safeDeckName = deckName.trim().take(160)
        if (safeDeckName.isBlank() || drafts.isEmpty()) return 0
        val deckId = decks(subjectId)
            .firstOrNull { it.name.equals(safeDeckName, ignoreCase = true) }
            ?.id
        return importableDrafts(deckId, drafts).size
    }

    fun importVocabulary(
        subjectId: String,
        deckName: String,
        sourceTitle: String,
        drafts: List<FlashcardImportDraft>,
        actionSource: String = "ai",
    ): VocabularyImportResult? {
        val safeDeckName = deckName.trim().take(160)
        if (safeDeckName.isBlank() || drafts.isEmpty()) return null
        val existingDeck = decks(subjectId).firstOrNull { it.name.equals(safeDeckName, ignoreCase = true) }
        val deckId = existingDeck?.id ?: newId()
        val unique = importableDrafts(existingDeck?.id, drafts)
        if (unique.isEmpty()) return VocabularyImportResult(deckId, 0, drafts.size)
        val stamp = now()
        val sourceTag = sourceTitle.lowercase().replace(TAG_UNSAFE, "-").trim('-').take(60)
        val persisted = runCatching {
            db.transaction {
                if (existingDeck == null) q.insertDeck(deckId, subjectId, safeDeckName, stamp)
                unique.forEach { draft ->
                    q.insertCard(
                        newId(),
                        deckId,
                        draft.front,
                        draft.back,
                        "source:$sourceTag,theme:${draft.theme}",
                        stamp,
                    )
                }
                val recorded = activity?.record(
                    feature = "flashcards",
                    type = "cards_imported",
                    entityId = deckId,
                    source = actionSource,
                    properties = buildJsonObject {
                        put("deck_id", deckId)
                        put("subject_id", subjectId)
                        put("source_title", sourceTitle.take(200))
                        put("from_theme", unique.minOf { it.theme })
                        put("to_theme", unique.maxOf { it.theme })
                        put("count", unique.size)
                    },
                ) ?: true
                check(recorded) { "Could not append flashcard import ActivityEvent" }
            }
        }.isSuccess
        if (!persisted) return null
        return VocabularyImportResult(deckId, unique.size, drafts.size - unique.size)
    }

    private fun importableDrafts(
        existingDeckId: String?,
        drafts: List<FlashcardImportDraft>,
    ): List<FlashcardImportDraft> {
        val existingFronts = existingDeckId?.let { id ->
            cards(id).mapTo(hashSetOf()) { normalizeFront(it.front) }
        } ?: hashSetOf()
        return deduplicateImportDrafts(existingFronts, drafts, MAX_BULK_CARDS)
    }

    fun updateCard(id: String, front: String, back: String, tags: String) {
        db.transaction {
            q.updateCardContent(front, back, tags, id)
            recordActivityOrThrow("card_updated", id) { put("card_id", id) }
        }
    }

    fun setStarred(id: String, starred: Boolean) {
        db.transaction {
            q.setStarred(if (starred) 1L else 0L, id)
            recordActivityOrThrow("card_starred_changed", id) {
                put("card_id", id)
                put("starred", starred)
            }
        }
    }

    fun setIgnored(id: String, ignored: Boolean) {
        db.transaction {
            q.setIgnored(if (ignored) 1L else 0L, id)
            recordActivityOrThrow("card_ignored_changed", id) {
                put("card_id", id)
                put("ignored", ignored)
            }
        }
    }

    fun deleteCard(id: String) {
        db.transaction {
            q.deleteCard(id)
            recordActivityOrThrow("card_deleted", id) { put("card_id", id) }
        }
    }

    // review -----------------------------------------------------------
    /** Cards for a session, hardest/most-overdue first for DUE, created order otherwise. */
    fun queue(deckId: String, filter: ReviewFilter, shuffle: Boolean = false): List<Flashcard> {
        val t = now()
        val all = cards(deckId).filter { it.ignored == 0L }
        val picked = when (filter) {
            ReviewFilter.ALL -> all
            ReviewFilter.NEW -> all.filter { it.state == 0L }
            ReviewFilter.HARD -> all.filter { it.lastRating == 2L || it.lapses > 0 }
            ReviewFilter.STARRED -> all.filter { it.starred == 1L }
            ReviewFilter.DUE -> all.filter { it.due <= t }.sortedBy { it.due }
        }
        return if (shuffle) picked.shuffled() else picked
    }

    /** Apply an Anki-style grade; returns the new schedule. */
    fun grade(card: Flashcard, rating: Rating): Schedule {
        val t = now()
        val sched = Fsrs.next(
            state = CardState.of(card.state.toInt()),
            stability = card.stability,
            difficulty = card.difficulty,
            lastReviewMillis = card.lastReview,
            rating = rating,
            nowMillis = t,
        )
        db.transaction {
            q.updateSchedule(
                state = sched.state.value.toLong(),
                stability = sched.stability,
                difficulty = sched.difficulty,
                due = sched.dueMillis,
                lastReview = t,
                lapseInc = if (rating == Rating.AGAIN && card.state == 2L) 1L else 0L,
                lastRating = rating.value.toLong(),
                id = card.id,
            )
            val elapsed = card.lastReview?.let { (t - it).toDouble() / 86_400_000 } ?: 0.0
            q.insertLog(newId(), card.id, rating.value.toLong(), t, elapsed, sched.intervalDays)
            recordActivityOrThrow("card_reviewed", card.id) {
                put("card_id", card.id)
                put("rating", rating.value)
                put("due", sched.dueMillis)
            }
        }
        return sched
    }

    fun previewLabel(card: Flashcard, rating: Rating): String = Fsrs.previewLabel(
        CardState.of(card.state.toInt()), card.stability, card.difficulty,
        card.lastReview, rating, now(),
    )

    fun reviewsToday(): Long {
        val t = now()
        return q.logCountSince(t - t % 86_400_000).executeAsOne()
    }

    private fun recordActivityOrThrow(
        type: String,
        entityId: String,
        properties: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) {
        check(
            activity?.record(
                feature = "flashcards",
                type = type,
                entityId = entityId,
                properties = buildJsonObject(properties),
            ) ?: true,
        ) { "Could not append flashcard ActivityEvent" }
    }

    private companion object {
        const val MAX_BULK_CARDS = 2_000
        val TAG_UNSAFE = Regex("[^a-z0-9]+")
    }
}

private fun normalizeFront(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

/** Pure import boundary used by previews and covered without a database driver. */
internal fun deduplicateImportDrafts(
    existingFronts: Set<String>,
    drafts: List<FlashcardImportDraft>,
    limit: Int = 2_000,
): List<FlashcardImportDraft> {
    val existing = existingFronts.mapTo(hashSetOf(), ::normalizeFront)
    val unique = linkedMapOf<String, FlashcardImportDraft>()
    drafts.take(limit.coerceAtLeast(0)).forEach { draft ->
        val front = draft.front.trim().take(160)
        val back = draft.back.trim().take(600)
        val key = normalizeFront(front)
        if (front.isNotBlank() && back.isNotBlank() && key !in existing) {
            if (key !in unique) unique[key] = draft.copy(front = front, back = back)
        }
    }
    return unique.values.toList()
}
