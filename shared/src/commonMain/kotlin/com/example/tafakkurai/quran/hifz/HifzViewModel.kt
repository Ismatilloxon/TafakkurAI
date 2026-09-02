package com.example.tafakkurai.quran.hifz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.db.HifzCard
import com.example.tafakkurai.flashcards.Rating
import com.example.tafakkurai.quran.Ayah
import com.example.tafakkurai.quran.AudioController
import com.example.tafakkurai.quran.AudioUiState
import com.example.tafakkurai.quran.AyahAudioCache
import com.example.tafakkurai.quran.AyahAudioPlayer
import com.example.tafakkurai.quran.AyahPrefetcher
import com.example.tafakkurai.quran.DEFAULT_MUSHAF
import com.example.tafakkurai.quran.MushafEdition
import com.example.tafakkurai.quran.PrefetchState
import com.example.tafakkurai.quran.PlaybackState
import com.example.tafakkurai.quran.QuranProgressStore
import com.example.tafakkurai.quran.QuranRepository
import com.example.tafakkurai.quran.RepeatMode
import com.example.tafakkurai.quran.SURAH_CATALOG
import com.example.tafakkurai.quran.Surah
import com.example.tafakkurai.quran.ayahCountOf
import com.example.tafakkurai.quran.surahOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ── Home / setup ─────────────────────────────────────────────────────────────

/** One enrolled surah as the home list draws it. */
data class HifzSurahRow(
    val surah: Surah,
    val progress: SurahHifzProgress,
)

/**
 * The hifz home and setup screens.
 *
 * Everything it reads is local and synchronous — the card table is the source of truth and
 * lives on the device, so opening the screen with no connection shows the same numbers as
 * opening it with one.
 */
