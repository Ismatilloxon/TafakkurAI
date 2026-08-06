package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tafakkurai.flashcards.Rating
import com.example.tafakkurai.quran.SURAH_CATALOG
import com.example.tafakkurai.quran.Surah
import com.example.tafakkurai.quran.hifz.HifzCardUi
import com.example.tafakkurai.quran.hifz.HifzSessionUiState
import com.example.tafakkurai.quran.hifz.HifzSessionViewModel
import com.example.tafakkurai.quran.hifz.HifzSurahRow
import com.example.tafakkurai.quran.hifz.HifzViewModel
import com.example.tafakkurai.ui.*
import org.koin.mp.KoinPlatform.getKoin

// ─────────────────────────────────────────────────────────────────────────────
// Hifz — memorization.
//
// Three screens: the home (what is owed today, what has been taken on), the setup
// (choosing a run of ayahs and a pace), and the session (prompt, recite, reveal, grade).
//
// No ayah text is stored anywhere in this feature. Everything on the page is resolved
// through QuranRepository as it is drawn, so the mushaf and tarjima chosen in the reader
// are the ones that appear here too.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun hifzViewModel(): HifzViewModel = viewModel {
    HifzViewModel(getKoin().get(), getKoin().get(), getKoin().get(), getKoin().get())
}

// ── Home ─────────────────────────────────────────────────────────────────────

@Composable
fun HifzHomeScreen(
    onBack: () -> Unit,
    onSetup: () -> Unit,
    onReview: () -> Unit,
    onOpenSurah: (Int) -> Unit,
) {
    val c = LocalTf.current
    val vm = hifzViewModel()
    // Recompute on every entry: a session just finished changes every number here.
    LaunchedEffect(Unit) { vm.refresh() }
    val stats = vm.stats
    val plan = vm.plan

    Column(Modifier.fillMaxSize()) {
        BackBar("Hifz · Yodlash", onBack)
        ScrollBody {
            if (!stats.hasCards) {
                EmptyHifz(onSetup)
                return@ScrollBody
            }

            TodayCard(
                due = plan.review.size,
                fresh = plan.fresh.size,
                reciter = vm.reciterName,
                onReview = onReview,
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile("${stats.memorized}", "Memorized", c.green)
                StatTile("${stats.total}", "Enrolled")
                StatTile("${stats.streakDays}", "Day streak", c.warn)
            }

            if (stats.reviewedToday > 0) {
                InsetCard(pad = 13) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        TfIcon("checkCircle", size = 16, color = c.green)
                        TfText(
                            "${stats.reviewedToday} reviewed today",
                            color = c.fg2, size = 13.5f,
                        )
                    }
                }
            }

            SectionTitle("Your surahs")
            Column(
                Modifier.padding(horizontal = HPAD.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                vm.rows.forEach { row ->
                    SurahHifzRow(
                        row,
                        onOpen = { onOpenSurah(row.surah.number) },
                        onDrop = { vm.drop(row.surah.number) },
                    )
                }
                AddDashed("Add a surah or range", onSetup)
            }

            SectionTitle("Pace")
            PaceCard(vm)
        }
    }
}

@Composable
private fun EmptyHifz(onSetup: () -> Unit) {
    val c = LocalTf.current
    InsetCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BreathOrb(size = 56, color = c.gold, purple = c.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Overline("HIFZ · YODLASH")
                TfText("Start memorizing", color = c.fg, size = 16.5f, weight = FontWeight.SemiBold)
            }
        }
        TfText(
            "Choose a surah — or a run of ayahs inside one — and a pace you can keep. " +
                "Each ayah comes back for review just before you'd forget it, and its recitation " +
                "downloads so a session works with no connection.",
            color = c.fg2, size = 13.5f, lineHeight = 1.55f, modifier = Modifier.padding(top = 12.dp),
        )
    }
    Box(Modifier.padding(horizontal = HPAD.dp)) {
        PrimaryButton("Choose a surah", onClick = onSetup)
    }
}

