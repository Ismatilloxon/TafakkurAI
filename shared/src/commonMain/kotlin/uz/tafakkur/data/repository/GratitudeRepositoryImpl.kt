package uz.tafakkur.data.repository

import com.example.tafakkurai.gratitude.GRAT_CHALLENGES
import com.example.tafakkurai.gratitude.GratChallenge
import com.example.tafakkurai.gratitude.GratChallengeProgress
import com.example.tafakkurai.gratitude.GratEntry
import com.example.tafakkurai.gratitude.GratSettings
import com.example.tafakkurai.gratitude.GratStats
import uz.tafakkur.core.feedback.CallInfo
import uz.tafakkur.core.feedback.NetworkExecutor
import uz.tafakkur.data.dto.CreateGratitudeEntryDto
import uz.tafakkur.data.dto.GratitudeChallengeDto
import uz.tafakkur.data.dto.GratitudeEntryDto
import uz.tafakkur.data.dto.GratitudeSettingsDto
import uz.tafakkur.data.dto.GratitudeStatsDto
import uz.tafakkur.data.dto.UpdateGratitudeEntryDto
import uz.tafakkur.data.remote.GratitudeApi
import uz.tafakkur.domain.repository.GratitudeCategoryShare
import uz.tafakkur.domain.repository.GratitudeDelta
import uz.tafakkur.domain.repository.GratitudeInsights
import uz.tafakkur.domain.repository.GratitudeMonth
import uz.tafakkur.domain.repository.GratitudeRepository

/**
 * The Gratitude feature is local-first: the UI reads and writes [com.example.tafakkurai
 * .gratitude.GratitudeStore] and this repository only ever runs from the outbox drain or a
 * pull refresh. So every call goes through the executor in *silent* mode ([bg]) — mapped and
 * logged like everything else, but with no loader and no snackbar, because the on-device
 * write already gave the user their feedback.
 */
class GratitudeRepositoryImpl(
    private val api: GratitudeApi,
    private val executor: NetworkExecutor,
) : GratitudeRepository {

    private suspend fun <T> bg(call: CallInfo, block: suspend () -> T): T =
        executor.call(call, showLoader = false, reportErrors = false, block = block)

    override suspend fun create(entry: GratEntry): GratEntry = bg(CallInfo("POST", "v1/gratitude/entries")) {
        api.create(
            CreateGratitudeEntryDto(
                clientId = entry.id,
                mode = entry.mode,
                text = entry.text,
                items = entry.items,
                why = entry.why,
                category = entry.category,
                mood = entry.mood,
                emotions = entry.emotions,
                photoCount = entry.photoCount,
                hasAudio = entry.hasAudio,
                hasVideo = entry.hasVideo,
                locationLabel = entry.locationLabel,
                favourite = entry.favourite,
                entryDate = entry.entryDate,
            ),
        ).toDomain()
    }

    // The backend keys on client_id, so an edit is the same idempotent write as a create.
    override suspend fun update(entry: GratEntry): GratEntry = create(entry)

    override suspend fun setFavourite(id: String, favourite: Boolean): GratEntry =
        bg(CallInfo("PATCH", "v1/gratitude/entries/$id")) {
            api.update(id, UpdateGratitudeEntryDto(favourite = favourite)).toDomain()
        }

    override suspend fun delete(id: String) = bg(CallInfo("DELETE", "v1/gratitude/entries/$id")) {
        api.delete(id)
    }

    override suspend fun changes(since: String?, limit: Int?): GratitudeDelta =
        bg(CallInfo("GET", "v1/gratitude/changes")) {
            val res = api.changes(since, limit)
            GratitudeDelta(
                entries = res.entries.filterNot { it.deleted }.map { it.toDomain() },
                deletedIds = res.entries.filter { it.deleted }.map { it.clientId }.toSet(),
                cursor = res.cursor,
                more = res.more,
            )
        }

    override suspend fun prompts(): List<String> = bg(CallInfo("GET", "v1/gratitude/prompts")) {
        api.prompts().prompts
    }

    override suspend fun stats(): GratStats = bg(CallInfo("GET", "v1/gratitude/stats")) {
        api.stats().toDomain()
    }

    override suspend fun insights(month: String?): GratitudeInsights =
        bg(CallInfo("GET", "v1/gratitude/insights")) {
            val dto = api.insights(month)
            GratitudeInsights(
                stats = dto.stats.toDomain(),
                moodWeek = dto.moodWeek,
                categories = dto.categories.map { GratitudeCategoryShare(it.key, it.label, it.percent, it.count) },
                reflection = dto.reflection,
                month = GratitudeMonth(
                    name = dto.month.name,
                    year = dto.month.year,
                    entries = dto.month.entries,
                    days = dto.month.days,
                    longest = dto.month.longest,
                    topCategory = dto.month.topCategory,
                    line = dto.month.line,
                ),
            )
        }

    override suspend fun challenges(): List<GratChallengeProgress> =
        bg(CallInfo("GET", "v1/gratitude/challenges")) {
            api.challenges().challenges.map { it.toDomain() }
        }

    override suspend fun joinChallenge(id: String, joined: Boolean): List<GratChallengeProgress> =
        bg(CallInfo("POST", "v1/gratitude/challenges/$id/join")) {
            api.joinChallenge(id, joined).challenges.map { it.toDomain() }
        }

    override suspend fun completeChallengeDay(id: String, day: Int): List<GratChallengeProgress> =
        bg(CallInfo("POST", "v1/gratitude/challenges/$id/complete")) {
            api.completeChallengeDay(id, day).challenges.map { it.toDomain() }
        }

    override suspend fun settings(): GratSettings = bg(CallInfo("GET", "v1/gratitude/settings")) {
        api.settings().toDomain()
    }

    override suspend fun saveSettings(settings: GratSettings): GratSettings =
        bg(CallInfo("PUT", "v1/gratitude/settings")) {
            api.saveSettings(
                GratitudeSettingsDto(
                    reminderSlot = settings.reminderSlot,
                    reminderTime = settings.reminderTime,
                    faceId = settings.faceId,
                    appLock = settings.appLock,
                    hidePreview = settings.hidePreview,
                    cloudSync = settings.cloudSync,
                ),
            ).toDomain()
        }
}

// The local id is the backend's client_id — mapping it back keeps one identity for an
// entry across device, outbox and server.
private fun GratitudeEntryDto.toDomain() = GratEntry(
    id = clientId.ifEmpty { id },
    mode = mode,
    text = text,
    items = items,
    why = why,
    category = category,
    mood = mood,
    emotions = emotions,
    photoCount = photoCount,
    hasAudio = hasAudio,
    hasVideo = hasVideo,
    locationLabel = locationLabel,
    favourite = favourite,
    entryDate = entryDate,
    serverId = id,
)

private fun GratitudeStatsDto.toDomain() = GratStats(
    streak = streak,
    longest = longest,
    total = total,
    monthDays = monthDays,
    week = if (week.size == 7) week else List(7) { false },
)

private fun GratitudeChallengeDto.toDomain(): GratChallengeProgress {
    val catalogue = GRAT_CHALLENGES.firstOrNull { it.id == id }
        ?: GratChallenge(id, title, sub, total, days)
    return GratChallengeProgress(catalogue, joined, doneDays.toSet())
}

private fun GratitudeSettingsDto.toDomain() =
    GratSettings(reminderSlot, reminderTime, faceId, appLock, hidePreview, cloudSync)
