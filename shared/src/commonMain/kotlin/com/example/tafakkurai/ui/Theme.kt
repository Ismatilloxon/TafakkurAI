package com.example.tafakkurai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Tafakkur "Fajr" design system — ported from kit.jsx.
 * Seven switchable palettes (5 dark, 2 light). The active palette's [TfColors.teal]
 * is the accent. Light themes (noor/sahifa) carry the "printed mushaf / cool paper" feel.
 */
@Immutable
data class TfColors(
    val fg: Color,
    val fg2: Color,
    val fg3: Color,
    val fg4: Color,
    val bg0: Color,
    val bg1: Color,
    val card: Color,
    val card2: Color,
    val card3: Color,
    val sep: Color,
    val line: Color,
    val hair: Color,
    val glass: Color,
    val green: Color,
    val gold: Color,
    val cyan: Color,
    val purple: Color,
    val rose: Color,
    val warn: Color,
    val danger: Color,
    val teal: Color,
    val isLight: Boolean,
) {
    /** Accent == teal in the prototype. */
    val accent: Color get() = teal

    /** Map a named color token to this palette (named() in kit.jsx). */
    fun named(name: String): Color = when (name) {
        "gold" -> gold
        "green" -> green
        "cyan" -> cyan
        "purple" -> purple
        "rose" -> rose
        else -> teal // teal / blue / default
    }
}

/** softOf(hex, a) — opaque color with applied alpha. */
fun Color.soft(alpha: Float = 0.16f): Color = copy(alpha = alpha)

private val dkHair = Color(1f, 1f, 1f, 0.07f)
private val ltHair = Color(60 / 255f, 50 / 255f, 35 / 255f, 0.10f)

