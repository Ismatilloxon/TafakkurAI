package uz.tafakkur.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import uz.tafakkur.core.feedback.CallInfo
import uz.tafakkur.core.feedback.FeedbackKeys
import uz.tafakkur.core.feedback.NetworkExecutor
import uz.tafakkur.data.dto.CoachDeltaDto
import uz.tafakkur.data.dto.CoachDoneDto
import uz.tafakkur.data.dto.CoachErrorDto
import uz.tafakkur.data.dto.CreateEntryRequestDto
import uz.tafakkur.data.dto.MessageRequestDto
import uz.tafakkur.data.dto.PrayerSettingsDto
import uz.tafakkur.data.dto.PublishRequestDto
import uz.tafakkur.data.dto.ReplaceInterestsDto
import uz.tafakkur.data.dto.SessionRequestDto
import uz.tafakkur.data.dto.TrackPrayerRequestDto
import uz.tafakkur.data.dto.UpdateProfileDto
import uz.tafakkur.data.mapper.toDomain
import uz.tafakkur.data.remote.AuthApi
import uz.tafakkur.data.remote.CoachApi
import uz.tafakkur.data.remote.FeedApi
import uz.tafakkur.data.remote.LearnApi
import uz.tafakkur.data.remote.PrayerApi
import uz.tafakkur.data.remote.TrackerApi
import uz.tafakkur.data.remote.UserApi
import uz.tafakkur.domain.model.AuthProvider
import uz.tafakkur.domain.model.AuthSession
import uz.tafakkur.domain.model.CoachEvent
import uz.tafakkur.domain.model.CoachHistory
import uz.tafakkur.domain.model.FeedPage
import uz.tafakkur.domain.model.Gamification
import uz.tafakkur.domain.model.Lesson
import uz.tafakkur.domain.model.LikeResult
import uz.tafakkur.domain.model.Me
import uz.tafakkur.domain.model.PrayerTimes
import uz.tafakkur.domain.model.SocialPost
import uz.tafakkur.domain.model.Subject
import uz.tafakkur.domain.model.TrackerEntry
import uz.tafakkur.domain.model.UserProfile
import uz.tafakkur.domain.repository.AuthRepository
import uz.tafakkur.domain.repository.CoachRepository
import uz.tafakkur.domain.repository.FeedRepository
import uz.tafakkur.domain.repository.LearnRepository
import uz.tafakkur.domain.repository.PrayerRepository
import uz.tafakkur.domain.repository.TrackerRepository
import uz.tafakkur.domain.repository.UserRepository
import uz.tafakkur.network.SseFrame
import uz.tafakkur.network.TokenProvider
import uz.tafakkur.network.appJson

/**
 * Every network-backed repository routes its calls through [NetworkExecutor.call]. That one
 * wrapper is what gives the whole app its global loader, centralised error→snackbar handling
 * and per-request logging, so the individual methods stay as thin as they were — the only new
 * thing each one declares is *what kind of feedback it deserves*:
 *
 *  - `successKey`   — a success snackbar, set only for meaningful mutations (never GETs).
 *  - `showLoader`   — off for tiny/optimistic calls (like/track) so the global loader never
 *                     flashes for a one-tap action.
 *  - `reportErrors` — off for background/outbox work that is already best-effort; those calls
 *                     are still mapped and logged, just not surfaced as a toast.
 */

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val tokens: TokenProvider,
    private val executor: NetworkExecutor,
) : AuthRepository {

    override suspend fun startPhoneOtp(phone: String): String =
        executor.call(CallInfo("POST", "v1/auth/otp/start")) {
            api.startOtp(phone).verificationId
        }

    override suspend fun signIn(provider: AuthProvider): AuthSession {
        val req = when (provider) {
            is AuthProvider.Google -> SessionRequestDto(provider = "google", idToken = provider.idToken)
            is AuthProvider.Apple -> SessionRequestDto(provider = "apple", idToken = provider.idToken)
            is AuthProvider.Phone -> SessionRequestDto(
                provider = "phone",
                verificationId = provider.verificationId,
                otpCode = provider.code,
            )
        }
        return executor.call(CallInfo("POST", "v1/auth/session"), successKey = FeedbackKeys.LOGIN) {
            val resp = api.session(req)
            tokens.update(resp.accessToken, resp.refreshToken)
            resp.toDomain()
        }
    }

    override suspend fun logout() {
        // Best-effort: clear local tokens even if the server call fails, and don't nag the
        // user with an error toast for a sign-out that already succeeded locally.
        try {
            executor.call(CallInfo("POST", "v1/auth/logout"), showLoader = false, reportErrors = false) {
                api.logout()
            }
        } finally {
            tokens.clear()
        }
    }

    override fun isLoggedIn(): Boolean = tokens.isLoggedIn()
}

