package uz.tafakkur.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Gates interactive API calls behind one safe liveness request.
 *
 * Render Free may suspend the backend after 15 minutes without inbound traffic and can take
 * about a minute to resume. Sending a mutation as the wake-up request would leave the client
 * unable to tell whether a timeout happened before or after the server committed it. This gate
 * retries only `GET /healthz`; the actual repository operation still runs exactly once.
 */
interface BackendReadiness {
    suspend fun awaitReady()
}

class KtorBackendReadiness(
    private val client: HttpClient,
) : BackendReadiness {
    private val gate = Mutex()
    private var lastReady: TimeMark? = null

    override suspend fun awaitReady() = gate.withLock {
        if (lastReady?.elapsedNow()?.let { it < FRESH_FOR } == true) return@withLock

        withTimeout(WARMUP_BUDGET_MILLIS) {
            var lastFailure: Throwable? = null
            repeat(ATTEMPTS) { attempt ->
                try {
                    client.get("healthz") {
                        timeout {
                            requestTimeoutMillis = WAKE_REQUEST_TIMEOUT_MILLIS
                            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                            socketTimeoutMillis = WAKE_REQUEST_TIMEOUT_MILLIS
                        }
                    }.bodyAsText()
                    lastReady = TimeSource.Monotonic.markNow()
                    return@withTimeout
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Throwable) {
                    lastFailure = failure
                    if (attempt < ATTEMPTS - 1) {
                        delay(RETRY_DELAYS_MILLIS[attempt])
                    }
                }
            }
            throw checkNotNull(lastFailure)
        }
    }

    private companion object {
        // Shorter than Render Free's 15-minute idle window. A stale gate is warmed again
        // before a mutation can become the request that wakes the service.
        val FRESH_FOR = 10.minutes
        const val ATTEMPTS = 3
        const val WARMUP_BUDGET_MILLIS = 90_000L
        const val WAKE_REQUEST_TIMEOUT_MILLIS = 75_000L
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
        val RETRY_DELAYS_MILLIS = longArrayOf(1_000L, 3_000L)
    }
}