class HifzViewModel(
    private val store: HifzStore,
    val planner: HifzPlanner,
    private val progress: QuranProgressStore,
    private val cache: AyahAudioCache? = null,
) : ViewModel() {

    var stats by mutableStateOf(HifzStats())
        private set

    var rows by mutableStateOf<List<HifzSurahRow>>(emptyList())
        private set

    /** Complete 114-surah picker state. Filtering lives here, outside composition. */
    var searchQuery by mutableStateOf("")
        private set

    var availableSurahs by mutableStateOf(SURAH_CATALOG)
        private set

    /** Today's session, recomputed whenever the screen is resumed. */
    var plan by mutableStateOf(HifzPlan())
        private set

    private val prefetcher: AyahPrefetcher? = cache?.let { AyahPrefetcher(it, viewModelScope) }

    var downloads by mutableStateOf(PrefetchState())
        private set

    init {
        refresh()
        // One collector for the lifetime of the screen — enrolling again re-points the
        // prefetcher, it does not need a second subscription.
        prefetcher?.let { p -> viewModelScope.launch { p.state.collect { downloads = it } } }
    }

    fun refresh() {
        planner.reload()
        stats = store.stats()
        plan = planner.plan()
        rows = store.progressBySurah().values
            .mapNotNull { p -> surahOf(p.surahNumber)?.let { HifzSurahRow(it, p) } }
            .sortedBy { it.surah.number }
    }

    /** The qari whose voice the drill uses — the one already chosen for reading. */
    val reciterName: String get() = progress.reciter.name

    // ── Enrolment ────────────────────────────────────────────────────────────

    /**
     * Puts a run of ayahs under memorization and starts downloading its recitation.
     *
     * The download is started, not awaited. Hifz sessions happen on commutes and in
     * masjids, so having the audio on disk matters — but a session must never be blocked
     * behind a progress bar, and playback falls back to streaming for anything not yet
     * fetched.
     */
    fun enrol(surahNumber: Int, fromAyah: Int, toAyah: Int): Int {
        val added = store.addRange(surahNumber, fromAyah, toAyah)
        prefetcher?.open(surahNumber = surahNumber, reciter = progress.reciter, fromAyah = fromAyah)
        refresh()
        return added
    }

    fun drop(surahNumber: Int) {
        store.removeSurah(surahNumber)
        refresh()
    }

    /** Ayah count of a surah, so the setup screen can bound its range pickers. */
    fun ayahCount(surahNumber: Int): Int = ayahCountOf(surahNumber)

    fun searchSurahs(query: String) {
        searchQuery = query
        availableSurahs = searchHifzSurahs(query)
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun setDailyNew(value: Int) { planner.setDailyNew(value); refresh() }
    fun setReviewCap(value: Int) { planner.setReviewCap(value); refresh() }
    fun setRepeatCount(value: Int) { planner.setRepeatCount(value) }
    fun setAutoPlay(on: Boolean) { planner.setAutoPlay(on) }

    override fun onCleared() {
        prefetcher?.cancel()
        super.onCleared()
    }
}

// ── Review session ───────────────────────────────────────────────────────────

/**
 * One card as the review screen draws it.
 *
 * [cue] is the ayah *before* the one being tested — the prompt a hifz teacher gives. [ayah]
 * is the answer, revealed after the attempt. Both come from [QuranRepository] at render
 * time; neither is stored against the card.
 */
data class HifzCardUi(
    val card: HifzCard,
    val surah: Surah?,
    val cue: Ayah?,
    val ayah: Ayah?,
) {
    val isFirstOfSurah: Boolean get() = card.ayah == 1
    val reference: String get() = "${card.surah}:${card.ayah}"
}

data class HifzSessionUiState(
    val isLoading: Boolean = true,
    val total: Int = 0,
    val index: Int = 0,
    val current: HifzCardUi? = null,
    val revealed: Boolean = false,
    val finished: Boolean = false,
    val audio: AudioUiState = AudioUiState(),
    val counts: Map<Rating, Int> = emptyMap(),
    val reviewed: Int = 0,
    /** The rasm the ayah is drawn in — the same one the reader is set to. */
    val mushaf: MushafEdition = DEFAULT_MUSHAF,
    val showTranslation: Boolean = true,
    val error: String? = null,
) {
    val progress: Float get() = if (total <= 0) 0f else (index.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * A memorization session: prompt, attempt, reveal, grade.
 *
 * The grading itself is [HifzStore.grade], which is [com.example.tafakkurai.flashcards.Fsrs]
 * — the same scheduler the flashcard decks use. What is different here is the prompt (the
 * preceding ayah rather than a question) and the recitation looping behind it.
 */
class HifzSessionViewModel(
    private val store: HifzStore,
    private val planner: HifzPlanner,
    private val repository: QuranRepository,
    private val progress: QuranProgressStore,
    player: AyahAudioPlayer,
    private val cache: AyahAudioCache? = null,
) : ViewModel() {

    private val audio = AudioController(
        player = player,
        scope = viewModelScope,
        onAyahStarted = { surah, ayah ->
            // Reciting an ayah is reading it, so it counts towards Qurʼan progress the same
            // way the reader does. The reader's *resume point* is deliberately not moved:
            // drilling 2:255 must not drag the reader away from where the user was reading.
            progress.markRead(surah, ayah)
        },
        localUrlFor = { remote -> cache?.cachedUrl(remote) },
    )

    private var queue by mutableStateOf<List<HifzCard>>(emptyList())
    private var index by mutableStateOf(0)
    private var revealed by mutableStateOf(false)
    private var loading by mutableStateOf(true)
    private var counts by mutableStateOf<Map<Rating, Int>>(emptyMap())
    private var reviewedCards by mutableStateOf<Set<String>>(emptySet())
    private var failure by mutableStateOf<String?>(null)
    private var loadJob: Job? = null

    /** surahNumber → its ayahs, so a session inside one surah fetches once. */
    private val ayahsBySurah = mutableMapOf<Int, List<Ayah>>()
    private var current by mutableStateOf<HifzCardUi?>(null)

    private val _state = MutableStateFlow(HifzSessionUiState())
    val state: StateFlow<HifzSessionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            audio.state.collect { audioState -> publish(audio = audioState) }
        }
    }

    /** Starts a fresh route-scoped session even though the activity-owned ViewModel is retained. */
    fun beginSession() {
        loadJob?.cancel()
        audio.activate()
        audio.stop()
        planner.reload()
        val plan = planner.plan()
        queue = plan.queue
        index = 0
        revealed = false
        loading = true
        counts = emptyMap()
        reviewedCards = emptySet()
        failure = null
        current = null
        audio.setRepeat(RepeatMode.AYAH, times = planner.repeatCount)
        publish()
        loadJob = viewModelScope.launch { loadCurrent() }
    }

    /** Stops route-owned work and audio as soon as this custom navigation screen leaves. */
    fun endSession() {
        loadJob?.cancel()
        loadJob = null
        audio.stop()
        audio.setRepeat(RepeatMode.OFF)
        audio.deactivate()
    }

    /** Fetches whatever the current card needs to be drawn, then publishes it. */
    private suspend fun loadCurrent() {
        loading = true
        failure = null
        publish()
        val card = queue.getOrNull(index)
        if (card == null) {
            current = null
            loading = false
            publish()
            return
        }
        val ayahs = ayahsBySurah[card.surah] ?: try {
            repository.getAyahs(card.surah)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failure = "hifz.review.load_failed"
            emptyList()
        }.also { if (it.isNotEmpty()) ayahsBySurah[card.surah] = it }

        current = HifzCardUi(
            card = card,
            surah = surahOf(card.surah),
            cue = ayahs.firstOrNull { it.number == card.ayah - 1 },
            ayah = ayahs.firstOrNull { it.number == card.ayah },
        )
        if (current?.ayah == null) failure = "hifz.review.load_failed"
        audio.setQueue(ayahs, progress.reciter)
        loading = false
        publish()
    }

    // ── Events ───────────────────────────────────────────────────────────────

    /**
     * Shows the ayah and, unless the user turned it off, starts its recitation on the loop
     * the planner is set to. Hearing it is part of the check, not a decoration.
     */
    fun reveal() {
        if (revealed || loading || failure != null || current?.ayah == null) return
        revealed = true
        val card = current?.card
        if (planner.autoPlay && card != null) {
            audio.setRepeat(RepeatMode.AYAH, times = planner.repeatCount)
            audio.playAyah(card.surah, card.ayah, basmala = false)
        }
        publish()
    }

    fun playPause() {
        val card = current?.card ?: return
        if (!revealed || loading || failure != null) return
        val audioState = audio.state.value
        if (audioState.playback == PlaybackState.IDLE ||
            (audioState.playback == PlaybackState.PAUSED &&
                audioState.durationMs > 0L && audioState.positionMs >= audioState.durationMs)
        ) {
            audio.stop()
            audio.setRepeat(RepeatMode.AYAH, times = planner.repeatCount)
            audio.playAyah(card.surah, card.ayah, basmala = false)
        } else {
            audio.togglePlayPause()
        }
    }

    fun seek(fraction: Float) = audio.seekToFraction(fraction)

    /** Replays the current ayah from the top — the "again, slower" button. */
    fun replay() {
        val card = current?.card?.takeIf { revealed && !loading && failure == null } ?: return
        audio.stop()
        audio.setRepeat(RepeatMode.AYAH, times = planner.repeatCount)
        audio.playAyah(card.surah, card.ayah, basmala = false)
    }

    fun grade(rating: Rating) {
        if (loading || !revealed || failure != null) return
        val card = current?.card ?: return
        loading = true
        publish()
        val graded = runCatching { store.grade(card, rating) }.isSuccess
        if (!graded) {
            loading = false
            failure = "hifz.review.grade_failed"
            publish()
            return
        }
        counts = counts + (rating to (counts[rating] ?: 0) + 1)
        reviewedCards = reviewedCards + "${card.surah}:${card.ayah}"
        audio.stop()
        // Anki's rule, and the right one for hifz: an ayah you could not recall comes back
        // before the session ends rather than waiting for tomorrow.
        if (rating == Rating.AGAIN) {
            store.cardOf(card.surah, card.ayah)?.let { queue = queue + it }
        }
        index++
        revealed = false
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadCurrent() }
    }

    fun retryLoad() {
        if (loading) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadCurrent() }
    }

    fun previewLabel(rating: Rating): String =
        current?.card?.let { store.previewLabel(it, rating) } ?: ""

    override fun onCleared() {
        // The player is an app-wide single — stop this session's playback, leave the player.
        endSession()
        super.onCleared()
    }

    private fun publish(audio: AudioUiState = _state.value.audio) {
        _state.value = HifzSessionUiState(
            isLoading = loading,
            total = queue.size,
            index = index,
            current = current,
            revealed = revealed,
            finished = queue.isNotEmpty() && index >= queue.size,
            audio = audio,
            counts = counts,
            reviewed = reviewedCards.size,
            mushaf = progress.mushaf,
            showTranslation = progress.showTranslation,
            error = failure,
        )
    }
}

/** Matches every bundled surah by number, transliteration, meaning, or Arabic name. */
internal fun searchHifzSurahs(
    query: String,
    catalog: List<Surah> = SURAH_CATALOG,
): List<Surah> {
    val term = query.trim()
    if (term.isEmpty()) return catalog
    if (term.all { it.isDigit() }) {
        return catalog.filter { it.number.toString() == term }
    }
    val normalizedTerm = term.hifzSearchKey()
    if (normalizedTerm.isEmpty()) return catalog
    return catalog.filter { surah ->
        surah.nameEnglish.hifzSearchKey().contains(normalizedTerm) ||
            surah.nameTranslation.hifzSearchKey().contains(normalizedTerm) ||
            surah.nameArabic.hifzSearchKey().contains(normalizedTerm)
    }
}

private fun String.hifzSearchKey(): String =
    lowercase().filter { it.isLetterOrDigit() }
