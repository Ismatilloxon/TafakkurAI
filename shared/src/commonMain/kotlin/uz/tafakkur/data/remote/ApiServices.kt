package uz.tafakkur.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import uz.tafakkur.data.dto.HifzCardsResponseDto
import uz.tafakkur.data.dto.HifzGradeRequestDto
import uz.tafakkur.data.dto.HifzPlanRequestDto
import uz.tafakkur.data.dto.CreateEntryRequestDto
import uz.tafakkur.data.dto.EntriesResponseDto
import uz.tafakkur.data.dto.GamificationEnvelopeDto
import uz.tafakkur.data.dto.GooglePollRequestDto
import uz.tafakkur.data.dto.GooglePollResponseDto
import uz.tafakkur.data.dto.GoogleStartResponseDto
import uz.tafakkur.data.dto.LessonDto
import uz.tafakkur.data.dto.LessonsResponseDto
import uz.tafakkur.data.dto.LikeResponseDto
import uz.tafakkur.data.dto.MeResponseDto
import uz.tafakkur.data.dto.MessageRequestDto
import uz.tafakkur.data.dto.OtpStartRequestDto
import uz.tafakkur.data.dto.OtpStartResponseDto
import uz.tafakkur.data.dto.PhotoUrlDto
import uz.tafakkur.data.dto.PrayerSettingsDto
import uz.tafakkur.data.dto.PrayerTimesResponseDto
import uz.tafakkur.data.dto.PublishRequestDto
import uz.tafakkur.data.dto.AddPhoneStartDto
import uz.tafakkur.data.dto.AddPhoneVerifyDto
import uz.tafakkur.data.dto.ReplaceInterestsDto
import uz.tafakkur.data.dto.SetEmailDto
import uz.tafakkur.data.dto.SessionRequestDto
import uz.tafakkur.data.dto.SessionResponseDto
import uz.tafakkur.data.dto.SocialPostDto
import uz.tafakkur.data.dto.CoachHistoryDto
import uz.tafakkur.data.dto.SubjectsResponseDto
import uz.tafakkur.data.dto.TelegramBotDto
import uz.tafakkur.data.dto.TelegramVerifyRequestDto
import uz.tafakkur.data.dto.TrackPrayerRequestDto
import uz.tafakkur.data.dto.TrackerEntryDto
import uz.tafakkur.data.dto.UpdateProfileDto
import uz.tafakkur.data.dto.UserProfileDto
import uz.tafakkur.network.SseFrame

class AuthApi(private val client: HttpClient) {
    suspend fun startOtp(phone: String): OtpStartResponseDto =
        client.post("v1/auth/otp/start") { setBody(OtpStartRequestDto(phone)) }.body()

    suspend fun session(req: SessionRequestDto): SessionResponseDto =
        client.post("v1/auth/session") { setBody(req) }.body()

    suspend fun telegramBot(): TelegramBotDto = client.get("v1/auth/telegram/bot").body()

    // Google runs as a browser round trip: the server hands back a URL to open and a
    // state to poll on, so no Google SDK is linked into either platform.
    suspend fun googleStart(): GoogleStartResponseDto = client.post("v1/auth/google/start").body()

    suspend fun googlePoll(state: String): GooglePollResponseDto =
        client.post("v1/auth/google/poll") { setBody(GooglePollRequestDto(state)) }.body()

    suspend fun telegramVerify(code: String): SessionResponseDto =
        client.post("v1/auth/telegram/verify") { setBody(TelegramVerifyRequestDto(code)) }.body()

    suspend fun logout() {
        client.post("v1/auth/logout")
    }
}

class UserApi(private val client: HttpClient) {
    suspend fun me(): MeResponseDto = client.get("v1/me").body()

    suspend fun updateProfile(dto: UpdateProfileDto): UserProfileDto =
        client.post("v1/me/profile") { method = io.ktor.http.HttpMethod.Put; setBody(dto) }.body()

    suspend fun replaceInterests(dto: ReplaceInterestsDto): UserProfileDto =
        client.post("v1/me/interests") { method = io.ktor.http.HttpMethod.Put; setBody(dto) }.body()

    suspend fun updatePrayerSettings(dto: PrayerSettingsDto): UserProfileDto =
        client.post("v1/me/prayer-settings") { method = io.ktor.http.HttpMethod.Put; setBody(dto) }.body()

    suspend fun completeOnboarding(): UserProfileDto =
        client.post("v1/me/onboarding/complete").body()

    // ── Completing the contact detail sign-in could not supply ──
    // The address is saved as given; the number has to survive an SMS round trip
    // first, which is why it takes two calls and the email takes one.
    suspend fun setEmail(email: String): UserProfileDto =
        client.post("v1/me/email") { method = io.ktor.http.HttpMethod.Put; setBody(SetEmailDto(email)) }.body()

