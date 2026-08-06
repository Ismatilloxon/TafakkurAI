package com.example.tafakkurai

/**
 * Keys the app stores in `PrefPersistence` (the local key/value table).
 *
 * Collected in one place so a key is never spelled two ways in two screens.
 */
object AppPrefs {
    /**
     * Display name of the signed-in user, refreshed from `GET /v1/me` on every launch.
     *
     * Cached rather than read live so the Today header renders immediately and correctly on a
     * cold, offline start instead of waiting on the network or falling back to a mock name.
     */
    const val DISPLAY_NAME = "profile.displayName"

    /** Where the last successful gratitude delta pull stopped. */
    const val GRATITUDE_CURSOR = "gratitude.sync.cursor"

    /**
     * Written once on first launch after an install. Its absence is how the app detects that
     * it was reinstalled: this table goes away with the app, the iOS Keychain does not.
     */
    const val INSTALL_MARKER = "install.marker"
}
