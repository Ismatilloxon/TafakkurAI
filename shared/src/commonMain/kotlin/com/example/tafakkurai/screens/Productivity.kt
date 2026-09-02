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
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.habits.HabitStore
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.tasks.data.TaskStore
import com.example.tafakkurai.tasks.domain.CreateTaskCommand
import com.example.tafakkurai.tasks.domain.ProductivityTask
import com.example.tafakkurai.tasks.domain.TaskPriority
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfAddButton
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.ui.components.TfSecondaryButton
import com.example.tafakkurai.ui.components.TfTextField
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.mp.KoinPlatform.getKoin

@Composable
fun ProductivityScreen(onOpenHabits: () -> Unit = {}) {
    val c = LocalTf.current
    val activity = remember { getKoin().get<ActivityLog>() }
    var tab by remember { mutableStateOf("tasks") }
    Column(Modifier.fillMaxSize()) {
        LargeTitle(tr("productivity.title"))
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
            Segmented(
                listOf(
                    "tasks" to tr("productivity.tab.tasks"),
                    "habits" to tr("productivity.tab.habits"),
                    "focus" to tr("productivity.tab.focus"),
                ),
                tab, {
                    tab = it
                    activity.record("productivity", "productivity_tab_selected", buildJsonObject { put("tab", it) })
                },
            )
        }
        when (tab) {
            "tasks" -> TasksTab()
            "habits" -> HabitsTab(onOpenHabits)
            else -> FocusBody()
        }
    }
}

@Composable
fun FocusScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("productivity.tab.focus"), onBack)
        FocusBody()
    }
}

// ── Tasks ────────────────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.TasksTab() {
    val strings = LocalStrings.current
    val store = remember { getKoin().get<TaskStore>() }
    val revision = store.revision
    var adding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    val tasks = remember(revision) { store.tasks.toList() }

    fun save() {
        val created = store.create(CreateTaskCommand(title = title, priority = priority))
        if (created != null) {
            title = ""
            priority = TaskPriority.MEDIUM
            adding = false
        }
    }

    ScrollBody {
        val todo = tasks.filter { !it.completed }
        val done = tasks.filter { it.completed }
        if (todo.isEmpty() && !adding) EmptyNote(strings["tasks.empty"])
        else {
            SectionTitle(strings["tasks.todo"])
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                todo.forEach { task ->
                    key(task.id) { TaskRow(task) { store.toggle(task.id) } }
                }
                if (adding) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TfTextField(
                                value = title,
                                onValueChange = { title = it.take(200) },
                                placeholder = strings["tasks.title_placeholder"],
                                onImeAction = ::save,
                            )
                            Segmented(
                                TaskPriority.entries.map { it.id to strings["tasks.priority.${it.id}"] },
                                priority.id,
                                { value -> priority = TaskPriority.from(value) },
                            )
                            TfPrimaryButton(strings["tasks.save"], ::save, Modifier.fillMaxWidth())
                            TfSecondaryButton(
                                strings["common.cancel"],
                                onClick = {
                                    adding = false
                                    title = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    TfAddButton(strings["tasks.add"], onClick = { adding = true })
                }
            }
        }
        if (done.isNotEmpty()) {
            SectionTitle(strings["tasks.done"])
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                done.forEach { task ->
                    key(task.id) { TaskRow(task) { store.toggle(task.id) } }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(t: ProductivityTask, onToggle: () -> Unit) {
    val c = LocalTf.current
    val strings = LocalStrings.current
    val pri = when (t.priority) {
        TaskPriority.HIGH -> c.danger
        TaskPriority.MEDIUM -> c.warn
        TaskPriority.LOW -> c.fg3
    }
    Card(Modifier.fillMaxWidth(), pad = 13) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .border(1.5.dp, if (t.completed) c.green else c.line, CircleShape)
                    .background(if (t.completed) c.green else Color.Transparent)
                    .then(pressScale(onToggle, 0.9f)),
                contentAlignment = Alignment.Center,
            ) { if (t.completed) TfIcon("check", size = 14, color = Color.White) }
            Column(Modifier.weight(1f)) {
                TfText(t.title, color = if (t.completed) c.fg3 else c.fg, size = 14.5f, weight = FontWeight.Medium, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(pri))
                    TfText(strings["tasks.category.${t.categoryId}"], color = c.fg3, size = 11.5f)
                    TfText(
                        strings.format(
                            "tasks.due",
                            if (t.dueDate == deviceDateKey()) strings["tasks.today"] else t.dueDate,
                        ),
                        color = c.fg3,
                        size = 11.5f,
                    )
                }
            }
        }
    }
}

// ── Habits ───────────────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.HabitsTab(onOpenHabits: () -> Unit) {
    val c = LocalTf.current
    val strings = LocalStrings.current
    val store = remember { getKoin().get<HabitStore>() }
    val revision = store.revision
    val habits = remember(revision) { store.scheduledOn(store.today()) }
    val stats = remember(revision) { store.statsAll() }
    ScrollBody {
        if (habits.isEmpty()) {
            EmptyNote(strings["habits.summary.empty"])
        }
        Column(
            Modifier.padding(horizontal = HPAD.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            habits.forEach { habit ->
                val stat = stats[habit.id]
                val done = stat?.isDoneToday == true
                val color = c.named(habit.colorKey)
                key(habit.id) {
                    Card(Modifier.fillMaxWidth(), onClick = onOpenHabits) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconBadge(habit.icon, color, size = 42, iconSize = 20)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TfText(
                                        habit.name,
                                        color = c.fg,
                                        size = 14.5f,
                                        weight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TfIcon("flame", size = 13, color = c.warn)
                                    TfText(
                                        "${stat?.current ?: 0}",
                                        color = c.fg2,
                                        size = 12.5f,
                                        family = Fonts.mono,
                                    )
                                }
                                ProgressBar(
                                    stat?.rate ?: 0f,
                                    color,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Box(
                                Modifier.size(30.dp).clip(CircleShape)
                                    .border(1.5.dp, if (done) c.green else c.line, CircleShape)
                                    .background(if (done) c.green else Color.Transparent)
                                    .then(pressScale({ store.toggle(habit.id) }, 0.9f)),
                                contentAlignment = Alignment.Center,
                            ) { if (done) TfIcon("check", size = 15, color = Color.White) }
                        }
                    }
                }
            }
            TfSecondaryButton(strings["habits.summary.manage"], onOpenHabits, Modifier.fillMaxWidth())
        }
    }
}

