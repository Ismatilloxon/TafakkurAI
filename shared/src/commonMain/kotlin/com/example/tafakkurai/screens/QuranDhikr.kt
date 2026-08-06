package com.example.tafakkurai.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tafakkurai.adhkar.AdhkarCategory
import com.example.tafakkurai.adhkar.AdhkarItem
import com.example.tafakkurai.adhkar.AdhkarStore
import com.example.tafakkurai.adhkar.adhkarFor
import com.example.tafakkurai.dhikr.DHIKR_CATALOG
import com.example.tafakkurai.dhikr.DhikrStore
import com.example.tafakkurai.dhikr.QUICK_TARGETS
import com.example.tafakkurai.quran.hifz.HifzStore
import com.example.tafakkurai.quran.AyahReaderViewModel
import com.example.tafakkurai.quran.BASMALA_MEANING
import com.example.tafakkurai.quran.AyahUiModel
import com.example.tafakkurai.quran.MushafEdition
import com.example.tafakkurai.quran.MushafPageViewModel
import com.example.tafakkurai.quran.PlaybackState
import com.example.tafakkurai.quran.PrefetchState
import com.example.tafakkurai.quran.QuranUiEvent
import com.example.tafakkurai.quran.QuranUiState
import com.example.tafakkurai.quran.QuranViewModel
import com.example.tafakkurai.quran.ReaderUiEvent
import com.example.tafakkurai.quran.Reciter
import com.example.tafakkurai.quran.SurahUiModel
import com.example.tafakkurai.quran.TOTAL_AYAH_COUNT
import com.example.tafakkurai.quran.TranslationEdition
import com.example.tafakkurai.quran.showsBasmala
import com.example.tafakkurai.quran.surahOf
import com.example.tafakkurai.ui.*
import org.koin.mp.KoinPlatform.getKoin

// ─────────────────────────────────────────────────────────────────────────────
// Qurʼan & dhikr. Three tabs: reading, tasbih, adhkar.
//
// Aggregate statistics deliberately do NOT live here — Today owns the dashboard
// ("how consistent have I been?"), these screens answer "what can I do now?".
// The only numbers shown are the ones you act on directly.
// ─────────────────────────────────────────────────────────────────────────────

private val QD_TABS = listOf("quran" to "Qurʼan", "dhikr" to "Dhikr", "adhkar" to "Adhkar")

@Composable
fun QuranDhikrScreen(
    onBack: () -> Unit,
    onOpenSurah: (Int) -> Unit = {},
    onOpenHifz: () -> Unit = {},
) {
    var tab by remember { mutableStateOf("quran") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Qurʼan & dhikr", onBack)
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 10.dp)) {
            Segmented(QD_TABS, tab, { tab = it })
        }
        Crossfade(
            targetState = tab,
            animationSpec = tween(200),
            label = "quran-dhikr-tab",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { active ->
            when (active) {
                "quran" -> QuranTab(onOpenSurah, onOpenHifz)
                "dhikr" -> DhikrTab()
                else -> AdhkarTab()
            }
        }
    }
}

// ── Qurʼan tab ───────────────────────────────────────────────────────────────

