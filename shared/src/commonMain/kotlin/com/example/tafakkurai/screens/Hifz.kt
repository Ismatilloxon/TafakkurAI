package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tafakkurai.flashcards.Rating
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.quran.Surah
import com.example.tafakkurai.quran.hifz.HifzCardUi
import com.example.tafakkurai.quran.hifz.HifzSessionUiState
import com.example.tafakkurai.quran.hifz.HifzSessionViewModel
import com.example.tafakkurai.quran.hifz.HifzSurahRow
import com.example.tafakkurai.quran.hifz.HifzViewModel
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfAddButton
import com.example.tafakkurai.ui.components.TfConfirmationDialog
import com.example.tafakkurai.ui.components.TfEmptyState
import com.example.tafakkurai.ui.components.TfIconButton
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.ui.components.TfSearchField
import com.example.tafakkurai.ui.components.TfSettingsToggleRow
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
    var pendingDrop by remember { mutableStateOf<HifzSurahRow?>(null) }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("hifz.home.title"), onBack)
        if (!stats.hasCards) {
            ScrollBody {
                EmptyHifz(onSetup)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "today", contentType = "today") {
                    TodayCard(
                        due = plan.review.size,
                        fresh = plan.fresh.size,
                        reciter = vm.reciterName,
                        onReview = onReview,
                    )
                }
                item(key = "stats", contentType = "stats") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatTile(stats.memorized.toString(), tr("hifz.home.stat.memorized"), c.green)
                        StatTile(stats.total.toString(), tr("hifz.home.stat.enrolled"))
                        StatTile(stats.streakDays.toString(), tr("hifz.home.stat.streak"), c.warn)
                    }
                }
                if (stats.reviewedToday > 0) {
                    item(key = "reviewed-today", contentType = "status") {
                        InsetCard(pad = 13) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                TfIcon("checkCircle", size = 16, color = c.green)
                                TfText(
                                    tr("hifz.home.reviewed_today", stats.reviewedToday),
                                    color = c.fg2,
                                    size = 13.5f,
                                )
                            }
                        }
                    }
                }
                item(key = "surah-heading", contentType = "heading") {
                    SectionTitle(tr("hifz.home.your_surahs"))
                }
                items(
                    items = vm.rows,
                    key = { it.surah.number },
                    contentType = { "surah-progress" },
                ) { row ->
                    Box(Modifier.padding(horizontal = HPAD.dp)) {
                        SurahHifzRow(
                            row,
                            onOpen = { onOpenSurah(row.surah.number) },
                            onDrop = { pendingDrop = row },
                        )
                    }
                }
                item(key = "add", contentType = "action") {
                    Box(Modifier.padding(horizontal = HPAD.dp)) {
                        TfAddButton(tr("hifz.home.add_surah"), onSetup)
                    }
                }
                item(key = "pace-heading", contentType = "heading") {
                    SectionTitle(tr("hifz.home.pace"))
                }
                item(key = "pace", contentType = "settings") {
                    PaceCard(vm)
                }
            }
        }
    }

    pendingDrop?.let { row ->
        TfConfirmationDialog(
            title = tr("hifz.remove.title"),
            message = tr("hifz.remove.message", row.surah.nameEnglish),
            confirmLabel = tr("hifz.remove.confirm"),
            cancelLabel = tr("common.cancel"),
            destructive = true,
            onConfirm = {
                vm.drop(row.surah.number)
                pendingDrop = null
            },
            onCancel = { pendingDrop = null },
        )
    }
}

