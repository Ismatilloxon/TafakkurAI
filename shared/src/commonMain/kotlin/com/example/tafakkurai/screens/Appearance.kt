package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.Tweaks
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.i18n.tr

@Composable
fun AppearanceScreen(tweaks: Tweaks, onBack: () -> Unit) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("appearance.title"), onBack)
        ScrollBody {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TfText(tr("appearance.heading"), color = c.fg, size = 26f, weight = FontWeight.Bold, family = Fonts.display)
                TfText(tr("appearance.subtitle"), color = c.fg2, size = 13.5f, lineHeight = 1.5f)
            }
            Box(Modifier.padding(horizontal = HPAD.dp)) {
                Segmented(listOf("dark" to tr("appearance.dark"), "light" to tr("appearance.light")), tweaks.mode, { tweaks.mode = it })
            }
            val showMode = tweaks.mode
            val selected = if (showMode == "light") tweaks.lightTheme else tweaks.darkTheme
            val themes = ThemeMetaList.filter { it.mode == showMode }
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                themes.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { meta ->
                            val col = TfThemes.getValue(meta.key)
                            val on = selected == meta.key
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                    .background(col.bg1)
                                    .border(if (on) 2.dp else 0.5.dp, if (on) c.accent else col.line, RoundedCornerShape(18.dp))
                                    .then(pressScale({ if (showMode == "light") tweaks.lightTheme = meta.key else tweaks.darkTheme = meta.key }, 0.97f))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ThemePreview(col)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        TfText(meta.name, color = col.fg, size = 15f, weight = FontWeight.SemiBold, family = Fonts.display)
                                        TfText(meta.concept, color = col.fg2, size = 11.5f)
                                    }
                                    if (on) Box(Modifier.size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                                        TfIcon("check", size = 13, color = Color.White)
                                    }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreview(col: TfColors) {
    Row(
        Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(12.dp)).background(col.card).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        RingGauge(0.7f, col.teal, col.card3, strokeWidth = 4f, size = 40) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(col.gold))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.8f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(col.teal))
            Box(Modifier.fillMaxWidth(0.55f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(col.gold))
            Box(Modifier.fillMaxWidth(0.65f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(col.purple))
        }
    }
}
