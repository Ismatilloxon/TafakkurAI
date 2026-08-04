package uz.tafakkur.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import uz.tafakkur.data.dto.RefreshRequestDto
import uz.tafakkur.data.dto.RefreshResponseDto

/** JSON config shared by the client and manual (de)serialization. */
val appJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Builds the configured Ktor client. The [engine] is injected per platform
 * (OkHttp on Android, Darwin on iOS) so this stays in commonMain.
 */
fun createHttpClient(
    engine: HttpClientEngine,
    config: ApiConfig,
    tokens: TokenProvider,
): HttpClient = HttpClient(engine) {
    expectSuccess = true

    install(ContentNegotiation) { json(appJson) }
    install(Logging) { level = LogLevel.INFO }

    // Bounds every request so a dead connection surfaces as a Timeout the error mapper can
    // classify, instead of hanging the loader forever. Streaming calls (the coach SSE) opt
    // out per-request with `timeout { requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS }`.
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }

    defaultRequest {
        url(config.baseUrl)
        contentType(ContentType.Application.Json)
    }

    install(Auth) {
        bearer {
            loadTokens {
                tokens.accessToken()?.let { BearerTokens(it, tokens.refreshToken().orEmpty()) }
            }
            refreshTokens {
                val rt = tokens.refreshToken() ?: return@refreshTokens null
                try {
                    val resp: RefreshResponseDto = client.post("v1/auth/refresh") {
                        markAsRefreshTokenRequest()
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequestDto(rt))
                    }.body()
                    tokens.update(resp.accessToken, rt)
                    BearerTokens(resp.accessToken, rt)
                } catch (e: Exception) {
                    tokens.clear()
                    null
                }
            }
            // Attach the bearer everywhere except the public auth endpoints.
            sendWithoutRequest { request -> !request.url.encodedPath.contains("/auth/") }
        }
    }

    // Map every non-2xx into a typed ApiException carrying the backend envelope.
    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, _ ->
            val responseException = cause as? ResponseException ?: return@handleResponseExceptionWithRequest
            val response = responseException.response
            // Read the payload once as text, then parse the envelope from it — this keeps the
            // raw body available for logging (a second body<T>() read would fail).
            val raw = try {
                response.bodyAsText()
            } catch (e: Exception) {
                null
            }
            val envelope = raw?.let {
                try {
                    appJson.decodeFromString(ErrorEnvelopeDto.serializer(), it)
                } catch (e: Exception) {
                    null
                }
            }
            throw ApiException(
                code = envelope?.error?.code ?: "http_error",
                message = envelope?.error?.message ?: (cause.message ?: "request failed"),
                status = response.status.value,
                body = raw,
            )
        }
    }
}