@Composable
private fun TodayCard(due: Int, fresh: Int, reciter: String, onReview: () -> Unit) {
    val c = LocalTf.current
    InsetCard {
        Overline("TODAY")
        Row(
            Modifier.padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("$due to review", c.accent, c.accent.soft(0.16f))
            Pill("$fresh new", c.gold, c.gold.soft(0.16f))
        }
        TfText(
            if (due + fresh == 0) {
                "Nothing due. Everything you've taken on is resting — the next review lands when it lands."
            } else {
                "Reviews first, then new ayahs. In $reciter's voice."
            },
            color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 11.dp),
        )
        if (due + fresh > 0) {
            Box(Modifier.padding(top = 13.dp)) {
                PrimaryButton("Start · ${due + fresh} ayahs", onClick = onReview)
            }
        }
    }
}

@Composable
private fun SurahHifzRow(row: HifzSurahRow, onOpen: () -> Unit, onDrop: () -> Unit) {
    val c = LocalTf.current
    val p = row.progress
    // The row opens the surah in the reader; the x is the only part that doesn't.
    Card(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(c.gold.soft(0.14f)),
                contentAlignment = Alignment.Center,
            ) { TfText("${row.surah.number}", color = c.gold, size = 15f, family = Fonts.mono) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TfText(row.surah.nameEnglish, color = c.fg, size = 15.5f, weight = FontWeight.SemiBold)
                    TfText(row.surah.nameArabic, color = c.fg3, size = 13f, family = Fonts.serif)
                    Spacer(Modifier.weight(1f))
                    if (p.due > 0) Pill("${p.due} due", c.accent, c.accent.soft(0.14f))
                }
                TfText(
                    "${p.memorized} of ${p.total} memorized",
                    color = c.fg2, size = 12.5f,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressBar(p.fraction, c.green, modifier = Modifier.weight(1f))
                    TfText("${p.percent}%", color = c.fg2, size = 11.5f, family = Fonts.mono)
                }
            }
            Box(pressScale(onDrop, 0.85f)) { TfIcon("x", size = 16, color = c.fg3) }
        }
    }
}

@Composable
private fun PaceCard(vm: HifzViewModel) {
    val c = LocalTf.current
    InsetCard {
        Stepper(
            label = "New ayahs per day",
            hint = "Intake waits when the review load is already at its cap.",
            value = vm.planner.dailyNew,
            onChange = vm::setDailyNew,
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(0.5.dp).background(c.line))
        Stepper(
            label = "Reviews per session",
            hint = "A ceiling, so a backlog after a missed week still opens.",
            value = vm.planner.reviewCap,
            step = 5,
            onChange = vm::setReviewCap,
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(0.5.dp).background(c.line))
        Stepper(
            label = "Repeats per ayah",
            hint = "How many times the recitation loops when you reveal an ayah.",
            value = vm.planner.repeatCount,
            onChange = vm::setRepeatCount,
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    hint: String,
    value: Int,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    val c = LocalTf.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TfText(label, color = c.fg, size = 14.5f, weight = FontWeight.Medium)
            TfText(hint, color = c.fg3, size = 11.5f, lineHeight = 1.4f)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton("chevronDown") { onChange(value - step) }
            TfText("$value", color = c.fg, size = 16f, family = Fonts.mono)
            StepButton("chevronUp") { onChange(value + step) }
        }
    }
}

@Composable
private fun StepButton(icon: String, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(c.card2)
            .border(0.5.dp, c.line, CircleShape).then(pressScale(onClick, 0.88f)),
        contentAlignment = Alignment.Center,
    ) { TfIcon(icon, size = 14, color = c.fg2) }
}

// ── Setup ────────────────────────────────────────────────────────────────────

