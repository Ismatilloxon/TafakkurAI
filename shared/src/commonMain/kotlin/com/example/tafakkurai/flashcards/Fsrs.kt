package com.example.tafakkurai.flashcards

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/** Anki-style grading buttons. */
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

enum class CardState(val value: Int) { NEW(0), LEARNING(1), REVIEW(2), RELEARNING(3);
    companion object { fun of(v: Int) = entries.first { it.value == v } }
}

data class Schedule(
    val state: CardState,
    val stability: Double,
    val difficulty: Double,
    val dueMillis: Long,
    val intervalDays: Double,
)

/**
 * FSRS-4.5 (Free Spaced Repetition Scheduler) with short learning steps for
 * new/lapsed cards, targeting 90% retention.
 */
object Fsrs {
    private val W = doubleArrayOf(
        0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031,
        1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
    )
    private const val REQUEST_RETENTION = 0.9
    private const val MAX_INTERVAL_DAYS = 36500.0
    private const val DAY_MS = 86_400_000L

    private fun initStability(r: Rating) = max(W[r.value - 1], 0.1)

    private fun initDifficulty(r: Rating) =
        (W[4] - (r.value - 3) * W[5]).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, r: Rating): Double {
        val next = d - W[6] * (r.value - 3)
        return (W[7] * initDifficulty(Rating.GOOD) + (1 - W[7]) * next).coerceIn(1.0, 10.0)
    }

    private fun retrievability(elapsedDays: Double, stability: Double) =
        (1 + elapsedDays / (9 * stability)).pow(-1.0)

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) W[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) W[16] else 1.0
        return s * (exp(W[8]) * (11 - d) * s.pow(-W[9]) *
            (exp(W[10] * (1 - r)) - 1) * hardPenalty * easyBonus + 1)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double =
        W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp(W[14] * (1 - r))

    private fun intervalDays(stability: Double): Double =
        (9 * stability * (1 / REQUEST_RETENTION - 1)).coerceIn(1.0, MAX_INTERVAL_DAYS)

    /**
     * Compute the next schedule for a card.
     * @param lastReviewMillis null for cards never reviewed.
     */
    fun next(
        state: CardState,
        stability: Double,
        difficulty: Double,
        lastReviewMillis: Long?,
        rating: Rating,
        nowMillis: Long,
    ): Schedule {
        val elapsedDays =
            if (lastReviewMillis == null) 0.0
            else max(0.0, (nowMillis - lastReviewMillis).toDouble() / DAY_MS)

        return when (state) {
            CardState.NEW, CardState.LEARNING, CardState.RELEARNING -> {
                val s = if (state == CardState.NEW) initStability(rating) else stability
                val d = if (state == CardState.NEW) initDifficulty(rating) else nextDifficulty(difficulty, rating)
                when (rating) {
                    Rating.AGAIN -> learn(s, d, 1, nowMillis)          // 1 min
                    Rating.HARD -> learn(s, d, 6, nowMillis)           // 6 min
                    Rating.GOOD -> graduate(s, d, nowMillis)
                    Rating.EASY -> graduate(s * 1.3, d, nowMillis)
                }
            }
            CardState.REVIEW -> {
                val r = retrievability(elapsedDays, max(stability, 0.1))
                val d = nextDifficulty(difficulty, rating)
                if (rating == Rating.AGAIN) {
                    val s = nextForgetStability(difficulty, max(stability, 0.1), r)
                    Schedule(CardState.RELEARNING, max(s, 0.1), d, nowMillis + 5 * 60_000, 0.0)
                } else {
                    val s = nextRecallStability(difficulty, max(stability, 0.1), r, rating)
                    val ivl = intervalDays(s).let { if (rating == Rating.HARD) max(1.0, it * 0.8) else it }
                    Schedule(CardState.REVIEW, s, d, nowMillis + (ivl * DAY_MS).roundToLong(), ivl)
                }
            }
        }
    }

    private fun learn(s: Double, d: Double, minutes: Int, now: Long) =
        Schedule(CardState.LEARNING, s, d, now + minutes * 60_000L, 0.0)

    private fun graduate(s: Double, d: Double, now: Long): Schedule {
        val ivl = intervalDays(max(s, 0.1))
        return Schedule(CardState.REVIEW, max(s, 0.1), d, now + (ivl * DAY_MS).roundToLong(), ivl)
    }

    /** Human label of the interval a rating would produce — shown on grade buttons like Anki. */
    fun previewLabel(
        state: CardState, stability: Double, difficulty: Double,
        lastReviewMillis: Long?, rating: Rating, nowMillis: Long,
    ): String {
        val sched = next(state, stability, difficulty, lastReviewMillis, rating, nowMillis)
        val ms = sched.dueMillis - nowMillis
        return when {
            ms < 60 * 60_000 -> "${max(1, ms / 60_000)}m"
            ms < DAY_MS -> "${ms / 3_600_000}h"
            ms < 30 * DAY_MS -> "${(ms.toDouble() / DAY_MS).roundToLong()}d"
            ms < 365 * DAY_MS -> "${(ms.toDouble() / (30 * DAY_MS)).roundToLong()}mo"
            else -> "${(ms.toDouble() / (365 * DAY_MS)).roundToLong()}y"
        }
    }
}
