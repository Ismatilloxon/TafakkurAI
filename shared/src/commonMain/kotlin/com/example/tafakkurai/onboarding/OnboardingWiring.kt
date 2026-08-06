package com.example.tafakkurai.onboarding

import com.example.tafakkurai.AppPrefs
import com.example.tafakkurai.spiritual.PrefPersistence
import uz.tafakkur.network.TokenProvider
import uz.tafakkur.domain.usecase.SignInUseCase
import uz.tafakkur.domain.usecase.StartPhoneOtpUseCase
import uz.tafakkur.domain.usecase.GetTelegramBotUseCase
import uz.tafakkur.domain.usecase.VerifyTelegramCodeUseCase
import uz.tafakkur.domain.usecase.StartGoogleLoginUseCase
import uz.tafakkur.domain.usecase.PollGoogleLoginUseCase
import kotlinx.coroutines.delay
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

/**
 * Drops a session left behind by a previous install of the app.
 *
 * On iOS the Keychain is not part of the app sandbox: deleting the app leaves its items in
 * place, and a reinstall with the same bundle id reads them straight back. The tokens therefore
 * outlived the uninstall, [uz.tafakkur.network.TokenProvider.isLoggedIn] came up true on a
 * genuinely fresh install, and the login screen never appeared. Android has the opposite
 * behaviour — the Keystore key and its SharedPreferences both go — so there the marker is
 * simply written next to an already-empty store and nothing is cleared.
 *
 * Detected via [PrefPersistence] rather than a platform API because that table lives in the
 * app's own database file, which *is* removed on uninstall on both platforms.
 *
 * Must run before the gate reads [uz.tafakkur.domain.usecase.IsLoggedInUseCase].
 */
fun clearSessionIfReinstalled(prefs: PrefPersistence, tokens: TokenProvider) {
    if (prefs.get(AppPrefs.INSTALL_MARKER) != null) return
    tokens.clear()
    prefs.put(AppPrefs.INSTALL_MARKER, "1")
}

/** Raised when a provider button is tapped that this build has no credentials for. */
class ProviderNotConfigured(val provider: AuthProvider) : Exception("$provider sign-in is not configured")

/**
 * [OnboardingAuth] backed by the real backend, replacing [DemoOnboardingAuth].
 *
 * Every provider works end to end: the backend verifies the OTP itself, Apple's identity token
 * is minted by the system sheet ([appleSignIn]) and verified server-side against Apple's JWKS,
 * and Google is a browser round trip the backend drives from both ends — so no Google SDK is
 * linked into either platform and the client secret never reaches the device.
 */
class BackendOnboardingAuth(
    private val startPhoneOtp: StartPhoneOtpUseCase,
    private val signIn: SignInUseCase,
    private val getTelegramBot: GetTelegramBotUseCase,
    private val verifyTelegram: VerifyTelegramCodeUseCase,
    private val startGoogle: StartGoogleLoginUseCase,
    private val pollGoogle: PollGoogleLoginUseCase,
) : OnboardingAuth {

    /** Handed back by `POST /v1/auth/otp/start` and required to redeem the code. */
    private var verificationId: String? = null

    /** The sign-in the browser is currently working on, kept between start and poll. */
    private var googleState: String? = null
    private var googlePollMs: Long = 2000
    private var googleBudgetMs: Long = 0

    override suspend fun startGoogleLogin(): String {
        val start = startGoogle()
        googleState = start.state
        // A server that asks for an implausibly fast cadence would turn this loop into a
        // hot poll against its own rate limiter.
        googlePollMs = start.pollIntervalMs.coerceIn(500L, 10_000L)
        googleBudgetMs = start.expiresInSeconds * 1000
        return start.authUrl
    }

    override suspend fun awaitGoogleApproval(): Boolean {
        val state = googleState ?: return false
        // Polled rather than pushed: the device needs no public URL and no custom scheme,
        // and the state dies on its own when the server-side attempt expires.
        var waited = 0L
        while (waited < googleBudgetMs) {
            // A dropped connection mid-wait is not a failed sign-in — the user is in the
            // browser, possibly on a different network. Keep polling until the state
            // expires, then report the timeout once.
            val session = try {
                pollGoogle(state)
            } catch (e: Exception) {
                null
            }
            if (session != null) {
                googleState = null
                return true
            }
            delay(googlePollMs)
            waited += googlePollMs
        }
        googleState = null
        return false
    }

    override suspend fun telegramBot(): TelegramBotInfo {
        val bot = getTelegramBot()
        return TelegramBotInfo(bot.username, bot.deepLink, bot.codeLength)
    }

    override suspend fun verifyTelegramCode(code: String): Boolean = try {
        // Stores the tokens through TokenProvider, so the session survives a cold launch.
        verifyTelegram(code)
        true
    } catch (e: Exception) {
        // A wrong or expired code is the expected failure here; the screen keeps the user
        // on the field rather than treating it as an outage.
        false
    }

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
