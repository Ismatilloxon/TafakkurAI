package com.example.tafakkurai.i18n

/**
 * English table — also the fallback for every other language, so a key missing elsewhere
 * still renders real words. Keys are `screen.element`, lower case, dot separated.
 *
 * Adding a string means adding it here first, then to the other three tables; the
 * completeness test fails the build if a language is left behind.
 */
internal val EN: Map<String, String> = mapOf(
    // ── Language picker ──────────────────────────────────────────────────────
    "lang.overline" to "LANGUAGE",
    "lang.title" to "Choose your language",
    "lang.subtitle" to "You can change this later in your profile.",
    "lang.continue" to "Continue",

    // ── Common actions ───────────────────────────────────────────────────────
    "common.back" to "Back",
    "common.retry" to "Try again",
    "common.done" to "Done",
    "common.cancel" to "Cancel",
    "common.save" to "Save",
    "common.close" to "Close",
    "common.loading" to "Loading…",
    "common.next" to "Next",

    // ── Onboarding ───────────────────────────────────────────────────────────
    "onb.skip" to "Skip",
    "onb.start" to "Get started",
    "onb.continue" to "Continue",
    "onb.aspect.spiritual" to "SPIRITUAL",
    "onb.aspect.physical" to "PHYSICAL",
    "onb.aspect.educational" to "EDUCATIONAL",
    "onb.aspect.social" to "SOCIAL",
    "onb.aspect.financial" to "FINANCIAL",

    // ── Login ────────────────────────────────────────────────────────────────
    "login.title" to "Welcome",
    "login.subtitle" to "Sign in to continue. Your data stays private, on your device.",
    "login.apple" to "Continue with Apple",
    "login.google" to "Continue with Google",
    "login.phone" to "Continue with phone number",
    "login.privacy" to "On-device AI · your data is never sold",
    "login.terms" to "By continuing you accept the Terms of Use and the Privacy Policy.",
    "login.signing_in" to "Signing in…",
    "login.provider_unavailable" to "This sign-in method is not available yet. Use your phone number.",
    "login.failed" to "Sign-in failed. Please try again.",
    "login.google_timeout" to "Google sign-in timed out. Tap the button again.",

    // ── Phone / OTP ──────────────────────────────────────────────────────────
    "phone.overline" to "PHONE NUMBER",
    "phone.title" to "Enter your number",
    "phone.subtitle" to "We'll send a confirmation code by SMS.",
    "phone.send_code" to "Send code",
    "otp.overline" to "CONFIRMATION",
    "otp.title" to "Enter the code",
    "otp.sent_to" to "A 6-digit code was sent to +998 {0}.",
    "otp.verified" to "Verified",
    "otp.resend_in" to "Resend · 0:{0}",
    "otp.resend" to "Resend the code",
    "otp.demo_hint" to "The code arrives by SMS and expires in 5 minutes.",

    // ── Profile ──────────────────────────────────────────────────────────────
    "profile.title" to "Profile",
    "profile.change_photo" to "Change photo",
    "profile.streak" to "Streak",
    "profile.level" to "Level",
    "profile.xp" to "XP",
    "profile.coach" to "Coach",
    "profile.language" to "Language",
    "profile.city" to "City",
    "profile.coach.name" to "Murabbiy",
    "profile.language_hint" to "The whole app switches instantly.",
    "profile.phone" to "Phone",
    "profile.email" to "Email",
    // ── Finishing the contact detail sign-in could not supply ──
    "profile.contact.overline" to "FINISH YOUR PROFILE",
    "profile.contact.phone_title" to "Add your phone number",
    "profile.contact.phone_hint" to "You signed in with Google, so we don't have a number yet. We'll text you a code to confirm it.",
    "profile.contact.code_hint" to "Enter the 6-digit code we just sent.",
    "profile.contact.email_title" to "Add your email",
    "profile.contact.email_hint" to "You signed in with your phone number, so we don't have an address yet.",
    "profile.contact.send_code" to "Send code",
    "profile.contact.confirm" to "Confirm",
    "profile.contact.save" to "Save",
    "profile.contact.change_number" to "Use a different number",
    "profile.contact.email_invalid" to "That doesn't look like an email address.",
    "profile.contact.failed" to "Couldn't save that. Try again.",

    // ── Appearance ───────────────────────────────────────────────────────────
    "appearance.title" to "Appearance",
    "appearance.heading" to "Make it yours",
    "appearance.subtitle" to "Pick a theme — the whole app re-skins instantly. Your dark and light " +
        "choices are remembered separately.",
    "appearance.dark" to "Dark",
    "appearance.light" to "Light",

    // ── Feedback: errors ─────────────────────────────────────────────────────
    "feedback.error.no_internet" to "No internet connection. Check your network and try again.",
    "feedback.error.timeout" to "The request took too long. Please try again.",
    "feedback.error.bad_request" to "Something was off with that request. Please try again.",
    "feedback.error.unauthorized" to "Your session has expired. Please sign in again.",
    "feedback.error.forbidden" to "You do not have permission to do that.",
    "feedback.error.not_found" to "We could not find what you were looking for.",
    "feedback.error.conflict" to "That conflicts with existing data. Please refresh and try again.",
    "feedback.error.validation" to "Please check the highlighted fields and try again.",
    "feedback.error.rate_limited" to "Too many attempts. Please wait a moment and try again.",
    "feedback.error.server" to "Something went wrong on our side. Please try again shortly.",
    "feedback.error.parse" to "We received an unexpected response. Please try again.",
    "feedback.error.unknown" to "Something went wrong. Please try again.",

    // ── Feedback: success ────────────────────────────────────────────────────
    "feedback.success.login" to "Signed in successfully.",
    "feedback.success.registered" to "Your account is ready. Welcome!",
    "feedback.success.profile_updated" to "Profile updated.",
    "feedback.success.photo_updated" to "Photo updated.",
    "feedback.success.interests_saved" to "Interests saved.",
    "feedback.success.settings_saved" to "Settings saved.",
    "feedback.success.thought_published" to "Your thought has been shared.",
    "feedback.success.lesson_completed" to "Lesson completed.",
    "feedback.success.entry_saved" to "Saved.",

    // ── Feedback: warnings ───────────────────────────────────────────────────
    "feedback.warning.weak_connection" to "Your connection looks weak. This may take longer than usual.",
    "feedback.warning.unsaved_changes" to "You have unsaved changes.",
    "feedback.warning.session_expiring" to "Your session is about to expire.",

    // ── Dates ────────────────────────────────────────────────────────────────
    // {0} weekday, {1} day, {2} month — the join differs per language.
    "date.header" to "{0}, {2} {1}",
    "date.weekday.0" to "Sunday",
    "date.weekday.1" to "Monday",
    "date.weekday.2" to "Tuesday",
    "date.weekday.3" to "Wednesday",
    "date.weekday.4" to "Thursday",
    "date.weekday.5" to "Friday",
    "date.weekday.6" to "Saturday",
    "date.month.1" to "January",
    "date.month.2" to "February",
    "date.month.3" to "March",
    "date.month.4" to "April",
    "date.month.5" to "May",
    "date.month.6" to "June",
    "date.month.7" to "July",
    "date.month.8" to "August",
    "date.month.9" to "September",
    "date.month.10" to "October",
    "date.month.11" to "November",
    "date.month.12" to "December",

    // ── Today: greeting by time of day ───────────────────────────────────────
    "today.greeting.morning" to "Good morning",
    "today.greeting.afternoon" to "Good afternoon",
    "today.greeting.evening" to "Good evening",
    "today.greeting.night" to "Peaceful night",

    // ── Tab bar ──────────────────────────────────────────────────────────────
    "tab.today" to "Today",
    "tab.learn" to "Learn",
    "tab.do" to "Do",
    "tab.thoughts" to "Thoughts",
    "tab.more" to "More",

    // ── Thoughts ─────────────────────────────────────────────────────────────
    "thoughts.title" to "Thoughts",
    "thoughts.compose_hint" to "Share a thought…",
    "thoughts.circles" to "Your circles",
)