    suspend fun addPhoneStart(phone: String): OtpStartResponseDto =
        client.post("v1/me/phone/start") { setBody(AddPhoneStartDto(phone)) }.body()

    suspend fun addPhoneVerify(verificationId: String, code: String): UserProfileDto =
        client.post("v1/me/phone/verify") { setBody(AddPhoneVerifyDto(verificationId, code)) }.body()

    suspend fun uploadPhoto(bytes: ByteArray, filename: String, contentType: String): PhotoUrlDto =
        client.post("v1/me/photo") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("photo", bytes, Headers.build {
                            append(HttpHeaders.ContentType, contentType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                        })
                    },
                ),
            )
        }.body()
}

class PrayerApi(private val client: HttpClient) {
    suspend fun times(
        lat: Double,
        lng: Double,
        date: String?,
        method: String?,
        madhab: String?,
        tz: String?,
    ): PrayerTimesResponseDto = client.get("v1/prayer/times") {
        parameter("lat", lat)
        parameter("lng", lng)
        date?.let { parameter("date", it) }
        method?.let { parameter("method", it) }
        madhab?.let { parameter("madhab", it) }
        tz?.let { parameter("tz", it) }
    }.body()

    suspend fun track(req: TrackPrayerRequestDto): GamificationEnvelopeDto =
        client.post("v1/prayer/track") { setBody(req) }.body()
}

class FeedApi(private val client: HttpClient) {
    suspend fun feed(cursor: String?, limit: Int?): uz.tafakkur.data.dto.FeedPageDto =
        client.get("v1/thoughts") {
            cursor?.let { parameter("cursor", it) }
            limit?.let { parameter("limit", it) }
        }.body()

    suspend fun publish(req: PublishRequestDto): SocialPostDto =
        client.post("v1/thoughts") { setBody(req) }.body()

    suspend fun toggleLike(id: String): LikeResponseDto =
        client.post("v1/thoughts/$id/like").body()
}

class LearnApi(private val client: HttpClient) {
    suspend fun subjects(): SubjectsResponseDto = client.get("v1/subjects").body()
    suspend fun lessons(subjectId: String): LessonsResponseDto =
        client.get("v1/subjects/$subjectId/lessons").body()
    suspend fun lesson(id: String): LessonDto = client.get("v1/lessons/$id").body()
    suspend fun complete(id: String): GamificationEnvelopeDto =
        client.post("v1/lessons/$id/complete").body()
}

class TrackerApi(private val client: HttpClient) {
    suspend fun create(kind: String, req: CreateEntryRequestDto): TrackerEntryDto =
        client.post("v1/trackers/$kind") { setBody(req) }.body()

    suspend fun list(kind: String, from: String?, to: String?): EntriesResponseDto =
        client.get("v1/trackers/$kind") {
            from?.let { parameter("from", it) }
            to?.let { parameter("to", it) }
        }.body()
}

class CoachApi(private val client: HttpClient) {
    suspend fun history(conversationId: String, cursor: String?, limit: Int?): CoachHistoryDto =
        client.get("v1/coach/history") {
            parameter("conversation_id", conversationId)
            cursor?.let { parameter("cursor", it) }
            limit?.let { parameter("limit", it) }
        }.body()

    /** Streams the coach reply as decoded SSE frames (event + data). */
    fun stream(req: MessageRequestDto): Flow<SseFrame> = flow {
        client.preparePost("v1/coach/message") {
            accept(ContentType.Text.EventStream)
            setBody(req)
        }.execute { response ->
            val channel = response.bodyAsChannel()
            var currentEvent = "message"
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isEmpty() -> currentEvent = "message"
                    line.startsWith("event:") -> currentEvent = line.substringAfter("event:").trim()
                    line.startsWith("data:") -> emit(SseFrame(currentEvent, line.substringAfter("data:").trim()))
                }
            }
        }
    }
}

/**
 * Qurʼan memorization. Separate from [LearnApi] because it is not lesson content: these
 * endpoints carry one user's schedule, not the shared curriculum.
 */
class QuranApi(private val client: HttpClient) {
    suspend fun hifzCards(): HifzCardsResponseDto = client.get("v1/quran/hifz").body()

    suspend fun gradeHifz(req: HifzGradeRequestDto): GamificationEnvelopeDto =
        client.post("v1/quran/hifz/grade") { setBody(req) }.body()

    suspend fun setHifzPlan(req: HifzPlanRequestDto): HifzPlanRequestDto =
        client.put("v1/quran/hifz/plan") { setBody(req) }.body()
}
