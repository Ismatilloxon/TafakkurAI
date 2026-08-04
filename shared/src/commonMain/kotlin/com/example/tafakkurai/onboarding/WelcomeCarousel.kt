package com.example.tafakkurai.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.ui.*
import kotlinx.coroutines.launch
import com.example.tafakkurai.i18n.tr

/**
 * The 8-slide welcome carousel. tr("onb.skip") skips to login on any slide; the last
 * slide's CTA (tr("onb.start")) also finishes. [onFinish] advances the flow to Login.
 */
@Composable
fun WelcomeCarousel(onSkip: () -> Unit, onFinish: () -> Unit) {
    val c = LocalTf.current
    val pager = rememberPagerState { ONB_SLIDES.size }
    val scope = rememberCoroutineScope()
    val last = ONB_SLIDES.lastIndex
    val current = ONB_SLIDES[pager.currentPage]
    val accent = accentOf(c, current.accent)

    Column(Modifier.fillMaxSize()) {
        // top chrome — skip hides on the last slide
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(40.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pager.currentPage < last) SkipButton(onSkip)
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            SlideBody(ONB_SLIDES[page])
        }

        // bottom chrome — dots + CTA
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PageDots(ONB_SLIDES.size, pager.currentPage, accent)
            OnbCta(
                label = if (pager.currentPage == last) tr("onb.start") else tr("onb.continue"),
                accent = accent,
                arrow = pager.currentPage != last,
            ) {
                if (pager.currentPage == last) onFinish()
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun SlideBody(slide: Slide) {
    val c = LocalTf.current
    val accent = accentOf(c, slide.accent)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            SlideArtView(slide, accent)
        }
        // copy block
        Column(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TfText(
                slide.overline, color = accent, size = 10.5f,
                weight = FontWeight.SemiBold, family = Fonts.mono, letterSpacing = 1f,
            )
            TfText(
                slide.title, color = c.fg, size = slide.titleSize,
                weight = if (slide.serifTitle) FontWeight.Normal else FontWeight.Bold,
                family = if (slide.serifTitle) Fonts.serif else Fonts.display,
                align = TextAlign.Center, lineHeight = 1.1f,
            )
            TfText(
                slide.body, color = c.fg2, size = 15f, lineHeight = 1.55f,
                align = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (slide.chips.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    slide.chips.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { PillChip(it, accent) }
                        }
                    }
                }
            }
        }
    }
}

/** Routes a slide to its hero art. */
@Composable
private fun SlideArtView(slide: Slide, accent: Color) {
    val c = LocalTf.current
    when (slide.art) {
        SlideArt.ORB -> Box(contentAlignment = Alignment.Center) {
            GlowBehind(accent, size = 300, opacity = 0.22f)
            BreathingOrb(size = 156, accent = accent, purple = c.purple)
        }

        SlideArt.RADAR -> Box(contentAlignment = Alignment.Center) {
            GlowBehind(accent, size = 300, opacity = 0.14f)
            BalanceRadar(
                axes = listOf(
                    RadarAxis(tr("onb.aspect.spiritual"), 0.82f, c.gold),
                    RadarAxis(tr("onb.aspect.physical"), 0.55f, c.green),
                    RadarAxis(tr("onb.aspect.educational"), 0.70f, c.purple),
                    RadarAxis(tr("onb.aspect.social"), 0.60f, c.cyan),
                    RadarAxis(tr("onb.aspect.financial"), 0.40f, c.warn),
                ),
                fill = c.teal,
                size = 236,
            )
        }

        SlideArt.PRAYER -> {
            val prayers = listOf(
                PrayerTime("Bomdod", "05:12", 312, c.gold),
                PrayerTime("Peshin", "12:51", 771, c.gold),
                PrayerTime("Asr", "17:34", 1054, c.gold),
                PrayerTime("Shom", "20:08", 1208, c.gold),
                PrayerTime("Xufton", "21:28", 1288, c.gold),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    GlowBehind(accent, size = 260, opacity = 0.16f)
                    PrayerDayRing(
                        prayers = prayers,
                        fajrMin = 312, maghribMin = 1208, nowMin = 581,
                        nextName = "Peshin", countdown = "3h 10m",
                        accent = accent, size = 200,
                    )
                }
                PrayerTimesStrip(prayers, nextIndex = 1)
            }
        }

        SlideArt.TILES -> AspectTileGrid(slide.tiles, accent, columns = slide.tileCols)
    }
}
