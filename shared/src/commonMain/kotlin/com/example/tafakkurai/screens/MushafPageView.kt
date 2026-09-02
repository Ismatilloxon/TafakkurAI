package com.example.tafakkurai.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.quran.LINES_PER_PAGE
import com.example.tafakkurai.quran.MushafEdition
import com.example.tafakkurai.quran.MushafLine
import com.example.tafakkurai.quran.MushafPage
import com.example.tafakkurai.quran.MushafUiState
import com.example.tafakkurai.quran.MushafWord
import com.example.tafakkurai.quran.arabicIndic
import com.example.tafakkurai.quran.surahOf
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*

// ─────────────────────────────────────────────────────────────────────────────
// The mushaf page.
//
// Shown when the meaning is switched off: with nothing to read alongside it, the
// Arabic stops being a list of rows and becomes the printed page — fifteen lines
// inside a frame, ayahs running into one another and separated only by their
// roundels, exactly where the King Fahd Complex prints them.
//
// The layout is not ours; it comes from the mushaf metadata (see MushafPageRepository).
// What is ours is the frame, the banner and the tap targets — every word still plays
// its ayah, which a paper mushaf cannot do.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MushafPageView(
    state: MushafUiState,
    playingAyah: Int?,
    selectedAyah: Int?,
    mushaf: MushafEdition,
    onTapAyah: (surahNumber: Int, ayahNumber: Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    val page = state.page
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            // Turning the page is the gesture the surface asks for; the chevrons below
            // are for anyone who does not try it.
            .pointerInput(state.index, state.pageNumbers) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // Right-to-left: dragging left moves forward, as with paper.
                        if (dx < -PAGE_TURN_SLOP) onNext() else if (dx > PAGE_TURN_SLOP) onPrevious()
                        dx = 0f
                    },
                    onHorizontalDrag = { _, amount -> dx += amount },
                )
            },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                page == null && state.error != null -> ErrorState(tr(state.error), onRetry)
                page == null -> LoadingState(tr("quran.mushaf.opening"))
                else -> PageSheet(page, playingAyah, selectedAyah, mushaf, onTapAyah)
            }
        }
        PageTurnBar(state, onPrevious, onNext)
    }
    // A page that is still loading behind a page already drawn: say so quietly rather
    // than blanking the sheet the reader is looking at.
    if (state.isLoading && page != null) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            TfText(tr("quran.mushaf.loading_page"), color = c.fg3, size = 11f)
        }
    }
}

/** The framed sheet: the double rule of a printed mushaf, drawn as two nested borders. */
@Composable
private fun PageSheet(
    page: MushafPage,
    playingAyah: Int?,
    selectedAyah: Int?,
    mushaf: MushafEdition,
    onTapAyah: (Int, Int) -> Unit,
) {
    val c = LocalTf.current
    // Warm paper in a light palette; in a dark one the page stays dark — an all-white
    // sheet at night is the one thing a night reader will not forgive.
    val paper = if (c.isLight) Color(0xFFFBF5E4) else c.card
    val rule = c.gold.soft(if (c.isLight) 0.55f else 0.40f)

    Column(
        Modifier.fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(paper)
            .border(1.5.dp, rule, RoundedCornerShape(10.dp))
            .padding(5.dp)
            .border(0.8.dp, rule.soft(0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        PageRunningHead(page)
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            // Fifteen lines share the height of the sheet, as they do in print.
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (line in page.lines) {
                when (line) {
                    is MushafLine.SurahBanner -> SurahBanner(line.surahNumber)
                    is MushafLine.Basmala -> TfText(
                        mushaf.basmala, color = c.fg, size = 17f, family = Fonts.serif,
                        align = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is MushafLine.Verse -> VerseLine(
                        line, page.lines.size, playingAyah, selectedAyah, onTapAyah,
                    )
                }
            }
        }
        PageFooter(page)
    }
}

/** Surah name and juz above the rule, the way the running head is printed. */
@Composable
private fun PageRunningHead(page: MushafPage) {
    val c = LocalTf.current
    val head = page.surahNumbers.mapNotNull { surahOf(it)?.nameArabic }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TfText("جزء ${arabicIndic(page.juzNumber)}", color = c.fg3, size = 11f, family = Fonts.serif)
        TfText(head, color = c.fg3, size = 11f, family = Fonts.serif, maxLines = 1)
    }
    Box(Modifier.fillMaxWidth().height(0.8.dp).background(c.gold.soft(0.35f)))
}

/** The page number in its roundel, centred under the frame as it is in print. */
@Composable
private fun PageFooter(page: MushafPage) {
    val c = LocalTf.current
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(c.gold.soft(0.14f))
                .border(0.8.dp, c.gold.soft(0.45f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            TfText(arabicIndic(page.number), color = c.gold, size = 12f, family = Fonts.serif)
        }
    }
}

/**
 * One printed line of ayah text.
 *
 * Justified the way the mushaf justifies: the words of the line are spread across the
 * full measure rather than set solid with a ragged edge. Each word is its own tap
 * target so the ayah it belongs to can be played from anywhere along the line.
 */
@Composable
private fun VerseLine(
    line: MushafLine.Verse,
    lineCount: Int,
    playingAyah: Int?,
    selectedAyah: Int?,
    onTapAyah: (Int, Int) -> Unit,
) {
    val size = fontSizeFor(line.words, lineCount)
    // The page is read right to left; laying the row out RTL puts the first word of the
    // line on the right without reversing the list by hand.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((index, word) in line.words.withIndex()) {
                WordGlyph(
                    word = word,
                    size = size,
                    isPlaying = playingAyah == word.ayahNumber,
                    isSelected = selectedAyah == word.ayahNumber,
                    onTap = { onTapAyah(word.surahNumber, word.ayahNumber) },
                )
                if (index != line.words.lastIndex) Spacer(Modifier.width(2.dp))
            }
        }
    }
}

