package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tafakkurai.*
import com.example.tafakkurai.prayer.PrayerProgressViewModel
import com.example.tafakkurai.prayer.rememberPrayerTimes
import com.example.tafakkurai.ui.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.mp.KoinPlatform.getKoin
import uz.tafakkur.domain.usecase.CreateTrackerEntryUseCase
import uz.tafakkur.domain.usecase.TrackPrayerUseCase

// ── Prayer tracker ───────────────────────────────────────────────────────────
private val PRAYER_CYCLE = listOf("upcoming", "ontime", "jamaat")

@Composable
fun PrayerTrackerScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val trackPrayer = remember { getKoin().get<TrackPrayerUseCase>() }
    val states = remember { mutableStateListOf<PrayerToday>().apply { addAll(TPRAYER_TODAY) } }
    val prayed = states.count { it.state != "upcoming" }
    // Times + countdown come from the device location (fallback list until a fix lands).
    val times = rememberPrayerTimes()
    val prayerVm: PrayerProgressViewModel = viewModel { PrayerProgressViewModel(getKoin().get()) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Prayer", onBack)
        ScrollBody {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.Center) {
                    AmbientGlow(c.gold, opacity = 0.18f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        TfText("الصلاة", color = c.gold, size = 40f, family = Fonts.serif)
                        TfText("$prayed of 5 prayed", color = c.fg, size = 20f, weight = FontWeight.Bold, family = Fonts.display)
                        Pill(
                            "Next · ${prayerVm.state.nextName} in ${prayerVm.state.countdown}",
                            c.accent, c.accent.soft(0.16f),
                        )
                        TfText("$THIJRI · $TCITY", color = c.fg3, size = 12.5f)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${TPRAYER_STATS.streak}", "day streak", c.warn)
                StatTile("${TPRAYER_STATS.jamaat}%", "jamaat", c.green)
                StatTile("${TPRAYER_STATS.onTime}%", "on-time", c.accent)
            }
            Overline("TODAY · TAP TO LOG", color = c.fg3)
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                states.forEachIndexed { i, p ->
                    val meta = PRAYER_STATES.getValue(p.state)
                    Card(Modifier.fillMaxWidth(), pad = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(meta.color.soft(0.14f)), contentAlignment = Alignment.Center) {
                                TfIcon(meta.icon, size = 20, color = meta.color)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TfText(p.name, color = c.fg, size = 15.5f, weight = FontWeight.SemiBold)
                                    TfText(p.arabic, color = c.gold, size = 15f, family = Fonts.serif)
                                }
                                TfText(times[p.key] ?: p.time, color = c.fg3, size = 12.5f, family = Fonts.mono)
                            }
                            val nextState = PRAYER_CYCLE[(PRAYER_CYCLE.indexOf(p.state).coerceAtLeast(0) + 1) % PRAYER_CYCLE.size]
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(meta.color.soft(0.16f))
                                    .then(pressScale({ states[i] = p.copy(state = nextState); scope.launch { runCatching { trackPrayer(p.name.lowercase()) } } }, 0.94f))
                                    .padding(horizontal = 13.dp, vertical = 7.dp),
                            ) { TfText(if (p.state == "upcoming") "Log" else meta.label, color = meta.color, size = 13f, weight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            ProactiveCard(
                "You pray Fajr in jamaat 6 days a week, mashaʼAllah. Asr is your most-missed — want a reminder 15 minutes before?",
                action = "Set reminder", modifier = Modifier.padding(horizontal = HPAD.dp),
            )
            PrivacyFooter("Reflective and educational — never a ruling. Times use your calculation method.")
        }
    }
}

// ── Tafakkur journal ─────────────────────────────────────────────────────────
private val JOURNAL_MOODS = listOf("Calm", "Grateful", "Hopeful", "Tested")