@Composable
private fun EmptyHifz(onSetup: () -> Unit) {
    val c = LocalTf.current
    InsetCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BreathOrb(size = 56, color = c.gold, purple = c.accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Overline(tr("hifz.home.empty.overline"))
                TfText(tr("hifz.home.empty.title"), color = c.fg, size = 16.5f, weight = FontWeight.SemiBold)
            }
        }
        TfText(
            tr("hifz.home.empty.detail"),
            color = c.fg2, size = 13.5f, lineHeight = 1.55f, modifier = Modifier.padding(top = 12.dp),
        )
    }
    Box(Modifier.padding(horizontal = HPAD.dp)) {
        TfPrimaryButton(tr("hifz.home.empty.action"), onClick = onSetup, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TodayCard(due: Int, fresh: Int, reciter: String, onReview: () -> Unit) {
    val c = LocalTf.current
    InsetCard {
        Overline(tr("hifz.home.today"))
        Row(
            Modifier.padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(tr("hifz.home.due", due), c.accent, c.accent.soft(0.16f))
            Pill(tr("hifz.home.fresh", fresh), c.gold, c.gold.soft(0.16f))
        }
        TfText(
            if (due + fresh == 0) {
                tr("hifz.home.resting")
            } else {
                tr("hifz.home.ready", reciter)
            },
            color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 11.dp),
        )
        if (due + fresh > 0) {
            Box(Modifier.padding(top = 13.dp)) {
                TfPrimaryButton(
                    tr("hifz.home.start", due + fresh),
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SurahHifzRow(row: HifzSurahRow, onOpen: () -> Unit, onDrop: () -> Unit) {
    val c = LocalTf.current
    val p = row.progress
    // The row opens the surah in the reader; the remove action is the only part that doesn't.
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
                    if (p.due > 0) Pill(tr("hifz.row.due", p.due), c.accent, c.accent.soft(0.14f))
                }
                TfText(
                    tr("hifz.row.memorized", p.memorized, p.total),
                    color = c.fg2, size = 12.5f,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressBar(p.fraction, c.green, modifier = Modifier.weight(1f))
                    TfText("${p.percent}%", color = c.fg2, size = 11.5f, family = Fonts.mono)
                }
            }
            TfIconButton(
                icon = "trash",
                contentDescription = tr("hifz.row.remove_a11y", row.surah.nameEnglish),
                onClick = onDrop,
                destructive = true,
            )
        }
    }
}

@Composable
private fun PaceCard(vm: HifzViewModel) {
    val c = LocalTf.current
    InsetCard {
        Stepper(
            label = tr("hifz.pace.daily_new.label"),
            hint = tr("hifz.pace.daily_new.hint"),
            value = vm.planner.dailyNew,
            onChange = vm::setDailyNew,
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(0.5.dp).background(c.line))
        Stepper(
            label = tr("hifz.pace.review_cap.label"),
            hint = tr("hifz.pace.review_cap.hint"),
            value = vm.planner.reviewCap,
            step = 5,
            onChange = vm::setReviewCap,
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(0.5.dp).background(c.line))
        Stepper(
            label = tr("hifz.pace.repeat.label"),
            hint = tr("hifz.pace.repeat.hint"),
            value = vm.planner.repeatCount,
            onChange = vm::setRepeatCount,
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(0.5.dp).background(c.line))
        TfSettingsToggleRow(
            title = tr("hifz.pace.autoplay.label"),
            detail = tr("hifz.pace.autoplay.hint"),
            checked = vm.planner.autoPlay,
            onCheckedChange = vm::setAutoPlay,
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
            TfIconButton(
                icon = "chevronDown",
                contentDescription = tr("hifz.pace.decrease_a11y", label),
                onClick = { onChange(value - step) },
            )
            TfText("$value", color = c.fg, size = 16f, family = Fonts.mono)
            TfIconButton(
                icon = "chevronUp",
                contentDescription = tr("hifz.pace.increase_a11y", label),
                onClick = { onChange(value + step) },
            )
        }
    }
}

// ── Setup ────────────────────────────────────────────────────────────────────

@Composable
fun HifzSetupScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val c = LocalTf.current
    val vm = hifzViewModel()
    var picked by remember { mutableStateOf<Surah?>(null) }
    var from by remember { mutableStateOf(1) }
    var to by remember { mutableStateOf(1) }

    // A retained ViewModel may remember the previous visit's filter. Each fresh entry starts
    // from the complete mushaf index so no surah is accidentally hidden behind old text.
    LaunchedEffect(vm) { vm.searchSurahs("") }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("hifz.setup.title"), onBack)
        val surah = picked
        if (surah == null) {
            val matches = vm.availableSurahs
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "search", contentType = "search") {
                    TfSearchField(
                        query = vm.searchQuery,
                        onQuery = vm::searchSurahs,
                        placeholder = tr("hifz.setup.search_hint"),
                        searchContentDescription = tr("hifz.setup.search_a11y"),
                        clearContentDescription = tr("hifz.setup.clear_search_a11y"),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                    )
                }
                item(key = "count", contentType = "heading") {
                    SectionTitle(
                        if (vm.searchQuery.isBlank()) {
                            tr("hifz.setup.surahs_count", matches.size)
                        } else {
                            tr("hifz.setup.matches_count", matches.size)
                        },
                    )
                }
                if (matches.isEmpty()) {
                    item(key = "no-match", contentType = "empty") {
                        TfEmptyState(tr("hifz.setup.no_matches", vm.searchQuery))
                    }
                }
                items(
                    items = matches,
                    key = { it.number },
                    contentType = { "surah" },
                ) { match ->
                    Box(Modifier.padding(horizontal = HPAD.dp)) {
                        SurahPickRow(match) {
                            picked = match
                            from = 1
                            to = match.ayahCount
                        }
                    }
                }
            }
        } else {
            ScrollBody {
                val count = vm.ayahCount(surah.number)
                InsetCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            TfText(surah.nameEnglish, color = c.fg, size = 18f, weight = FontWeight.SemiBold)
                            TfText(
                                tr(
                                    "hifz.setup.surah_meta",
                                    count,
                                    tr(
                                        if (surah.revelationType.name == "MECCAN") {
                                            "quran.surah.meccan"
                                        } else {
                                            "quran.surah.medinan"
                                        },
                                    ),
                                ),
                                color = c.fg2, size = 12.5f,
                            )
                        }
                        TfText(surah.nameArabic, color = c.gold, size = 24f, family = Fonts.serif)
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(0.5.dp).background(c.line))
                    Stepper(
                        label = tr("hifz.setup.from_ayah"),
                        hint = tr("hifz.setup.from_hint"),
                        value = from,
                        onChange = { from = it.coerceIn(1, count) },
                    )
                    Box(Modifier.padding(top = 12.dp)) {
                        Stepper(
                            label = tr("hifz.setup.to_ayah"),
                            hint = tr("hifz.setup.to_hint"),
                            value = to,
                            onChange = { to = it.coerceIn(1, count) },
                        )
                    }
                }
                val span = (maxOf(from, to) - minOf(from, to) + 1)
                InsetCard(pad = 13) {
                    TfText(
                        tr(
                            "hifz.setup.range_summary",
                            span,
                            durationFor(span, vm.planner.dailyNew),
                            vm.planner.dailyNew,
                        ),
                        color = c.fg, size = 14f, weight = FontWeight.Medium,
                    )
                    TfText(
                        tr("hifz.setup.overlap_hint"),
                        color = c.fg3, size = 12f, lineHeight = 1.45f, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(
                    Modifier.padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    TfPrimaryButton(tr("hifz.setup.add_action"), modifier = Modifier.fillMaxWidth(), onClick = {
                        vm.enrol(surah.number, from, to)
                        onDone()
                    })
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .border(0.5.dp, c.line, RoundedCornerShape(12.dp))
                            .then(pressScale({ picked = null }, 0.98f))
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TfText(
                            tr("hifz.setup.choose_different"),
                            color = c.fg2,
                            size = 14f,
                            weight = FontWeight.Medium,
                        )
                    }
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
                TfText(
                    tr(
                        "hifz.setup.surah_meta",
                        s.ayahCount,
                        tr(
                            if (s.revelationType.name == "MECCAN") {
                                "quran.surah.meccan"
                            } else {
                                "quran.surah.medinan"
                            },
                        ),
                    ),
                    color = c.fg3,
                    size = 11.5f,
                )
            }
            TfText(s.nameArabic, color = c.gold, size = 17f, family = Fonts.serif)
        }
    }
}

