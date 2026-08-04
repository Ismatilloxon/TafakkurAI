package com.example.tafakkurai.quran

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Progress of the chunk being downloaded right now. */
data class PrefetchState(
    val firstAyah: Int = 0,
    val lastAyah: Int = 0,
    val completed: Int = 0,
    val total: Int = 0,
    val isDownloading: Boolean = false,
    /**
     * True for the chunk fetched when the surah opens. The reader shows that one as a full
     * progress bar on the page; later chunks are quiet background work and only get the
     * thin bar in the player.
     */
    val isOpeningChunk: Boolean = false,
) {
    val fraction: Float get() = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    val isComplete: Boolean get() = total > 0 && completed >= total
}

/**
 * Downloads recitation a chunk of ayahs at a time so a surah keeps playing without the
 * network: [CHUNK_SIZE] ayahs on opening the surah, and the next chunk once the reader is
 * [TRIGGER_AT] ayahs into the current one — early enough that the fetch finishes before
 * playback runs off the end of what is stored.
 *
 * Files are fetched one at a time on purpose. Ten parallel requests would finish sooner
 * but compete with the ayah actually playing, which streams over the same connection.
 */
class AyahPrefetcher(
    private val downloader: AudioDownloader,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PrefetchState())
    val state: StateFlow<PrefetchState> = _state.asStateFlow()

    private var job: Job? = null
    private var surahNumber: Int = 0
    private var reciter: Reciter = DEFAULT_RECITER

    /** Highest ayah already covered by a finished or running chunk. */
    private var covered: Int = 0

    /**
     * Called when the reader opens a surah. Only ever fetches [reciter] — the qari selected
     * right now — never the other four, which would be four times the data for audio the
     * listener has not asked for.
     *
     * [fromAyah] is where recitation will actually start, so a surah resumed at ayah 40
     * downloads 40 onwards instead of an opening chunk playback would skip past.
     */
    fun open(surahNumber: Int, reciter: Reciter, fromAyah: Int = 1) {
        if (this.surahNumber == surahNumber && this.reciter.id == reciter.id && covered > 0) return
        cancel()
        this.surahNumber = surahNumber
        this.reciter = reciter
        covered = 0
        fetchFrom(fromAyah.coerceAtLeast(1), isOpeningChunk = true)
    }

    /**
     * Called as recitation advances. Once the reader is deep enough into the stored chunk,
     * the next one starts downloading behind them.
     */
    fun onAyahReached(ayahNumber: Int) {
        if (surahNumber == 0 || covered == 0) return
        val remaining = covered - ayahNumber
        if (remaining > CHUNK_SIZE - TRIGGER_AT) return
        if (covered >= ayahCountOf(surahNumber)) return // whole surah is already stored
        fetchFrom(covered + 1, isOpeningChunk = false)
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(isDownloading = false)
    }

    private fun fetchFrom(firstAyah: Int, isOpeningChunk: Boolean) {
        if (job?.isActive == true) return
        val ayahCount = ayahCountOf(surahNumber)
        if (ayahCount == 0 || firstAyah > ayahCount) return
        val lastAyah = minOf(firstAyah + CHUNK_SIZE - 1, ayahCount)
        val urls = (firstAyah..lastAyah).mapNotNull { n ->
            QuranAudioSource.urlFor(reciter, surahNumber, n)
        }
        // Claimed up front: a second trigger while this chunk runs must not re-queue it.
        covered = lastAyah

        _state.value = PrefetchState(
            firstAyah = firstAyah,
            lastAyah = lastAyah,
            completed = 0,
            total = urls.size,
            isDownloading = urls.isNotEmpty(),
            isOpeningChunk = isOpeningChunk,
        )

        job = scope.launch {
            var done = 0
            for (url in urls) {
                if (!isActive) return@launch
                downloader.download(url)
                done++
                _state.value = _state.value.copy(completed = done)
            }
            _state.value = _state.value.copy(isDownloading = false)
        }
    }

    companion object {
        /** Ayahs fetched per chunk. */
        const val CHUNK_SIZE: Int = 30

        /** Position inside the chunk at which the next one starts downloading. */
        const val TRIGGER_AT: Int = 20
    }
}