@Composable
fun TafakkurJournalScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val createEntry = remember { getKoin().get<CreateTrackerEntryUseCase>() }
    var promptIdx by remember { mutableStateOf(0) }
    var mood by remember { mutableStateOf("Calm") }
    var entry by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Tafakkur", onBack)
        ScrollBody {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.Center) {
                    AmbientGlow(c.accent, opacity = 0.16f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                        BreathOrb(size = 80, color = c.accent, purple = c.purple)
                        Overline("REFLECT · TAKE A BREATH")
                        TfText(TPROMPTS[promptIdx], color = c.fg, size = 22f, family = Fonts.serif, align = TextAlign.Center, lineHeight = 1.3f, modifier = Modifier.padding(horizontal = 28.dp))
                        Box(Modifier.then(pressScale({ promptIdx = (promptIdx + 1) % TPROMPTS.size }, 0.95f)).clip(RoundedCornerShape(999.dp)).border(0.5.dp, c.line, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TfIcon("repeat", size = 13, color = c.fg2)
                                TfText("Another prompt", color = c.fg2, size = 13f)
                            }
                        }
                    }
                }
            }
            // write card
            InsetCard(pad = 16) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    JOURNAL_MOODS.forEach { m ->
                        val on = mood == m
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) c.accent.soft(0.18f) else c.card3)
                                .then(pressScale({ mood = m }, 0.95f)).padding(horizontal = 12.dp, vertical = 7.dp),
                        ) { TfText(m, color = if (on) c.accent else c.fg2, size = 12.5f, weight = FontWeight.Medium) }
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                    if (entry.isEmpty()) TfText("Write freely. No one sees this but you and Allah.", color = c.fg3, size = 16f, family = Fonts.serif, lineHeight = 1.5f)
                    BasicTextField(
                        value = entry, onValueChange = { entry = it },
                        textStyle = TextStyle(color = c.fg, fontSize = 16.sp, fontFamily = Fonts.serif, lineHeight = 25.sp),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.card3).then(pressScale({}, 0.9f)), contentAlignment = Alignment.Center) {
                        TfIcon("mic", size = 19, color = c.fg2)
                    }
                    Box(Modifier.weight(1f)) { PrimaryButton("Save entry") {
                        val text = entry.trim()
                        if (text.isNotEmpty()) scope.launch { runCatching { createEntry("journal", buildJsonObject { put("mood", mood); put("prompt", TPROMPTS[promptIdx]); put("text", text) }.toString()) } }
                        entry = ""
                    } }
                }
            }
            ProactiveCard("Yesterday you wrote about patience in a code review. A gentle practice today: pause for one breath before you reply to anything.", modifier = Modifier.padding(horizontal = HPAD.dp))
            // history
            SectionTitle("Your reflections")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TJOURNAL.forEach { j -> JournalCard(j) }
            }
            PrivacyFooter()
        }
    }
}

@Composable
private fun JournalCard(j: JournalEntry) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TfText(j.date, color = c.fg3, size = 12f, family = Fonts.mono, modifier = Modifier.weight(1f))
            Pill(j.mood, c.gold, c.gold.soft(0.16f))
        }
        TfText(j.title, color = c.fg, size = 18f, family = Fonts.serif, modifier = Modifier.padding(top = 8.dp))
        TfText(j.excerpt, color = c.fg2, size = 13.5f, lineHeight = 1.55f, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            j.gratitude.take(3).forEach { g -> Pill(g, c.green, c.green.soft(0.14f), leading = { TfIcon("leaf", size = 10, color = c.green) }) }
        }
    }
}

