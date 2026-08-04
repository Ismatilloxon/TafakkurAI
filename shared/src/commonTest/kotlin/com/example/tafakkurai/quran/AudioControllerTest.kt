package com.example.tafakkurai.quran

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records what the platform layer was asked to do, and lets a test drive its callbacks. */
private class FakeAyahAudioPlayer(
    private val failWith: String? = null,
) : AyahAudioPlayer {
    override val isSupported: Boolean = true
    override var listener: AyahAudioPlayerListener? = null

    val loadedUrls = mutableListOf<String>()
    val nowPlaying = mutableListOf<NowPlaying>()
    var playing = false
    var stopped = 0
    var position = 0L
    var duration = 30_000L

    override fun load(url: String) {
        loadedUrls += url
        if (failWith != null) listener?.onError(failWith) else listener?.onReady(duration)
    }

    override fun play() { playing = true }
    override fun pause() { playing = false }
    override fun stop() { playing = false; stopped++ }
    override fun seekTo(positionMs: Long) { position = positionMs }
    override fun positionMs(): Long = position
    override fun durationMs(): Long = duration
    override fun release() = Unit

    override fun updateNowPlaying(info: NowPlaying) { nowPlaying += info }

    /** Simulates the file reaching its end. */
    fun finish() = listener?.onCompleted()

    /** Simulates the lock screen / notification / headset acting on playback. */
    fun remotePlayPause(isPlaying: Boolean) {
        playing = isPlaying
        listener?.onExternalPlayPause(isPlaying)
    }

    fun remoteNext() = listener?.onExternalNext()
}

private fun withController(
    player: AyahAudioPlayer,
    queueSurah: Int = 114,
    reciter: Reciter = DEFAULT_RECITER,
    onAyahStarted: (Int, Int) -> Unit = { _, _ -> },
    block: (AudioController) -> Unit,
) {
    val scope = CoroutineScope(Dispatchers.Unconfined)
    try {
        val controller = AudioController(player, scope, onAyahStarted)
        controller.setQueue(BundledQuranRepository { reciter }.buildAyahs(queueSurah, reciter), reciter)
        block(controller)
    } finally {
        scope.cancel()
    }
}

class AudioControllerTest {

    @Test
    fun tapping_an_ayah_loads_that_ayahs_audio() {
        val player = FakeAyahAudioPlayer()
        withController(player, queueSurah = 2) { controller ->
            controller.playAyah(2, 255)

            assertEquals(1, player.loadedUrls.size)
            assertEquals("https://everyayah.com/data/Alafasy_128kbps/002255.mp3", player.loadedUrls.single())
            val track = assertNotNull(controller.state.value.track)
            assertEquals(2, track.surahNumber)
            assertEquals(255, track.ayahNumber)
            assertEquals("Al-Baqarah", track.surahName)
            assertEquals("Mishary Alafasy", track.reciterName)
            assertTrue(controller.state.value.isPlaying)
            assertTrue(player.playing)
        }
    }

    @Test
    fun playing_an_ayah_records_it_as_read() {
        val started = mutableListOf<Pair<Int, Int>>()
        withController(FakeAyahAudioPlayer(), onAyahStarted = { s, a -> started += s to a }) { controller ->
            controller.playAyah(114, 3)
            assertEquals(listOf(114 to 3), started)
        }
    }

