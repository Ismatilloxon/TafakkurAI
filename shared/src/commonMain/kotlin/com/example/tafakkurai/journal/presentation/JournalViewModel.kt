package com.example.tafakkurai.journal.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.tafakkurai.journal.domain.JournalDraft
import com.example.tafakkurai.journal.domain.JournalMood
import com.example.tafakkurai.journal.domain.JournalPrompt
import com.example.tafakkurai.journal.domain.JournalReflection
import com.example.tafakkurai.journal.domain.JournalRepository
import com.example.tafakkurai.journal.domain.JournalSummary
import com.example.tafakkurai.journal.domain.MAX_JOURNAL_BODY_CHARS
import com.example.tafakkurai.journal.domain.filterReflections
import com.example.tafakkurai.journal.domain.journalSummary
import com.example.tafakkurai.journal.domain.nextPrompt
import com.example.tafakkurai.journal.domain.promptForDate
import com.example.tafakkurai.prayer.deviceDateKey

/** Immutable snapshot consumed by the Journal screen. */
data class JournalUiState(
    val body: String,
    val prompt: JournalPrompt,
    val mood: JournalMood,
    val editingId: String?,
    val query: String,
    val entries: List<JournalReflection>,
    val summary: JournalSummary,
    val pendingDelete: JournalReflection?,
    val feedbackKey: String?,
    val storageUnavailable: Boolean,
) {
    val canSave: Boolean get() = body.isNotBlank() && !storageUnavailable
    val isEditing: Boolean get() = editingId != null
}

/**
 * State holder for the two routes that expose Tafakkur Journal.
 *
 * It depends only on the domain repository. SQL, sync and ActivityEvent details remain in
 * the data implementation; the composable sends typed commands here and renders [state].
 */
class JournalViewModel(
    private val repository: JournalRepository,
    private val today: () -> String = ::deviceDateKey,
    private val localizedLabel: (String) -> String = { it },
) : ViewModel() {
    private var draftBody by mutableStateOf("")
    private var prompt by mutableStateOf(promptForDate(today()))
    private var selectedMood by mutableStateOf(JournalMood.CALM)
    private var editingId by mutableStateOf<String?>(null)
    private var searchQuery by mutableStateOf("")
    private var pendingDeleteId by mutableStateOf<String?>(null)
    private var feedbackKey by mutableStateOf<String?>(null)

    init {
        // The repository is app-scoped. Refreshing when either public route opens also
        // repairs its snapshot after an account switch or a background database merge.
        repository.refresh()
    }

    val state: JournalUiState
        get() {
            // Reading the observable revision makes successful repository changes invalidate
            // the snapshot without exposing Compose or SQL types through the domain model.
            repository.revision
            val all = repository.entries
            return JournalUiState(
                body = draftBody,
                prompt = prompt,
                mood = selectedMood,
                editingId = editingId,
                query = searchQuery,
                entries = filterReflections(all, searchQuery, localizedLabel),
                summary = journalSummary(all, today()),
                pendingDelete = pendingDeleteId?.let { id -> all.firstOrNull { it.id == id } },
                feedbackKey = feedbackKey,
                storageUnavailable = repository.hasLoadError,
            )
        }

    fun setBody(value: String) {
        draftBody = value.take(MAX_JOURNAL_BODY_CHARS)
        feedbackKey = null
    }

    fun setMood(value: JournalMood) {
        selectedMood = value
        feedbackKey = null
    }

    fun nextPrompt() {
        prompt = nextPrompt(prompt)
        feedbackKey = null
    }

    fun setQuery(value: String) {
        searchQuery = value.take(MAX_QUERY_CHARS)
    }

    fun retryLoad(): Boolean {
        val loaded = repository.refresh()
        if (loaded) feedbackKey = null
        return loaded
    }

    fun save(): Boolean {
        val draft = JournalDraft(prompt = prompt, mood = selectedMood, body = draftBody)
        val saved = editingId?.let { repository.update(it, draft) } ?: repository.create(draft)
        if (saved == null) {
            feedbackKey = if (draftBody.isBlank()) "journal.feedback.empty" else "journal.feedback.error"
            return false
        }
        val updated = editingId != null
        resetComposer(next = true)
        feedbackKey = if (updated) "journal.feedback.updated" else "journal.feedback.saved"
        return true
    }

    fun edit(entry: JournalReflection) {
        editingId = entry.id
        prompt = entry.prompt
        selectedMood = entry.mood
        draftBody = entry.body
        feedbackKey = null
    }

    fun cancelEdit() {
        resetComposer(next = false)
        feedbackKey = null
    }

    fun requestDelete(entry: JournalReflection) {
        pendingDeleteId = entry.id
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }

    fun confirmDelete(): Boolean {
        val id = pendingDeleteId ?: return false
        val deleted = repository.delete(id)
        if (deleted && editingId == id) resetComposer(next = false)
        pendingDeleteId = null
        feedbackKey = if (deleted) "journal.feedback.deleted" else "journal.feedback.error"
        return deleted
    }

    private fun resetComposer(next: Boolean) {
        draftBody = ""
        selectedMood = JournalMood.CALM
        editingId = null
        if (next) prompt = nextPrompt(prompt)
    }

    private companion object {
        const val MAX_QUERY_CHARS = 200
    }
}
