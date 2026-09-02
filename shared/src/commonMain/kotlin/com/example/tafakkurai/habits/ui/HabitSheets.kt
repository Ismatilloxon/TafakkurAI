package com.example.tafakkurai.habits.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.tafakkurai.habits.*
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*
import kotlin.math.roundToInt

// ═════════════════════════════════════════════════════════════════════════════
// Skip
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Skipping is not failing, and this sheet's whole job is to say so before the user taps.
 *
 * The reason chips are optional and they feed the coach's picture of the user's rhythm —
 * "Traveling" three Fridays running is a schedule problem, not a motivation one.
 */
@Composable
fun SkipSheet(habit: Habit, onConfirm: (reason: String) -> Unit) {
    val c = LocalTf.current
    var reason by remember { mutableStateOf("") }
    SheetHeader("repeat", c.cyan, tr("habits.action.skip_today"), habit.name)
    TfText(
        tr("habits.skip.detail"),
        color = c.fg2, size = 14f, lineHeight = 1.55f,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    HABIT_SKIP_REASON_KEYS.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            row.forEach { option ->
                val on = reason == option
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) c.cyan.soft(0.18f) else c.card)
                        .border(1.dp, if (on) c.cyan else c.sep, RoundedCornerShape(999.dp))
                        .then(pressScale({ reason = if (on) "" else option }, 0.96f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TfText(tr(option), color = if (on) c.fg else c.fg2, size = 13.5f, maxLines = 1)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    HabitButton(tr("habits.action.skip_today"), c.cyan, { onConfirm(reason) }, Modifier.padding(top = 12.dp))
}

// ═════════════════════════════════════════════════════════════════════════════
// Pause
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PauseSheet(habit: Habit, onConfirm: (days: Int) -> Unit) {
    val c = LocalTf.current
    var days by remember { mutableStateOf(7) }
    SheetHeader("pause", c.warn, tr("habits.pause.title"), habit.name)
    TfText(
        tr("habits.pause.detail"),
        color = c.fg2, size = 14f, lineHeight = 1.55f,
        modifier = Modifier.padding(bottom = 16.dp),
    )
    HABIT_PAUSE_OPTIONS.forEach { (value, label) ->
        val on = days == value
        Row(
            Modifier.fillMaxWidth().padding(bottom = 9.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (on) c.warn.soft(0.14f) else c.card)
                .border(1.dp, if (on) c.warn else c.sep, RoundedCornerShape(13.dp))
                .then(pressScale({ days = value }, 0.98f))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) c.warn else c.fg4, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(c.warn)) }
            TfText(tr(label), color = c.fg, size = 15f, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
    HabitButton(tr("habits.action.pause"), c.warn, { onConfirm(days) }, Modifier.padding(top = 12.dp))
}

// ═════════════════════════════════════════════════════════════════════════════
// Reminder
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ReminderSheet(habit: Habit, onConfirm: (enabled: Boolean, time: String, smart: Boolean) -> Unit) {
    val c = LocalTf.current
    val tint = c.named(habit.colorKey)
    var enabled by remember { mutableStateOf(habit.reminderEnabled) }
    var time by remember { mutableStateOf(habit.reminderTime.ifBlank { habit.timeLocal.ifBlank { "07:00" } }) }
    var smart by remember { mutableStateOf(habit.smartTiming) }

    SheetHeader("bell", tint, tr("habits.action.reminder"), habit.name)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(13.dp)).background(c.card)
            .border(0.5.dp, c.sep, RoundedCornerShape(13.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TfText(tr("habits.reminder.enable"), color = c.fg, size = 15f, modifier = Modifier.weight(1f))
        TfSwitch(enabled, tint) { enabled = it }
    }

    if (enabled) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HABIT_TIME_CHOICES.forEach { option ->
                val on = time == option
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (on) tint.soft(0.18f) else c.card)
                        .border(1.dp, if (on) tint else c.sep, RoundedCornerShape(10.dp))
                        .then(pressScale({ time = option }, 0.94f))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    TfText(
                        option, color = if (on) tint else c.fg2, size = 14f,
                        weight = if (on) FontWeight.Bold else FontWeight.Medium, family = Fonts.mono,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 20.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.purple.soft(0.08f))
                .border(0.5.dp, c.purple.soft(0.35f), RoundedCornerShape(13.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(c.purple, tint))),
                contentAlignment = Alignment.Center,
            ) { TfIcon("sparkles", size = 13, color = Color.White) }
            Column(Modifier.weight(1f)) {
                TfText(
                    tr("habits.reminder.smart_detail"),
                    color = c.fg, size = 13.5f, lineHeight = 1.45f,
                )
                Row(
                    Modifier.padding(top = 8.dp).then(pressScale({ smart = !smart }, 0.95f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        Modifier.size(width = 38.dp, height = 23.dp).clip(RoundedCornerShape(999.dp))
                            .background(if (smart) c.purple else c.card3),
                    ) {
                        Box(
                            Modifier.padding(2.5.dp).offset(x = if (smart) 15.dp else 0.dp)
                                .size(18.dp).clip(CircleShape).background(Color.White)
                        )
                    }
                    TfText(
                        tr(if (smart) "habits.state.on" else "habits.state.off"),
                        color = if (smart) c.purple else c.fg3, size = 12.5f, weight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    HabitButton(tr("habits.reminder.save"), tint, { onConfirm(enabled, time, smart) })
}

// ═════════════════════════════════════════════════════════════════════════════
// Day detail
// ═════════════════════════════════════════════════════════════════════════════

/** What a tapped calendar cell shows: every habit that was due that day, and its answer. */
@Composable
fun DaySheet(store: HabitStore, dateKey: String) {
    val c = LocalTf.current
    val due = store.habits.filter { it.state != HabitLifecycle.ARCHIVED && it.scheduledOn(dateKey) }
    val done = due.count { store.entry(it.id, dateKey)?.status == HabitStatus.DONE }

    SheetHeader(
        "calendar",
        c.accent,
        tr("habits.calendar.day_complete", done, due.size),
        localizedHabitDay(dateKey, store.today()),
    )
    if (due.isEmpty()) {
        TfText(
            tr("habits.calendar.day_empty"),
            color = c.fg3, size = 14f, align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
        )
        return
    }
    due.forEach { habit ->
        val tint = c.named(habit.colorKey)
        val status = store.entry(habit.id, dateKey)?.status
        Row(
            Modifier.fillMaxWidth().padding(bottom = 9.dp)
                .clip(RoundedCornerShape(13.dp)).background(c.card)
                .border(0.5.dp, c.sep, RoundedCornerShape(13.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(habit.icon, tint, size = 34, iconSize = 17, soft = tint.soft(0.16f))
            TfText(
                habit.name,
                color = if (status == HabitStatus.DONE) c.fg else c.fg3,
                size = 14.5f, modifier = Modifier.weight(1f), maxLines = 1,
            )
            when (status) {
                HabitStatus.DONE -> Box(
                    Modifier.size(24.dp).clip(CircleShape).background(tint),
                    contentAlignment = Alignment.Center,
                ) { TfIcon("check", size = 14, color = onAccent(tint)) }
                HabitStatus.SKIPPED -> TfText(tr("habits.status.skipped_short"), color = c.cyan, size = 10f, family = Fonts.mono)
                HabitStatus.PARTIAL -> TfText(
                    "${store.entry(habit.id, dateKey)?.value ?: 0}/${habit.goalTarget}",
                    color = tint, size = 11f, family = Fonts.mono,
                )
                null -> Box(Modifier.size(24.dp).clip(CircleShape).border(1.6.dp, c.fg4, CircleShape))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Achievement
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun AwardSheet(view: HabitAchievementView) {
    val c = LocalTf.current
    val tint = c.named(view.achievement.color)
    // Celebrate on open, once. The burst fires off a token that changes when this sheet is
    // composed for a newly unlocked badge, and never for a locked one.
    var celebrate by remember(view.id) { mutableStateOf(0) }
    LaunchedEffect(view.id) { if (view.unlocked) celebrate = 1 }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            if (view.unlocked) {
                HeroGlow(tint, Modifier.matchParentSize(), opacity = 0.4f)
                Burst(celebrate, tint, count = 20)
            }
            Box(
                Modifier.size(120.dp).clip(RoundedCornerShape(30.dp))
                    .background(
                        if (view.unlocked) Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.5f)))
                        else Brush.linearGradient(listOf(c.fg4, c.fg4))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TfIcon(
                    if (view.unlocked) view.achievement.icon else "lock",
                    size = 54, color = if (view.unlocked) Color.White else c.fg3,
                )
            }
        }
        TfText(
            tr(view.achievement.nameKey), color = c.fg, size = 24f, weight = FontWeight.Bold,
            family = Fonts.display, align = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        )
        TfText(
            tr(view.achievement.subKey), color = c.fg2, size = 14.5f, lineHeight = 1.55f,
            align = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp).padding(bottom = 18.dp),
        )
        if (view.unlocked) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(tint.soft(0.14f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TfIcon("check", size = 13, color = tint)
                TfText(tr("habits.achievement.unlocked"), color = tint, size = 12f, family = Fonts.mono)
            }
        } else {
            Column(Modifier.widthIn(max = 260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressBar(view.progress, tint, height = 7, modifier = Modifier.weight(1f))
                    TfText("${(view.progress * 100).roundToInt()}%", color = c.fg2, size = 12f, family = Fonts.mono)
                }
                TfText(
                    tr("habits.achievement.goal", tr(view.achievement.goalKey)),
                    color = c.fg3, size = 12.5f, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Delete confirmation
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Deleting is confirmed rather than undone-only because it removes visible history, and a
 * snackbar the user scrolls past is not consent. The undo still exists afterwards — this
 * dialog says so, which is what makes the confirm feel safe rather than final.
 */
@Composable
fun DeleteHabitDialog(habit: Habit?, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = LocalTf.current
    val open = habit != null
    val progress by animateFloatAsState(if (open) 1f else 0f, tween(240), label = "delete")
    if (progress < 0.01f || habit == null) return

    Box(Modifier.fillMaxSize().zIndex(85f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                .background(Color(6 / 255f, 8 / 255f, 13 / 255f, 0.6f * progress))
                .then(pressScale(onCancel, 1f))
        )
        Column(
            Modifier.width(300.dp)
                .scale(0.9f + 0.1f * progress).alpha(progress)
                .clip(RoundedCornerShape(20.dp))
                .background(c.card2)
                .border(0.5.dp, c.line, RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(c.danger.soft(0.16f)),
                contentAlignment = Alignment.Center,
            ) { TfIcon("trash", size = 26, color = c.danger) }
            TfText(
                tr("habits.delete.title", habit.name), color = c.fg, size = 20f, weight = FontWeight.Bold,
                family = Fonts.display, align = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            TfText(
                tr("habits.delete.detail"),
                color = c.fg2, size = 13.5f, lineHeight = 1.55f, align = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp)).background(c.card)
                        .border(0.5.dp, c.line, RoundedCornerShape(12.dp))
                        .then(pressScale(onCancel, 0.97f))
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { TfText(tr("common.cancel"), color = c.fg, size = 15f, weight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp)).background(c.danger)
                        .then(pressScale(onConfirm, 0.97f))
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { TfText(tr("habits.action.delete"), color = Color.White, size = 15f, weight = FontWeight.Bold) }
            }
        }
    }
}
