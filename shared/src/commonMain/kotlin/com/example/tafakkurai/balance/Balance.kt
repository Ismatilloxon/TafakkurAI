package com.example.tafakkurai.balance

/**
 * The balance radar, computed from what the user actually did.
 *
 * Two rules shape this file:
 *
 *  1. Every number traces back to a recorded action — a prayer marked, a dhikr counted, an
 *     ayah read, a gratitude entry written. Nothing is assumed or averaged in from
 *     defaults, because a radar that moves on its own tells the user nothing.
 *  2. A dimension the app does not track yet reports `null`, not a plausible-looking score.
 *     Physical and Financial have no data source in the app today, so they read "not
 *     tracked" rather than inventing a number the user cannot influence.
 */

/** One axis of the radar. A null [value] means "nothing tracked for this yet". */
data class BalanceScore(
    val key: String,
    val label: String,
    val value: Float?,
    /** Plain-language reason for the number, shown under the legend. */
    val detail: String,
) {
    val percent: Int? get() = value?.let { (it * 100).toInt() }
    val isTracked: Boolean get() = value != null
}

/**
 * Everything the calculator reads, gathered from the stores by [BalanceCalculator].
 *
 * A data class rather than the stores themselves so the arithmetic is testable without a
 * database, Compose, or Koin.
 */
data class BalanceInput(
    /** Prayers marked today, out of five. Jamaat counts for more than alone. */
    val prayersOnTime: Int = 0,
    val prayersInJamaat: Int = 0,
    /** Today's dhikr count against the user's own target, already 0..1. */
    val dhikrProgress: Float = 0f,
    val dhikrStreak: Int = 0,
    /** Morning + evening adhkar completion today, each 0..1. */
    val adhkarProgress: Float = 0f,
    /** Ayahs read in total. Cumulative — the Qurʼan store keeps no per-day history. */
    val ayahsRead: Int = 0,
    /** Distinct surahs opened far enough to record a read ayah. */
    val surahsStarted: Int = 0,
    /** Days written in over the last week, 0..7, and the current gratitude streak. */
    val gratitudeDaysThisWeek: Int = 0,
    val gratitudeStreak: Int = 0,
    /** Flashcards waiting for review and cards already learned. */
    val cardsLearned: Int = 0,
    val cardsDue: Int = 0,
)

/**
 * Turns raw activity into the five axes.
 *
 * Each dimension is a weighted blend of its inputs, and every input saturates: reading 400
 * ayahs is not four times "more educational" than 100, so the curve flattens rather than
 * letting one heavy day pin an axis at 100 forever.
 */
fun computeBalance(input: BalanceInput): List<BalanceScore> {
    val prayer = prayerScore(input.prayersOnTime, input.prayersInJamaat)
    val quran = saturate(input.ayahsRead, full = QURAN_AYAH_TARGET)
    val breadth = saturate(input.surahsStarted, full = QURAN_SURAH_TARGET)
    val gratitude = (input.gratitudeDaysThisWeek / 7f).coerceIn(0f, 1f)
    val cards = saturate(input.cardsLearned, full = CARD_TARGET)

    val spiritual = weighted(
        prayer to 0.45f,
        input.dhikrProgress.coerceIn(0f, 1f) to 0.20f,
        input.adhkarProgress.coerceIn(0f, 1f) to 0.20f,
        quran to 0.15f,
    )
    val educational = weighted(
        quran to 0.35f,
        breadth to 0.25f,
        cards to 0.40f,
    )
    // Gratitude is the only relational practice the app records — who you are thankful for,
    // written down day after day. It is a partial signal and the detail line says so.
    val social = weighted(
        gratitude to 0.7f,
        saturate(input.gratitudeStreak, full = STREAK_TARGET) to 0.3f,
    )

    return listOf(
        BalanceScore(
            "physical", "Physical", null,
            "Not tracked yet — no activity, sleep or hydration data recorded.",
        ),
        BalanceScore(
            "social", "Social", social,
            gratitudeDetail(input),
        ),
        BalanceScore(
            "financial", "Financial", null,
            "Not tracked yet — no income, spending or sadaqah recorded.",
        ),
        BalanceScore(
            "educational", "Educational", educational,
            educationalDetail(input),
        ),
        BalanceScore(
            "spiritual", "Spiritual", spiritual,
            spiritualDetail(input),
        ),
    )
}

/** A prayer in jamaat counts fully; alone but on time counts for most of it. */
private fun prayerScore(onTime: Int, inJamaat: Int): Float {
    val counted = (inJamaat.coerceIn(0, 5) * 1.0f) + (onTime.coerceIn(0, 5) * 0.8f)
    return (counted / 5f).coerceIn(0f, 1f)
}

/**
 * Diminishing returns: half of [full] already reads about 71, and going past [full] cannot
 * push an axis above 100.
 */
private fun saturate(count: Int, full: Int): Float {
    if (count <= 0 || full <= 0) return 0f
    val ratio = (count.toFloat() / full).coerceIn(0f, 1f)
    return kotlin.math.sqrt(ratio)
}

private fun weighted(vararg parts: Pair<Float, Float>): Float {
    val total = parts.sumOf { it.second.toDouble() }.toFloat()
    if (total <= 0f) return 0f
    return (parts.sumOf { (value, weight) -> (value * weight).toDouble() }.toFloat() / total)
        .coerceIn(0f, 1f)
}

private fun spiritualDetail(input: BalanceInput): String {
    val prayers = input.prayersOnTime + input.prayersInJamaat
    return "$prayers of 5 prayers marked today · dhikr ${(input.dhikrProgress * 100).toInt()}%" +
        " · adhkar ${(input.adhkarProgress * 100).toInt()}% · ${input.ayahsRead} ayahs read"
}

private fun educationalDetail(input: BalanceInput): String =
    "${input.ayahsRead} ayahs across ${input.surahsStarted} surahs · ${input.cardsLearned} cards learned" +
        if (input.cardsDue > 0) " · ${input.cardsDue} due" else ""

private fun gratitudeDetail(input: BalanceInput): String = when {
    input.gratitudeDaysThisWeek == 0 -> "From your gratitude journal — nothing written this week."
    else -> "${input.gratitudeDaysThisWeek} of the last 7 days journalled" +
        if (input.gratitudeStreak > 1) " · ${input.gratitudeStreak}-day streak" else ""
}

/** Reading this many ayahs reads as a full Qurʼan dimension. */
private const val QURAN_AYAH_TARGET = 300

private const val QURAN_SURAH_TARGET = 10

private const val CARD_TARGET = 60

private const val STREAK_TARGET = 14
