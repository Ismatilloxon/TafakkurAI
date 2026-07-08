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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.vm.LearnViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun LearnScreen(onLesson: () -> Unit, onSubject: (Subject) -> Unit) {
    val c = LocalTf.current
    val vm: LearnViewModel = viewModel { LearnViewModel(getKoin().get()) }
    Column(Modifier.fillMaxSize()) {
        LargeTitle("Learn · Taʼlim")
        ScrollBody {
            ProactiveCard(
                "You're 3 lessons from finishing TypeScript Module 3 — open the AI lesson.",
                action = "Open lesson", onAction = onLesson,
                modifier = Modifier.padding(horizontal = HPAD.dp),
            )
            // English speaking drill
            InsetCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BreathOrb(size = 56, color = c.cyan, purple = c.purple)
                    Column(Modifier.weight(1f)) {
                        Overline("DAILY DRILL · INGLIZ TILI")
                        TfText("Speaking — describe a trend", color = c.fg, size = 15.5f, weight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                        TfText("90 seconds. I'll score your fluency and pronunciation.", color = c.fg2, size = 12.5f, lineHeight = 1.4f, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(Modifier.size(46.dp).clip(CircleShape).background(c.cyan), contentAlignment = Alignment.Center) {
                        TfIcon("mic", size = 22, color = Color.White)
                    }
                }
            }
            SectionTitle("Subjects")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.subjects.forEach { s -> SubjectRow(s) { onSubject(s) } }
                AddDashed("Add a subject")
            }
        }
    }
}

@Composable
private fun SubjectRow(s: Subject, onOpen: () -> Unit) {
    val c = LocalTf.current
    val col = c.named(if (s.accent == "blue") "cyan" else s.accent)
    Card(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            IconBadge(s.icon, col, size = 46, iconSize = 22)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TfText(s.name, color = c.fg, size = 16f, weight = FontWeight.SemiBold)
                    TfText(s.native, color = c.fg3, size = 12f)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        TfIcon("flame", size = 12, color = c.warn)
                        TfText("${s.streak}", color = c.fg2, size = 12f, family = Fonts.mono)
                    }
                }
                TfText(s.level, color = c.fg2, size = 12.5f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgressBar(s.progress, col, modifier = Modifier.weight(1f))
                    TfText("${(s.progress * 100).toInt()}%", color = c.fg2, size = 11.5f, family = Fonts.mono)
                }
            }
        }
    }
}

// ── Subject detail ───────────────────────────────────────────────────────────
@Composable
fun SubjectScreen(s: Subject, onBack: () -> Unit, onLesson: () -> Unit) {
    val c = LocalTf.current
    val col = c.named(if (s.accent == "blue") "cyan" else s.accent)
    Column(Modifier.fillMaxSize()) {
        BackBar(s.name, onBack)
        ScrollBody {
            // header: badge + level + streak
            InsetCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(s.icon, col, size = 56, iconSize = 26)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            TfText(s.name, color = c.fg, size = 19f, weight = FontWeight.SemiBold)
                            TfText(s.native, color = c.fg3, size = 12.5f)
                        }
                        TfText(s.level, color = c.fg2, size = 13f)
                    }
                    Pill("${s.streak}-day", c.warn, c.warn.soft(0.16f), leading = { TfIcon("flame", size = 11, color = c.warn) })
                }
            }
            // progress
            InsetCard {
                Overline("PROGRESS")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ProgressBar(s.progress, col, modifier = Modifier.weight(1f))
                    TfText("${(s.progress * 100).toInt()}%", color = c.fg2, size = 12f, family = Fonts.mono)
                }
            }
            // up next → lesson
            ProactiveCard(
                "Up next · ${s.next}",
                action = "Open lesson", onAction = onLesson,
                modifier = Modifier.padding(horizontal = HPAD.dp),
            )
            Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
                PrimaryButton("Continue learning", onClick = onLesson)
            }
        }
    }
}

@Composable
fun AddDashed(label: String, onClick: () -> Unit = {}) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .then(pressScale(onClick, 0.98f)).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        TfIcon("plus", size = 16, color = c.fg2)
        Spacer(Modifier.width(7.dp))
        TfText(label, color = c.fg2, size = 14f, weight = FontWeight.Medium)
    }
}

// ── AI lesson + quiz ─────────────────────────────────────────────────────────
private enum class LessonStage { READ, QUIZ, RESULT }