@Composable
private fun QuranTab(onOpenSurah: (Int) -> Unit, onOpenHifz: () -> Unit) {
    val c = LocalTf.current
    val vm: QuranViewModel = viewModel { QuranViewModel(getKoin().get(), getKoin().get()) }
    val state by vm.state.collectAsState()

    // Persisted progress can change from the reader; re-read it when the tab appears.
    LaunchedEffect(Unit) { vm.refresh() }

    when {
        state.isLoading && state.surahs.isEmpty() -> LoadingState("Loading the Qurʼan…")
        state.error != null -> ErrorState(state.error!!) { vm.onEvent(QuranUiEvent.Retry) }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "continue") { ContinueReadingCard(state, onOpenSurah) }
            item(key = "overall") { OverallProgressCard(state) }
            item(key = "hifz") { HifzEntryCard(onOpenHifz) }
            item(key = "search") { SurahSearchField(state.query) { vm.onEvent(QuranUiEvent.Search(it)) } }
            item(key = "heading") {
                SectionTitle(
                    if (state.query.isBlank()) "114 Surahs" else "${state.surahs.size} matching",
                )
            }
            if (state.surahs.isEmpty()) {
                item(key = "no-match") {
                    EmptyState("No surah matches “${state.query}”.")
                }
            }
            items(state.surahs, key = { it.surah.number }) { row ->
                Box(Modifier.padding(horizontal = HPAD.dp)) {
                    SurahRow(row) { onOpenSurah(row.surah.number) }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(state: QuranUiState, onOpenSurah: (Int) -> Unit) {
    val c = LocalTf.current
    val last = state.lastRead
    if (last == null) {
        // Nothing read yet — offer a real starting point rather than an empty card.
        InsetCard(onClick = { onOpenSurah(1) }) {
            Overline("START READING")
            TfText(
                "Begin with Al-Fatihah",
                color = c.fg, size = 18f, weight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            TfText(
                "Tap any ayah to hear it and mark your place.",
                color = c.fg2, size = 12.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 2.dp),
            )
        }
        return
    }
    val surah = surahOf(last.surahNumber)
    InsetCard(onClick = { onOpenSurah(last.surahNumber) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Overline("CONTINUE READING")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    TfText(last.surahName, color = c.fg, size = 18f, weight = FontWeight.SemiBold)
                    if (surah != null) TfText(surah.nameArabic, color = c.gold, size = 18f, family = Fonts.serif)
                }
                TfText(
                    "Ayah ${last.ayahNumber}${surah?.let { " of ${it.ayahCount}" } ?: ""}",
                    color = c.fg2, size = 12.5f, modifier = Modifier.padding(top = 2.dp),
                )
                if (surah != null) {
                    ProgressBar(
                        (last.ayahNumber.toFloat() / surah.ayahCount).coerceIn(0f, 1f),
                        c.gold,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(c.gold)
                    .semantics { contentDescription = "Continue reading ${last.surahName}" },
                contentAlignment = Alignment.Center,
            ) { TfIcon("play", size = 20, color = Color.White) }
        }
    }
}

@Composable
private fun OverallProgressCard(state: QuranUiState) {
    val c = LocalTf.current
    val animated by animateFloatAsState(state.overallProgress, tween(600), label = "quran-overall")
    InsetCard(pad = 18) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("OVERALL READ")
                TfText(
                    "${state.overallPercent}%",
                    color = c.fg, size = 34f, weight = FontWeight.Bold, family = Fonts.mono,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TfText(
                    "${state.ayahsRead} / ${state.totalAyahs} ayahs",
                    color = c.fg2, size = 12.5f, family = Fonts.mono,
                )
                TfText(
                    "${state.completedSurahs} of 114 surahs complete",
                    color = c.fg3, size = 11.5f,
                )
            }
        }
        ProgressBar(animated, c.green, height = 6, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

/**
 * Way into memorization, sitting under the reading progress because that is the order people
 * arrive in: they read a surah, then decide to hold on to it.
 *
 * It reads the card table directly — the numbers are local, so there is nothing to load and
 * no state for this row to be wrong about.
 */
@Composable
private fun HifzEntryCard(onOpen: () -> Unit) {
    val c = LocalTf.current
    val store = remember { getKoin().get<HifzStore>() }
    // Recomputed on each composition of the tab, which is when a finished session returns.
    val stats = remember(store) { store.stats() }

    Card(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            IconBadge("brain", c.gold, size = 46, iconSize = 22)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TfText("Hifz · Yodlash", color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                TfText(
                    when {
                        !stats.hasCards -> "Memorize a surah, an ayah at a time."
                        stats.due > 0 -> "${stats.due} ${if (stats.due == 1) "ayah" else "ayahs"} to review today"
                        else -> "${stats.memorized} of ${stats.total} memorized · nothing due"
                    },
                    color = c.fg2, size = 12.5f,
                )
            }
            if (stats.due > 0) Pill("${stats.due}", c.gold, c.gold.soft(0.16f))
            TfIcon("chevronRight", size = 18, color = c.fg3)
        }
    }
}

@Composable
private fun SurahSearchField(query: String, onQuery: (String) -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HPAD.dp)
            .clip(RoundedCornerShape(12.dp)).background(c.card3)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        TfIcon("search", size = 16, color = c.fg3)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) TfText("Search surah, meaning or number…", color = c.fg3, size = 14f)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = Fonts.sans),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search surahs" },
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.then(pressScale({ onQuery("") }, 0.9f)).padding(2.dp)) {
                TfIcon("x", size = 15, color = c.fg3)
            }
        }
    }
}

