package com.example.tafakkurai.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.tafakkurai.ui.LocalTf

private enum class Step { INTRO, LOGIN, TELEGRAM, PHONE, OTP }

/**
 * Drives the onboarding + auth flow: welcome carousel → login → phone → OTP.
 * [onFinished] fires once the user is authenticated (route to the app root).
 *
 * [start] is the spec's debug "Launch at": AUTO (intro on first run, login after
 * the carousel has been seen), or a forced INTRO / LOGIN / APP entry point.
 */
@Composable
fun OnboardingRoot(
    auth: OnboardingAuth,
    gate: OnboardingGate,
    start: OnbStart = OnbStart.AUTO,
    onFinished: () -> Unit,
) {
    val c = LocalTf.current
    var step by remember {
        mutableStateOf(
            when (start) {
                OnbStart.INTRO -> Step.INTRO
                OnbStart.LOGIN, OnbStart.APP -> Step.LOGIN
                OnbStart.AUTO -> if (gate.completed) Step.LOGIN else Step.INTRO
            }
        )
    }
    var phoneE164 by remember { mutableStateOf("") }
    var phoneDisplay by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(c.bg1).windowInsetsPadding(WindowInsets.systemBars),
    ) {
        when (step) {
            Step.INTRO -> WelcomeCarousel(
                onSkip = { gate.completed = true; step = Step.LOGIN },
                onFinish = { gate.completed = true; step = Step.LOGIN },
            )

            Step.LOGIN -> LoginScreen(
                auth = auth,
                onAuthed = onFinished,
                // Without this the PHONE/OTP steps below were unreachable — the login screen
                // had no way to reach them, so the only working sign-in path was dead code.
                onPhone = { step = Step.PHONE },
                onTelegram = { step = Step.TELEGRAM },
            )

            Step.TELEGRAM -> TelegramLoginScreen(
                auth = auth,
                onBack = { step = Step.LOGIN },
                onAuthed = onFinished,
            )

            Step.PHONE -> PhoneEntryScreen(
                auth = auth,
                onBack = { step = Step.LOGIN },
                onCodeSent = { e164, display ->
                    phoneE164 = e164
                    phoneDisplay = display
                    step = Step.OTP
                },
            )

            Step.OTP -> OtpScreen(
                auth = auth,
                phoneE164 = phoneE164,
                phoneDisplay = phoneDisplay,
                onBack = { step = Step.PHONE },
                onVerified = onFinished,
            )
        }
    }
}
