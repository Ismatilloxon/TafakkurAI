package com.example.tafakkurai.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  Tafakkur onboarding — shared art + primitives.
//
//  Self-contained on purpose: everything here is drawn from the verified design
//  system (TfText / TfIcon / pressScale / LocalTf / Fonts / soft) plus Compose
//  Canvas. No dependency on inferred component signatures, so the module compiles
//  against only what is known-good.
// ─────────────────────────────────────────────────────────────────────────────

/** Accent-token → palette color for a slide. Mirrors §1 of the spec. */
fun accentOf(c: TfColors, key: String): Color = when (key) {
    "gold" -> c.gold
    "green" -> c.green
    "purple" -> c.purple
    "cyan" -> c.cyan
    "amber" -> c.warn
    else -> c.teal // "teal"
}

/** Auto-contrast label color for a filled CTA (dark text on light accents). */
fun onAccent(accent: Color): Color =
    if (accent.luminance() > 0.45f) Color(0xFF0B0E16) else Color.White

// ── Ambient glow ──────────────────────────────────────────────────────────────
/** Soft radial glow behind a hero, in the slide's accent color. */
@Composable
fun GlowBehind(color: Color, size: Int = 320, opacity: Float = 0.22f) {
    Canvas(Modifier.size(size.dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = opacity), Color.Transparent),
                center = center,
                radius = this.size.minDimension / 2f,
            ),
            radius = this.size.minDimension / 2f,
            center = center,
        )
    }
}

// ── Breathing orb ───────────────────────────────────────────────────────────
/**
 * Three concentric pulsing rings + a gradient core (accent→purple) with a
 * sparkles glyph. ~4.8s ease-in-out loop (2.4s each way, reversed).
 */
@Composable
fun BreathingOrb(size: Int, accent: Color, purple: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            // 3 expanding rings, fading as they grow
            for (i in 0..2) {
                val phase = ((p + i / 3f) % 1f)
                val ringR = r * (0.5f + phase * 0.5f)
                drawCircle(
                    color = accent.copy(alpha = (1f - phase) * 0.28f),
                    radius = ringR,
                    center = center,
                    style = Stroke(width = 1.5f),
                )
            }
            // gradient core, gently pulsing
            val coreR = r * (0.44f + p * 0.06f)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(accent, purple),
                    start = Offset(center.x - coreR, center.y - coreR),
                    end = Offset(center.x + coreR, center.y + coreR),
                ),
                radius = coreR,
                center = center,
            )
        }
        TfIcon("sparkles", size = (size * 0.3f).toInt().coerceAtLeast(14), color = Color.White)
    }
}

// ── Page dots (active dot elongates to a pill) ────────────────────────────────
@Composable
fun PageDots(count: Int, index: Int, accent: Color) {
    val c = LocalTf.current
    val description = tr("onb.page_indicator_a11y", index + 1, count)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == index
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (on) 22.dp else 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) accent else c.fg4),
            )
        }
    }
}

// ── Full-width CTA (accent fill, auto-contrast label, press 0.94) ─────────────
@Composable
fun OnbCta(label: String, accent: Color, arrow: Boolean = true, onClick: () -> Unit) {
    val fg = onAccent(accent)
    Row(
        Modifier
            .fillMaxWidth()
            .then(pressScale(onClick, 0.94f))
            .clip(RoundedCornerShape(15.dp))
            .background(accent)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TfText(label, color = fg, size = 16f, weight = FontWeight.SemiBold)
        if (arrow) {
            Spacer(Modifier.width(7.dp))
            TfIcon("arrowRight", size = 17, color = fg)
        }
    }
}

/** Top-right "Oʻtkazish" (Skip). */
@Composable
fun SkipButton(onClick: () -> Unit) {
    val c = LocalTf.current
    Box(Modifier.then(pressScale(onClick, 0.92f)).padding(8.dp)) {
        TfText(tr("onb.skip"), color = c.fg3, size = 14f, weight = FontWeight.Medium)
    }
}

/** Rounded chip in the slide accent (accent @16% fill + soft accent border). */
@Composable
fun PillChip(text: String, accent: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.soft(0.14f))
            .border(0.5.dp, accent.soft(0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TfText(text, color = accent, size = 12f, weight = FontWeight.Medium)
    }
}

// ── Sub-feature tile ─────────────────────────────────────────────────────────
data class AspectTile(val icon: String, val titleKey: String, val subtitleKey: String)

@Composable
fun AspectTileView(tile: AspectTile, accent: Color, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.verticalGradient(listOf(c.card2, c.card)))
            .border(0.5.dp, accent.soft(0.28f), RoundedCornerShape(15.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(accent.soft(0.16f)),
            contentAlignment = Alignment.Center,
        ) { TfIcon(tile.icon, size = 19, color = accent) }
        TfText(tr(tile.titleKey), color = c.fg, size = 13f, weight = FontWeight.SemiBold)
        TfText(tr(tile.subtitleKey).uppercase(), color = c.fg3, size = 9.5f, family = Fonts.mono, letterSpacing = 0.4f)
    }
}