val TfThemes: Map<String, TfColors> = mapOf(
    "sukut" to TfColors(
        fg = Color(0xFFF4F6FB), fg2 = Color(225 / 255f, 230 / 255f, 245 / 255f, 0.64f),
        fg3 = Color(225 / 255f, 230 / 255f, 245 / 255f, 0.36f), fg4 = Color(225 / 255f, 230 / 255f, 245 / 255f, 0.18f),
        bg0 = Color(0xFF07090F), bg1 = Color(0xFF0B0E16), card = Color(0xFF141826), card2 = Color(0xFF1A2032), card3 = Color(0xFF232A3D),
        sep = Color(150 / 255f, 160 / 255f, 200 / 255f, 0.16f), line = Color(0xFF2A3146), hair = dkHair,
        glass = Color(11 / 255f, 14 / 255f, 22 / 255f, 0.8f),
        green = Color(0xFF9FD0B0), gold = Color(0xFFE6CCA0), cyan = Color(0xFF8AA0E8), purple = Color(0xFFB98AE0),
        rose = Color(0xFFE08AA8), warn = Color(0xFFE0B98A), danger = Color(0xFFE5604D), teal = Color(0xFF8AA0E8), isLight = false,
    ),
    "fajr" to TfColors(
        fg = Color(0xFFFBFAF7), fg2 = Color(243 / 255f, 239 / 255f, 231 / 255f, 0.64f),
        fg3 = Color(243 / 255f, 239 / 255f, 231 / 255f, 0.36f), fg4 = Color(243 / 255f, 239 / 255f, 231 / 255f, 0.18f),
        bg0 = Color(0xFF080A0A), bg1 = Color(0xFF0D0F11), card = Color(0xFF16181C), card2 = Color(0xFF1B1E22), card3 = Color(0xFF23262B),
        sep = Color(150 / 255f, 146 / 255f, 134 / 255f, 0.16f), line = Color(0xFF2A2D32), hair = dkHair,
        glass = Color(13 / 255f, 15 / 255f, 17 / 255f, 0.8f),
        green = Color(0xFF7FB85C), gold = Color(0xFFD4A24A), cyan = Color(0xFF46B4E8), purple = Color(0xFF8E79F0),
        rose = Color(0xFFE0607F), warn = Color(0xFFE0A93B), danger = Color(0xFFE5604D), teal = Color(0xFF13A88C), isLight = false,
    ),
    "maghrib" to TfColors(
        fg = Color(0xFFFBF4EC), fg2 = Color(247 / 255f, 235 / 255f, 222 / 255f, 0.64f),
        fg3 = Color(247 / 255f, 235 / 255f, 222 / 255f, 0.36f), fg4 = Color(247 / 255f, 235 / 255f, 222 / 255f, 0.18f),
        bg0 = Color(0xFF100C09), bg1 = Color(0xFF14100D), card = Color(0xFF1E1712), card2 = Color(0xFF241B14), card3 = Color(0xFF2E241B),
        sep = Color(190 / 255f, 150 / 255f, 120 / 255f, 0.16f), line = Color(0xFF34281E), hair = dkHair,
        glass = Color(20 / 255f, 16 / 255f, 13 / 255f, 0.8f),
        green = Color(0xFFB6B45E), gold = Color(0xFFE5B452), cyan = Color(0xFFD98B6B), purple = Color(0xFFC28BA0),
        rose = Color(0xFFC25B5B), warn = Color(0xFFE5B452), danger = Color(0xFFE5604D), teal = Color(0xFFE0794B), isLight = false,
    ),
    "zumurrad" to TfColors(
        fg = Color(0xFFF2F8F4), fg2 = Color(225 / 255f, 240 / 255f, 230 / 255f, 0.62f),
        fg3 = Color(225 / 255f, 240 / 255f, 230 / 255f, 0.34f), fg4 = Color(225 / 255f, 240 / 255f, 230 / 255f, 0.18f),
        bg0 = Color(0xFF060F0B), bg1 = Color(0xFF08120E), card = Color(0xFF0F1C16), card2 = Color(0xFF14241B), card3 = Color(0xFF1C2E24),
        sep = Color(120 / 255f, 170 / 255f, 140 / 255f, 0.16f), line = Color(0xFF1E3228), hair = dkHair,
        glass = Color(8 / 255f, 18 / 255f, 14 / 255f, 0.8f),
        green = Color(0xFF7FB85C), gold = Color(0xFFE5C572), cyan = Color(0xFF5FC2C0), purple = Color(0xFF9AD46A),
        rose = Color(0xFFE08AA8), warn = Color(0xFFE5C572), danger = Color(0xFFE5604D), teal = Color(0xFF1FB874), isLight = false,
    ),
    "sama" to TfColors(
        fg = Color(0xFFF0F3F8), fg2 = Color(222 / 255f, 230 / 255f, 245 / 255f, 0.62f),
        fg3 = Color(222 / 255f, 230 / 255f, 245 / 255f, 0.34f), fg4 = Color(222 / 255f, 230 / 255f, 245 / 255f, 0.18f),
        bg0 = Color(0xFF0A0E18), bg1 = Color(0xFF0F1320), card = Color(0xFF19202F), card2 = Color(0xFF1E2638), card3 = Color(0xFF283044),
        sep = Color(150 / 255f, 165 / 255f, 200 / 255f, 0.16f), line = Color(0xFF2A3346), hair = dkHair,
        glass = Color(15 / 255f, 19 / 255f, 32 / 255f, 0.8f),
        green = Color(0xFF9ED0A0), gold = Color(0xFFE9C7A0), cyan = Color(0xFF7FC8C0), purple = Color(0xFFA6A0E0),
        rose = Color(0xFFE0BE9A), warn = Color(0xFFE9C7A0), danger = Color(0xFFE5604D), teal = Color(0xFF7FC8C0), isLight = false,
    ),
    "noor" to TfColors(
        fg = Color(0xFF2A2620), fg2 = Color(42 / 255f, 38 / 255f, 32 / 255f, 0.66f),
        fg3 = Color(42 / 255f, 38 / 255f, 32 / 255f, 0.40f), fg4 = Color(42 / 255f, 38 / 255f, 32 / 255f, 0.20f),
        bg0 = Color(0xFFEDE6D7), bg1 = Color(0xFFF4EEE2), card = Color(0xFFFFFFFF), card2 = Color(0xFFFBF6EC), card3 = Color(0xFFF2EAD9),
        sep = Color(60 / 255f, 50 / 255f, 35 / 255f, 0.14f), line = Color(60 / 255f, 50 / 255f, 35 / 255f, 0.20f), hair = ltHair,
        glass = Color(244 / 255f, 238 / 255f, 226 / 255f, 0.82f),
        green = Color(0xFF5E924B), gold = Color(0xFFB0832C), cyan = Color(0xFF2E80A8), purple = Color(0xFF7B5EA8),
        rose = Color(0xFFC25B7A), warn = Color(0xFFB0832C), danger = Color(0xFFC0392B), teal = Color(0xFF1F7A5C), isLight = true,
    ),
    "sahifa" to TfColors(
        fg = Color(0xFF1B2430), fg2 = Color(27 / 255f, 36 / 255f, 48 / 255f, 0.64f),
        fg3 = Color(27 / 255f, 36 / 255f, 48 / 255f, 0.40f), fg4 = Color(27 / 255f, 36 / 255f, 48 / 255f, 0.20f),
        bg0 = Color(0xFFE7ECF1), bg1 = Color(0xFFEEF2F6), card = Color(0xFFFFFFFF), card2 = Color(0xFFF6F9FC), card3 = Color(0xFFEAF0F5),
        sep = Color(30 / 255f, 45 / 255f, 65 / 255f, 0.12f), line = Color(30 / 255f, 45 / 255f, 65 / 255f, 0.18f), hair = ltHair,
        glass = Color(238 / 255f, 242 / 255f, 246 / 255f, 0.82f),
        green = Color(0xFF4E9A6B), gold = Color(0xFFB0832C), cyan = Color(0xFF2E80C8), purple = Color(0xFF6E5EC8),
        rose = Color(0xFFC25B7A), warn = Color(0xFFC08A3A), danger = Color(0xFFC0392B), teal = Color(0xFF128A78), isLight = true,
    ),
)

