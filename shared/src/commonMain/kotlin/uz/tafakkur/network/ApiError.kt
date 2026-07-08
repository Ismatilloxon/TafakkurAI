package uz.tafakkur.network

import kotlinx.serialization.Serializable

/** Mirrors the backend error envelope: {"error": {"code","message"}}. */
@Serializable
data class ErrorEnvelopeDto(val error: ApiErrorDto = ApiErrorDto())

@Serializable
data class ApiErrorDto(val code: String = "", val message: String = "")

/** Thrown for any non-2xx response, carrying the backend's code + HTTP status. */
class ApiException(
    val code: String,
    override val message: String,
    val status: Int,
) : Exception(message)

/** One decoded Server-Sent Events frame (used by the coach stream). */
data class SseFrame(val event: String, val data: String)
