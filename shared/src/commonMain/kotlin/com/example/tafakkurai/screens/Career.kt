package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*

@Composable
fun CareerScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    var tab by remember { mutableStateOf("roadmap") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Career", onBack)
//        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, top = 10.dp, bottom = 4.dp)) {
//            Overline("TARGET ROLE")
//            TfText(TCAREER.role, color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display, modifier = Modifier.padding(top = 2.dp))
//        }
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 8.dp)) {
            Segmented(listOf("roadmap" to "Roadmap", "cv" to "CV", "interview" to "Interview"), tab, { tab = it })
        }
        when (tab) {
            "roadmap" -> RoadmapTab()
            "cv" -> CvTab()
            else -> InterviewTab()
        }
    }
}

@Composable
private fun ColumnScope.RoadmapTab() {
    val c = LocalTf.current
    ScrollBody {
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TCAREER.roadmap.forEach { ph ->
                val col = when { ph.done -> c.green; ph.current -> c.accent; else -> c.fg3 }
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Pill(ph.phase, col, col.soft(0.16f))
                        TfText(ph.title, color = c.fg, size = 15.5f, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (ph.done) TfIcon("checkCircle", size = 18, color = c.green)
                    }
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ph.items.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                TfIcon(if (ph.done) "checkCircle" else "circle", size = 16, color = if (ph.done) c.green else c.fg3)
                                TfText(item, color = if (ph.done) c.fg3 else c.fg2, size = 14f)
                            }
                        }
                    }
                }
            }
        }
        SectionTitle("University matches")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TCAREER.universities.forEach { u ->
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge("graduationCap", c.named("purple"), size = 44)
                        Column(Modifier.weight(1f)) {
                            TfText(u.name, color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                            TfText("${u.program} · deadline ${u.deadline}", color = c.fg2, size = 12.5f)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            TfText("${u.match}%", color = c.green, size = 17f, weight = FontWeight.Bold, family = Fonts.mono)
                            TfText("match", color = c.fg3, size = 11f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CvTab() {
    val c = LocalTf.current
    ScrollBody {
        InsetCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RingGauge(TCAREER.cvScore / 100f, c.accent, c.card3, strokeWidth = 7f, size = 84) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TfText("${TCAREER.cvScore}", color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.mono)
                        TfText("CV score", color = c.fg3, size = 10f)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TfText("Strong, with room to sharpen", color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                    TfText("A few targeted edits would lift this above 85.", color = c.fg2, size = 12.5f, lineHeight = 1.4f)
                }
            }
        }
        SectionTitle("AI suggestions")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TCAREER.cvTips.forEach { tip ->
                Card(Modifier.fillMaxWidth(), pad = 13) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(c.accent.soft(0.14f)), contentAlignment = Alignment.Center) {
                            TfIcon("sparkles", size = 14, color = c.accent)
                        }
                        TfText(tip, color = c.fg, size = 14f, lineHeight = 1.45f, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) { PrimaryButton("Open CV editor") {} }
    }
}

@Composable
private fun ColumnScope.InterviewTab() {
    val c = LocalTf.current
    ScrollBody {
        InsetCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BreathOrb(size = 56, color = c.accent, purple = c.purple)
                Column(Modifier.weight(1f)) {
                    TfText("Mock interview", color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                    TfText("Rehearse out loud with me. I'll listen, then give kind, specific feedback.", color = c.fg2, size = 12.5f, lineHeight = 1.45f, modifier = Modifier.padding(top = 3.dp))
                }
                Box(Modifier.size(46.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    TfIcon("mic", size = 22, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
        SectionTitle("Practice questions")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TCAREER.interviews.forEach { iv ->
                Card(Modifier.fillMaxWidth(), onClick = {}) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Pill(iv.kind, c.named("purple"), c.named("purple").soft(0.16f))
                                Pill(iv.difficulty, c.fg2, c.card3)
                            }
                            TfText(iv.q, color = c.fg, size = 14.5f, weight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                        }
                        TfIcon("play", size = 18, color = c.accent)
                    }
                }
            }
        }
    }
}
