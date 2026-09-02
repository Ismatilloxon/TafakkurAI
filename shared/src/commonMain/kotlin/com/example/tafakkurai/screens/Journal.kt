package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.formatJournalDate
import com.example.tafakkurai.i18n.journalActiveDaysUnit
import com.example.tafakkurai.i18n.journalEntriesUnit
import com.example.tafakkurai.i18n.journalStreakUnit
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.journal.domain.JournalMood
import com.example.tafakkurai.journal.domain.JournalReflection
import com.example.tafakkurai.journal.domain.MAX_JOURNAL_BODY_CHARS
import com.example.tafakkurai.journal.presentation.JournalUiState
import com.example.tafakkurai.journal.presentation.JournalViewModel
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.ui.AmbientGlow
import com.example.tafakkurai.ui.BackBar
import com.example.tafakkurai.ui.BreathOrb
import com.example.tafakkurai.ui.Card
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.LargeTitle
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.Overline
import com.example.tafakkurai.ui.Pill
import com.example.tafakkurai.ui.SectionTitle
import com.example.tafakkurai.ui.TfIcon
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.pressScale
import com.example.tafakkurai.ui.soft
import com.example.tafakkurai.ui.components.TfConfirmationDialog
import com.example.tafakkurai.ui.components.TfIconButton
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.ui.components.TfSearchField
import com.example.tafakkurai.ui.components.TfSecondaryButton
import com.example.tafakkurai.ui.components.TfTextField

