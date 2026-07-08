package com.example.tafakkurai.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.ChatMsg
import kotlinx.coroutines.launch
import uz.tafakkur.domain.model.CoachEvent
import uz.tafakkur.domain.usecase.SendCoachMessageUseCase

/**
 * Streams Murabbiy replies from the backend (SSE) into the existing chat UI.
 * Reuses the prototype's ChatMsg model so ChatBubble/ChatInput/SuggestRow are
 * untouched. Replaces the local ChatState mock.
 */
class CoachViewModel(
    private val sendMessage: SendCoachMessageUseCase,
) : ViewModel() {

    var messages by mutableStateOf<List<ChatMsg>>(emptyList())
        private set
    var draft by mutableStateOf("")
    var streaming by mutableStateOf(false)
        private set

    private var conversationId: String? = null

    fun send(text: String = draft) {
        val t = text.trim()
        if (t.isEmpty() || streaming) return
        draft = ""
        // user message + an empty assistant bubble we fill as deltas arrive
        messages = messages + ChatMsg("me", t, "now") + ChatMsg("ai", "", "now")
        val idx = messages.lastIndex
        streaming = true

        viewModelScope.launch {
            val sb = StringBuilder()
            try {
                sendMessage(t, conversationId).collect { ev ->
                    when (ev) {
                        is CoachEvent.Delta -> {
                            sb.append(ev.text)
                            replaceLast(idx, sb.toString())
                        }
                        is CoachEvent.Done -> conversationId = ev.conversationId
                        is CoachEvent.Error -> replaceLast(
                            idx,
                            sb.toString().ifEmpty { "Couldn't reach Murabbiy right now." },
                        )
                    }
                }
            } catch (e: Exception) {
                replaceLast(idx, sb.toString().ifEmpty { "Couldn't reach Murabbiy right now." })
            } finally {
                streaming = false
            }
        }
    }

    private fun replaceLast(idx: Int, text: String) {
        val list = messages.toMutableList()
        if (idx in list.indices) list[idx] = list[idx].copy(text = text)
        messages = list
    }
}
