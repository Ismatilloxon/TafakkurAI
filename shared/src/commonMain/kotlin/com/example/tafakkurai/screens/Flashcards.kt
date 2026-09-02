package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tafakkurai.db.Flashcard
import com.example.tafakkurai.flashcards.Rating
import com.example.tafakkurai.flashcards.ReviewDirection
import com.example.tafakkurai.flashcards.ReviewFilter
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfAddButton
import com.example.tafakkurai.ui.components.TfConfirmationDialog
import com.example.tafakkurai.ui.components.TfIconButton
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.ui.components.TfSecondaryButton
import com.example.tafakkurai.ui.components.TfTextField
import com.example.tafakkurai.vm.FlashcardsViewModel
import org.koin.mp.KoinPlatform.getKoin

// ── Deck list per subject ────────────────────────────────────────────────────
@Composable
fun FlashcardDecksScreen(
    subjectId: String,
    sessionKey: Int,
    onBack: () -> Unit,
    onDeck: (String) -> Unit,
) {
    val c = LocalTf.current
    val vm: FlashcardsViewModel = viewModel(key = "flashcard-decks:$sessionKey:$subjectId") {
        FlashcardsViewModel(getKoin().get())
    }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    LaunchedEffect(subjectId) { vm.loadDecks(subjectId) }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("flashcards.title"), onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.decks.isEmpty() && !adding) {
                item(key = "empty", contentType = "empty") {
                    InsetCard {
                        TfText(tr("flashcards.empty.title"), color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                        TfText(
                            tr("flashcards.empty.detail"),
                            color = c.fg2,
                            size = 13.5f,
                            lineHeight = 1.5f,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            items(vm.decks, key = { it.first.id }, contentType = { "deck" }) { (deck, st) ->
                Box(Modifier.padding(horizontal = HPAD.dp)) {
                    Card(Modifier.fillMaxWidth(), onClick = { onDeck(deck.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            IconBadge("bookOpen", c.accent, size = 44, iconSize = 21)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                TfText(deck.name, color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill(tr("flashcards.stats.due", st.dueCount), c.accent, c.accent.soft(0.14f))
                                    Pill(tr("flashcards.stats.new", st.newCount), c.cyan, c.cyan.soft(0.14f))
                                    if (st.hardCount > 0) {
                                        Pill(tr("flashcards.stats.hard", st.hardCount), c.warn, c.warn.soft(0.14f))
                                    }
                                }
                            }
                            TfText("${st.total}", color = c.fg3, size = 13f, family = Fonts.mono)
                        }
                    }
                }
            }
            item(key = "deck-action", contentType = "action") {
                Box(Modifier.padding(horizontal = HPAD.dp)) {
                    if (adding) {
                        Card(Modifier.fillMaxWidth(), pad = 14) {
                            TfTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholder = tr("flashcards.deck_name_hint"),
                            )
                            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TfPrimaryButton(tr("common.create"), modifier = Modifier.weight(1f), onClick = {
                                    vm.createDeck(subjectId, name); name = ""; adding = false
                                })
                                TfSecondaryButton(
                                    tr("common.cancel"),
                                    onClick = { name = ""; adding = false },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else TfAddButton(tr("flashcards.new_deck"), onClick = { adding = true })
                }
            }
        }
    }
}

// ── Deck detail: cards + filters + study launcher ────────────────────────────
@Composable
fun FlashcardDeckScreen(
    deckId: String,
    sessionKey: Int,
    onBack: () -> Unit,
    onStudy: (ReviewFilter, ReviewDirection) -> Unit,
) {
    val c = LocalTf.current
    val vm: FlashcardsViewModel = viewModel(key = "flashcard-deck:$sessionKey:$deckId") {
        FlashcardsViewModel(getKoin().get())
    }
    var filter by remember { mutableStateOf(ReviewFilter.DUE) }
    var direction by remember { mutableStateOf(ReviewDirection.FRONT_BACK) }
    var editing by remember { mutableStateOf<Flashcard?>(null) }
    var pendingDelete by remember { mutableStateOf<Flashcard?>(null) }
    var adding by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(deckId, refresh) { vm.loadCards(deckId) }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("flashcards.deck.title"), onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // study mode picker
            item(key = "study", contentType = "study") {
                InsetCard {
                    Overline(tr("flashcards.study.overline"))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(tr("flashcards.filter.due"), filter == ReviewFilter.DUE) { filter = ReviewFilter.DUE }
                        FilterChip(tr("flashcards.filter.hard"), filter == ReviewFilter.HARD) { filter = ReviewFilter.HARD }
                        FilterChip(tr("flashcards.filter.new"), filter == ReviewFilter.NEW) { filter = ReviewFilter.NEW }
                        FilterChip(tr("flashcards.filter.starred"), filter == ReviewFilter.STARRED) { filter = ReviewFilter.STARRED }
                        FilterChip(tr("flashcards.filter.all"), filter == ReviewFilter.ALL) { filter = ReviewFilter.ALL }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(tr("flashcards.direction.front_back"), direction == ReviewDirection.FRONT_BACK) { direction = ReviewDirection.FRONT_BACK }
                        FilterChip(tr("flashcards.direction.back_front"), direction == ReviewDirection.BACK_FRONT) { direction = ReviewDirection.BACK_FRONT }
                        FilterChip(tr("flashcards.direction.mixed"), direction == ReviewDirection.MIXED) { direction = ReviewDirection.MIXED }
                    }
                    Box(Modifier.padding(top = 12.dp)) {
                        TfPrimaryButton(
                            tr("flashcards.study_now"),
                            onClick = { onStudy(filter, direction) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item(key = "cards-heading", contentType = "heading") {
                SectionTitle(tr("flashcards.cards_count", vm.cards.size))
            }
            items(vm.cards, key = { it.id }, contentType = { "card" }) { card ->
                Box(Modifier.padding(horizontal = HPAD.dp)) {
                    CardRow(
                        card = card,
                        onEdit = { editing = card; adding = false },
                        onStar = { vm.toggleStar(deckId, card) },
                        onIgnore = { vm.toggleIgnore(deckId, card) },
                        onDelete = { pendingDelete = card },
                    )
                }
            }
            item(key = "card-action", contentType = "action") {
                Box(Modifier.padding(horizontal = HPAD.dp)) {
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
                    } else TfAddButton(tr("flashcards.add_card"), onClick = { adding = true })
                }
            }
        }
    }
    pendingDelete?.let { card ->
        TfConfirmationDialog(
            title = tr("flashcards.delete.title"),
            message = tr("flashcards.delete.message", card.front),
            confirmLabel = tr("flashcards.delete.confirm"),
            cancelLabel = tr("common.cancel"),
            onConfirm = {
                vm.deleteCard(deckId, card.id)
                pendingDelete = null
            },
            onCancel = { pendingDelete = null },
            destructive = true,
        )
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
                    if (card.state == 0L) Pill(tr("flashcards.badge.new"), c.cyan, c.cyan.soft(0.14f))
                    if (card.lastRating == 2L || card.lapses > 0) {
                        Pill(tr("flashcards.badge.hard"), c.warn, c.warn.soft(0.14f))
                    }
                    if (card.tags.isNotBlank()) card.tags.split(",").take(3).forEach { t ->
                        Pill("#${t.trim()}", c.fg3, c.card2)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TfIconButton(
                    icon = "star",
                    contentDescription = tr(if (card.starred == 1L) "flashcards.action.unstar" else "flashcards.action.star", card.front),
                    onClick = onStar,
                )
                TfIconButton(
                    icon = "pause",
                    contentDescription = tr(if (dim) "flashcards.action.include" else "flashcards.action.ignore", card.front),
                    onClick = onIgnore,
                )
                TfIconButton(
                    icon = "x",
                    contentDescription = tr("flashcards.action.delete", card.front),
                    onClick = onDelete,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun CardEditor(initial: Flashcard?, onSave: (String, String, String) -> Unit, onCancel: () -> Unit) {
    var front by remember(initial) { mutableStateOf(initial?.front ?: "") }
    var back by remember(initial) { mutableStateOf(initial?.back ?: "") }
    var tags by remember(initial) { mutableStateOf(initial?.tags ?: "") }
    Card(Modifier.fillMaxWidth(), pad = 14) {
        Overline(tr(if (initial == null) "flashcards.editor.new" else "flashcards.editor.edit"))
        EditorField(tr("flashcards.editor.front_hint"), front, { front = it }, minHeight = 44)
        EditorField(tr("flashcards.editor.back_hint"), back, { back = it }, minHeight = 44)
        EditorField(tr("flashcards.editor.tags_hint"), tags, { tags = it }, minHeight = 24)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TfPrimaryButton(
                tr("common.save"),
                onClick = { onSave(front, back, tags) },
                modifier = Modifier.weight(1f),
                enabled = front.isNotBlank() && back.isNotBlank(),
            )
            TfSecondaryButton(tr("common.cancel"), onCancel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EditorField(hint: String, value: String, onChange: (String) -> Unit, minHeight: Int) =
    TfTextField(
        value = value,
        onValueChange = onChange,
        placeholder = hint,
        modifier = Modifier.padding(top = 8.dp).heightIn(min = minHeight.dp),
        singleLine = false,
    )

@Composable
private fun FilterChip(label: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) c.accent.soft(0.18f) else c.card2)
            .border(0.5.dp, if (on) c.accent else c.sep, RoundedCornerShape(999.dp))
            .then(pressScale(onClick, 0.95f)).padding(horizontal = 13.dp, vertical = 7.dp),
    ) { TfText(label, color = if (on) c.accent else c.fg2, size = 13f, weight = FontWeight.Medium) }
}

// ── Review session (Anki-style) ──────────────────────────────────────────────
@Composable
fun FlashcardReviewScreen(
    deckId: String,
    sessionKey: Int,
    filter: ReviewFilter,
    direction: ReviewDirection,
    onBack: () -> Unit,
) {
    val c = LocalTf.current
    val vm: FlashcardsViewModel = viewModel(key = "flashcard-review:$sessionKey:$deckId") {
        FlashcardsViewModel(getKoin().get())
    }
    LaunchedEffect(deckId, filter, direction) { vm.startSession(deckId, filter, direction) }
    val card = vm.current

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("flashcards.review.title"), onBack)
        when {
            vm.queue.isEmpty() -> ScrollBody {
                InsetCard {
                    TfText(tr("flashcards.review.empty.title"), color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                    TfText(
                        tr("flashcards.review.empty.detail"),
                        color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box(Modifier.padding(horizontal = HPAD.dp)) {
                    TfPrimaryButton(tr("common.back"), onClick = onBack, modifier = Modifier.fillMaxWidth())
                }
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
                        Overline(tr(if (vm.flippedCard) "flashcards.face.back_front" else "flashcards.face.front"))
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
                            TfText(tr("flashcards.tap_to_reveal"), color = c.fg3, size = 13f, modifier = Modifier.padding(top = 24.dp))
                        }
                    }
                    // grade bar
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (vm.revealed) {
                            GradeButton(tr("flashcards.grade.again"), localizedReviewInterval(vm.previewLabel(Rating.AGAIN)), c.danger, Modifier.weight(1f)) { vm.grade(Rating.AGAIN) }
                            GradeButton(tr("flashcards.grade.hard"), localizedReviewInterval(vm.previewLabel(Rating.HARD)), c.warn, Modifier.weight(1f)) { vm.grade(Rating.HARD) }
                            GradeButton(tr("flashcards.grade.good"), localizedReviewInterval(vm.previewLabel(Rating.GOOD)), c.green, Modifier.weight(1f)) { vm.grade(Rating.GOOD) }
                            GradeButton(tr("flashcards.grade.easy"), localizedReviewInterval(vm.previewLabel(Rating.EASY)), c.cyan, Modifier.weight(1f)) { vm.grade(Rating.EASY) }
                        } else {
                            Box(Modifier.weight(1f)) {
                                TfPrimaryButton(
                                    tr("flashcards.reveal_answer"),
                                    onClick = { vm.revealed = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
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
                TfText(
                    tr(
                        if (vm.history.size == 1) "flashcards.summary.reviewed_one" else "flashcards.summary.reviewed_many",
                        vm.history.size,
                    ),
                    color = c.fg,
                    size = 24f,
                    weight = FontWeight.Bold,
                    family = Fonts.display,
                )
            }
            InsetCard {
                Overline(tr("flashcards.summary.overline"))
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(tr("flashcards.summary.again", counts[Rating.AGAIN] ?: 0), c.danger, c.danger.soft(0.14f))
                    Pill(tr("flashcards.summary.hard", counts[Rating.HARD] ?: 0), c.warn, c.warn.soft(0.14f))
                    Pill(tr("flashcards.summary.good", counts[Rating.GOOD] ?: 0), c.green, c.green.soft(0.14f))
                    Pill(tr("flashcards.summary.easy", counts[Rating.EASY] ?: 0), c.cyan, c.cyan.soft(0.14f))
                }
                TfText(
                    tr("flashcards.summary.detail"),
                    color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 12.dp),
                )
            }
            Box(Modifier.padding(horizontal = HPAD.dp)) {
                TfPrimaryButton(tr("common.done"), onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun localizedReviewInterval(raw: String): String {
    val unit = listOf("mo", "m", "h", "d", "y").firstOrNull(raw::endsWith) ?: return raw
    val value = raw.removeSuffix(unit).toLongOrNull() ?: return raw
    return tr("hifz.interval.$unit", value)
}
