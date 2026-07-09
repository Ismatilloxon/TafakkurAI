package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.tafakkurai.db.Flashcard
import com.example.tafakkurai.flashcards.Rating
import com.example.tafakkurai.flashcards.ReviewDirection
import com.example.tafakkurai.flashcards.ReviewFilter
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.vm.FlashcardsViewModel
import org.koin.mp.KoinPlatform.getKoin

// ── Deck list per subject ────────────────────────────────────────────────────
@Composable
fun FlashcardDecksScreen(subjectId: String, onBack: () -> Unit, onDeck: (String) -> Unit) {
    val c = LocalTf.current
    val vm: FlashcardsViewModel = viewModel { FlashcardsViewModel(getKoin().get()) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(subjectId) { vm.loadDecks(subjectId) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Flashcards", onBack)
        ScrollBody {
            if (vm.decks.isEmpty() && !adding) {
                InsetCard {
                    TfText("No decks yet", color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                    TfText(
                        "Create a deck and add key–value cards. FSRS schedules each review at the moment you're about to forget.",
                        color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.decks.forEach { (deck, st) ->
                    Card(Modifier.fillMaxWidth(), onClick = { onDeck(deck.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            IconBadge("bookOpen", c.accent, size = 44, iconSize = 21)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                TfText(deck.name, color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill("${st.dueCount} due", c.accent, c.accent.soft(0.14f))
                                    Pill("${st.newCount} new", c.cyan, c.cyan.soft(0.14f))
                                    if (st.hardCount > 0) Pill("${st.hardCount} hard", c.warn, c.warn.soft(0.14f))
                                }
                            }
                            TfText("${st.total}", color = c.fg3, size = 13f, family = Fonts.mono)
                        }
                    }
                }
                if (adding) {
                    InsetCard(pad = 14) {
                        Box(Modifier.fillMaxWidth()) {
                            if (name.isEmpty()) TfText("Deck name…", color = c.fg3, size = 15f)
                            BasicTextField(
                                value = name, onValueChange = { name = it }, singleLine = true,
                                textStyle = TextStyle(color = c.fg, fontSize = 15.sp, fontFamily = Fonts.sans),
                                cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("Create", c.accent) {
                                vm.createDeck(subjectId, name); name = ""; adding = false
                            }
                            SmallButton("Cancel", c.fg3) { name = ""; adding = false }
                        }
                    }
                } else AddDashed("New deck") { adding = true }
            }
        }
    }
}

// ── Deck detail: cards + filters + study launcher ────────────────────────────
@Composable
fun FlashcardDeckScreen(deckId: String, onBack: () -> Unit, onStudy: (ReviewFilter, ReviewDirection) -> Unit) {
    val c = LocalTf.current
    val vm: FlashcardsViewModel = viewModel { FlashcardsViewModel(getKoin().get()) }
    var filter by remember { mutableStateOf(ReviewFilter.DUE) }
    var direction by remember { mutableStateOf(ReviewDirection.FRONT_BACK) }
    var editing by remember { mutableStateOf<Flashcard?>(null) }
    var adding by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(deckId, refresh) { vm.loadCards(deckId) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Deck", onBack)
        ScrollBody {
            // study mode picker
            InsetCard {
                Overline("STUDY")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip("Due", filter == ReviewFilter.DUE) { filter = ReviewFilter.DUE }
                    FilterChip("Hard", filter == ReviewFilter.HARD) { filter = ReviewFilter.HARD }
                    FilterChip("New", filter == ReviewFilter.NEW) { filter = ReviewFilter.NEW }
                    FilterChip("Starred", filter == ReviewFilter.STARRED) { filter = ReviewFilter.STARRED }
                    FilterChip("All", filter == ReviewFilter.ALL) { filter = ReviewFilter.ALL }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip("Front → Back", direction == ReviewDirection.FRONT_BACK) { direction = ReviewDirection.FRONT_BACK }
                    FilterChip("Back → Front", direction == ReviewDirection.BACK_FRONT) { direction = ReviewDirection.BACK_FRONT }
                    FilterChip("Mixed", direction == ReviewDirection.MIXED) { direction = ReviewDirection.MIXED }
                }
                Box(Modifier.padding(top = 12.dp)) {
                    PrimaryButton("Study now") { onStudy(filter, direction) }
                }
            }

            SectionTitle("Cards · ${vm.cards.size}")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.cards.forEach { card ->
                    CardRow(
                        card = card,
                        onEdit = { editing = card; adding = false },
                        onStar = { vm.toggleStar(deckId, card) },
                        onIgnore = { vm.toggleIgnore(deckId, card) },
                        onDelete = { vm.deleteCard(deckId, card.id) },
                    )
                }
                if (adding || editing != null) {
                    CardEditor(
                        initial = editing,
                        onSave = { front, back, tags ->
                            val e = editing
                            if (e == null) vm.addCard(deckId, front, back, tags)
                            else vm.updateCard(deckId, e.id, front, back, tags)
                            adding = false; editing = null; refresh++
                        },
                        onCancel = { adding = false; editing = null },
                    )
                } else AddDashed("Add card") { adding = true }
            }
        }
    }
}

@Composable
private fun CardRow(card: Flashcard, onEdit: () -> Unit, onStar: () -> Unit, onIgnore: () -> Unit, onDelete: () -> Unit) {
    val c = LocalTf.current
    val dim = card.ignored == 1L
    Card(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TfText(card.front, color = if (dim) c.fg3 else c.fg, size = 15f, weight = FontWeight.Medium)
                TfText(card.back, color = if (dim) c.fg3 else c.fg2, size = 13f, lineHeight = 1.4f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (card.state == 0L) Pill("new", c.cyan, c.cyan.soft(0.14f))
                    if (card.lastRating == 2L || card.lapses > 0) Pill("hard", c.warn, c.warn.soft(0.14f))
                    if (card.tags.isNotBlank()) card.tags.split(",").take(3).forEach { t ->
                        Pill("#${t.trim()}", c.fg3, c.card2)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(pressScale(onStar, 0.85f)) {
                    TfIcon("star", size = 17, color = if (card.starred == 1L) c.gold else c.fg3)
                }
                Box(pressScale(onIgnore, 0.85f)) {
                    TfIcon("pause", size = 17, color = if (dim) c.warn else c.fg3)
                }
                Box(pressScale(onDelete, 0.85f)) {
                    TfIcon("x", size = 17, color = c.fg3)
                }
            }
        }
    }
}

@Composable
private fun CardEditor(initial: Flashcard?, onSave: (String, String, String) -> Unit, onCancel: () -> Unit) {
    val c = LocalTf.current
    var front by remember(initial) { mutableStateOf(initial?.front ?: "") }
    var back by remember(initial) { mutableStateOf(initial?.back ?: "") }
    var tags by remember(initial) { mutableStateOf(initial?.tags ?: "") }
    InsetCard(pad = 14) {
        Overline(if (initial == null) "NEW CARD" else "EDIT CARD")
        EditorField("Front — question / word", front, { front = it }, minHeight = 44)
        EditorField("Back — answer / meaning", back, { back = it }, minHeight = 44)
        EditorField("Tags, comma separated", tags, { tags = it }, minHeight = 24)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Save", c.accent) { onSave(front, back, tags) }
            SmallButton("Cancel", c.fg3, onClick = onCancel)
        }
    }
}

@Composable
private fun EditorField(hint: String, value: String, onChange: (String) -> Unit, minHeight: Int) {
    val c = LocalTf.current
    Box(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(c.card2).border(0.5.dp, c.line, RoundedCornerShape(10.dp))
            .padding(10.dp).heightIn(min = minHeight.dp),
    ) {
        if (value.isEmpty()) TfText(hint, color = c.fg3, size = 14f)
        BasicTextField(
            value = value, onValueChange = onChange,
            textStyle = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = Fonts.sans, lineHeight = 20.sp),
            cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) c.accent.soft(0.18f) else c.card2)
            .border(0.5.dp, if (on) c.accent else c.sep, RoundedCornerShape(999.dp))
            .then(pressScale(onClick, 0.95f)).padding(horizontal = 13.dp, vertical = 7.dp),
    ) { TfText(label, color = if (on) c.accent else c.fg2, size = 13f, weight = FontWeight.Medium) }
}

