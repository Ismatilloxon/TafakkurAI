package com.example.tafakkurai.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.i18n.Language
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.Strings
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.Overline
import com.example.tafakkurai.ui.TfIcon
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.pressScale
import com.example.tafakkurai.ui.soft

/**
 * First screen on a fresh install, before the welcome carousel — everything after it is
 * written in whichever language is picked here, so it cannot come later.
 *
 * Each option previews itself: tapping a language re-renders this screen in it immediately,
 * so the choice can be judged before it is committed. Names are always in the language
 * itself, since someone who cannot read the current interface language must still find
 * their own.
 */
@Composable
fun LanguageScreen(initial: Language, onChoose: (Language) -> Unit) {
    val c = LocalTf.current
    var selected by remember { mutableStateOf(initial) }

    // Local preview: the labels below follow the highlighted option, not the saved one.
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStrings provides Strings.of(selected),
    ) {
        Column(
            Modifier.fillMaxSize().background(c.bg1).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Overline(tr("lang.overline"))
            TfText(
                tr("lang.title"),
                color = c.fg, size = 26f, weight = FontWeight.Bold, family = Fonts.display,
                modifier = Modifier.padding(top = 6.dp),
            )
            TfText(
                tr("lang.subtitle"),
                color = c.fg2, size = 13.5f, lineHeight = 1.5f,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Language.entries.forEach { language ->
                    LanguageRow(
                        language = language,
                        selected = language == selected,
                        onClick = { selected = language },
                    )
                }
            }

            Spacer(Modifier.padding(top = 12.dp))
            Box(
                Modifier.fillMaxWidth().padding(top = 22.dp)
                    .clip(RoundedCornerShape(14.dp)).background(c.accent)
                    .then(pressScale({ onChoose(selected) }, 0.97f))
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                TfText(tr("lang.continue"), color = Color.White, size = 15f, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LanguageRow(language: Language, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accent.soft(0.12f) else c.card)
            .border(
                0.5.dp,
                if (selected) c.accent.soft(0.55f) else c.sep,
                RoundedCornerShape(14.dp),
            )
            .then(pressScale(onClick, 0.985f))
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TfText(language.nativeName, color = c.fg, size = 16f, weight = FontWeight.Medium)
            TfText(
                tr(language.detailKey), color = c.fg3, size = 12f,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { TfIcon("check", size = 13, color = Color.White) }
        }
    }
}
