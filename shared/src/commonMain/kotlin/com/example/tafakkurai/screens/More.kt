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
import com.example.tafakkurai.ui.*

private data class SubMod(val name: String, val sub: String, val icon: String, val route: String)
private data class Dimension(val name: String, val native: String, val tagline: String, val color: String, val score: Int, val mods: List<SubMod>)

private val DIMENSIONS = listOf(
    Dimension("Spiritual", "Maʼnaviy", "Salah, Qur'an, dhikr, reflection", "gold", 84, listOf(
        SubMod("Prayer tracker", "5 daily salah", "moonStars", "prayer"),
        SubMod("Tafakkur journal", "Reflect & breathe", "penLine", "tafakkur"),
        SubMod("Gratitude", "Shukr — 3 daily", "leaf", "gratitude"),
        SubMod("Qurʼan & dhikr", "Read · tasbih", "book", "quran"),
    )),
    Dimension("Physical", "Jismoniy", "Move, rest, train, recover", "green", 70, listOf(
        SubMod("Habits", "Streaks & routines", "activity", "do"),
        SubMod("Focus", "Pomodoro space", "target", "focus"),
        SubMod("Sleep & rest", "Score & stages", "bedDouble", "sleep"),
        SubMod("Sport & Gym", "Train & track", "dumbbell", "sport"),
    )),
    Dimension("Educational", "Taʼlim", "Read, study, build, teach", "purple", 42, listOf(
        SubMod("Subjects", "4 in progress", "bookOpen", "learn"),
        SubMod("AI lessons", "Generated for you", "sparkles", "lesson"),
        SubMod("Quizzes", "Spaced repetition", "checkSquare", "quizzes"),
        SubMod("Career", "CV · interviews", "briefcase", "career"),
    )),
    Dimension("Social", "Ijtimoiy", "Family, ummah, ties of kinship", "cyan", 55, listOf(
        SubMod("Thoughts", "From your circle", "messageCircle", "thoughts"),
        SubMod("Circles", "Shared goals", "users", "social"),
        SubMod("Mentors", "A few steps ahead", "graduationCap", "mentors"),
    )),
    Dimension("Financial", "Moliyaviy", "Earn lawfully, give sadaqah", "gold", 62, listOf(
        SubMod("Portfolio", "Net worth", "wallet", "finance"),
        SubMod("Money habits", "Save & track", "repeat", "finance"),
        SubMod("Insights", "Educational only", "lineChart", "finance"),
        SubMod("Sadaqah", "Giving intentions", "heart", "sadaqah"),
    )),
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        LargeTitle("Explore", subtitle = "Oʻzingni rivojlantir")
        ScrollBody(gap = 22) {
            ProactiveCard(
                "Educational is your lowest dimension this month, at 42. Twenty minutes of reading after Isha would bring it back into balance.",
                action = "Open a lesson", onAction = { onOpen("lesson") },
                modifier = Modifier.padding(horizontal = HPAD.dp),
            )
            DIMENSIONS.forEach { d -> DimensionSection(d, onOpen) }
            // Settings footer
            Card(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp), onClick = { onOpen("settings") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge("settings", c.fg2, size = 42)
                    Column(Modifier.weight(1f)) {
                        TfText("Settings & privacy", color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                        TfText("On-device AI · sync · data", color = c.fg2, size = 12.5f)
                    }
                    TfIcon("chevronRight", size = 18, color = c.fg3)
                }
            }
        }
    }
}

@Composable
private fun DimensionSection(d: Dimension, onOpen: (String) -> Unit) {
    val c = LocalTf.current
    val col = c.named(d.color)
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(d.mods.first().icon, col, size = 40)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TfText(d.name, color = c.fg, size = 18f, weight = FontWeight.Bold, family = Fonts.display)
                    TfText(d.native, color = c.fg3, size = 12.5f)
                }
                TfText(d.tagline, color = c.fg2, size = 12.5f, modifier = Modifier.padding(top = 1.dp))
            }
            TfText("${d.score}", color = col, size = 20f, weight = FontWeight.Bold, family = Fonts.mono)
        }
        ProgressBar(d.score / 100f, col, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        // 2-col grid
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            d.mods.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { m ->
                        Card(Modifier.weight(1f).heightIn(min = 96.dp), onClick = { onOpen(m.route) }) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(col.soft(0.14f)), contentAlignment = Alignment.Center) {
                                TfIcon(m.icon, size = 19, color = col)
                            }
                            Spacer(Modifier.height(10.dp))
                            TfText(m.name, color = c.fg, size = 14f, weight = FontWeight.SemiBold)
                            TfText(m.sub, color = c.fg3, size = 11.5f, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
