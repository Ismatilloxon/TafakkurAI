package com.example.tafakkurai.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.SocialPost
import kotlinx.coroutines.launch
import uz.tafakkur.core.feedback.silently
import uz.tafakkur.domain.model.SocialPost as DomainPost
import uz.tafakkur.domain.usecase.GetFeedUseCase
import uz.tafakkur.domain.usecase.IsLoggedInUseCase
import uz.tafakkur.domain.usecase.PublishThoughtUseCase
import uz.tafakkur.domain.usecase.ToggleLikeUseCase

/**
 * Backs the Thoughts feed with the real backend (replaces the TSOCIAL mock).
 * State is Compose-observable so App/ThoughtsScreen re-render on change.
 */
class ThoughtsViewModel(
    private val getFeed: GetFeedUseCase,
    private val publishThought: PublishThoughtUseCase,
    private val toggleLikeUseCase: ToggleLikeUseCase,
    private val isLoggedIn: IsLoggedInUseCase,
) : ViewModel() {

    var posts by mutableStateOf<List<SocialPost>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        // App() builds this view model before the auth gate is evaluated, so the first load
        // happens while the user may still be on the language picker. Keep it quiet.
        refresh(silent = true)
    }

    /**
     * @param silent suppresses the global loader and error snackbar. Set for loads the user did
     *  not ask for — otherwise a signed-out or offline launch greets a first-time user with
     *  "session expired" on the language screen, which is exactly what it used to do.
     */
    fun refresh(silent: Boolean = false) {
        // /v1/thoughts is an authenticated endpoint; calling it without a token only ever
        // produces a 401.
        if (!isLoggedIn()) {
            posts = emptyList()
            return
        }
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val page = if (silent) silently { getFeed() } else getFeed()
                posts = page.posts.map { it.toUi() }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load feed"
            } finally {
                loading = false
            }
        }
    }

    fun publish(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                posts = listOf(publishThought(text).toUi()) + posts
            } catch (e: Exception) {
                error = e.message ?: "Failed to publish"
            }
        }
    }

    fun like(id: String) {
        viewModelScope.launch {
            try {
                toggleLikeUseCase(id)
                refresh()
            } catch (e: Exception) {
                // keep the optimistic UI state; surface silently
            }
        }
    }
}

/** Maps the backend post to the prototype's UI model. */
private fun DomainPost.toUi(): SocialPost = SocialPost(
    id = id,
    author = authorName,
    handle = if (authorHandle.startsWith("@")) authorHandle else "@$authorHandle",
    avatarSeed = (id.hashCode() and 0x7fffffff) % 8,
    time = createdAt.substringAfter('T', "").take(5).ifEmpty { "now" },
    text = text,
    likes = likeCount,
    comments = 0,
    tag = "Thought",
)
