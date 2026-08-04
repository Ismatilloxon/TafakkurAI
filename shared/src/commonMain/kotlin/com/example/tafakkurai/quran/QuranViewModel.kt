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
import kotlinx.coroutines.launch

/** One row of the 114-surah list. Everything the row draws, already computed. */
data class SurahUiModel(
    val surah: Surah,
    val readCount: Int,
    val percent: Int,
    val isComplete: Boolean,
    val isLastRead: Boolean,
    val isAvailableOffline: Boolean,
)

data class QuranUiState(
    val surahs: List<SurahUiModel> = emptyList(),
    val overallProgress: Float = 0f,
    val overallPercent: Int = 0,
    val ayahsRead: Int = 0,
    val totalAyahs: Int = TOTAL_AYAH_COUNT,
    val completedSurahs: Int = 0,
    val lastRead: LastRead? = null,
    val reciter: Reciter = DEFAULT_RECITER,
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && surahs.isEmpty()
}

sealed interface QuranUiEvent {
    data class Search(val query: String) : QuranUiEvent
    data class SelectReciter(val reciterId: String) : QuranUiEvent
    data object Retry : QuranUiEvent
}

/**
 * Qurʼan home state.
 *
 * The surah index comes from [QuranRepository] (async, so a real API can replace the
 * bundled catalog later), while progress comes from [QuranProgressStore], which is
 * Compose-snapshot state. `snapshotFlow` bridges the two into one immutable
 * [QuranUiState], so a tap in the reader updates this screen's percentages with no
 * manual refresh and no chance of the two disagreeing.
 */
class QuranViewModel(
    private val repository: QuranRepository,
    private val progress: QuranProgressStore,
) : ViewModel() {

    private var catalog by mutableStateOf<List<Surah>>(emptyList())
    private var loading by mutableStateOf(true)
    private var failure by mutableStateOf<String?>(null)
    private var query by mutableStateOf("")

    private val _state = MutableStateFlow(QuranUiState())
    val state: StateFlow<QuranUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            snapshotFlow { buildState() }.collect { _state.value = it }
        }
        load()
    }

    fun onEvent(event: QuranUiEvent) {
        when (event) {
            is QuranUiEvent.Search -> query = event.query
            is QuranUiEvent.SelectReciter -> progress.setReciter(event.reciterId)
            QuranUiEvent.Retry -> load()
        }
    }

    /** Re-reads persisted progress — call when returning to the tab. */
    fun refresh() = progress.reload()

    private fun load() {
        viewModelScope.launch {
            loading = true
            failure = null
            runCatching { repository.getSurahs() }
                .onSuccess { catalog = it }
                .onFailure { failure = "Couldn't load the Qurʼan index." }
            loading = false
        }
    }

    private fun buildState(): QuranUiState {
        val last = progress.lastRead
        val term = query.trim()
        val rows = catalog
            .filter { matches(it, term) }
            .map { surah ->
                val read = progress.readCountOf(surah.number)
                SurahUiModel(
                    surah = surah,
                    readCount = read,
                    percent = SurahProgress(surah.number, read, surah.ayahCount).percent,
                    isComplete = read >= surah.ayahCount,
                    isLastRead = last?.surahNumber == surah.number,
                    isAvailableOffline = surah.number in BundledAyahText.offlineSurahs,
                )
            }
        return QuranUiState(
            surahs = rows,
            overallProgress = progress.overallProgress,
            overallPercent = progress.overallPercent,
            ayahsRead = progress.totalRead,
            completedSurahs = progress.completedSurahCount,
            lastRead = last,
            reciter = progress.reciter,
            query = query,
            isLoading = loading,
            error = failure,
        )
    }

    /** Matches surah number, transliteration, meaning or Arabic name. */
    private fun matches(surah: Surah, term: String): Boolean {
        if (term.isEmpty()) return true
        if (surah.number.toString() == term) return true
        return surah.nameEnglish.contains(term, ignoreCase = true) ||
            surah.nameTranslation.contains(term, ignoreCase = true) ||
            surah.nameArabic.contains(term)
    }
}
