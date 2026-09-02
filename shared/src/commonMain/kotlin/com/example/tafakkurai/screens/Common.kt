package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.ui.*

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
fun PrivacyFooter(text: String) {
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
