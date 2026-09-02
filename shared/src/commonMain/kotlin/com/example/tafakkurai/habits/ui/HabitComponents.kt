package com.example.tafakkurai.habits.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.composed
import com.example.tafakkurai.i18n.tr
import androidx.compose.ui.zIndex
import com.example.tafakkurai.habits.*
import com.example.tafakkurai.ui.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Habits component kit — the "Fajr" design language expressed in Compose.
 *
 * Every primitive here maps 1:1 to one in the prototype's `habits-kit.jsx`, and each takes
 * a palette *token* colour resolved by the caller from [LocalTf], never a hex value: the
 * same habit has to read as "gold" across all seven themes.
 *
 * Nothing here knows about the store. These take data and callbacks so the screens stay
 * the only place that talks to [com.example.tafakkurai.habits.HabitStore].
 */

/**
 * Honours the system "reduce motion" setting.
 *
 * Defaulted rather than detected because Compose Multiplatform has no common API for it;
 * the platform layer provides the real value (`UIAccessibility.isReduceMotionEnabled` on
 * iOS, `Settings.Global.ANIMATOR_DURATION_SCALE` on Android). Defaulting to false means
 * animations are on until somebody wires the flag, which is the right failure direction —
 * a missing provider must not silently strip the module's motion.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** The prototype's `cubic-bezier(.16,1,.3,1)` — a long, soft settle. */
private val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** The overshoot on a completion morph. */
private val EaseBack = CubicBezierEasing(0.34f, 1.56f, 0.5f, 1f)

/** Heatmap "nothing here" cell. Neutral so it reads the same on light and dark themes. */
@Composable
fun heatTrack(): Color = Color(130 / 255f, 140 / 255f, 165 / 255f, 0.16f)

// ─────────────────────────────────────────────────────────────────────────────
// Ring
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The progress ring behind every hero on this module.
 *
 * The arc is drawn with a sweep gradient rather than a flat colour because a flat ring at
 * this size reads as a loading spinner; the two-stop gradient is what makes it read as a
 * measurement. [glow] adds the soft outer bloom the prototype gets from `drop-shadow`.
 */
@Composable
fun HabitRing(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    gradientTo: Color? = null,
    size: Int = 132,
    stroke: Int = 11,
    glow: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = LocalTf.current
    val reduce = LocalReduceMotion.current
    val target = value.coerceIn(0f, 1f)
    val swept by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduce) tween(0) else tween(1050, easing = EaseOutExpo),
        label = "ring",
    )
    val track = heatTrack()
    val end = gradientTo ?: c.purple

    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val sw = stroke.dp.toPx()
            val inset = sw / 2f
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arc, style = Stroke(sw))
            if (glow && swept > 0.01f) {
                // Two wider, fainter passes stand in for a Gaussian drop-shadow, which
                // Compose has no cross-platform primitive for.
                drawArc(
                    color.copy(alpha = 0.18f), -90f, 360f * swept, false,
                    topLeft, arc, style = Stroke(sw * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    color.copy(alpha = 0.22f), -90f, 360f * swept, false,
                    topLeft, arc, style = Stroke(sw * 1.5f, cap = StrokeCap.Round),
                )
            }
            drawArc(
                Brush.linearGradient(listOf(color, end), Offset.Zero, Offset(this.size.width, this.size.height)),
                -90f, 360f * swept, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** A number that counts up on first appearance. Ease-out-cubic over 700ms. */
@Composable
fun CountUp(
    to: Float,
    color: Color,
    size: Float = 34f,
    weight: FontWeight = FontWeight.Bold,
    family: androidx.compose.ui.text.font.FontFamily = Fonts.display,
    format: (Float) -> String = { it.roundToInt().toString() },
) {
    val reduce = LocalReduceMotion.current
    val animated = remember { Animatable(if (reduce) to else 0f) }
    LaunchedEffect(to, reduce) {
        if (reduce) animated.snapTo(to) else animated.animateTo(to, tween(700, easing = EaseOutExpo))
    }
    TfText(format(animated.value), color = color, size = size, weight = weight, family = family)
}

// ─────────────────────────────────────────────────────────────────────────────
// Small badges
// ─────────────────────────────────────────────────────────────────────────────

/** Flame + count. [live] adds the slow breathing pulse used on the detail hero. */
@Composable
fun StreakBadge(count: Int, color: Color, size: Float = 11f, live: Boolean = false) {
    val reduce = LocalReduceMotion.current
    val scale = if (live && !reduce) {
        val t = rememberInfiniteTransition(label = "flame")
        t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "flameScale").value
    } else {
        1f
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TfIcon("flame", size = (size + 2).toInt(), color = color, modifier = Modifier.scale(scale))
        TfText("$count", color = color, size = size, weight = FontWeight.Bold, family = Fonts.mono)
    }
}

/** 1–3 filled dots. Reads at a glance where a word would not fit. */
@Composable
fun DifficultyDots(level: HabitDifficulty, color: Color) {
    val off = Color(130 / 255f, 140 / 255f, 165 / 255f, 0.28f)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (i < level.dots) color else off))
        }
    }
}

