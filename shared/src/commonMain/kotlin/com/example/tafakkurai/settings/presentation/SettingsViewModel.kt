package com.example.tafakkurai.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tafakkurai.settings.domain.GetSettingsUseCase
import com.example.tafakkurai.settings.domain.ObserveSettingsSyncUseCase
import com.example.tafakkurai.settings.domain.SetCloudConversationUseCase
import com.example.tafakkurai.settings.domain.SettingsSyncStatus
import com.example.tafakkurai.settings.domain.SignOutSettingsUseCase
import com.example.tafakkurai.settings.domain.SyncSettingsNowUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class SettingsUiState(
    val cloudConversationEnabled: Boolean = false,
    val signingOut: Boolean = false,
    val signOutFailed: Boolean = false,
)

class SettingsViewModel(
    getSettings: GetSettingsUseCase,
    private val setCloudConversation: SetCloudConversationUseCase,
    observeSync: ObserveSettingsSyncUseCase,
    private val syncNow: SyncSettingsNowUseCase,
    private val signOut: SignOutSettingsUseCase,
) : ViewModel() {
    var state by mutableStateOf(
        SettingsUiState(cloudConversationEnabled = getSettings().cloudConversationEnabled),
    )
        private set

    val syncStatus: StateFlow<SettingsSyncStatus> = observeSync()

    fun setCloudConversationEnabled(enabled: Boolean) {
        setCloudConversation(enabled)
        state = state.copy(cloudConversationEnabled = enabled)
    }

    fun syncNow() = syncNow.invoke()

    fun signOut(onSuccess: () -> Unit) {
        if (state.signingOut) return
        state = state.copy(signingOut = true, signOutFailed = false)
        viewModelScope.launch {
            runCatching { signOut() }
                .onSuccess { onSuccess() }
                .onFailure { state = state.copy(signingOut = false, signOutFailed = true) }
        }
    }

    fun dismissSignOutError() {
        state = state.copy(signOutFailed = false)
    }
}
