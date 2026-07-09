package com.example.tafakkurai.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.tafakkurai.db.Deck
import com.example.tafakkurai.db.Flashcard
import com.example.tafakkurai.flashcards.DeckStats
import com.example.tafakkurai.flashcards.FlashcardStore
import com.example.tafakkurai.flashcards.Rating
import com.example.tafakkurai.flashcards.ReviewDirection
import com.example.tafakkurai.flashcards.ReviewFilter
import kotlin.random.Random

/** One graded card in the session, for the end-of-session summary. */
data class SessionEntry(val card: Flashcard, val rating: Rating)

/** Drives deck list, card editing, and an Anki-style FSRS review session. */
class FlashcardsViewModel(private val store: FlashcardStore) : ViewModel() {

    var decks by mutableStateOf<List<Pair<Deck, DeckStats>>>(emptyList())
        private set
    var cards by mutableStateOf<List<Flashcard>>(emptyList())
        private set

    // review session
    var queue by mutableStateOf<List<Flashcard>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    var revealed by mutableStateOf(false)
    var flippedCard by mutableStateOf(false) // back shown as question (BACK_FRONT / MIXED)
        private set
    var history by mutableStateOf<List<SessionEntry>>(emptyList())
        private set
    private var direction = ReviewDirection.FRONT_BACK

    val current: Flashcard? get() = queue.getOrNull(index)
    val finished: Boolean get() = queue.isNotEmpty() && index >= queue.size

    fun loadDecks(subjectId: String) {
        decks = store.decks(subjectId).map { it to store.stats(it.id) }
    }

    fun createDeck(subjectId: String, name: String) {
        if (name.isBlank()) return
        store.createDeck(subjectId, name.trim())
        loadDecks(subjectId)
    }

    fun deleteDeck(subjectId: String, deckId: String) {
        store.deleteDeck(deckId)
        loadDecks(subjectId)
    }

    fun loadCards(deckId: String) { cards = store.cards(deckId) }

    fun addCard(deckId: String, front: String, back: String, tags: String) {
        if (front.isBlank() || back.isBlank()) return
        store.addCard(deckId, front.trim(), back.trim(), tags.trim())
        loadCards(deckId)
    }

    fun updateCard(deckId: String, id: String, front: String, back: String, tags: String) {
        store.updateCard(id, front.trim(), back.trim(), tags.trim())
        loadCards(deckId)
    }

    fun toggleStar(deckId: String, card: Flashcard) {
        store.setStarred(card.id, card.starred == 0L)
        loadCards(deckId)
    }

    fun toggleIgnore(deckId: String, card: Flashcard) {
        store.setIgnored(card.id, card.ignored == 0L)
        loadCards(deckId)
    }

    fun deleteCard(deckId: String, id: String) {
        store.deleteCard(id)
        loadCards(deckId)
    }

    // session ------------------------------------------------------------
    fun startSession(
        deckId: String,
        filter: ReviewFilter,
        direction: ReviewDirection = ReviewDirection.FRONT_BACK,
        shuffle: Boolean = false,
    ) {
        this.direction = direction
        queue = store.queue(deckId, filter, shuffle)
        index = 0
        revealed = false
        history = emptyList()
        rollFlip()
    }

    private fun rollFlip() {
        flippedCard = when (direction) {
            ReviewDirection.FRONT_BACK -> false
            ReviewDirection.BACK_FRONT -> true
            ReviewDirection.MIXED -> Random.nextBoolean()
        }
    }

    fun previewLabel(rating: Rating): String = current?.let { store.previewLabel(it, rating) } ?: ""

    fun grade(rating: Rating) {
        val card = current ?: return
        store.grade(card, rating)
        history = history + SessionEntry(card, rating)
        // Again puts the card back at the end of this session, like Anki learning steps.
        if (rating == Rating.AGAIN) queue = queue + card
        index++
        revealed = false
        rollFlip()
    }

    fun sessionCounts(): Map<Rating, Int> = history.groupingBy { it.rating }.eachCount()
}
