package uz.tafakkur.core.feedback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The three feedback flavours the app standardises on. */
enum class SnackbarType { SUCCESS, WARNING, ERROR }

/**
 * One feedback message.
 *
 * Text can be supplied two ways so the non-UI layers (repositories, the error mapper) stay
 * free of Compose: pass [textKey] to have the host resolve it against the active language,
 * or pass a ready [text] for messages that are already localised or come user-friendly from
 * the backend. The host prefers [text] when both are present.
 */
data class SnackbarMessage(
    val id: Long,
    val type: SnackbarType,
    val textKey: String? = null,
    val text: String? = null,
    val durationMs: Long = defaultDurationFor(type),
) {
    companion object {
        const val DURATION_SHORT = 3000L
        const val DURATION_MEDIUM = 4000L
        const val DURATION_LONG = 5000L

        fun defaultDurationFor(type: SnackbarType): Long =
            if (type == SnackbarType.ERROR) DURATION_LONG else DURATION_MEDIUM
    }
}

/**
 * The app-wide feedback bus. Anything — a repository, a use-case, the network layer or a
 * screen — can post a message without knowing how (or where) it is rendered, which is what
 * keeps the snackbar globally reachable yet decoupled from any single screen.
 *
 * Messages are shown one at a time; extra ones queue so a burst (e.g. two failures at once)
 * is read in order instead of overwriting each other. The Compose host drives timing and
 * calls [dismiss] when a message's time is up or the user taps close.
 */
interface SnackbarController {
    /** The message currently on screen, or `null` when nothing is showing. */
    val current: StateFlow<SnackbarMessage?>

    fun show(
        type: SnackbarType,
        textKey: String? = null,
        text: String? = null,
        durationMs: Long = SnackbarMessage.defaultDurationFor(type),
    )

    /** Removes [id] if it is the one showing and promotes the next queued message. */
    fun dismiss(id: Long)

    fun success(textKey: String? = null, text: String? = null) =
        show(SnackbarType.SUCCESS, textKey, text)

    fun warning(textKey: String? = null, text: String? = null) =
        show(SnackbarType.WARNING, textKey, text)

    fun error(textKey: String? = null, text: String? = null) =
        show(SnackbarType.ERROR, textKey, text)
}

/**
 * Default queueing implementation.
 *
 * All mutation runs inside [scope] under a [Mutex], so [show]/[dismiss] are safe from any
 * thread while the queue stays consistent. Consecutive duplicates are collapsed so a retry
 * loop or two identical failures don't stack the same line twice.
 */
class DefaultSnackbarController(private val scope: CoroutineScope) : SnackbarController {

    private val mutex = Mutex()
    private val queue = ArrayDeque<SnackbarMessage>()
    private var nextId = 1L

    private val _current = MutableStateFlow<SnackbarMessage?>(null)
    override val current: StateFlow<SnackbarMessage?> = _current.asStateFlow()

    override fun show(type: SnackbarType, textKey: String?, text: String?, durationMs: Long) {
        scope.launch {
            mutex.withLock {
                val message = SnackbarMessage(
                    id = nextId++,
                    type = type,
                    textKey = textKey,
                    text = text,
                    durationMs = durationMs,
                )
                if (isDuplicate(message)) return@withLock
                if (_current.value == null) _current.value = message else queue.addLast(message)
            }
        }
    }

    override fun dismiss(id: Long) {
        scope.launch {
            mutex.withLock {
                if (_current.value?.id == id) {
                    _current.value = queue.removeFirstOrNull()
                } else {
                    queue.removeAll { it.id == id }
                }
            }
        }
    }

    /** Same type + same content as what is showing or last queued. */
    private fun isDuplicate(candidate: SnackbarMessage): Boolean {
        val last = queue.lastOrNull() ?: _current.value ?: return false
        return last.type == candidate.type &&
            last.textKey == candidate.textKey &&
            last.text == candidate.text
    }
}
