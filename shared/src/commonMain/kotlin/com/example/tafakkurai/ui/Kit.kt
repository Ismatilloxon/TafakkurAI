package com.example.tafakkurai.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.i18n.tr

// ── Text ─────────────────────────────────────────────────────────────────────
@Composable
fun TfText(
    text: String,
    color: Color,
    size: Float = 14f,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Fonts.sans,
    lineHeight: Float = 0f,
    letterSpacing: Float = 0f,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = family,
            lineHeight = if (lineHeight > 0f) (size * lineHeight).sp else TextUnit.Unspecified,
            letterSpacing = letterSpacing.sp,
            textAlign = align,
        ),
    )
}

// ── Press feedback (button:active { scale .94 } in the prototype) ─────────────
@Composable
fun pressScale(onClick: () -> Unit, scaleTo: Float = 0.94f): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) scaleTo else 1f, tween(160))
    return Modifier
        .scale(scale)
        .clickableNoRipple(interaction, onClick)
}

@Composable
private fun Modifier.clickableNoRipple(interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )

// ── Card ─────────────────────────────────────────────────────────────────────
@Composable
fun Card(
    modifier: Modifier = Modifier,
    pad: Int = 16,
    radius: Int = 16,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalTf.current
    val base = Modifier
        .clip(RoundedCornerShape(radius.dp))
        .background(Brush.verticalGradient(listOf(c.card2, c.card)))
        .border(0.5.dp, c.hair, RoundedCornerShape(radius.dp))
    Column(
        modifier = (if (onClick != null) modifier.then(pressScale(onClick, 0.985f)) else modifier)
            .then(base)
            .padding(pad.dp),
        content = content,
    )
}

// ── Section title ─────────────────────────────────────────────────────────────
@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}, serif: Boolean = false) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        TfText(
            title,
            color = c.fg,
            size = if (serif) 27f else 21f,
            weight = if (serif) FontWeight.Normal else FontWeight.Bold,
            family = if (serif) Fonts.serif else Fonts.display,
            letterSpacing = 0.3f,
        )
        if (action != null) {
            TfText(action, color = c.accent, size = 15f, modifier = pressScale(onAction, 0.9f))
        }
    }
}

@Composable
fun Overline(text: String, color: Color? = null, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    TfText(text.uppercase(), color = color ?: c.fg3, size = 10f, weight = FontWeight.SemiBold, family = Fonts.mono, letterSpacing = 0.8f, modifier = modifier)
}

// ── Progress bar ──────────────────────────────────────────────────────────────
@Composable
fun ProgressBar(value: Float, color: Color, height: Int = 5, modifier: Modifier = Modifier, track: Color? = null) {
    val c = LocalTf.current
    val boundedValue = value.coerceIn(0f, 1f)
    Box(
        modifier
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(boundedValue, 0f..1f) }
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp))
            .background(track ?: Color(120 / 255f, 130 / 255f, 150 / 255f, 0.22f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(boundedValue)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height.dp))
                .background(color)
        )
    }
}

// ── Pill ──────────────────────────────────────────────────────────────────────
@Composable
fun Pill(text: String, color: Color, soft: Color, modifier: Modifier = Modifier, leading: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(soft)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        leading?.invoke()
        TfText(text.uppercase(), color = color, size = 10.5f, weight = FontWeight.SemiBold, family = Fonts.mono, letterSpacing = 0.3f)
    }
}

// ── Icon badge ────────────────────────────────────────────────────────────────
@Composable
fun IconBadge(name: String, color: Color, size: Int = 40, iconSize: Int = 20, soft: Color? = null) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(soft ?: color.soft()),
        contentAlignment = Alignment.Center,
    ) {
        TfIcon(name, size = iconSize, color = color)
    }
}

// ── Segmented control ─────────────────────────────────────────────────────────
@Composable
fun Segmented(tabs: List<Pair<String, String>>, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    Row(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(120 / 255f, 130 / 255f, 150 / 255f, 0.16f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        tabs.forEach { (k, l) ->
            val on = value == k
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) Color(0xFF3A4150) else Color.Transparent)
                    .then(pressScale({ onChange(k) }, 0.97f))
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                TfText(l, color = if (on) c.fg else c.fg2, size = 13f, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

// ── Proactive AI card ─────────────────────────────────────────────────────────
@Composable
fun ProactiveCard(text: String, action: String? = null, onAction: () -> Unit = {}, compact: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    val proactivity = LocalProactivity.current
    if (proactivity == "ambient" && compact) return
    val accent = c.accent
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accent.soft(0.16f), c.purple.soft(0.05f))))
            .border(0.5.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, c.purple))),
            contentAlignment = Alignment.Center,
        ) { TfIcon("sparkles", size = 16, color = Color.White) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            TfText(text, color = c.fg, size = 13.5f, lineHeight = 1.5f)
            if (action != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent)
                        .then(pressScale(onAction))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    TfText(action, color = Color.White, size = 12.5f, weight = FontWeight.SemiBold)
                    TfIcon("arrowRight", size = 12, color = Color.White)
                }
            }
        }
    }
}

// ── Screen scaffolding ────────────────────────────────────────────────────────
@Composable
fun LargeTitle(title: String, subtitle: String? = null, serif: Boolean = false, right: @Composable (() -> Unit)? = null) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            if (subtitle != null) {
                TfText(subtitle, color = c.fg2, size = 14f, modifier = Modifier.padding(bottom = 3.dp))
            }
            TfText(
                title, color = c.fg,
                size = if (serif) 40f else 33f,
                weight = if (serif) FontWeight.Normal else FontWeight.Bold,
                family = if (serif) Fonts.serif else Fonts.display,
                letterSpacing = if (serif) 0f else 0.36f,
                lineHeight = 1.05f,
            )
        }
        right?.invoke()
    }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, right: @Composable (() -> Unit)? = null) {
    val c = LocalTf.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.glass)
            .drawBottomHairline(c.sep)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.then(pressScale(onBack, 0.9f)).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TfIcon("chevronLeft", size = 20, color = c.accent)
            TfText(tr("common.back"), color = c.accent, size = 16f)
        }
        TfText(title, color = c.fg, size = 16f, weight = FontWeight.SemiBold)
        Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.CenterEnd) { right?.invoke() }
    }
}
