package com.example.tafakkurai.quran

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Records every file written, standing in for the platform cache directory. */
private class FakeAudioFileStore : AudioFileStore {
    val files = mutableMapOf<String, ByteArray>()

    override fun localUrl(key: String): String? = if (key in files) "file:///cache/$key" else null

    override fun write(key: String, bytes: ByteArray): String? {
        files[key] = bytes
        return "file:///cache/$key"
    }

    override fun sizeBytes(): Long = files.values.sumOf { it.size.toLong() }

    override fun removeOthers(keyPrefix: String) {
        files.keys.filterNot { it.startsWith(keyPrefix) }.forEach { files.remove(it) }
    }

    override fun clear() = files.clear()
}

/** Every request answers with a plausible mp3 body unless [status] says otherwise. */
private fun cacheOver(
    store: FakeAudioFileStore,
    status: HttpStatusCode = HttpStatusCode.OK,
    onRequest: (String) -> Unit = {},
): AyahAudioCache {
    val engine = MockEngine { request ->
        onRequest(request.url.toString())
        respond(
            content = ByteArray(4096) { 1 },
            status = status,
            headers = headersOf("Content-Type", "audio/mpeg"),
        )
    }
    return AyahAudioCache(engine, store)
}

/** Records the urls asked for, with no network and no store behind it. */
private class FakeDownloader : AudioDownloader {
    val requested = mutableListOf<String>()
    override suspend fun download(remoteUrl: String): Boolean {
        requested += remoteUrl
        return true
    }
}

/**
 * Runs a prefetcher on the test scheduler. [advanceUntilIdle] after each action is what
 * lets a test assert on downloads that are, in the app, deliberately asynchronous.
 */
private fun withPrefetcher(downloader: AudioDownloader, block: suspend TestScope.(AyahPrefetcher) -> Unit) =
    runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        try {
            block(AyahPrefetcher(downloader, scope))
        } finally {
            scope.cancel()
        }
    }

class AyahAudioCacheTest {

    @Test
    fun a_downloaded_ayah_is_served_from_disk_afterwards() {
        val store = FakeAudioFileStore()
        val requested = mutableListOf<String>()
        val cache = cacheOver(store) { requested += it }
        val url = "https://everyayah.com/data/Alafasy_128kbps/002001.mp3"

        runTest {
            assertTrue(cache.download(url))
            assertEquals("file:///cache/Alafasy_128kbps_002001.mp3", cache.cachedUrl(url))

            // Already stored — a second request would be wasted bandwidth.
            assertTrue(cache.download(url))
            assertEquals(1, requested.size)
        }
    }

    @Test
    fun a_failed_download_leaves_nothing_cached() {
        val store = FakeAudioFileStore()
        val cache = cacheOver(store, status = HttpStatusCode.NotFound)

        runTest {
            assertFalse(cache.download("https://everyayah.com/data/Alafasy_128kbps/002001.mp3"))
            assertTrue(store.files.isEmpty())
        }
    }
}

class AudioCacheEvictionTest {

    @Test
    fun switching_qari_drops_the_previous_voices_downloads() {
        val store = FakeAudioFileStore()
        val cache = cacheOver(store)
        store.write("Alafasy_128kbps_002001.mp3", ByteArray(2048))
        store.write("Abdurrahmaan_As-Sudais_192kbps_002001.mp3", ByteArray(2048))

        cache.keepOnly(reciterOf("sudais"))

        assertEquals(setOf("Abdurrahmaan_As-Sudais_192kbps_002001.mp3"), store.files.keys)
    }
}

class AyahPrefetcherTest {

    @Test
    fun a_resumed_surah_downloads_from_where_playback_will_start() {
        val downloader = FakeDownloader()
        withPrefetcher(downloader) { prefetcher ->
            prefetcher.open(surahNumber = 2, reciter = DEFAULT_RECITER, fromAyah = 40)
            advanceUntilIdle()

            assertTrue(downloader.requested.first().endsWith("002040.mp3"))
            assertTrue(downloader.requested.last().endsWith("002069.mp3"))
            assertEquals(30, downloader.requested.size)
        }
    }

