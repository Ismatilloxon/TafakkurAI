package com.example.tafakkurai.quran

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PlaybackState { IDLE, LOADING, PLAYING, PAUSED, ERROR }

/**
 * What happens when an ayah finishes reciting.
 *
 * [OFF] rolls into the next ayah — how the reader recites a surah. [AYAH] replays the same
 * ayah, which is the memorization drill: hearing one ayah several times over is what fixes
 * it. [RANGE] recites a run of ayahs and returns to its start, for joining ayahs that are
 * each known alone but not yet in sequence.
 */
enum class RepeatMode { OFF, AYAH, RANGE }

data class AudioTrack(
    val surahNumber: Int,
    val surahName: String,
    val ayahNumber: Int,
    val url: String,
    val reciterName: String,
    /**
     * True while the opening Basmala is reciting, ahead of [ayahNumber] 1. It is not an
     * ayah of this surah, so it is never marked read and never highlights an ayah row.
     */
    val isBasmala: Boolean = false,
)

data class AudioUiState(
    val track: AudioTrack? = null,
    val playback: PlaybackState = PlaybackState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val isSupported: Boolean = true,
    val repeat: RepeatMode = RepeatMode.OFF,
    /** Completed plays of the current ayah or range, for a "2 / 3" readout. */
    val repeatLap: Int = 0,
    val repeatTarget: Int = 1,
) {
    val isActive: Boolean get() = track != null
    val isPlaying: Boolean get() = playback == PlaybackState.PLAYING
    val isLoading: Boolean get() = playback == PlaybackState.LOADING
    val progress: Float get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * All the playback rules that must behave identically on both platforms: which ayah is
 * selected, what tapping a different ayah does to the one already playing, how next and
 * previous clamp at the ends of a surah, and when an ayah counts as read.
 *
 * The platform [AyahAudioPlayer] only decodes bytes; every decision lives here, so the
 * behaviour is shared and testable rather than written twice.
 */
class AudioController(
    private val player: AyahAudioPlayer,
    private val scope: CoroutineScope,
    /** Called when playback of an ayah begins — used to record reading progress. */
    private val onAyahStarted: (surah: Int, ayah: Int) -> Unit = { _, _ -> },
    /**
     * Maps a CDN url to a downloaded copy when [AyahPrefetcher] has already fetched it.
     * Returning null simply streams, so playback never depends on the download finishing.
     */
    private val localUrlFor: (String) -> String? = { null },
) {
    private val _state = MutableStateFlow(AudioUiState(isSupported = player.isSupported))
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

    /** Ayahs of the surah currently loaded, so next/previous know their bounds. */
    private var queue: List<Ayah> = emptyList()
    private var reciter: Reciter = DEFAULT_RECITER
    private var ticker: Job? = null

    /** Url handed to the player. Null while a track is only primed, never decoded. */
    private var loadedUrl: String? = null

    // ── Repeat ───────────────────────────────────────────────────────────────
    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var repeatTarget: Int = 1
    private var repeatLap: Int = 0
    private var rangeStart: Int = 1
    private var rangeEnd: Int = 1

    init {
        player.listener = object : AyahAudioPlayerListener {
            override fun onReady(durationMs: Long) {
                _state.update { it.copy(durationMs = durationMs, playback = PlaybackState.PLAYING, error = null) }
                player.play()
                startTicker()
                publishNowPlaying()
            }

            override fun onCompleted() {
                stopTicker()
                // A repeat still owing laps takes the completion for itself.
                if (repeatOnComplete()) return
                // Continuous recitation: roll into the next ayah, and stop cleanly at the
                // end of the surah rather than looping back to the start.
                if (!advance(1)) {
                    _state.update {
                        it.copy(playback = PlaybackState.PAUSED, positionMs = it.durationMs)
                    }
                }
            }

            override fun onError(message: String) {
                stopTicker()
                _state.update { it.copy(playback = PlaybackState.ERROR, error = message) }
                publishNowPlaying()
            }

            // ── System controls ──────────────────────────────────────────────
            // The OS has already applied these to the audio itself; the controller only
            // catches up its own state so the in-app bar can never disagree with the
            // lock screen.

            override fun onExternalPlayPause(isPlaying: Boolean) {
                val playback = _state.value.playback
                if (playback == PlaybackState.IDLE || playback == PlaybackState.ERROR) return
                if (isPlaying) {
                    _state.update { it.copy(playback = PlaybackState.PLAYING) }
                    startTicker()
                } else {
                    stopTicker()
                    _state.update { it.copy(playback = PlaybackState.PAUSED) }
                }
                publishNowPlaying()
            }

            override fun onExternalNext() {
                next()
            }

            override fun onExternalPrevious() {
                previous()
            }
        }
    }

    /** Called whenever the reader loads a surah or the user changes qari. */
    fun setQueue(ayahs: List<Ayah>, reciter: Reciter) {
        queue = ayahs
        this.reciter = reciter
        // A primed track holds a url for the old qari; re-point it at the new one.
        val track = _state.value.track
        if (loadedUrl == null && track != null) {
            val url = urlFor(track.surahNumber, track.ayahNumber) ?: return
            _state.update { it.copy(track = track.copy(url = url, reciterName = reciter.name)) }
        }
    }

    /**
     * Arms the repeat behaviour applied when an ayah finishes.
     *
     * @param times total plays, not extra ones — `times = 3` recites an ayah three times.
     * @param from first ayah of a [RepeatMode.RANGE] loop; ignored by the other modes.
     * @param to last ayah of the loop, defaulting to a single-ayah range.
     */
    fun setRepeat(mode: RepeatMode, times: Int = 1, from: Int = 1, to: Int = from) {
        repeatMode = mode
        repeatTarget = times.coerceAtLeast(1)
        rangeStart = minOf(from, to).coerceAtLeast(1)
        rangeEnd = maxOf(from, to).coerceAtLeast(rangeStart)
        repeatLap = 0
        _state.update { it.copy(repeat = mode, repeatLap = 0, repeatTarget = repeatTarget) }
    }

    /**
     * Points the player bar at an ayah without decoding anything — how a reopened surah
     * offers to resume where recitation stopped. Nothing is fetched until play is pressed,
     * and it never overrides a track that is already loaded.
     */
    fun prime(surahNumber: Int, ayahNumber: Int) {
        if (_state.value.track != null) return
        val url = urlFor(surahNumber, ayahNumber) ?: return
        loadedUrl = null
        _state.value = AudioUiState(
            track = AudioTrack(
                surahNumber,
                surahOf(surahNumber)?.nameEnglish ?: "Surah $surahNumber",
                ayahNumber,
                url,
                reciter.name,
            ),
            playback = PlaybackState.PAUSED,
            isSupported = player.isSupported,
            repeat = repeatMode,
            repeatTarget = repeatTarget,
        )
        publishNowPlaying()
    }

    /**
     * Plays one ayah. Tapping the ayah that is already playing toggles pause instead of
     * restarting it — otherwise a mis-tap loses your place in a long recitation.
     *
     * Starting a surah at its first ayah recites the Basmala first, the way the surah is
     * opened aloud. [basmala] is how the Basmala's own completion asks for ayah 1 without
     * the opening being inserted a second time.
     */
    fun playAyah(surahNumber: Int, ayahNumber: Int, basmala: Boolean = showsBasmala(surahNumber)) {
        val current = _state.value.track
        if (current != null && current.surahNumber == surahNumber &&
            current.ayahNumber == ayahNumber && !current.isBasmala
        ) {
            togglePlayPause()
            return
        }
        if (basmala && ayahNumber == 1) {
            playBasmala(surahNumber)
            return
        }
        val url = urlFor(surahNumber, ayahNumber)
        if (url == null) {
            _state.update {
                it.copy(playback = PlaybackState.ERROR, error = "No audio for this ayah")
            }
            return
        }
        start(
            AudioTrack(
                surahNumber = surahNumber,
                surahName = surahOf(surahNumber)?.nameEnglish ?: "Surah $surahNumber",
                ayahNumber = ayahNumber,
                url = url,
                reciterName = reciter.name,
            ),
        )
    }

    /**
     * The Basmala in the selected qari's voice. Al-Fatihah's first ayah *is* the Basmala,
     * so it is the same recording — no separate file and no second CDN to depend on.
     */
    private fun playBasmala(surahNumber: Int) {
        val remote = QuranAudioSource.urlFor(reciter, 1, 1)
        val url = remote?.let { localUrlFor(it) ?: it }
        if (url == null) {
            // Nothing to open with; go straight to the ayah rather than failing the tap.
            playAyah(surahNumber, 1, basmala = false)
            return
        }
        start(
            AudioTrack(
                surahNumber = surahNumber,
                surahName = surahOf(surahNumber)?.nameEnglish ?: "Surah $surahNumber",
                ayahNumber = 1,
                url = url,
                reciterName = reciter.name,
                isBasmala = true,
            ),
        )
    }

    /** Loads a track and reports it — the one path into playback for ayahs and Basmala. */
    private fun start(track: AudioTrack) {
        stopTicker()
        player.stop()
        _state.value = AudioUiState(
            track = track,
            playback = PlaybackState.LOADING,
            isSupported = player.isSupported,
            repeat = repeatMode,
            repeatLap = repeatLap,
            repeatTarget = repeatTarget,
        )
        // The Basmala is not an ayah of this surah, so it must not count as one read.
        if (!track.isBasmala) onAyahStarted(track.surahNumber, track.ayahNumber)
        loadedUrl = track.url
        // Before the load so the system notification names the right ayah from the start.
        publishNowPlaying()
        player.load(track.url)
    }

    fun togglePlayPause() {
        when (_state.value.playback) {
            PlaybackState.PLAYING -> {
                player.pause()
                stopTicker()
                _state.update { it.copy(playback = PlaybackState.PAUSED) }
                publishNowPlaying()
            }
            PlaybackState.PAUSED -> {
                val track = _state.value.track ?: return
                // Resuming at the top of a surah still opens with the Basmala.
                if (loadedUrl == null && !track.isBasmala && track.ayahNumber == 1 &&
                    showsBasmala(track.surahNumber)
                ) {
                    playBasmala(track.surahNumber)
                    return
                }
                // A primed track has nothing decoded yet — load it, which starts playback.
                if (loadedUrl != track.url) {
                    _state.update { it.copy(playback = PlaybackState.LOADING, error = null) }
                    onAyahStarted(track.surahNumber, track.ayahNumber)
                    loadedUrl = track.url
                    publishNowPlaying()
                    player.load(track.url)
                    return
                }
                player.play()
                _state.update { it.copy(playback = PlaybackState.PLAYING) }
                startTicker()
                publishNowPlaying()
            }
            PlaybackState.ERROR -> _state.value.track?.let { retry(it) }
            else -> Unit
        }
    }

    fun next(): Boolean = advance(1)

    fun previous(): Boolean = advance(-1)

    /** Seek by fraction of the track, which is what a progress bar drag gives us. */
    fun seekToFraction(fraction: Float) {
        val duration = _state.value.durationMs
        if (duration <= 0L) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
        publishNowPlaying()
    }

    fun stop() {
        stopTicker()
        player.stop()
        loadedUrl = null
        // The repeat *setting* survives a stop — only the laps counted against it reset,
        // so leaving and reopening a drill does not silently turn repeat off.
        repeatLap = 0
        _state.value = AudioUiState(
            isSupported = player.isSupported,
            repeat = repeatMode,
            repeatTarget = repeatTarget,
        )
    }

    fun release() {
        stopTicker()
        player.listener = null
        player.release()
    }

    /**
     * Consumes a finished ayah when a repeat still owes laps. True means playback has been
     * restarted here and the caller must not advance.
     *
     * The replay goes through [start] rather than [playAyah] because playAyah reads a second
     * request for the ayah already playing as a pause toggle — right for a tap, wrong for a
     * drill, which would stop dead on its second lap.
     */
    private fun repeatOnComplete(): Boolean {
        val track = _state.value.track ?: return false
        // The opening Basmala is not part of the drill: it plays once and hands over to ayah 1.
        if (track.isBasmala) return false
        return when (repeatMode) {
            RepeatMode.OFF -> false
            RepeatMode.AYAH -> {
                repeatLap++
                if (repeatLap >= repeatTarget) {
                    repeatLap = 0
                    false
                } else {
                    start(track)
                    true
                }
            }
            RepeatMode.RANGE -> {
                // Mid-range: let the normal advance walk to the next ayah of the run.
                if (track.ayahNumber < rangeEnd) return false
                repeatLap++
                if (repeatLap >= repeatTarget) {
                    repeatLap = 0
                    false
                } else {
                    // A one-ayah range lands back on the ayah that just played.
                    if (rangeStart == track.ayahNumber) start(track)
                    else playAyah(track.surahNumber, rangeStart, basmala = false)
                    true
                }
            }
        }
    }

    /** Moves [delta] ayahs within the loaded surah. False when that would leave it. */
    private fun advance(delta: Int): Boolean {
        val track = _state.value.track ?: return false
        // The Basmala sits just before ayah 1: forward from it opens the surah proper,
        // and there is nothing before it to go back to.
        if (track.isBasmala) {
            if (delta <= 0) return false
            playAyah(track.surahNumber, 1, basmala = false)
            return true
        }
        val target = track.ayahNumber + delta
        val last = if (queue.isNotEmpty()) queue.size else ayahCountOf(track.surahNumber)
        if (target < 1 || target > last) return false
        playAyah(track.surahNumber, target, basmala = false)
        return true
    }

    /**
     * Mirrors the current track into the OS player. Called after every state transition
     * and on each position tick, so the lock screen's scrubber tracks the recitation.
     */
    private fun publishNowPlaying() {
        val state = _state.value
        val track = state.track ?: return
        val last = if (queue.isNotEmpty()) queue.size else ayahCountOf(track.surahNumber)
        player.updateNowPlaying(
            NowPlaying(
                title = if (track.isBasmala) "${track.surahName} · Bismillah"
                else "${track.surahName} · Ayah ${track.ayahNumber}",
                artist = track.reciterName,
                album = track.surahName,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                hasNext = track.isBasmala || track.ayahNumber < last,
                hasPrevious = !track.isBasmala && track.ayahNumber > 1,
            ),
        )
    }

    private fun urlFor(surahNumber: Int, ayahNumber: Int): String? {
        val remote = queue.firstOrNull { it.number == ayahNumber && it.surahId == surahNumber }?.audioUrl
            ?: QuranAudioSource.urlFor(reciter, surahNumber, ayahNumber)
            ?: return null
        // Downloaded copy first: it starts instantly and works with no connection at all.
        return localUrlFor(remote) ?: remote
    }

    private fun retry(track: AudioTrack) {
        _state.update { it.copy(playback = PlaybackState.LOADING, error = null) }
        loadedUrl = track.url
        player.load(track.url)
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive) {
                val position = player.positionMs()
                val duration = player.durationMs()
                _state.update {
                    if (it.playback == PlaybackState.PLAYING) {
                        it.copy(
                            positionMs = position,
                            durationMs = if (duration > 0L) duration else it.durationMs,
                        )
                    } else {
                        it
                    }
                }
                publishNowPlaying()
                delay(POLL_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val POLL_MS = 250L
    }
}