@Composable
private fun SmallButton(label: String, color: Color, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.soft(0.16f))
            .then(pressScale(onClick, 0.95f)).padding(horizontal = 16.dp, vertical = 9.dp),
    ) { TfText(label, color = color, size = 13.5f, weight = FontWeight.SemiBold) }
}

// ── Review session (Anki-style) ──────────────────────────────────────────────
@Composable
fun FlashcardReviewScreen(
    deckId: String,
    filter: ReviewFilter,
    direction: ReviewDirection,
    onBack: () -> Unit,
) {
    val c = LocalTf.current
    val vm: FlashcardsViewModel = viewModel { FlashcardsViewModel(getKoin().get()) }
    LaunchedEffect(deckId, filter, direction) { vm.startSession(deckId, filter, direction) }
    val card = vm.current

    Column(Modifier.fillMaxSize()) {
        BackBar("Review", onBack)
        when {
            vm.queue.isEmpty() -> ScrollBody {
                InsetCard {
                    TfText("Nothing to review", color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                    TfText(
                        "No cards match this filter right now. Add cards or come back when reviews are due.",
                        color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box(Modifier.padding(horizontal = HPAD.dp)) { PrimaryButton("Back", onClick = onBack) }
            }

            vm.finished || card == null -> SessionSummary(vm, onBack)

            else -> {
                val question = if (vm.flippedCard) card.back else card.front
                val answer = if (vm.flippedCard) card.front else card.back
                Column(Modifier.fillMaxSize().padding(horizontal = HPAD.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgressBar(vm.index / vm.queue.size.toFloat(), c.accent, modifier = Modifier.weight(1f))
                        TfText("${vm.index + 1}/${vm.queue.size}", color = c.fg2, size = 12f, family = Fonts.mono)
                    }
                    // card face
                    Column(
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(c.card).border(0.5.dp, c.line, RoundedCornerShape(18.dp))
                            .then(pressScale({ vm.revealed = true }, 0.99f))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Overline(if (vm.flippedCard) "BACK → FRONT" else "FRONT")
                        TfText(
                            question, color = c.fg, size = 24f, family = Fonts.serif,
                            lineHeight = 1.3f, align = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (vm.revealed) {
                            Box(Modifier.padding(vertical = 18.dp).fillMaxWidth(0.5f).height(0.5.dp).background(c.line))
                            TfText(
                                answer, color = c.fg2, size = 18f, lineHeight = 1.45f,
                                align = TextAlign.Center,
                            )
                        } else {
                            TfText("Tap to reveal", color = c.fg3, size = 13f, modifier = Modifier.padding(top = 24.dp))
                        }
                    }
                    // grade bar
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (vm.revealed) {
                            GradeButton("Again", vm.previewLabel(Rating.AGAIN), c.danger, Modifier.weight(1f)) { vm.grade(Rating.AGAIN) }
                            GradeButton("Hard", vm.previewLabel(Rating.HARD), c.warn, Modifier.weight(1f)) { vm.grade(Rating.HARD) }
                            GradeButton("Good", vm.previewLabel(Rating.GOOD), c.green, Modifier.weight(1f)) { vm.grade(Rating.GOOD) }
                            GradeButton("Easy", vm.previewLabel(Rating.EASY), c.cyan, Modifier.weight(1f)) { vm.grade(Rating.EASY) }
                        } else {
                            Box(Modifier.weight(1f)) { PrimaryButton("Reveal answer") { vm.revealed = true } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, interval: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
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
private fun SessionSummary(vm: FlashcardsViewModel, onBack: () -> Unit) {
    val c = LocalTf.current
    val counts = vm.sessionCounts()
    Column(Modifier.fillMaxSize()) {
        ScrollBody {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BreathOrb(size = 96, color = c.accent, purple = c.purple)
                TfText("${vm.history.size} cards reviewed", color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display)
            }
            InsetCard {
                Overline("SESSION SUMMARY")
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Again ${counts[Rating.AGAIN] ?: 0}", c.danger, c.danger.soft(0.14f))
                    Pill("Hard ${counts[Rating.HARD] ?: 0}", c.warn, c.warn.soft(0.14f))
                    Pill("Good ${counts[Rating.GOOD] ?: 0}", c.green, c.green.soft(0.14f))
                    Pill("Easy ${counts[Rating.EASY] ?: 0}", c.cyan, c.cyan.soft(0.14f))
                }
                TfText(
                    "FSRS rescheduled every card. Hard and lapsed cards land in the Hard filter — drill them separately anytime.",
                    color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 12.dp),
                )
            }
            Box(Modifier.padding(horizontal = HPAD.dp)) { PrimaryButton("Done", onClick = onBack) }
        }
    }
}
