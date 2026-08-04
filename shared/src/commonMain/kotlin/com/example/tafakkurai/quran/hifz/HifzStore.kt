package com.example.tafakkurai.quran.hifz

import com.example.tafakkurai.db.HifzCard
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.CardState
import com.example.tafakkurai.flashcards.Fsrs
import com.example.tafakkurai.flashcards.Rating
import com.example.tafakkurai.flashcards.Schedule
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.quran.ayahCountOf
import com.example.tafakkurai.sync.Outbox
import com.example.tafakkurai.sync.syncJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlin.random.Random

/** Outbox payload for one graded ayah — enough for the backend to upsert the schedule. */
@Serializable
data class HifzGradeSyncPayload(
    val surah: Int,
    val ayah: Int,
    val rating: Int,
    val at: Long,
)

/** How far a surah has been taken, counted from the cards themselves. */
data class SurahHifzProgress(
    val surahNumber: Int,
    val total: Int,
    val memorized: Int,
    val due: Int,
) {
    val fraction: Float get() = if (total <= 0) 0f else (memorized.toFloat() / total).coerceIn(0f, 1f)
    val percent: Int get() = (fraction * 100).toInt()
}

data class HifzStats(
    val total: Int = 0,
    val memorized: Int = 0,
    val due: Int = 0,
    val fresh: Int = 0,
    val reviewedToday: Int = 0,
    val streakDays: Int = 0,
) {
    val hasCards: Boolean get() = total > 0
}

/**
 * Local-first persistence for memorization, on the same footing as [FlashcardStore]:
 * SQLDelight underneath, [Fsrs] on top, and every write mirrored into the [Outbox] so a
 * session done on a plane still reaches the backend on the next app enter.
 *
 * It stores references and schedule state and nothing else. No ayah text is ever written
 * here — see `HifzCard.sq` for why that matters.
 */
