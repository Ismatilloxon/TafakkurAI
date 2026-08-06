package com.example.tafakkurai.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.rememberUrlOpener
import com.example.tafakkurai.ui.*
import kotlinx.coroutines.launch
import com.example.tafakkurai.i18n.tr
import org.koin.mp.KoinPlatform.getKoin
import uz.tafakkur.core.feedback.SnackbarController
import uz.tafakkur.core.feedback.SnackbarType

/**
 * Login: breathing-orb hero, three provider buttons and a privacy footer.
 * Apple/Google authenticate directly; phone opens number entry.
 */
@Composable
fun LoginScreen(
    auth: OnboardingAuth,
    onAuthed: () -> Unit,
    onPhone: () -> Unit,
    onTelegram: () -> Unit,
) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { getKoin().get<SnackbarController>() }
    val openUrl = rememberUrlOpener()
    var loading by remember { mutableStateOf<AuthProvider?>(null) }
    val busy = loading != null

    fun direct(provider: AuthProvider) {
        if (busy) return
        loading = provider
        scope.launch {
            try {
                auth.signInWith(provider)
                onAuthed()
            } catch (e: AppleSignInCancelled) {
                // Dismissing the Apple sheet is a decision, not a failure — say nothing and
                // leave the user on the login screen with the button re-enabled.
            } catch (e: ProviderNotConfigured) {
                // Say so instead of reporting success: the demo stub used to sign the user in
                // without a token, which is why every screen behind the login wall came up
                // empty under a "session expired" toast.
                snackbar.show(SnackbarType.WARNING, textKey = "login.provider_unavailable")
            } catch (e: Exception) {
                snackbar.show(SnackbarType.ERROR, textKey = "login.failed")
            } finally {
                loading = null
            }
        }
    }

    /**
     * Google is a browser round trip, not an SDK call: the app opens the account chooser in
     * the system browser and then waits. It has to be the *system* browser — Google rejects
     * OAuth started inside an embedded WebView with `disallowed_useragent`.
     */
    fun google() {
        if (busy) return
        loading = AuthProvider.GOOGLE
        scope.launch {
            try {
                openUrl(auth.startGoogleLogin())
                // Only after the browser leg finishes does a session exist; until then the
                // button keeps spinning, so the user knows the app is still expecting them.
                if (auth.awaitGoogleApproval()) onAuthed()
                else snackbar.show(SnackbarType.WARNING, textKey = "login.google_timeout")
            } catch (e: ProviderNotConfigured) {
                snackbar.show(SnackbarType.WARNING, textKey = "login.provider_unavailable")
            } catch (e: Exception) {
                snackbar.show(SnackbarType.ERROR, textKey = "login.failed")
            } finally {
                loading = null
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(contentAlignment = Alignment.Center) {
            GlowBehind(c.accent, size = 240, opacity = 0.22f)
            BreathingOrb(size = 92, accent = c.accent, purple = c.purple)
        }
        Spacer(Modifier.height(28.dp))
        TfText(tr("login.title"), color = c.fg, size = 38f, family = Fonts.serif, align = TextAlign.Center, lineHeight = 1.05f)
        Spacer(Modifier.height(12.dp))
        TfText(
            tr("login.subtitle"),
            color = c.fg2, size = 14.5f, lineHeight = 1.55f, align = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(36.dp))

        // ── Provider buttons ──
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Primary path: no phone number, no third-party SDK — the backend issues a
            // one-time code and the bot confirms it (POST /v1/auth/telegram/start → /poll).
            AuthButton(
                bg = TELEGRAM_BUTTON_BG, border = null, label = "Telegram orqali kirish",
                labelColor = Color.White, spinnerColor = Color.White,
                loading = false, enabled = !busy,
                leading = { TelegramMark(Color.White, 19) },
                onClick = { if (!busy) onTelegram() },
            )
            AuthButton(
                bg = Color.White, border = null, label = tr("login.apple"),
                labelColor = Color.Black, spinnerColor = Color.Black,
                loading = loading == AuthProvider.APPLE, enabled = !busy,
                leading = { AppleMark(Color.Black, 19) },
                onClick = { direct(AuthProvider.APPLE) },
            )
            AuthButton(
                bg = c.card, border = c.line, label = tr("login.google"),
                labelColor = c.fg, spinnerColor = c.fg,
                loading = loading == AuthProvider.GOOGLE, enabled = !busy,
                leading = { GoogleMark(18) },
                onClick = { google() },
            )
            // Phone/OTP is intentionally not offered: Telegram covers the same "no third-party
            // SDK" case without an SMS bill. The screens and endpoints still exist behind
            // [onPhone] if it is ever brought back.
        }

        Spacer(Modifier.height(28.dp))
        // ── Footer ──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShieldCheckGlyph(c.green, 15)
            TfText(tr("login.privacy"), color = c.green, size = 12.5f, weight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        TfText(
            tr("login.terms"),
            color = c.fg3, size = 11.5f, lineHeight = 1.5f, align = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun AuthButton(
    bg: Color,
    border: Color?,
    label: String,
    labelColor: Color,
    spinnerColor: Color,
    loading: Boolean,
    enabled: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .then(pressScale(onClick, 0.97f))
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .alpha(if (enabled) 1f else 0.6f)
            .padding(vertical = 15.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Spinner(spinnerColor)
                TfText(tr("login.signing_in"), color = labelColor, size = 15.5f, weight = FontWeight.SemiBold)
            }
        } else {
            Box(Modifier.align(Alignment.CenterStart)) { leading() }
            TfText(label, color = labelColor, size = 15.5f, weight = FontWeight.SemiBold)
        }
    }
}

// ── Loading spinner ──────────────────────────────────────────────────────────
@Composable
fun Spinner(color: Color, size: Int = 18) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(750, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(size.dp)) {
        drawArc(color, startAngle = a, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
    }
}

private val TELEGRAM_BUTTON_BG = Color(0xFF2AABEE)

/** Paper-plane mark, drawn rather than bundled — the icon registry has no Telegram entry. */
@Composable
private fun TelegramMark(color: Color, size: Int) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val plane = Path().apply {
            moveTo(w * 0.06f, h * 0.46f)
            lineTo(w * 0.94f, h * 0.10f)
            lineTo(w * 0.78f, h * 0.90f)
            lineTo(w * 0.48f, h * 0.62f)
            close()
        }
        drawPath(plane, color)
    }
}

