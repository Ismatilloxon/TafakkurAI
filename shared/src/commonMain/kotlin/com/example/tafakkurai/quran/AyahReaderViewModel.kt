package com.example.tafakkurai.quran

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One ayah as the reader draws it. */
data class AyahUiModel(
    val ayah: Ayah,
    val isRead: Boolean,
    val isSelected: Boolean,
    val isPlaying: Boolean,
)

data class AyahReaderUiState(
    val surah: Surah? = null,
    val ayahs: List<AyahUiModel> = emptyList(),
    val readCount: Int = 0,
    val reciter: Reciter = DEFAULT_RECITER,
    val reciters: List<Reciter> = RECITERS,
    val translation: TranslationEdition = DEFAULT_TRANSLATION,
    val translations: List<TranslationEdition> = TRANSLATIONS,
    /** The rasm the ayahs are drawn in — also decides the Basmala and the line height. */
    val mushaf: MushafEdition = DEFAULT_MUSHAF,
    val mushafs: List<MushafEdition> = MUSHAFS,
    /** False reads the surah in Arabic alone — no meaning under the ayahs. */
    val showTranslation: Boolean = true,
    val audio: AudioUiState = AudioUiState(),
    val downloads: PrefetchState = PrefetchState(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val progress: Float
        get() = surah?.let { if (it.ayahCount == 0) 0f else (readCount.toFloat() / it.ayahCount).coerceIn(0f, 1f) } ?: 0f

    /** True when the surah loaded but none of its text is available to show. */
    val isTextUnavailable: Boolean
        get() = !isLoading && error == null && ayahs.isNotEmpty() && ayahs.none { it.ayah.hasText }
}

sealed interface ReaderUiEvent {
    data class TapAyah(val ayahNumber: Int) : ReaderUiEvent
    data class ToggleRead(val ayahNumber: Int) : ReaderUiEvent
    data class SelectReciter(val reciterId: String) : ReaderUiEvent
    data class SelectTranslation(val translationId: String) : ReaderUiEvent
    data class SelectMushaf(val mushafId: String) : ReaderUiEvent
    data class SetShowTranslation(val on: Boolean) : ReaderUiEvent
    data class Seek(val fraction: Float) : ReaderUiEvent
    data object PlayPause : ReaderUiEvent
    data object Next : ReaderUiEvent
    data object Previous : ReaderUiEvent
    data object Retry : ReaderUiEvent
}

/**
 * The reader. Owns loading one surah's ayahs and the [AudioController] for it.
 *
 * Tapping an ayah does four things in one gesture — select, record it as read, move the
 * resume point, and start its recitation — and they are sequenced here rather than in the
 * composable so the same behaviour holds when audio is unavailable.
 */
class AyahReaderViewModel(
    private val surahNumber: Int,
    private val repository: QuranRepository,
    private val progress: QuranProgressStore,
    player: AyahAudioPlayer,
    private val cache: AyahAudioCache? = null,
) : ViewModel() {

    private val prefetch: AyahPrefetcher? = cache?.let { AyahPrefetcher(it, viewModelScope) }

    private val audio = AudioController(
        player = player,
        scope = viewModelScope,
        onAyahStarted = { surah, ayah ->
            progress.markRead(surah, ayah)
            // Remembered separately from reading progress so reopening the surah resumes
            // recitation at the ayah that was playing, not wherever the eye last was.
            progress.setLastListened(surah, ayah)
            // Downloading is driven by where the listener actually is, not by a timer.
            prefetch?.onAyahReached(ayah)
        },
        localUrlFor = { remote -> cache?.cachedUrl(remote) },
    )

    private var loaded by mutableStateOf<List<Ayah>>(emptyList())
    private var selected by mutableStateOf<Int?>(null)
    private var loading by mutableStateOf(true)
    private var failure by mutableStateOf<String?>(null)

    private val _state = MutableStateFlow(AyahReaderUiState())
    val state: StateFlow<AyahReaderUiState> = _state.asStateFlow()

    init {
        // Opening the reader is the explicit start of this exact learning scope. It creates
        // one replay-safe source event, which the integration projector turns into the
        // connected habit, Today row and quiz goal without this ViewModel knowing those stores.
        progress.startSurah(surahNumber)
        viewModelScope.launch {
            combine(
                snapshotFlow { snapshot() },
                audio.state,
                prefetch?.state ?: MutableStateFlow(PrefetchState()),
            ) { base, audioState, downloads ->
                base.copy(
                    audio = audioState,
                    downloads = downloads,
                    ayahs = base.ayahs.map { row ->
                        row.copy(
                            isPlaying = audioState.isPlaying &&
                                audioState.track?.surahNumber == surahNumber &&
                                audioState.track.ayahNumber == row.ayah.number &&
                                // The opening Basmala belongs to no ayah row.
                                !audioState.track.isBasmala,
                        )
                    },
                )
            }.collect { _state.value = it }
        }
        load()
        // Resume where the reader left off inside this surah.
        selected = progress.lastRead?.takeIf { it.surahNumber == surahNumber }?.ayahNumber
    }

    fun onEvent(event: ReaderUiEvent) {
        when (event) {
            is ReaderUiEvent.TapAyah -> tapAyah(event.ayahNumber)
            is ReaderUiEvent.ToggleRead -> progress.toggleRead(surahNumber, event.ayahNumber)
            is ReaderUiEvent.SelectReciter -> selectReciter(event.reciterId)
            is ReaderUiEvent.SelectTranslation -> {
                progress.setTranslation(event.translationId)
                // Re-read so every ayah carries text in the chosen language and script.
                load(keepSelection = true)
            }
            is ReaderUiEvent.SelectMushaf -> {
                progress.setMushaf(event.mushafId)
                // The Arabic itself comes from a different edition now — re-read it.
                load(keepSelection = true)
            }
            // No reload: the meaning is already loaded, so this only changes what is drawn.
            is ReaderUiEvent.SetShowTranslation -> progress.setShowTranslation(event.on)
            is ReaderUiEvent.Seek -> audio.seekToFraction(event.fraction)
            ReaderUiEvent.PlayPause -> audio.togglePlayPause()
            ReaderUiEvent.Next -> audio.next()
            ReaderUiEvent.Previous -> audio.previous()
            ReaderUiEvent.Retry -> load()
        }
    }

    /** Restores callbacks after another audio screen temporarily owned the shared player. */
    fun activateAudio() = audio.activate()

    override fun onCleared() {
        // The player is an app-wide singleton — stop this surah's playback but leave the
        // player itself alive for the next reader instance.
        audio.stop()
        prefetch?.cancel()
        super.onCleared()
    }

    private fun tapAyah(ayahNumber: Int) {
        selected = ayahNumber
        // Recorded first so reading progress is kept even when audio can't play.
        progress.markRead(surahNumber, ayahNumber)
        audio.playAyah(surahNumber, ayahNumber)
    }

    private fun selectReciter(reciterId: String) {
        progress.setReciter(reciterId)
        // The old voice's downloads can never be played from here again — deleting them
        // keeps the cache at one qari instead of growing by a whole voice per switch.
        cache?.keepOnly(progress.reciter)
        // Re-read so every ayah carries a url for the new qari, then re-arm the queue.
        load(keepSelection = true)
    }

    private fun load(keepSelection: Boolean = false) {
        viewModelScope.launch {
            loading = true
            failure = null
            try {
                val ayahs = repository.getAyahs(surahNumber)
                    loaded = ayahs
                    audio.setQueue(ayahs, progress.reciter)
                    // Downloads a chunk of recitation in the selected qari's voice only,
                    // starting where playback will — playback itself never waits for it.
                    prefetch?.open(
                        surahNumber = surahNumber,
                        reciter = progress.reciter,
                        fromAyah = progress.lastListenedOf(surahNumber) ?: 1,
                    )
                    // Arm the player at the resume ayah — pressing play continues from there.
                    progress.lastListenedOf(surahNumber)?.let { resumeAt ->
                        audio.prime(surahNumber, resumeAt)
                        if (!keepSelection) selected = resumeAt
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                loaded = emptyList()
                failure = "quran.reader.load_failed"
            }
            loading = false
            if (!keepSelection) selected = selected ?: progress.lastRead
                ?.takeIf { it.surahNumber == surahNumber }?.ayahNumber
        }
    }

    private fun snapshot(): AyahReaderUiState {
        val surah = surahOf(surahNumber)
        val current = selected
        return AyahReaderUiState(
            surah = surah,
            ayahs = loaded.map { ayah ->
                AyahUiModel(
                    ayah = ayah,
                    isRead = progress.isRead(surahNumber, ayah.number),
                    isSelected = current == ayah.number,
                    isPlaying = false, // filled in from the audio flow in the combine above
                )
            },
            readCount = progress.readCountOf(surahNumber),
            reciter = progress.reciter,
            translation = progress.translation,
            mushaf = progress.mushaf,
            showTranslation = progress.showTranslation,
            isLoading = loading,
            error = failure,
        )
    }
}
