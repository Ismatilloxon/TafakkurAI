package uz.tafakkur.core.feedback

/** Every failure category the app translates a raw throwable into. */
enum class AppErrorType {
    NO_INTERNET,
    TIMEOUT,
    BAD_REQUEST,       // 400
    UNAUTHORIZED,      // 401
    FORBIDDEN,         // 403
    NOT_FOUND,         // 404
    CONFLICT,          // 409
    VALIDATION,        // 422
    RATE_LIMITED,      // 429
    SERVER,            // 5xx
    PARSE,             // serialization / malformed response
    UNKNOWN,           // anything else
}

/**
 * A failure expressed in the app's own vocabulary rather than the network stack's.
 *
 * [messageKey] points at a user-friendly line in the string catalogue; the host resolves it
 * in the active language. [overrideMessage] carries a backend message *only* when it was
 * judged safe to show verbatim (see [NetworkErrorMapper]); otherwise it is null and the
 * generic localised line is used. [status], [code] and [cause] are kept for logging, not for
 * display.
 */
data class AppError(
    val type: AppErrorType,
    val messageKey: String,
    val overrideMessage: String? = null,
    val status: Int? = null,
    val code: String? = null,
    val cause: Throwable? = null,
) {
    companion object {
        // i18n keys — defined in every catalog (see CatalogEn/Ru/UzLatn/UzCyrl).
        const val KEY_NO_INTERNET = "feedback.error.no_internet"
        const val KEY_TIMEOUT = "feedback.error.timeout"
        const val KEY_BAD_REQUEST = "feedback.error.bad_request"
        const val KEY_UNAUTHORIZED = "feedback.error.unauthorized"
        const val KEY_FORBIDDEN = "feedback.error.forbidden"
        const val KEY_NOT_FOUND = "feedback.error.not_found"
        const val KEY_CONFLICT = "feedback.error.conflict"
        const val KEY_VALIDATION = "feedback.error.validation"
        const val KEY_RATE_LIMITED = "feedback.error.rate_limited"
        const val KEY_SERVER = "feedback.error.server"
        const val KEY_PARSE = "feedback.error.parse"
        const val KEY_UNKNOWN = "feedback.error.unknown"
    }
}

/**
 * The typed exception repositories throw once a raw failure has been mapped, logged and
 * (unless silenced) shown. Screens/ViewModels can `catch (e: AppException)` to react to a
 * specific [AppErrorType] — e.g. bounce to the login screen on [AppErrorType.UNAUTHORIZED] —
 * without ever parsing HTTP status codes themselves.
 */
class AppException(val error: AppError) :
    Exception(error.overrideMessage ?: error.messageKey, error.cause) {
    val type: AppErrorType get() = error.type
}