@Composable
fun CategoryChip(category: HabitCategory, active: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val c = LocalTf.current
    val tint = c.named(category.color)
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) tint.soft(0.18f) else c.card)
            .border(1.dp, if (active) tint else c.sep, RoundedCornerShape(999.dp))
            .then(pressScale(onClick, 0.95f))
            .padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TfIcon(category.icon, size = if (small) 13 else 15, color = if (active) tint else c.fg2)
        TfText(
            tr(category.labelKey),
            color = if (active) c.fg else c.fg2,
            size = if (small) 12f else 13f,
            weight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** Rounded pill filter, the one the home screen's slot row uses. */
@Composable
fun FilterPill(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) accent else c.card)
            .border(1.dp, if (active) accent else c.sep, RoundedCornerShape(999.dp))
            .then(pressScale(onClick, 0.95f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        TfText(
            label,
            color = if (active) onAccent(accent) else c.fg2,
            size = 13f,
            weight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * Readable foreground for a filled accent surface.
 *
 * The prototype hardcodes `#0B0F12`, which vanishes on the two light themes where the
 * accent itself is dark. Deriving it from perceived luminance keeps every filled button
 * legible in all seven palettes.
 */
fun onAccent(background: Color): Color {
    val l = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (l > 0.6f) Color(0xFF0B0F12) else Color.White
}

// ─────────────────────────────────────────────────────────────────────────────
// Completion control
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The morphing completion control: ring → filled disc with a check, plus an expanding
 * ripple on the tap that completed it.
 *
 * [partial] renders the current count inside the ring for a count/duration goal, which is
 * what makes "5 of 8 glasses" a single control instead of a separate editor.
 */
@Composable
fun CompletionButton(
    done: Boolean,
    color: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 30,
    partial: Int? = null,
) {
    val c = LocalTf.current
    val reduce = LocalReduceMotion.current
    var ripple by remember { mutableStateOf(0) }

    val fill by animateFloatAsState(
        if (done) 1f else 0f,
        if (reduce) tween(0) else spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "fill",
    )
    val check by animateFloatAsState(
        if (done) 1f else 0f,
        if (reduce) tween(0) else tween(300, delayMillis = 60, easing = EaseBack),
        label = "check",
    )

    Box(
        modifier
            .size(size.dp)
            .then(pressScale({ if (!done) ripple++; onToggle() }, 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        if (ripple > 0 && !reduce) key(ripple) { RippleRing(color, size) }
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = fill))
                .border(
                    if (fill > 0.9f) 0.dp else 2.dp,
                    if (partial != null && partial > 0) color else c.fg3,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                check > 0.02f -> Box(Modifier.scale(check)) {
                    TfIcon("check", size = (size * 0.56f).toInt(), color = onAccent(color))
                }
                partial != null && partial > 0 -> TfText(
                    "$partial", color = color, size = size * 0.34f,
                    weight = FontWeight.Bold, family = Fonts.mono,
                )
            }
        }
    }
}

@Composable
private fun RippleRing(color: Color, size: Int) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(600)) }
    Box(
        Modifier
            .size(size.dp)
            .scale(0.4f + progress.value * 2f)
            .alpha((1f - progress.value) * 0.55f)
            .border(2.dp, color, CircleShape),
    )
}

/**
 * Confetti burst. Re-fires whenever [token] changes, which is how the caller says
 * "a habit was just completed" without owning any animation state.
 */