class HifzStore(
    private val db: TafakkurDb,
    private val outbox: Outbox? = null,
    private val now: () -> Long = ::epochMillis,
) {
    private val q get() = db.hifzCardQueries

    private fun newId() = buildString {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        append("hz-")
        repeat(16) { append(chars[Random.nextInt(chars.length)]) }
    }

    // ── Enrolment ────────────────────────────────────────────────────────────

    /**
     * Puts a run of ayahs under memorization. Out-of-range input is clamped rather than
     * rejected: "Al-Baqarah from 250" with no end means to the end of the surah, and an
     * inverted range is read the way it was obviously meant.
     *
     * @return how many ayahs were newly enrolled.
     */
    fun addRange(surahNumber: Int, fromAyah: Int = 1, toAyah: Int = ayahCountOf(surahNumber)): Int {
        val count = ayahCountOf(surahNumber)
        if (count == 0) return 0
        val lo = minOf(fromAyah, toAyah).coerceIn(1, count)
        val hi = maxOf(fromAyah, toAyah).coerceIn(1, count)
        val before = countAll()
        val at = now()
        db.transaction {
            (lo..hi).forEach { ayah -> q.insertCard(surahNumber, ayah, at) }
        }
        return (countAll() - before).toInt()
    }

    fun remove(surahNumber: Int, ayahNumber: Int) = q.deleteCard(surahNumber, ayahNumber)

    fun removeSurah(surahNumber: Int) = q.deleteSurah(surahNumber)

    fun clear() {
        db.transaction {
            q.clearAll()
            q.clearReviews()
        }
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    fun all(): List<HifzCard> = q.selectAll().executeAsList()

    fun cardsOf(surahNumber: Int): List<HifzCard> = q.selectBySurah(surahNumber).executeAsList()

    fun cardOf(surahNumber: Int, ayahNumber: Int): HifzCard? =
        q.selectOne(surahNumber, ayahNumber).executeAsOneOrNull()

    /** Most overdue first — the ayah closest to being forgotten is the one to see. */
    fun due(limit: Int = Int.MAX_VALUE): List<HifzCard> =
        q.selectDue(now(), limit.toLong()).executeAsList()

    /** Never-reviewed cards in mushaf order, so a surah is learned front to back. */
    fun fresh(limit: Int = Int.MAX_VALUE): List<HifzCard> =
        q.selectNew(limit.toLong()).executeAsList()

    fun countAll(): Long = q.countAll().executeAsOne()

    fun stats(): HifzStats {
        val t = now()
        return HifzStats(
            total = q.countAll().executeAsOne().toInt(),
            memorized = q.countMemorized().executeAsOne().toInt(),
            due = q.countDue(t).executeAsOne().toInt(),
            fresh = q.countNew().executeAsOne().toInt(),
            reviewedToday = q.countReviewsSince(startOfDay(t)).executeAsOne().toInt(),
            streakDays = streakDays(t),
        )
    }

    fun progressBySurah(): Map<Int, SurahHifzProgress> =
        q.countsBySurah(now()).executeAsList().associate { row ->
            row.surah to SurahHifzProgress(
                surahNumber = row.surah,
                total = row.total.toInt(),
                memorized = (row.memorized ?: 0L).toInt(),
                due = (row.due ?: 0L).toInt(),
            )
        }

    // ── Grading ──────────────────────────────────────────────────────────────

    /**
     * Applies a grade and returns the new schedule.
     *
     * This is [FlashcardStore.grade] with an ayah reference where the card id was — the
     * scheduling itself is the identical call, so memorization and flashcards can never
     * drift into two different notions of "due".
     */
    fun grade(card: HifzCard, rating: Rating): Schedule {
        val t = now()
        val sched = Fsrs.next(
            state = CardState.of(card.state),
            stability = card.stability,
            difficulty = card.difficulty,
            lastReviewMillis = card.lastReview,
            rating = rating,
            nowMillis = t,
        )
        db.transaction {
            q.updateSchedule(
                state = sched.state.value,
                stability = sched.stability,
                difficulty = sched.difficulty,
                due = sched.dueMillis,
                lastReview = t,
                // A lapse is forgetting something that had been learned — not a miss
                // while still in the learning steps.
                lapseInc = if (rating == Rating.AGAIN && card.state == CardState.REVIEW.value) 1 else 0,
                lastRating = rating.value,
                surah = card.surah,
                ayah = card.ayah,
            )
            q.insertReview(newId(), card.surah, card.ayah, rating.value, t, sched.intervalDays)
        }
        // Fire-and-forget: the outbox owns retry and ordering, not this call.
        outbox?.enqueue(
            entity = ENTITY,
            op = OP_GRADE,
            payload = syncJson.encodeToString(
                HifzGradeSyncPayload(card.surah, card.ayah, rating.value, t),
            ),
        )
        return sched
    }

    /** The interval a rating would produce, for the grade buttons. */
    fun previewLabel(card: HifzCard, rating: Rating): String = Fsrs.previewLabel(
        CardState.of(card.state), card.stability, card.difficulty,
        card.lastReview, rating, now(),
    )

    // ── Internals ────────────────────────────────────────────────────────────

    private fun startOfDay(t: Long): Long = t - t % DAY_MS

    /**
     * Consecutive days with at least one review, counting back from today.
     *
     * Today not being reviewed yet does not break the streak — it is only broken once a
     * whole day has passed with nothing in it, which is what a user means by "I haven't
     * missed a day".
     */
    private fun streakDays(t: Long): Int {
        val today = t / DAY_MS
        val days = q.reviewDaysSince(0L).executeAsList().toSet()
        if (days.isEmpty()) return 0
        var cursor = if (today in days) today else today - 1
        var streak = 0
        while (cursor in days) {
            streak++
            cursor--
        }
        return streak
    }

    companion object {
        const val ENTITY: String = "hifzCard"
        const val OP_GRADE: String = "grade"
        private const val DAY_MS = 86_400_000L
    }
}