/** Rough calendar span for a run at a given daily intake — a sentence, not a promise. */
@Composable
private fun durationFor(ayahs: Int, perDay: Int): String {
    val days = if (perDay <= 0) ayahs else (ayahs + perDay - 1) / perDay
    return when {
        days <= 1 -> tr("hifz.setup.duration.day")
        days < 14 -> tr("hifz.setup.duration.days", days)
        days < 60 -> tr("hifz.setup.duration.weeks", (days + 3) / 7)
        else -> tr("hifz.setup.duration.months", (days + 15) / 30)
    }
}

// ── Review session ───────────────────────────────────────────────────────────

@Composable
fun HifzReviewScreen(onBack: () -> Unit) {
    val vm: HifzSessionViewModel = viewModel {
        HifzSessionViewModel(
            getKoin().get(), getKoin().get(), getKoin().get(),
            getKoin().get(), getKoin().get(), getKoin().get(),
        )
    }
    DisposableEffect(vm) {
        vm.beginSession()
        onDispose { vm.endSession() }
    }
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("hifz.review.title"), onBack)
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
            TfText(tr("hifz.review.empty.title"), color = c.fg, size = 16f, weight = FontWeight.SemiBold)
            TfText(
                tr("hifz.review.empty.detail"),
                color = c.fg2, size = 13.5f, lineHeight = 1.55f, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(Modifier.padding(horizontal = HPAD.dp)) {
            TfPrimaryButton(tr("hifz.review.back"), onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
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
                TfText(tr("hifz.review.loading"), color = c.fg3, size = 14f, modifier = Modifier.padding(top = 40.dp))
            } else {
                PromptCard(card, state)
                if (state.revealed) AnswerCard(card, state, vm)
            }
            state.error?.let { errorKey ->
                TfText(tr(errorKey), color = c.danger, size = 13f)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isLoading) {
                TfText(
                    tr("hifz.review.loading"),
                    color = c.fg3,
                    size = 13f,
                    modifier = Modifier.weight(1f),
                    align = TextAlign.Center,
                )
            } else if (state.error != null) {
                Box(Modifier.weight(1f)) {
                    TfPrimaryButton(
                        tr("common.retry"),
                        onClick = vm::retryLoad,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (state.revealed) {
                GradeBtn(tr("hifz.grade.again"), localizedInterval(vm.previewLabel(Rating.AGAIN)), c.danger, Modifier.weight(1f)) { vm.grade(Rating.AGAIN) }
                GradeBtn(tr("hifz.grade.hard"), localizedInterval(vm.previewLabel(Rating.HARD)), c.warn, Modifier.weight(1f)) { vm.grade(Rating.HARD) }
                GradeBtn(tr("hifz.grade.good"), localizedInterval(vm.previewLabel(Rating.GOOD)), c.green, Modifier.weight(1f)) { vm.grade(Rating.GOOD) }
                GradeBtn(tr("hifz.grade.easy"), localizedInterval(vm.previewLabel(Rating.EASY)), c.cyan, Modifier.weight(1f)) { vm.grade(Rating.EASY) }
            } else {
                Box(Modifier.weight(1f)) {
                    TfPrimaryButton(
                        tr("hifz.review.reveal"),
                        onClick = vm::reveal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
            Overline(tr("hifz.review.recite"))
            Spacer(Modifier.weight(1f))
            if (card.card.state == 0) Pill(tr("hifz.review.new"), c.gold, c.gold.soft(0.16f))
        }
        TfText(
            tr(
                "hifz.review.reference",
                card.surah?.nameEnglish ?: tr("hifz.review.surah_number", card.card.surah),
                card.card.ayah,
            ),
            color = c.fg, size = 20f, family = Fonts.serif, lineHeight = 1.25f,
        )

        when {
            card.isFirstOfSurah -> TfText(
                tr("hifz.review.opening"),
                color = c.fg3, size = 13f, modifier = Modifier.padding(top = 2.dp),
            )
            card.cue?.hasText == true -> {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Overline(tr("hifz.review.cue"))
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
                tr("hifz.review.cue_missing"),
                color = c.fg3, size = 12.5f, lineHeight = 1.45f, modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (!state.revealed) {
            TfText(
                tr("hifz.review.instruction"),
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
    val playPauseA11y = tr(
        if (state.audio.isPlaying) "hifz.review.pause_a11y" else "hifz.review.play_a11y",
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.gold.soft(0.07f))
            .border(0.5.dp, c.gold.soft(0.35f), RoundedCornerShape(18.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Overline(tr("hifz.review.ayah_heading", card.card.ayah))
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
            TfText(tr("hifz.review.text_unavailable"), color = c.fg3, size = 13.5f)
            TfText(
                tr("hifz.review.text_unavailable_detail"),
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
                Modifier.size(44.dp).clip(CircleShape).background(c.gold)
                    .then(pressScale({ vm.playPause() }, 0.9f))
                    .semantics {
                        role = Role.Button
                        contentDescription = playPauseA11y
                    },
                contentAlignment = Alignment.Center,
            ) {
                TfIcon(if (state.audio.isPlaying) "pause" else "play", size = 18, color = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TfText(
                    if (state.audio.isLoading) tr("hifz.review.audio_loading")
                    else tr(
                        "hifz.review.repeat_progress",
                        (state.audio.repeatLap + 1).coerceAtMost(state.audio.repeatTarget),
                        state.audio.repeatTarget,
                    ),
                    color = c.fg2, size = 12.5f, family = Fonts.mono,
                )
                ProgressBar(state.audio.progress, c.gold, height = 3, modifier = Modifier.fillMaxWidth())
            }
            TfIconButton(
                icon = "repeat",
                contentDescription = tr("hifz.review.replay_a11y"),
                onClick = vm::replay,
            )
        }
        if (state.audio.error != null) {
            TfText(tr("hifz.review.audio_failed"), color = c.danger, size = 12f)
        }
    }
}

@Composable
private fun GradeBtn(label: String, interval: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val a11y = tr("hifz.grade.a11y", label, interval)
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(color.soft(0.16f))
            .border(0.5.dp, color.soft(0.4f), RoundedCornerShape(12.dp))
            .then(pressScale(onClick, 0.94f))
            .semantics { role = Role.Button; contentDescription = a11y }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TfText(label, color = color, size = 13.5f, weight = FontWeight.SemiBold)
        TfText(interval, color = color, size = 10.5f, family = Fonts.mono, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun localizedInterval(raw: String): String {
    val unit = listOf("mo", "m", "h", "d", "y").firstOrNull { raw.endsWith(it) } ?: return raw
    val value = raw.removeSuffix(unit).toLongOrNull() ?: return raw
    return tr("hifz.interval.$unit", value)
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
                tr(
                    if (state.reviewed == 1) "hifz.result.reviewed_one" else "hifz.result.reviewed_many",
                    state.reviewed,
                ),
                color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display,
            )
        }
        InsetCard {
            Overline(tr("hifz.result.session"))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(tr("hifz.result.again", state.counts[Rating.AGAIN] ?: 0), c.danger, c.danger.soft(0.14f))
                Pill(tr("hifz.result.hard", state.counts[Rating.HARD] ?: 0), c.warn, c.warn.soft(0.14f))
                Pill(tr("hifz.result.good", state.counts[Rating.GOOD] ?: 0), c.green, c.green.soft(0.14f))
                Pill(tr("hifz.result.easy", state.counts[Rating.EASY] ?: 0), c.cyan, c.cyan.soft(0.14f))
            }
            TfText(
                tr("hifz.result.detail"),
                color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 12.dp),
            )
        }
        Box(Modifier.padding(horizontal = HPAD.dp)) {
            TfPrimaryButton(tr("common.done"), onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}