@Composable
fun Burst(token: Int, color: Color, count: Int = 15) {
    val c = LocalTf.current
    val reduce = LocalReduceMotion.current
    if (reduce || token == 0) return

    val palette = listOf(color, c.gold, c.green, c.purple, c.rose)
    val particles = remember(token) {
        List(count) { i ->
            val angle = (2 * PI * i / count) + (Random.nextDouble() - 0.5)
            val distance = 26f + Random.nextFloat() * 40f
            BurstParticle(
                dx = (cos(angle) * distance).toFloat(),
                dy = (sin(angle) * distance).toFloat() - 8f,
                color = palette[i % palette.size],
                size = 4f + Random.nextFloat() * 5f,
                round = Random.nextBoolean(),
            )
        }
    }
    val progress = remember(token) { Animatable(0f) }
    LaunchedEffect(token) { progress.animateTo(1f, tween(740, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f))) }
    if (progress.value >= 1f) return

    Box(Modifier.fillMaxSize().zIndex(5f), contentAlignment = Alignment.Center) {
        val t = progress.value
        particles.forEach { p ->
            Box(
                Modifier
                    .offset { IntOffset((p.dx * t).dp.roundToPx(), (p.dy * t).dp.roundToPx()) }
                    .size(p.size.dp)
                    .alpha(1f - t)
                    .clip(if (p.round) CircleShape else RoundedCornerShape(1.5.dp))
                    .background(p.color),
            )
        }
    }
}

private data class BurstParticle(
    val dx: Float, val dy: Float, val color: Color, val size: Float, val round: Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
// Charts
// ─────────────────────────────────────────────────────────────────────────────

/**
 * GitHub-style contribution grid. [levels] runs oldest → newest, seven rows per column.
 *
 * A level of -1 means "in the future": drawn as an outline, never as a miss. Without that
 * distinction the second half of the current week reads as five failures.
 */
@Composable
fun Heatmap(
    levels: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    cell: Int = 12,
    gap: Int = 3,
    onCell: ((index: Int, level: Int) -> Unit)? = null,
) {
    val c = LocalTf.current
    val track = heatTrack()
    val alphas = listOf(0.10f, 0.34f, 0.55f, 0.78f, 1f)
    val weeks = (levels.size + 6) / 7

    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        repeat(weeks) { w ->
            Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                repeat(7) { d ->
                    val index = w * 7 + d
                    if (index >= levels.size) {
                        Spacer(Modifier.size(cell.dp))
                        return@repeat
                    }
                    val level = levels[index]
                    val base = Modifier
                        .size(cell.dp)
                        .clip(RoundedCornerShape(max(3, (cell * 0.24f).toInt()).dp))
                    Box(
                        when {
                            level < 0 -> base.border(
                                1.dp, c.line, RoundedCornerShape(max(3, (cell * 0.24f).toInt()).dp),
                            )
                            level == 0 -> base.background(track)
                            else -> base.background(color.copy(alpha = alphas[level.coerceIn(0, 4)]))
                        }.then(
                            if (onCell != null && level >= 0) pressScale({ onCell(index, level) }, 0.85f)
                            else Modifier
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun HeatLegend(color: Color, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    val track = heatTrack()
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TfText(tr("habits.heatmap.less"), color = c.fg3, size = 10f, family = Fonts.mono)
        listOf(0.10f, 0.34f, 0.55f, 0.78f, 1f).forEachIndexed { i, a ->
            Box(
                Modifier.size(11.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i == 0) track else color.copy(alpha = a))
            )
        }
        TfText(tr("habits.heatmap.more"), color = c.fg3, size = 10f, family = Fonts.mono)
    }
}

/** Weekly / monthly bars. [values] are 0..1. */
@Composable
fun HabitBars(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    height: Int = 70,
    highlightLast: Boolean = false,
) {
    val c = LocalTf.current
    val reduce = LocalReduceMotion.current
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(height.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                val on = highlightLast && i == values.lastIndex
                val grown by animateFloatAsState(
                    v.coerceIn(0f, 1f),
                    if (reduce) tween(0) else tween(600, easing = EaseOutExpo),
                    label = "bar$i",
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(max(0.05f, grown))
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (on) color else color.copy(alpha = 0.4f))
                    )
                }
            }
        }
        if (labels != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                labels.forEachIndexed { i, l ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TfText(
                            l,
                            color = if (highlightLast && i == labels.lastIndex) color else c.fg3,
                            size = 9.5f, family = Fonts.mono,
                        )
                    }
                }
            }
        }
    }
}

