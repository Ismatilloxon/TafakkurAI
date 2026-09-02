package com.example.tafakkurai

/**
 * Keys the app stores in `PrefPersistence` (the local key/value table).
 *
 * Collected in one place so a key is never spelled two ways in two screens.
 */
object AppPrefs {
    /** Device-level interface language, preserved across account sign-out. */
    const val LANGUAGE = "app.language"

    /** Device-level welcome completion, preserved when switching accounts. */
    const val ONBOARDING_SEEN = "onboarding.seen"

    /**
     * Display name of the signed-in user, refreshed from `GET /v1/me` on every launch.
     *
     * Cached rather than read live so the Today header renders immediately and correctly on a
     * cold, offline start instead of waiting on the network or falling back to a mock name.
     */
    const val DISPLAY_NAME = "profile.displayName"

    /** Where the last successful gratitude delta pull stopped. */
    /**
     * The signed-in account id, cached so an offline launch can bind the sync engine
     * before /v1/me answers. Without it a launch with no network would capture nothing,
     * and everything the user did on that flight would be queued under an empty user.
     */
    const val USER_ID = "profile.uid"

    /**
     * Written once on first launch after an install. Its absence is how the app detects that
     * it was reinstalled: this table goes away with the app, the iOS Keychain does not.
     */
    const val INSTALL_MARKER = "install.marker"

    /**
     * Explicit opt-in for sending ordinary coach conversation to the TafakkurAI backend.
     * Local schema-constrained actions do not read this flag and always remain available.
     */
    const val AI_CLOUD_CONVERSATION_ENABLED = "ai.cloudConversationEnabled"

    const val APPEARANCE_NAV = "appearance.navigation"
    const val APPEARANCE_MODE = "appearance.mode"
    const val APPEARANCE_DARK_THEME = "appearance.darkTheme"
    const val APPEARANCE_LIGHT_THEME = "appearance.lightTheme"
    const val APPEARANCE_SERIF = "appearance.serif"
    const val AI_PROACTIVITY = "ai.proactivity"

    /** Device-level, versioned order of the visible Today dashboard blocks. */
    const val TODAY_DASHBOARD_ORDER = "today.dashboard.order"

}