@Composable
private fun SurahRow(row: SurahUiModel, onClick: () -> Unit) {
    val c = LocalTf.current
    val s = row.surah
    val badge = if (row.isComplete) c.green else c.gold
    Card(Modifier.fillMaxWidth(), pad = 13, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(badge.soft(0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                TfText(
                    s.number.toString().padStart(2, '0'),
                    color = badge, size = 13.5f, weight = FontWeight.Bold, family = Fonts.mono,
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TfText(s.nameEnglish, color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                    if (row.isLastRead) Pill("last read", c.accent, c.accent.soft(0.16f))
                }
                TfText(s.nameArabic, color = c.gold, size = 15f, family = Fonts.serif, modifier = Modifier.padding(top = 1.dp))
                TfText(
                    "${s.nameTranslation} · ${s.ayahCount} ayahs · ${if (s.revelationType.name == "MECCAN") "Meccan" else "Medinan"}",
                    color = c.fg3, size = 11.5f, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    row.isComplete -> TfIcon("checkCircle", size = 18, color = c.green)
                    row.percent > 0 -> TfText("${row.percent}%", color = c.fg2, size = 12f, family = Fonts.mono)
                    else -> TfIcon("chevronRight", size = 16, color = c.fg3)
                }
                if (row.isAvailableOffline) TfIcon("download", size = 12, color = c.fg3)
            }
        }
        if (row.percent in 1..99) {
            ProgressBar(row.percent / 100f, badge, height = 3, modifier = Modifier.fillMaxWidth().padding(top = 9.dp))
        }
    }
}

// ── Ayah reader ──────────────────────────────────────────────────────────────

@Composable
fun AyahReaderScreen(surahNumber: Int, onBack: () -> Unit) {
    val c = LocalTf.current
    val vm: AyahReaderViewModel = viewModel(key = "reader-$surahNumber") {
        AyahReaderViewModel(
            surahNumber, getKoin().get(), getKoin().get(), getKoin().get(), getKoin().get(),
        )
    }
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    var reciterSheet by remember { mutableStateOf(false) }

    // Keep the ayah being recited pinned to the top of the list as playback advances.
    val playingAyah = state.audio.track
        ?.takeIf { it.surahNumber == surahNumber }
        ?.ayahNumber
    LaunchedEffect(playingAyah, state.ayahs.size) {
        val ayah = playingAyah ?: return@LaunchedEffect
        val index = state.ayahs.indexOfFirst { it.ayah.number == ayah }
        if (index >= 0) listState.animateScrollToItem(index + 1) // +1 for the surah header item
    }

    val surah = state.surah
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackBar(surah?.nameEnglish ?: "Surah $surahNumber", onBack) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TranslationToggle(state.showTranslation) {
                        vm.onEvent(ReaderUiEvent.SetShowTranslation(!state.showTranslation))
                    }
                    Box(
                        Modifier.then(pressScale({ reciterSheet = true }, 0.9f)).padding(6.dp)
                            .semantics { contentDescription = "Reader settings" },
                    ) { TfIcon("sliders", size = 19, color = c.accent) }
                }
            }
            when {
                state.isLoading && state.ayahs.isEmpty() -> LoadingState("Loading ${surah?.nameEnglish ?: "surah"}…")
                state.error != null -> ErrorState(state.error!!) { vm.onEvent(ReaderUiEvent.Retry) }
                // Meaning off: nothing needs to sit beside the Arabic, so the surah is
                // drawn as the mushaf prints it rather than as a list of rows.
                !state.showTranslation -> MushafPane(
                    surahNumber = surahNumber,
                    playingAyah = playingAyah,
                    selectedAyah = state.ayahs.firstOrNull { it.isSelected }?.ayah?.number,
                    mushaf = state.mushaf,
                    onTapAyah = { surahOfWord, ayahNumber ->
                        // A page can carry the end of the previous surah or the start of
                        // the next; only this surah's ayahs are this reader's to play.
                        if (surahOfWord == surahNumber) vm.onEvent(ReaderUiEvent.TapAyah(ayahNumber))
                    },
                    modifier = Modifier.weight(1f)
                        .padding(bottom = if (state.audio.isActive) 96.dp else 0.dp),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        top = 10.dp,
                        // Leave room for the pinned player so the last ayah stays reachable.
                        bottom = if (state.audio.isActive) 190.dp else 120.dp,
                    ),
                    // Read with the meaning the ayahs are separate blocks, but only just:
                    // a tight gap keeps one ayah and its translation reading as one unit.
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "surah-header") {
                        SurahHeader(
                            state.readCount, surahNumber, state.progress,
                            state.showTranslation, state.mushaf,
                        )
                    }
                    if (state.downloads.isOpeningChunk && state.downloads.isDownloading) {
                        item(key = "download") {
                            Box(Modifier.padding(horizontal = HPAD.dp)) {
                                DownloadCard(state.downloads)
                            }
                        }
                    }
                    if (state.isTextUnavailable) {
                        item(key = "no-text") {
                            EmptyState(
                                "This surah's text hasn't been downloaded yet. Connect to the internet and pull " +
                                    "up again — recitation audio still works offline once cached.",
                            )
                        }
                    }
                    items(state.ayahs, key = { it.ayah.number }) { row ->
                        Box(Modifier.padding(horizontal = HPAD.dp)) {
                            AyahCard(
                                row = row,
                                showTranslation = state.showTranslation,
                                mushaf = state.mushaf,
                                onTap = { vm.onEvent(ReaderUiEvent.TapAyah(row.ayah.number)) },
                                onToggleRead = { vm.onEvent(ReaderUiEvent.ToggleRead(row.ayah.number)) },
                            )
                        }
                    }
                }
            }
        }

        if (state.audio.isActive) {
            AyahPlayerBar(
                state = state.audio,
                downloads = state.downloads,
                modifier = Modifier.align(Alignment.BottomCenter),
                onPlayPause = { vm.onEvent(ReaderUiEvent.PlayPause) },
                onPrevious = { vm.onEvent(ReaderUiEvent.Previous) },
                onNext = { vm.onEvent(ReaderUiEvent.Next) },
                onSeek = { vm.onEvent(ReaderUiEvent.Seek(it)) },
            )
        }

        ReaderSettingsSheet(
            open = reciterSheet,
            selectedId = state.reciter.id,
            reciters = state.reciters,
            translations = state.translations,
            selectedTranslationId = state.translation.id,
            showTranslation = state.showTranslation,
            mushafs = state.mushafs,
            selectedMushafId = state.mushaf.id,
            onPick = { vm.onEvent(ReaderUiEvent.SelectReciter(it)); reciterSheet = false },
            onPickTranslation = { vm.onEvent(ReaderUiEvent.SelectTranslation(it)) },
            onPickMushaf = { vm.onEvent(ReaderUiEvent.SelectMushaf(it)) },
            onSetShowTranslation = { vm.onEvent(ReaderUiEvent.SetShowTranslation(it)) },
            onClose = { reciterSheet = false },
        )
    }
}

