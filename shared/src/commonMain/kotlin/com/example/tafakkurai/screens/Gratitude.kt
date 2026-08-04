package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.example.tafakkurai.gratitude.*
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.spiritual.daysFromCivil
import com.example.tafakkurai.spiritual.parseDateKey
import com.example.tafakkurai.ui.*
import org.koin.mp.KoinPlatform.getKoin

// ── palette helper ───────────────────────────────────────────────────────────

/** Resolves a domain colour token against the active palette. */
@Composable
private fun gratColor(token: String): Color {
    val c = LocalTf.current
    return when (token) {
        "fg2" -> c.fg2
        "warn" -> c.warn
        "danger" -> c.danger
        else -> c.named(token)
    }
}

private const val GPAD = 16

// ── small building blocks ────────────────────────────────────────────────────

/** The drawn mood face: eyes plus a mouth whose curve encodes the mood. */
@Composable
private fun MoodFace(mood: GratMood, size: Int = 30, active: Boolean = false) {
    val color = gratColor(mood.color)
    val ink = if (active) Color(0xFF12100A) else color
    Canvas(Modifier.size(size.dp)) {
        val s = this.size.minDimension
        val unit = s / 24f
        drawCircle(
            color = if (active) color.copy(alpha = 0.92f) else color.copy(alpha = 0.14f),
            radius = 11f * unit,
            center = center,
        )
        drawCircle(
            color = if (active) color else color.copy(alpha = 0.4f),
            radius = 11f * unit,
            center = center,
            style = Stroke(width = unit),
        )
        drawCircle(ink, radius = 1.15f * unit, center = Offset(8.6f * unit, 10.2f * unit))
        drawCircle(ink, radius = 1.15f * unit, center = Offset(15.4f * unit, 10.2f * unit))
        // Mouth: quadratic curve, control point offset by the mood value.
        val off = (mood.curve - 0.5f) * 16f
        val path = Path().apply {
            moveTo(8f * unit, 14.6f * unit)
            quadraticTo(12f * unit, (14.6f + off) * unit, 16f * unit, 14.6f * unit)
        }
        drawPath(path, ink, style = Stroke(width = 1.5f * unit, cap = StrokeCap.Round))
    }
}

/** Circular progress arc used by the journey cards. */
@Composable
private fun ProgArc(fraction: Float, color: Color, size: Int = 48, stroke: Int = 5, content: @Composable () -> Unit) {
    val track = Color(150 / 255f, 160 / 255f, 190 / 255f, 0.16f)
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.dp.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            drawArc(
                color, -90f, 360f * fraction.coerceIn(0f, 1f), false,
                Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
private fun CatTag(category: String, small: Boolean = false) {
    val cat = categoryOf(category)
    val color = gratColor(cat.color)
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(color.soft(0.14f))
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TfIcon(cat.icon, size = if (small) 11 else 12, color = color)
        TfText(cat.label, color = color, size = if (small) 10.5f else 11.5f, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun GIconBtn(name: String, onClick: () -> Unit, color: Color? = null) {
    val c = LocalTf.current
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(c.card2).then(pressScale(onClick, 0.9f)),
        contentAlignment = Alignment.Center,
    ) { TfIcon(name, size = 18, color = color ?: c.fg2) }
}

/** Back bar with optional trailing actions — the Gratitude module's own chrome. */
@Composable
private fun GTop(onBack: () -> Unit, right: @Composable RowScope.() -> Unit = {}) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(c.glass).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.then(pressScale(onBack, 0.9f)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TfIcon("chevronLeft", size = 20, color = c.accent)
            TfText("Back", color = c.accent, size = 16f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = right)
    }
}

@Composable
private fun EntryCard(entry: GratEntry, todayKey: String, onOpen: (GratEntry) -> Unit) {
    val c = LocalTf.current
    val mood = moodOf(entry.mood)
    Card(Modifier.fillMaxWidth(), pad = 15, onClick = { onOpen(entry) }) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TfText(gratDateLabel(entry.entryDate, todayKey), color = c.fg3, size = 11f, family = Fonts.mono)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mood != null) MoodFace(mood, size = 18)
                CatTag(entry.category, small = true)
            }
        }
        TfText(entry.text, color = c.fg, size = 16.5f, family = Fonts.serif, lineHeight = 1.45f)
        if (entry.photoCount > 0 || entry.hasAudio || entry.favourite) {
            Row(
                Modifier.fillMaxWidth().padding(top = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (entry.photoCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        TfIcon("image", size = 13, color = c.fg3)
                        TfText("${entry.photoCount}", color = c.fg3, size = 10.5f, family = Fonts.mono)
                    }
                }
                if (entry.hasAudio) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        TfIcon("mic", size = 13, color = c.fg3)
                        TfText("Voice", color = c.fg3, size = 10.5f, family = Fonts.mono)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (entry.favourite) TfIcon("heart", size = 14, color = c.rose)
            }
        }
    }
}

@Composable
private fun GOverline(text: String, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    TfText(
        text.uppercase(), color = c.fg3, size = 10.5f, weight = FontWeight.SemiBold,
        family = Fonts.mono, letterSpacing = 0.8f, modifier = modifier,
    )
}

@Composable
private fun GPrimaryButton(label: String, icon: String? = null, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalTf.current
    val bg = if (enabled) color else c.card2
    val fg = if (enabled) Color(0xFF0C1A12) else c.fg4
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .then(if (enabled) pressScale(onClick, 0.97f) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TfIcon(icon, size = 18, color = fg)
            Spacer(Modifier.width(8.dp))
        }
        TfText(label, color = fg, size = 15.5f, weight = FontWeight.Bold)
    }
}

// ── router ───────────────────────────────────────────────────────────────────

private sealed interface GRoute {
    data object Home : GRoute
    data class Compose(val prompt: String) : GRoute
    data object History : GRoute
    data object Search : GRoute
    data object Jar : GRoute
    data object Challenges : GRoute
    data object Insights : GRoute
    data object Monthly : GRoute
    data object Settings : GRoute
    data class Detail(val id: String) : GRoute
}

/**
 * Gratitude (Shukr) — the full module: home, composer, history/calendar, search,
 * jar, journeys, insights, monthly reflection, entry detail and settings.
 *
 * Every screen reads the shared [GratitudeStore], so a save on the composer is
 * visible on the home card, in history and in the stats before the network is ever
 * touched. The store queues each change for sync; nothing here awaits a request.
 */
