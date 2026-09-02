package com.example.tafakkurai.onboarding

// ─────────────────────────────────────────────────────────────────────────────
//  Onboarding + auth — model, content and the auth/gate seams.
//
//  Copy is Uzbek-primary and verbatim from the Tafakkur "Fajr" spec (§2–§4).
//  Accent keys map to the palette in OnboardingArt.accentOf().
// ─────────────────────────────────────────────────────────────────────────────

enum class SlideArt { ORB, RADAR, PRAYER, TILES }

data class Slide(
    val accent: String,
    val overlineKey: String,
    val titleKey: String,
    val titleSize: Float,
    val bodyKey: String,
    val art: SlideArt,
    val tiles: List<AspectTile> = emptyList(),
    val tileCols: Int = 2,
    val chipKeys: List<String> = emptyList(),
    val serifTitle: Boolean = false,
)

/** The 8 welcome slides, in order. Last slide's CTA reads "Boshlash" → Login. */
val ONB_SLIDES: List<Slide> = listOf(
    // 1 — Hero
    Slide(
        accent = "teal",
        overlineKey = "onb.hero.overline",
        titleKey = "onb.hero.title",
        titleSize = 46f,
        serifTitle = true,
        bodyKey = "onb.hero.body",
        art = SlideArt.ORB,
    ),
    // 2 — Balance intro
    Slide(
        accent = "teal",
        overlineKey = "onb.balance.overline",
        titleKey = "onb.balance.title",
        titleSize = 30f,
        bodyKey = "onb.balance.body",
        art = SlideArt.RADAR,
    ),
    // 3 — Spiritual
    Slide(
        accent = "gold",
        overlineKey = "onb.spiritual.overline",
        titleKey = "onb.spiritual.title",
        titleSize = 30f,
        bodyKey = "onb.spiritual.body",
        art = SlideArt.PRAYER,
        chipKeys = listOf(
            "onb.chip.prayer_tracker",
            "onb.chip.reflection",
            "onb.chip.quran_dhikr",
            "onb.chip.gratitude",
        ),
    ),
    // 4 — Physical
    Slide(
        accent = "green",
        overlineKey = "onb.physical.overline",
        titleKey = "onb.physical.title",
        titleSize = 30f,
        bodyKey = "onb.physical.body",
        art = SlideArt.TILES,
        tiles = listOf(
            AspectTile("repeat", "onb.tile.habits.title", "onb.tile.habits.subtitle"),
            AspectTile("coffee", "onb.tile.focus.title", "onb.tile.focus.subtitle"),
            AspectTile("moon", "onb.tile.sleep.title", "onb.tile.sleep.subtitle"),
            AspectTile("activity", "onb.tile.sport.title", "onb.tile.sport.subtitle"),
        ),
    ),
    // 5 — Educational
    Slide(
        accent = "purple",
        overlineKey = "onb.educational.overline",
        titleKey = "onb.educational.title",
        titleSize = 30f,
        bodyKey = "onb.educational.body",
        art = SlideArt.TILES,
        tiles = listOf(
            AspectTile("languages", "onb.tile.subjects.title", "onb.tile.subjects.subtitle"),
            AspectTile("sparkles", "onb.tile.ai_lessons.title", "onb.tile.ai_lessons.subtitle"),
            AspectTile("checkCircle", "onb.tile.tests.title", "onb.tile.tests.subtitle"),
            AspectTile("bookmark", "onb.tile.flashcards.title", "onb.tile.flashcards.subtitle"),
        ),
    ),
    // 6 — Social
    Slide(
        accent = "cyan",
        overlineKey = "onb.social.overline",
        titleKey = "onb.social.title",
        titleSize = 30f,
        bodyKey = "onb.social.body",
        art = SlideArt.TILES,
        tiles = listOf(
            AspectTile("messageCircle", "onb.tile.posts.title", "onb.tile.posts.subtitle"),
            AspectTile("users", "onb.tile.circles.title", "onb.tile.circles.subtitle"),
            AspectTile("heart", "onb.tile.checkins.title", "onb.tile.checkins.subtitle"),
        ),
        tileCols = 3,
    ),
    // 7 — Financial
    Slide(
        accent = "amber",
        overlineKey = "onb.financial.overline",
        titleKey = "onb.financial.title",
        titleSize = 30f,
        bodyKey = "onb.financial.body",
        art = SlideArt.TILES,
        tiles = listOf(
            AspectTile("list", "onb.tile.ledger.title", "onb.tile.ledger.subtitle"),
            AspectTile("wallet", "onb.tile.budgets.title", "onb.tile.budgets.subtitle"),
            AspectTile("activity", "onb.tile.balance.title", "onb.tile.balance.subtitle"),
        ),
        tileCols = 3,
    ),
    // 8 — AI coach
    Slide(
        accent = "teal",
        overlineKey = "onb.coach.overline",
        titleKey = "onb.coach.title",
        titleSize = 30f,
        bodyKey = "onb.coach.body",
        art = SlideArt.ORB,
        chipKeys = listOf(
            "onb.chip.daily_plan",
            "onb.chip.motivation",
            "onb.chip.reflection",
            "onb.chip.on_device",
        ),
    ),
)

// ── Auth seam ────────────────────────────────────────────────────────────────
enum class AuthProvider { APPLE, GOOGLE, PHONE, TELEGRAM }

/** Which bot to open, and how many digits the code it replies with has. */
data class TelegramBotInfo(val botUsername: String, val deepLink: String, val codeLength: Int)

/**
 * The onboarding flow talks to auth through this seam only. Production wiring uses
 * [BackendOnboardingAuth], backed by the use-cases in [uz.tafakkur.di.SharedModule]:
 *   signInWith(APPLE/GOOGLE) → SignInUseCase(...)
 *   sendOtp(phone)           → StartPhoneOtpUseCase(phone)
 *   verifyOtp(code)          → SignInUseCase(otp = code)   // sets the token
 * and gate the app root on IsLoggedInUseCase instead of the in-memory flag.
 */
interface OnboardingAuth {
    /** Direct providers (Apple). Suspends while "authenticating". */
    suspend fun signInWith(provider: AuthProvider)

    /**
     * Starts the Google browser sign-in and returns the URL to open. The account chooser
     * lives on accounts.google.com, so this is a URL and not an SDK call.
     */
    suspend fun startGoogleLogin(): String

    /**
     * Waits for the browser leg of [startGoogleLogin] to finish. False means the user
     * gave up or the attempt expired — not an error to report.
     */
    suspend fun awaitGoogleApproval(): Boolean

    /** Which bot to send the user to. */
    suspend fun telegramBot(): TelegramBotInfo

    /** Redeems the code the bot replied with after the user shared their contact. */
    suspend fun verifyTelegramCode(code: String): Boolean

    /** Request an SMS code for an E.164 phone (e.g. +998901234567). */
    suspend fun sendOtp(phoneE164: String)

    /** Verify the 6-digit code. Returns true on success. */
    suspend fun verifyOtp(code: String): Boolean
}

/** Whether the welcome carousel has been seen. */
interface OnboardingGate {
    var completed: Boolean
}

/** Debug override for where the flow starts (spec's "Launch at" control). */
enum class OnbStart { AUTO, INTRO, LOGIN, APP }
