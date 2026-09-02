package com.example.tafakkurai.learning.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.tafakkurai.learning.domain.QuranQuizAttempt
import com.example.tafakkurai.learning.domain.QuranQuizQuestion
import com.example.tafakkurai.learning.domain.QuranQuizRepository
import com.example.tafakkurai.learning.domain.quranQuizQuestions
import com.example.tafakkurai.quran.surahOf
import com.example.tafakkurai.sync.activityUuidV4
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceTimeZoneId
import com.example.tafakkurai.sleep.SleepTime

data class QuranQuizGoalUiModel(
    val surahNumber: Int,
    val name: String,
    val arabicName: String,
    val sourceKey: String,
    val latestAttempt: QuranQuizAttempt?,
)

data class QuranQuizSessionUiState(
    val attemptId: String,
    val surahNumber: Int,
    val surahName: String,
    val arabicName: String,
    val questions: List<QuranQuizQuestion>,
    val answerIndexes: List<Int?>,
    val currentIndex: Int,
    val completionAttemptAt: Long? = null,
    val completionLocalDate: String? = null,
    val completionTimezone: String? = null,
    val completedAttempt: QuranQuizAttempt? = null,
) {
    val currentQuestion: QuranQuizQuestion? get() = questions.getOrNull(currentIndex)
    val selectedIndex: Int? get() = answerIndexes.getOrNull(currentIndex)
    val isLastQuestion: Boolean get() = currentIndex == questions.lastIndex
}

data class QuranQuizUiState(
    val query: String,
    val totalGoals: Int,
    val goals: List<QuranQuizGoalUiModel>,
    val session: QuranQuizSessionUiState?,
    val loadFailed: Boolean,
    val feedbackKey: String?,
)

sealed interface QuranQuizEvent {
    data class Search(val query: String) : QuranQuizEvent
    data class Start(val surahNumber: Int) : QuranQuizEvent
    data class Select(val choiceIndex: Int) : QuranQuizEvent
    data object Next : QuranQuizEvent
    data object RetryQuiz : QuranQuizEvent
    data object Refresh : QuranQuizEvent
}

/** One app-hoisted state holder shared by the quiz library and every exact-surah session. */
class QuranQuizViewModel(
    private val repository: QuranQuizRepository,
    private val newAttemptId: () -> String = ::activityUuidV4,
    private val now: () -> Long = ::epochMillis,
    private val localDateAt: (Long) -> String = { SleepTime.dateKeyOf(it) },
    private val timezone: () -> String = ::deviceTimeZoneId,
) : ViewModel() {
    private var query by mutableStateOf("")
    private var session by mutableStateOf<QuranQuizSessionUiState?>(null)
    private var feedbackKey by mutableStateOf<String?>(null)

    val state: QuranQuizUiState
        get() {
            repository.revision
            val needle = query.trim().lowercase()
            val goals = repository.goals.mapNotNull { goal ->
                val surah = surahOf(goal.surahNumber) ?: return@mapNotNull null
                QuranQuizGoalUiModel(
                    surahNumber = surah.number,
                    name = surah.nameEnglish,
                    arabicName = surah.nameArabic,
                    sourceKey = sourceKey(goal.source),
                    latestAttempt = repository.latestAttempts[surah.number],
                )
            }.filter { item ->
                needle.isBlank() ||
                    needle in item.name.lowercase() ||
                    needle in item.arabicName.lowercase() ||
                    needle == item.surahNumber.toString()
            }
            return QuranQuizUiState(
                query = query,
                totalGoals = repository.goals.size,
                goals = goals,
                session = session,
                loadFailed = repository.loadFailed,
                feedbackKey = feedbackKey,
            )
        }

    fun onEvent(event: QuranQuizEvent) {
        when (event) {
            is QuranQuizEvent.Search -> {
                query = event.query.take(MAX_QUERY_LENGTH)
                feedbackKey = null
            }
            is QuranQuizEvent.Start -> start(event.surahNumber)
            is QuranQuizEvent.Select -> select(event.choiceIndex)
            QuranQuizEvent.Next -> next()
            QuranQuizEvent.RetryQuiz -> session?.let { start(it.surahNumber) }
            QuranQuizEvent.Refresh -> {
                repository.refresh()
                feedbackKey = if (repository.loadFailed) "quran.quiz.load_failed" else null
            }
        }
    }

    private fun start(surahNumber: Int) {
        repository.refresh()
        if (repository.loadFailed) {
            session = null
            feedbackKey = "quran.quiz.load_failed"
            return
        }
        if (repository.goals.none { it.surahNumber == surahNumber }) {
            session = null
            feedbackKey = "quran.quiz.not_available"
            return
        }
        val surah = surahOf(surahNumber)
        val questions = quranQuizQuestions(surahNumber)
        if (surah == null || questions.isEmpty()) {
            session = null
            feedbackKey = "quran.quiz.load_failed"
            return
        }
        session = QuranQuizSessionUiState(
            attemptId = newAttemptId(),
            surahNumber = surah.number,
            surahName = surah.nameEnglish,
            arabicName = surah.nameArabic,
            questions = questions,
            answerIndexes = List(questions.size) { null },
            currentIndex = 0,
        )
        feedbackKey = null
    }

    private fun select(choiceIndex: Int) {
        val current = session ?: return
        val question = current.currentQuestion ?: return
        if (current.completedAttempt != null || current.selectedIndex != null || choiceIndex !in question.choices.indices) {
            return
        }
        session = current.copy(
            answerIndexes = current.answerIndexes.toMutableList().also {
                it[current.currentIndex] = choiceIndex
            },
        )
        feedbackKey = null
    }

    private fun next() {
        val current = session ?: return
        if (current.selectedIndex == null || current.completedAttempt != null) return
        if (!current.isLastQuestion) {
            session = current.copy(currentIndex = current.currentIndex + 1)
            return
        }
        val correct = current.questions.indices.count { index ->
            current.answerIndexes[index] == current.questions[index].answerIndex
        }
        val completionAttemptAt = current.completionAttemptAt ?: now()
        val completionLocalDate = current.completionLocalDate ?: localDateAt(completionAttemptAt)
        val completionTimezone = current.completionTimezone ?: timezone()
        val saving = current.copy(
            completionAttemptAt = completionAttemptAt,
            completionLocalDate = completionLocalDate,
            completionTimezone = completionTimezone,
        )
        session = saving
        val attempt = repository.complete(
            attemptId = saving.attemptId,
            surahNumber = saving.surahNumber,
            correct = correct,
            total = saving.questions.size,
            completedAt = completionAttemptAt,
            localDate = completionLocalDate,
            timezone = completionTimezone,
        )
        if (attempt == null) {
            feedbackKey = "quran.quiz.save_failed"
            return
        }
        session = saving.copy(completedAttempt = attempt)
        feedbackKey = null
    }

    private fun sourceKey(source: String): String {
        val parts = source.split('+').toSet()
        return when {
            "quran" in parts && "hifz" in parts -> "quran.quiz.source.both"
            "hifz" in parts -> "quran.quiz.source.hifz"
            "quran" in parts -> "quran.quiz.source.reading"
            else -> "quran.quiz.source.unknown"
        }
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 120
    }
}
