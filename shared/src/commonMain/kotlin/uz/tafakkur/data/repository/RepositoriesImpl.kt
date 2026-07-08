package uz.tafakkur.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val tokens: TokenProvider,
) : AuthRepository {

    override suspend fun startPhoneOtp(phone: String): String =
        api.startOtp(phone).verificationId

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
        val resp = api.session(req)
        tokens.update(resp.accessToken, resp.refreshToken)
        return resp.toDomain()
    }

    override suspend fun logout() {
        try {
            api.logout()
        } finally {
            tokens.clear()
        }
    }

    override fun isLoggedIn(): Boolean = tokens.isLoggedIn()
}

class UserRepositoryImpl(private val api: UserApi) : UserRepository {
    override suspend fun getMe(): Me = api.me().toDomain()

    override suspend fun updateProfile(
        displayName: String?,
        handle: String?,
        gender: String?,
        birthDate: String?,
        language: String?,
    ): UserProfile = api.updateProfile(
        UpdateProfileDto(displayName, handle, gender, birthDate, language),
    ).toDomain()

    override suspend fun replaceInterests(interests: List<String>): UserProfile =
        api.replaceInterests(ReplaceInterestsDto(interests)).toDomain()

    override suspend fun updatePrayerSettings(
        city: String?,
        country: String?,
        madhab: String,
        calcMethod: String,
    ): UserProfile = api.updatePrayerSettings(PrayerSettingsDto(city, country, madhab, calcMethod)).toDomain()

    override suspend fun completeOnboarding(): UserProfile = api.completeOnboarding().toDomain()

    override suspend fun uploadPhoto(bytes: ByteArray, filename: String, contentType: String): String =
        api.uploadPhoto(bytes, filename, contentType).url
}

class PrayerRepositoryImpl(private val api: PrayerApi) : PrayerRepository {
    override suspend fun getTimes(
        lat: Double,
        lng: Double,
        date: String?,
        method: String?,
        madhab: String?,
        tz: String?,
    ): PrayerTimes = api.times(lat, lng, date, method, madhab, tz).toDomain()

    override suspend fun track(prayer: String, date: String?): Gamification =
        api.track(TrackPrayerRequestDto(prayer, date)).gamification.toDomain()
}

class FeedRepositoryImpl(private val api: FeedApi) : FeedRepository {
    override suspend fun feed(cursor: String?, limit: Int?): FeedPage =
        api.feed(cursor, limit).toDomain()

    override suspend fun publish(text: String): SocialPost =
        api.publish(PublishRequestDto(text)).toDomain()

    override suspend fun toggleLike(thoughtId: String): LikeResult {
        val r = api.toggleLike(thoughtId)
        return LikeResult(r.liked, r.likeCount)
    }
}

class CoachRepositoryImpl(private val api: CoachApi) : CoachRepository {
    override fun streamMessage(conversationId: String?, message: String): Flow<CoachEvent> =
        api.stream(MessageRequestDto(conversationId, message))
            .map { it.toCoachEvent() }
            .catch { emit(CoachEvent.Error(it.message ?: "coach stream failed")) }

    override suspend fun history(conversationId: String, cursor: String?, limit: Int?): CoachHistory =
        api.history(conversationId, cursor, limit).toDomain()

    private fun SseFrame.toCoachEvent(): CoachEvent = when (event) {
        "done" -> CoachEvent.Done(appJson.decodeFromString(CoachDoneDto.serializer(), data).conversationId)
        "error" -> CoachEvent.Error(appJson.decodeFromString(CoachErrorDto.serializer(), data).message)
        else -> CoachEvent.Delta(appJson.decodeFromString(CoachDeltaDto.serializer(), data).text)
    }
}

class LearnRepositoryImpl(private val api: LearnApi) : LearnRepository {
    override suspend fun subjects(): List<Subject> = api.subjects().subjects.map { it.toDomain() }
    override suspend fun lessons(subjectId: String): List<Lesson> =
        api.lessons(subjectId).lessons.map { it.toDomain() }
    override suspend fun lesson(lessonId: String): Lesson = api.lesson(lessonId).toDomain()
    override suspend fun complete(lessonId: String): Gamification =
        api.complete(lessonId).gamification.toDomain()
}

class TrackerRepositoryImpl(private val api: TrackerApi) : TrackerRepository {
    override suspend fun create(kind: String, payloadJson: String, entryDate: String?): TrackerEntry {
        val payload = appJson.parseToJsonElement(payloadJson)
        return api.create(kind, CreateEntryRequestDto(entryDate, payload)).toDomain()
    }

    override suspend fun list(kind: String, from: String?, to: String?): List<TrackerEntry> =
        api.list(kind, from, to).entries.map { it.toDomain() }
}
