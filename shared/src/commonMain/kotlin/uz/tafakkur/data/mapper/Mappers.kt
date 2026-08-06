package uz.tafakkur.data.mapper

import uz.tafakkur.data.dto.CoachHistoryDto
import uz.tafakkur.data.dto.CoachMessageDto
import uz.tafakkur.data.dto.FeedPageDto
import uz.tafakkur.data.dto.GamificationDto
import uz.tafakkur.data.dto.LessonDto
import uz.tafakkur.data.dto.MeResponseDto
import uz.tafakkur.data.dto.NextPrayerDto
import uz.tafakkur.data.dto.PrayerTimeDto
import uz.tafakkur.data.dto.PrayerTimesResponseDto
import uz.tafakkur.data.dto.SessionResponseDto
import uz.tafakkur.data.dto.SocialPostDto
import uz.tafakkur.data.dto.SubjectDto
import uz.tafakkur.data.dto.TrackerEntryDto
import uz.tafakkur.data.dto.UserProfileDto
import uz.tafakkur.domain.model.AuthSession
import uz.tafakkur.domain.model.CoachHistory
import uz.tafakkur.domain.model.CoachMessage
import uz.tafakkur.domain.model.FeedPage
import uz.tafakkur.domain.model.Gamification
import uz.tafakkur.domain.model.Lesson
import uz.tafakkur.domain.model.Me
import uz.tafakkur.domain.model.NextPrayer
import uz.tafakkur.domain.model.PrayerTime
import uz.tafakkur.domain.model.PrayerTimes
import uz.tafakkur.domain.model.SocialPost
import uz.tafakkur.domain.model.Subject
import uz.tafakkur.domain.model.TrackerEntry
import uz.tafakkur.domain.model.UserProfile

fun UserProfileDto.toDomain() = UserProfile(
    uid = uid,
    displayName = displayName,
    email = email,
    phone = phone,
    photoUrl = photoUrl,
    provider = provider,
    handle = handle,
    gender = gender,
    birthDate = birthDate,
    city = city,
    country = country,
    languageTag = languageTag,
    madhab = madhab,
    calcMethod = calcMethod,
    interests = interests,
    onboardingComplete = onboardingComplete,
    needsPhone = needsPhone,
    needsEmail = needsEmail,
)

fun GamificationDto.toDomain() = Gamification(streak, level, xp, xpNext)

fun MeResponseDto.toDomain() = Me(user.toDomain(), gamification.toDomain())

fun SessionResponseDto.toDomain() = AuthSession(accessToken, refreshToken, user.toDomain(), needsOnboarding)

fun PrayerTimeDto.toDomain() = PrayerTime(name, time)
fun NextPrayerDto.toDomain() = NextPrayer(name, time, secondsRemaining)
fun PrayerTimesResponseDto.toDomain() = PrayerTimes(
    date = date,
    method = method,
    madhab = madhab,
    times = times.map { it.toDomain() },
    next = next.toDomain(),
)

fun SocialPostDto.toDomain() = SocialPost(
    id = id,
    text = text,
    authorId = authorId,
    authorName = authorName,
    authorHandle = authorHandle,
    authorPhotoUrl = authorPhotoUrl,
    likeCount = likeCount,
    likedByMe = likedByMe,
    createdAt = createdAt,
)
fun FeedPageDto.toDomain() = FeedPage(posts.map { it.toDomain() }, nextCursor)

fun CoachMessageDto.toDomain() = CoachMessage(id, role, content, createdAt)
fun CoachHistoryDto.toDomain() = CoachHistory(conversationId, messages.map { it.toDomain() }, nextCursor)

fun SubjectDto.toDomain() = Subject(id, title, description, orderIndex)
fun LessonDto.toDomain() = Lesson(id, subjectId, title, body, orderIndex)

fun TrackerEntryDto.toDomain() = TrackerEntry(
    id = id,
    kind = kind,
    payloadJson = payload.toString(),
    entryDate = entryDate,
    createdAt = createdAt,
)