// ── Gratitude / Shukr ────────────────────────────────────────────────────────
@Composable
fun GratitudeScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val createEntry = remember { getKoin().get<CreateTrackerEntryUseCase>() }
    val items = remember { mutableStateListOf<String>().apply { addAll(TGRATITUDE_TODAY) } }
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Shukr", onBack)
        ScrollBody {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TfText("Shukr · gratitude", color = c.fg, size = 26f, family = Fonts.serif)
                TfText("Name three things you're grateful for today. Small is fine — small is the point.", color = c.fg2, size = 13.5f, lineHeight = 1.5f)
            }
            InsetCard {
                Overline("TODAY · ${items.size} of 3")
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEachIndexed { i, g ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(c.green.soft(0.16f)), contentAlignment = Alignment.Center) { TfIcon("leaf", size = 14, color = c.green) }
                            TfText(g, color = c.fg, size = 15f, modifier = Modifier.weight(1f))
                            Box(Modifier.then(pressScale({ items.removeAt(i) }, 0.9f)).padding(4.dp)) { TfIcon("x", size = 15, color = c.fg3) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(c.card3).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (draft.isEmpty()) TfText("Add something…", color = c.fg3, size = 14.5f)
                            BasicTextField(
                                value = draft, onValueChange = { draft = it }, singleLine = true,
                                textStyle = TextStyle(color = c.fg, fontSize = 14.5.sp, fontFamily = Fonts.sans),
                                cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Box(Modifier.size(32.dp).clip(CircleShape).background(c.green).then(pressScale({ if (draft.isNotBlank()) { val g = draft.trim(); items.add(g); draft = ""; scope.launch { runCatching { createEntry("gratitude", buildJsonObject { put("item", g) }.toString()) } } } }, 0.9f)), contentAlignment = Alignment.Center) {
                            TfIcon("plus", size = 17, color = Color.White)
                        }
                    }
                }
            }
            SectionTitle("This week")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                TJOURNAL.forEach { j ->
                    Card(Modifier.fillMaxWidth(), pad = 14) {
                        TfText(j.date, color = c.fg3, size = 11.5f, family = Fonts.mono)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            j.gratitude.forEach { g -> Pill(g, c.green, c.green.soft(0.14f), leading = { TfIcon("leaf", size = 10, color = c.green) }) }
                        }
                    }
                }
            }
            PrivacyFooter()
        }
    }
}

// ── Qurʼan & dhikr ───────────────────────────────────────────────────────────
@Composable
fun QuranDhikrScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    var tab by remember { mutableStateOf("quran") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Qurʼan & dhikr", onBack)
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 10.dp)) {
            Segmented(listOf("quran" to "Qurʼan", "dhikr" to "Dhikr"), tab, { tab = it })
        }
        if (tab == "quran") QuranTab() else DhikrTab()
    }
}

