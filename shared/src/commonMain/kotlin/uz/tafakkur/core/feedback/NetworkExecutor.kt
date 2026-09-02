package uz.tafakkur.core.feedback

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/** Identifies a call for logging: the HTTP verb and the relative path. */
data class CallInfo(val method: String, val path: String)

/**
 * Marks a coroutine subtree as "silent": any [NetworkExecutor.call] running under it skips
 * the loader and both snackbars but is still mapped, logged and retried normally.
 *
 * This is how best-effort background work (launch-time sync, the outbox drain, silent
 * refreshes) reuses the exact same call path as interactive work without spamming the user
 * with loaders or "No internet" toasts when the app simply started offline.
 */
class SilentFeedback : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<SilentFeedback>
}

/** Runs [block] with feedback suppressed (see [SilentFeedback]). */
suspend fun <T> silently(block: suspend CoroutineScope.() -> T): T =
    withContext(SilentFeedback(), block)

/**
 * The one wrapper every repository call goes through.
 *
 * Responsibilities, in order:
 *  1. Reference-count the global loader for the whole call — including body parsing, so the
 *     loader reflects real work and concurrent calls keep it up until the last returns.
 *  3. On success, optionally post a success snackbar (only for meaningful mutations).
 *  4. On failure, map → log → (unless silent) show the error snackbar, then rethrow a typed
 *     [AppException] so callers still know the operation failed.
 *
 * [CancellationException] is always rethrown untouched — a cancelled coroutine is not an
 * error and must never light up the loader or a toast.
 */
class NetworkExecutor(
    private val loading: LoadingController,
    private val snackbar: SnackbarController,
    private val mapper: NetworkErrorMapper,
    private val logger: NetworkLogger,
) {

    suspend fun <T> call(
        call: CallInfo,
        showLoader: Boolean = true,
        reportErrors: Boolean = true,
        successKey: String? = null,
        block: suspend () -> T,
    ): T {
        val silent = coroutineContext[SilentFeedback] != null
        val useLoader = showLoader && !silent

        if (useLoader) loading.begin()
        try {
            val result = block()
            if (successKey != null && !silent) snackbar.success(textKey = successKey)
            return result
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            val error = mapper.map(t)
            logger.logFailure(call, error, t)
            if (reportErrors && !silent) {
                snackbar.error(textKey = error.messageKey, text = error.overrideMessage)
            }
            throw AppException(error)
        } finally {
            if (useLoader) loading.end()
        }
    }
}
