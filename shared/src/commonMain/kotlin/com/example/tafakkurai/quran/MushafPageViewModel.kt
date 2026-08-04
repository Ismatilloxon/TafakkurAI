package com.example.tafakkurai.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MushafUiState(
    /** The pages this surah spans, in print order. */
    val pageNumbers: List<Int> = emptyList(),
    /** Index into [pageNumbers] of the page on screen. */
    val index: Int = 0,
    val page: MushafPage? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val pageNumber: Int? get() = pageNumbers.getOrNull(index)
    val hasPrevious: Boolean get() = index > 0
    val hasNext: Boolean get() = index < pageNumbers.lastIndex
}

/**
 * The mushaf page view's own state, kept apart from [AyahReaderViewModel] so the reader
 * owns reading and audio and this owns pagination. Playback is still the reader's: a tap
 * on a word here is routed back into [ReaderUiEvent.TapAyah].
 */
class MushafPageViewModel(
    private val surahNumber: Int,
    private val repository: MushafPageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MushafUiState())
    val state: StateFlow<MushafUiState> = _state.asStateFlow()

    init { open() }

    /** Jumps to the page an ayah is printed on — used to follow the recitation. */
    fun showAyah(ayahNumber: Int) {
        val pages = _state.value.pageNumbers
        if (pages.isEmpty()) return
        viewModelScope.launch {
            val target = pages.indexOfFirst { number ->
                runCatching { repository.getPage(number) }.getOrNull()
                    ?.lines?.filterIsInstance<MushafLine.Verse>()
                    ?.any { line -> line.words.any { it.surahNumber == surahNumber && it.ayahNumber == ayahNumber } }
                    ?: false
            }
            if (target >= 0 && target != _state.value.index) select(target)
        }
    }

    fun next() = select(_state.value.index + 1)

    fun previous() = select(_state.value.index - 1)

    fun select(index: Int) {
        val pages = _state.value.pageNumbers
        if (index !in pages.indices || index == _state.value.index) return
        _state.update { it.copy(index = index) }
        loadCurrent()
    }

    fun retry() { if (_state.value.pageNumbers.isEmpty()) open() else loadCurrent() }

    private fun open() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val range = runCatching { repository.pagesOf(surahNumber) }.getOrNull()
            if (range == null) {
                _state.update { it.copy(isLoading = false, error = "Couldn't load the mushaf pages.") }
                return@launch
            }
            _state.update { it.copy(pageNumbers = range.toList(), index = 0) }
            loadCurrent()
        }
    }

    private fun loadCurrent() {
        val number = _state.value.pageNumber ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.getPage(number) }
                .onSuccess { page ->
                    // Ignore a page that arrived after the reader turned past it.
                    if (_state.value.pageNumber != number) return@onSuccess
                    _state.update { it.copy(page = page, isLoading = false) }
                }
                .onFailure {
                    if (_state.value.pageNumber != number) return@onFailure
                    _state.update { it.copy(isLoading = false, error = "Couldn't load page $number.") }
                }
            // The next page is nearly always the next thing wanted; fetch it while reading.
            (repository as? QuranComMushafPageRepository)?.prefetch(number + 1)
        }
    }
}
