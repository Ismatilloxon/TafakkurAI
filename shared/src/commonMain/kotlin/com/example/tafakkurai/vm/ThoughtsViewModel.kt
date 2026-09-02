package com.example.tafakkurai.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.SocialPost
import com.example.tafakkurai.social.domain.MAX_SOCIAL_POST_LENGTH
import com.example.tafakkurai.social.domain.SocialRepository
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
    private val localSocial: SocialRepository,
) : ViewModel() {

    private val pendingLikes = mutableSetOf<String>()

    var posts by mutableStateOf<List<SocialPost>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadMoreError by mutableStateOf<String?>(null)
        private set
    var publishing by mutableStateOf(false)
        private set
    var publishError by mutableStateOf<String?>(null)
        private set

    private var nextCursor: String? = null

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
            nextCursor = null
            hasMore = false
            return
        }
        if (loading || loadingMore) return
        viewModelScope.launch {
            loading = true
            error = null
            loadMoreError = null
            try {
                val page = if (silent) silently { getFeed() } else getFeed()
                posts = page.posts.map { it.toUi() }
                nextCursor = page.nextCursor?.takeIf { it.isNotBlank() }
                hasMore = nextCursor != null
            } catch (e: Exception) {
                error = "thoughts.error.load"
            } finally {
                loading = false
            }
        }
    }

    /** Appends the next backend page without hiding or duplicating posts already on screen. */
    fun loadMore() {
        val cursor = nextCursor ?: return
        if (loading || loadingMore || !isLoggedIn()) return
        viewModelScope.launch {
            loadingMore = true
            loadMoreError = null
            try {
                val page = getFeed(cursor)
                val knownIds = posts.mapTo(mutableSetOf()) { it.id }
                val additions = page.posts.map { it.toUi() }.filter { knownIds.add(it.id) }
                posts = posts + additions

                // A repeated cursor would leave the control in an endless request loop.
                nextCursor = page.nextCursor
                    ?.takeIf { it.isNotBlank() && it != cursor }
                hasMore = nextCursor != null
            } catch (e: Exception) {
                loadMoreError = "thoughts.error.load"
            } finally {
                loadingMore = false
            }
        }
    }

    fun publish(text: String, onPublished: () -> Unit) {
        val content = text.trim()
        if (content.isBlank() || publishing) return
        if (content.length > MAX_SOCIAL_POST_LENGTH) {
            publishError = "thoughts.error.too_long"
            return
        }
        viewModelScope.launch {
            publishing = true
            publishError = null
            try {
                val published = publishThought(content).toUi()
                if (!localSocial.recordPublished(published.id)) {
                    error = "social.save_failed"
                }
                posts = listOf(published) + posts
                onPublished()
            } catch (e: Exception) {
                publishError = "thoughts.error.publish"
            } finally {
                publishing = false
            }
        }
    }

    fun like(id: String) {
        if (id in pendingLikes || !isLoggedIn()) return
        val index = posts.indexOfFirst { it.id == id }
        if (index < 0) return
        val previous = posts[index]
        val optimisticLiked = !previous.likedByMe
        posts = posts.toMutableList().also { updated ->
            updated[index] = previous.copy(
                likedByMe = optimisticLiked,
                likes = (previous.likes + if (optimisticLiked) 1 else -1).coerceAtLeast(0),
            )
        }
        pendingLikes += id
        viewModelScope.launch {
            try {
                val result = toggleLikeUseCase(id)
                posts = posts.map { post ->
                    if (post.id == id) {
                        post.copy(likedByMe = result.liked, likes = result.likeCount.coerceAtLeast(0))
                    } else {
                        post
                    }
                }
                if (!localSocial.setPostLiked(id, result.liked, source = "backend")) {
                    error = "social.save_failed"
                }
            } catch (e: Exception) {
                posts = posts.map { post -> if (post.id == id) previous else post }
                error = "thoughts.error.like"
            } finally {
                pendingLikes -= id
            }
        }
    }
}

/** Maps the backend post to the prototype's UI model. */
private fun DomainPost.toUi(): SocialPost = SocialPost(
    id = id,
    author = authorName,
    handle = authorHandle.takeIf { it.isNotBlank() }
        ?.let { if (it.startsWith("@")) it else "@$it" }
        .orEmpty(),
    avatarSeed = (id.hashCode() and 0x7fffffff) % 8,
    time = createdAt.substringAfter('T', "").take(5),
    text = text,
    likes = likeCount,
    comments = 0,
    tag = "",
    likedByMe = likedByMe,
)
