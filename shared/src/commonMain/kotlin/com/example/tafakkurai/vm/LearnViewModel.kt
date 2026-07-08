package com.example.tafakkurai.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.Subject
import kotlinx.coroutines.launch
import uz.tafakkur.domain.model.Subject as DomainSubject
import uz.tafakkur.domain.usecase.GetSubjectsUseCase

/** Backs LearnScreen's subject list with GET /subjects (replaces TSUBJECTS). */
class LearnViewModel(
    private val getSubjects: GetSubjectsUseCase,
) : ViewModel() {

    var subjects by mutableStateOf<List<Subject>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                subjects = getSubjects().map { it.toUi() }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load subjects"
            } finally {
                loading = false
            }
        }
    }
}

/**
 * Maps the backend subject to the prototype's richer UI model. Fields the
 * backend doesn't track (icon, progress, streak, native) get neutral defaults.
 */
private fun DomainSubject.toUi(): Subject = Subject(
    id = id,
    name = title,
    native = description,
    icon = "book",
    seed = 0,
    level = description,
    progress = 0f,
    next = "Start learning",
    streak = 0,
    accent = "cyan",
)
