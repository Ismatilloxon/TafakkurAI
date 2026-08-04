package com.example.tafakkurai.onboarding

/**
 * What the native Sign in with Apple sheet hands back.
 *
 * [idToken] is the only field the backend needs — `POST /v1/auth/session` verifies it against
 * Apple's JWKS and reads the subject/email out of the verified claims itself, so a client that
 * lied about [email] would gain nothing.
 *
 * [fullName] and [email] are populated by Apple **only on the very first authorization** for a
 * given Apple ID + app pair; every later sign-in returns them as null even though the user is
 * the same. Anything that wants the display name has to persist it on that first pass.
 */
data class AppleCredential(
    val idToken: String,
    val fullName: String? = null,
    val email: String? = null,
)

/** The user dismissed the Apple sheet. Not a failure — the UI should stay put and say nothing. */
class AppleSignInCancelled : Exception("Sign in with Apple was cancelled")

/**
 * Presents the system Sign in with Apple sheet and suspends until the user finishes it.
 *
 * iOS shows the real sheet; every other target throws [ProviderNotConfigured], because Apple
 * only mints identity tokens through its own native framework.
 */
expect suspend fun appleSignIn(): AppleCredential