/** Smooth trend line with an area fill. [values] are 0..1. */
@Composable
fun TrendLine(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    height: Int = 90,
) {
    val c = LocalTf.current
    if (values.size < 2) return
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val pad = 6.dp.toPx()
            val n = values.size
            val innerW = size.width - pad * 2
            val innerH = size.height - 14.dp.toPx()
            fun x(i: Int) = pad + i * (innerW / (n - 1))
            fun y(v: Float) = innerH - v.coerceIn(0f, 1f) * (innerH - 8.dp.toPx()) + 4.dp.toPx()

            val line = Path().apply {
                moveTo(x(0), y(values[0]))
                for (i in 1 until n) {
                    val x0 = x(i - 1); val y0 = y(values[i - 1])
                    val x1 = x(i); val y1 = y(values[i])
                    val cx = (x0 + x1) / 2f
                    cubicTo(cx, y0, cx, y1, x1, y1)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(n - 1), size.height)
                lineTo(x(0), size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent)))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            for (i in 0 until n) {
                val last = i == n - 1
                if (last) drawCircle(c.card, 6.dp.toPx(), Offset(x(i), y(values[i])))
                drawCircle(color, if (last) 4.dp.toPx() else 2.5.dp.toPx(), Offset(x(i), y(values[i])))
            }
        }
        if (labels != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { TfText(it, color = c.fg3, size = 9f, family = Fonts.mono) }
            }
        }
    }
}

/** Balance radar with the axis labels laid out around it. */
@Composable
fun HabitRadar(dims: List<LabelledRate>, color: Color, modifier: Modifier = Modifier, size: Int = 190) {
    val c = LocalTf.current
    if (dims.isEmpty()) return
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = min(cx, cy) - 26.dp.toPx()
            val n = dims.size
            fun point(i: Int, r: Float): Offset {
                val angle = (-PI / 2 + i * (2 * PI / n)).toFloat()
                return Offset(cx + r * cos(angle), cy + r * sin(angle))
            }
            val grid = Color(150 / 255f, 160 / 255f, 190 / 255f, 0.12f)
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { g ->
                val ring = Path()
                for (i in 0 until n) {
                    val p = point(i, radius * g)
                    if (i == 0) ring.moveTo(p.x, p.y) else ring.lineTo(p.x, p.y)
                }
                ring.close()
                drawPath(ring, grid, style = Stroke(1.dp.toPx()))
            }
            for (i in 0 until n) drawLine(grid, Offset(cx, cy), point(i, radius), 1.dp.toPx())

            val shape = Path()
            for (i in 0 until n) {
                val p = point(i, radius * dims[i].value.coerceIn(0f, 1f))
                if (i == 0) shape.moveTo(p.x, p.y) else shape.lineTo(p.x, p.y)
            }
            shape.close()
            drawPath(
                shape,
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.08f)),
                    center = Offset(cx, cy), radius = radius,
                ),
            )
            drawPath(shape, color, style = Stroke(1.8.dp.toPx()))
            for (i in 0 until n) {
                val p = point(i, radius * dims[i].value.coerceIn(0f, 1f))
                drawCircle(c.card, 5.dp.toPx(), p)
                drawCircle(color, 3.5.dp.toPx(), p)
            }
        }
        // Labels are laid out as real text nodes rather than drawn into the canvas, so they
        // inherit the theme's font and scale with the system text size.
        dims.forEachIndexed { i, d ->
            val angle = (-PI / 2 + i * (2 * PI / dims.size)).toFloat()
            val r = size / 2f - 10f
            val dx = (r * cos(angle)).dp
            val dy = (r * sin(angle)).dp
            Box(Modifier.offset(x = dx, y = dy)) {
                TfText(tr(d.label), color = c.fg2, size = 10f, weight = FontWeight.SemiBold, family = Fonts.mono)
            }
        }
    }
}