    @Test
    fun only_the_selected_qari_is_ever_requested() {
        val downloader = FakeDownloader()
        withPrefetcher(downloader) { prefetcher ->
            prefetcher.open(surahNumber = 114, reciter = reciterOf("ghamdi"))
            advanceUntilIdle()

            assertTrue(downloader.requested.all { it.contains("Ghamadi_40kbps") })
        }
    }

    @Test
    fun opening_a_surah_downloads_its_first_thirty_ayahs() {
        val downloader = FakeDownloader()
        withPrefetcher(downloader) { prefetcher ->
            prefetcher.open(surahNumber = 2, reciter = DEFAULT_RECITER)
            advanceUntilIdle()

            assertEquals(30, downloader.requested.size)
            assertTrue(downloader.requested.first().endsWith("Alafasy_128kbps/002001.mp3"))
            assertTrue(downloader.requested.last().endsWith("Alafasy_128kbps/002030.mp3"))
            val state = prefetcher.state.value
            assertEquals(1, state.firstAyah)
            assertEquals(30, state.lastAyah)
            assertEquals(30, state.completed)
            assertTrue(state.isOpeningChunk)
            assertTrue(state.isComplete)
            assertFalse(state.isDownloading)
        }
    }

    @Test
    fun a_short_surah_downloads_only_the_ayahs_it_has() {
        val downloader = FakeDownloader()
        withPrefetcher(downloader) { prefetcher ->
            prefetcher.open(surahNumber = 114, reciter = DEFAULT_RECITER) // An-Nas: 6 ayahs
            advanceUntilIdle()

            assertEquals(6, downloader.requested.size)
            assertEquals(6, prefetcher.state.value.lastAyah)
        }
    }

    @Test
    fun reaching_the_twentieth_ayah_fetches_the_next_chunk_in_the_background() {
        val downloader = FakeDownloader()
        withPrefetcher(downloader) { prefetcher ->
            prefetcher.open(surahNumber = 2, reciter = DEFAULT_RECITER)
            advanceUntilIdle()

            prefetcher.onAyahReached(19) // still deep inside what is stored
            advanceUntilIdle()
            assertEquals(30, downloader.requested.size)

            prefetcher.onAyahReached(20)
            advanceUntilIdle()
            assertEquals(60, downloader.requested.size)
            val state = prefetcher.state.value
            assertEquals(31, state.firstAyah)
            assertEquals(60, state.lastAyah)
            // Only the opening chunk gets the full-width bar; this one is background work.
            assertFalse(state.isOpeningChunk)
        }
    }

    @Test
    fun the_last_chunk_of_a_surah_stops_at_its_final_ayah() {
        val downloader = FakeDownloader()
        withPrefetcher(downloader) { prefetcher ->
            prefetcher.open(surahNumber = 108, reciter = DEFAULT_RECITER) // Al-Kawthar: 3
            advanceUntilIdle()
            prefetcher.onAyahReached(3)
            advanceUntilIdle()

            assertEquals(3, downloader.requested.size) // nothing beyond the surah requested
        }
    }

    @Test
    fun reopening_the_same_surah_does_not_download_it_again() {
        val downloader = FakeDownloader()
        withPrefetcher(downloader) { prefetcher ->
            prefetcher.open(surahNumber = 114, reciter = DEFAULT_RECITER)
            advanceUntilIdle()
            prefetcher.open(surahNumber = 114, reciter = DEFAULT_RECITER)
            advanceUntilIdle()

            assertEquals(6, downloader.requested.size)
        }
    }

    @Test
    fun changing_qari_refetches_in_the_new_voice() {
        val downloader = FakeDownloader()
        withPrefetcher(downloader) { prefetcher ->
            prefetcher.open(surahNumber = 114, reciter = DEFAULT_RECITER)
            advanceUntilIdle()
            prefetcher.open(surahNumber = 114, reciter = reciterOf("sudais"))
            advanceUntilIdle()

            assertTrue(downloader.requested.any { it.contains("Alafasy_128kbps") })
            assertTrue(downloader.requested.any { it.contains("Abdurrahmaan_As-Sudais_192kbps") })
        }
    }
}