@Composable
fun HifzSetupScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val c = LocalTf.current
    val vm = hifzViewModel()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Surah?>(null) }
    var from by remember { mutableStateOf(1) }
    var to by remember { mutableStateOf(1) }

    val matches = remember(query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) SURAH_CATALOG
        else SURAH_CATALOG.filter {
            it.nameEnglish.lowercase().contains(q) ||
                it.nameTranslation.lowercase().contains(q) ||
                it.number.toString() == q
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Add to hifz", onBack)
        ScrollBody {
            val surah = picked
            if (surah == null) {
                InsetCard(pad = 13) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        TfIcon("search", size = 16, color = c.fg3)
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) TfText("Search surah…", color = c.fg3, size = 15f)
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = TextStyle(color = c.fg, fontSize = 15.sp, fontFamily = Fonts.sans),
                                cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Column(
                    Modifier.padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    matches.take(SURAH_PICKER_LIMIT).forEach { s ->
                        SurahPickRow(s) {
                            picked = s
                            from = 1
                            to = s.ayahCount
                        }
                    }
                    if (matches.size > SURAH_PICKER_LIMIT) {
                        TfText(
                            "${matches.size - SURAH_PICKER_LIMIT} more — keep typing to narrow it down.",
                            color = c.fg3, size = 12.5f, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            } else {
                val count = vm.ayahCount(surah.number)
                InsetCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            TfText(surah.nameEnglish, color = c.fg, size = 18f, weight = FontWeight.SemiBold)
                            TfText(
                                "${surah.nameTranslation} · $count ayahs",
                                color = c.fg2, size = 12.5f,
                            )
                        }
                        TfText(surah.nameArabic, color = c.gold, size = 24f, family = Fonts.serif)
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(0.5.dp).background(c.line))
                    Stepper(
                        label = "From ayah",
                        hint = "Where the run starts.",
                        value = from,
                        onChange = { from = it.coerceIn(1, count) },
                    )
                    Box(Modifier.padding(top = 12.dp)) {
                        Stepper(
                            label = "To ayah",
                            hint = "Where it ends. The whole surah by default.",
                            value = to,
                            onChange = { to = it.coerceIn(1, count) },
                        )
                    }
                }
                val span = (maxOf(from, to) - minOf(from, to) + 1)
                InsetCard(pad = 13) {
                    TfText(
                        "$span ${if (span == 1) "ayah" else "ayahs"} · about " +
                            "${daysFor(span, vm.planner.dailyNew)} at ${vm.planner.dailyNew} a day",
                        color = c.fg, size = 14f, weight = FontWeight.Medium,
                    )
                    TfText(
                        "Ayahs already under memorization keep their schedule — adding an " +
                            "overlapping range never resets progress.",
                        color = c.fg3, size = 12f, lineHeight = 1.45f, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(
                    Modifier.padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    PrimaryButton("Add to hifz") {
                        vm.enrol(surah.number, from, to)
                        onDone()
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .border(0.5.dp, c.line, RoundedCornerShape(12.dp))
                            .then(pressScale({ picked = null }, 0.98f))
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { TfText("Choose a different surah", color = c.fg2, size = 14f, weight = FontWeight.Medium) }
                }
            }
        }
    }
}

@Composable
private fun SurahPickRow(s: Surah, onClick: () -> Unit) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth(), pad = 13, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.card2),
                contentAlignment = Alignment.Center,
            ) { TfText("${s.number}", color = c.fg3, size = 12.5f, family = Fonts.mono) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TfText(s.nameEnglish, color = c.fg, size = 15f, weight = FontWeight.Medium)
                TfText("${s.nameTranslation} · ${s.ayahCount} ayahs", color = c.fg3, size = 11.5f)
            }
            TfText(s.nameArabic, color = c.gold, size = 17f, family = Fonts.serif)
        }
    }
}

/** Rough calendar span for a run at a given daily intake — a sentence, not a promise. */
private fun daysFor(ayahs: Int, perDay: Int): String {
    val days = if (perDay <= 0) ayahs else (ayahs + perDay - 1) / perDay
    return when {
        days <= 1 -> "a day"
        days < 14 -> "$days days"
        days < 60 -> "${(days + 3) / 7} weeks"
        else -> "${(days + 15) / 30} months"
    }
}

private const val SURAH_PICKER_LIMIT = 30