/** The small labelled metric card used across Detail and Statistics. */
@Composable
fun HabitStatTile(
    icon: String,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
    big: Boolean = false,
) {
    val c = LocalTf.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .border(0.5.dp, c.sep, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TfIcon(icon, size = 15, color = color)
            TfText(label.uppercase(), color = c.fg3, size = 9.5f, family = Fonts.mono, letterSpacing = 0.5f)
        }
        Row(
            Modifier.padding(top = 9.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TfText(value, color = c.fg, size = if (big) 27f else 23f, weight = FontWeight.Bold, family = Fonts.display)
            if (unit != null) TfText(unit, color = c.fg3, size = 12f, family = Fonts.mono)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overlays
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom sheet.
 *
 * Kept mounted and translated off-screen rather than added/removed, so the slide-out
 * actually plays — a sheet that is removed from composition on close vanishes instantly
 * and every dismissal feels like a crash.
 */
@Composable
fun HabitSheet(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalTf.current
    val reduce = LocalReduceMotion.current
    val offset by animateFloatAsState(
        if (open) 0f else 1f,
        if (reduce) tween(0) else tween(380, easing = EaseOutExpo),
        label = "sheet",
    )
    val scrim by animateFloatAsState(if (open) 1f else 0f, tween(300), label = "scrim")
    if (offset >= 0.999f) return

    Box(modifier.fillMaxSize().zIndex(70f)) {
        Box(
            Modifier.fillMaxSize()
                .background(Color(6 / 255f, 8 / 255f, 13 / 255f, 0.6f * scrim))
                .then(pressScale(onClose, 1f))
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .offset { IntOffset(0, (offset * 900).dp.roundToPx()) }
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(c.bg1)
                .border(
                    0.5.dp, c.line,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(width = 38.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(150 / 255f, 160 / 255f, 185 / 255f, 0.35f))
                )
            }
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 30.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SheetHeader(icon: String, color: Color, title: String, sub: String? = null) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.soft(0.16f)),
            contentAlignment = Alignment.Center,
        ) { TfIcon(icon, size = 20, color = color) }
        Column(Modifier.weight(1f)) {
            TfText(title, color = c.fg, size = 19f, weight = FontWeight.Bold, family = Fonts.display)
            if (sub != null) TfText(sub, color = c.fg3, size = 12.5f, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

data class ContextAction(
    val label: String,
    val icon: String,
    val danger: Boolean = false,
    val onPress: () -> Unit,
)

/** iOS-style long-press menu: blurred-feeling scrim, centred card, scale-in. */
@Composable
fun HabitContextMenu(
    open: Boolean,
    onClose: () -> Unit,
    items: List<ContextAction>,
    header: @Composable (() -> Unit)? = null,
) {
    val c = LocalTf.current
    val reduce = LocalReduceMotion.current
    val progress by animateFloatAsState(
        if (open) 1f else 0f,
        if (reduce) tween(0) else tween(280, easing = EaseBack),
        label = "menu",
    )
    if (progress < 0.01f) return

    Box(Modifier.fillMaxSize().zIndex(80f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                .background(Color(6 / 255f, 8 / 255f, 13 / 255f, 0.55f * progress))
                .then(pressScale(onClose, 1f))
        )
        Column(
            Modifier.width(260.dp).scale(0.9f + 0.1f * progress).alpha(progress),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (header != null) {
                Box(Modifier.padding(bottom = 10.dp)) { header() }
            }
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.card2)
                    .border(0.5.dp, c.line, RoundedCornerShape(16.dp))
            ) {
                items.forEachIndexed { i, item ->
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (i > 0) Modifier.drawTopHairline(c.sep) else Modifier)
                            .then(pressScale({ onClose(); item.onPress() }, 0.98f))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TfText(
                            item.label,
                            color = if (item.danger) c.danger else c.fg,
                            size = 15f, weight = FontWeight.Medium,
                        )
                        TfIcon(item.icon, size = 18, color = if (item.danger) c.danger else c.fg2)
                    }
                }
            }
        }
    }
}

/** What the undo snackbar says, and what undoing it does. */
data class SnackData(
    val token: Int,
    val text: String,
    val icon: String,
    val color: Color,
    val undo: (() -> Unit)? = null,
)

/**
 * Undo snackbar with a draining progress line.
 *
 * The bar drains for exactly as long as the auto-dismiss, so the remaining time is legible
 * rather than a guess — the difference between "I still have a moment" and tapping into a
 * snackbar that has already gone.
 */
@Composable
fun HabitSnackbar(data: SnackData?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    val reduce = LocalReduceMotion.current
    val visible = data != null
    val shift by animateFloatAsState(
        if (visible) 0f else 1f,
        if (reduce) tween(0) else tween(400, easing = EaseOutExpo),
        label = "snack",
    )
    val drain = remember(data?.token) { Animatable(1f) }
    LaunchedEffect(data?.token) {
        if (data == null) return@LaunchedEffect
        drain.snapTo(1f)
        drain.animateTo(0f, tween(3600, easing = LinearEasing))
        onDismiss()
    }
    if (shift >= 0.999f || data == null) return

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .offset { IntOffset(0, (shift * 160).dp.roundToPx()) }
            .clip(RoundedCornerShape(14.dp))
            .background(c.card3)
            .border(0.5.dp, c.line, RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(data.color.soft(0.2f)),
                contentAlignment = Alignment.Center,
            ) { TfIcon(data.icon, size = 15, color = data.color) }
            TfText(data.text, color = c.fg, size = 13.5f, modifier = Modifier.weight(1f), maxLines = 2)
            if (data.undo != null) {
                TfText(
                    tr("habits.action.undo"), color = c.accent, size = 14f, weight = FontWeight.Bold,
                    modifier = Modifier.then(pressScale({ data.undo.invoke(); onDismiss() }, 0.9f)).padding(4.dp),
                )
            }
        }
        Box(
            Modifier.fillMaxWidth(drain.value.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(data.color)
        )
    }
}

