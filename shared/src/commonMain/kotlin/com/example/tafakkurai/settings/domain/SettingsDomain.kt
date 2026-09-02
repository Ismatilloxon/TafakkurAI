package com.example.tafakkurai.settings.domain

import kotlinx.coroutines.flow.StateFlow

/** Privacy choices that affect app-wide behavior. */
data class SettingsPreferences(
    val cloudConversationEnabled: Boolean = false,
)

/** Domain-facing settings source. Storage details stay in data/. */
interface SettingsRepository {
    fun read(): SettingsPreferences
    fun setCloudConversationEnabled(enabled: Boolean)
}

sealed interface SettingsSyncStatus {
    data class Idle(val pending: Long) : SettingsSyncStatus
    data class Syncing(val pending: Long) : SettingsSyncStatus
    data class Synced(val pushed: Int, val pulled: Int, val pending: Long) : SettingsSyncStatus
    data class Failed(val pending: Long) : SettingsSyncStatus
    data object SignedOut : SettingsSyncStatus
}

/** Narrow port around the app sync subsystem for Settings. */
interface SettingsSyncGateway {
    val status: StateFlow<SettingsSyncStatus>
    fun syncNow()
}

/** Performs the privacy-sensitive account teardown as one operation. */
interface SettingsAccountGateway {
    suspend fun signOut()
}

class GetSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): SettingsPreferences = repository.read()
}

class SetCloudConversationUseCase(private val repository: SettingsRepository) {
    operator fun invoke(enabled: Boolean) = repository.setCloudConversationEnabled(enabled)
}

class IsCloudConversationEnabledUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): Boolean = repository.read().cloudConversationEnabled
}

class ObserveSettingsSyncUseCase(private val gateway: SettingsSyncGateway) {
    operator fun invoke(): StateFlow<SettingsSyncStatus> = gateway.status
}

class SyncSettingsNowUseCase(private val gateway: SettingsSyncGateway) {
    operator fun invoke() = gateway.syncNow()
}

class SignOutSettingsUseCase(private val gateway: SettingsAccountGateway) {
    suspend operator fun invoke() = gateway.signOut()
}
