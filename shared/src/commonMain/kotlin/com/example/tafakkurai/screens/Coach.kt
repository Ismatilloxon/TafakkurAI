package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.ai.domain.LocalActionIssue
import com.example.tafakkurai.ai.domain.LocalExecutionResult
import com.example.tafakkurai.ai.domain.LocalProposal
import com.example.tafakkurai.ai.domain.ProposedAction
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.Strings
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.ui.components.TfSecondaryButton
import com.example.tafakkurai.ui.components.TfTextField
import com.example.tafakkurai.vm.CoachViewModel

@Composable
fun CoachScreen(
    viewModel: CoachViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("coach.title"), onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            BreathOrb(size = 52, color = c.accent, purple = c.purple)
            Column(Modifier.weight(1f)) {
                TfText(
                    tr("coach.title"),
                    color = c.fg,
                    size = 19f,
                    weight = FontWeight.Bold,
                    family = Fonts.display,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(c.green))
                    TfText(tr("coach.mode"), color = c.green, size = 12f)
                }
            }
        }
        ChatTab(viewModel, onOpenSettings)
    }
}

@Composable
private fun ColumnScope.ChatTab(vm: CoachViewModel, onOpenSettings: () -> Unit) {
    val strings = LocalStrings.current
    val listState = rememberLazyListState()
    val visibleItems = vm.messages.size + if (
        vm.proposal != null || vm.execution != null || vm.cloudUnavailable || vm.cloudDisabled
    ) 1 else 0
    LaunchedEffect(visibleItems, vm.messages.lastOrNull()?.text, vm.proposal, vm.execution) {
        if (visibleItems > 0) listState.animateScrollToItem(visibleItems - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = HPAD.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (vm.messages.isEmpty() && vm.proposal == null && vm.execution == null) {
            item(key = "coach-intro") {
                InsetCard {
                    TfText(strings["coach.intro"], color = LocalTf.current.fg2, size = 14f, lineHeight = 1.5f)
                }
            }
        }
        itemsIndexed(
            vm.messages,
            key = { index, message -> "${index}:${message.from}:${message.time}" },
        ) { _, message ->
            if (message.text.isNotBlank()) ChatBubble(message)
            else if (vm.streaming) {
                InsetCard { TfText(strings["coach.thinking"], color = LocalTf.current.fg3, size = 13f) }
            }
        }
        vm.proposal?.let { proposal ->
            item(key = "coach-proposal") { LocalProposalCard(vm, proposal, strings) }
        }
        vm.execution?.let { result ->
            item(key = "coach-execution") { ExecutionCard(vm, result, strings) }
        }
        if (vm.cloudUnavailable) {
            item(key = "coach-cloud-error") {
                NoticeCard(strings["coach.cloud_unavailable"], onDismiss = vm::dismissFeedback)
            }
        }
        if (vm.cloudDisabled) {
            item(key = "coach-cloud-disabled") {
                NoticeCard(
                    text = strings["coach.cloud_disabled"],
                    onDismiss = vm::dismissFeedback,
                    actionLabel = strings["coach.open_settings"],
                    onAction = onOpenSettings,
                )
            }
        }
    }
    val suggestions = listOf(
        strings["coach.suggestion.task"],
        strings["coach.suggestion.habit"],
        strings["coach.suggestion.hifz"],
        strings["coach.suggestion.flashcards"],
    )
    SuggestRow(suggestions) { vm.send(it) }
    ChatInput(
        draft = vm.draft,
        onDraft = { vm.draft = it },
        onSend = { vm.send() },
        placeholder = strings["coach.input"],
    )
}

@Composable
private fun LocalProposalCard(vm: CoachViewModel, proposal: LocalProposal, strings: Strings) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth()) {
        when (proposal) {
            LocalProposal.NotLocalAction -> Unit
            is LocalProposal.Ready -> {
                Overline(strings["coach.preview.title"], c.accent)
                TfText(
                    actionDescription(proposal.action, proposal.itemCount, strings),
                    color = c.fg,
                    size = 14f,
                    lineHeight = 1.5f,
                    modifier = Modifier.padding(top = 6.dp),
                )
                TfText(
                    strings["coach.preview.local_note"],
                    color = c.fg3,
                    size = 11.5f,
                    lineHeight = 1.4f,
                    modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                )
                TfPrimaryButton(strings["coach.confirm"], vm::confirmProposal, Modifier.fillMaxWidth())
                Box(Modifier.padding(top = 8.dp)) {
                    TfSecondaryButton(strings["common.cancel"], vm::cancelProposal, Modifier.fillMaxWidth())
                }
            }
            is LocalProposal.NeedsSource -> {
                Overline(strings["coach.source.required"], c.warn)
                TfText(
                    strings.format(
                        "coach.source.explanation",
                        proposal.action.sourceTitle,
                        proposal.action.fromTheme,
                        proposal.action.toTheme,
                    ),
                    color = c.fg2,
                    size = 13.5f,
                    lineHeight = 1.5f,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (vm.importingSource) {
                    TfTextField(
                        value = vm.sourceDraft,
                        onValueChange = vm::updateSourceDraft,
                        placeholder = strings["coach.source.paste_hint"],
                        modifier = Modifier.padding(top = 10.dp).heightIn(min = 150.dp),
                        singleLine = false,
                        imeAction = ImeAction.Default,
                    )
                    Box(Modifier.padding(top = 10.dp)) {
                        TfPrimaryButton(strings["coach.source.check"], vm::saveSource, Modifier.fillMaxWidth())
                    }
                } else {
                    Box(Modifier.padding(top = 10.dp)) {
                        TfPrimaryButton(strings["coach.source.add"], vm::beginSourceImport, Modifier.fillMaxWidth())
                    }
                }
                Box(Modifier.padding(top = 8.dp)) {
                    TfSecondaryButton(strings["common.cancel"], vm::cancelProposal, Modifier.fillMaxWidth())
                }
            }
            is LocalProposal.Invalid -> {
                Overline(strings["coach.cannot_apply"], c.warn)
                TfText(
                    issueMessage(proposal.issue, proposal.arguments, strings),
                    color = c.fg2,
                    size = 13.5f,
                    lineHeight = 1.5f,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Box(Modifier.padding(top = 10.dp)) {
                    TfSecondaryButton(strings["common.close"], vm::dismissFeedback, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ExecutionCard(vm: CoachViewModel, result: LocalExecutionResult, strings: Strings) {
    val c = LocalTf.current
    val text = when (result) {
        is LocalExecutionResult.Success -> when (result.action) {
            is ProposedAction.CreateTask -> strings["coach.result.task"]
            is ProposedAction.LogActivity -> strings["coach.result.activity"]
            is ProposedAction.CreateHabit -> strings["coach.result.habit"]
            is ProposedAction.SaveGratitude -> strings["coach.result.gratitude"]
            is ProposedAction.StartHifz -> strings.format("coach.result.hifz", result.affected)
            is ProposedAction.CreateFlashcards -> strings.format("coach.result.flashcards", result.affected)
        }
        is LocalExecutionResult.Failure -> issueMessage(result.issue, result.arguments, strings)
    }
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(
                if (result is LocalExecutionResult.Success) "check" else "alertCircle",
                if (result is LocalExecutionResult.Success) c.green else c.warn,
                size = 36,
                iconSize = 17,
            )
            TfText(text, color = c.fg, size = 13.5f, lineHeight = 1.45f, modifier = Modifier.weight(1f))
        }
        Box(Modifier.padding(top = 10.dp)) {
            TfSecondaryButton(strings["common.close"], vm::dismissFeedback, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NoticeCard(
    text: String,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth()) {
        TfText(text, color = c.fg2, size = 13.5f, lineHeight = 1.5f)
        if (actionLabel != null && onAction != null) {
            Box(Modifier.padding(top = 10.dp)) {
                TfPrimaryButton(actionLabel, onAction, Modifier.fillMaxWidth())
            }
        }
        Box(Modifier.padding(top = 10.dp)) {
            TfSecondaryButton(tr("common.close"), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

private fun actionDescription(action: ProposedAction, count: Int, strings: Strings): String = when (action) {
    is ProposedAction.CreateTask -> strings.format(
        "coach.preview.task",
        action.title,
        action.dueDate,
        action.dueTime.ifBlank { strings["tasks.any_time"] },
    )
    is ProposedAction.LogActivity -> action.durationMinutes?.let { minutes ->
        strings.format("coach.preview.activity", action.title, minutes)
    } ?: strings.format("coach.preview.activity_no_duration", action.title)
    is ProposedAction.CreateHabit -> strings.format("coach.preview.habit", action.name)
    is ProposedAction.SaveGratitude -> strings.format("coach.preview.gratitude", action.text)
    is ProposedAction.StartHifz -> strings.format(
        "coach.preview.hifz", action.surahNumber, action.surahName,
    )
    is ProposedAction.CreateFlashcards -> strings.format(
        "coach.preview.flashcards",
        count,
        action.sourceTitle,
        action.fromTheme,
        action.toTheme,
    )
}

private fun issueMessage(issue: LocalActionIssue, args: List<String>, strings: Strings): String = when (issue) {
    LocalActionIssue.MISSING_TITLE -> strings["coach.issue.missing_title"]
    LocalActionIssue.MISSING_SOURCE -> strings["coach.issue.missing_source"]
    LocalActionIssue.MISSING_SCOPE -> strings["coach.issue.missing_scope"]
    LocalActionIssue.SOURCE_TOO_LARGE -> strings["coach.issue.source_too_large"]
    LocalActionIssue.SOURCE_HAS_NO_SCOPED_PAIRS -> strings["coach.issue.no_pairs"]
    LocalActionIssue.SOURCE_MISSING_THEMES -> strings.format(
        "coach.issue.missing_themes", args.firstOrNull().orEmpty(),
    )
    LocalActionIssue.NO_NEW_CARDS -> strings["coach.issue.no_new_cards"]
    LocalActionIssue.UNKNOWN_SURAH -> strings["coach.issue.unknown_surah"]
    LocalActionIssue.ACTION_FAILED -> strings["coach.issue.failed"]
    LocalActionIssue.UNSUPPORTED_ACTION -> strings["coach.issue.unsupported"]
}
