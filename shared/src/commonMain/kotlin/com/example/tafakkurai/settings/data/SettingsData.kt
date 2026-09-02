package com.example.tafakkurai.settings.data

import com.example.tafakkurai.AppPrefs
import com.example.tafakkurai.profile.domain.ProfilePhotoRepository
import com.example.tafakkurai.settings.domain.SettingsAccountGateway
import com.example.tafakkurai.settings.domain.SettingsPreferences
import com.example.tafakkurai.settings.domain.SettingsRepository
import com.example.tafakkurai.settings.domain.SettingsSyncGateway
import com.example.tafakkurai.settings.domain.SettingsSyncStatus
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.sync.SyncEngine
import com.example.tafakkurai.sync.SyncOutcome
import com.example.tafakkurai.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import uz.tafakkur.domain.usecase.LogoutUseCase

class LocalSettingsRepository(
    private val preferences: PrefPersistence,
) : SettingsRepository {
    override fun read(): SettingsPreferences = SettingsPreferences(
        cloudConversationEnabled = preferences
            .get(AppPrefs.AI_CLOUD_CONVERSATION_ENABLED)
            ?.toBooleanStrictOrNull() == true,
    )

    override fun setCloudConversationEnabled(enabled: Boolean) {
        preferences.put(AppPrefs.AI_CLOUD_CONVERSATION_ENABLED, enabled.toString())
    }
}

class SchedulerSettingsSyncGateway(
    private val scheduler: SyncScheduler,
    private val engine: SyncEngine,
    scope: CoroutineScope,
) : SettingsSyncGateway {
    private val mutableStatus = MutableStateFlow<SettingsSyncStatus>(
        SettingsSyncStatus.Idle(engine.pendingCount()),
    )
    override val status: StateFlow<SettingsSyncStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch {
            scheduler.lastOutcome.collect { outcome ->
                mutableStatus.value = when (outcome) {
                    null -> SettingsSyncStatus.Idle(engine.pendingCount())
                    SyncOutcome.NotSignedIn -> SettingsSyncStatus.SignedOut
                    is SyncOutcome.Success -> SettingsSyncStatus.Synced(
                        pushed = outcome.pushed,
                        pulled = outcome.pulled,
                        pending = outcome.stillPending,
                    )
                    is SyncOutcome.Failed -> SettingsSyncStatus.Failed(outcome.stillPending)
                }
            }
        }
    }

    override fun syncNow() {
        mutableStatus.value = SettingsSyncStatus.Syncing(engine.pendingCount())
        scheduler.syncNow()
    }
}

/**
 * Shared-device sign-out implementation. It uploads best-effort while credentials still exist,
 * wipes every local account table, detaches capture, revokes/clears tokens, and finally restores
 * only device-level language/install preferences. Feature data is never restored.
 */
class PrivateAccountGateway(
    private val logout: LogoutUseCase,
    private val syncEngine: SyncEngine,
    private val syncScheduler: SyncScheduler,
    private val preferences: PrefPersistence,
    private val profilePhoto: ProfilePhotoRepository,
    private val memoryResetter: LocalAccountMemoryResetter,
) : SettingsAccountGateway {
    override suspend fun signOut() {
        val language = preferences.get(AppPrefs.LANGUAGE)
        val installMarker = preferences.get(AppPrefs.INSTALL_MARKER) ?: "1"
        val onboardingSeen = preferences.get(AppPrefs.ONBOARDING_SEEN)
        val devicePreferences = DEVICE_PREFERENCE_KEYS.mapNotNull { key ->
            preferences.get(key)?.let { key to it }
        }
        var wipeFailure: Throwable? = null

        try {
            syncEngine.signOut(wipeLocal = true, revokeRemoteSession = { logout() })
        } catch (failure: Throwable) {
            wipeFailure = failure
        } finally {
            syncScheduler.stop()
            // The table wipe bypasses this store, so update its observable cache explicitly.
            try {
                profilePhoto.clear()
            } catch (failure: Throwable) {
                // Keep completing the non-sensitive in-memory reset, but never turn a failed
                // private-photo deletion into a successful sign-out result.
                wipeFailure?.addSuppressed(failure) ?: run { wipeFailure = failure }
            }
            memoryResetter.reloadFromEmptyDatabase()
            preferences.put(AppPrefs.INSTALL_MARKER, installMarker)
            language?.let { preferences.put(AppPrefs.LANGUAGE, it) }
            onboardingSeen?.let { preferences.put(AppPrefs.ONBOARDING_SEEN, it) }
            devicePreferences.forEach { (key, value) -> preferences.put(key, value) }
            preferences.remove(AppPrefs.DISPLAY_NAME)
            preferences.remove(AppPrefs.USER_ID)
        }

        wipeFailure?.let { throw it }
    }

    private companion object {
        val DEVICE_PREFERENCE_KEYS = listOf(
            AppPrefs.APPEARANCE_NAV,
            AppPrefs.APPEARANCE_MODE,
            AppPrefs.APPEARANCE_DARK_THEME,
            AppPrefs.APPEARANCE_LIGHT_THEME,
            AppPrefs.APPEARANCE_SERIF,
            AppPrefs.AI_PROACTIVITY,
            AppPrefs.TODAY_DASHBOARD_ORDER,
        )
    }
}
