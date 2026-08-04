package uz.tafakkur.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the v1 gratitude endpoints (backend module `internal/gratitude`).
 * Field names follow the snake_case the Go handlers emit.
 */

@Serializable
data class GratitudeEntryDto(
    val id: String,
    @SerialName("client_id") val clientId: String,
    val mode: String = "quick",
    val text: String,
    val items: List<String> = emptyList(),
    val why: String? = null,
    val category: String = "moments",
    val mood: String? = null,
    val emotions: List<String> = emptyList(),
    @SerialName("photo_count") val photoCount: Int = 0,
    @SerialName("has_audio") val hasAudio: Boolean = false,
    @SerialName("has_video") val hasVideo: Boolean = false,
    @SerialName("location_label") val locationLabel: String? = null,
    val favourite: Boolean = false,
    @SerialName("entry_date") val entryDate: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val deleted: Boolean = false,
)

@Serializable
data class CreateGratitudeEntryDto(
    @SerialName("client_id") val clientId: String,
    val mode: String,
    val text: String,
    val items: List<String> = emptyList(),
    val why: String? = null,
    val category: String,
    val mood: String? = null,
    val emotions: List<String> = emptyList(),
    @SerialName("photo_count") val photoCount: Int = 0,
    @SerialName("has_audio") val hasAudio: Boolean = false,
    @SerialName("has_video") val hasVideo: Boolean = false,
    @SerialName("location_label") val locationLabel: String? = null,
    val favourite: Boolean = false,
    @SerialName("entry_date") val entryDate: String? = null,
)

@Serializable
data class UpdateGratitudeEntryDto(
    val text: String? = null,
    val why: String? = null,
    val category: String? = null,
    val mood: String? = null,
    val emotions: List<String>? = null,
    val favourite: Boolean? = null,
)

@Serializable
data class GratitudeEntriesDto(
    val entries: List<GratitudeEntryDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class GratitudeChangesDto(
    val entries: List<GratitudeEntryDto> = emptyList(),
    val cursor: String = "",
    val more: Boolean = false,
)

@Serializable
data class GratitudePromptsDto(val prompts: List<String> = emptyList())

@Serializable
data class GratitudeStatsDto(
    val streak: Int = 0,
    val longest: Int = 0,
    val total: Int = 0,
    @SerialName("month_days") val monthDays: Int = 0,
    val week: List<Boolean> = emptyList(),
)

@Serializable
data class GratitudeCategoryShareDto(
    val key: String,
    val label: String = "",
    val percent: Int = 0,
    val count: Int = 0,
)

@Serializable
data class GratitudeMonthDto(
    val name: String = "",
    val year: Int = 0,
    val entries: Int = 0,
    val days: Int = 0,
    val longest: Int = 0,
    @SerialName("top_category") val topCategory: String = "",
    val line: String = "",
)

@Serializable
data class GratitudeInsightsDto(
    val stats: GratitudeStatsDto = GratitudeStatsDto(),
    @SerialName("mood_week") val moodWeek: List<Int> = emptyList(),
    val categories: List<GratitudeCategoryShareDto> = emptyList(),
    val reflection: String = "",
    val month: GratitudeMonthDto = GratitudeMonthDto(),
)

@Serializable
data class GratitudeChallengeDto(
    val id: String,
    val title: String = "",
    val sub: String = "",
    val total: Int = 0,
    val days: List<String> = emptyList(),
    val joined: Boolean = false,
    val done: Int = 0,
    @SerialName("done_days") val doneDays: List<Int> = emptyList(),
)

@Serializable
data class GratitudeChallengesDto(val challenges: List<GratitudeChallengeDto> = emptyList())

@Serializable
data class JoinChallengeDto(val joined: Boolean)

@Serializable
data class CompleteChallengeDayDto(val day: Int)

@Serializable
data class GratitudeSettingsDto(
    @SerialName("reminder_slot") val reminderSlot: String = "evening",
    @SerialName("reminder_time") val reminderTime: String = "19:30",
    @SerialName("face_id") val faceId: Boolean = false,
    @SerialName("app_lock") val appLock: Boolean = false,
    @SerialName("hide_preview") val hidePreview: Boolean = true,
    @SerialName("cloud_sync") val cloudSync: Boolean = true,
    @SerialName("updated_at") val updatedAt: String = "",
)