/**
 * The printed-page half of the reader, with its own pagination state.
 *
 * A separate composable so the page metadata is fetched only once the meaning is actually
 * switched off — a reader who always reads with the translation never makes the call.
 */
@Composable
private fun MushafPane(
    surahNumber: Int,
    playingAyah: Int?,
    selectedAyah: Int?,
    mushaf: MushafEdition,
    onTapAyah: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: MushafPageViewModel = viewModel(key = "mushaf-$surahNumber") {
        MushafPageViewModel(surahNumber, getKoin().get())
    }
    val state by vm.state.collectAsState()
    // The page follows the recitation, the way a listener's finger does.
    LaunchedEffect(playingAyah) { playingAyah?.let(vm::showAyah) }
    MushafPageView(
        state = state,
        playingAyah = playingAyah,
        selectedAyah = selectedAyah,
        mushaf = mushaf,
        onTapAyah = onTapAyah,
        onPrevious = vm::previous,
        onNext = vm::next,
        onRetry = vm::retry,
        modifier = modifier,
    )
}

/**
 * Nav-bar switch between reading with the meaning and reading the Arabic alone. The fast
 * path for a mode that gets flipped mid-surah; the labelled control that explains it lives
 * in [ReaderSettingsSheet], which is where someone who does not recognise the glyph will
 * find it.
 */
@Composable
private fun TranslationToggle(on: Boolean, onToggle: () -> Unit) {
    val c = LocalTf.current
    // Lit accent when the meaning is on, dimmed when the read is Arabic-only. Same bare-icon
    // treatment as the qari button beside it, so the bar reads as one row of controls.
    val fg by animateColorAsState(if (on) c.accent else c.fg3, tween(200), label = "translation-toggle")
    Box(
        Modifier.then(pressScale(onToggle, 0.9f)).padding(6.dp)
            .semantics { contentDescription = if (on) "Hide translation" else "Show translation" },
    ) { TfIcon("languages", size = 19, color = fg) }
}

@Composable
private fun SurahHeader(
    readCount: Int,
    surahNumber: Int,
    progress: Float,
    showTranslation: Boolean,
    mushaf: MushafEdition,
) {
    val c = LocalTf.current
    val surah = surahOf(surahNumber) ?: return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            AmbientGlow(c.gold, opacity = 0.14f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp),
            ) {
                TfText(surah.nameArabic, color = c.gold, size = 36f, family = Fonts.serif)
                TfText(surah.nameEnglish, color = c.fg, size = 19f, weight = FontWeight.SemiBold, family = Fonts.display)
                TfText(
                    "${surah.nameTranslation} · ${surah.ayahCount} ayahs",
                    color = c.fg3, size = 12.5f,
                )
                Pill("$readCount read", c.green, c.green.soft(0.16f))
                ProgressBar(progress, c.green, height = 4, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                if (showsBasmala(surahNumber)) BasmalaLine(showTranslation, mushaf)
            }
        }
    }
}

/**
 * Progress of the recitation being downloaded as the surah opens. Shown once, at the top
 * of the list: playback does not wait for it, so it is information, never a blocker.
 */
@Composable
private fun DownloadCard(state: PrefetchState) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth(), pad = 13) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(c.accent.soft(0.16f)),
                contentAlignment = Alignment.Center,
            ) { TfIcon("download", size = 15, color = c.accent) }
            Column(Modifier.weight(1f)) {
                TfText(
                    "Downloading ayahs ${state.firstAyah}–${state.lastAyah}",
                    color = c.fg, size = 13.5f, weight = FontWeight.Medium,
                )
                TfText(
                    "${state.completed} of ${state.total} · plays offline once done",
                    color = c.fg3, size = 11.5f, modifier = Modifier.padding(top = 1.dp),
                )
            }
            TfText(
                "${(state.fraction * 100).toInt()}%",
                color = c.accent, size = 12f, family = Fonts.mono,
            )
        }
        ProgressBar(
            state.fraction, c.accent, height = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
        )
    }
}

/**
 * The Basmala, opening every surah that carries it. Rendered as part of the header rather
 * than as an ayah row: it is not numbered, not marked read, and never played on its own.
 */
@Composable
private fun BasmalaLine(showTranslation: Boolean, mushaf: MushafEdition) {
    val c = LocalTf.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
    ) {
        TfText(
            // Same rasm as the ayahs below it — the line is part of the page, not a label.
            mushaf.basmala, color = c.fg, size = 22f, family = Fonts.serif,
            align = TextAlign.Center, lineHeight = 1.8f,
        )
        // Follows the ayah rows: an Arabic-only read leaves no English anywhere on the page.
        if (showTranslation) {
            TfText(BASMALA_MEANING, color = c.fg3, size = 12f, align = TextAlign.Center)
        }
    }
}