data class ThemeMeta(val key: String, val name: String, val concept: String, val mode: String)

val ThemeMetaList = listOf(
    ThemeMeta("sukut", "Sukut", "Moonlit night", "dark"),
    ThemeMeta("fajr", "Fajr", "Warm dawn", "dark"),
    ThemeMeta("maghrib", "Maghrib", "Sunset earth", "dark"),
    ThemeMeta("zumurrad", "Zumurrad", "Emerald luxe", "dark"),
    ThemeMeta("sama", "Sama", "Soft sky", "dark"),
    ThemeMeta("noor", "Noor", "Printed mushaf", "light"),
    ThemeMeta("sahifa", "Sahifa", "Cool paper", "light"),
)

/** Typeface roles. Real SF Pro / Inter / New York aren't bundled — nearest system families. */
object Fonts {
    val sans = FontFamily.Default
    val display = FontFamily.Default
    val mono = FontFamily.Monospace
    val serif = FontFamily.Serif
}

// ── CompositionLocals (AppCtx in kit.jsx) ────────────────────────────────────
val LocalTf: ProvidableCompositionLocal<TfColors> = staticCompositionLocalOf { TfThemes.getValue("sukut") }
val LocalProactivity: ProvidableCompositionLocal<String> = compositionLocalOf { "balanced" }
val LocalSerifHead: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }
val LocalNavMode: ProvidableCompositionLocal<String> = compositionLocalOf { "tabs" }

/** Shorthand accessor mirroring `C` in the prototype. */
val C: TfColors
    @Composable get() = LocalTf.current
