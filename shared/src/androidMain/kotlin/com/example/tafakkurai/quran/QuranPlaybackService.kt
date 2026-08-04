package com.example.tafakkurai.quran

import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Keeps recitation playing when the app is backgrounded, and gives the OS its player.
 *
 * A [MediaSessionService] is what makes background audio legal on modern Android: it
 * promotes itself to a foreground service with a media notification while the player is
 * playing, and the notification, lock-screen controls, Bluetooth buttons and Android Auto
 * all come from the session for free.
 *
 * The service does not own the player — [QuranPlayback] does — so starting or stopping
 * the service never interrupts audio that is already running.
 */
class QuranPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = AyahNavigationPlayer(
            player = QuranPlayback.player(this),
            onNext = { QuranPlayback.onSkipNext?.invoke() },
            onPrevious = { QuranPlayback.onSkipPrevious?.invoke() },
        )
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Recitation is a deliberate act, not background music: when the user swipes the app
     * away and nothing is playing, take the notification with it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Releases the session only. The ExoPlayer is app-scoped and is released by
        // AndroidAyahAudioPlayer.release(), so a service restart resumes the same playback.
        session?.release()
        session = null
        super.onDestroy()
    }
}

/**
 * Teaches the media session that this player has a "next" and a "previous" even though
 * ExoPlayer holds exactly one ayah at a time.
 *
 * Each ayah is loaded as its own single-item timeline (they are separate CDN files), so
 * ExoPlayer reports no next or previous item and the notification would show no skip
 * buttons. Overriding the seek-to-next/previous commands routes those presses back to
 * [AudioController], which knows the surah's bounds and loads the right file.
 */
private class AyahNavigationPlayer(
    player: Player,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) : ForwardingPlayer(player) {

    private val navigationCommands = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*navigationCommands).build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in navigationCommands || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = QuranPlayback.hasNextAyah()

    override fun hasPreviousMediaItem(): Boolean = QuranPlayback.hasPreviousAyah()

    override fun seekToNext() = onNext()

    override fun seekToNextMediaItem() = onNext()

    override fun seekToPrevious() = onPrevious()

    override fun seekToPreviousMediaItem() = onPrevious()
}