// ── Focus (Pomodoro) ─────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.FocusBody() {
    val c = LocalTf.current
    val strings = LocalStrings.current
    val activity = remember { getKoin().get<ActivityLog>() }
    val taskStore = remember { getKoin().get<TaskStore>() }
    val activityEvents by activity.events.collectAsState()
    val taskRevision = taskStore.revision
    val total = 25 * 60
    var remaining by remember { mutableStateOf(total) }
    var running by remember { mutableStateOf(false) }
    var isBreak by remember { mutableStateOf(false) }
    var completionRecorded by remember { mutableStateOf(false) }

    LaunchedEffect(running, isBreak) {
        while (running && remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        if (remaining == 0 && !completionRecorded) {
            activity.record(
                "productivity",
                if (isBreak) "focus_break_completed" else "focus_completed",
                buildJsonObject { put("seconds", if (isBreak) 5 * 60 else total) },
            )
            completionRecorded = true
            running = false
        }
    }

    val completedToday = remember(activityEvents) {
        activityEvents.count {
            it.feature == "productivity" && it.type == "focus_completed" &&
                it.localDate == deviceDateKey()
        }
    }
    val upcomingTasks = remember(taskRevision) {
        taskStore.onDate().filterNot { it.completed }.take(3)
    }

    val mm = (remaining / 60).toString().padStart(2, '0')
    val ss = (remaining % 60).toString().padStart(2, '0')
    val sessionTotal = if (isBreak) 5 * 60 else total

    ScrollBody {
        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                AmbientGlow(c.accent, opacity = 0.22f)
                RingGauge(1f - remaining.toFloat() / sessionTotal, c.accent, c.card3, strokeWidth = 7f, size = 232) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Overline(strings[if (isBreak) "focus.break" else "focus.label"])
                        TfText("$mm:$ss", color = c.fg, size = 52f, weight = FontWeight.Bold, family = Fonts.mono, letterSpacing = 1f)
                        TfText(
                            strings[if (isBreak) "focus.break_hint" else "focus.work_hint"],
                            color = c.fg2,
                            size = 12.5f,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FocusBtn("repeat", c.fg2, 52) {
                activity.record("productivity", "focus_reset", buildJsonObject { put("remainingSeconds", remaining) })
                remaining = total
                running = false
                isBreak = false
                completionRecorded = false
            }
            Box(
                Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(16.dp)).background(c.accent)
                    .then(pressScale({
                        if (!running && remaining == 0) {
                            remaining = if (isBreak) 5 * 60 else total
                            completionRecorded = false
                        }
                        val starting = !running
                        running = starting
                        activity.record(
                            "productivity",
                            when {
                                isBreak && starting -> "focus_break_started"
                                isBreak -> "focus_break_paused"
                                starting -> "focus_started"
                                else -> "focus_paused"
                            },
                            buildJsonObject { put("remainingSeconds", remaining) },
                        )
                    }, 0.96f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TfIcon(if (running) "pause" else "play", size = 22, color = Color.White)
                    TfText(
                        strings[if (running) "focus.pause" else "focus.start"],
                        color = Color.White,
                        size = 16f,
                        weight = FontWeight.SemiBold,
                    )
                }
            }
            FocusBtn("coffee", c.warn, 52) {
                activity.record("productivity", "focus_break_selected", buildJsonObject { put("seconds", 5 * 60) })
                remaining = 5 * 60
                running = false
                isBreak = true
                completionRecorded = false
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(FOCUS_DAILY_TARGET) { i ->
                    Box(
                        Modifier.size(width = 24.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < completedToday) c.accent else c.card3),
                    )
                }
            }
        }
        TfText(
            strings.format("focus.sessions_today", completedToday, FOCUS_DAILY_TARGET),
            color = c.fg3,
            size = 12.5f,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )
        SectionTitle(strings["focus.up_next"])
        if (upcomingTasks.isEmpty()) {
            EmptyNote(strings["focus.no_tasks"])
        } else Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            upcomingTasks.forEach { task ->
                Card(Modifier.fillMaxWidth(), pad = 13) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        TfText(
                            task.dueTime.ifBlank { "—" },
                            color = c.fg3,
                            size = 12f,
                            family = Fonts.mono,
                            modifier = Modifier.width(44.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            TfText(task.title, color = c.fg, size = 14f, weight = FontWeight.Medium)
                            TfText(
                                strings["tasks.category.${task.categoryId}"],
                                color = c.fg3,
                                size = 11.5f,
                            )
                        }
                        Pill(strings["focus.task"], c.fg2, c.card3)
                    }
                }
            }
        }
    }
}

private const val FOCUS_DAILY_TARGET = 4

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