/** One word, or the roundel closing an ayah. */
@Composable
private fun WordGlyph(
    word: MushafWord,
    size: Float,
    isPlaying: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    val c = LocalTf.current
    // Highlighting is by ayah, not by word: a tap lights the whole ayah wherever it runs.
    val tint by animateColorAsState(
        when {
            isPlaying -> c.gold.soft(0.26f)
            isSelected -> c.gold.soft(0.12f)
            else -> Color.Transparent
        },
        tween(200), label = "mushaf-word",
    )
    if (word.isEndMarker) {
        // U+06DD carries the number inside its rosette in any Quranic font; where the
        // font has no rosette the digits still read correctly on their own.
        TfText(
            "۝${word.text}",
            color = c.gold, size = size * 0.95f, family = Fonts.serif, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(tint)
                .then(pressScale(onTap, 0.96f)).padding(horizontal = 1.dp),
        )
    } else {
        TfText(
            word.text,
            color = c.fg, size = size, family = Fonts.serif, weight = FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(tint)
                .then(pressScale(onTap, 0.96f)).padding(horizontal = 1.dp),
        )
    }
}

/** The ornamented band a surah opens with. */
@Composable
private fun SurahBanner(surahNumber: Int) {
    val c = LocalTf.current
    val surah = surahOf(surahNumber)
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(c.gold.soft(0.10f))
            .border(1.dp, c.gold.soft(0.5f), RoundedCornerShape(6.dp))
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TfText("❁", color = c.gold.soft(0.7f), size = 11f)
            TfText(
                "سورة ${surah?.nameArabic ?: ""}",
                color = c.gold, size = 15f, family = Fonts.serif, maxLines = 1,
            )
            TfText("❁", color = c.gold.soft(0.7f), size = 11f)
        }
    }
}

/** Previous / next page, and where in the surah the page sits. */
@Composable
private fun PageTurnBar(state: MushafUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TurnButton("chevronRight", state.hasPrevious, onPrevious)
        TfText(
            state.pageNumber?.let { "Page $it · ${state.index + 1} of ${state.pageNumbers.size}" } ?: "",
            color = c.fg3, size = 11.5f, family = Fonts.mono,
        )
        TurnButton("chevronLeft", state.hasNext, onNext)
    }
}

@Composable
private fun TurnButton(icon: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.card3)
            .then(if (enabled) pressScale(onClick, 0.9f) else Modifier),
        contentAlignment = Alignment.Center,
    ) { TfIcon(icon, size = 16, color = if (enabled) c.fg2 else c.fg4) }
}

/**
 * How large the line can be set.
 *
 * A real mushaf is typeset once, per page, by hand; we have one type size per line and a
 * measure that changes with the device. Estimating from the character count keeps a dense
 * line inside its measure and lets a short one — the closing line of a surah, say — stay
 * as large as the rest of the page rather than shrinking to match.
 */
private fun fontSizeFor(words: List<MushafWord>, lineCount: Int): Float {
    val chars = words.sumOf { it.text.length } + words.size
    val size = when {
        chars <= 26 -> 22f
        chars <= 34 -> 20f
        chars <= 42 -> 18f
        chars <= 52 -> 16f
        else -> 14f
    }
    // A page carrying a banner and a Basmala has less height for its verse lines.
    return if (lineCount > LINES_PER_PAGE) size * 0.94f else size
}

private const val PAGE_TURN_SLOP = 60f