/**
 * A row that reveals actions on a horizontal drag and opens a menu on long press.
 *
 * The drag threshold is checked against the vertical delta too: without that, a swipe row
 * inside a scrolling list steals every scroll that starts on a card.
 */
@Composable
fun SwipeRow(
    actions: List<SwipeAction>,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Int = 18,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { (actions.size * 76).dp.toPx() }
    var offsetPx by remember { mutableStateOf(0f) }
    var opened by remember { mutableStateOf(false) }
    val settled by animateFloatAsState(offsetPx, tween(240, easing = EaseOutExpo), label = "swipe")

    Box(modifier.clip(RoundedCornerShape(radius.dp))) {
        if (actions.isNotEmpty()) {
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                actions.forEach { a ->
                    Column(
                        Modifier.width(76.dp).fillMaxHeight()
                            .background(a.background)
                            .then(pressScale({ offsetPx = 0f; opened = false; a.onPress() }, 0.96f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        TfIcon(a.icon, size = 19, color = Color.White)
                        TfText(a.label, color = Color.White, size = 11f, weight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(settled.roundToInt(), 0) }
                .pointerInput(actions.size) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .pointerInput(actions.size) {
                    detectDragGestures(
                        onDragEnd = {
                            opened = offsetPx < -revealPx * 0.5f
                            offsetPx = if (opened) -revealPx else 0f
                        },
                        onDragCancel = { offsetPx = if (opened) -revealPx else 0f },
                    ) { change, drag ->
                        if (abs(drag.x) <= abs(drag.y)) return@detectDragGestures
                        change.consume()
                        offsetPx = (offsetPx + drag.x).coerceIn(-revealPx - 20f, 20f)
                    }
                },
        ) { content() }
    }
}

data class SwipeAction(
    val label: String,
    val icon: String,
    val background: Color,
    val onPress: () -> Unit,
)

/** Pill switch. Foundation has no Switch, and Material is not a dependency here. */
@Composable
fun TfSwitch(checked: Boolean, color: Color, onChange: (Boolean) -> Unit) {
    val c = LocalTf.current
    val shift by animateFloatAsState(if (checked) 1f else 0f, tween(200, easing = EaseOutExpo), label = "switch")
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) color else c.card3)
            .then(pressScale({ onChange(!checked) }, 0.94f)),
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = (shift * 18).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Full-width filled action button — the primary CTA on sheets and Create. */
@Composable
fun HabitButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: String? = null,
) {
    val c = LocalTf.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) color else c.card2)
            .then(if (enabled) pressScale(onClick, 0.98f) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TfIcon(icon, size = 19, color = if (enabled) onAccent(color) else c.fg3)
            Spacer(Modifier.width(9.dp))
        }
        TfText(
            label,
            color = if (enabled) onAccent(color) else c.fg3,
            size = 15f, weight = FontWeight.Bold,
        )
    }
}

