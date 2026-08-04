package com.example.tafakkurai.quran

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Android ayah playback on ExoPlayer, driven from a media session so recitation survives
 * leaving the app.
 *
 * Two pieces make that work, and both live outside this class: [QuranPlaybackService]
 * publishes the MediaSession that draws the notification and lock-screen player and keeps
 * the process in the foreground, and [QuranPlayback] holds the single ExoPlayer instance
 * both the service and this port talk to. Creating the player here rather than inside the
 * service means playback can start before the service is up, and the service adopts it.
 *
 * Requires `android.permission.INTERNET` for CDN streaming, plus the foreground-service
 * permissions and the `QuranPlaybackService` declaration in the host app manifest.
 */
class AndroidAyahAudioPlayer(context: Context) : AyahAudioPlayer {

    private val appContext = context.applicationContext

    override val isSupported: Boolean = true

    override var listener: AyahAudioPlayerListener? = null

    /** Latest metadata, applied to the next [MediaItem] so the notification reads right. */
    private var pendingNowPlaying: NowPlaying? = null

    private val playerEvents = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> listener?.onReady(durationMs())
                Player.STATE_ENDED -> listener?.onCompleted()
                else -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onError(error.localizedMessage ?: "Couldn't play this ayah")
        }

        /**
         * The only reliable way to tell an in-app tap from the notification, a headset
         * button or an incoming call: ExoPlayer reports *why* playWhenReady changed, and
         * only the reasons below come from outside the app.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val external = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE ||
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            if (external) listener?.onExternalPlayPause(playWhenReady)
        }
    }

    private val player: ExoPlayer by lazy {
        QuranPlayback.player(appContext).also { instance ->
            instance.addListener(playerEvents)
            // Notification skip buttons mean "next ayah", which only the controller knows
            // how to resolve — so they come back in as external commands.
            QuranPlayback.onSkipNext = { listener?.onExternalNext() }
            QuranPlayback.onSkipPrevious = { listener?.onExternalPrevious() }
        }
    }

    override fun load(url: String) {
        // Started before playback so the notification exists by the time audio comes out;
        // MediaSessionService promotes itself to the foreground once the player plays.
        startPlaybackService()
        val info = pendingNowPlaying
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(info?.title ?: "Qurʼan")
                    .setArtist(info?.artist)
                    .setAlbumTitle(info?.album)
                    .build(),
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
        // Nothing is playing any more — drop the notification and the foreground state.
        appContext.stopService(Intent(appContext, QuranPlaybackService::class.java))
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun positionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    override fun durationMs(): Long =
        player.duration.let { if (it == C.TIME_UNSET || it < 0L) 0L else it }

    override fun updateNowPlaying(info: NowPlaying) {
        pendingNowPlaying = info
        QuranPlayback.setNavigation(hasNext = info.hasNext, hasPrevious = info.hasPrevious)
    }

    override fun release() {
        player.removeListener(playerEvents)
        listener = null
        appContext.stopService(Intent(appContext, QuranPlaybackService::class.java))
        QuranPlayback.release()
    }

    private fun startPlaybackService() {
        val intent = Intent(appContext, QuranPlaybackService::class.java)
        // A start attempt can be refused (background start limits); playback in-app still
        // works, so this never takes the reader down with it.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }
    }
}

/**
 * The app-wide recitation player and the wiring its system controls need.
 *
 * A single object because both [AndroidAyahAudioPlayer] (in-app) and
 * [QuranPlaybackService] (notification, lock screen, Android Auto) must drive the same
 * ExoPlayer — two instances would mean two recitations at once.
 */
internal object QuranPlayback {

    private var exoPlayer: ExoPlayer? = null

    @Volatile
    private var hasNext: Boolean = false

    @Volatile
    private var hasPrevious: Boolean = false

    @Volatile
    var onSkipNext: (() -> Unit)? = null

    @Volatile
    var onSkipPrevious: (() -> Unit)? = null

    fun player(context: Context): ExoPlayer =
        exoPlayer ?: ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exoPlayer = it }

    fun setNavigation(hasNext: Boolean, hasPrevious: Boolean) {
        this.hasNext = hasNext
        this.hasPrevious = hasPrevious
    }

    fun hasNextAyah(): Boolean = hasNext

    fun hasPreviousAyah(): Boolean = hasPrevious

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        onSkipNext = null
        onSkipPrevious = null
    }
}
