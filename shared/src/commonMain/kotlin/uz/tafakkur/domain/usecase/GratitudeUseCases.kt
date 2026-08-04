package uz.tafakkur.domain.usecase

import com.example.tafakkurai.gratitude.GratEntry
import com.example.tafakkurai.gratitude.GratSettings
import com.example.tafakkurai.gratitude.GratitudeStore
import uz.tafakkur.domain.repository.GratitudeRepository

// ---- Gratitude ----
//
// Writes go through GratitudeStore (local first, outbox second); these use-cases are
// the network half the outbox drains into, plus the read models the insights screens
// prefer from the server when it is reachable.

class SaveGratitudeEntryUseCase(private val repo: GratitudeRepository) {
    suspend operator fun invoke(entry: GratEntry) = repo.create(entry)
}

class SetGratitudeFavouriteUseCase(private val repo: GratitudeRepository) {
    suspend operator fun invoke(id: String, favourite: Boolean) = repo.setFavourite(id, favourite)
}

class DeleteGratitudeEntryUseCase(private val repo: GratitudeRepository) {
    suspend operator fun invoke(id: String) = repo.delete(id)
}

class GetGratitudeInsightsUseCase(private val repo: GratitudeRepository) {
    suspend operator fun invoke(month: String? = null) = repo.insights(month)
}

class GetGratitudePromptsUseCase(private val repo: GratitudeRepository) {
    suspend operator fun invoke() = repo.prompts()
}

class JoinGratitudeChallengeUseCase(private val repo: GratitudeRepository) {
    suspend operator fun invoke(id: String, joined: Boolean) = repo.joinChallenge(id, joined)
}

class CompleteGratitudeChallengeDayUseCase(private val repo: GratitudeRepository) {
    suspend operator fun invoke(id: String, day: Int) = repo.completeChallengeDay(id, day)
}

class SaveGratitudeSettingsUseCase(private val repo: GratitudeRepository) {
    suspend operator fun invoke(settings: GratSettings) = repo.saveSettings(settings)
}

/**
 * Pulls everything the backend changed since the last sync and merges it into the
 * local store, so a second device's entries appear without a reinstall. Safe to call
 * on every app enter: it is a delta, and a failure just leaves the cursor untouched.
 */
class PullGratitudeChangesUseCase(
    private val repo: GratitudeRepository,
    private val store: GratitudeStore,
) {
    suspend operator fun invoke(since: String? = null): String {
        val delta = repo.changes(since)
        store.mergeFromServer(delta.entries, delta.deletedIds)
        return delta.cursor
    }
}
