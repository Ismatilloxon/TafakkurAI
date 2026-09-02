package com.example.tafakkurai.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.Subject
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.prayer.deviceTimeZoneId
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.sync.activityUuidV4
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uz.tafakkur.domain.model.Gamification
import uz.tafakkur.domain.model.CompleteLessonCommand
import uz.tafakkur.domain.model.Lesson as DomainLesson
import uz.tafakkur.domain.model.Subject as DomainSubject
import uz.tafakkur.domain.usecase.CompleteLessonUseCase
import uz.tafakkur.domain.usecase.GetLessonUseCase
import uz.tafakkur.domain.usecase.GetLessonsUseCase
import uz.tafakkur.domain.usecase.GetSubjectsUseCase

/**
 * State holder for the real backend-backed learning path.
 *
 * Subjects, lesson lists, lesson content and completion all come from the Learn API. The
 * presentation layer exposes localization keys for failures instead of leaking transport
 * messages, and a successful completion appends one replay-safe ActivityEvent so connected
 * read models can project the same user action.
 */
class LearnViewModel(
    private val getSubjects: GetSubjectsUseCase,
    private val getLessons: GetLessonsUseCase,
    private val getLesson: GetLessonUseCase,
    private val completeLesson: CompleteLessonUseCase,
    private val activity: ActivityLog,
    private val preferences: PrefPersistence,
) : ViewModel() {

    var subjects by mutableStateOf<List<Subject>>(emptyList())
        private set
    var subjectsLoading by mutableStateOf(false)
        private set
    var subjectsErrorKey by mutableStateOf<String?>(null)
        private set

    var selectedSubjectId by mutableStateOf<String?>(null)
        private set
    var lessons by mutableStateOf<List<DomainLesson>>(emptyList())
        private set
    var lessonsLoading by mutableStateOf(false)
        private set
    var lessonsErrorKey by mutableStateOf<String?>(null)
        private set

    var selectedLessonId by mutableStateOf<String?>(null)
        private set
    var selectedLesson by mutableStateOf<DomainLesson?>(null)
        private set
    var lessonLoading by mutableStateOf(false)
        private set
    var lessonErrorKey by mutableStateOf<String?>(null)
        private set

    var completing by mutableStateOf(false)
        private set
    var completion by mutableStateOf<Gamification?>(null)
        private set
    var completionErrorKey by mutableStateOf<String?>(null)
        private set

    init {
        loadSubjects()
    }

    fun loadSubjects() {
        if (subjectsLoading) return
        viewModelScope.launch {
            subjectsLoading = true
            subjectsErrorKey = null
            runCatching { getSubjects().map { it.toUi() } }
                .onSuccess { subjects = it }
                .onFailure { subjectsErrorKey = "learn.error.subjects" }
            subjectsLoading = false
        }
    }

    fun openSubject(subjectId: String) {
        if (subjectId.isBlank()) return
        val changed = selectedSubjectId != subjectId
        selectedSubjectId = subjectId
        lessons = emptyList()
        lessonsErrorKey = null
        if (changed) {
            selectedLessonId = null
            selectedLesson = null
            completion = null
            completionErrorKey = null
        }
        loadLessons(subjectId)
    }

    fun retryLessons() {
        if (!lessonsLoading) selectedSubjectId?.let(::loadLessons)
    }

    fun openLesson(lessonId: String) {
        if (lessonId.isBlank()) return
        recordLessonStarted(lessonId)
        selectedLessonId = lessonId
        selectedLesson = null
        lessonErrorKey = null
        completion = null
        completionErrorKey = null
        loadLesson(lessonId)
    }

    fun retryLesson() {
        if (!lessonLoading) selectedLessonId?.let(::loadLesson)
    }

    fun completeSelectedLesson() {
        val lesson = selectedLesson ?: return
        if (lesson.completed || completing || completion != null) return
        viewModelScope.launch {
            completing = true
            completionErrorKey = null
            runCatching {
                val eventPreferenceKey = "$COMPLETION_EVENT_PREFIX${lesson.id}"
                val eventId = preferences.get(eventPreferenceKey)
                    ?: activityUuidV4().also { preferences.put(eventPreferenceKey, it) }
                val result = completeLesson(
                    lesson.id,
                    CompleteLessonCommand(
                        eventId = eventId,
                        localDate = deviceDateKey(),
                        timezone = deviceTimeZoneId(),
                    ),
                )
                preferences.put(eventPreferenceKey, result.eventId)
                check(
                    activity.record(
                        feature = "learn",
                        type = "lesson_completed",
                        entityId = lesson.id,
                        // Persisted before the request: a retry after a response/process gap
                        // replays this account-specific id instead of uploading another fact.
                        eventId = result.eventId,
                        localDateOverride = result.localDate,
                        timezoneOverride = result.timezone,
                        properties = buildJsonObject {
                            put("lesson_id", lesson.id)
                            put("subject_id", lesson.subjectId)
                        },
                    ),
                )
                result.gamification
            }.onSuccess {
                completion = it
                selectedLesson = selectedLesson?.copy(completed = true)
            }.onFailure {
                completionErrorKey = "learn.error.complete"
            }
            completing = false
        }
    }

    private fun recordLessonStarted(lessonId: String) {
        val preferenceKey = "$START_EVENT_PREFIX$lessonId"
        val eventId = preferences.get(preferenceKey)
            ?: activityUuidV4().also { preferences.put(preferenceKey, it) }
        activity.record(
            feature = "learn",
            type = "lesson_started",
            entityId = lessonId,
            eventId = eventId,
            properties = buildJsonObject {
                put("lesson_id", lessonId)
                selectedSubjectId?.let { put("subject_id", it) }
            },
        )
    }

    private fun loadLessons(subjectId: String) {
        viewModelScope.launch {
            lessonsLoading = true
            lessonsErrorKey = null
            val result = runCatching { getLessons(subjectId) }
            if (selectedSubjectId == subjectId) {
                result
                    .onSuccess { lessons = it }
                    .onFailure { lessonsErrorKey = "learn.error.lessons" }
                lessonsLoading = false
            }
        }
    }

    private fun loadLesson(lessonId: String) {
        viewModelScope.launch {
            lessonLoading = true
            lessonErrorKey = null
            val result = runCatching { getLesson(lessonId) }
            if (selectedLessonId == lessonId) {
                result
                    .onSuccess { selectedLesson = it }
                    .onFailure { lessonErrorKey = "learn.error.lesson" }
                lessonLoading = false
            }
        }
    }

    private companion object {
        const val COMPLETION_EVENT_PREFIX = "learn.completionEvent."
        const val START_EVENT_PREFIX = "learn.startEvent."
    }
}

/**
 * App-owned seed rows have stable UUIDs, so their visible content is resolved through the
 * selected-language catalog. Subjects authored outside the app keep their backend title and
 * description instead of being guessed from mutable English text.
 */
private fun DomainSubject.toUi(): Subject {
    val contentKey = SEEDED_SUBJECT_CONTENT_KEYS[id]
    return Subject(
        id = id,
        name = title,
        native = description,
        titleKey = contentKey?.let { "$it.title" },
        descriptionKey = contentKey?.let { "$it.description" },
        icon = "bookOpen",
        seed = orderIndex,
        level = "",
        progress = 0f,
        next = "",
        streak = 0,
        accent = "cyan",
    )
}

private val SEEDED_SUBJECT_CONTENT_KEYS = mapOf(
    "11111111-1111-1111-1111-111111111111" to "learn.seed.salah",
    "22222222-2222-2222-2222-222222222222" to "learn.seed.gratitude",
    "33333333-3333-3333-3333-333333333333" to "learn.seed.character",
)