class UserRepositoryImpl(
    private val api: UserApi,
    private val executor: NetworkExecutor,
) : UserRepository {

    override suspend fun getMe(): Me =
        executor.call(CallInfo("GET", "v1/me")) { api.me().toDomain() }

    override suspend fun updateProfile(
        displayName: String?,
        handle: String?,
        gender: String?,
        birthDate: String?,
        language: String?,
    ): UserProfile = executor.call(
        CallInfo("PUT", "v1/me/profile"),
        successKey = FeedbackKeys.PROFILE_UPDATED,
    ) {
        api.updateProfile(UpdateProfileDto(displayName, handle, gender, birthDate, language)).toDomain()
    }

    override suspend fun replaceInterests(interests: List<String>): UserProfile = executor.call(
        CallInfo("PUT", "v1/me/interests"),
        successKey = FeedbackKeys.INTERESTS_SAVED,
    ) {
        api.replaceInterests(ReplaceInterestsDto(interests)).toDomain()
    }

    override suspend fun updatePrayerSettings(
        city: String?,
        country: String?,
        madhab: String,
        calcMethod: String,
    ): UserProfile = executor.call(
        CallInfo("PUT", "v1/me/prayer-settings"),
        successKey = FeedbackKeys.SETTINGS_SAVED,
    ) {
        api.updatePrayerSettings(PrayerSettingsDto(city, country, madhab, calcMethod)).toDomain()
    }

    // Part of the onboarding flow — its own screen shows the outcome, so stay quiet here.
    override suspend fun completeOnboarding(): UserProfile =
        executor.call(CallInfo("POST", "v1/me/onboarding/complete")) {
            api.completeOnboarding().toDomain()
        }

    override suspend fun uploadPhoto(bytes: ByteArray, filename: String, contentType: String): String =
        executor.call(CallInfo("POST", "v1/me/photo"), successKey = FeedbackKeys.PHOTO_UPDATED) {
            api.uploadPhoto(bytes, filename, contentType).url
        }
}

class PrayerRepositoryImpl(
    private val api: PrayerApi,
    private val executor: NetworkExecutor,
) : PrayerRepository {

    override suspend fun getTimes(
        lat: Double,
        lng: Double,
        date: String?,
        method: String?,
        madhab: String?,
        tz: String?,
    ): PrayerTimes = executor.call(CallInfo("GET", "v1/prayer/times"), showLoader = false) {
        api.times(lat, lng, date, method, madhab, tz).toDomain()
    }

    // Called by the outbox drain after the user marked a prayer locally; feedback already
    // happened on-device, so this backend sync is log-only.
    override suspend fun track(prayer: String, date: String?): Gamification =
        executor.call(CallInfo("POST", "v1/prayer/track"), showLoader = false, reportErrors = false) {
            api.track(TrackPrayerRequestDto(prayer, date)).gamification.toDomain()
        }
}

