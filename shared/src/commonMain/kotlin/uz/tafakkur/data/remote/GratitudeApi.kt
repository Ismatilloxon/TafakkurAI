package uz.tafakkur.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import uz.tafakkur.data.dto.CompleteChallengeDayDto
import uz.tafakkur.data.dto.CreateGratitudeEntryDto
import uz.tafakkur.data.dto.GratitudeChallengesDto
import uz.tafakkur.data.dto.GratitudeChangesDto
import uz.tafakkur.data.dto.GratitudeEntriesDto
import uz.tafakkur.data.dto.GratitudeEntryDto
import uz.tafakkur.data.dto.GratitudeInsightsDto
import uz.tafakkur.data.dto.GratitudePromptsDto
import uz.tafakkur.data.dto.GratitudeSettingsDto
import uz.tafakkur.data.dto.GratitudeStatsDto
import uz.tafakkur.data.dto.JoinChallengeDto
import uz.tafakkur.data.dto.UpdateGratitudeEntryDto

/** Thin transport over the backend Gratitude module. No caching — that is the store's job. */
class GratitudeApi(private val client: HttpClient) {

    suspend fun create(req: CreateGratitudeEntryDto): GratitudeEntryDto =
        client.post("v1/gratitude/entries") { setBody(req) }.body()

    suspend fun list(
        from: String? = null,
        to: String? = null,
        query: String? = null,
        category: String? = null,
        favourite: Boolean? = null,
        limit: Int? = null,
        before: String? = null,
    ): GratitudeEntriesDto = client.get("v1/gratitude/entries") {
        from?.let { parameter("from", it) }
        to?.let { parameter("to", it) }
        query?.let { parameter("q", it) }
        category?.let { parameter("category", it) }
        favourite?.let { parameter("favourite", it) }
        limit?.let { parameter("limit", it) }
        before?.let { parameter("before", it) }
    }.body()

    suspend fun get(id: String): GratitudeEntryDto = client.get("v1/gratitude/entries/$id").body()

    suspend fun update(id: String, req: UpdateGratitudeEntryDto): GratitudeEntryDto =
        client.patch("v1/gratitude/entries/$id") { setBody(req) }.body()

    suspend fun toggleFavourite(id: String): GratitudeEntryDto =
        client.post("v1/gratitude/entries/$id/favourite").body()

    suspend fun delete(id: String) {
        client.delete("v1/gratitude/entries/$id")
    }

    /** Delta pull for offline sync: everything touched since [since], deletions included. */
    suspend fun changes(since: String?, limit: Int? = null): GratitudeChangesDto =
        client.get("v1/gratitude/changes") {
            since?.let { parameter("since", it) }
            limit?.let { parameter("limit", it) }
        }.body()

    suspend fun prompts(): GratitudePromptsDto = client.get("v1/gratitude/prompts").body()

    suspend fun stats(): GratitudeStatsDto = client.get("v1/gratitude/stats").body()

    suspend fun insights(month: String? = null): GratitudeInsightsDto =
        client.get("v1/gratitude/insights") { month?.let { parameter("month", it) } }.body()

    suspend fun random(): GratitudeEntryDto = client.get("v1/gratitude/random").body()

    suspend fun throwback(): GratitudeEntryDto = client.get("v1/gratitude/throwback").body()

    suspend fun challenges(): GratitudeChallengesDto = client.get("v1/gratitude/challenges").body()

    suspend fun joinChallenge(id: String, joined: Boolean): GratitudeChallengesDto =
        client.post("v1/gratitude/challenges/$id/join") { setBody(JoinChallengeDto(joined)) }.body()

    suspend fun completeChallengeDay(id: String, day: Int): GratitudeChallengesDto =
        client.post("v1/gratitude/challenges/$id/complete") { setBody(CompleteChallengeDayDto(day)) }.body()

    suspend fun settings(): GratitudeSettingsDto = client.get("v1/gratitude/settings").body()

    suspend fun saveSettings(dto: GratitudeSettingsDto): GratitudeSettingsDto =
        client.put("v1/gratitude/settings") { setBody(dto) }.body()
}
