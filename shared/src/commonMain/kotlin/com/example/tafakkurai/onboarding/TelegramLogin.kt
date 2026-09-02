package com.example.tafakkurai.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.rememberUrlOpener
import com.example.tafakkurai.ui.*
import kotlinx.coroutines.launch

/**
 * Telegram sign-in. The bot — not this app — asks for the phone number, through
 * Telegram's own "share contact" button, so no number is ever typed here and no
 * SMS is sent. The bot replies with a confirmation code; typing it in redeems a
 * session, and from then on only the JWT pair is used.
 */
@Composable
fun TelegramLoginScreen(
    auth: OnboardingAuth,
    onBack: () -> Unit,
    onAuthed: () -> Unit,
) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlOpener()

    var bot by remember { mutableStateOf<TelegramBotInfo?>(null) }
    var code by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    val codeLength = bot?.codeLength ?: 6

    LaunchedEffect(Unit) {
        bot = runCatching { auth.telegramBot() }.getOrNull()
    }

    // Takes the value explicitly: when the last digit is typed, `code` is still the
    // previous composition's value, so reading it here submitted nothing at all.
    fun submit(value: String) {
        if (value.length != codeLength || checking) return
        checking = true
        wrong = false
        scope.launch {
            val ok = auth.verifyTelegramCode(value)
            checking = false
            if (ok) onAuthed() else { wrong = true; code = "" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // The IME covers the code boxes on a small screen; imePadding lifts the column
            // and the scroll state lets the user reach anything still below the keyboard.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Box(contentAlignment = Alignment.Center) {
            GlowBehind(TELEGRAM_BLUE, size = 170, opacity = 0.20f)
            BreathingOrb(size = 66, accent = TELEGRAM_BLUE, purple = c.purple)
        }
        Spacer(Modifier.height(18.dp))
        TfText(
            tr("login.telegram"),
            color = c.fg, size = 29f, family = Fonts.serif, align = TextAlign.Center, lineHeight = 1.1f,
        )
        Spacer(Modifier.height(10.dp))
        TfText(
            tr("telegram.instructions", codeLength),
            color = c.fg2, size = 14f, lineHeight = 1.55f, align = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(20.dp))

        TelegramButton(
            label = if (bot == null) tr("telegram.preparing") else tr("telegram.open"),
            enabled = bot != null,
            onClick = { bot?.let { openUrl(it.deepLink) } },
        )

        Spacer(Modifier.height(20.dp))
        TfText(tr("telegram.enter_code"), color = c.fg3, size = 12.5f)
        Spacer(Modifier.height(10.dp))

        CodeField(
            value = code,
            length = codeLength,
            enabled = !checking,
            error = wrong,
            onChange = {
                wrong = false
                code = it
                // Submit the moment the last digit lands: the user has nothing left to decide.
                if (it.length == codeLength) submit(it)
            },
            onDone = { submit(code) },
        )

        Spacer(Modifier.height(14.dp))
        when {
            checking -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Spinner(c.fg2)
                TfText(tr("telegram.checking"), color = c.fg2, size = 13.5f)
            }

            wrong -> TfText(
                tr("telegram.invalid_code"),
                color = c.danger, size = 13f, lineHeight = 1.5f, align = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )

            bot != null -> TfText(
                tr("telegram.bot", "@${bot!!.botUsername}"),
                color = c.fg3, size = 12.5f,
            )
        }

        Spacer(Modifier.height(24.dp))
        TfText(
            tr("common.back"),
            color = c.fg3, size = 14f,
            modifier = Modifier.then(pressScale(onBack, 0.97f)).padding(8.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

private val TELEGRAM_BLUE = Color(0xFF2AABEE)

/**
 * One text field rendered as [length] boxes. A single field rather than one per
 * digit so paste-from-Telegram lands in one go and backspace behaves normally.
 */
@Composable
private fun CodeField(
    value: String,
    length: Int,
    enabled: Boolean,
    error: Boolean,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val c = LocalTf.current
    val description = tr("telegram.code_field_a11y", length)
    BasicTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() }.take(length)) },
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(length) { i ->
                    val ch = value.getOrNull(i)
                    val filled = ch != null
                    val border = when {
                        error -> c.danger
                        filled -> TELEGRAM_BLUE
                        else -> c.line
                    }
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.card)
                            .border(1.dp, border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        TfText(ch?.toString() ?: "", color = c.fg, size = 22f, weight = FontWeight.SemiBold)
                    }
                }
            }
        },
    )
}

@Composable
private fun TelegramButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (enabled) pressScale(onClick, 0.97f) else Modifier)
            .clip(shape)
            .background(if (enabled) TELEGRAM_BLUE else TELEGRAM_BLUE.copy(alpha = 0.45f))
            .padding(vertical = 15.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        TfText(label, color = Color.White, size = 15.5f, weight = FontWeight.SemiBold)
    }
}