/** Achievement badge tile. [shine] sweeps across an unlocked badge. */
@Composable
fun AchievementCard(view: HabitAchievementView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    val tint = c.named(view.achievement.color)
    val locked = !view.unlocked
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (locked) Brush.verticalGradient(listOf(c.card, c.card))
                else Brush.linearGradient(listOf(tint.soft(0.2f), c.card))
            )
            .border(0.5.dp, if (locked) c.sep else tint.soft(0.5f), RoundedCornerShape(16.dp))
            .then(pressScale(onClick, 0.97f))
            .padding(15.dp)
            .heightIn(min = 150.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(15.dp))
                .background(
                    if (locked) Brush.verticalGradient(listOf(c.fg4, c.fg4))
                    else Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.5f)))
                ),
            contentAlignment = Alignment.Center,
        ) {
            TfIcon(if (locked) "lock" else view.achievement.icon, size = 26, color = if (locked) c.fg3 else Color.White)
        }
        Column(Modifier.padding(top = 14.dp)) {
            TfText(
                tr(view.achievement.nameKey),
                color = if (locked) c.fg2 else c.fg,
                size = 15.5f, weight = FontWeight.Bold, family = Fonts.display,
            )
            TfText(
                tr(view.achievement.subKey), color = c.fg3, size = 11.5f, lineHeight = 1.4f,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (locked) {
                Row(
                    Modifier.padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProgressBar(view.progress, c.fg3, height = 4, modifier = Modifier.weight(1f))
                    TfText("${(view.progress * 100).roundToInt()}%", color = c.fg3, size = 10f, family = Fonts.mono)
                }
            } else {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    TfIcon("check", size = 11, color = tint)
                    TfText(tr("habits.achievement.unlocked"), color = tint, size = 10f, family = Fonts.mono)
                }
            }
        }
    }
}

/** Section overline used inside the habits screens, with an optional trailing action. */
@Composable
fun HabitSection(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = LocalTf.current
    Row(
        modifier.fillMaxWidth().padding(top = 22.dp, bottom = 11.dp, start = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        TfText(
            title.uppercase(), color = c.fg3, size = 10.5f,
            weight = FontWeight.SemiBold, family = Fonts.mono, letterSpacing = 0.9f,
        )
        if (action != null) {
            TfText(action, color = c.accent, size = 13.5f, modifier = Modifier.then(pressScale(onAction, 0.9f)))
        }
    }
}

/** Card matching the module's surface treatment, without the screens/ package's insets. */
@Composable
fun HabitCardSurface(
    modifier: Modifier = Modifier,
    pad: Int = 16,
    radius: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalTf.current
    Column(
        modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(Brush.verticalGradient(listOf(c.card2, c.card)))
            .border(0.5.dp, c.hair, RoundedCornerShape(radius.dp))
            .padding(pad.dp),
        content = content,
    )
}

/** Soft radial bloom behind a hero. Sized to the caller rather than the fixed AmbientGlow. */
@Composable
fun HeroGlow(color: Color, modifier: Modifier = Modifier, opacity: Float = 0.22f) {
    Box(
        modifier.drawBehind {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = opacity), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension / 1.4f,
                ),
                radius = size.minDimension / 1.4f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    )
}

/** Reports its own height so a parent can size a glow to it without a fixed constant. */
@Composable
fun MeasuredBox(modifier: Modifier = Modifier, content: @Composable BoxScope.(heightDp: Int) -> Unit) {
    var height by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { height = with(density) { it.height.toDp().value.toInt() } }) {
        content(height)
    }
}

/**
 * Vertical drag reported in dp, for the reorder handle.
 *
 * Its own modifier rather than a raw `pointerInput` at the call site because the handle
 * needs the delta in dp — the caller divides by a fixed row height to get "how many rows
 * has this moved", and doing that in pixels would behave differently on every screen
 * density.
 */
fun Modifier.verticalDrag(
    onStart: () -> Unit,
    onDelta: (dyDp: Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = composed {
    val density = LocalDensity.current
    pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { onStart() },
            onDragEnd = onEnd,
            onDragCancel = onEnd,
        ) { change, drag ->
            change.consume()
            onDelta(with(density) { drag.y.toDp().value })
        }
    }
}
