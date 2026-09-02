package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.learning.domain.QuranQuizText
import com.example.tafakkurai.learning.presentation.QuranQuizEvent
import com.example.tafakkurai.learning.presentation.QuranQuizGoalUiModel
import com.example.tafakkurai.learning.presentation.QuranQuizSessionUiState
import com.example.tafakkurai.learning.presentation.QuranQuizViewModel
import com.example.tafakkurai.ui.BackBar
import com.example.tafakkurai.ui.Card
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.IconBadge
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.Overline
import com.example.tafakkurai.ui.ProgressBar
import com.example.tafakkurai.ui.TfIcon
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.pressScale
import com.example.tafakkurai.ui.soft
import com.example.tafakkurai.ui.components.TfEmptyState
import com.example.tafakkurai.ui.components.TfErrorState
import com.example.tafakkurai.ui.components.TfLoadingState
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.ui.components.TfSearchField
import com.example.tafakkurai.ui.components.TfSecondaryButton

/** Searchable library of quizzes generated only from exact Qurʼan/Hifz scopes the user started. */
@Composable
fun QuranQuizLibraryScreen(
    viewModel: QuranQuizViewModel,
    onBack: () -> Unit,
    onOpenQuiz: (Int) -> Unit,
) {
    val state = viewModel.state
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("quran.quiz.library.title"), onBack)
        when {
            state.loadFailed -> TfErrorState(
                message = tr("quran.quiz.load_failed"),
                retryLabel = tr("common.retry"),
                onRetry = { viewModel.onEvent(QuranQuizEvent.Refresh) },
            )
            state.totalGoals == 0 -> TfEmptyState(
                title = tr("quran.quiz.empty.title"),
                detail = tr("quran.quiz.empty.detail"),
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        TfText(
                            tr("quran.quiz.library.heading"),
                            color = LocalTf.current.fg,
                            size = 25f,
                            weight = FontWeight.Bold,
                            family = Fonts.display,
                        )
                        TfText(
                            tr("quran.quiz.library.detail"),
                            color = LocalTf.current.fg2,
                            size = 13f,
                            lineHeight = 1.5f,
                        )
                    }
                }
                item(key = "search") {
                    TfSearchField(
                        query = state.query,
                        onQuery = { viewModel.onEvent(QuranQuizEvent.Search(it)) },
                        placeholder = tr("quran.quiz.search.hint"),
                        searchContentDescription = tr("quran.quiz.search.a11y"),
                        clearContentDescription = tr("quran.quiz.search.clear_a11y"),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                    )
                }
                if (state.goals.isEmpty()) {
                    item(key = "empty-search") {
                        TfEmptyState(
                            title = tr("quran.quiz.search.empty", state.query),
                            icon = "search",
                        )
                    }
                } else {
                    items(
                        items = state.goals,
                        key = QuranQuizGoalUiModel::surahNumber,
                        contentType = { "surah-quiz" },
                    ) { goal ->
                        QuranQuizGoalRow(goal = goal, onClick = { onOpenQuiz(goal.surahNumber) })
                    }
                }
                item(key = "source") {
                    SourceNotice()
                }
            }
        }
    }
}

@Composable
private fun QuranQuizGoalRow(goal: QuranQuizGoalUiModel, onClick: () -> Unit) {
    val c = LocalTf.current
    val a11y = tr("quran.quiz.open_a11y", goal.name)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = HPAD.dp).semantics {
            role = Role.Button
            contentDescription = a11y
        },
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(c.purple.soft(0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                TfText(goal.surahNumber.toString(), color = c.purple, size = 13f, weight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TfText(goal.name, color = c.fg, size = 15.5f, weight = FontWeight.SemiBold)
                    TfText(goal.arabicName, color = c.gold, size = 16f, family = Fonts.serif)
                }
                TfText(tr(goal.sourceKey), color = c.fg3, size = 11.5f)
                goal.latestAttempt?.let { attempt ->
                    TfText(
                        tr("quran.quiz.latest_score", attempt.correct, attempt.total),
                        color = c.green,
                        size = 11.5f,
                    )
                }
            }
            TfIcon("chevronRight", size = 18, color = c.fg3)
        }
    }
}

/** Five-question, deterministic review for one exact surah. */
@Composable
fun QuranQuizSessionScreen(
    surahNumber: Int,
    viewModel: QuranQuizViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.state
    LaunchedEffect(surahNumber) {
        viewModel.onEvent(QuranQuizEvent.Start(surahNumber))
    }
    val session = state.session?.takeIf { it.surahNumber == surahNumber }
    Column(Modifier.fillMaxSize()) {
        BackBar(session?.surahName ?: tr("quran.quiz.session.title"), onBack)
        when {
            session == null && state.feedbackKey != null -> TfErrorState(
                message = tr(state.feedbackKey),
                retryLabel = tr("common.retry"),
                onRetry = { viewModel.onEvent(QuranQuizEvent.Start(surahNumber)) },
            )
            session == null -> TfLoadingState(tr("quran.quiz.loading"))
            session.completedAttempt != null -> QuranQuizResult(
                session = session,
                onRetry = { viewModel.onEvent(QuranQuizEvent.RetryQuiz) },
                onDone = onBack,
            )
            else -> QuranQuizQuestionContent(
                session = session,
                feedbackKey = state.feedbackKey,
                onSelect = { viewModel.onEvent(QuranQuizEvent.Select(it)) },
                onNext = { viewModel.onEvent(QuranQuizEvent.Next) },
            )
        }
    }
}

