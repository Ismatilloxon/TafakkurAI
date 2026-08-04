package com.example.tafakkurai.quran.hifz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.HifzCard
import com.example.tafakkurai.spiritual.PrefPersistence

/**
 * One day's work.
 *
 * [review] comes before [fresh] in [queue] and that ordering is the whole point: taking on
 * new ayahs while yesterday's are slipping is how people end up with a large surah they can
 * no longer recite. Consolidation first, intake second.
 */
data class HifzPlan(
    val review: List<HifzCard> = emptyList(),
    val fresh: List<HifzCard> = emptyList(),
) {
    val queue: List<HifzCard> get() = review + fresh
    val size: Int get() = review.size + fresh.size
    val isEmpty: Boolean get() = size == 0
}

/**
 * Turns the card table into today's session, under two limits the user sets.
 *
 * Both limits exist to protect the same thing — that memorization is a daily practice, not
 * a sprint. A cap on new ayahs keeps the review load from compounding faster than it can be
 * carried; a cap on reviews keeps a backlog after a missed week from presenting a wall of
 * three hundred cards that nobody opens.
 */
class HifzPlanner(
    private val store: HifzStore,
    private val prefs: PrefPersistence,
) {
    private var dailyNewPref by mutableStateOf(DEFAULT_DAILY_NEW)
    private var reviewCapPref by mutableStateOf(DEFAULT_REVIEW_CAP)
    private var repeatPref by mutableStateOf(DEFAULT_REPEAT)
    private var autoPlayPref by mutableStateOf(true)

    init { reload() }

    fun reload() {
        dailyNewPref = prefs.get(KEY_DAILY_NEW)?.toIntOrNull()?.coerceIn(1, 20) ?: DEFAULT_DAILY_NEW
        reviewCapPref = prefs.get(KEY_REVIEW_CAP)?.toIntOrNull()?.coerceIn(5, 200) ?: DEFAULT_REVIEW_CAP
        repeatPref = prefs.get(KEY_REPEAT)?.toIntOrNull()?.coerceIn(1, 10) ?: DEFAULT_REPEAT
        // Absent means never toggled — recitation plays on reveal by default.
        autoPlayPref = prefs.get(KEY_AUTOPLAY) != "0"
    }

    /** New ayahs to take on per day. */
    val dailyNew: Int get() = dailyNewPref

    /** Ceiling on due cards in one session, so a backlog stays openable. */
    val reviewCap: Int get() = reviewCapPref

    /** How many times an ayah's recitation loops before the grade buttons are useful. */
    val repeatCount: Int get() = repeatPref

    /** Whether revealing an ayah starts its recitation. */
    val autoPlay: Boolean get() = autoPlayPref

    fun setDailyNew(value: Int) {
        dailyNewPref = value.coerceIn(1, 20)
        prefs.put(KEY_DAILY_NEW, dailyNewPref.toString())
    }

    fun setReviewCap(value: Int) {
        reviewCapPref = value.coerceIn(5, 200)
        prefs.put(KEY_REVIEW_CAP, reviewCapPref.toString())
    }

    fun setRepeatCount(value: Int) {
        repeatPref = value.coerceIn(1, 10)
        prefs.put(KEY_REPEAT, repeatPref.toString())
    }

    fun setAutoPlay(on: Boolean) {
        autoPlayPref = on
        prefs.put(KEY_AUTOPLAY, if (on) "1" else "0")
    }

    /**
     * Builds today's session.
     *
     * New ayahs are only offered once the review load is under control: if today already
     * carries a full cap of due cards, intake waits for a lighter day rather than adding to
     * a queue that is already at its limit.
     */
    fun plan(): HifzPlan {
        val review = store.due(reviewCapPref)
        val room = if (review.size >= reviewCapPref) 0 else dailyNewPref
        return HifzPlan(review = review, fresh = if (room == 0) emptyList() else store.fresh(room))
    }

    companion object {
        const val KEY_DAILY_NEW: String = "hifz.dailyNew"
        const val KEY_REVIEW_CAP: String = "hifz.reviewCap"
        const val KEY_REPEAT: String = "hifz.repeat"
        const val KEY_AUTOPLAY: String = "hifz.autoPlay"

        /** Two ayahs a day finishes the mushaf in about eight years — a pace people keep. */
        const val DEFAULT_DAILY_NEW: Int = 2
        const val DEFAULT_REVIEW_CAP: Int = 40
        const val DEFAULT_REPEAT: Int = 3
    }
}
