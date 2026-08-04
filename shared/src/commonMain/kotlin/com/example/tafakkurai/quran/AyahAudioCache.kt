package com.example.tafakkurai.quran

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

/**
 * Where downloaded recitation files live. A port because only the platform knows its own
 * caches directory, and only it can turn a path into a url the media player accepts.
 *
 * Implementations must be safe to call from a background thread and must never throw —
 * a failed write means "not cached", which only costs a stream.
 */
interface AudioFileStore {

    /** Playable url ("file://…") for an already-downloaded file, or null. */
    fun localUrl(key: String): String?

    /** Writes the file and returns its playable url, or null if it could not be stored. */
    fun write(key: String, bytes: ByteArray): String?

    /** Total bytes held, for a future "clear downloads" control. */
    fun sizeBytes(): Long

    /** Deletes every stored file whose key does not start with [keyPrefix]. */
    fun removeOthers(keyPrefix: String)

    fun clear()
}

/** No storage: every ayah streams. The default so a platform without a store still plays. */
class NoOpAudioFileStore : AudioFileStore {
    override fun localUrl(key: String): String? = null
    override fun write(key: String, bytes: ByteArray): String? = null
    override fun sizeBytes(): Long = 0L
    override fun removeOthers(keyPrefix: String) = Unit
    override fun clear() = Unit
}

/**
 * Fetching one recitation file. Narrower than [AyahAudioCache] so [AyahPrefetcher] can be
 * driven without a network stack in tests, and so a future queued/metered downloader can
 * replace it without touching the chunking rules.
 */
interface AudioDownloader {
    /** True when the ayah is playable offline afterwards. Must not throw. */
    suspend fun download(remoteUrl: String): Boolean
}

/**
 * Downloads ayah recitations for offline playback and hands back local urls.
 *
 * It builds its own [HttpClient] from the shared engine rather than reusing the app's
 * authenticated client — that one attaches the user's Tafakkur bearer token to every
 * request, which must never be sent to the audio CDN.
 */
class AyahAudioCache(
    engine: HttpClientEngine,
    private val store: AudioFileStore,
) : AudioDownloader {
    private val client = HttpClient(engine) { expectSuccess = true }

    /** A local url when the file is on disk, else null — callers then stream the remote one. */
    fun cachedUrl(remoteUrl: String): String? = store.localUrl(keyOf(remoteUrl))

    fun isCached(remoteUrl: String): Boolean = cachedUrl(remoteUrl) != null

    /**
     * Keeps only [reciter]'s files. Called when the qari changes: the previous voice's
     * downloads can never be played again from the reader, so holding them would just
     * grow the cache by one full qari per switch.
     */
    fun keepOnly(reciter: Reciter) = store.removeOthers("${reciter.cdnFolder}_")

    /**
     * Fetches one file unless it is already stored. Returns true when the ayah is playable
     * offline afterwards. Never throws: a failed download is a slower next play, not an
     * error the reader has to show.
     */
    override suspend fun download(remoteUrl: String): Boolean {
        val key = keyOf(remoteUrl)
        if (store.localUrl(key) != null) return true
        return runCatching {
            val bytes = client.get(remoteUrl).bodyAsBytes()
            // A truncated body would be cached as a corrupt file and never re-fetched.
            if (bytes.size < MIN_PLAUSIBLE_BYTES) return false
            store.write(key, bytes) != null
        }.getOrDefault(false)
    }

    /**
     * "Alafasy_128kbps/002255.mp3" → "Alafasy_128kbps_002255.mp3": flat, collision-free
     * per qari, and readable when inspecting the cache directory by hand.
     */
    private fun keyOf(remoteUrl: String): String =
        remoteUrl.trimEnd('/').split("/").takeLast(2).joinToString("_").ifBlank { remoteUrl.hashCode().toString() }

    private companion object {
        /** An mp3 of an ayah is never this small; anything shorter is a CDN error page. */
        const val MIN_PLAUSIBLE_BYTES = 1024
    }
}
