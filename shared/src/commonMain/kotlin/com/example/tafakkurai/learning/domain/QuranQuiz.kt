package com.example.tafakkurai.learning.domain

import com.example.tafakkurai.quran.RevelationType
import com.example.tafakkurai.quran.SURAH_CATALOG
import com.example.tafakkurai.quran.Surah
import com.example.tafakkurai.quran.surahOf

data class QuranQuizGoal(
    val surahNumber: Int,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class QuranQuizAttempt(
    val id: String,
    val surahNumber: Int,
    val correct: Int,
    val total: Int,
    val completedAt: Long,
)

sealed interface QuranQuizText {
    data class Key(val key: String, val arguments: List<String> = emptyList()) : QuranQuizText
    data class Source(val value: String) : QuranQuizText
}

data class QuranQuizQuestion(
    val id: String,
    val prompt: QuranQuizText.Key,
    val choices: List<QuranQuizText>,
    val answerIndex: Int,
    val explanation: QuranQuizText.Key,
)

interface QuranQuizRepository {
    val revision: Int
    val goals: List<QuranQuizGoal>
    val latestAttempts: Map<Int, QuranQuizAttempt>
    val loadFailed: Boolean
    fun refresh()
    fun ensureAvailable(surahNumber: Int, source: String): QuranQuizGoal?
    fun latestAttempt(surahNumber: Int): QuranQuizAttempt? = latestAttempts[surahNumber]
    fun complete(
        attemptId: String,
        surahNumber: Int,
        correct: Int,
        total: Int,
        completedAt: Long,
        localDate: String,
        timezone: String,
    ): QuranQuizAttempt?
}

/**
 * Creates an exact-surah review from canonical mushaf index facts only. It never invents tafsir,
 * rulings or verse meanings. Every question carries a stable source-backed explanation.
 */
fun quranQuizQuestions(surahNumber: Int): List<QuranQuizQuestion> {
    val surah = surahOf(surahNumber) ?: return emptyList()
    val nearby = distractorSurahs(surah)
    return listOf(
        question(
            id = "number",
            surah = surah,
            promptKey = "quran.quiz.question.number",
            promptArgs = listOf(surah.nameEnglish),
            answer = surah.number.toString(),
            distractors = nearby.map { it.number.toString() },
            explanationKey = "quran.quiz.explain.number",
            explanationArgs = listOf(surah.nameEnglish, surah.number.toString()),
        ),
        question(
            id = "ayah_count",
            surah = surah,
            promptKey = "quran.quiz.question.ayah_count",
            promptArgs = listOf(surah.nameEnglish),
            answer = surah.ayahCount.toString(),
            distractors = ayahCountDistractors(surah.ayahCount),
            explanationKey = "quran.quiz.explain.ayah_count",
            explanationArgs = listOf(surah.nameEnglish, surah.ayahCount.toString()),
        ),
        binaryQuestion(surah),
        question(
            id = "arabic_name",
            surah = surah,
            promptKey = "quran.quiz.question.arabic_name",
            promptArgs = listOf(surah.nameEnglish),
            answer = surah.nameArabic,
            distractors = nearby.map { it.nameArabic },
            explanationKey = "quran.quiz.explain.arabic_name",
            explanationArgs = listOf(surah.nameEnglish, surah.nameArabic),
        ),
        question(
            id = "name",
            surah = surah,
            promptKey = "quran.quiz.question.name",
            promptArgs = listOf(surah.number.toString()),
            answer = surah.nameEnglish,
            distractors = nearby.map { it.nameEnglish },
            explanationKey = "quran.quiz.explain.name",
            explanationArgs = listOf(surah.number.toString(), surah.nameEnglish),
        ),
    )
}

private fun binaryQuestion(surah: Surah): QuranQuizQuestion {
    val answerKey = if (surah.revelationType == RevelationType.MECCAN) {
        "quran.surah.meccan"
    } else {
        "quran.surah.medinan"
    }
    val otherKey = if (answerKey == "quran.surah.meccan") "quran.surah.medinan" else "quran.surah.meccan"
    val choices = if (surah.number % 2 == 0) listOf(otherKey, answerKey) else listOf(answerKey, otherKey)
    return QuranQuizQuestion(
        id = "revelation",
        prompt = QuranQuizText.Key("quran.quiz.question.revelation", listOf(surah.nameEnglish)),
        choices = choices.map { QuranQuizText.Key(it) },
        answerIndex = choices.indexOf(answerKey),
        explanation = QuranQuizText.Key(
            "quran.quiz.explain.revelation",
            listOf(surah.nameEnglish, answerKey),
        ),
    )
}

private fun question(
    id: String,
    surah: Surah,
    promptKey: String,
    promptArgs: List<String>,
    answer: String,
    distractors: List<String>,
    explanationKey: String,
    explanationArgs: List<String>,
): QuranQuizQuestion {
    val raw = (listOf(answer) + distractors).distinct().take(4)
    val shift = (surah.number + id.hashCode().ushr(1)) % raw.size
    val choices = raw.drop(shift) + raw.take(shift)
    return QuranQuizQuestion(
        id = id,
        prompt = QuranQuizText.Key(promptKey, promptArgs),
        choices = choices.map { QuranQuizText.Source(it) },
        answerIndex = choices.indexOf(answer),
        explanation = QuranQuizText.Key(explanationKey, explanationArgs),
    )
}

private fun distractorSurahs(surah: Surah): List<Surah> {
    val offsets = listOf(1, -1, 7, -7, 19, -19)
    return offsets.asSequence()
        .map { offset ->
            val zeroBased = surah.number - 1 + offset
            (((zeroBased % SURAH_CATALOG.size) + SURAH_CATALOG.size) % SURAH_CATALOG.size) + 1
        }
        .mapNotNull(::surahOf)
        .filter { it.number != surah.number }
        .distinctBy { it.number }
        .take(3)
        .toList()
}

private fun ayahCountDistractors(answer: Int): List<String> {
    val offsets = listOf(1, -1, 2, -2, 5, -5, 10)
    return offsets.asSequence()
        .map { (answer + it).coerceAtLeast(1) }
        .filter { it != answer }
        .distinct()
        .take(3)
        .map(Int::toString)
        .toList()
}