    @Test
    fun tapping_the_playing_ayah_again_pauses_instead_of_restarting() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 2)
            controller.playAyah(114, 2)

            assertEquals(1, player.loadedUrls.size) // not reloaded
            assertEquals(PlaybackState.PAUSED, controller.state.value.playback)
            assertFalse(player.playing)

            controller.playAyah(114, 2) // and back again
            assertTrue(controller.state.value.isPlaying)
        }
    }

    @Test
    fun priming_arms_an_ayah_without_fetching_it() {
        val player = FakeAyahAudioPlayer()
        withController(player, queueSurah = 2) { controller ->
            controller.prime(2, 10)

            assertTrue(player.loadedUrls.isEmpty())
            assertEquals(10, controller.state.value.track?.ayahNumber)
            assertEquals(PlaybackState.PAUSED, controller.state.value.playback)
            assertFalse(player.playing)
        }
    }

    @Test
    fun playing_a_primed_track_resumes_that_ayah() {
        val player = FakeAyahAudioPlayer()
        val started = mutableListOf<Pair<Int, Int>>()
        withController(player, queueSurah = 2, onAyahStarted = { s, a -> started += s to a }) { controller ->
            controller.prime(2, 10)
            controller.togglePlayPause()

            assertTrue(player.loadedUrls.single().endsWith("002010.mp3"))
            assertTrue(controller.state.value.isPlaying)
            assertEquals(listOf(2 to 10), started)
        }
    }

    @Test
    fun priming_never_displaces_a_track_already_playing() {
        val player = FakeAyahAudioPlayer()
        withController(player, queueSurah = 2) { controller ->
            controller.playAyah(2, 40)
            controller.prime(2, 10)

            assertEquals(40, controller.state.value.track?.ayahNumber)
            assertTrue(controller.state.value.isPlaying)
        }
    }

    @Test
    fun the_system_player_is_told_which_ayah_is_reciting() {
        val player = FakeAyahAudioPlayer()
        withController(player, queueSurah = 2) { controller ->
            controller.playAyah(2, 255)

            val info = player.nowPlaying.last()
            assertEquals("Al-Baqarah · Ayah 255", info.title)
            assertEquals("Mishary Alafasy", info.artist)
            assertTrue(info.isPlaying)
            assertTrue(info.hasNext)
            assertTrue(info.hasPrevious)
        }
    }

    @Test
    fun the_first_and_last_ayah_report_no_skip_target() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 1)
            assertFalse(player.nowPlaying.last().hasPrevious)

            controller.playAyah(114, 6) // An-Nas has 6 ayahs
            assertFalse(player.nowPlaying.last().hasNext)
        }
    }

    @Test
    fun pausing_from_the_lock_screen_pauses_the_in_app_player_too() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 2)

            player.remotePlayPause(false)
            assertEquals(PlaybackState.PAUSED, controller.state.value.playback)
            assertFalse(player.nowPlaying.last().isPlaying)

            player.remotePlayPause(true)
            assertTrue(controller.state.value.isPlaying)
        }
    }

    @Test
    fun the_notifications_next_button_moves_to_the_next_ayah() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 2)
            player.remoteNext()

            assertEquals(3, controller.state.value.track?.ayahNumber)
            assertTrue(player.loadedUrls.last().endsWith("114003.mp3"))
        }
    }

    @Test
    fun a_surah_opens_with_the_basmala_then_rolls_into_ayah_one() {
        val player = FakeAyahAudioPlayer()
        val started = mutableListOf<Pair<Int, Int>>()
        withController(player, queueSurah = 2, onAyahStarted = { s, a -> started += s to a }) { controller ->
            controller.playAyah(2, 1)

            // Al-Fatihah 1:1 is the Basmala, so it is the same recording in the same voice.
            assertTrue(player.loadedUrls.single().endsWith("001001.mp3"))
            assertTrue(assertNotNull(controller.state.value.track).isBasmala)
            assertEquals("Al-Baqarah · Bismillah", player.nowPlaying.last().title)
            assertTrue(started.isEmpty()) // the Basmala is not an ayah of Al-Baqarah

            player.finish()
            assertTrue(player.loadedUrls.last().endsWith("002001.mp3"))
            assertFalse(assertNotNull(controller.state.value.track).isBasmala)
            assertEquals(listOf(2 to 1), started)
        }
    }

    @Test
    fun at_tawbah_and_al_fatihah_start_without_a_prepended_basmala() {
        // At-Tawbah has no Basmala in the mushaf; in Al-Fatihah it is already ayah 1.
        val player = FakeAyahAudioPlayer()
        withController(player, queueSurah = 9) { controller ->
            controller.playAyah(9, 1)
            assertTrue(player.loadedUrls.single().endsWith("009001.mp3"))
        }

        val fatihah = FakeAyahAudioPlayer()
        withController(fatihah, queueSurah = 1) { controller ->
            controller.playAyah(1, 1)
            assertEquals(1, fatihah.loadedUrls.size)
            assertTrue(fatihah.loadedUrls.single().endsWith("001001.mp3"))
            assertFalse(assertNotNull(controller.state.value.track).isBasmala)
        }
    }

    @Test
    fun tapping_an_ayah_other_than_the_first_does_not_prepend_the_basmala() {
        val player = FakeAyahAudioPlayer()
        withController(player, queueSurah = 2) { controller ->
            controller.playAyah(2, 255)
            assertTrue(player.loadedUrls.single().endsWith("002255.mp3"))
        }
    }

    @Test
    fun previous_from_ayah_two_returns_to_ayah_one_without_repeating_the_basmala() {
        val player = FakeAyahAudioPlayer()
        withController(player, queueSurah = 2) { controller ->
            controller.playAyah(2, 2)
            assertTrue(controller.previous())

            assertTrue(player.loadedUrls.last().endsWith("002001.mp3"))
            assertFalse(assertNotNull(controller.state.value.track).isBasmala)
        }
    }

    @Test
    fun next_and_previous_move_one_ayah_at_a_time() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 3)

            assertTrue(controller.next())
            assertEquals(4, controller.state.value.track?.ayahNumber)
            assertTrue(player.loadedUrls.last().endsWith("114004.mp3"))

            assertTrue(controller.previous())
            assertEquals(3, controller.state.value.track?.ayahNumber)
            assertTrue(player.loadedUrls.last().endsWith("114003.mp3"))
        }
    }

    @Test
    fun next_and_previous_clamp_at_the_ends_of_the_surah() {
        withController(FakeAyahAudioPlayer()) { controller ->
            controller.playAyah(114, 1)
            assertFalse(controller.previous())
            assertEquals(1, controller.state.value.track?.ayahNumber)

            controller.playAyah(114, 6) // An-Nas has 6 ayahs
            assertFalse(controller.next())
            assertEquals(6, controller.state.value.track?.ayahNumber)
        }
    }

    @Test
    fun finishing_an_ayah_rolls_into_the_next_one() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 1)
            player.finish() // Basmala → ayah 1
            player.finish() // ayah 1 → ayah 2

            assertEquals(2, controller.state.value.track?.ayahNumber)
            assertTrue(controller.state.value.isPlaying)
        }
    }

    @Test
    fun finishing_the_last_ayah_stops_rather_than_looping() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 6)
            player.finish()

            assertEquals(6, controller.state.value.track?.ayahNumber)
            assertEquals(PlaybackState.PAUSED, controller.state.value.playback)
        }
    }

    @Test
    fun changing_reciter_changes_the_url_that_gets_loaded() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 1)
            assertTrue(player.loadedUrls.last().contains("Alafasy_128kbps"))

            val sudais = reciterOf("sudais")
            controller.setQueue(BundledQuranRepository { sudais }.buildAyahs(114, sudais), sudais)
            controller.playAyah(114, 2)

            assertTrue(player.loadedUrls.last().contains("Abdurrahmaan_As-Sudais_192kbps"))
            assertEquals("Abdul Rahman Al-Sudais", controller.state.value.track?.reciterName)
        }
    }

    @Test
    fun a_failed_load_surfaces_an_error_state_not_silent_dead_controls() {
        withController(FakeAyahAudioPlayer(failWith = "Network unreachable")) { controller ->
            controller.playAyah(114, 1)

            assertEquals(PlaybackState.ERROR, controller.state.value.playback)
            assertEquals("Network unreachable", controller.state.value.error)
            assertFalse(controller.state.value.isPlaying)
        }
    }

    @Test
    fun seeking_maps_a_fraction_onto_the_track_duration() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 1)
            controller.seekToFraction(0.5f)

            assertEquals(15_000L, player.position)
            assertEquals(15_000L, controller.state.value.positionMs)

            controller.seekToFraction(2f) // clamped
            assertEquals(30_000L, player.position)
        }
    }

    @Test
    fun stopping_clears_the_player_bar() {
        val player = FakeAyahAudioPlayer()
        withController(player) { controller ->
            controller.playAyah(114, 1)
            controller.stop()

            assertNull(controller.state.value.track)
            assertFalse(controller.state.value.isActive)
            assertEquals(PlaybackState.IDLE, controller.state.value.playback)
        }
    }

    @Test
    fun progress_is_zero_until_a_duration_is_known() {
        assertEquals(0f, AudioUiState().progress)
        assertEquals(0.25f, AudioUiState(positionMs = 250, durationMs = 1000).progress)
    }
}

class NoOpAyahAudioPlayerTest {

    @Test
    fun reports_unsupported_and_fails_loads_explicitly() {
        val player = NoOpAyahAudioPlayer()
        var error: String? = null
        player.listener = object : AyahAudioPlayerListener {
            override fun onReady(durationMs: Long) = Unit
            override fun onCompleted() = Unit
            override fun onError(message: String) { error = message }
        }

        assertFalse(player.isSupported)
        player.load("https://example.test/001001.mp3")
        assertNotNull(error)
    }
}
