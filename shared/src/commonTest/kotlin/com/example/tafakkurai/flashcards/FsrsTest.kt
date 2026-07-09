package com.example.tafakkurai.flashcards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsTest {
    private val now = 1_750_000_000_000L
    private val day = 86_400_000L

    @Test
    fun newCardGoodGraduatesToReview() {
        val s = Fsrs.next(CardState.NEW, 0.0, 0.0, null, Rating.GOOD, now)
        assertEquals(CardState.REVIEW, s.state)
        assertTrue(s.dueMillis > now + day / 2, "graduated card due at least ~1 day out")
        assertTrue(s.stability > 0)
        assertTrue(s.difficulty in 1.0..10.0)
    }

    @Test
    fun newCardAgainStaysLearningWithShortStep() {
        val s = Fsrs.next(CardState.NEW, 0.0, 0.0, null, Rating.AGAIN, now)
        assertEquals(CardState.LEARNING, s.state)
        assertTrue(s.dueMillis - now <= 10 * 60_000)
    }

    @Test
    fun reviewAgainLapsesToRelearning() {
        val s = Fsrs.next(CardState.REVIEW, 10.0, 5.0, now - 10 * day, Rating.AGAIN, now)
        assertEquals(CardState.RELEARNING, s.state)
        assertTrue(s.stability < 10.0, "stability drops on lapse")
    }

    @Test
    fun easyYieldsLongerIntervalThanHard() {
        val hard = Fsrs.next(CardState.REVIEW, 10.0, 5.0, now - 10 * day, Rating.HARD, now)
        val good = Fsrs.next(CardState.REVIEW, 10.0, 5.0, now - 10 * day, Rating.GOOD, now)
        val easy = Fsrs.next(CardState.REVIEW, 10.0, 5.0, now - 10 * day, Rating.EASY, now)
        assertTrue(hard.dueMillis < good.dueMillis)
        assertTrue(good.dueMillis < easy.dueMillis)
    }

    @Test
    fun repeatedGoodGrowsInterval() {
        var state = CardState.NEW
        var stability = 0.0
        var difficulty = 0.0
        var last: Long? = null
        var t = now
        var prevIvl = 0L
        repeat(5) {
            val s = Fsrs.next(state, stability, difficulty, last, Rating.GOOD, t)
            val ivl = s.dueMillis - t
            assertTrue(ivl >= prevIvl, "interval should not shrink on Good")
            prevIvl = ivl
            state = s.state; stability = s.stability; difficulty = s.difficulty
            last = t; t = s.dueMillis
        }
    }
}
