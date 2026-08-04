package com.example.tafakkurai.onboarding

import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
//  Onboarding + auth — model, content and the auth/gate seams.
//
//  Copy is Uzbek-primary and verbatim from the Tafakkur "Fajr" spec (§2–§4).
//  Accent keys map to the palette in OnboardingArt.accentOf().
// ─────────────────────────────────────────────────────────────────────────────

enum class SlideArt { ORB, RADAR, PRAYER, TILES }

data class Slide(
    val accent: String,
    val overline: String,
    val title: String,
    val titleSize: Float,
    val body: String,
    val art: SlideArt,
    val tiles: List<AspectTile> = emptyList(),
    val tileCols: Int = 2,
    val chips: List<String> = emptyList(),
    val serifTitle: Boolean = false,
)

/** The 8 welcome slides, in order. Last slide's CTA reads "Boshlash" → Login. */
val ONB_SLIDES: List<Slide> = listOf(
    // 1 — Hero
    Slide(
        accent = "teal",
        overline = "BISMILLAH",
        title = "Tafakkur AI",
        titleSize = 46f,
        serifTitle = true,
        body = "Oʻzingizni har kuni rivojlantiring — fikrlang, oʻrganing, amal qiling va muhosaba qiling. Bir tinch, aqlli hamroh bilan.",
        art = SlideArt.ORB,
    ),
    // 2 — Balance intro
    Slide(
        accent = "teal",
        overline = "MUVOZANAT · BALANCE",
        title = "Besh tomonlama rivojlanish",
        titleSize = 30f,
        body = "Hayotingiz besh yoʻnalishda oʻlchanadi. Har birida quyidagi imkoniyatlar — keyingi ekranlarda koʻring.",
        art = SlideArt.RADAR,
    ),
    // 3 — Spiritual
    Slide(
        accent = "gold",
        overline = "MAʼNAVIY · SPIRITUAL",
        title = "Maʼnaviy rivojlanish",
        titleSize = 30f,
        body = "Kuningiz Salah bilan uygʻun — ibodat, muhosaba va shukr.",
        art = SlideArt.PRAYER,
        chips = listOf("Namoz tracker", "Tafakkur", "Qurʼon & zikr", "Shukr"),
    ),
    // 4 — Physical
    Slide(
        accent = "green",
        overline = "JISMONIY · PHYSICAL",
        title = "Jismoniy salomatlik",
        titleSize = 30f,
        body = "Energiya, dam va harakat — tanangizga gʻamxoʻrlik.",
        art = SlideArt.TILES,
        tiles = listOf(
            AspectTile("repeat", "Odatlar", "Streak"),
            AspectTile("coffee", "Fokus", "Pomodoro"),
            AspectTile("moon", "Uyqu & dam", "Tahlil"),
            AspectTile("activity", "Sport & Gym", "Mashqlar"),
        ),
    ),
    // 5 — Educational
    Slide(
        accent = "purple",
        overline = "TAʼLIM · EDUCATIONAL",
        title = "Taʼlim va oʻsish",
        titleSize = 30f,
        body = "AI yordamida oʻrganing, mashq qiling va karyera quring.",
        art = SlideArt.TILES,
        tiles = listOf(
            AspectTile("languages", "Fanlar", "Til, kod, math"),
            AspectTile("sparkles", "AI darslar", "Shaxsiy"),
            AspectTile("checkCircle", "Testlar", "Bilim sinovi"),
            AspectTile("briefcase", "Karyera", "CV & suhbat"),
        ),
    ),
    // 6 — Social
    Slide(
        accent = "cyan",
        overline = "IJTIMOIY · SOCIAL",
        title = "Ijtimoiy aloqa",
        titleSize = 30f,
        body = "Hamjamiyat, doʻstlar va ustozlar bilan birga oʻsing.",
        art = SlideArt.TILES,
        tiles = listOf(
            AspectTile("messageCircle", "Fikrlar", "Ulashish"),
            AspectTile("users", "Doiralar", "Guruhlar"),
            AspectTile("graduationCap", "Ustozlar", "Yoʻl-yoʻriq"),
        ),
        tileCols = 3,
    ),
    // 7 — Financial
    Slide(
        accent = "amber",
        overline = "MOLIYAVIY · FINANCIAL",
        title = "Moliyaviy ong",
        titleSize = 30f,
        body = "Toping, tejang va ehson qiling — ogohlik bilan.",
        art = SlideArt.TILES,
        tiles = listOf(
            AspectTile("lineChart", "Portfel", "Kuzatuv"),
            AspectTile("trendingUp", "Pul odatlari", "Tejash"),
            AspectTile("activity", "Tahlillar", "Insight"),
            AspectTile("heart", "Sadaqa", "Xayriya"),
        ),
    ),
    // 8 — AI coach
    Slide(
        accent = "teal",
        overline = "MURABBIY · AI COACH",
        title = "Shaxsiy AI murabbiy",
        titleSize = 30f,
        body = "Murabbiy kuningizni rejalashtiradi, darslar yaratadi va siz bilan muhosaba qiladi — barchasi qurilmangizda, maxfiy.",
        art = SlideArt.ORB,
        chips = listOf("Kunlik reja", "Motivatsiya", "Muhosaba", "On-device"),
    ),
)

// ── Auth seam ────────────────────────────────────────────────────────────────
enum class AuthProvider { APPLE, GOOGLE, PHONE }

/**
 * The onboarding flow talks to auth through this seam only. The demo impl below
 * makes the flow fully runnable with no backend. To go live, back these calls
 * with the existing use-cases wired in [uz.tafakkur.di.SharedModule]:
 *   signInWith(APPLE/GOOGLE) → SignInUseCase(...)
 *   sendOtp(phone)           → StartPhoneOtpUseCase(phone)
 *   verifyOtp(code)          → SignInUseCase(otp = code)   // sets the token
 * and gate the app root on IsLoggedInUseCase instead of the in-memory flag.
 */
interface OnboardingAuth {
    /** Direct providers (Apple / Google). Suspends while "authenticating". */
    suspend fun signInWith(provider: AuthProvider)

    /** Request an SMS code for an E.164 phone (e.g. +998901234567). */
    suspend fun sendOtp(phoneE164: String)

    /** Verify the 6-digit code. Returns true on success. */
    suspend fun verifyOtp(code: String): Boolean
}

/** Runnable stub: short delays, accepts any 6-digit code (spec's demo mode). */
class DemoOnboardingAuth : OnboardingAuth {
    override suspend fun signInWith(provider: AuthProvider) {
        delay(800)
    }

    override suspend fun sendOtp(phoneE164: String) {
        delay(600)
    }

    override suspend fun verifyOtp(code: String): Boolean {
        delay(400)
        return code.length == 6 && code.all { it.isDigit() }
    }
}

/**
 * Whether the welcome carousel has been seen. In-memory by default (the flow
 * still gates correctly within a session); swap for a persisted impl — or the
 * real IsLoggedInUseCase — to skip onboarding across cold launches.
 */
interface OnboardingGate {
    var completed: Boolean
}

class InMemoryOnboardingGate : OnboardingGate {
    override var completed: Boolean = false
}

/** Debug override for where the flow starts (spec's "Launch at" control). */
enum class OnbStart { AUTO, INTRO, LOGIN, APP }