@Composable
fun GratitudeScreen(onBack: () -> Unit) {
    val store = remember { getKoin().get<GratitudeStore>() }
    val stack = remember { mutableStateListOf<GRoute>(GRoute.Home) }
    var savedFlash by remember { mutableStateOf(false) }
    val todayKey = remember { deviceDateKey() }

    fun push(route: GRoute) = stack.add(route)
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }

    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            kotlinx.coroutines.delay(1400)
            savedFlash = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val top = stack.last()) {
            is GRoute.Home -> GratHome(
                store = store, todayKey = todayKey, onBack = onBack,
                onCompose = { push(GRoute.Compose(it)) },
                onOpen = { push(GRoute.Detail(it.id)) },
                onRoute = ::push,
            )
            is GRoute.Compose -> GratCompose(
                store = store, seedPrompt = top.prompt, onBack = ::pop,
                onSaved = {
                    while (stack.size > 1) stack.removeAt(stack.lastIndex)
                    savedFlash = true
                },
            )
            is GRoute.History -> GratHistory(store, todayKey, ::pop, { push(GRoute.Search) }) { push(GRoute.Detail(it.id)) }
            is GRoute.Search -> GratSearch(store, todayKey, ::pop) { push(GRoute.Detail(it.id)) }
            is GRoute.Jar -> GratJar(store, todayKey, ::pop)
            is GRoute.Challenges -> GratChallenges(store, ::pop)
            is GRoute.Insights -> GratInsights(store, ::pop) { push(GRoute.Monthly) }
            is GRoute.Monthly -> GratMonthly(store, ::pop)
            is GRoute.Settings -> GratSettingsScreen(store, ::pop)
            is GRoute.Detail -> {
                val entry = store.byId(top.id)
                // The entry can vanish under us (deleted from another surface); leave the
                // screen as a side effect rather than mutating the stack during composition.
                if (entry == null) LaunchedEffect(top.id) { pop() } else GratDetail(store, entry, todayKey, ::pop)
            }
        }

        if (savedFlash) SavedOverlay()
    }
}

@Composable
private fun SavedOverlay() {
    val c = LocalTf.current
    Box(
        Modifier.fillMaxSize().background(Color(7 / 255f, 9 / 255f, 13 / 255f, 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                AmbientGlow(c.green, opacity = 0.5f)
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.green, c.green.soft(0.5f)))),
                    contentAlignment = Alignment.Center,
                ) { TfIcon("check", size = 30, color = Color(0xFF0C1A12)) }
            }
            TfText("Gratitude captured.", color = c.fg, size = 22f, family = Fonts.serif)
        }
    }
}

// ── HOME ─────────────────────────────────────────────────────────────────────