/** Tiles laid out in [columns]-wide rows. */
@Composable
fun AspectTileGrid(tiles: List<AspectTile>, accent: Color, columns: Int = 2) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t -> AspectTileView(t, accent, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ── Balance radar (pentagon) ─────────────────────────────────────────────────
data class RadarAxis(val label: String, val value: Float, val color: Color)

@Composable
fun BalanceRadar(axes: List<RadarAxis>, fill: Color, size: Int = 220) {
    val c = LocalTf.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = c.fg3, fontSize = 10.sp, fontFamily = Fonts.mono, fontWeight = FontWeight.SemiBold)
    val description = tr("onb.balance_chart_a11y")
    Canvas(Modifier.size(size.dp).semantics { contentDescription = description }) {
        val n = axes.size
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val rMax = min(cx, cy) * 0.72f
        fun pt(i: Int, radius: Float): Offset {
            val a = (-PI / 2 + 2 * PI * i / n).toFloat()
            return Offset(cx + radius * cos(a), cy + radius * sin(a))
        }
        // grid rings
        for (ring in 1..3) {
            val rr = rMax * ring / 3f
            val grid = Path()
            for (i in 0 until n) {
                val o = pt(i, rr)
                if (i == 0) grid.moveTo(o.x, o.y) else grid.lineTo(o.x, o.y)
            }
            grid.close()
            drawPath(grid, color = c.sep, style = Stroke(width = 1f))
        }
        // spokes
        for (i in 0 until n) drawLine(c.sep, start = Offset(cx, cy), end = pt(i, rMax), strokeWidth = 1f)
        // value polygon
        val poly = Path()
        for (i in 0 until n) {
            val o = pt(i, rMax * axes[i].value.coerceIn(0f, 1f))
            if (i == 0) poly.moveTo(o.x, o.y) else poly.lineTo(o.x, o.y)
        }
        poly.close()
        drawPath(poly, color = fill.copy(alpha = 0.22f))
        drawPath(poly, color = fill, style = Stroke(width = 2f))
        // vertex dots + labels
        for (i in 0 until n) {
            val v = pt(i, rMax * axes[i].value.coerceIn(0f, 1f))
            drawCircle(axes[i].color, radius = 3.5f, center = v)
            val lp = pt(i, rMax + 14f)
            val layout = measurer.measure(AnnotatedString(axes[i].label), labelStyle)
            drawText(layout, topLeft = Offset(lp.x - layout.size.width / 2f, lp.y - layout.size.height / 2f))
        }
    }
}

// ── Prayer day-ring (24h) + times strip ──────────────────────────────────────
data class PrayerTime(val name: String, val time: String, val minutes: Int, val color: Color)

/**
 * A 24h ring: light daytime arc (Fajr→Maghrib), accent night arc, five prayer
 * dots and a "now" marker. Center shows NEXT · <name> and the countdown.
 */
@Composable
fun PrayerDayRing(
    prayers: List<PrayerTime>,
    fajrMin: Int,
    maghribMin: Int,
    nowMin: Int,
    nextName: String,
    countdown: String,
    accent: Color,
    size: Int = 210,
) {
    val c = LocalTf.current
    val nextLabel = tr("onb.prayer.next", nextName.uppercase())
    val description = tr("onb.prayer_ring_a11y", nextName, countdown)
    fun sweep(fromMin: Int, toMin: Int): Pair<Float, Float> {
        val start = -90f + fromMin / 1440f * 360f
        val span = ((toMin - fromMin + 1440) % 1440) / 1440f * 360f
        return start to span
    }
    Box(
        Modifier.size(size.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8f
            val inset = stroke / 2f + 2f
            val topLeft = Offset(inset, inset)
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
            // night ring (full), then day arc on top
            drawArc(accent.copy(alpha = 0.5f), -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val (ds, dspan) = sweep(fajrMin, maghribMin)
            drawArc(c.gold.copy(alpha = 0.85f), ds, dspan, false, topLeft, arcSize, style = Stroke(stroke))
            // prayer dots
            val r = this.size.minDimension / 2f - inset
            fun dot(min: Int, color: Color, rad: Float) {
                val a = (-PI / 2 + 2 * PI * (min / 1440f)).toFloat()
                drawCircle(color, radius = rad, center = Offset(center.x + r * cos(a), center.y + r * sin(a)))
            }
            prayers.forEach { dot(it.minutes, it.color, 4.5f) }
            // now marker
            val na = (-PI / 2 + 2 * PI * (nowMin / 1440f)).toFloat()
            val nowC = Offset(center.x + r * cos(na), center.y + r * sin(na))
            drawCircle(c.fg, radius = 5.5f, center = nowC)
            drawCircle(c.bg1, radius = 2.5f, center = nowC)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TfText(nextLabel, color = c.fg3, size = 10f, family = Fonts.mono, letterSpacing = 0.8f)
            TfText(countdown, color = c.fg, size = 26f, weight = FontWeight.Bold, family = Fonts.display)
        }
    }
}

@Composable
fun PrayerTimesStrip(prayers: List<PrayerTime>, nextIndex: Int) {
    val c = LocalTf.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        prayers.forEachIndexed { i, p ->
            val next = i == nextIndex
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (next) c.gold.soft(0.16f) else Color.Transparent)
                    .border(0.5.dp, if (next) c.gold.soft(0.4f) else Color.Transparent, RoundedCornerShape(11.dp))
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                TfText(p.name, color = if (next) c.gold else c.fg2, size = 10.5f, weight = FontWeight.Medium, maxLines = 1)
                TfText(p.time, color = if (next) c.gold else c.fg3, size = 11.5f, family = Fonts.mono, weight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
