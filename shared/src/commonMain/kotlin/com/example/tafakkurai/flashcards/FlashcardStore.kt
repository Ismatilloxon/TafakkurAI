package com.example.tafakkurai.flashcards

import com.example.tafakkurai.db.Deck
import com.example.tafakkurai.db.Flashcard
import com.example.tafakkurai.db.TafakkurDb
import kotlin.random.Random

/** Which cards a study session draws from. */
enum class ReviewFilter { DUE, ALL, NEW, HARD, STARRED }

/** Front→back, back→front, or a random mix per card. */
enum class ReviewDirection { FRONT_BACK, BACK_FRONT, MIXED }

data class DeckStats(val total: Long, val newCount: Long, val dueCount: Long, val hardCount: Long)

/** Local-first flashcard persistence + FSRS grading on top of SQLDelight. */
class FlashcardStore(private val db: TafakkurDb, private val now: () -> Long = ::epochMillis) {
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
        q.insertDeck(id, subjectId, name, now())
        return id
    }

    fun renameDeck(id: String, name: String) = q.renameDeck(name, id)
    fun deleteDeck(id: String) = q.deleteDeck(id)

    fun stats(deckId: String): DeckStats {
        val row = q.deckCounts(now(), deckId).executeAsOne()
        return DeckStats(row.total, row.new ?: 0, row.due ?: 0, row.hard ?: 0)
    }

    // cards ------------------------------------------------------------
    fun cards(deckId: String): List<Flashcard> = q.cardsByDeck(deckId).executeAsList()

    fun addCard(deckId: String, front: String, back: String, tags: String = ""): String {
        val id = newId()
        q.insertCard(id, deckId, front, back, tags, now())
        return id
    }

    fun updateCard(id: String, front: String, back: String, tags: String) =
        q.updateCardContent(front, back, tags, id)

    fun setStarred(id: String, starred: Boolean) = q.setStarred(if (starred) 1L else 0L, id)
    fun setIgnored(id: String, ignored: Boolean) = q.setIgnored(if (ignored) 1L else 0L, id)
    fun deleteCard(id: String) = q.deleteCard(id)

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
}
