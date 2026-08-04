package com.example.tafakkurai.quran

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * iOS ayah playback on AVPlayer.
 *
 * AVPlayer buffers and starts on its own, and observing `status` properly would mean KVO,
 * which is awkward from Kotlin/Native. Instead readiness is reported immediately and the
 * duration is filled in by [AudioController]'s position poll once AVPlayer knows it — the
 * controller already tolerates a zero duration until the item is ready.
 *
 * End-of-item and failure arrive through NSNotificationCenter, which needs no KVO.
 *
 * Background playback needs three things, and this class owns the last two:
 *  1. `UIBackgroundModes: [audio]` in the host app's Info.plist — without it iOS suspends
 *     the process on background and recitation stops mid-ayah.
 *  2. An AVAudioSession with the `.playback` category, activated. The host app also sets
 *     this at launch; setting the same category twice is harmless, and doing it here means
 *     recitation is never silenced by the ringer switch even if the host forgets.
 *  3. MPRemoteCommandCenter targets, so the lock screen and Control Center player can
 *     play, pause and move between ayahs, with metadata from MPNowPlayingInfoCenter.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAyahAudioPlayer : AyahAudioPlayer {

    override val isSupported: Boolean = true

    override var listener: AyahAudioPlayerListener? = null

    private val player = AVPlayer()
    private var endObserver: Any? = null
    private var failObserver: Any? = null
    private var remoteCommandsInstalled = false

    /** Last metadata pushed, so a remote command can answer with the current values. */
    private var nowPlaying: NowPlaying? = null

    override fun load(url: String) {
        activateAudioSession()
        installRemoteCommands()
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            listener?.onError("Invalid audio address")
            return
        }
        removeObservers()
        val item = AVPlayerItem(uRL = nsUrl)
        val center = NSNotificationCenter.defaultCenter
        endObserver = center.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { listener?.onCompleted() }
        failObserver = center.addObserverForName(
            name = AVPlayerItemFailedToPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { listener?.onError("Couldn't play this ayah") }

        player.replaceCurrentItemWithPlayerItem(item)
        listener?.onReady(0L)
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.pause()
        removeObservers()
        player.replaceCurrentItemWithPlayerItem(null)
        clearNowPlaying()
    }

    override fun updateNowPlaying(info: NowPlaying) {
        nowPlaying = info
        installRemoteCommands()
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.nextTrackCommand.enabled = info.hasNext
        center.previousTrackCommand.enabled = info.hasPrevious

        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to info.title,
            MPMediaItemPropertyArtist to info.artist,
            MPMediaItemPropertyAlbumTitle to info.album,
            MPMediaItemPropertyPlaybackDuration to NSNumber(double = info.durationMs / 1000.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber(double = info.positionMs / 1000.0),
            // Zero rate is what tells the lock screen the scrubber is parked, not moving.
            MPNowPlayingInfoPropertyPlaybackRate to NSNumber(double = if (info.isPlaying) 1.0 else 0.0),
        )
    }

    override fun seekTo(positionMs: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
    }

    override fun positionMs(): Long = millisOf(CMTimeGetSeconds(player.currentTime()))

    override fun durationMs(): Long {
        val item = player.currentItem ?: return 0L
        return millisOf(CMTimeGetSeconds(item.duration))
    }

    override fun release() {
        stop()
        removeRemoteCommands()
        listener = null
    }

    private fun activateAudioSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, null)
        session.setActive(true, null)
    }

    /**
     * One set of targets for the whole app lifetime. The handlers report the *intent* to
     * [AudioController]; it applies the change and pushes fresh metadata back, so the OS
     * player and the in-app bar can never drift apart.
     */
    private fun installRemoteCommands() {
        if (remoteCommandsInstalled) return
        remoteCommandsInstalled = true
        val center = MPRemoteCommandCenter.sharedCommandCenter()

        center.playCommand.enabled = true
        center.playCommand.addTargetWithHandler { _ ->
            player.play()
            listener?.onExternalPlayPause(true)
            MPRemoteCommandHandlerStatusSuccess
        }
        center.pauseCommand.enabled = true
        center.pauseCommand.addTargetWithHandler { _ ->
            player.pause()
            listener?.onExternalPlayPause(false)
            MPRemoteCommandHandlerStatusSuccess
        }
        center.togglePlayPauseCommand.enabled = true
        center.togglePlayPauseCommand.addTargetWithHandler { _ ->
            val wasPlaying = nowPlaying?.isPlaying == true
            if (wasPlaying) player.pause() else player.play()
            listener?.onExternalPlayPause(!wasPlaying)
            MPRemoteCommandHandlerStatusSuccess
        }
        center.nextTrackCommand.addTargetWithHandler { _ ->
            listener?.onExternalNext()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.previousTrackCommand.addTargetWithHandler { _ ->
            listener?.onExternalPrevious()
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun removeRemoteCommands() {
        if (!remoteCommandsInstalled) return
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.playCommand.removeTarget(null)
        center.pauseCommand.removeTarget(null)
        center.togglePlayPauseCommand.removeTarget(null)
        center.nextTrackCommand.removeTarget(null)
        center.previousTrackCommand.removeTarget(null)
        remoteCommandsInstalled = false
    }

    private fun clearNowPlaying() {
        nowPlaying = null
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    /** AVPlayer reports NaN for an indefinite/unloaded time — treat that as "unknown". */
    private fun millisOf(seconds: Double): Long =
        if (seconds.isNaN() || seconds <= 0.0) 0L else (seconds * 1000.0).toLong()

    private fun removeObservers() {
        val center = NSNotificationCenter.defaultCenter
        endObserver?.let { center.removeObserver(it) }
        failObserver?.let { center.removeObserver(it) }
        endObserver = null
        failObserver = null
    }
}
