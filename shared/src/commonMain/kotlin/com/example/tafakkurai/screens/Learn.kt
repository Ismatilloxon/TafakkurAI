package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tafakkurai.*
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfEmptyState
import com.example.tafakkurai.ui.components.TfErrorState
import com.example.tafakkurai.ui.components.TfLoadingState
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.vm.LearnViewModel
import org.koin.mp.KoinPlatform.getKoin

@Composable
private fun learnViewModel(sessionKey: Int): LearnViewModel = viewModel(key = "learn:$sessionKey") {
    LearnViewModel(
        getKoin().get(),
        getKoin().get(),
        getKoin().get(),
        getKoin().get(),
        getKoin().get(),
        getKoin().get(),
    )
}

@Composable
fun LearnScreen(
    sessionKey: Int,
    onLesson: () -> Unit,
    onSubject: (String) -> Unit,
    onFlashcards: () -> Unit,
) {
    val vm = learnViewModel(sessionKey)
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        LargeTitle(tr("learn.title"))
        InsetCard(onClick = onFlashcards) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                IconBadge("bookOpen", c.cyan, size = 44, iconSize = 21)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    TfText(
                        tr("learn.flashcards.title"),
                        color = c.fg,
                        size = 15.5f,
                        weight = FontWeight.SemiBold,
                    )
                    TfText(tr("learn.flashcards.detail"), color = c.fg2, size = 12.5f)
                }
                TfIcon("chevronRight", size = 18, color = c.fg3)
            }
        }
        when {
            vm.subjectsLoading && vm.subjects.isEmpty() -> TfLoadingState(
                tr("learn.loading.subjects"),
                Modifier.weight(1f),
            )
            vm.subjectsErrorKey != null && vm.subjects.isEmpty() -> TfErrorState(
                message = tr(vm.subjectsErrorKey!!),
                retryLabel = tr("common.retry"),
                onRetry = vm::loadSubjects,
                modifier = Modifier.weight(1f),
            )
            vm.subjects.isEmpty() -> TfEmptyState(
                title = tr("learn.empty.subjects.title"),
                detail = tr("learn.empty.subjects.detail"),
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                vm.selectedLesson?.takeIf { vm.completion == null }?.let { lesson ->
                    item(key = "resume:${lesson.id}", contentType = "resume") {
                        InsetCard(onClick = onLesson) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconBadge("bookOpen", LocalTf.current.cyan, size = 42, iconSize = 20)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Overline(tr("learn.resume.label"))
                                TfText(lesson.title, color = LocalTf.current.fg, size = 15f, weight = FontWeight.SemiBold)
                            }
                            TfIcon("chevronRight", size = 18, color = LocalTf.current.fg3)
                        }
                    }
                    }
                }
                item(key = "subjects-heading", contentType = "heading") {
                    SectionTitle(tr("learn.subjects.title"))
                }
                items(vm.subjects, key = Subject::id, contentType = { "subject" }) { subject ->
                    Box(Modifier.padding(horizontal = HPAD.dp)) {
                        SubjectRow(subject) { onSubject(subject.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectRow(s: Subject, onOpen: () -> Unit) {
    val c = LocalTf.current
    val col = c.named(if (s.accent == "blue") "cyan" else s.accent)
    val title = s.localizedTitle()
    val description = s.localizedDescription()
    Card(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            IconBadge(s.icon, col, size = 46, iconSize = 22)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TfText(title, color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                if (description.isNotBlank()) {
                    TfText(description, color = c.fg2, size = 12.5f, lineHeight = 1.4f, maxLines = 2)
                }
            }
            TfIcon("chevronRight", size = 18, color = c.fg3)
        }
    }
}

// ── Subject detail ───────────────────────────────────────────────────────────
@Composable
fun SubjectScreen(
    subjectId: String,
    sessionKey: Int,
    onBack: () -> Unit,
    onLesson: () -> Unit,
    onFlashcards: () -> Unit,
) {
    val vm = learnViewModel(sessionKey)
    val c = LocalTf.current
    val subject = vm.subjects.firstOrNull { it.id == subjectId }
    val subjectTitle = subject?.localizedTitle()
    val subjectDescription = subject?.localizedDescription().orEmpty()
    val col = c.cyan
    LaunchedEffect(subjectId) { vm.openSubject(subjectId) }

    Column(Modifier.fillMaxSize()) {
        BackBar(subjectTitle ?: tr("learn.subject.title"), onBack)
        when {
            vm.lessonsLoading -> TfLoadingState(tr("learn.loading.lessons"), Modifier.weight(1f))
            vm.lessonsErrorKey != null -> TfErrorState(
                message = tr(vm.lessonsErrorKey!!),
                retryLabel = tr("common.retry"),
                onRetry = vm::retryLessons,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                subject?.let { realSubject ->
                    item(key = "subject:${realSubject.id}", contentType = "subject-summary") {
                        InsetCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            IconBadge(realSubject.icon, col, size = 52, iconSize = 24)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TfText(subjectTitle ?: realSubject.name, color = c.fg, size = 18f, weight = FontWeight.SemiBold)
                                if (subjectDescription.isNotBlank()) {
                                    TfText(subjectDescription, color = c.fg2, size = 13f, lineHeight = 1.45f)
                                }
                            }
                        }
                    }
                    }
                }
                item(key = "flashcards", contentType = "action") {
                    InsetCard(onClick = onFlashcards) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            IconBadge("bookOpen", col, size = 44, iconSize = 21)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                TfText(tr("learn.flashcards.title"), color = c.fg, size = 15.5f, weight = FontWeight.SemiBold)
                                TfText(tr("learn.flashcards.detail"), color = c.fg2, size = 12.5f)
                            }
                            TfIcon("chevronRight", size = 18, color = c.fg3)
                        }
                    }
                }
                item(key = "lessons-heading", contentType = "heading") {
                    SectionTitle(tr("learn.lessons.title"))
                }
                if (vm.lessons.isEmpty()) {
                    item(key = "lessons-empty", contentType = "empty") {
                        TfEmptyState(
                            title = tr("learn.empty.lessons.title"),
                            detail = tr("learn.empty.lessons.detail"),
                        )
                    }
                } else {
                    items(vm.lessons, key = { it.id }, contentType = { "lesson" }) { lesson ->
                        Box(Modifier.padding(horizontal = HPAD.dp)) {
                            Card(Modifier.fillMaxWidth(), onClick = {
                                vm.openLesson(lesson.id)
                                onLesson()
                            }) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        Modifier.size(38.dp).clip(CircleShape).background(col.soft(0.14f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TfText(lesson.orderIndex.toString(), color = col, size = 12.5f, weight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        TfText(lesson.title, color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                                        if (lesson.body.isNotBlank()) {
                                            TfText(lesson.body, color = c.fg2, size = 12.5f, lineHeight = 1.4f, maxLines = 2)
                                        }
                                    }
                                    TfIcon(
                                        if (lesson.completed) "checkCircle" else "chevronRight",
                                        size = 18,
                                        color = if (lesson.completed) c.green else c.fg3,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun Subject.localizedTitle(): String = titleKey?.let { tr(it) } ?: name

@Composable
@ReadOnlyComposable
private fun Subject.localizedDescription(): String = descriptionKey?.let { tr(it) } ?: native

@Composable
fun LessonScreen(sessionKey: Int, onBack: () -> Unit) {
    val vm = learnViewModel(sessionKey)
    val c = LocalTf.current
    val lesson = vm.selectedLesson
    val completion = vm.completion

    Column(Modifier.fillMaxSize()) {
        BackBar(lesson?.title ?: tr("learn.lesson.title"), onBack)
        when {
            vm.lessonLoading -> TfLoadingState(tr("learn.loading.lesson"), Modifier.weight(1f))
            vm.lessonErrorKey != null -> TfErrorState(
                message = tr(vm.lessonErrorKey!!),
                retryLabel = tr("common.retry"),
                onRetry = vm::retryLesson,
                modifier = Modifier.weight(1f),
            )
            lesson == null -> TfEmptyState(
                title = tr("learn.empty.lesson.title"),
                detail = tr("learn.empty.lesson.detail"),
                modifier = Modifier.weight(1f),
            )
            completion != null -> ScrollBody {
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconBadge("checkCircle", c.green, size = 72, iconSize = 34)
                    TfText(tr("learn.completed.title"), color = c.fg, size = 25f, weight = FontWeight.Bold)
                    TfText(tr("learn.completed.detail"), color = c.fg2, size = 14f, lineHeight = 1.5f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(tr("learn.completed.xp", completion.xp), c.gold, c.gold.soft(0.16f))
                        Pill(tr("learn.completed.level", completion.level), c.purple, c.purple.soft(0.16f))
                    }
                    Pill(tr("learn.completed.streak", completion.streak), c.warn, c.warn.soft(0.16f))
                }
                Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
                    TfPrimaryButton(tr("learn.back_to_subject"), onBack, Modifier.fillMaxWidth())
                }
            }
            else -> ScrollBody {
                Column(
                    Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Overline(tr("learn.lesson.number", lesson.orderIndex))
                    TfText(lesson.title, color = c.fg, size = 28f, family = Fonts.serif, lineHeight = 1.1f)
                    TfText(lesson.body, color = c.fg2, size = 15f, lineHeight = 1.65f, modifier = Modifier.padding(top = 4.dp))
                }
                vm.completionErrorKey?.let { errorKey ->
                    InsetCard {
                        TfText(tr(errorKey), color = c.danger, size = 13.5f, lineHeight = 1.45f)
                    }
                }
                Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
                    TfPrimaryButton(
                        label = tr(
                            when {
                                lesson.completed -> "learn.completed.title"
                                vm.completing -> "learn.completing"
                                else -> "learn.complete"
                            },
                        ),
                        onClick = vm::completeSelectedLesson,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !vm.completing && !lesson.completed,
                        icon = "checkCircle",
                    )
                }
            }
        }
    }
}
