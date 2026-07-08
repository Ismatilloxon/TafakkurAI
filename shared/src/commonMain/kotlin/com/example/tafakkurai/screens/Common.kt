package com.example.tafakkurai.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Shared screen scaffolding for the screens/ package. App.kt hosts each screen
// inside a status-bar-inset Box; tab roots scroll the whole screen, detail
// screens pin a BackBar above a scrolling body.
// ─────────────────────────────────────────────────────────────────────────────

/** Standard horizontal content inset for cards/sections inside a screen body. */
val HPAD = 16

/** Scrolling region that fills the height left under a header row (tab-root screens). */
@Composable
fun ColumnScope.ScrollScreen(bottomPad: Int = 116, gap: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(gap.dp),
        content = content,
    )
}

/** Scrolling body that fills the height left under a pinned BackBar. */
@Composable
fun ColumnScope.ScrollBody(bottomPad: Int = 116, gap: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(top = 14.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(gap.dp),
        content = content,
    )
}

/** A card already inset to the standard horizontal padding. */
@Composable
fun InsetCard(pad: Int = 16, radius: Int = 16, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp), pad = pad, radius = radius, onClick = onClick, content = content)
}

/** Colored dot + mono overline — the per-aspect block heading on Today. */
@Composable
fun AspectHeading(label: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Overline(label)
    }
}

/** A stat tile for a Row of equal-width metrics. */
@Composable
fun RowScope.StatTile(value: String, label: String, color: Color? = null) {
    val c = LocalTf.current
    Card(Modifier.weight(1f), pad = 14) {
        TfText(value, color = color ?: c.fg, size = 22f, weight = FontWeight.Bold, family = Fonts.mono)
        TfText(label, color = c.fg2, size = 12f, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Privacy footer reused on private/on-device screens. */
@Composable
fun PrivacyFooter(text: String = "Private · processed on your device. Nothing leaves your phone.") {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TfIcon("lock", size = 13, color = c.fg3)
        TfText(text, color = c.fg3, size = 12f, lineHeight = 1.4f)
    }
}

// ── Salah day-clock ring (Today hero) ────────────────────────────────────────
@Composable
fun SalahRing(size: Int = 268) {
    val c = LocalTf.current
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f + 14.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val rad = (this.size.minDimension - inset * 2) / 2f
            fun deg(min: Int) = (min / 1440f) * 360f - 90f
            fun pt(min: Int): Offset {
                val a = deg(min) * (3.14159265f / 180f)
                return Offset(cx + rad * cos(a), cy + rad * sin(a))
            }
            // base night track (full circle)
            drawArc(c.line, 0f, 360f, false, tl, arcSize, style = Stroke(stroke))
            // daytime arc Fajr → Maghrib
            val fajr = 275; val maghrib = 1242
            drawArc(
                c.gold.copy(alpha = 0.55f), deg(fajr), (maghrib - fajr) / 1440f * 360f, false,
                tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // prayer markers
            TPRAYERS.forEach { p ->
                val o = pt(p.min)
                drawCircle(c.bg1, 7.dp.toPx(), o)
                drawCircle(if (p.key == "sunrise") c.gold else c.accent, 4.dp.toPx(), o)
            }
            // now marker
            val now = pt(TNOW_MIN)
            drawCircle(c.green, 6.dp.toPx(), now)
            drawCircle(c.bg1, 2.5.dp.toPx(), now)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Overline("NEXT · ${TNEXT.name.uppercase()}")
            TfText(TNEXT.at, color = c.fg, size = 38f, weight = FontWeight.Bold, family = Fonts.mono, letterSpacing = 1f)
            Pill("in ${TNEXT.inLabel}", c.accent, c.accent.soft(0.16f))
        }
    }
}

// ── Stub screen for sub-modules without a full implementation yet ─────────────
data class StubInfo(val title: String, val native: String, val icon: String, val color: String, val context: String, val tie: String)

val STUB_INFO: Map<String, StubInfo> = mapOf(
    "quizzes" to StubInfo(
        "Quizzes", "Testlar", "checkSquare", "purple",
        "Spaced-repetition quizzes drawn from the lessons you've finished — short, daily, and adaptive.",
        "Murabbiy will resurface a concept right before you'd naturally forget it.",
    ),
    "sadaqah" to StubInfo(
        "Sadaqah", "Sadaqa", "heart", "rose",
        "A quiet place to set giving intentions and track them — for yourself, not for show.",
        "If you'd like, I'll offer a gentle reminder on Fridays.",
    ),
    "settings" to StubInfo(
        "Settings & privacy", "Sozlamalar", "settings", "teal",
        "On-device AI controls, optional iCloud sync (off by default), data export, prayer reminders, calculation method, and language.",
        "Everything stays on your phone unless you choose to turn on sync.",
    ),
    "mentors" to StubInfo(
        "Mentors", "Murabbiylar", "users", "green",
        "Matched with people a few steps ahead of you — privately, and entirely on your terms.",
        "I can suggest a mentor whose path matches your backend goal.",
    ),
)

@Composable
fun StubScreen(info: StubInfo, onBack: () -> Unit) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        BackBar(info.title, onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            TopGlow(c.named(info.color))
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    Modifier.size(98.dp).clip(RoundedCornerShape(28.dp))
                        .background(Brush.verticalGradient(listOf(c.named(info.color).soft(0.22f), c.named(info.color).soft(0.08f)))),
                    contentAlignment = Alignment.Center,
                ) { TfIcon(info.icon, size = 44, color = c.named(info.color)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TfText(info.title, color = c.fg, size = 26f, weight = FontWeight.Bold, family = Fonts.display)
                    Overline(info.native)
                }
                TfText(info.context, color = c.fg2, size = 15f, lineHeight = 1.55f, align = TextAlign.Center)
                ProactiveCard(info.tie, action = "Set up", modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp))
            }
        }
    }
}
