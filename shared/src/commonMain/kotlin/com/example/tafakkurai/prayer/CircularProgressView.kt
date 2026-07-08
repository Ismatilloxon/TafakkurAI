package com.example.tafakkurai.prayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import com.example.tafakkurai.ui.AmbientGlow
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.Overline
import com.example.tafakkurai.ui.Pill
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.soft

/**
 * Clock-style ring. The track is a full circle; [progress] (0..1) fills clockwise
 * from 12 o'clock. Fill animates smoothly second-to-second, but when a prayer
 * passes — progress jumps 1 → 0 — it *snaps* to empty instead of unwinding
 * backwards, matching how a fresh interval begins.
 */
@Composable
fun CircularProgressView(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Int = 268,
    strokeWidth: Int = 9,
    color: Color = LocalTf.current.gold,
    track: Color = LocalTf.current.line,
    content: @Composable () -> Unit = {},
) {
    val target = progress.coerceIn(0f, 1f)
    val anim = remember { Animatable(target) }
    LaunchedEffect(target) {
        if (target < anim.value - 0.5f) {
            anim.snapTo(target)                       // interval rolled over → reset, don't rewind
        } else {
            anim.animateTo(target, tween(900, easing = LinearEasing))
        }
    }

    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val sw = strokeWidth.dp.toPx()
            val inset = sw / 2f + 14.dp.toPx()
            val arc = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(sw))
            drawArc(
                color, -90f, 360f * anim.value, false, tl, arc,
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * Today hero: the live prayer-progress ring. Pulls its [PrayerProgressViewModel],
 * renders the current fill, and forces an immediate recompute whenever the app
 * returns to the foreground so it never shows a stale frame after background.
 */
@Composable
fun SalahProgressHero(
    modifier: Modifier = Modifier,
    vm: PrayerProgressViewModel = viewModel { PrayerProgressViewModel() },
) {
    val c = LocalTf.current
    val ui = vm.state

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        AmbientGlow(c.gold, opacity = 0.20f)
        CircularProgressView(progress = ui.fraction, color = c.gold, track = c.line) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Overline("NEXT · ${ui.nextName.uppercase()}")
                TfText(
                    ui.nextAt, color = c.fg, size = 38f, weight = FontWeight.Bold,
                    family = Fonts.mono, letterSpacing = 1f,
                )
                Pill("in ${ui.countdown}", c.accent, c.accent.soft(0.16f))
            }
        }
    }
}
