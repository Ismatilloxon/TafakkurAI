package com.example.tafakkurai.onboarding

/**
 * Sign in with Apple on Android needs the web flow (Apple's OAuth endpoint + a server-side
 * client secret signed with the team's .p8 key), which this build has no credentials for.
 * Failing loudly keeps the Android login screen honest — the button reports "unavailable"
 * rather than dropping the user behind the wall with no session.
 */
actual suspend fun appleSignIn(): AppleCredential = throw ProviderNotConfigured(AuthProvider.APPLE)