@Composable
private fun ColumnScope.QuranQuizQuestionContent(
    session: QuranQuizSessionUiState,
    feedbackKey: String?,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val c = LocalTf.current
    val question = session.currentQuestion ?: return
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = HPAD.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item(key = "progress") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Overline(tr("quran.quiz.progress", session.currentIndex + 1, session.questions.size), c.purple)
                    TfText(session.arabicName, color = c.gold, size = 17f, family = Fonts.serif)
                }
                ProgressBar(
                    value = (session.currentIndex + 1f) / session.questions.size,
                    color = c.purple,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item(key = "prompt-${question.id}") {
            Card(Modifier.fillMaxWidth()) {
                TfText(
                    localized(question.prompt),
                    color = c.fg,
                    size = 21f,
                    weight = FontWeight.SemiBold,
                    lineHeight = 1.4f,
                )
            }
        }
        items(question.choices.indices.toList(), key = { "${question.id}:$it" }) { index ->
            QuranQuizChoice(
                text = localized(question.choices[index]),
                index = index,
                selectedIndex = session.selectedIndex,
                answerIndex = question.answerIndex,
                onClick = { onSelect(index) },
            )
        }
        if (session.selectedIndex != null) {
            item(key = "explanation-${question.id}") {
                Card(Modifier.fillMaxWidth()) {
                    val correct = session.selectedIndex == question.answerIndex
                    TfText(
                        tr(if (correct) "quran.quiz.answer.correct" else "quran.quiz.answer.incorrect"),
                        color = if (correct) c.green else c.warn,
                        size = 14f,
                        weight = FontWeight.Bold,
                    )
                    TfText(
                        localized(question.explanation),
                        color = c.fg2,
                        size = 13f,
                        lineHeight = 1.5f,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
            item(key = "next-${question.id}") {
                TfPrimaryButton(
                    label = tr(if (session.isLastQuestion) "quran.quiz.finish" else "common.next"),
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (feedbackKey != null) {
            item(key = "feedback") {
                TfText(feedbackKey.let { tr(it) }, color = c.danger, size = 13f, lineHeight = 1.4f)
            }
        }
        item(key = "source") { SourceNotice(inset = false) }
    }
}

@Composable
private fun QuranQuizChoice(
    text: String,
    index: Int,
    selectedIndex: Int?,
    answerIndex: Int,
    onClick: () -> Unit,
) {
    val c = LocalTf.current
    val answered = selectedIndex != null
    val isAnswer = answered && index == answerIndex
    val isWrongSelection = answered && index == selectedIndex && index != answerIndex
    val accent = when {
        isAnswer -> c.green
        isWrongSelection -> c.danger
        else -> c.fg3
    }
    val state = when {
        isAnswer -> tr("quran.quiz.choice.correct_a11y")
        isWrongSelection -> tr("quran.quiz.choice.incorrect_a11y")
        else -> tr("quran.quiz.choice.not_selected_a11y")
    }
    val interaction = if (answered) Modifier else pressScale(onClick, 0.98f)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isAnswer || isWrongSelection) accent.soft(0.12f) else c.card)
            .border(1.dp, if (isAnswer || isWrongSelection) accent else c.line, RoundedCornerShape(14.dp))
            .then(interaction)
            .semantics {
                role = Role.RadioButton
                selected = selectedIndex == index
                if (answered) disabled()
                contentDescription = text
                stateDescription = state
            }
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(accent.soft(0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            TfText((index + 1).toString(), color = accent, size = 12f, weight = FontWeight.Bold)
        }
        TfText(text, color = c.fg, size = 14.5f, modifier = Modifier.weight(1f))
        if (isAnswer) TfIcon("checkCircle", size = 18, color = c.green)
    }
}

@Composable
private fun ColumnScope.QuranQuizResult(
    session: QuranQuizSessionUiState,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) {
    val c = LocalTf.current
    val attempt = session.completedAttempt ?: return
    val messageKey = when {
        attempt.correct == attempt.total -> "quran.quiz.result.perfect"
        attempt.correct * 2 >= attempt.total -> "quran.quiz.result.good"
        else -> "quran.quiz.result.review"
    }
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge("star", c.gold, size = 72, iconSize = 32)
        TfText(
            tr("quran.quiz.result.title"),
            color = c.fg,
            size = 27f,
            weight = FontWeight.Bold,
            family = Fonts.display,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        TfText(
            tr("quran.quiz.result.score", attempt.correct, attempt.total),
            color = c.purple,
            size = 34f,
            weight = FontWeight.Bold,
            family = Fonts.mono,
            modifier = Modifier.padding(top = 10.dp),
        )
        TfText(
            tr(messageKey),
            color = c.fg2,
            size = 14f,
            lineHeight = 1.5f,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TfPrimaryButton(
            label = tr("common.done"),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
        TfSecondaryButton(
            label = tr("quran.quiz.retry"),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

@Composable
private fun SourceNotice(inset: Boolean = true) {
    val c = LocalTf.current
    val modifier = if (inset) Modifier.fillMaxWidth().padding(horizontal = HPAD.dp) else Modifier.fillMaxWidth()
    Card(modifier) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TfIcon("shieldCheck", size = 17, color = c.green)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TfText(tr("quran.quiz.source.title"), color = c.fg, size = 13.5f, weight = FontWeight.SemiBold)
                TfText(tr("quran.quiz.source.detail"), color = c.fg3, size = 11.5f, lineHeight = 1.45f)
            }
        }
    }
}

@Composable
private fun localized(text: QuranQuizText): String {
    val strings = LocalStrings.current
    return when (text) {
        is QuranQuizText.Source -> text.value
        is QuranQuizText.Key -> {
            val arguments = text.arguments.map { argument ->
                if (argument.startsWith("quran.surah.")) strings[argument] else argument
            }
            strings.format(text.key, *arguments.toTypedArray())
        }
    }
}