@Composable
private fun AyahCard(
    row: AyahUiModel,
    showTranslation: Boolean,
    mushaf: MushafEdition,
    onTap: () -> Unit,
    onToggleRead: () -> Unit,
) {
    val c = LocalTf.current
    val ayah = row.ayah
    // Selected ayah gets a warm surface; playing adds a border. Subtle, never loud.
    val surface by animateColorAsState(
        if (row.isSelected) c.gold.soft(0.10f) else Color.Transparent,
        tween(220), label = "ayah-surface",
    )
    val border = if (row.isPlaying) c.gold else if (row.isSelected) c.gold.soft(0.35f) else c.hair

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
            .border(0.5.dp, border, RoundedCornerShape(16.dp))
            // Each ayah is its own tap target — the page is never one big button.
            .then(pressScale(onTap, 0.99f))
            .padding(16.dp)
            .semantics { contentDescription = "Ayah ${ayah.number}. Tap to listen." },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(if (row.isRead) c.green.soft(0.18f) else c.card3)
                    .then(pressScale(onToggleRead, 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                if (row.isRead) TfIcon("check", size = 13, color = c.green)
                else TfText(ayah.number.toString(), color = c.fg3, size = 11f, family = Fonts.mono)
            }
            if (row.isRead) {
                TfText(ayah.number.toString(), color = c.fg3, size = 11.5f, family = Fonts.mono)
            }
            Spacer(Modifier.weight(1f))
            if (row.isPlaying) Pill("playing", c.gold, c.gold.soft(0.16f))
        }

        if (ayah.hasText) {
            TfText(
                ayah.arabicText!!,
                color = c.fg, size = 25f, family = Fonts.serif,
                // Indo-Pak stacks marks over and under the line; it gets more room than
                // Uthmani does, or the rows collide.
                align = TextAlign.End, lineHeight = mushaf.lineHeight,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (showTranslation && !ayah.translation.isNullOrBlank()) {
                TfText(
                    ayah.translation, color = c.fg2, size = 14f, lineHeight = 1.6f,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        } else {
            TfText(
                "Text not downloaded",
                color = c.fg3, size = 13f, modifier = Modifier.padding(top = 10.dp),
            )
            TfText(
                "Tap to listen to the recitation of this ayah.",
                color = c.fg3, size = 12f, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ── Bottom player ────────────────────────────────────────────────────────────

@Composable
private fun AyahPlayerBar(
    state: com.example.tafakkurai.quran.AudioUiState,
    downloads: PrefetchState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    val c = LocalTf.current
    val track = state.track ?: return
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(c.card2)
            .drawTopHairline(c.line)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.gold.soft(0.16f)),
                contentAlignment = Alignment.Center,
            ) { TfIcon("bookOpen", size = 17, color = c.gold) }
            Column(Modifier.weight(1f)) {
                TfText(track.reciterName, color = c.fg, size = 14f, weight = FontWeight.SemiBold, maxLines = 1)
                TfText(
                    if (track.isBasmala) "${track.surahName} · Bismillah"
                    else "${track.surahName} · Ayah ${track.ayahNumber}",
                    color = c.fg2, size = 12f, maxLines = 1,
                )
            }
            if (state.playback == PlaybackState.ERROR) {
                Pill("unavailable", c.danger, c.danger.soft(0.16f))
            }
        }

        // The next chunk arriving in the background. Deliberately small and unlabelled
        // beyond a count — it is not something the listener has to act on.
        if (downloads.isDownloading && !downloads.isOpeningChunk) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TfIcon("download", size = 12, color = c.fg3)
                ProgressBar(
                    downloads.fraction, c.accent, height = 2,
                    modifier = Modifier.weight(1f),
                )
                TfText(
                    "${downloads.completed}/${downloads.total}",
                    color = c.fg3, size = 10.5f, family = Fonts.mono,
                )
            }
        }

        if (state.error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TfText(state.error, color = c.fg3, size = 12f, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).border(0.5.dp, c.line, RoundedCornerShape(999.dp))
                        .then(pressScale(onPlayPause, 0.95f)).padding(horizontal = 12.dp, vertical = 6.dp),
                ) { TfText("Try again", color = c.accent, size = 12.5f, weight = FontWeight.SemiBold) }
            }
        } else {
            SeekBar(state.progress, onSeek)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerButton("chevronLeft", "Previous ayah", c.fg2, 20, onPrevious)
                Box(
                    Modifier.size(50.dp).clip(CircleShape).background(c.gold)
                        .then(pressScale(onPlayPause, 0.92f))
                        .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isLoading) {
                        TfText("···", color = Color.White, size = 18f, weight = FontWeight.Bold)
                    } else {
                        TfIcon(if (state.isPlaying) "pause" else "play", size = 22, color = Color.White)
                    }
                }
                PlayerButton("chevronRight", "Next ayah", c.fg2, 20, onNext)
            }
        }
    }
}

@Composable
private fun PlayerButton(icon: String, label: String, color: Color, size: Int, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(c.card3).then(pressScale(onClick, 0.9f))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { TfIcon(icon, size = size, color = color) }
}

