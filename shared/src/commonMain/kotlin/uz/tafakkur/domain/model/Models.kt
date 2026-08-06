package uz.tafakkur.domain.model

/** Matches the KMP client's UserProfile model. Dates/times are RFC3339 / ISO
 *  strings — parse with kotlinx-datetime in the UI layer if needed. */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String?,
    val phone: String?,
    val photoUrl: String?,
    val provider: String,
    val handle: String,
    val gender: String?,
    val birthDate: String?,
    val city: String?,
    val country: String?,
    val languageTag: String,
    val madhab: String,
    val calcMethod: String,
    val interests: List<String>,
    val onboardingComplete: Boolean,
)

data class Gamification(val streak: Int, val level: Int, val xp: Int, val xpNext: Int)

data class Me(val user: UserProfile, val gamification: Gamification)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: UserProfile,
    val needsOnboarding: Boolean,
)

/** Which bot to open for sign-in, and how long the code it hands back is. */
data class TelegramBot(
    val username: String,
    val deepLink: String,
    val codeLength: Int,
)

/**
 * A browser sign-in that has been started but not finished: where to send the user,
 * and how to wait for them coming back. [state] is opaque and single-use.
 */
data class GoogleLogin(
    val state: String,
    val authUrl: String,
    val pollIntervalMs: Long,
    val expiresInSeconds: Long,
)

/** Sign-in intent handed to AuthRepository.signIn. */
sealed interface AuthProvider {
    data class Google(val idToken: String) : AuthProvider
    data class Apple(val idToken: String) : AuthProvider
    data class Phone(val verificationId: String, val code: String) : AuthProvider
}

data class PrayerTime(val name: String, val time: String)
data class NextPrayer(val name: String, val time: String, val secondsRemaining: Long)
data class PrayerTimes(
    val date: String,
    val method: String,
    val madhab: String,
    val times: List<PrayerTime>,
    val next: NextPrayer,
)

data class SocialPost(
    val id: String,
    val text: String,
    val authorId: String,
    val authorName: String,
    val authorHandle: String,
    val authorPhotoUrl: String?,
    val likeCount: Int,
    val likedByMe: Boolean,
    val createdAt: String,
)

data class FeedPage(val posts: List<SocialPost>, val nextCursor: String?)

data class LikeResult(val liked: Boolean, val likeCount: Int)

data class CoachMessage(val id: String, val role: String, val content: String, val createdAt: String)
data class CoachHistory(val conversationId: String, val messages: List<CoachMessage>, val nextCursor: String?)

/** Streamed coach events surfaced to the UI as a Flow. */
sealed interface CoachEvent {
    data class Delta(val text: String) : CoachEvent
    data class Done(val conversationId: String) : CoachEvent
    data class Error(val message: String) : CoachEvent
}

data class Subject(val id: String, val title: String, val description: String, val orderIndex: Int)
data class Lesson(val id: String, val subjectId: String, val title: String, val body: String, val orderIndex: Int)

data class TrackerEntry(
    val id: String,
    val kind: String,
    val payloadJson: String,
    val entryDate: String,
    val createdAt: String,
)
