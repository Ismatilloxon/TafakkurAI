package uz.tafakkur.core.feedback

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import uz.tafakkur.network.ApiException

/** Turns any raw throwable coming out of the network stack into a typed [AppError]. */
interface NetworkErrorMapper {
    fun map(throwable: Throwable): AppError
}

/**
 * The single place that decides what each failure *means* and what the user should read.
 *
 * Order matters: the most specific, most reliable signals (our own [ApiException] status,
 * Ktor's timeout types) are checked first; loose connectivity/serialisation heuristics —
 * which have to work across the OkHttp and Darwin engines without referencing platform
 * classes — come last so they never mask a precise match.
 */
class DefaultNetworkErrorMapper : NetworkErrorMapper {

    override fun map(throwable: Throwable): AppError = when (throwable) {
        is ApiException -> fromHttpStatus(throwable)

        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        -> AppError(AppErrorType.TIMEOUT, AppError.KEY_TIMEOUT, cause = throwable)

        is SerializationException ->
            AppError(AppErrorType.PARSE, AppError.KEY_PARSE, cause = throwable)

        else -> fromHeuristics(throwable)
    }

    /** Maps the backend's HTTP status to a category, borrowing its message only when safe. */
    private fun fromHttpStatus(e: ApiException): AppError {
        val type = when (e.status) {
            400 -> AppErrorType.BAD_REQUEST
            401 -> AppErrorType.UNAUTHORIZED
            403 -> AppErrorType.FORBIDDEN
            404 -> AppErrorType.NOT_FOUND
            409 -> AppErrorType.CONFLICT
            422 -> AppErrorType.VALIDATION
            429 -> AppErrorType.RATE_LIMITED
            in 500..599 -> AppErrorType.SERVER
            else -> AppErrorType.UNKNOWN
        }
        val key = when (type) {
            AppErrorType.BAD_REQUEST -> AppError.KEY_BAD_REQUEST
            AppErrorType.UNAUTHORIZED -> AppError.KEY_UNAUTHORIZED
            AppErrorType.FORBIDDEN -> AppError.KEY_FORBIDDEN
            AppErrorType.NOT_FOUND -> AppError.KEY_NOT_FOUND
            AppErrorType.CONFLICT -> AppError.KEY_CONFLICT
            AppErrorType.VALIDATION -> AppError.KEY_VALIDATION
            AppErrorType.RATE_LIMITED -> AppError.KEY_RATE_LIMITED
            AppErrorType.SERVER -> AppError.KEY_SERVER
            else -> AppError.KEY_UNKNOWN
        }
        // Validation/conflict/bad-request bodies are usually written for the end user
        // ("Handle already taken"). Everything else may leak internals, so it is dropped in
        // favour of the generic localised line.
        val override = e.message
            .takeIf { type in USER_FACING_STATUSES && isUserFriendly(it) }
        return AppError(type, key, overrideMessage = override, status = e.status, code = e.code, cause = e)
    }

    /** Cross-engine fallback: classify by class name and message when there's no typed hook. */
    private fun fromHeuristics(t: Throwable): AppError {
        val hay = ((t::class.simpleName ?: "") + " " + (t.message ?: "")).lowercase()
        return when {
            "timeout" in hay || "timed out" in hay ->
                AppError(AppErrorType.TIMEOUT, AppError.KEY_TIMEOUT, cause = t)

            listOf(
                "unknownhost", "unresolvedaddress", "unable to resolve host",
                "connectexception", "failed to connect", "network is unreachable",
                "no address associated", "not connected to the internet", "sockettimeout",
                "connection reset", "connection refused", "software caused connection abort",
            ).any { it in hay } ->
                AppError(AppErrorType.NO_INTERNET, AppError.KEY_NO_INTERNET, cause = t)

            "serialization" in hay || "jsonconvert" in hay || "jsondecoding" in hay ->
                AppError(AppErrorType.PARSE, AppError.KEY_PARSE, cause = t)

            else -> AppError(AppErrorType.UNKNOWN, AppError.KEY_UNKNOWN, cause = t)
        }
    }

    /**
     * A cheap "is this fit to show a user" check: present, not obviously a machine token
     * (no snake_case codes, no stack traces / SQL), and a sane length.
     */
    private fun isUserFriendly(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        if (message.length > 160) return false
        val lower = message.lowercase()
        val looksTechnical = listOf(
            "exception", "sql", "null pointer", "stacktrace", "panic",
            "runtime", "0x", "http_error", "internal",
        ).any { it in lower }
        val looksLikeCode = message.matches(Regex("^[a-z0-9_]+$")) // e.g. "invalid_request"
        return !looksTechnical && !looksLikeCode
    }

    private companion object {
        val USER_FACING_STATUSES = setOf(
            AppErrorType.VALIDATION,
            AppErrorType.CONFLICT,
            AppErrorType.BAD_REQUEST,
        )
    }
}
