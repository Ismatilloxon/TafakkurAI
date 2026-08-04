package com.example.tafakkurai.onboarding

import com.example.tafakkurai.spiritual.PrefPersistence
import uz.tafakkur.domain.usecase.SignInUseCase
import uz.tafakkur.domain.usecase.StartPhoneOtpUseCase
import uz.tafakkur.domain.model.AuthProvider as DomainAuthProvider

/**
 * The welcome carousel is a once-in-a-lifetime screen, so its "seen" flag has to outlive the
 * process. Stored through [PrefPersistence] (SQLDelight) rather than the secure store: it is
 * not a secret, and losing it only costs the user one extra swipe.
 */
class PrefOnboardingGate(private val prefs: PrefPersistence) : OnboardingGate {

    override var completed: Boolean
        get() = prefs.get(KEY) == "1"
        set(value) = prefs.put(KEY, if (value) "1" else "0")

    private companion object {
        const val KEY = "onboarding.seen"
    }
}

/** Raised when a provider button is tapped that this build has no credentials for. */
class ProviderNotConfigured(val provider: AuthProvider) : Exception("$provider sign-in is not configured")

/**
 * [OnboardingAuth] backed by the real backend, replacing [DemoOnboardingAuth].
 *
 * Phone and Apple work end to end: the backend verifies the OTP itself, and Apple's identity
 * token is minted by the system sheet ([appleSignIn]) and verified server-side against Apple's
 * JWKS. Google still needs its SDK integrated, so that button raises [ProviderNotConfigured]
 * instead of pretending to succeed — which is what [DemoOnboardingAuth] did, and why every
 * screen behind the login wall came up empty.
 */
class BackendOnboardingAuth(
    private val startPhoneOtp: StartPhoneOtpUseCase,
    private val signIn: SignInUseCase,
) : OnboardingAuth {

    /** Handed back by `POST /v1/auth/otp/start` and required to redeem the code. */
    private var verificationId: String? = null

    override suspend fun signInWith(provider: AuthProvider) {
        when (provider) {
            // The sheet suspends here until the user approves or dismisses it; SignInUseCase
            // then stores the tokens through TokenProvider, so the session survives a cold launch.
            AuthProvider.APPLE ->
                signIn(DomainAuthProvider.Apple(idToken = appleSignIn().idToken))

            else -> throw ProviderNotConfigured(provider)
        }
    }

    override suspend fun sendOtp(phoneE164: String) {
        verificationId = startPhoneOtp(phoneE164)
    }

    override suspend fun verifyOtp(code: String): Boolean {
        val id = verificationId ?: return false
        return try {
            // SignInUseCase stores the tokens through TokenProvider, so a successful call here
            // is what makes the session survive the next cold launch.
            signIn(DomainAuthProvider.Phone(verificationId = id, code = code))
            true
        } catch (e: Exception) {
            // NetworkExecutor has already mapped, logged and shown the failure; the screen only
            // needs to know to keep the user on the code field.
            false
        }
    }
}