// ── Brand / auth glyphs (no matching entries in the icon registry) ────────────
@Composable
private fun AppleMark(color: Color, size: Int) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width; val h = this.size.height
        // body — two lobes
        val lobeR = w * 0.28f
        drawCircle(color, radius = lobeR, center = Offset(w * 0.38f, h * 0.62f))
        drawCircle(color, radius = lobeR, center = Offset(w * 0.62f, h * 0.62f))
        drawRect(color, topLeft = Offset(w * 0.24f, h * 0.44f), size = Size(w * 0.52f, h * 0.30f))
        // leaf
        drawCircle(color, radius = w * 0.11f, center = Offset(w * 0.60f, h * 0.20f))
        // bite (background-colored notch — reads on the white Apple button)
        drawCircle(Color.White, radius = w * 0.13f, center = Offset(w * 0.86f, h * 0.55f))
    }
}

@Composable
private fun GoogleMark(size: Int) {
    val blue = Color(0xFF4285F4); val red = Color(0xFFEA4335)
    val yellow = Color(0xFFFBBC05); val green = Color(0xFF34A853)
    Canvas(Modifier.size(size.dp)) {
        val stroke = this.size.minDimension * 0.22f
        val inset = stroke / 2f
        val tl = Offset(inset, inset)
        val sz = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawArc(red, startAngle = 135f, sweepAngle = 90f, useCenter = false, topLeft = tl, size = sz, style = Stroke(stroke))
        drawArc(yellow, startAngle = 90f, sweepAngle = 45f, useCenter = false, topLeft = tl, size = sz, style = Stroke(stroke))
        drawArc(green, startAngle = 20f, sweepAngle = 70f, useCenter = false, topLeft = tl, size = sz, style = Stroke(stroke))
        drawArc(blue, startAngle = -35f, sweepAngle = 55f, useCenter = false, topLeft = tl, size = sz, style = Stroke(stroke))
        // blue crossbar of the G
        drawRect(blue, topLeft = Offset(this.size.width * 0.5f, this.size.height * 0.42f), size = Size(this.size.width * 0.5f - inset, stroke))
    }
}

@Composable
private fun PhoneGlyph(color: Color, size: Int) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width; val h = this.size.height
        val body = androidx.compose.ui.geometry.RoundRect(
            left = w * 0.28f, top = h * 0.08f, right = w * 0.72f, bottom = h * 0.92f,
            radiusX = w * 0.10f, radiusY = w * 0.10f,
        )
        drawPath(Path().apply { addRoundRect(body) }, color = color, style = Stroke(width = w * 0.09f))
        drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.5f, h * 0.80f))
    }
}

@Composable
private fun ShieldCheckGlyph(color: Color, size: Int) {
    Canvas(Modifier.size(size.dp)) {
        val w = this.size.width; val h = this.size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.90f, h * 0.22f)
            lineTo(w * 0.90f, h * 0.52f)
            cubicTo(w * 0.90f, h * 0.78f, w * 0.72f, h * 0.90f, w * 0.5f, h * 0.96f)
            cubicTo(w * 0.28f, h * 0.90f, w * 0.10f, h * 0.78f, w * 0.10f, h * 0.52f)
            lineTo(w * 0.10f, h * 0.22f)
            close()
        }
        drawPath(shield, color = color, style = Stroke(width = w * 0.09f))
        val check = Path().apply {
            moveTo(w * 0.34f, h * 0.50f)
            lineTo(w * 0.46f, h * 0.63f)
            lineTo(w * 0.68f, h * 0.37f)
        }
        drawPath(check, color = color, style = Stroke(width = w * 0.10f, cap = StrokeCap.Round))
    }
}