@Composable
private fun ColumnScope.QuranTab() {
    val c = LocalTf.current
    ScrollBody {
        InsetCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Overline("CONTINUE READING")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        TfText(TQURAN_LAST.surah, color = c.fg, size = 18f, weight = FontWeight.SemiBold)
                        TfText(TQURAN_LAST.arabic, color = c.gold, size = 18f, family = Fonts.serif)
                    }
                    TfText("Ayah ${TQURAN_LAST.ayah} · Juz ${TQURAN_LAST.juz}", color = c.fg2, size = 12.5f, modifier = Modifier.padding(top = 2.dp))
                    ProgressBar(TQURAN_LAST.pct, c.gold, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Box(Modifier.size(46.dp).clip(CircleShape).background(c.gold), contentAlignment = Alignment.Center) { TfIcon("play", size = 20, color = Color.White) }
            }
        }
        InsetCard(pad = 18) {
            Overline("VERSE OF THE DAY")
            TfText(TQURAN_VERSE.arabic, color = c.fg, size = 26f, family = Fonts.serif, align = TextAlign.End, lineHeight = 1.6f, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            TfText(TQURAN_VERSE.meaning, color = c.fg2, size = 14.5f, lineHeight = 1.6f, modifier = Modifier.padding(top = 12.dp))
            TfText(TQURAN_VERSE.ref, color = c.gold, size = 12.5f, family = Fonts.mono, modifier = Modifier.padding(top = 8.dp))
        }
        SectionTitle("Surahs")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TQURAN_SURAHS.forEach { s ->
                Card(Modifier.fillMaxWidth(), pad = 13) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.gold.soft(0.14f)), contentAlignment = Alignment.Center) {
                            TfText("${s.no}", color = c.gold, size = 14f, weight = FontWeight.Bold, family = Fonts.mono)
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                TfText(s.name, color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                                TfText(s.arabic, color = c.gold, size = 15f, family = Fonts.serif)
                            }
                            TfText("${s.meaning} · ${s.ayahs} ayahs · ${s.place}", color = c.fg3, size = 11.5f)
                        }
                        if (s.read >= 1f) TfIcon("checkCircle", size = 18, color = c.green)
                        else if (s.read > 0f) TfText("${(s.read * 100).toInt()}%", color = c.fg3, size = 11.5f, family = Fonts.mono)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DhikrTab() {
    val c = LocalTf.current
    val target = 33
    var count by remember { mutableStateOf(0) }
    ScrollBody {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                AmbientGlow(c.green, opacity = 0.16f)
                RingGauge(count.toFloat() / target, c.green, c.card3, strokeWidth = 8f, size = 220,
                    modifier = Modifier.then(pressScale({ if (count < target) count++ }, 0.97f))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TfText("سُبْحَانَ ٱللَّٰه", color = c.gold, size = 22f, family = Fonts.serif)
                        TfText("$count", color = c.fg, size = 54f, weight = FontWeight.Bold, family = Fonts.mono)
                        TfText("of $target · tap", color = c.fg3, size = 12f)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.clip(RoundedCornerShape(999.dp)).border(0.5.dp, c.line, RoundedCornerShape(999.dp)).then(pressScale({ count = 0 }, 0.95f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { TfIcon("repeat", size = 14, color = c.fg2); TfText("Reset", color = c.fg2, size = 13f) }
            }
        }
        SectionTitle("Adhkar")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TDHIKR.forEach { d ->
                Card(Modifier.fillMaxWidth(), pad = 14) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            TfText(d.arabic, color = c.gold, size = 18f, family = Fonts.serif)
                            TfText("${d.translit} · ${d.meaning}", color = c.fg2, size = 12f, modifier = Modifier.padding(top = 3.dp))
                        }
                        TfText("${d.count}/${d.target}", color = d.color, size = 14f, family = Fonts.mono, weight = FontWeight.SemiBold)
                    }
                    ProgressBar(d.count.toFloat() / d.target, d.color, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
        }
    }
}

// ── Reflect (module screen) ──────────────────────────────────────────────────
@Composable
fun ReflectScreen(serif: Boolean) {
    val c = LocalTf.current
    var promptIdx by remember { mutableStateOf(0) }
    var entry by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        LargeTitle("Reflect", serif = serif)
        ScrollBody {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BreathOrb(size = 72, color = c.accent, purple = c.purple)
                    TfText(TPROMPTS[promptIdx], color = c.fg, size = 21f, family = Fonts.serif, align = TextAlign.Center, lineHeight = 1.3f, modifier = Modifier.padding(horizontal = 28.dp))
                }
            }
            InsetCard {
                Box(Modifier.fillMaxWidth().heightIn(min = 100.dp)) {
                    if (entry.isEmpty()) TfText("Take a breath, then write…", color = c.fg3, size = 15f, family = Fonts.serif)
                    BasicTextField(
                        value = entry, onValueChange = { entry = it },
                        textStyle = TextStyle(color = c.fg, fontSize = 15.sp, fontFamily = Fonts.serif, lineHeight = 24.sp),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(Modifier.padding(top = 10.dp)) { PrimaryButton("Save & shuffle") { entry = ""; promptIdx = (promptIdx + 1) % TPROMPTS.size } }
            }
            SectionTitle("Recent")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TJOURNAL.forEach { j -> JournalCard(j) }
            }
            PrivacyFooter()
        }
    }
}