@Composable
fun LessonScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    var stage by remember { mutableStateOf(LessonStage.READ) }
    var qIndex by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var correct by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        BackBar(TLESSON.subject, onBack)
        when (stage) {
            LessonStage.READ -> ScrollBody {
                Box(Modifier.padding(horizontal = HPAD.dp)) { Pill("Generated for you", c.accent, c.accent.soft(0.16f), leading = { TfIcon("sparkles", size = 11, color = c.accent) }) }
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Overline("${TLESSON.subject} · ${TLESSON.module} · ${TLESSON.index} · ${TLESSON.minutes} MIN")
                    TfText(TLESSON.title, color = c.fg, size = 28f, family = Fonts.serif, lineHeight = 1.1f)
                    TfText(TLESSON.intro, color = c.fg2, size = 15f, lineHeight = 1.6f, modifier = Modifier.padding(top = 4.dp))
                }
                TLESSON.blocks.forEach { b ->
                    when (b.type) {
                        "concept" -> InsetCard {
                            if (b.heading != null) TfText(b.heading, color = c.fg, size = 16f, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                            if (b.body != null) TfText(b.body, color = c.fg2, size = 14.5f, lineHeight = 1.6f)
                        }
                        "code" -> CodeBlock(b.lang ?: "code", b.code ?: "")
                    }
                }
                Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) { PrimaryButton("Start quiz · ${TQUIZ.size} questions") { stage = LessonStage.QUIZ } }
            }

            LessonStage.QUIZ -> {
                val item = TQUIZ[qIndex]
                ScrollBody {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgressBar((qIndex + 1f) / TQUIZ.size, c.accent, modifier = Modifier.weight(1f))
                        TfText("${qIndex + 1}/${TQUIZ.size}", color = c.fg2, size = 12f, family = Fonts.mono)
                    }
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        TfText(item.q, color = c.fg, size = 19f, family = Fonts.serif, lineHeight = 1.3f)
                    }
                    Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        item.options.forEachIndexed { i, opt ->
                            val state = when {
                                picked == null -> 0
                                i == item.answer -> 1   // correct
                                i == picked -> 2        // wrong pick
                                else -> 0
                            }
                            val border = when (state) { 1 -> c.green; 2 -> c.danger; else -> c.line }
                            val bg = when (state) { 1 -> c.green.soft(0.12f); 2 -> c.danger.soft(0.12f); else -> c.card }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(bg)
                                    .border(if (state == 0) 0.5.dp else 1.dp, border, RoundedCornerShape(13.dp))
                                    .then(pressScale({ if (picked == null) { picked = i; if (i == item.answer) correct++ } }, 0.99f))
                                    .padding(15.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                TfText(opt, color = c.fg, size = 14.5f, lineHeight = 1.4f, modifier = Modifier.weight(1f))
                                if (state == 1) TfIcon("checkCircle", size = 18, color = c.green)
                                if (state == 2) TfIcon("x", size = 18, color = c.danger)
                            }
                        }
                    }
                    if (picked != null) {
                        InsetCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.accent, c.purple))), contentAlignment = Alignment.Center) {
                                    TfIcon("sparkles", size = 14, color = Color.White)
                                }
                                Column {
                                    Overline("WHY")
                                    TfText(item.explain, color = c.fg2, size = 13.5f, lineHeight = 1.5f, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
                            PrimaryButton(if (qIndex < TQUIZ.size - 1) "Next" else "See results") {
                                if (qIndex < TQUIZ.size - 1) { qIndex++; picked = null } else stage = LessonStage.RESULT
                            }
                        }
                    }
                }
            }

            LessonStage.RESULT -> ScrollBody {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BreathOrb(size = 96, color = c.accent, purple = c.purple)
                    TfText("$correct / ${TQUIZ.size} correct", color = c.fg, size = 26f, weight = FontWeight.Bold, family = Fonts.display)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("+40 XP", c.gold, c.gold.soft(0.16f), leading = { TfIcon("star", size = 11, color = c.gold) })
                        Pill("${TUSER.streak}-day streak", c.warn, c.warn.soft(0.16f), leading = { TfIcon("flame", size = 11, color = c.warn) })
                    }
                }
                InsetCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.accent, c.purple))), contentAlignment = Alignment.Center) {
                            TfIcon("sparkles", size = 15, color = Color.White)
                        }
                        TfText(
                            "Solid grasp of generics. I added a short task to your plan: refactor your `first()` helper to be generic — applying it locks it in.",
                            color = c.fg2, size = 14f, lineHeight = 1.55f,
                        )
                    }
                }
                Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) { PrimaryButton("Back to Learn", onClick = onBack) }
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val c = LocalTf.current
    Column(Modifier.padding(horizontal = HPAD.dp).fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF0B0E14)).border(0.5.dp, c.hair, RoundedCornerShape(13.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE5604D)))
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE0B93B)))
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF2BB673)))
            Spacer(Modifier.weight(1f))
            TfText(lang, color = Color(0xFF8893A8), size = 10.5f, family = Fonts.mono, letterSpacing = 0.5f)
        }
        TfText(code, color = Color(0xFFCBD5E8), size = 12.5f, family = Fonts.mono, lineHeight = 1.55f, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.accent)
            .then(pressScale(onClick, 0.97f)).padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
    ) { TfText(label, color = Color.White, size = 15.5f, weight = FontWeight.SemiBold) }
}