class FeedRepositoryImpl(
    private val api: FeedApi,
    private val executor: NetworkExecutor,
) : FeedRepository {

    override suspend fun feed(cursor: String?, limit: Int?): FeedPage =
        executor.call(CallInfo("GET", "v1/thoughts")) { api.feed(cursor, limit).toDomain() }

    override suspend fun publish(text: String): SocialPost =
        executor.call(CallInfo("POST", "v1/thoughts"), successKey = FeedbackKeys.THOUGHT_PUBLISHED) {
            api.publish(PublishRequestDto(text)).toDomain()
        }

    // Optimistic one-tap action: no loader; a failure still surfaces so the UI can revert.
    override suspend fun toggleLike(thoughtId: String): LikeResult =
        executor.call(CallInfo("POST", "v1/thoughts/$thoughtId/like"), showLoader = false) {
            val r = api.toggleLike(thoughtId)
            LikeResult(r.liked, r.likeCount)
        }
}

class CoachRepositoryImpl(
    private val api: CoachApi,
    private val executor: NetworkExecutor,
) : CoachRepository {

    // Streaming SSE: the coach screen renders its own typing/error state token by token, so it
    // is intentionally NOT wrapped in the executor (no global loader for a live stream).
    override fun streamMessage(conversationId: String?, message: String): Flow<CoachEvent> =
        api.stream(MessageRequestDto(conversationId, message))
            .map { it.toCoachEvent() }
            .catch { emit(CoachEvent.Error(it.message ?: "coach stream failed")) }

    override suspend fun history(conversationId: String, cursor: String?, limit: Int?): CoachHistory =
        executor.call(CallInfo("GET", "v1/coach/history")) {
            api.history(conversationId, cursor, limit).toDomain()
        }

    private fun SseFrame.toCoachEvent(): CoachEvent = when (event) {
        "done" -> CoachEvent.Done(appJson.decodeFromString(CoachDoneDto.serializer(), data).conversationId)
        "error" -> CoachEvent.Error(appJson.decodeFromString(CoachErrorDto.serializer(), data).message)
        else -> CoachEvent.Delta(appJson.decodeFromString(CoachDeltaDto.serializer(), data).text)
    }
}

class LearnRepositoryImpl(
    private val api: LearnApi,
    private val executor: NetworkExecutor,
) : LearnRepository {

    override suspend fun subjects(): List<Subject> =
        executor.call(CallInfo("GET", "v1/subjects")) { api.subjects().subjects.map { it.toDomain() } }

    override suspend fun lessons(subjectId: String): List<Lesson> =
        executor.call(CallInfo("GET", "v1/subjects/$subjectId/lessons")) {
            api.lessons(subjectId).lessons.map { it.toDomain() }
        }

    override suspend fun lesson(lessonId: String): Lesson =
        executor.call(CallInfo("GET", "v1/lessons/$lessonId")) { api.lesson(lessonId).toDomain() }

    override suspend fun complete(lessonId: String): Gamification =
        executor.call(CallInfo("POST", "v1/lessons/$lessonId/complete"), successKey = FeedbackKeys.LESSON_COMPLETED) {
            api.complete(lessonId).gamification.toDomain()
        }
}

class TrackerRepositoryImpl(
    private val api: TrackerApi,
    private val executor: NetworkExecutor,
) : TrackerRepository {

    override suspend fun create(kind: String, payloadJson: String, entryDate: String?): TrackerEntry =
        executor.call(CallInfo("POST", "v1/trackers/$kind"), successKey = FeedbackKeys.ENTRY_SAVED) {
            val payload = appJson.parseToJsonElement(payloadJson)
            api.create(kind, CreateEntryRequestDto(entryDate, payload)).toDomain()
        }

    override suspend fun list(kind: String, from: String?, to: String?): List<TrackerEntry> =
        executor.call(CallInfo("GET", "v1/trackers/$kind")) {
            api.list(kind, from, to).entries.map { it.toDomain() }
        }
}
