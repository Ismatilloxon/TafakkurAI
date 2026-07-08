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
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*
import kotlinx.coroutines.delay

@Composable
fun ProductivityScreen() {
    val c = LocalTf.current
    var tab by remember { mutableStateOf("tasks") }
    Column(Modifier.fillMaxSize()) {
        LargeTitle("Do")
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
            Segmented(
                listOf("tasks" to "Tasks", "habits" to "Habits", "focus" to "Focus"),
                tab, { tab = it },
            )
        }
        when (tab) {
            "tasks" -> TasksTab()
            "habits" -> HabitsTab()
            else -> FocusBody()
        }
    }
}

@Composable
fun FocusScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar("Focus", onBack)
        FocusBody()
    }
}

// ── Tasks ────────────────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.TasksTab() {
    val c = LocalTf.current
    val tasks = remember { mutableStateListOf<TaskItem>().apply { addAll(TTASKS) } }
    ScrollBody {
        ProactiveCard(
            "Two high-priority items are due today. I scheduled the auth refactor into your 8am focus block — your sharpest window.",
            modifier = Modifier.padding(horizontal = HPAD.dp),
        )
        val todo = tasks.filter { !it.done }
        val done = tasks.filter { it.done }
        if (todo.isEmpty()) EmptyNote("All clear. Nothing left to do — rest is part of the work.")
        else {
            SectionTitle("To-do")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                todo.forEach { t -> TaskRow(t) { idx -> tasks[tasks.indexOf(t)] = t.copy(done = !t.done) } }
                AddDashed("Add task")
            }
        }
        if (done.isNotEmpty()) {
            SectionTitle("Done")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                done.forEach { t -> TaskRow(t) { tasks[tasks.indexOf(t)] = t.copy(done = !t.done) } }
            }
        }
    }
}

@Composable
private fun TaskRow(t: TaskItem, onToggle: (Int) -> Unit) {
    val c = LocalTf.current
    val pri = when (t.priority) { "high" -> c.danger; "med" -> c.warn; else -> c.fg3 }
    Card(Modifier.fillMaxWidth(), pad = 13) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .border(1.5.dp, if (t.done) c.green else c.line, CircleShape)
                    .background(if (t.done) c.green else Color.Transparent)
                    .then(pressScale({ onToggle(0) }, 0.9f)),
                contentAlignment = Alignment.Center,
            ) { if (t.done) TfIcon("check", size = 14, color = Color.White) }
            Column(Modifier.weight(1f)) {
                TfText(t.title, color = if (t.done) c.fg3 else c.fg, size = 14.5f, weight = FontWeight.Medium, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(pri))
                    TfText(t.tag, color = c.fg3, size = 11.5f)
                    TfText("· ${t.due}", color = c.fg3, size = 11.5f)
                }
            }
        }
    }
}

// ── Habits ───────────────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.HabitsTab() {
    val c = LocalTf.current
    val habits = remember { mutableStateListOf<Habit>().apply { addAll(THABITS) } }
    ScrollBody {
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            habits.forEach { h ->
                val col = c.named(if (h.accent == "blue") "cyan" else h.accent)
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(h.icon, col, size = 42, iconSize = 20)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                TfText(h.name, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TfIcon("flame", size = 13, color = c.warn)
                                TfText("${h.streak}", color = c.fg2, size = 12.5f, family = Fonts.mono)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                h.week.forEach { d ->
                                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (d == 1) col else c.card3))
                                }
                            }
                        }
                        val idx = habits.indexOf(h)
                        Box(
                            Modifier.size(30.dp).clip(CircleShape)
                                .border(1.5.dp, if (h.done) c.green else c.line, CircleShape)
                                .background(if (h.done) c.green else Color.Transparent)
                                .then(pressScale({ habits[idx] = h.copy(done = !h.done) }, 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) { if (h.done) TfIcon("check", size = 15, color = Color.White) }
                    }
                }
            }
            AddDashed("New habit")
        }
    }
}

// ── Focus (Pomodoro) ─────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.FocusBody() {
    val c = LocalTf.current
    val total = 25 * 60
    var remaining by remember { mutableStateOf(total) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0) running = false
    }

    val mm = (remaining / 60).toString().padStart(2, '0')
    val ss = (remaining % 60).toString().padStart(2, '0')

    ScrollBody {
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                AmbientGlow(c.accent, opacity = 0.22f)
                RingGauge(1f - remaining.toFloat() / total, c.accent, c.card3, strokeWidth = 7f, size = 232) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Overline("FOCUS")
                        TfText("$mm:$ss", color = c.fg, size = 52f, weight = FontWeight.Bold, family = Fonts.mono, letterSpacing = 1f)
                        TfText("Deep work — API refactor", color = c.fg2, size = 12.5f)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusBtn("repeat", c.fg2, 52) { remaining = total; running = false }
            Box(
                Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(16.dp)).background(c.accent)
                    .then(pressScale({ running = !running }, 0.96f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TfIcon(if (running) "pause" else "play", size = 22, color = Color.White)
                    TfText(if (running) "Pause" else "Start", color = Color.White, size = 16f, weight = FontWeight.SemiBold)
                }
            }
            FocusBtn("coffee", c.warn, 52) { remaining = 5 * 60; running = false }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i ->
                    Box(Modifier.size(width = if (i < 3) 24.dp else 24.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < 3) c.accent else c.card3))
                }
            }
        }
        TfText("3 of 4 sessions today", color = c.fg3, size = 12.5f, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        SectionTitle("Up next")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TPLAN.filter { !it.done }.take(3).forEach { p ->
                Card(Modifier.fillMaxWidth(), pad = 13) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        TfText(p.time, color = c.fg3, size = 12f, family = Fonts.mono, modifier = Modifier.width(44.dp))
                        Column(Modifier.weight(1f)) {
                            TfText(p.title, color = c.fg, size = 14f, weight = FontWeight.Medium)
                            TfText(p.sub, color = c.fg3, size = 11.5f)
                        }
                        Pill(p.dur, c.fg2, c.card3)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusBtn(icon: String, color: Color, size: Int, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c.card2).border(0.5.dp, c.sep, CircleShape)
            .then(pressScale(onClick, 0.9f)),
        contentAlignment = Alignment.Center,
    ) { TfIcon(icon, size = 22, color = color) }
}

@Composable
fun EmptyNote(text: String) {
    val c = LocalTf.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), contentAlignment = Alignment.Center) {
        TfText(text, color = c.fg3, size = 14f, lineHeight = 1.5f, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