/** Tap anywhere on the track to seek there. */
@Composable
private fun SeekBar(progress: Float, onSeek: (Float) -> Unit) {
    val c = LocalTf.current
    var width by remember { mutableStateOf(1f) }
    Box(
        Modifier.fillMaxWidth().height(22.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onSeek(offset.x / width) }
            },
        contentAlignment = Alignment.Center,
    ) {
        ProgressBar(progress, c.gold, height = 4, modifier = Modifier.fillMaxWidth())
    }
}

// ── Reader settings sheet ────────────────────────────────────────────────────

/**
 * Everything about *how* this surah is read: which mushaf it is printed in, whose voice
 * recites it, and whether the meaning shows. All three re-read the surah when changed, so
 * they live in one sheet rather than scattered across the nav bar.
 */
@Composable
private fun BoxScope.ReaderSettingsSheet(
    open: Boolean,
    selectedId: String,
    reciters: List<Reciter>,
    translations: List<TranslationEdition>,
    selectedTranslationId: String,
    showTranslation: Boolean,
    mushafs: List<MushafEdition>,
    selectedMushafId: String,
    onPick: (String) -> Unit,
    onPickTranslation: (String) -> Unit,
    onPickMushaf: (String) -> Unit,
    onSetShowTranslation: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTf.current
    val scrim by animateFloatAsState(if (open) 1f else 0f, tween(240), label = "reciter-scrim")
    val slide by animateFloatAsState(if (open) 0f else 1f, tween(300), label = "reciter-slide")
    if (!open && scrim <= 0f) return

    Box(
        Modifier.matchParentSize().background(Color(0xFF07090D).copy(alpha = 0.55f * scrim))
            .then(pressScale(onClose, 1f)),
    )
    BoxWithConstraints(Modifier.align(Alignment.BottomCenter)) {
        // Taller than the qari list alone needs: the mushaf and translation pickers sit
        // above and below it.
        val height = maxHeight * 0.82f
        Column(
            Modifier.fillMaxWidth().height(height).offset(y = height * slide)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(c.bg1).drawTopHairline(c.line)
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().drawBottomHairline(c.sep)
                    .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Overline("READER")
                    TfText(
                        "Reading settings", color = c.fg, size = 17f,
                        weight = FontWeight.SemiBold, family = Fonts.display,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(c.card2).then(pressScale(onClose, 0.9f)),
                    contentAlignment = Alignment.Center,
                ) { TfIcon("x", size = 16, color = c.fg2) }
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = HPAD.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Overline("MUSHAF")
                TfText(
                    "Which printed rasm the ayahs are drawn in. The words are identical in " +
                        "all three — the letters and marks are not.",
                    color = c.fg2, size = 13f, lineHeight = 1.45f,
                )
                mushafs.forEach { mushaf ->
                    val on = mushaf.id == selectedMushafId
                    Card(Modifier.fillMaxWidth(), pad = 13, onClick = { onPickMushaf(mushaf.id) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                TfText(mushaf.label, color = c.fg, size = 15f, weight = FontWeight.Medium)
                                TfText(
                                    mushaf.note, color = c.fg3, size = 12f,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                            if (on) TfIcon("checkCircle", size = 19, color = c.green)
                        }
                        // The Basmala in that mushaf's own rasm: the picker shows the
                        // difference rather than asking anyone to take it on trust.
                        TfText(
                            mushaf.basmala, color = if (on) c.gold else c.fg2, size = 19f,
                            family = Fonts.serif, align = TextAlign.End,
                            lineHeight = mushaf.lineHeight,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                }
                Overline("QARI", modifier = Modifier.padding(top = 10.dp))
                reciters.forEach { reciter ->
                    val on = reciter.id == selectedId
                    Card(Modifier.fillMaxWidth(), pad = 13, onClick = { onPick(reciter.id) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (on) c.gold.soft(0.18f) else c.card3),
                                contentAlignment = Alignment.Center,
                            ) {
                                TfText(
                                    reciter.name.split(" ").mapNotNull { it.firstOrNull() }
                                        .joinToString("").take(2).uppercase(),
                                    color = if (on) c.gold else c.fg3, size = 13f, weight = FontWeight.SemiBold,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                TfText(reciter.name, color = c.fg, size = 15f, weight = FontWeight.Medium)
                                TfText(
                                    reciter.nameArabic, color = c.gold, size = 13.5f, family = Fonts.serif,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                            if (on) TfIcon("checkCircle", size = 19, color = c.green)
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Overline("TRANSLATION")
                    Segmented(
                        listOf("on" to "Show meaning", "off" to "Arabic only"),
                        if (showTranslation) "on" else "off",
                        { onSetShowTranslation(it == "on") },
                    )
                    TfText(
                        // The edition list stays live while the meaning is hidden — picking one
                        // decides what appears the moment the toggle goes back on.
                        if (showTranslation) "Meaning shown under each ayah"
                        else "Ayahs show Arabic alone. Your edition is kept for when you switch back.",
                        color = c.fg2, size = 13f, lineHeight = 1.45f,
                    )
                    translations.forEach { edition ->
                        val on = edition.id == selectedTranslationId
                        Card(Modifier.fillMaxWidth(), pad = 12, onClick = { onPickTranslation(edition.id) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    TfText(edition.label, color = c.fg, size = 14.5f, weight = FontWeight.Medium)
                                    TfText(
                                        edition.translator, color = c.fg3, size = 12f,
                                        modifier = Modifier.padding(top = 1.dp),
                                    )
                                }
                                if (on) TfIcon("checkCircle", size = 19, color = c.green)
                            }
                        }
                    }
                }
                PrivacyFooter(
                    "Recitation streams from the everyayah.com CDN, the Madani and Turkish " +
                        "texts and all translations come from alquran.cloud, and the Indo-Pak " +
                        "text from quran.com. Offline, the reader falls back to its bundled " +
                        "Uthmani excerpt whichever mushaf is chosen. Uzbek Latin is the " +
                        "Cyrillic translation transliterated, not a separate translation. " +
                        "Your reading progress stays on this device.",
                )
            }
        }
    }
}

// ── Dhikr tab ────────────────────────────────────────────────────────────────

@Composable
private fun DhikrTab() {
    val c = LocalTf.current
    val store = remember { getKoin().get<DhikrStore>() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) { store.reload() }

    val selected = store.selected
    val count = store.count
    val target = store.target
    val ring by animateFloatAsState(store.progress, tween(320), label = "dhikr-ring")
    // A small kick on each tap, settling back — enough to feel, not enough to distract.
    val bump by animateFloatAsState(if (store.isTargetReached) 1.04f else 1f, tween(240), label = "dhikr-bump")

    fun tap() {
        val reached = store.increment()
        runCatching {
            haptics.performHapticFeedback(
                if (reached) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
            )
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Counter — one-handed: the ring itself is the tap target.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                AmbientGlow(if (store.isTargetReached) c.green else c.gold, opacity = 0.16f)
                RingGauge(
                    value = ring,
                    color = if (store.isTargetReached) c.green else c.gold,
                    track = c.card3,
                    strokeWidth = 9f,
                    size = 236,
                    modifier = Modifier.scale(bump)
                        .then(pressScale(::tap, 0.97f))
                        .semantics { contentDescription = "${selected.transliteration}. $count of $target. Tap to count." },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TfText(selected.arabic, color = c.gold, size = 24f, family = Fonts.serif)
                        TfText("$count", color = c.fg, size = 56f, weight = FontWeight.Bold, family = Fonts.mono)
                        TfText("of $target", color = c.fg3, size = 12.5f, family = Fonts.mono)
                    }
                }
            }
        }

        TfText(
            selected.meaning, color = c.fg2, size = 13.5f, align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )

        // Big primary tap target, for when the ring isn't comfortable to reach.
        Row(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(c.gold)
                    .then(pressScale(::tap, 0.97f))
                    .semantics { contentDescription = "Count one" },
                contentAlignment = Alignment.Center,
            ) { TfIcon("plus", size = 26, color = Color.White) }
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.card3)
                    .then(pressScale({ store.reset() }, 0.94f))
                    .semantics { contentDescription = "Reset counter" },
                contentAlignment = Alignment.Center,
            ) { TfIcon("repeat", size = 20, color = c.fg2) }
        }

        // Quick targets
        Row(
            Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_TARGETS.forEach { value ->
                val on = target == value
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (on) c.gold.soft(0.18f) else c.card3)
                        .then(pressScale({ store.setTarget(value) }, 0.95f))
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TfText(
                        "$value", color = if (on) c.gold else c.fg2, size = 14f,
                        family = Fonts.mono, weight = FontWeight.SemiBold,
                    )
                }
            }
        }

        SectionTitle("Current dhikr")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = HPAD.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DHIKR_CATALOG.forEach { dhikr ->
                val on = dhikr.id == selected.id
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (on) c.gold.soft(0.18f) else c.card3)
                        .then(pressScale({ store.select(dhikr.id) }, 0.95f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    TfText(
                        dhikr.transliteration,
                        color = if (on) c.gold else c.fg2, size = 13f,
                        weight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }

        SectionTitle("Favourites")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DHIKR_CATALOG.forEach { dhikr ->
                val counted = store.countOf(dhikr.id)
                val favourite = store.isFavourite(dhikr.id)
                Card(Modifier.fillMaxWidth(), pad = 14, onClick = { store.select(dhikr.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            TfText(dhikr.arabic, color = c.gold, size = 18f, family = Fonts.serif)
                            TfText(
                                "${dhikr.transliteration} · ${dhikr.meaning}",
                                color = c.fg2, size = 12f, modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        TfText(
                            "$counted", color = if (counted > 0) c.green else c.fg3,
                            size = 15f, family = Fonts.mono, weight = FontWeight.SemiBold,
                        )
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c.card3)
                                .then(pressScale({ store.toggleFavourite(dhikr.id) }, 0.9f))
                                .semantics {
                                    contentDescription =
                                        if (favourite) "Remove ${dhikr.transliteration} from favourites"
                                        else "Add ${dhikr.transliteration} to favourites"
                                },
                            contentAlignment = Alignment.Center,
                        ) { TfIcon("star", size = 15, color = if (favourite) c.gold else c.fg3) }
                    }
                }
            }
        }
    }
}