@Composable
private fun GratHome(
    store: GratitudeStore,
    todayKey: String,
    onBack: () -> Unit,
    onCompose: (String) -> Unit,
    onOpen: (GratEntry) -> Unit,
    onRoute: (GRoute) -> Unit,
) {
    val c = LocalTf.current
    val accent = c.green
    var promptIndex by remember { mutableStateOf(0) }
    val prompt = GRAT_PROMPTS[promptIndex % GRAT_PROMPTS.size]
    val today = store.today()
    val stats = store.stats()
    val throwback = remember(store.entries.size) { store.throwback() }

    Column(Modifier.fillMaxSize()) {
        GTop(onBack) {
            GIconBtn("search", { onRoute(GRoute.Search) })
            GIconBtn("calendar", { onRoute(GRoute.History) })
            GIconBtn("settings", { onRoute(GRoute.Settings) })
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
        ) {
            // title
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                AmbientGlow(accent, opacity = 0.34f)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TfText("Gratitude", color = c.fg, size = 33f, weight = FontWeight.Bold, family = Fonts.display)
                        TfText("الشكر", color = accent.soft(0.85f), size = 22f, family = Fonts.serif)
                    }
                    TfText("Notice the good in today.", color = c.fg2, size = 14.5f, modifier = Modifier.padding(top = 3.dp))
                }
            }

            // today's gratitude — the primary CTA
            Box(Modifier.padding(start = GPAD.dp, end = GPAD.dp, top = 16.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(accent.soft(0.16f), c.cyan.soft(0.05f))))
                        .border(0.5.dp, accent.soft(0.4f), RoundedCornerShape(18.dp))
                        .padding(18.dp),
                ) {
                    GOverline("Today's gratitude", Modifier.padding(bottom = 8.dp))
                    TfText(prompt, color = c.fg, size = 21f, family = Fonts.serif, lineHeight = 1.32f)
                    Row(
                        Modifier.padding(top = 11.dp).then(pressScale({ promptIndex++ }, 0.95f)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TfIcon("repeat", size = 13, color = c.fg3)
                        TfText("Change prompt", color = c.fg3, size = 12.5f)
                    }
                    if (today.isNotEmpty()) {
                        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            today.take(3).forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(Color(0f, 0f, 0f, 0.16f))
                                        .padding(horizontal = 11.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    TfIcon("leaf", size = 13, color = accent)
                                    TfText(entry.text, color = c.fg, size = 13.5f, maxLines = 1)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GPrimaryButton("Add gratitude", icon = "plus", color = accent) { onCompose(prompt) }
                }
            }

            // streak
            Box(Modifier.padding(start = GPAD.dp, end = GPAD.dp, top = 14.dp)) {
                Card(Modifier.fillMaxWidth(), pad = 15, onClick = { onRoute(GRoute.Insights) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconBadge("flame", c.gold, size = 46, iconSize = 22)
                        Column(Modifier.weight(1f)) {
                            TfText(
                                if (stats.streak > 0) "Day ${stats.streak} of your journey" else "Start your journey today",
                                color = c.fg, size = 15f, weight = FontWeight.SemiBold,
                            )
                            TfText(
                                "${stats.total} entries · longest ${stats.longest}",
                                color = c.fg2, size = 12.5f, modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            stats.week.forEach { on ->
                                Box(
                                    Modifier.size(6.dp).clip(CircleShape)
                                        .background(if (on) c.gold else Color(150 / 255f, 160 / 255f, 190 / 255f, 0.24f)),
                                )
                            }
                        }
                    }
                }
            }

            // memory throwback
            if (throwback != null) {
                Column(Modifier.padding(start = GPAD.dp, end = GPAD.dp, top = 22.dp)) {
                    GOverline("A memory to revisit", Modifier.padding(start = 4.dp, bottom = 10.dp))
                    Card(Modifier.fillMaxWidth(), pad = 16) {
                        TfIcon("quote", size = 20, color = c.gold.soft(0.7f))
                        TfText(
                            throwback.text, color = c.fg, size = 18f, family = Fonts.serif, lineHeight = 1.44f,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                        )
                        TfText(
                            "${gratRelativeLabel(throwback.entryDate, todayKey)} · ${gratDateLabel(throwback.entryDate, todayKey)}",
                            color = c.fg3, size = 11f, family = Fonts.mono,
                        )
                        Row(Modifier.padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(c.rose.soft(0.14f))
                                    .then(pressScale({ store.toggleFavourite(throwback.id) }, 0.97f))
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TfIcon("heart", size = 15, color = c.rose)
                                Spacer(Modifier.width(7.dp))
                                TfText("Grateful for this", color = c.rose, size = 13f, weight = FontWeight.SemiBold)
                            }
                            Box(
                                Modifier.size(46.dp, 40.dp).clip(RoundedCornerShape(11.dp))
                                    .border(0.5.dp, c.line, RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center,
                            ) { TfIcon("share", size = 16, color = c.fg2) }
                        }
                    }
                }
            }

            // recent
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                GOverline("Recent memories")
                TfText("View all", color = accent, size = 14f, modifier = pressScale({ onRoute(GRoute.History) }, 0.92f))
            }
            Column(Modifier.padding(horizontal = GPAD.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                if (store.entries.isEmpty()) {
                    TfText(
                        "Nothing here yet. Your first gratitude starts the journal.",
                        color = c.fg3, size = 14f, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                } else {
                    store.entries.take(2).forEach { EntryCard(it, todayKey, onOpen) }
                }
            }

            // quick tiles
            Row(
                Modifier.fillMaxWidth().padding(start = GPAD.dp, end = GPAD.dp, top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                listOf(
                    Triple(GRoute.Jar, "gift" to "Jar", c.gold),
                    Triple(GRoute.Challenges, "target" to "Journeys", c.teal),
                    Triple(GRoute.Insights, "trendingUp" to "Insights", c.purple),
                ).forEach { (route, meta, color) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.card)
                            .border(0.5.dp, c.sep, RoundedCornerShape(16.dp))
                            .then(pressScale({ onRoute(route) }, 0.96f))
                            .padding(horizontal = 10.dp, vertical = 15.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        IconBadge(meta.first, color, size = 40, iconSize = 20)
                        TfText(meta.second, color = c.fg, size = 12.5f, weight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── COMPOSER ─────────────────────────────────────────────────────────────────

@Composable
private fun GratCompose(store: GratitudeStore, seedPrompt: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = LocalTf.current
    val accent = c.green
    var promptIndex by remember { mutableStateOf(GRAT_PROMPTS.indexOf(seedPrompt).coerceAtLeast(0)) }
    val prompt = GRAT_PROMPTS[promptIndex % GRAT_PROMPTS.size]

    var mode by remember { mutableStateOf(GratMode.QUICK) }
    var quick by remember { mutableStateOf("") }
    val three = remember { mutableStateListOf("", "", "") }
    var reflect by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf<String?>(null) }
    val emotions = remember { mutableStateListOf<String>() }
    var category by remember { mutableStateOf<String?>(null) }
    val media = remember { mutableStateListOf<String>() }
    var location by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    val hasContent = when (mode) {
        GratMode.QUICK -> quick.isNotBlank()
        GratMode.THREE -> three.any { it.isNotBlank() }
        GratMode.REFLECT -> reflect.isNotBlank()
    }

    fun save() {
        if (!hasContent) return
        val items = three.map { it.trim() }.filter { it.isNotEmpty() }
        val text = when (mode) {
            GratMode.QUICK -> quick.trim()
            GratMode.THREE -> items.joinToString(" · ")
            GratMode.REFLECT -> reflect.trim()
        }
        store.add(
            text = text,
            mode = mode,
            items = if (mode == GratMode.THREE) items else emptyList(),
            why = why,
            category = category,
            mood = mood,
            emotions = emotions.toList(),
            photoCount = media.count { it == "photo" || it == "camera" },
            hasAudio = media.contains("voice"),
            hasVideo = media.contains("video"),
            locationLabel = if (location) "Tashkent, Uzbekistan" else null,
        )
        onSaved()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TfText("Cancel", color = c.fg2, size = 16f, modifier = pressScale(onBack, 0.92f))
            TfText("New gratitude", color = c.fg, size = 16f, weight = FontWeight.SemiBold)
            TfText(
                "Save", color = if (hasContent) accent else c.fg4, size = 16f, weight = FontWeight.Bold,
                modifier = pressScale({ save() }, 0.92f),
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = GPAD.dp),
        ) {
            // prompt
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(accent.soft(0.09f))
                    .border(0.5.dp, accent.soft(0.3f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                GOverline("Today I'm grateful for…")
                TfText(prompt, color = c.fg, size = 19f, family = Fonts.serif, lineHeight = 1.34f, modifier = Modifier.padding(top = 6.dp))
                Row(
                    Modifier.padding(top = 9.dp).then(pressScale({ promptIndex++ }, 0.95f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TfIcon("repeat", size = 12, color = c.fg3)
                    TfText("Another prompt", color = c.fg3, size = 12f)
                }
            }

            Spacer(Modifier.height(16.dp))
            Segmented(
                tabs = GratMode.entries.map { it.id to it.label },
                value = mode.id,
                onChange = { mode = GratMode.from(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            when (mode) {
                GratMode.QUICK -> GField(
                    value = quick, onValue = { quick = it },
                    placeholder = "One small thing you're grateful for…",
                    serif = true, minHeight = 84,
                )
                GratMode.THREE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    three.forEachIndexed { i, value ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card2)
                                .border(0.5.dp, c.sep, RoundedCornerShape(12.dp))
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).background(accent.soft(0.2f)),
                                contentAlignment = Alignment.Center,
                            ) { TfText("${i + 1}", color = accent, size = 11f, family = Fonts.mono, weight = FontWeight.SemiBold) }
                            Box(Modifier.weight(1f)) {
                                if (value.isEmpty()) TfText("I'm grateful for…", color = c.fg3, size = 16f, family = Fonts.serif)
                                BasicTextField(
                                    value = value, onValueChange = { three[i] = it }, singleLine = true,
                                    textStyle = TextStyle(color = c.fg, fontSize = 16.sp, fontFamily = Fonts.serif),
                                    cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    GField(value = why, onValue = { why = it }, placeholder = "Why did these moments matter? (optional)", size = 13.5f)
                }
                GratMode.REFLECT -> GField(
                    value = reflect, onValue = { reflect = it },
                    placeholder = "Write freely. No one sees this but you.",
                    serif = true, minHeight = 150,
                )
            }

            // AI reflect
            Row(
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(listOf(c.purple.soft(0.18f), accent.soft(0.08f))))
                    .border(0.5.dp, c.purple.soft(0.4f), RoundedCornerShape(999.dp))
                    .then(pressScale({ hint = reflectionHint(if (mode == GratMode.QUICK) quick else if (mode == GratMode.REFLECT) reflect else three.joinToString(" ")) }, 0.96f))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TfIcon("sparkles", size = 15, color = c.purple)
                TfText("Help me reflect", color = c.fg, size = 13f, weight = FontWeight.SemiBold)
            }
            hint?.let { text ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 11.dp).clip(RoundedCornerShape(14.dp))
                        .background(c.card).border(0.5.dp, c.sep, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(c.purple, accent))),
                        contentAlignment = Alignment.Center,
                    ) { TfIcon("sparkles", size = 14, color = Color.White) }
                    Column {
                        GOverline("Murabbiy · reflection")
                        TfText(text, color = c.fg, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }

            // mood
            GOverline("How did today feel?", Modifier.padding(top = 22.dp, bottom = 11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GRAT_MOODS.forEach { m ->
                    val on = mood == m.key
                    Column(
                        Modifier.weight(1f).then(pressScale({ mood = if (on) null else m.key }, 0.92f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MoodFace(m, size = 40, active = on)
                        TfText(
                            m.label, color = if (on) c.fg else c.fg3, size = 10.5f,
                            weight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }

            // emotions
            GOverline("Emotions", Modifier.padding(top = 22.dp, bottom = 11.dp))
            GFlowRow(GRAT_EMOTIONS) { tag ->
                val on = emotions.contains(tag)
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (on) accent.soft(0.16f) else c.card2)
                        .border(1.dp, if (on) accent else c.sep, RoundedCornerShape(999.dp))
                        .then(pressScale({ if (on) emotions.remove(tag) else emotions.add(tag) }, 0.94f))
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    TfText(tag, color = if (on) c.fg else c.fg2, size = 13f, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                }
            }

            // category
            GOverline("Category", Modifier.padding(top = 22.dp, bottom = 11.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                GRAT_CATEGORIES.forEach { cat ->
                    val on = category == cat.key
                    val color = gratColor(cat.color)
                    Column(
                        Modifier.width(62.dp).then(pressScale({ category = if (on) null else cat.key }, 0.93f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.size(50.dp).clip(RoundedCornerShape(15.dp))
                                .background(if (on) color.copy(alpha = 0.9f) else color.soft(0.14f))
                                .border(1.dp, if (on) color else color.soft(0.3f), RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center,
                        ) { TfIcon(cat.icon, size = 22, color = if (on) Color(0xFF12100A) else color) }
                        TfText(cat.label, color = if (on) c.fg else c.fg3, size = 10.5f, align = TextAlign.Center, lineHeight = 1.1f)
                    }
                }
            }

            // attachments
            GOverline("Add to this moment", Modifier.padding(top = 22.dp, bottom = 11.dp))
            GFlowRow(
                listOf("photo" to "Photo", "camera" to "Camera", "voice" to "Voice", "video" to "Video", "location" to "Location"),
            ) { (key, label) ->
                val on = if (key == "location") location else media.contains(key)
                val icon = when (key) {
                    "photo" -> "image"; "camera" -> "camera"; "voice" -> "mic"; "video" -> "video"; else -> "mapPin"
                }
                Row(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(if (on) accent.soft(0.16f) else c.card2)
                        .border(0.5.dp, if (on) accent else c.sep, RoundedCornerShape(11.dp))
                        .then(
                            pressScale({
                                if (key == "location") location = !location
                                else if (on) media.remove(key) else media.add(key)
                            }, 0.94f),
                        )
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    TfIcon(icon, size = 16, color = if (on) accent else c.fg2)
                    TfText(
                        if (key == "location" && on) "Tashkent" else label,
                        color = if (on) c.fg else c.fg2, size = 13f,
                        weight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = GPAD.dp, vertical = 12.dp)) {
            GPrimaryButton("Save gratitude", color = accent, enabled = hasContent) { save() }
        }
    }
}

/** Local, deterministic composer hint — no network, no model, works offline. */
private fun reflectionHint(source: String): String {
    val s = source.lowercase()
    return when {
        listOf("stress", "hard", "tired", "qiyin", "charchadim").any { s.contains(it) } ->
            "What part of today still gave you a small sense of comfort?"
        listOf("family", "mother", "friend", "ona", "oila", "do'st").any { s.contains(it) } ->
            "That sounds meaningful. What did it make you feel?"
        else -> "What is one small thing that went quietly right today?"
    }
}

@Composable
private fun GField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    serif: Boolean = false,
    size: Float = 18f,
    minHeight: Int = 0,
) {
    val c = LocalTf.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card2)
            .border(0.5.dp, c.sep, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .then(if (minHeight > 0) Modifier.heightIn(min = minHeight.dp) else Modifier),
    ) {
        if (value.isEmpty()) {
            TfText(placeholder, color = c.fg3, size = size, family = if (serif) Fonts.serif else Fonts.sans)
        }
        BasicTextField(
            value = value, onValueChange = onValue,
            textStyle = TextStyle(
                color = c.fg, fontSize = size.sp,
                fontFamily = if (serif) Fonts.serif else Fonts.sans,
                lineHeight = (size * 1.5f).sp,
            ),
            cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Minimal wrapping row — avoids depending on the experimental FlowRow API. */
@Composable
private fun <T> GFlowRow(items: List<T>, perRow: Int = 3, item: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { item(it) } }
        }
    }
}

// ── HISTORY ──────────────────────────────────────────────────────────────────

@Composable
private fun GratHistory(
    store: GratitudeStore,
    todayKey: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpen: (GratEntry) -> Unit,
) {
    val c = LocalTf.current
    var tab by remember { mutableStateOf("timeline") }
    var selected by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        GTop(onBack) { GIconBtn("search", onSearch) }
        Column(Modifier.padding(horizontal = 20.dp)) {
            TfText("Your memories", color = c.fg, size = 28f, weight = FontWeight.Bold, family = Fonts.display)
            Spacer(Modifier.height(12.dp))
            Segmented(
                tabs = listOf("timeline" to "Timeline", "calendar" to "Calendar"),
                value = tab, onChange = { tab = it }, modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = GPAD.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (tab == "timeline") {
                if (store.entries.isEmpty()) {
                    TfText("No memories yet.", color = c.fg3, size = 14f, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                }
                store.entries.forEach { EntryCard(it, todayKey, onOpen) }
            } else {
                GratCalendar(store, todayKey, selected) { selected = if (selected == it) null else it }
                val day = selected
                if (day == null) {
                    TfText("Tap a day to revisit it.", color = c.fg3, size = 13.5f, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
                } else {
                    val dayEntries = store.onDate(day)
                    if (dayEntries.isEmpty()) {
                        TfText(
                            "No gratitude noted on ${gratDateLabel(day, todayKey)}.",
                            color = c.fg3, size = 14f, align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                        )
                    } else {
                        dayEntries.forEach { EntryCard(it, todayKey, onOpen) }
                    }
                }
            }
        }
    }
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    else -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
}

/** 0 = Sunday. 1970-01-01 was a Thursday, hence the +4 shift. */
private fun weekdayOf(year: Int, month: Int, day: Int): Int =
    (((daysFromCivil(year, month, day) % 7) + 7 + 4) % 7).toInt()

@Composable
private fun GratCalendar(store: GratitudeStore, todayKey: String, selected: String?, onSelect: (String) -> Unit) {
    val c = LocalTf.current
    val accent = c.green
    val (year, month, _) = parseDateKey(todayKey) ?: return
    val marks = store.activeDays()
    val total = daysInMonth(year, month)
    val lead = weekdayOf(year, month, 1)
    val monthName = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[month - 1]

    Card(Modifier.fillMaxWidth(), pad = 16) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TfText("$monthName $year", color = c.fg, size = 17f, weight = FontWeight.SemiBold, family = Fonts.display)
            TfText("${marks.count { it.startsWith(todayKey.substring(0, 7)) }} days", color = c.fg3, size = 11f, family = Fonts.mono)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                TfText(it, color = c.fg4, size = 9f, family = Fonts.mono, align = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val cells: List<Int?> = List(lead) { null } + (1..total).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Box(Modifier.weight(1f).height(34.dp))
                    } else {
                        val key = "$year-${pad2(month)}-${pad2(day)}"
                        val marked = marks.contains(key)
                        val isSelected = selected == key
                        val isToday = key == todayKey
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp))
                                .background(
                                    when {
                                        isSelected -> accent
                                        marked -> accent.soft(0.16f)
                                        else -> Color.Transparent
                                    },
                                )
                                .then(if (isToday) Modifier.border(1.5.dp, accent, RoundedCornerShape(9.dp)) else Modifier)
                                .then(pressScale({ onSelect(key) }, 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            TfText(
                                "$day",
                                color = when {
                                    isSelected -> Color(0xFF0C1A12)
                                    marked -> c.fg
                                    else -> c.fg4
                                },
                                size = 11.5f, family = Fonts.mono,
                                weight = if (marked) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

private fun pad2(n: Int) = if (n < 10) "0$n" else "$n"

// ── SEARCH ───────────────────────────────────────────────────────────────────

@Composable
private fun GratSearch(store: GratitudeStore, todayKey: String, onBack: () -> Unit, onOpen: (GratEntry) -> Unit) {
    val c = LocalTf.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val results = searchEntries(store.entries, query, category)

    Column(Modifier.fillMaxSize()) {
        GTop(onBack)
        Column(Modifier.padding(horizontal = GPAD.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card2)
                    .border(0.5.dp, c.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                TfIcon("search", size = 18, color = c.fg3)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) TfText("Search your gratitude…", color = c.fg3, size = 15f)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = c.fg, fontSize = 15.sp, fontFamily = Fonts.sans),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.then(pressScale({ query = "" }, 0.9f))) { TfIcon("x", size = 16, color = c.fg3) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 11.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                GRAT_CATEGORIES.take(7).forEach { cat ->
                    val on = category == cat.key
                    val color = gratColor(cat.color)
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (on) color.soft(0.18f) else c.card2)
                            .border(1.dp, if (on) color else c.sep, RoundedCornerShape(999.dp))
                            .then(pressScale({ category = if (on) null else cat.key }, 0.94f))
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        TfIcon(cat.icon, size = 12, color = if (on) color else c.fg3)
                        TfText(cat.label, color = if (on) color else c.fg2, size = 12.5f, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = GPAD.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            TfText(
                "${results.size} ${if (results.size == 1) "memory" else "memories"}",
                color = c.fg3, size = 10.5f, family = Fonts.mono, modifier = Modifier.padding(start = 4.dp),
            )
            results.forEach { EntryCard(it, todayKey, onOpen) }
            if (results.isEmpty()) {
                TfText(
                    "Nothing found. Try another word.", color = c.fg3, size = 14.5f, align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            }
        }
    }
}

// ── JAR ──────────────────────────────────────────────────────────────────────

@Composable
private fun GratJar(store: GratitudeStore, todayKey: String, onBack: () -> Unit) {
    val c = LocalTf.current
    val accent = c.gold
    var picked by remember { mutableStateOf<GratEntry?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            GTop(onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    AmbientGlow(accent, opacity = 0.4f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TfText("Your gratitude jar", color = c.fg, size = 28f, weight = FontWeight.Bold, family = Fonts.display)
                        TfText(
                            "${store.entries.size} beautiful ${if (store.entries.size == 1) "moment" else "moments"}",
                            color = c.fg2, size = 14f, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // Each entry is one glowing bead, tinted by its category.
                Column(
                    Modifier.padding(horizontal = GPAD.dp).clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(c.card2, c.card)))
                        .border(0.5.dp, c.hair, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 22.dp),
                ) {
                    val beads = store.entries.take(42)
                    if (beads.isEmpty()) {
                        TfText(
                            "The jar fills as you write.", color = c.fg3, size = 14f, align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        )
                    }
                    beads.chunked(9).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.5.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            row.forEachIndexed { i, entry ->
                                val color = gratColor(categoryOf(entry.category).color)
                                val size = listOf(16, 20, 24, 14, 18, 22)[i % 6]
                                Box(
                                    Modifier.size(size.dp).clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.45f))),
                                        ),
                                )
                            }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = GPAD.dp, vertical = 20.dp)) {
                    GPrimaryButton("Pick a random memory", icon = "sparkles", color = accent, enabled = store.entries.isNotEmpty()) {
                        picked = store.randomMemory()
                    }
                    TfText(
                        "Every gratitude you write becomes a moment in the jar.",
                        color = c.fg3, size = 12.5f, align = TextAlign.Center, lineHeight = 1.5f,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }

        picked?.let { entry ->
            Box(
                Modifier.fillMaxSize().background(Color(7 / 255f, 9 / 255f, 13 / 255f, 0.7f))
                    .then(pressScale({ picked = null }, 1f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card)
                        .border(0.5.dp, c.line, RoundedCornerShape(20.dp)).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GOverline("Remember this?")
                    TfIcon("quote", size = 22, color = accent.soft(0.7f), modifier = Modifier.padding(vertical = 8.dp))
                    TfText(entry.text, color = c.fg, size = 20f, family = Fonts.serif, lineHeight = 1.44f, align = TextAlign.Center)
                    TfText(
                        gratDateLabel(entry.entryDate, todayKey), color = c.fg3, size = 11f, family = Fonts.mono,
                        modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.card2)
                                .border(0.5.dp, c.line, RoundedCornerShape(12.dp))
                                .then(pressScale({ picked = null }, 0.96f)).padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) { TfText("Close", color = c.fg2, size = 14f) }
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(accent)
                                .then(pressScale({ picked = store.randomMemory() }, 0.96f)).padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) { TfText("Another", color = Color(0xFF1A1306), size = 14f, weight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

// ── JOURNEYS ─────────────────────────────────────────────────────────────────

@Composable
private fun GratChallenges(store: GratitudeStore, onBack: () -> Unit) {
    val c = LocalTf.current
    val accent = c.green
    val progress = store.challengeProgress()

    Column(Modifier.fillMaxSize()) {
        GTop(onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = GPAD.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                TfText("Gratitude journeys", color = c.fg, size = 28f, weight = FontWeight.Bold, family = Fonts.display)
                TfText(
                    "Gentle, guided practice — one small step a day.",
                    color = c.fg2, size = 14f, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            progress.forEach { p ->
                if (p.challenge.days.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(accent.soft(0.14f), c.cyan.soft(0.04f))))
                            .border(0.5.dp, accent.soft(0.4f), RoundedCornerShape(18.dp)),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                TfText(p.challenge.title, color = c.fg, size = 18f, weight = FontWeight.Bold, family = Fonts.display, maxLines = 1)
                                TfText(p.challenge.sub, color = c.fg2, size = 13f, modifier = Modifier.padding(top = 2.dp))
                            }
                            ProgArc(p.fraction, accent) {
                                TfText("${p.done}/${p.challenge.total}", color = c.fg, size = 12f, family = Fonts.mono, weight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.background(Color(0f, 0f, 0f, 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            p.challenge.days.forEachIndexed { index, theme ->
                                val dayNumber = index + 1
                                val done = p.doneDays.contains(dayNumber)
                                val current = dayNumber == p.currentDay
                                Row(
                                    Modifier.fillMaxWidth()
                                        .then(pressScale({ store.completeChallengeDay(p.challenge.id, dayNumber) }, 0.98f))
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        Modifier.size(26.dp).clip(CircleShape)
                                            .background(
                                                when {
                                                    done -> accent
                                                    current -> accent.soft(0.2f)
                                                    else -> Color(150 / 255f, 160 / 255f, 190 / 255f, 0.12f)
                                                },
                                            )
                                            .then(if (current && !done) Modifier.border(1.5.dp, accent, CircleShape) else Modifier),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (done) TfIcon("check", size = 13, color = Color(0xFF0C1A12))
                                        else TfText("$dayNumber", color = if (current) accent else c.fg3, size = 11f, family = Fonts.mono, weight = FontWeight.SemiBold)
                                    }
                                    TfText(
                                        "Day $dayNumber — $theme",
                                        color = if (done) c.fg3 else c.fg, size = 14.5f,
                                        weight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (current && !done) Pill("Today", accent, accent.soft(0.16f))
                                }
                            }
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth(), pad = 17) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProgArc(p.fraction, c.purple, size = 52) {
                                TfText("${p.done}/${p.challenge.total}", color = c.fg, size = 11f, family = Fonts.mono, weight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                TfText(p.challenge.title, color = c.fg, size = 17f, weight = FontWeight.SemiBold, family = Fonts.display)
                                TfText(p.challenge.sub, color = c.fg2, size = 12.5f, modifier = Modifier.padding(top = 2.dp))
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp))
                                    .background(if (p.joined) Color.Transparent else accent)
                                    .then(if (p.joined) Modifier.border(1.dp, c.line, RoundedCornerShape(999.dp)) else Modifier)
                                    .then(pressScale({ store.setChallengeJoined(p.challenge.id, !p.joined) }, 0.94f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                TfText(
                                    if (p.joined) "Joined" else "Join",
                                    color = if (p.joined) c.fg2 else Color(0xFF0C1A12),
                                    size = 12.5f, weight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── INSIGHTS ─────────────────────────────────────────────────────────────────

@Composable
private fun GratInsights(store: GratitudeStore, onBack: () -> Unit, onMonthly: () -> Unit) {
    val c = LocalTf.current
    val accent = c.green
    val stats = store.stats()
    val shares = store.categoryShares()
    val moodWeek = store.moodWeek()
    val weekCount = stats.week.count { it }

    Column(Modifier.fillMaxSize()) {
        GTop(onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = GPAD.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TfText("Insights", color = c.fg, size = 28f, weight = FontWeight.Bold, family = Fonts.display, modifier = Modifier.padding(bottom = 16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf(
                    Triple("flame", "${stats.streak}", "day streak") to c.gold,
                    Triple("leaf", "${stats.total}", "entries") to accent,
                    Triple("calendar", "$weekCount/7", "days") to c.cyan,
                ).forEach { (meta, color) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.card)
                            .border(0.5.dp, c.sep, RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 13.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TfIcon(meta.first, size = 17, color = color)
                        TfText(meta.second, color = c.fg, size = 19f, weight = FontWeight.Bold, family = Fonts.display, modifier = Modifier.padding(top = 5.dp))
                        TfText(meta.third, color = c.fg3, size = 10.5f)
                    }
                }
            }

            GOverline("Mood this week", Modifier.padding(top = 22.dp, bottom = 11.dp, start = 4.dp))
            Card(Modifier.fillMaxWidth(), pad = 16) {
                Row(
                    Modifier.fillMaxWidth().height(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    moodWeek.forEachIndexed { i, value ->
                        val mood = GRAT_MOODS.getOrNull(value)
                        val color = if (mood != null) gratColor(mood.color) else c.fg4
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .height((if (value >= 0) ((value + 1) / 5f) * 72f else 4f).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.verticalGradient(listOf(color, color.soft(0.4f)))),
                            )
                            TfText(
                                listOf("M", "T", "W", "T", "F", "S", "S")[i],
                                color = c.fg4, size = 9.5f, family = Fonts.mono,
                            )
                        }
                    }
                }
            }

            GOverline("What you're grateful for", Modifier.padding(top = 22.dp, bottom = 11.dp, start = 4.dp))
            Card(Modifier.fillMaxWidth(), pad = 16) {
                if (shares.isEmpty()) {
                    TfText("Write a few entries and patterns will appear here.", color = c.fg3, size = 13f)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    shares.forEach { share ->
                        val cat = categoryOf(share.key)
                        val color = gratColor(cat.color)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            TfIcon(cat.icon, size = 15, color = color)
                            TfText(cat.label, color = c.fg, size = 13f, modifier = Modifier.width(74.dp), maxLines = 1)
                            ProgressBar(share.percent / 100f, color, height = 8, modifier = Modifier.weight(1f))
                            TfText("${share.percent}%", color = c.fg2, size = 11f, family = Fonts.mono, align = TextAlign.End, modifier = Modifier.width(32.dp))
                        }
                    }
                }
            }

            Box(Modifier.padding(top = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(c.purple.soft(0.16f), accent.soft(0.06f))))
                        .border(0.5.dp, c.purple.soft(0.35f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.purple, accent))),
                        contentAlignment = Alignment.Center,
                    ) { TfIcon("sparkles", size = 16, color = Color.White) }
                    Column {
                        GOverline("Reflection")
                        TfText(
                            gratReflection(shares, store.entries.size),
                            color = c.fg, size = 13.5f, lineHeight = 1.55f, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            GOverline("Monthly reflection", Modifier.padding(top = 22.dp, bottom = 11.dp, start = 4.dp))
            Card(Modifier.fillMaxWidth(), pad = 17, onClick = onMonthly) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge("bookOpen", c.gold, size = 46, iconSize = 22)
                    Column(Modifier.weight(1f)) {
                        TfText("Your month in gratitude", color = c.fg, size = 17f, weight = FontWeight.SemiBold, family = Fonts.display)
                        TfText("${stats.total} entries · ${stats.monthDays} days", color = c.fg2, size = 12.5f, modifier = Modifier.padding(top = 2.dp))
                    }
                    TfIcon("chevronRight", size = 20, color = c.fg3)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ── MONTHLY ──────────────────────────────────────────────────────────────────

@Composable
private fun GratMonthly(store: GratitudeStore, onBack: () -> Unit) {
    val c = LocalTf.current
    val accent = c.gold
    val stats = store.stats()
    val shares = store.categoryShares()
    val monthName = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )[(parseDateKey(deviceDateKey())?.second ?: 1) - 1]
    val monthPrefix = deviceDateKey().substring(0, 7)
    val monthEntries = store.entries.count { it.entryDate.startsWith(monthPrefix) }

    Column(Modifier.fillMaxSize()) {
        GTop(onBack) { GIconBtn("share", {}) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = GPAD.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                AmbientGlow(accent, opacity = 0.4f)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GOverline("$monthName ${parseDateKey(deviceDateKey())?.first ?: ""}")
                    TfText("A month of shukr", color = c.fg, size = 30f, family = Fonts.serif, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Card(Modifier.fillMaxWidth(), pad = 18) {
                TfText(
                    monthLine(monthName, shares, monthEntries),
                    color = c.fg, size = 18f, family = Fonts.serif, lineHeight = 1.5f,
                )
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                listOf(
                    listOf("Entries" to "$monthEntries", "Days practiced" to "${stats.monthDays}"),
                    listOf("Longest streak" to "${stats.longest}", "Top category" to (shares.firstOrNull()?.label ?: "—")),
                ).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        row.forEachIndexed { i, (label, value) ->
                            Card(Modifier.weight(1f), pad = 15) {
                                TfIcon(
                                    listOf("fileText", "calendar", "flame", "users")[rowIndex * 2 + i],
                                    size = 17, color = accent,
                                )
                                TfText(value, color = c.fg, size = 22f, weight = FontWeight.Bold, family = Fonts.display, modifier = Modifier.padding(top = 8.dp))
                                TfText(label, color = c.fg3, size = 12f)
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(top = 16.dp, bottom = 30.dp)) {
                GPrimaryButton("Share reflection", icon = "share", color = accent) { }
            }
        }
    }
}

private fun monthLine(month: String, shares: List<GratCategoryShare>, entries: Int): String = when {
    entries == 0 -> "$month has no entries yet — there is still time to notice something."
    shares.isEmpty() -> "$month was quiet, and quiet is also worth noting."
    else -> {
        val names = shares.take(3).map { it.label.lowercase() }
        val joined = when (names.size) {
            1 -> names[0]
            2 -> "${names[0]} and ${names[1]}"
            else -> "${names.dropLast(1).joinToString(", ")} and ${names.last()}"
        }
        "$month was full of moments connected to $joined — a steady, gentle month."
    }
}

// ── DETAIL ───────────────────────────────────────────────────────────────────

@Composable
private fun GratDetail(store: GratitudeStore, entry: GratEntry, todayKey: String, onBack: () -> Unit) {
    val c = LocalTf.current
    val cat = categoryOf(entry.category)
    val catColor = gratColor(cat.color)
    val mood = moodOf(entry.mood)
    val liked = store.byId(entry.id)?.favourite ?: entry.favourite

    Column(Modifier.fillMaxSize()) {
        GTop(onBack) {
            GIconBtn("share", {})
            GIconBtn("edit", {})
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        ) {
            Row(
                Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TfText(gratDateLabel(entry.entryDate, todayKey), color = c.fg3, size = 12f, family = Fonts.mono)
                if (mood != null) MoodFace(mood, size = 20)
                CatTag(entry.category, small = true)
            }
            TfText(entry.text, color = c.fg, size = 24f, family = Fonts.serif, lineHeight = 1.45f)
            entry.why?.let {
                TfText(it, color = c.fg2, size = 14.5f, lineHeight = 1.6f, modifier = Modifier.padding(top = 14.dp))
            }
            if (entry.photoCount > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(minOf(entry.photoCount, 2)) {
                        Box(
                            Modifier.weight(1f).height(110.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(catColor.soft(0.4f), catColor.soft(0.12f))))
                                .border(0.5.dp, c.hair, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) { TfIcon("image", size = 24, color = catColor.copy(alpha = 0.9f)) }
                    }
                }
            }
            if (entry.hasAudio) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(14.dp))
                        .background(c.card).border(0.5.dp, c.sep, RoundedCornerShape(14.dp))
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(c.green), contentAlignment = Alignment.Center) {
                        TfIcon("play", size = 16, color = Color(0xFF0C1A12))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(10, 18, 26, 14, 22, 30, 16, 24, 12, 20, 28, 15, 23, 11, 19).forEach { h ->
                            Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(c.green.soft(0.5f)))
                        }
                    }
                }
            }
            if (entry.emotions.isNotEmpty()) {
                Box(Modifier.padding(top = 18.dp)) {
                    GFlowRow(entry.emotions, perRow = 4) { tag ->
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(c.card2)
                                .border(0.5.dp, c.sep, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { TfText(tag, color = c.fg2, size = 12.5f) }
                    }
                }
            }
            entry.locationLabel?.let {
                Row(
                    Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TfIcon("mapPin", size = 14, color = c.fg3)
                    TfText(it, color = c.fg3, size = 13f)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (liked) c.rose.soft(0.16f) else c.card2)
                        .border(0.5.dp, if (liked) c.rose else c.sep, RoundedCornerShape(12.dp))
                        .then(pressScale({ store.toggleFavourite(entry.id) }, 0.97f))
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TfIcon("heart", size = 16, color = if (liked) c.rose else c.fg2)
                    Spacer(Modifier.width(8.dp))
                    TfText(
                        if (liked) "Favourited" else "Favourite",
                        color = if (liked) c.rose else c.fg2, size = 14f, weight = FontWeight.SemiBold,
                    )
                }
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .border(0.5.dp, c.line, RoundedCornerShape(12.dp))
                        .then(pressScale({ store.delete(entry.id); onBack() }, 0.92f)),
                    contentAlignment = Alignment.Center,
                ) { TfIcon("trash", size = 17, color = c.fg3) }
            }
        }
    }
}

// ── SETTINGS ─────────────────────────────────────────────────────────────────

@Composable
private fun GratSettingsScreen(store: GratitudeStore, onBack: () -> Unit) {
    val c = LocalTf.current
    val accent = c.green
    val settings = store.settings

    Column(Modifier.fillMaxSize()) {
        GTop(onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = GPAD.dp),
        ) {
            TfText("Gratitude settings", color = c.fg, size = 28f, weight = FontWeight.Bold, family = Fonts.display)

            GOverline("Daily reminder", Modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)
                    .border(0.5.dp, c.sep, RoundedCornerShape(16.dp)).padding(15.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REMINDER_SLOTS.forEach { (key, icon, label) ->
                        val on = settings.reminderSlot == key
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (on) accent.soft(0.14f) else c.card2)
                                .border(1.dp, if (on) accent else c.sep, RoundedCornerShape(12.dp))
                                .then(pressScale({ store.saveSettings(settings.copy(reminderSlot = key)) }, 0.95f))
                                .padding(horizontal = 6.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            TfIcon(icon, size = 19, color = if (on) accent else c.fg2)
                            TfText(label, color = if (on) c.fg else c.fg2, size = 12f, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TfText("Remind me at", color = c.fg2, size = 13.5f)
                    Box(
                        Modifier.clip(RoundedCornerShape(9.dp)).background(c.card2)
                            .border(0.5.dp, c.line, RoundedCornerShape(9.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { TfText(settings.reminderTime, color = c.fg, size = 14f, family = Fonts.mono) }
                }
                TfText(
                    "\"What is one good thing from today?\" — gentle, never guilt.",
                    color = c.fg3, size = 12f, lineHeight = 1.5f, modifier = Modifier.padding(top = 11.dp),
                )
            }

            GOverline("Privacy", Modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)
                    .border(0.5.dp, c.sep, RoundedCornerShape(16.dp)),
            ) {
                GToggleRow("faceId", accent, "Unlock with Face ID", null, settings.faceId) {
                    store.saveSettings(settings.copy(faceId = it))
                }
                GRowSeparator()
                GToggleRow("lock", accent, "App lock", "Require Face ID on every open", settings.appLock) {
                    store.saveSettings(settings.copy(appLock = it))
                }
                GRowSeparator()
                GToggleRow("bell", accent, "Hide preview in notifications", null, settings.hidePreview) {
                    store.saveSettings(settings.copy(hidePreview = it))
                }
                GRowSeparator()
                GToggleRow("shieldCheck", accent, "Cloud sync", "Encrypted, private to you", settings.cloudSync) {
                    store.saveSettings(settings.copy(cloudSync = it))
                }
            }
            TfText(
                "Your gratitude journal belongs to you. Stored on-device first, never shared to any feed.",
                color = c.fg3, size = 12.5f, lineHeight = 1.5f,
                modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp),
            )

            GOverline("Export", Modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp))
            GFlowRow(listOf("PDF", "JSON", "CSV", "Plain text"), perRow = 2) { format ->
                Row(
                    Modifier.clip(RoundedCornerShape(11.dp)).background(c.card)
                        .border(0.5.dp, c.sep, RoundedCornerShape(11.dp))
                        .padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    TfIcon("download", size = 15, color = c.fg2)
                    TfText(format, color = c.fg, size = 13.5f)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun GToggleRow(icon: String, color: Color, title: String, subtitle: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        IconBadge(icon, color, size = 34, iconSize = 18)
        Column(Modifier.weight(1f)) {
            TfText(title, color = c.fg, size = 14.5f)
            if (subtitle != null) TfText(subtitle, color = c.fg3, size = 12f, modifier = Modifier.padding(top = 1.dp))
        }
        Box(
            Modifier.size(46.dp, 28.dp).clip(RoundedCornerShape(999.dp))
                .background(if (on) color else Color(150 / 255f, 160 / 255f, 190 / 255f, 0.24f))
                .then(pressScale({ onChange(!on) }, 0.94f)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.padding(horizontal = 3.dp).size(22.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun GRowSeparator() {
    val c = LocalTf.current
    Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(0.5.dp).background(c.sep))
}
