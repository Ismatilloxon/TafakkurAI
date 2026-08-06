package uz.tafakkur.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------------- Auth ----------------

@Serializable
data class SessionRequestDto(
    val provider: String,
    @SerialName("id_token") val idToken: String? = null,
    val phone: String? = null,
    @SerialName("otp_code") val otpCode: String? = null,
    @SerialName("verification_id") val verificationId: String? = null,
)

@Serializable
data class SessionResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: UserProfileDto,
    @SerialName("needs_onboarding") val needsOnboarding: Boolean,
)

@Serializable
data class OtpStartRequestDto(val phone: String)

@Serializable
data class OtpStartResponseDto(@SerialName("verification_id") val verificationId: String)

@Serializable
data class TelegramBotDto(
    @SerialName("bot_username") val botUsername: String,
    @SerialName("deep_link") val deepLink: String,
    @SerialName("code_length") val codeLength: Int,
)

@Serializable
data class TelegramVerifyRequestDto(val code: String)

@Serializable
data class GoogleStartResponseDto(
    val state: String,
    @SerialName("auth_url") val authUrl: String,
    @SerialName("poll_interval_ms") val pollIntervalMs: Long,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
data class GooglePollRequestDto(val state: String)

/** `status` is "pending" until the browser leg finishes; only then is `session` present. */
@Serializable
data class GooglePollResponseDto(
    val status: String,
    val session: SessionResponseDto? = null,
)

@Serializable
data class RefreshRequestDto(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class RefreshResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
)

// ---------------- Profile / user ----------------

@Serializable
data class UserProfileDto(
    val uid: String,
    @SerialName("display_name") val displayName: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val provider: String,
    val handle: String,
    val gender: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    val city: String? = null,
    val country: String? = null,
    @SerialName("language_tag") val languageTag: String,
    val madhab: String,
    @SerialName("calc_method") val calcMethod: String,
    val interests: List<String> = emptyList(),
    @SerialName("onboarding_complete") val onboardingComplete: Boolean,
    // Which contact detail the sign-in provider could not supply. Defaulted so an
    // older backend that does not send them still deserialises.
    @SerialName("needs_phone") val needsPhone: Boolean = false,
    @SerialName("needs_email") val needsEmail: Boolean = false,
)

@Serializable
data class SetEmailDto(val email: String)

@Serializable
data class AddPhoneStartDto(val phone: String)

@Serializable
data class AddPhoneVerifyDto(
    @SerialName("verification_id") val verificationId: String,
    val code: String,
)

@Serializable
data class GamificationDto(
    val streak: Int,
    val level: Int,
    val xp: Int,
    @SerialName("xp_next") val xpNext: Int,
)

@Serializable
data class MeResponseDto(val user: UserProfileDto, val gamification: GamificationDto)

@Serializable
data class UpdateProfileDto(
    @SerialName("display_name") val displayName: String? = null,
    val handle: String? = null,
    val gender: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    val language: String? = null,
)

@Serializable
data class ReplaceInterestsDto(val interests: List<String>)

@Serializable
data class PrayerSettingsDto(
    val city: String? = null,
    val country: String? = null,
    val madhab: String,
    @SerialName("calc_method") val calcMethod: String,
)

@Serializable
data class PhotoUrlDto(val url: String)

// ---------------- Prayer ----------------

@Serializable
data class PrayerTimeDto(val name: String, val time: String)

@Serializable
data class NextPrayerDto(
    val name: String,
    val time: String,
    @SerialName("seconds_remaining") val secondsRemaining: Long,
)

@Serializable
data class PrayerTimesResponseDto(
    val date: String,
    val latitude: Double,
    val longitude: Double,
    val method: String,
    val madhab: String,
    val times: List<PrayerTimeDto>,
    val next: NextPrayerDto,
)

@Serializable
data class TrackPrayerRequestDto(val prayer: String, val date: String? = null)

@Serializable
data class GamificationEnvelopeDto(val ok: Boolean = true, val gamification: GamificationDto)

// ---------------- Feed ----------------

@Serializable
data class SocialPostDto(
    val id: String,
    val text: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_handle") val authorHandle: String = "",
    @SerialName("author_photo_url") val authorPhotoUrl: String? = null,
    @SerialName("like_count") val likeCount: Int,
    @SerialName("liked_by_me") val likedByMe: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FeedPageDto(
    val posts: List<SocialPostDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class PublishRequestDto(val text: String)

@Serializable
data class LikeResponseDto(val liked: Boolean, @SerialName("like_count") val likeCount: Int)

// ---------------- Coach ----------------

@Serializable
data class MessageRequestDto(
    @SerialName("conversation_id") val conversationId: String? = null,
    val message: String,
)

@Serializable
data class CoachMessageDto(
    val id: String,
    val role: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CoachHistoryDto(
    @SerialName("conversation_id") val conversationId: String,
    val messages: List<CoachMessageDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

// SSE data payloads
@Serializable
data class CoachDeltaDto(val type: String = "delta", val text: String = "")

@Serializable
data class CoachDoneDto(val type: String = "done", @SerialName("conversation_id") val conversationId: String = "")

@Serializable
data class CoachErrorDto(val message: String = "")

// ---------------- Learn ----------------

@Serializable
data class SubjectDto(
    val id: String,
    val title: String,
    val description: String,
    @SerialName("order_index") val orderIndex: Int,
)

@Serializable
data class LessonDto(
    val id: String,
    @SerialName("subject_id") val subjectId: String,
    val title: String,
    val body: String,
    @SerialName("order_index") val orderIndex: Int,
)

@Serializable
data class SubjectsResponseDto(val subjects: List<SubjectDto> = emptyList())

@Serializable
data class LessonsResponseDto(val lessons: List<LessonDto> = emptyList())

// ---------------- Tracker ----------------

@Serializable
data class CreateEntryRequestDto(
    @SerialName("entry_date") val entryDate: String? = null,
    val payload: JsonElement,
)

@Serializable
data class TrackerEntryDto(
    val id: String,
    val kind: String,
    val payload: JsonElement,
    @SerialName("entry_date") val entryDate: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class EntriesResponseDto(val entries: List<TrackerEntryDto> = emptyList())

// ---------------- Hifz (Qurʼan memorization) ----------------

/**
 * One graded ayah. The reference and the rating travel; the ayah text never does — the
 * backend resolves scripture the same way the client does, from the mushaf, not from us.
 */
@Serializable
data class HifzGradeRequestDto(
    val surah: Int,
    val ayah: Int,
    val rating: Int,
    @SerialName("reviewed_at") val reviewedAt: Long,
    // The schedule the device computed. The server stores it rather than recomputing it,
    // so one card can never have two different due dates.
    val state: Int,
    val stability: Double,
    val difficulty: Double,
    val due: Long,
)

@Serializable
data class HifzCardDto(
    val surah: Int,
    val ayah: Int,
    val state: Int,
    val stability: Double,
    val difficulty: Double,
    val due: Long,
    @SerialName("last_review") val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
)

@Serializable
data class HifzCardsResponseDto(val cards: List<HifzCardDto> = emptyList())

@Serializable
data class HifzPlanRequestDto(
    @SerialName("daily_new") val dailyNew: Int,
    @SerialName("review_cap") val reviewCap: Int,
)
