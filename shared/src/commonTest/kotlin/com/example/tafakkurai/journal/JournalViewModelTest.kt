package com.example.tafakkurai.journal

import com.example.tafakkurai.journal.domain.JournalDraft
import com.example.tafakkurai.journal.domain.JournalMood
import com.example.tafakkurai.journal.domain.JournalOrigin
import com.example.tafakkurai.journal.domain.JournalPrompt
import com.example.tafakkurai.journal.domain.JournalReflection
import com.example.tafakkurai.journal.domain.JournalRepository
import com.example.tafakkurai.journal.presentation.JournalViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalViewModelTest {

    @Test
    fun createEditAndDeleteUseTheRepositoryAndResetTheComposer() {
        val repository = FakeJournalRepository()
        val viewModel = JournalViewModel(repository, today = { TODAY })

        viewModel.setMood(JournalMood.GRATEFUL)
        viewModel.setBody("  A real reflection  ")
        assertTrue(viewModel.save())

        val created = repository.entries.single()
        assertEquals("A real reflection", created.body)
        assertEquals(JournalMood.GRATEFUL, created.mood)
        assertEquals("", viewModel.state.body)
        assertEquals("journal.feedback.saved", viewModel.state.feedbackKey)

        viewModel.edit(created)
        viewModel.setBody("Updated privately")
        assertTrue(viewModel.save())
        assertEquals("Updated privately", repository.entries.single().body)
        assertEquals("journal.feedback.updated", viewModel.state.feedbackKey)

        val updated = repository.entries.single()
        viewModel.requestDelete(updated)
        assertEquals(updated.id, viewModel.state.pendingDelete?.id)
        assertTrue(viewModel.confirmDelete())
        assertTrue(repository.entries.isEmpty())
        assertNull(viewModel.state.pendingDelete)
        assertEquals("journal.feedback.deleted", viewModel.state.feedbackKey)
    }

    @Test
    fun aFailedWriteKeepsTheDraftAndExplainsTheFailure() {
        val repository = FakeJournalRepository().apply { failWrites = true }
        val viewModel = JournalViewModel(repository, today = { TODAY })

        viewModel.setBody("Do not lose this")
        assertFalse(viewModel.save())

        assertEquals("Do not lose this", viewModel.state.body)
        assertEquals("journal.feedback.error", viewModel.state.feedbackKey)
        assertTrue(repository.entries.isEmpty())
    }

    @Test
    fun aLoadFailureMakesTheComposerReadOnlyUntilRetrySucceeds() {
        val repository = FakeJournalRepository().apply { failLoads = true }
        val viewModel = JournalViewModel(repository, today = { TODAY })
        viewModel.setBody("Kept as a draft")

        assertTrue(viewModel.state.storageUnavailable)
        assertFalse(viewModel.state.canSave)

        repository.failLoads = false
        assertTrue(viewModel.retryLoad())
        assertFalse(viewModel.state.storageUnavailable)
        assertTrue(viewModel.state.canSave)
        assertEquals("Kept as a draft", viewModel.state.body)
    }

    private class FakeJournalRepository : JournalRepository {
        private val stored = mutableListOf<JournalReflection>()
        override val entries: List<JournalReflection> get() = stored.toList()
        override var revision: Int = 0
        override var hasLoadError: Boolean = false
        var failWrites = false
        var failLoads = false
        private var nextId = 1

        override fun create(draft: JournalDraft): JournalReflection? {
            if (failWrites || draft.body.isBlank()) return null
            val entry = JournalReflection(
                id = "entry-${nextId++}",
                prompt = draft.prompt,
                mood = draft.mood,
                body = draft.body.trim(),
                entryDate = TODAY,
                origin = draft.origin,
                createdAt = nextId.toLong(),
                updatedAt = nextId.toLong(),
            )
            stored += entry
            revision++
            return entry
        }

        override fun update(id: String, draft: JournalDraft): JournalReflection? {
            if (failWrites || draft.body.isBlank()) return null
            val index = stored.indexOfFirst { it.id == id }
            if (index < 0) return null
            val updated = stored[index].copy(
                prompt = draft.prompt,
                mood = draft.mood,
                body = draft.body.trim(),
                updatedAt = stored[index].updatedAt + 1,
            )
            stored[index] = updated
            revision++
            return updated
        }

        override fun delete(id: String, actionOrigin: JournalOrigin): Boolean {
            if (failWrites) return false
            val removed = stored.removeAll { it.id == id }
            if (removed) revision++
            return removed
        }

        override fun refresh(): Boolean {
            hasLoadError = failLoads
            revision++
            return !failLoads
        }
    }

    private companion object {
        const val TODAY = "2026-08-29"
    }
}
