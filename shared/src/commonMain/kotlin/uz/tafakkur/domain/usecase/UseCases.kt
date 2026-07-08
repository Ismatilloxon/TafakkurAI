package uz.tafakkur.domain.usecase

import kotlinx.coroutines.flow.Flow
import uz.tafakkur.domain.model.AuthProvider
import uz.tafakkur.domain.model.CoachEvent
import uz.tafakkur.domain.repository.AuthRepository
import uz.tafakkur.domain.repository.CoachRepository
import uz.tafakkur.domain.repository.FeedRepository
import uz.tafakkur.domain.repository.LearnRepository
import uz.tafakkur.domain.repository.PrayerRepository
import uz.tafakkur.domain.repository.TrackerRepository
import uz.tafakkur.domain.repository.UserRepository

// ---- Auth ----
class StartPhoneOtpUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(phone: String) = repo.startPhoneOtp(phone)
}
class SignInUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(provider: AuthProvider) = repo.signIn(provider)
}
class LogoutUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke() = repo.logout()
}
class IsLoggedInUseCase(private val repo: AuthRepository) {
    operator fun invoke() = repo.isLoggedIn()
}

// ---- Profile ----
class GetMeUseCase(private val repo: UserRepository) {
    suspend operator fun invoke() = repo.getMe()
}
class UpdateProfileUseCase(private val repo: UserRepository) {
    suspend operator fun invoke(
        displayName: String? = null,
        handle: String? = null,
        gender: String? = null,
        birthDate: String? = null,
        language: String? = null,
    ) = repo.updateProfile(displayName, handle, gender, birthDate, language)
}
class ReplaceInterestsUseCase(private val repo: UserRepository) {
    suspend operator fun invoke(interests: List<String>) = repo.replaceInterests(interests)
}
class UpdatePrayerSettingsUseCase(private val repo: UserRepository) {
    suspend operator fun invoke(city: String?, country: String?, madhab: String, calcMethod: String) =
        repo.updatePrayerSettings(city, country, madhab, calcMethod)
}
class CompleteOnboardingUseCase(private val repo: UserRepository) {
    suspend operator fun invoke() = repo.completeOnboarding()
}
class UploadPhotoUseCase(private val repo: UserRepository) {
    suspend operator fun invoke(bytes: ByteArray, filename: String, contentType: String) =
        repo.uploadPhoto(bytes, filename, contentType)
}

// ---- Prayer ----
class GetPrayerTimesUseCase(private val repo: PrayerRepository) {
    suspend operator fun invoke(
        lat: Double,
        lng: Double,
        date: String? = null,
        method: String? = null,
        madhab: String? = null,
        tz: String? = null,
    ) = repo.getTimes(lat, lng, date, method, madhab, tz)
}
class TrackPrayerUseCase(private val repo: PrayerRepository) {
    suspend operator fun invoke(prayer: String, date: String? = null) = repo.track(prayer, date)
}

// ---- Feed ----
class GetFeedUseCase(private val repo: FeedRepository) {
    suspend operator fun invoke(cursor: String? = null, limit: Int? = null) = repo.feed(cursor, limit)
}
class PublishThoughtUseCase(private val repo: FeedRepository) {
    suspend operator fun invoke(text: String) = repo.publish(text)
}
class ToggleLikeUseCase(private val repo: FeedRepository) {
    suspend operator fun invoke(thoughtId: String) = repo.toggleLike(thoughtId)
}

// ---- Coach ----
class SendCoachMessageUseCase(private val repo: CoachRepository) {
    operator fun invoke(message: String, conversationId: String? = null): Flow<CoachEvent> =
        repo.streamMessage(conversationId, message)
}
class GetCoachHistoryUseCase(private val repo: CoachRepository) {
    suspend operator fun invoke(conversationId: String, cursor: String? = null, limit: Int? = null) =
        repo.history(conversationId, cursor, limit)
}

// ---- Learn ----
class GetSubjectsUseCase(private val repo: LearnRepository) {
    suspend operator fun invoke() = repo.subjects()
}
class GetLessonsUseCase(private val repo: LearnRepository) {
    suspend operator fun invoke(subjectId: String) = repo.lessons(subjectId)
}
class GetLessonUseCase(private val repo: LearnRepository) {
    suspend operator fun invoke(lessonId: String) = repo.lesson(lessonId)
}
class CompleteLessonUseCase(private val repo: LearnRepository) {
    suspend operator fun invoke(lessonId: String) = repo.complete(lessonId)
}

// ---- Trackers ----
class CreateTrackerEntryUseCase(private val repo: TrackerRepository) {
    suspend operator fun invoke(kind: String, payloadJson: String, entryDate: String? = null) =
        repo.create(kind, payloadJson, entryDate)
}
class GetTrackerEntriesUseCase(private val repo: TrackerRepository) {
    suspend operator fun invoke(kind: String, from: String? = null, to: String? = null) =
        repo.list(kind, from, to)
}
