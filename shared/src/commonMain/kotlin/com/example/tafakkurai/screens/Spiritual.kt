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
import com.example.tafakkurai.prayer.PrayerMark
import com.example.tafakkurai.prayer.PrayerMarkStore
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
@Composable
fun PrayerTrackerScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val trackPrayer = remember { getKoin().get<TrackPrayerUseCase>() }
    // Shared, persisted marks — same store the hero ring and Today card read/write.
    val markStore = remember { getKoin().get<PrayerMarkStore>() }
    val prayed = TPRAYER_TODAY.count { markStore.stateOf(it.key) != PrayerMark.NONE }
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
                TPRAYER_TODAY.forEach { p ->
                    val mark = markStore.stateOf(p.key)
                    val meta = PRAYER_STATES.getValue(mark.id)
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
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(meta.color.soft(0.16f))
                                    .then(pressScale({ markStore.cycle(p.key); scope.launch { runCatching { trackPrayer(p.key) } } }, 0.94f))
                                    .padding(horizontal = 13.dp, vertical = 7.dp),
                            ) { TfText(if (mark == PrayerMark.NONE) "Log" else meta.label, color = meta.color, size = 13f, weight = FontWeight.SemiBold) }
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
// Moved to screens/Gratitude.kt — the feature now has its own module (home,
// composer, history/calendar, search, jar, journeys, insights, monthly, settings)
// backed by GratitudeStore and the /v1/gratitude API.

// ── Qurʼan & dhikr ───────────────────────────────────────────────────────────
// Moved to screens/QuranDhikr.kt — the feature now has its own three tabs
// (Qurʼan / Dhikr / Adhkar), a surah list, an ayah reader and audio playback.

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
