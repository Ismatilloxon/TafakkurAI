package com.example.tafakkurai.quran

/**
 * Platform audio port.
 *
 * Deliberately an interface rather than an `expect class`: shared code then compiles and
 * behaves correctly even before a platform registers a real player, and a build that has
 * no implementation degrades to a clearly-labelled "audio unavailable" state instead of
 * failing to link. Android binds `AndroidAyahAudioPlayer`, iOS binds `IosAyahAudioPlayer`.
 */
interface AyahAudioPlayer {

    /** False for [NoOpAyahAudioPlayer] — the UI shows the unavailable state instead of dead controls. */
    val isSupported: Boolean

    /**
     * What the lock screen, notification shade and CarPlay/Android Auto should show for
     * the current recitation. Called whenever the track or playback state changes, so a
     * platform can keep the system player in step without knowing about ayahs at all.
     * A platform with no system player ignores it.
     */
    fun updateNowPlaying(info: NowPlaying) = Unit

    /** Loads a url and prepares it. [AyahAudioPlayerListener.onReady] fires when it can play. */
    fun load(url: String)

    fun play()

    fun pause()

    /** Stops and releases the current item, leaving the player reusable. */
    fun stop()

    fun seekTo(positionMs: Long)

    fun positionMs(): Long

    /** 0 until the item is prepared. */
    fun durationMs(): Long

    fun release()

    var listener: AyahAudioPlayerListener?
}

interface AyahAudioPlayerListener {
    fun onReady(durationMs: Long)
    fun onCompleted()
    fun onError(message: String)

    /**
     * Playback was started or paused from outside the app — lock screen, notification,
     * headset button, or an audio-focus interruption such as an incoming call. The in-app
     * player must follow the system, not argue with it.
     */
    fun onExternalPlayPause(isPlaying: Boolean) = Unit

    /** "Next track" pressed on a system control. Means the next ayah here. */
    fun onExternalNext() = Unit

    fun onExternalPrevious() = Unit
}

/** Metadata for the OS-level player. Mirrors [AudioUiState] in platform-neutral terms. */
data class NowPlaying(
    /** First line — the ayah being recited. */
    val title: String,
    /** Second line — the qari. */
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

/**
 * Default binding. Reports every load as unavailable so the bottom player renders its
 * error state rather than a set of buttons that silently do nothing.
 */
class NoOpAyahAudioPlayer : AyahAudioPlayer {
    override val isSupported: Boolean = false
    override var listener: AyahAudioPlayerListener? = null

    override fun load(url: String) {
        listener?.onError("Audio isn't available on this build")
    }

    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun positionMs(): Long = 0L
    override fun durationMs(): Long = 0L
    override fun release() = Unit
}
