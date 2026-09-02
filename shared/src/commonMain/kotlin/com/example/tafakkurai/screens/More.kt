package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*

private data class MoreModule(
    val titleKey: String,
    val detailKey: String,
    val icon: String,
    val route: String,
)

private data class MoreSection(
    val titleKey: String,
    val detailKey: String,
    val color: String,
    val icon: String,
    val modules: List<MoreModule>,
)

/** Only destinations with a connected implementation appear here. */
private val MORE_SECTIONS = listOf(
    MoreSection(
        titleKey = "more.section.spiritual",
        detailKey = "more.section.spiritual_detail",
        color = "gold",
        icon = "moonStars",
        modules = listOf(
            MoreModule("more.module.prayer", "more.module.prayer_detail", "moonStars", "prayer"),
            MoreModule("more.module.journal", "more.module.journal_detail", "penLine", "tafakkur"),
            MoreModule("more.module.gratitude", "more.module.gratitude_detail", "leaf", "gratitude"),
            MoreModule("more.module.quran", "more.module.quran_detail", "book", "quran"),
        ),
    ),
    MoreSection(
        titleKey = "more.section.physical",
        detailKey = "more.section.physical_detail",
        color = "green",
        icon = "activity",
        modules = listOf(
            MoreModule("more.module.habits", "more.module.habits_detail", "repeat", "habits"),
            MoreModule("more.module.focus", "more.module.focus_detail", "target", "focus"),
            MoreModule("more.module.sleep", "more.module.sleep_detail", "bedDouble", "sleep"),
            MoreModule("more.module.sport", "more.module.sport_detail", "activity", "sport"),
        ),
    ),
    MoreSection(
        titleKey = "more.section.learning",
        detailKey = "more.section.learning_detail",
        color = "purple",
        icon = "bookOpen",
        modules = listOf(
            MoreModule("more.module.learn", "more.module.learn_detail", "bookOpen", "learn"),
            MoreModule("more.module.hifz", "more.module.hifz_detail", "bookmark", "hifz"),
            MoreModule("more.module.quran_quizzes", "more.module.quran_quizzes_detail", "graduationCap", "quran:quizzes"),
        ),
    ),
    MoreSection(
        titleKey = "more.section.social",
        detailKey = "more.section.social_detail",
        color = "cyan",
        icon = "users",
        modules = listOf(
            MoreModule("more.module.thoughts", "more.module.thoughts_detail", "messageCircle", "thoughts"),
            MoreModule("more.module.community", "more.module.community_detail", "users", "social"),
        ),
    ),
    MoreSection(
        titleKey = "more.section.finance",
        detailKey = "more.section.finance_detail",
        color = "gold",
        icon = "wallet",
        modules = listOf(
            MoreModule("more.module.finance", "more.module.finance_detail", "wallet", "finance"),
        ),
    ),
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        LargeTitle(tr("more.title"), subtitle = tr("more.subtitle"))
        ScrollBody(gap = 22) {
            MORE_SECTIONS.forEach { section -> MoreSectionContent(section, onOpen) }
            Card(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                onClick = { onOpen("settings") },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBadge("settings", c.accent, size = 42)
                    Column(Modifier.weight(1f)) {
                        TfText(tr("more.settings"), color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                        TfText(tr("more.settings_detail"), color = c.fg2, size = 12.5f)
                    }
                    TfIcon("chevronRight", size = 18, color = c.fg3)
                }
            }
        }
    }
}

@Composable
private fun MoreSectionContent(section: MoreSection, onOpen: (String) -> Unit) {
    val c = LocalTf.current
    val color = c.named(section.color)
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(section.icon, color, size = 40)
            Column(Modifier.weight(1f)) {
                TfText(tr(section.titleKey), color = c.fg, size = 18f, weight = FontWeight.Bold, family = Fonts.display)
                TfText(tr(section.detailKey), color = c.fg2, size = 12.5f, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.modules.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { module ->
                        Card(
                            Modifier.weight(1f).heightIn(min = 96.dp),
                            onClick = { onOpen(module.route) },
                        ) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(color.soft(0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                TfIcon(module.icon, size = 19, color = color)
                            }
                            Spacer(Modifier.height(10.dp))
                            TfText(tr(module.titleKey), color = c.fg, size = 14f, weight = FontWeight.SemiBold)
                            TfText(tr(module.detailKey), color = c.fg3, size = 11.5f, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
