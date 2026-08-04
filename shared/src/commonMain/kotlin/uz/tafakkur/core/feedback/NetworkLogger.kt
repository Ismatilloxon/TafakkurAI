package uz.tafakkur.core.feedback

import uz.tafakkur.network.ApiException

/**
 * Records failed requests. Kept behind an interface so a platform can bind a richer sink
 * (Android `Log`, os_log, Crashlytics, …) without the rest of the app changing; the default
 * prints, which surfaces in Logcat and the Xcode console alike.
 */
interface NetworkLogger {
    fun logFailure(call: CallInfo, error: AppError, cause: Throwable)
}

/**
 * Emits one structured line per failure with everything asked for: endpoint, method, status,
 * error code, exception type, message and — when the server returned one — the response body.
 */
class PrintlnNetworkLogger : NetworkLogger {

    override fun logFailure(call: CallInfo, error: AppError, cause: Throwable) {
        val status = error.status?.toString() ?: "-"
        val code = error.code ?: error.type.name
        val body = (cause as? ApiException)?.body?.let { trim(it) }
        val line = buildString {
            append("[Network] ✗ ${call.method} ${call.path}")
            append(" | status=").append(status)
            append(" | code=").append(code)
            append(" | type=").append(cause::class.simpleName ?: "Throwable")
            append(" | message=").append(cause.message ?: "-")
            if (body != null) append(" | body=").append(body)
        }
        println(line)
    }

    private fun trim(body: String): String {
        val flat = body.replace('\n', ' ').trim()
        return if (flat.length > MAX_BODY) flat.take(MAX_BODY) + "…" else flat
    }

    private companion object {
        const val MAX_BODY = 500
    }
}
