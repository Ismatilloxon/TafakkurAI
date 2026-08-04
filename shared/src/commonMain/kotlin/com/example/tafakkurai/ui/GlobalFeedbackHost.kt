package com.example.tafakkurai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.i18n.LocalStrings
import kotlinx.coroutines.delay
import org.koin.mp.KoinPlatform.getKoin
import uz.tafakkur.core.feedback.LoadingController
import uz.tafakkur.core.feedback.SnackbarController
import uz.tafakkur.core.feedback.SnackbarMessage
import uz.tafakkur.core.feedback.SnackbarType

/**
 * The single, always-mounted overlay that renders the global loader and the global snackbar
 * above every screen. It reads the two controllers from Koin and owns nothing but timing and
 * pixels — the state itself lives in [LoadingController] / [SnackbarController], so anything
 * in the app can drive this without a reference to it.
 *
 * Drop this as the last child of a full-size [Box] wrapping the app content and it covers the
 * language picker, onboarding and the main shell alike.
 */
@Composable
fun GlobalFeedbackHost() {
    val loading = remember { getKoin().get<LoadingController>() }
    val snackbar = remember { getKoin().get<SnackbarController>() }

    Box(Modifier.fillMaxSize()) {
        GlobalLoadingOverlay(loading)
        GlobalSnackbarHost(snackbar)
    }
}

// ── Loading ──────────────────────────────────────────────────────────────────

/** Debounce before showing — requests faster than this never flash the loader. */
private const val LOADER_SHOW_DELAY_MS = 150L

/** Once shown, stay up at least this long so a quick finish doesn't blink. */
private const val LOADER_MIN_VISIBLE_MS = 350L

@Composable
private fun GlobalLoadingOverlay(loading: LoadingController) {
    val active by loading.active.collectAsState()
    var show by remember { mutableStateOf(false) }

    // Anti-flicker: wait out a short debounce before showing, and hold a minimum time before
    // hiding. Re-keying on [active] cancels a pending show/hide the moment the state flips, so
    // a burst of quick calls resolves to a single steady spinner (or none at all).
    LaunchedEffect(active) {
        if (active) {
            delay(LOADER_SHOW_DELAY_MS)
            if (loading.active.value) show = true
        } else {
            if (show) delay(LOADER_MIN_VISIBLE_MS)
            show = false
        }
    }

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
        modifier = Modifier.fillMaxSize(),
    ) {
        val c = LocalTf.current
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0f, 0f, 0f, 0.28f))
                // Swallow taps while a request is running so nothing behind the scrim reacts.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.card2)
                    .border(0.5.dp, c.hair, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = c.teal,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

// ── Snackbar ─────────────────────────────────────────────────────────────────

@Composable
private fun GlobalSnackbarHost(controller: SnackbarController) {
    val current by controller.current.collectAsState()
    // Keep the last message around so the exit animation has something to draw after
    // [current] has already gone back to null.
    var shown by remember { mutableStateOf<SnackbarMessage?>(null) }

    LaunchedEffect(current?.id) {
        val message = current ?: return@LaunchedEffect
        shown = message
        delay(message.durationMs)
        controller.dismiss(message.id) // auto-dismiss; a new message cancels this via the key
    }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(tween(240)) { -it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
        ) {
            shown?.let { message ->
                SnackbarCard(message = message, onDismiss = { controller.dismiss(message.id) })
            }
        }
    }
}

@Composable
private fun SnackbarCard(message: SnackbarMessage, onDismiss: () -> Unit) {
    val c = LocalTf.current
    val strings = LocalStrings.current

    val accent = when (message.type) {
        SnackbarType.SUCCESS -> c.green
        SnackbarType.WARNING -> c.warn
        SnackbarType.ERROR -> c.danger
    }
    val icon = when (message.type) {
        SnackbarType.SUCCESS -> "checkCircle"
        SnackbarType.WARNING -> "zap"
        SnackbarType.ERROR -> "x"
    }
    // Prefer a ready string (already localised, or a user-friendly backend message); otherwise
    // resolve the i18n key against the active language.
    val label = message.text ?: message.textKey?.let { strings[it] } ?: ""

    Row(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card2)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            TfIcon(icon, size = 16, color = accent)
        }

        TfText(
            text = label,
            color = c.fg,
            size = 14f,
            weight = FontWeight.Medium,
            lineHeight = 1.35f,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(2.dp))

        Box(
            Modifier.size(28.dp).clip(CircleShape).then(pressScale(onDismiss)),
            contentAlignment = Alignment.Center,
        ) {
            TfIcon("x", size = 14, color = c.fg3)
        }
    }
}