// ── Review session ───────────────────────────────────────────────────────────

@Composable
fun HifzReviewScreen(onBack: () -> Unit) {
    val vm: HifzSessionViewModel = viewModel {
        HifzSessionViewModel(
            getKoin().get(), getKoin().get(), getKoin().get(),
            getKoin().get(), getKoin().get(), getKoin().get(),
        )
    }
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        BackBar("Hifz review", onBack)
        when {
            state.total == 0 && !state.isLoading -> NothingDue(onBack)
            state.finished -> SessionResult(state, onBack)
            else -> ReviewBody(state, vm)
        }
    }
}

@Composable
private fun ColumnScope.NothingDue(onBack: () -> Unit) {
    val c = LocalTf.current
    ScrollBody {
        InsetCard {
            TfText("Nothing due right now", color = c.fg, size = 16f, weight = FontWeight.SemiBold)
            TfText(
                "Every ayah you've taken on is still resting. Coming back early doesn't help it " +
                    "stick — the schedule is what does the work.",
                color = c.fg2, size = 13.5f, lineHeight = 1.55f, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(Modifier.padding(horizontal = HPAD.dp)) { PrimaryButton("Back", onClick = onBack) }
    }
}

@Composable
private fun ColumnScope.ReviewBody(state: HifzSessionUiState, vm: HifzSessionViewModel) {
    val c = LocalTf.current
    val card = state.current

    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = HPAD.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProgressBar(state.progress, c.gold, modifier = Modifier.weight(1f))
            TfText(
                "${(state.index + 1).coerceAtMost(state.total)}/${state.total}",
                color = c.fg2, size = 12f, family = Fonts.mono,
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (card == null) {
                TfText("Loading…", color = c.fg3, size = 14f, modifier = Modifier.padding(top = 40.dp))
            } else {
                PromptCard(card, state)
                if (state.revealed) AnswerCard(card, state, vm)
            }
            if (state.error != null) {
                TfText(state.error, color = c.danger, size = 13f)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.revealed) {
                GradeBtn("Again", vm.previewLabel(Rating.AGAIN), c.danger, Modifier.weight(1f)) { vm.grade(Rating.AGAIN) }
                GradeBtn("Hard", vm.previewLabel(Rating.HARD), c.warn, Modifier.weight(1f)) { vm.grade(Rating.HARD) }
                GradeBtn("Good", vm.previewLabel(Rating.GOOD), c.green, Modifier.weight(1f)) { vm.grade(Rating.GOOD) }
                GradeBtn("Easy", vm.previewLabel(Rating.EASY), c.cyan, Modifier.weight(1f)) { vm.grade(Rating.EASY) }
            } else {
                Box(Modifier.weight(1f)) { PrimaryButton("Reveal the ayah") { vm.reveal() } }
            }
        }
    }
}

/**
 * The prompt: which ayah is being asked for, and the ayah immediately before it — the cue a
 * teacher gives. The ayah itself is deliberately absent until the attempt has been made.
 */
@Composable
private fun PromptCard(card: HifzCardUi, state: HifzSessionUiState) {
    val c = LocalTf.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)
            .border(0.5.dp, c.line, RoundedCornerShape(18.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("RECITE")
            Spacer(Modifier.weight(1f))
            if (card.card.state == 0) Pill("new", c.gold, c.gold.soft(0.16f))
        }
        TfText(
            "${card.surah?.nameEnglish ?: "Surah ${card.card.surah}"} · ayah ${card.card.ayah}",
            color = c.fg, size = 20f, family = Fonts.serif, lineHeight = 1.25f,
        )

        when {
            card.isFirstOfSurah -> TfText(
                "The opening ayah of the surah.",
                color = c.fg3, size = 13f, modifier = Modifier.padding(top = 2.dp),
            )
            card.cue?.hasText == true -> {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Overline("THE AYAH BEFORE")
                        TfText(
                            card.cue.arabicText!!,
                            color = c.fg2, size = 19f, family = Fonts.serif,
                            align = TextAlign.End, lineHeight = state.mushaf.lineHeight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            else -> TfText(
                "The preceding ayah hasn't been downloaded — recite from the reference alone.",
                color = c.fg3, size = 12.5f, lineHeight = 1.45f, modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (!state.revealed) {
            TfText(
                "Recite it from memory, then reveal.",
                color = c.fg3, size = 12.5f, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** The answer: the ayah as the reader would print it, plus its recitation on a loop. */
@Composable
private fun AnswerCard(card: HifzCardUi, state: HifzSessionUiState, vm: HifzSessionViewModel) {
    val c = LocalTf.current
    val ayah = card.ayah
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.gold.soft(0.07f))
            .border(0.5.dp, c.gold.soft(0.35f), RoundedCornerShape(18.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Overline("AYAH ${card.card.ayah}")
        if (ayah?.hasText == true) {
            TfText(
                ayah.arabicText!!,
                color = c.fg, size = 25f, family = Fonts.serif,
                align = TextAlign.End, lineHeight = state.mushaf.lineHeight,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.showTranslation && !ayah.translation.isNullOrBlank()) {
                TfText(ayah.translation, color = c.fg2, size = 14f, lineHeight = 1.6f)
            }
        } else {
            TfText("Text not downloaded", color = c.fg3, size = 13.5f)
            TfText(
                "The recitation still plays — this ayah's text isn't available offline yet.",
                color = c.fg3, size = 12f, lineHeight = 1.45f,
            )
        }

        // Recitation controls. The lap counter is the point: repeating one ayah is the drill.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(c.gold)
                    .then(pressScale({ vm.playPause() }, 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                TfIcon(if (state.audio.isPlaying) "pause" else "play", size = 18, color = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TfText(
                    if (state.audio.isLoading) "Loading recitation…"
                    else "Repeat ${(state.audio.repeatLap + 1).coerceAtMost(state.audio.repeatTarget)} of ${state.audio.repeatTarget}",
                    color = c.fg2, size = 12.5f, family = Fonts.mono,
                )
                ProgressBar(state.audio.progress, c.gold, height = 3, modifier = Modifier.fillMaxWidth())
            }
            Box(pressScale({ vm.replay() }, 0.88f)) {
                TfIcon("repeat", size = 18, color = c.fg2)
            }
        }
        if (state.audio.error != null) {
            TfText(state.audio.error, color = c.danger, size = 12f)
        }
    }
}

@Composable
private fun GradeBtn(label: String, interval: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(color.soft(0.16f))
            .border(0.5.dp, color.soft(0.4f), RoundedCornerShape(12.dp))
            .then(pressScale(onClick, 0.94f)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TfText(label, color = color, size = 13.5f, weight = FontWeight.SemiBold)
        TfText(interval, color = color, size = 10.5f, family = Fonts.mono, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun ColumnScope.SessionResult(state: HifzSessionUiState, onBack: () -> Unit) {
    val c = LocalTf.current
    ScrollBody {
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BreathOrb(size = 96, color = c.gold, purple = c.accent)
            TfText(
                "${state.reviewed} ${if (state.reviewed == 1) "ayah" else "ayahs"} reviewed",
                color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display,
            )
        }
        InsetCard {
            Overline("SESSION")
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Again ${state.counts[Rating.AGAIN] ?: 0}", c.danger, c.danger.soft(0.14f))
                Pill("Hard ${state.counts[Rating.HARD] ?: 0}", c.warn, c.warn.soft(0.14f))
                Pill("Good ${state.counts[Rating.GOOD] ?: 0}", c.green, c.green.soft(0.14f))
                Pill("Easy ${state.counts[Rating.EASY] ?: 0}", c.cyan, c.cyan.soft(0.14f))
            }
            TfText(
                "Each ayah is scheduled for the day you'd be about to forget it. Anything you " +
                    "rated Again came round a second time before the session ended.",
                color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 12.dp),
            )
        }
        Box(Modifier.padding(horizontal = HPAD.dp)) { PrimaryButton("Done", onClick = onBack) }
    }
}
