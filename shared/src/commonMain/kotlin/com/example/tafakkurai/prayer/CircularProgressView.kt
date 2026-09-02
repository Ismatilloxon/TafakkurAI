package com.example.tafakkurai.prayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.tafakkurai.PrayerTime
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.AmbientGlow
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.Overline
import com.example.tafakkurai.ui.Pill
import com.example.tafakkurai.ui.TfIcon
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.pressScale
import com.example.tafakkurai.ui.soft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import uz.tafakkur.domain.usecase.TrackPrayerUseCase

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
    snapEpoch: Int = 0,                               // bump to jump to [progress] without animating (e.g. app resume)
    content: @Composable () -> Unit = {},
) {
    val target = progress.coerceIn(0f, 1f)
    val anim = remember { Animatable(target) }
    var lastSnapEpoch by remember { mutableStateOf(snapEpoch) }
    LaunchedEffect(target, snapEpoch) {
        // Snap (no sweep) when the caller bumped the epoch — a resume must not animate the
        // ring catching up on the time that passed in the background — or on interval
        // rollover (1 → 0). Otherwise fill smoothly toward the new target.
        val snap = snapEpoch != lastSnapEpoch || target < anim.value - 0.5f
        lastSnapEpoch = snapEpoch
        if (snap) {
            anim.snapTo(target)
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
 * Icon + short label for every prayer, laid out around [ringSize]'s ring at the angle
 * of its own time: midnight is 12 o'clock, the day runs clockwise. Positions are
 * derived from [prayers] (`min` = minutes since midnight), so a new schedule — GPS
 * move, next day, another calculation method — moves the markers with it.
 *
 * The box is grown past [ringSize] so labels sitting outside the ring aren't clipped.
 */
@Composable
fun PrayerRingMarkers(
    prayers: List<PrayerTime>,
    modifier: Modifier = Modifier,
    ringSize: Int = 268,
    strokeWidth: Int = 9,
    labelGap: Int = 28,
    highlightKey: String = "",
) {
    val c = LocalTf.current
    // Same geometry the ring itself uses, so a dot lands exactly on the stroke.
    val ringRadius = ringSize / 2f - (strokeWidth / 2f + 14f)
    // Ring + a label ring outside it (gap + ~24dp for the icon/label stack itself).
    val boxSize = (ringSize + labelGap * 2 + 24).dp

    Box(modifier.size(boxSize), contentAlignment = Alignment.Center) {
        prayers.forEach { p ->
            val angle = ((p.min / 1440f) * 360f - 90f) * (PI.toFloat() / 180f)
            val ca = cos(angle)
            val sa = sin(angle)
            val on = p.key == highlightKey
            val tint = when {
                on -> c.gold
                p.key == "sunrise" -> c.fg3
                else -> c.accent
            }

            // Dot on the ring.
            Box(
                Modifier
                    .offset((ringRadius * ca).dp, (ringRadius * sa).dp)
                    .size(if (on) 11.dp else 9.dp)
                    .clip(CircleShape)
                    .background(c.bg1),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(if (on) 7.dp else 5.dp).clip(CircleShape).background(tint))
            }

            // Icon + short label just outside the ring.
            val lr = ringRadius + labelGap
            Column(
                Modifier.offset((lr * ca).dp, (lr * sa).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TfIcon(p.icon, size = if (on) 16 else 14, color = tint)
                TfText(
                    p.short,
                    color = if (on) c.fg else c.fg3,
                    size = 10.5f,
                    weight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
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
    onClick: () -> Unit = {},
    vm: PrayerProgressViewModel = viewModel { PrayerProgressViewModel(getKoin().get()) },
) {
    val c = LocalTf.current
    val ui = vm.state

    val scope = rememberCoroutineScope()
    val trackPrayer = remember { getKoin().get<TrackPrayerUseCase>() }
    val prayerRepository = remember { getKoin().get<LocationPrayerScheduleRepository>() }
    // The current prayer's mark, from the shared store — tapping cycles it and persists.
    val markStore = remember { getKoin().get<PrayerMarkStore>() }
    val hasSchedule = ui.currentKey.isNotBlank() && ui.nextKey.isNotBlank()
    val mark = if (hasSchedule) markStore.stateOf(ui.currentKey) else PrayerMark.NONE
    val nextPrayer = if (hasSchedule) localizedPrayerName(ui.nextKey) else ""
    val countdown = if (hasSchedule) localizedPrayerCountdown(ui.minutesToNext) else ""

    // Bumped on resume so the ring jumps to the fresh fraction instead of sweeping to catch up.
    var snapEpoch by remember { mutableStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                snapEpoch++         // snap, don't animate, over the backgrounded gap
                vm.refresh()
                markStore.refresh() // pick up a date rollover / marks changed elsewhere
                // Re-fetch every time, not only when there is no fix: it picks up a
                // permission granted in settings, a move to another city, and a
                // changed emulator location — all without a reinstall.
                scope.launch { prayerRepository.refresh() }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // Tapping the ring opens the prayer tracker. The inner "Complete prayer" button
    // has its own gesture, so it still logs the prayer without opening the tracker.
    Box(modifier.then(pressScale(onClick, 0.98f)), contentAlignment = Alignment.Center) {
        AmbientGlow(c.gold, opacity = 0.20f)
        // Markers read the same live schedule the ring/card use, so they stay in sync.
        val schedule = prayerRepository.schedule(0)
        PrayerRingMarkers(prayers = schedule, highlightKey = ui.currentKey)
        CircularProgressView(progress = ui.fraction, color = c.gold, track = c.line, snapEpoch = snapEpoch) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (!hasSchedule) {
                    Overline(tr("prayer.hero.location_needed"))
                    TfText("--:--", color = c.fg, size = 38f, weight = FontWeight.Bold, family = Fonts.mono)
                    Pill(tr("prayer.location.required_short"), c.accent, c.accent.soft(0.16f))
                } else {
                    Overline(tr("prayer.hero.next", nextPrayer.uppercase()))
                    TfText(
                        ui.nextAt, color = c.fg, size = 38f, weight = FontWeight.Bold,
                        family = Fonts.mono, letterSpacing = 1f,
                    )
                    Pill(tr("prayer.hero.in", countdown), c.accent, c.accent.soft(0.16f))

                // Tap cycles: Complete prayer → Completed (on time) → Jamaat → Complete prayer.
                val btnColor = if (mark == PrayerMark.NONE) c.gold else c.green
                val btnIcon = when (mark) {
                    PrayerMark.NONE -> "check"
                    PrayerMark.ONTIME -> "checkCircle"
                    PrayerMark.JAMAAT -> "users"
                }
                val btnLabel = when (mark) {
                    PrayerMark.NONE -> tr("prayer.action.complete")
                    PrayerMark.ONTIME -> tr("prayer.state.ontime")
                    PrayerMark.JAMAAT -> tr("prayer.state.jamaat")
                }
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(btnColor.soft(0.16f))
                        .then(
                            pressScale({
                                // Local-first: persist the mark immediately (survives restart,
                                // mirrored to card + tracker). Backend is best-effort, not a gate.
                                markStore.cycle(ui.currentKey)
                                scope.launch { runCatching { trackPrayer(ui.currentKey) } }
                            }, 0.94f)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TfIcon(btnIcon, size = 14, color = btnColor)
                        TfText(btnLabel, color = btnColor, size = 13f, weight = FontWeight.SemiBold)
                    }
                }
                }
            }
        }
    }
}
