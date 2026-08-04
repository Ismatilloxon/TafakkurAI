package uz.tafakkur.core.feedback

/**
 * i18n keys for the positive/advisory feedback the app raises deliberately (errors have
 * their own keys on [AppError]). Centralised so a message is defined in one place and every
 * catalog is guaranteed to carry it.
 */
object FeedbackKeys {
    // Success — only meaningful user actions, never GETs or background refreshes.
    const val LOGIN = "feedback.success.login"
    const val REGISTERED = "feedback.success.registered"
    const val PROFILE_UPDATED = "feedback.success.profile_updated"
    const val PHOTO_UPDATED = "feedback.success.photo_updated"
    const val INTERESTS_SAVED = "feedback.success.interests_saved"
    const val SETTINGS_SAVED = "feedback.success.settings_saved"
    const val THOUGHT_PUBLISHED = "feedback.success.thought_published"
    const val LESSON_COMPLETED = "feedback.success.lesson_completed"
    const val ENTRY_SAVED = "feedback.success.entry_saved"

    // Warning — non-critical, advisory.
    const val WEAK_CONNECTION = "feedback.warning.weak_connection"
    const val UNSAVED_CHANGES = "feedback.warning.unsaved_changes"
    const val SESSION_EXPIRING = "feedback.warning.session_expiring"
}
