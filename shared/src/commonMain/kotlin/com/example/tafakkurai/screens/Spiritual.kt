package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tafakkurai.*
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.journal.presentation.JournalViewModel
import com.example.tafakkurai.prayer.PrayerMark
import com.example.tafakkurai.prayer.PrayerMarkStore
import com.example.tafakkurai.prayer.PrayerProgressViewModel
import com.example.tafakkurai.prayer.localizedPrayerCountdown
import com.example.tafakkurai.prayer.localizedPrayerName
import com.example.tafakkurai.prayer.rememberPrayerTimes
import com.example.tafakkurai.ui.*
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import uz.tafakkur.domain.usecase.TrackPrayerUseCase

// ── Prayer tracker ───────────────────────────────────────────────────────────
@Composable
fun PrayerTrackerScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val trackPrayer = remember { getKoin().get<TrackPrayerUseCase>() }
    // Shared, persisted marks — same store the hero ring and Today card read/write.
    val markStore = remember { getKoin().get<PrayerMarkStore>() }
    val prayed = PRAYER_TRACKER_ROWS.count { markStore.stateOf(it.key) != PrayerMark.NONE }
    val jamaat = PRAYER_TRACKER_ROWS.count { markStore.stateOf(it.key) == PrayerMark.JAMAAT }
    val onTime = PRAYER_TRACKER_ROWS.count { markStore.stateOf(it.key) == PrayerMark.ONTIME }
    // Times + countdown come from the device location (fallback list until a fix lands).
    val times = rememberPrayerTimes()
    val prayerVm: PrayerProgressViewModel = viewModel { PrayerProgressViewModel(getKoin().get()) }
    val hasSchedule = prayerVm.state.nextKey.isNotBlank()
    val nextPrayer = if (hasSchedule) localizedPrayerName(prayerVm.state.nextKey) else tr("prayer.time.unavailable")
    val countdown = if (hasSchedule) localizedPrayerCountdown(prayerVm.state.minutesToNext) else tr("prayer.location.required_short")
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("prayer.title"), onBack)
        ScrollBody {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.Center) {
                    AmbientGlow(c.gold, opacity = 0.18f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        TfText("الصلاة", color = c.gold, size = 40f, family = Fonts.serif)
                        TfText(
                            tr("prayer.progress", prayed),
                            color = c.fg, size = 20f, weight = FontWeight.Bold, family = Fonts.display,
                        )
                        Pill(
                            tr("prayer.next", nextPrayer, countdown),
                            c.accent, c.accent.soft(0.16f),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("$prayed", tr("prayer.stat.recorded"), c.warn)
                StatTile("$jamaat", tr("prayer.stat.jamaat"), c.green)
                StatTile("$onTime", tr("prayer.stat.ontime"), c.accent)
            }
            Overline(tr("prayer.today.overline"), color = c.fg3)
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRAYER_TRACKER_ROWS.forEach { p ->
                    val mark = markStore.stateOf(p.key)
                    val meta = PRAYER_STATES.getValue(mark.id)
                    Card(Modifier.fillMaxWidth(), pad = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(meta.color.soft(0.14f)), contentAlignment = Alignment.Center) {
                                TfIcon(meta.icon, size = 20, color = meta.color)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TfText(localizedPrayerName(p.key), color = c.fg, size = 15.5f, weight = FontWeight.SemiBold)
                                    TfText(p.arabic, color = c.gold, size = 15f, family = Fonts.serif)
                                }
                                TfText(times[p.key] ?: tr("prayer.time.unavailable"), color = c.fg3, size = 12.5f, family = Fonts.mono)
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(meta.color.soft(0.16f))
                                    .then(pressScale({ markStore.cycle(p.key); scope.launch { runCatching { trackPrayer(p.key) } } }, 0.94f))
                                    .padding(horizontal = 13.dp, vertical = 7.dp),
                            ) {
                                TfText(
                                    if (mark == PrayerMark.NONE) tr("prayer.action.log")
                                    else tr("prayer.state.${mark.id}"),
                                    color = meta.color, size = 13f, weight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            ProactiveCard(
                if (prayed == PRAYER_TRACKER_ROWS.size) tr("prayer.insight.complete")
                else tr("prayer.insight.remaining", PRAYER_TRACKER_ROWS.size - prayed),
                modifier = Modifier.padding(horizontal = HPAD.dp),
            )
            PrivacyFooter(tr("prayer.privacy"))
        }
    }
}

// ── Tafakkur Journal ────────────────────────────────────────────────────────
@Composable
fun TafakkurJournalScreen(viewModel: JournalViewModel, onBack: () -> Unit) {
    JournalScreen(viewModel = viewModel, onBack = onBack)
}

// ── Gratitude / Shukr ────────────────────────────────────────────────────────
// Moved to screens/Gratitude.kt — the feature now has its own module (home,
// composer, history/calendar, search, jar, journeys, insights, monthly, settings)
// backed by GratitudeStore and the /v1/gratitude API.

// ── Qurʼan & dhikr ───────────────────────────────────────────────────────────
// Moved to screens/QuranDhikr.kt — the feature now has its own three tabs
// (Qurʼan / Dhikr / Adhkar), a surah list, an ayah reader and audio playback.

// ── Reflect (legacy module route) ───────────────────────────────────────────
// It deliberately shares the same ViewModel/source of truth instead of maintaining a
// second editor that loses the user's writing.
@Composable
fun ReflectScreen(viewModel: JournalViewModel, onBack: () -> Unit, serif: Boolean) {
    JournalScreen(viewModel = viewModel, onBack = onBack, serifTitle = serif)
}