/** One implementation for both the Tafakkur detail route and the legacy Reflect route. */
@Composable
fun JournalScreen(
    viewModel: JournalViewModel,
    onBack: (() -> Unit)?,
    serifTitle: Boolean = true,
) {
    val state = viewModel.state
    val title = tr("journal.title")
    val todayKey = deviceDateKey()
    val strings = LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        if (onBack != null) {
            BackBar(title, onBack)
        } else {
            LargeTitle(title, subtitle = tr("journal.subtitle"), serif = serifTitle)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "journal-prompt", contentType = "hero") {
                JournalPromptCard(
                    prompt = tr(state.prompt.titleKey),
                    onNext = viewModel::nextPrompt,
                )
            }

            if (state.storageUnavailable) {
                item(key = "journal-storage-error", contentType = "error") {
                    JournalStorageError(onRetry = viewModel::retryLoad)
                }
            } else {
                item(key = "journal-composer", contentType = "composer") {
                    JournalComposer(
                        state = state,
                        onBodyChange = viewModel::setBody,
                        onMoodChange = viewModel::setMood,
                        onCancelEdit = viewModel::cancelEdit,
                        onSave = { viewModel.save() },
                    )
                }
            }

            // A failed refresh must not make the last good private snapshot disappear.
            // Mutations stay disabled, while search and reading remain available.
            if (state.entries.isNotEmpty() || state.query.isNotEmpty()) {
                item(key = "journal-stats", contentType = "stats") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatTile(
                            state.summary.entryCount.toString(),
                            strings.journalEntriesUnit(state.summary.entryCount),
                            LocalTf.current.accent,
                        )
                        StatTile(
                            state.summary.activeDaysInLastSeven.toString(),
                            strings.journalActiveDaysUnit(state.summary.activeDaysInLastSeven),
                            LocalTf.current.green,
                        )
                        StatTile(
                            state.summary.streakDays.toString(),
                            strings.journalStreakUnit(state.summary.streakDays),
                            LocalTf.current.gold,
                        )
                    }
                }

                item(key = "journal-search", contentType = "search") {
                    TfSearchField(
                        query = state.query,
                        onQuery = viewModel::setQuery,
                        placeholder = tr("journal.search.placeholder"),
                        searchContentDescription = tr("journal.search.a11y"),
                        clearContentDescription = tr("journal.search.clear_a11y"),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                    )
                }
            }

            if (!state.storageUnavailable || state.entries.isNotEmpty() || state.query.isNotEmpty()) {
                item(key = "journal-heading", contentType = "heading") {
                    SectionTitle(tr("journal.section.reflections"), serif = true)
                }

                if (state.entries.isEmpty()) {
                    item(key = "journal-empty", contentType = "empty") {
                        JournalEmptyState(hasQuery = state.query.isNotEmpty())
                    }
                } else {
                    items(
                        items = state.entries,
                        key = { it.id },
                        contentType = { "reflection" },
                    ) { entry ->
                        JournalEntryCard(
                            entry = entry,
                            todayKey = todayKey,
                            onEdit = { viewModel.edit(entry) },
                            onDelete = { viewModel.requestDelete(entry) },
                            readOnly = state.storageUnavailable,
                        )
                    }
                }
            }

            item(key = "journal-privacy", contentType = "footer") {
                PrivacyFooter(tr("journal.privacy"))
            }
        }
    }

    if (state.pendingDelete != null && !state.storageUnavailable) {
        TfConfirmationDialog(
            title = tr("journal.delete.title"),
            message = tr("journal.delete.message"),
            confirmLabel = tr("journal.delete.confirm"),
            cancelLabel = tr("common.cancel"),
            onConfirm = { viewModel.confirmDelete() },
            onCancel = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun JournalPromptCard(prompt: String, onNext: () -> Unit) {
    val c = LocalTf.current
    val anotherPromptLabel = tr("journal.action.another_prompt")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            AmbientGlow(c.accent, opacity = 0.16f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                BreathOrb(size = 76, color = c.accent, purple = c.purple)
                Overline(tr("journal.overline"))
                TfText(
                    prompt,
                    color = c.fg,
                    size = 22f,
                    family = Fonts.serif,
                    align = TextAlign.Center,
                    lineHeight = 1.35f,
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .heightIn(min = 44.dp)
                        .border(0.5.dp, c.line, RoundedCornerShape(999.dp))
                        .then(pressScale(onNext, 0.95f))
                        .semantics {
                            role = Role.Button
                            contentDescription = anotherPromptLabel
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TfIcon("repeat", size = 13, color = c.fg2)
                    TfText(anotherPromptLabel, color = c.fg2, size = 13f)
                }
            }
        }
    }
}

@Composable
private fun JournalComposer(
    state: JournalUiState,
    onBodyChange: (String) -> Unit,
    onMoodChange: (JournalMood) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
) {
    val c = LocalTf.current
    val writeA11yLabel = tr("journal.write_a11y")
    InsetCard(pad = 16) {
        if (state.isEditing) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TfIcon("edit", size = 15, color = c.accent)
                TfText(
                    tr("journal.editing"),
                    color = c.accent,
                    size = 13f,
                    weight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Overline(tr("journal.mood.label"), modifier = Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            JournalMood.entries.chunked(2).forEach { moods ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    moods.forEach { mood ->
                        val selected = state.mood == mood
                        val moodLabel = tr(mood.titleKey)
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .heightIn(min = 44.dp)
                                .background(if (selected) c.accent.soft(0.18f) else c.card3)
                                .then(pressScale({ onMoodChange(mood) }, 0.96f))
                                .semantics {
                                    role = Role.RadioButton
                                    this.selected = selected
                                    contentDescription = moodLabel
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TfText(
                                moodLabel,
                                color = if (selected) c.accent else c.fg2,
                                size = 13f,
                                weight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        TfTextField(
            value = state.body,
            onValueChange = onBodyChange,
            placeholder = tr("journal.write_placeholder"),
            singleLine = false,
            modifier = Modifier
                .padding(top = 14.dp)
                .heightIn(min = 128.dp)
                .semantics { contentDescription = writeA11yLabel },
        )
        TfText(
            tr("journal.characters", state.body.length, MAX_JOURNAL_BODY_CHARS),
            color = c.fg3,
            size = 11.5f,
            modifier = Modifier.align(Alignment.End).padding(top = 5.dp),
        )

        state.feedbackKey?.let { key ->
            TfText(
                tr(key),
                color = if (key == "journal.feedback.error" || key == "journal.feedback.empty") c.danger else c.green,
                size = 12.5f,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isEditing) {
                TfSecondaryButton(
                    label = tr("journal.action.cancel_edit"),
                    onClick = onCancelEdit,
                    modifier = Modifier.weight(1f),
                )
            }
            TfPrimaryButton(
                label = tr(if (state.isEditing) "journal.action.update" else "journal.action.save"),
                onClick = onSave,
                enabled = state.canSave,
                icon = "check",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun JournalStorageError(onRetry: () -> Unit) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TfIcon("repeat", size = 22, color = c.danger)
            Column(Modifier.weight(1f)) {
                TfText(
                    tr("journal.error.load_title"),
                    color = c.fg,
                    size = 16f,
                    weight = FontWeight.SemiBold,
                )
                TfText(
                    tr("journal.error.load_body"),
                    color = c.fg2,
                    size = 13f,
                    lineHeight = 1.45f,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        TfSecondaryButton(
            label = tr("journal.error.retry"),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun JournalEmptyState(hasQuery: Boolean) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            TfIcon("penLine", size = 24, color = c.accent)
            Column(Modifier.weight(1f)) {
                TfText(
                    tr(if (hasQuery) "journal.search.empty_title" else "journal.empty.title"),
                    color = c.fg,
                    size = 16f,
                    weight = FontWeight.SemiBold,
                )
                TfText(
                    tr(if (hasQuery) "journal.search.empty_body" else "journal.empty.body"),
                    color = c.fg2,
                    size = 13f,
                    lineHeight = 1.45f,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalReflection,
    todayKey: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    readOnly: Boolean,
) {
    val c = LocalTf.current
    val strings = LocalStrings.current
    Card(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TfText(
                strings.formatJournalDate(entry.entryDate, todayKey),
                color = c.fg3,
                size = 12f,
                family = Fonts.mono,
                modifier = Modifier.weight(1f),
            )
            Pill(tr(entry.mood.titleKey), c.gold, c.gold.soft(0.16f))
        }
        TfText(
            tr(entry.prompt.titleKey),
            color = c.fg,
            size = 18f,
            family = Fonts.serif,
            modifier = Modifier.padding(top = 9.dp),
        )
        TfText(
            entry.body,
            color = c.fg2,
            size = 14f,
            lineHeight = 1.55f,
            maxLines = 8,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (!readOnly) {
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TfIconButton("edit", tr("journal.action.edit_entry"), onEdit)
                TfIconButton("trash", tr("journal.action.delete_entry"), onDelete, destructive = true)
            }
        }
    }
}