// ── Adhkar tab ───────────────────────────────────────────────────────────────

@Composable
private fun AdhkarTab() {
    val c = LocalTf.current
    val store = remember { getKoin().get<AdhkarStore>() }
    var category by remember { mutableStateOf(AdhkarCategory.MORNING) }
    var expanded by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { store.reload() }

    val items = adhkarFor(category)
    val done = store.doneCount(category)
    val total = store.totalCount(category)
    val progress by animateFloatAsState(store.progressOf(category), tween(320), label = "adhkar-progress")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "switch") {
            Box(Modifier.padding(horizontal = HPAD.dp)) {
                Segmented(
                    AdhkarCategory.entries.map { it.id to it.label },
                    category.id,
                    { category = AdhkarCategory.from(it) },
                )
            }
        }
        item(key = "progress") {
            InsetCard(pad = 18) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overline("${category.label.uppercase()} ADHKAR")
                        TfText(
                            "$done / $total",
                            color = c.fg, size = 30f, weight = FontWeight.Bold, family = Fonts.mono,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(contentAlignment = Alignment.Center) {
                        RingGauge(progress, c.green, c.card3, strokeWidth = 5f, size = 54) {
                            TfText(
                                "${store.percentOf(category)}%",
                                color = c.fg2, size = 12f, family = Fonts.mono, weight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                ProgressBar(progress, c.green, height = 6, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
        item(key = "hint") {
            TfText(
                if (category == AdhkarCategory.MORNING) "Recited after Fajr until sunrise." else "Recited after Asr until Maghrib.",
                color = c.fg3, size = 12.5f, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        itemsIndexed(items, key = { _, it -> "${category.id}-${it.id}" }) { index, item ->
            Box(Modifier.padding(horizontal = HPAD.dp)) {
                AdhkarRow(
                    item = item,
                    index = index + 1,
                    isDone = store.isDone(category, item.id),
                    isExpanded = expanded == item.id,
                    onToggleDone = { store.toggle(category, item.id) },
                    onToggleExpand = { expanded = if (expanded == item.id) null else item.id },
                )
            }
        }
        item(key = "footer") {
            PrivacyFooter("Reflective and educational — never a ruling. Your completions stay on this device.")
        }
    }
}

@Composable
private fun AdhkarRow(
    item: AdhkarItem,
    index: Int,
    isDone: Boolean,
    isExpanded: Boolean,
    onToggleDone: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth(), pad = 14, onClick = onToggleExpand) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (isDone) c.green.soft(0.18f) else c.card3)
                    .then(pressScale(onToggleDone, 0.88f))
                    .semantics {
                        contentDescription =
                            if (isDone) "Mark ${item.title} not done" else "Mark ${item.title} done"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isDone) TfIcon("check", size = 15, color = c.green)
                else TfText("$index", color = c.fg3, size = 11.5f, family = Fonts.mono)
            }
            Column(Modifier.weight(1f)) {
                TfText(
                    item.title,
                    color = if (isDone) c.fg3 else c.fg, size = 14.5f, weight = FontWeight.SemiBold,
                )
                TfText(item.reference, color = c.fg3, size = 11.5f, modifier = Modifier.padding(top = 2.dp))
            }
            if (item.repeat > 1) Pill("×${item.repeat}", c.gold, c.gold.soft(0.16f))
            TfIcon(if (isExpanded) "chevronUp" else "chevronDown", size = 16, color = c.fg3)
        }
        if (isExpanded) {
            TfText(
                item.arabic,
                color = c.fg, size = 20f, family = Fonts.serif, align = TextAlign.End, lineHeight = 1.95f,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            TfText(
                item.transliteration,
                color = c.gold, size = 12.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 10.dp),
            )
            TfText(
                item.meaning,
                color = c.fg2, size = 13.5f, lineHeight = 1.6f, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ── Shared states ────────────────────────────────────────────────────────────

@Composable
internal fun LoadingState(message: String) {
    val c = LocalTf.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BreathOrb(size = 64, color = c.accent, purple = c.purple)
        TfText(message, color = c.fg2, size = 14f, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit) {
    val c = LocalTf.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge("bookOpen", c.gold, size = 56, iconSize = 26)
        TfText(
            message, color = c.fg, size = 16f, align = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        TfText(
            "Check your connection and try again.",
            color = c.fg3, size = 13f, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
        )
        Box(
            Modifier.padding(top = 18.dp).clip(RoundedCornerShape(11.dp)).background(c.accent)
                .then(pressScale(onRetry, 0.95f)).padding(horizontal = 20.dp, vertical = 11.dp),
        ) { TfText("Try again", color = Color.White, size = 14f, weight = FontWeight.SemiBold) }
    }
}

@Composable
private fun EmptyState(message: String) {
    val c = LocalTf.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {
        TfText(message, color = c.fg3, size = 13.5f, align = TextAlign.Center, lineHeight = 1.55f)
    }
}
