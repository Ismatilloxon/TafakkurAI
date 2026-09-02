package uz.tafakkur.domain.repository

import kotlinx.coroutines.flow.Flow
import uz.tafakkur.domain.model.AuthProvider
import uz.tafakkur.domain.model.AuthSession
import uz.tafakkur.domain.model.CoachEvent
import uz.tafakkur.domain.model.CoachHistory
import uz.tafakkur.domain.model.CompleteLessonCommand
import uz.tafakkur.domain.model.FeedPage
import uz.tafakkur.domain.model.Gamification
import uz.tafakkur.domain.model.GoogleLogin
import uz.tafakkur.domain.model.Lesson
import uz.tafakkur.domain.model.LessonCompletion
import uz.tafakkur.domain.model.LikeResult
import uz.tafakkur.domain.model.Me
import uz.tafakkur.domain.model.PrayerTimes
import uz.tafakkur.domain.model.SocialPost
import uz.tafakkur.domain.model.Subject
import uz.tafakkur.domain.model.TelegramBot
import uz.tafakkur.domain.model.TrackerEntry
import uz.tafakkur.domain.model.UserProfile

interface AuthRepository {
    suspend fun startPhoneOtp(phone: String): String // returns verificationId
    suspend fun signIn(provider: AuthProvider): AuthSession

    /** Which bot the user should open to get a confirmation code. */
    suspend fun telegramBot(): TelegramBot

    /** Redeems the code the bot sent after the user shared their contact. */
    suspend fun verifyTelegramCode(code: String): AuthSession

    /** Begins the browser sign-in: the URL to open, and the state to wait on. */
    suspend fun startGoogleLogin(): GoogleLogin

    /**
     * One poll of a pending browser sign-in. Null means the user has not finished
     * picking an account yet — the expected answer for most of the wait, so it is a
     * value here rather than an exception.
     */
    suspend fun pollGoogleLogin(state: String): AuthSession?

    suspend fun logout()
    fun isLoggedIn(): Boolean
}

interface UserRepository {
    suspend fun getMe(): Me
    suspend fun updateProfile(
        displayName: String? = null,
        handle: String? = null,
        gender: String? = null,
        birthDate: String? = null,
        language: String? = null,
    ): UserProfile
    suspend fun replaceInterests(interests: List<String>): UserProfile
    suspend fun updatePrayerSettings(city: String?, country: String?, madhab: String, calcMethod: String): UserProfile
    suspend fun completeOnboarding(): UserProfile
    suspend fun uploadPhoto(bytes: ByteArray, filename: String, contentType: String): String

    /** Saves the address a phone/Telegram account signed in without. */
    suspend fun setEmail(email: String): UserProfile

    /** Sends a code to a number a Google/Apple account wants to add. Returns the verification id. */
    suspend fun startPhoneAttach(phone: String): String

    /** Redeems that code and attaches the number the challenge was created for. */
    suspend fun verifyPhoneAttach(verificationId: String, code: String): UserProfile
}

interface PrayerRepository {
    suspend fun getTimes(
        lat: Double,
        lng: Double,
        date: String? = null,
        method: String? = null,
        madhab: String? = null,
        tz: String? = null,
    ): PrayerTimes
    suspend fun track(prayer: String, date: String? = null): Gamification
}

interface FeedRepository {
    suspend fun feed(cursor: String? = null, limit: Int? = null): FeedPage
    suspend fun publish(text: String): SocialPost
    suspend fun toggleLike(thoughtId: String): LikeResult
}

interface CoachRepository {
    fun streamMessage(conversationId: String?, message: String): Flow<CoachEvent>
    suspend fun history(conversationId: String, cursor: String? = null, limit: Int? = null): CoachHistory
}

interface LearnRepository {
    suspend fun subjects(): List<Subject>
    suspend fun lessons(subjectId: String): List<Lesson>
    suspend fun lesson(lessonId: String): Lesson
    suspend fun complete(lessonId: String, command: CompleteLessonCommand): LessonCompletion
}

interface TrackerRepository {
    suspend fun create(kind: String, payloadJson: String, entryDate: String? = null): TrackerEntry
    suspend fun list(kind: String, from: String? = null, to: String? = null): List<TrackerEntry>
}

/**
 * Qurʼan memorization schedule. Only the write path is here: the client is the source of
 * truth for its own schedule (FSRS runs on-device, offline), and the backend's copy exists
 * for cross-device restore and for XP — never to tell the device when an ayah is due.
 */
interface HifzRepository {
    suspend fun grade(
        surah: Int,
        ayah: Int,
        rating: Int,
        reviewedAt: Long,
        state: Int,
        stability: Double,
        difficulty: Double,
        due: Long,
    )
}
