package com.example.tafakkurai.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.ChatMsg
import com.example.tafakkurai.ai.data.OnDeviceActionRouter
import com.example.tafakkurai.ai.data.VocabularySourceStore
import com.example.tafakkurai.ai.domain.LocalExecutionResult
import com.example.tafakkurai.ai.domain.LocalProposal
import com.example.tafakkurai.ai.domain.ProposedAction
import com.example.tafakkurai.settings.domain.IsCloudConversationEnabledUseCase
import kotlinx.coroutines.launch
import uz.tafakkur.domain.model.CoachEvent
import uz.tafakkur.domain.usecase.SendCoachMessageUseCase

/**
 * State-changing commands are parsed and previewed entirely on-device. Non-action
 * conversation may stream from the backend. Prose is never interpreted as a mutation.
 */
class CoachViewModel(
    private val sendMessage: SendCoachMessageUseCase,
    private val localActions: OnDeviceActionRouter,
    private val isCloudConversationEnabled: IsCloudConversationEnabledUseCase,
) : ViewModel() {

    var messages by mutableStateOf<List<ChatMsg>>(emptyList())
        private set
    var draft by mutableStateOf("")
    var streaming by mutableStateOf(false)
        private set
    var proposal by mutableStateOf<LocalProposal?>(null)
        private set
    var execution by mutableStateOf<LocalExecutionResult?>(null)
        private set
    var cloudUnavailable by mutableStateOf(false)
        private set
    var cloudDisabled by mutableStateOf(false)
        private set
    var importingSource by mutableStateOf(false)
        private set
    var sourceDraft by mutableStateOf("")
        private set

    private var conversationId: String? = null

    fun send(text: String = draft) {
        val value = text.trim()
        if (value.isEmpty() || streaming) return
        draft = ""
        proposal = null
        execution = null
        cloudUnavailable = false
        cloudDisabled = false
        importingSource = false
        sourceDraft = ""
        messages = messages + ChatMsg("me", value, "now")

        when (val local = localActions.propose(value)) {
            LocalProposal.NotLocalAction -> if (isCloudConversationEnabled()) {
                sendToCloud(value)
            } else {
                cloudDisabled = true
            }
            else -> proposal = local
        }
    }

    fun confirmProposal() {
        val ready = proposal as? LocalProposal.Ready ?: return
        execution = localActions.execute(ready.action)
        proposal = null
        importingSource = false
        sourceDraft = ""
    }

    fun cancelProposal() {
        proposal = null
        importingSource = false
        sourceDraft = ""
    }

    fun beginSourceImport() {
        if (proposal is LocalProposal.NeedsSource) importingSource = true
    }

    fun updateSourceDraft(value: String) {
        sourceDraft = value.take(VocabularySourceStore.MAX_CONTENT_CHARS)
    }

    fun saveSource() {
        val action = (proposal as? LocalProposal.NeedsSource)?.action ?: return
        proposal = localActions.saveSource(action, sourceDraft)
        if (proposal is LocalProposal.Ready) {
            importingSource = false
            sourceDraft = ""
        }
    }

    fun dismissFeedback() {
        execution = null
        cloudUnavailable = false
        cloudDisabled = false
        if (proposal is LocalProposal.Invalid) proposal = null
    }

    private fun sendToCloud(text: String) {
        messages = messages + ChatMsg("ai", "", "now")
        val index = messages.lastIndex
        streaming = true
        viewModelScope.launch {
            val reply = StringBuilder()
            try {
                sendMessage(text, conversationId).collect { event ->
                    when (event) {
                        is CoachEvent.Delta -> {
                            reply.append(event.text)
                            replaceMessage(index, reply.toString())
                        }
                        is CoachEvent.Done -> conversationId = event.conversationId
                        is CoachEvent.Error -> markCloudUnavailable(index, reply)
                    }
                }
            } catch (_: Exception) {
                markCloudUnavailable(index, reply)
            } finally {
                streaming = false
            }
        }
    }

    private fun markCloudUnavailable(index: Int, reply: StringBuilder) {
        cloudUnavailable = true
        if (reply.isEmpty()) messages = messages.filterIndexed { at, _ -> at != index }
    }

    private fun replaceMessage(index: Int, text: String) {
        val list = messages.toMutableList()
        if (index in list.indices) list[index] = list[index].copy(text = text)
        messages = list
    }
}

internal fun ProposedAction.previewIdentity(): String = when (this) {
    is ProposedAction.CreateTask -> "task"
    is ProposedAction.LogActivity -> "activity"
    is ProposedAction.CreateHabit -> "habit"
    is ProposedAction.SaveGratitude -> "gratitude"
    is ProposedAction.StartHifz -> "hifz"
    is ProposedAction.CreateFlashcards -> "flashcards"
}
