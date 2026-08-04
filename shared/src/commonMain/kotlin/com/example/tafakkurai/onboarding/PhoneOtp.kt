package com.example.tafakkurai.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.tafakkurai.i18n.tr

private const val PHONE_LEN = 9
private const val OTP_LEN = 6

// ── Phone entry ──────────────────────────────────────────────────────────────
@Composable
fun PhoneEntryScreen(
    auth: OnboardingAuth,
    onBack: () -> Unit,
    onCodeSent: (phoneE164: String, phoneDisplay: String) -> Unit,
) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val ready = digits.length == PHONE_LEN
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("phone.overline"), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            AccentTile { PhoneTileGlyph(c.accent, 26) }
            Spacer(Modifier.height(22.dp))
            TfText(tr("phone.title"), color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            TfText(tr("phone.subtitle"), color = c.fg2, size = 14f, align = TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            // prefix chip + grouped input
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card3)
                    .border(1.dp, if (ready) c.accent.soft(0.5f) else c.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(c.card).padding(horizontal = 12.dp, vertical = 12.dp)) {
                    TfText("+998", color = c.fg, size = 17f, weight = FontWeight.SemiBold, family = Fonts.mono)
                }
                Box(Modifier.weight(1f)) {
                    if (digits.isEmpty()) TfText("90 123 45 67", color = c.fg3, size = 18f, family = Fonts.mono, letterSpacing = 1f)
                    BasicTextField(
                        value = digits,
                        onValueChange = { input -> digits = input.filter { it.isDigit() }.take(PHONE_LEN) },
                        textStyle = TextStyle(color = c.fg, fontSize = 18.sp, fontFamily = Fonts.mono, letterSpacing = 1.sp),
                        cursorBrush = SolidColor(c.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = UzPhoneTransformation,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            SubmitButton(
                label = tr("phone.send_code"), accent = c.accent, enabled = ready && !sending, loading = sending,
            ) {
                sending = true
                scope.launch {
                    val e164 = "+998$digits"
                    auth.sendOtp(e164)
                    sending = false
                    onCodeSent(e164, formatUzPhone(digits))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── OTP ──────────────────────────────────────────────────────────────────────
@Composable
fun OtpScreen(
    auth: OnboardingAuth,
    phoneE164: String,
    phoneDisplay: String,
    onBack: () -> Unit,
    onVerified: () -> Unit,
) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var verified by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(30) }
    var resendKey by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // resend countdown, restarts when a new code is requested
    LaunchedEffect(resendKey) {
        seconds = 30
        while (seconds > 0) { delay(1000); seconds-- }
    }
    // auto-verify on the 6th digit
    LaunchedEffect(code) {
        if (code.length == OTP_LEN && !verified) {
            if (auth.verifyOtp(code)) {
                verified = true
                delay(500)
                onVerified()
            } else {
                code = ""
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("otp.overline"), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            AccentTile { TfIcon("messageCircle", size = 26, color = c.accent) }
            Spacer(Modifier.height(22.dp))
            TfText(tr("otp.title"), color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            TfText(
                tr("otp.sent_to", phoneDisplay),
                color = c.fg2, size = 14f, align = TextAlign.Center, lineHeight = 1.5f,
            )
            Spacer(Modifier.height(28.dp))

            // six cells, driven by one hidden field
            Box {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(OTP_LEN) { i ->
                        OtpCell(
                            char = code.getOrNull(i),
                            active = (i == code.length && !verified) || i < code.length,
                        )
                    }
                }
                BasicTextField(
                    value = code,
                    onValueChange = { if (!verified) code = it.filter { ch -> ch.isDigit() }.take(OTP_LEN) },
                    textStyle = TextStyle(color = Color.Transparent),
                    cursorBrush = SolidColor(Color.Transparent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.matchParentSize().focusRequester(focus),
                )
            }

            Spacer(Modifier.height(20.dp))
            if (verified) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TfIcon("checkCircle", size = 18, color = c.green)
                    TfText(tr("otp.verified"), color = c.green, size = 15f, weight = FontWeight.SemiBold)
                }
            } else if (seconds > 0) {
                TfText(tr("otp.resend_in", seconds.toString().padStart(2, '0')), color = c.fg3, size = 13f, family = Fonts.mono)
            } else {
                Box(Modifier.then(pressScale({
                    scope.launch { runCatching { auth.sendOtp(phoneE164) } }
                    code = ""
                    resendKey++
                }, 0.94f)).padding(6.dp)) {
                    TfText(tr("otp.resend"), color = c.accent, size = 14f, weight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))
            TfText(tr("otp.demo_hint"), color = c.fg4, size = 12f, align = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OtpCell(char: Char?, active: Boolean) {
    val c = LocalTf.current
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .size(width = 46.dp, height = 56.dp)
            .clip(shape)
            .background(if (active) c.accent.soft(0.08f) else c.card3)
            .border(if (active) 1.5.dp else 1.dp, if (active) c.accent else c.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        TfText(char?.toString() ?: "", color = c.fg, size = 22f, weight = FontWeight.SemiBold, family = Fonts.display)
    }
}

// ── Shared bits ──────────────────────────────────────────────────────────────
@Composable
private fun AccentTile(content: @Composable () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.accent.soft(0.14f)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SubmitButton(label: String, accent: Color, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val c = LocalTf.current
    val fg = onAccent(accent)
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) pressScale(onClick, 0.94f) else Modifier)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) accent else c.card3)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) Spinner(fg)
        else TfText(label, color = if (enabled) fg else c.fg3, size = 16f, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun PhoneTileGlyph(color: Color, size: Int) {
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

// ── Uzbek phone formatting: 9 digits → "90 123 45 67" (groups 2-3-2-2) ────────
fun formatUzPhone(digits: String): String {
    val d = digits.filter { it.isDigit() }.take(PHONE_LEN)
    val sb = StringBuilder()
    for (i in d.indices) {
        sb.append(d[i])
        if ((i == 1 || i == 4 || i == 6) && i != d.lastIndex) sb.append(' ')
    }
    return sb.toString()
}

val UzPhoneTransformation = VisualTransformation { text ->
    val out = formatUzPhone(text.text)
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            var t = offset
            if (offset >= 2) t++
            if (offset >= 5) t++
            if (offset >= 7) t++
            return t.coerceAtMost(out.length)
        }

        override fun transformedToOriginal(offset: Int): Int {
            var o = offset
            if (offset > 2) o--
            if (offset > 6) o--
            if (offset > 9) o--
            return o.coerceIn(0, PHONE_LEN)
        }
    }
    TransformedText(AnnotatedString(out), mapping)
}
