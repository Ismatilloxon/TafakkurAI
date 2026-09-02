package com.example.tafakkurai.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.habits.*
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.spiritual.daysFromCivil
import com.example.tafakkurai.spiritual.parseDateKey
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfTextField
import kotlin.math.roundToInt

// ═════════════════════════════════════════════════════════════════════════════
// Detail
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun HabitDetailScreen(
    store: HabitStore,
    habit: Habit,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMenu: () -> Unit,
    onReminder: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalTf.current
    val tint = c.named(habit.colorKey)
    var tab by remember { mutableStateOf("overview") }

    val revision = store.revision
    val today = store.today()
    val entries = remember(revision, habit.id) { store.entriesFor(habit.id) }
    val stats = remember(revision, habit.id) { HabitMath.habitStats(habit, entries, today) }
    val index = remember(revision, habit.id) { mapOf(habit.id to entries) }

    Column(Modifier.fillMaxSize()) {
        BackBar("", onBack) {
            Box(Modifier.then(pressScale(onMenu, 0.9f)).padding(6.dp)) {
                TfIcon("more", size = 22, color = c.accent)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 60.dp),
        ) {
            // hero
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                HeroGlow(tint, Modifier.fillMaxWidth().height(220.dp), opacity = 0.28f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 6.dp)) {
                    HabitRing(stats.rate, tint, gradientTo = tint.copy(alpha = 0.5f), size = 150, stroke = 11) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tint.soft(0.18f)),
                            contentAlignment = Alignment.Center,
                        ) { TfIcon(habit.icon, size = 26, color = tint) }
                        TfText(
                            tr("habits.detail.rate", (stats.rate * 100).roundToInt()), color = c.fg3, size = 10f,
                            family = Fonts.mono, letterSpacing = 0.8f,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    TfText(
                        habit.name, color = c.fg, size = 25f, weight = FontWeight.Bold,
                        family = Fonts.display, align = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Pill(tr(habit.category.labelKey), tint, tint.soft(0.14f)) {
                            TfIcon(habit.category.icon, size = 11, color = tint)
                        }
                        StreakBadge(stats.current, tint, size = 12f, live = stats.current > 0)
                    }
                }
            }

            HabitButton(
                tr(if (stats.isDoneToday) "habits.detail.completed_today" else "habits.action.mark_complete"),
                if (stats.isDoneToday) tint.soft(0.16f) else tint,
                onToggle,
                modifier = Modifier.padding(top = 18.dp),
                icon = if (stats.isDoneToday) "checkCircle" else "check",
            )

            Box(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                Segmented(
                    listOf(
                        "overview" to tr("habits.detail.tab.overview"),
                        "calendar" to tr("habits.nav.calendar"),
                        "notes" to tr("habits.detail.tab.notes"),
                    ),
                    tab, { tab = it }, Modifier.fillMaxWidth(),
                )
            }

            when (tab) {
                "overview" -> DetailOverview(habit, stats, index, today, tint, onEdit, onReminder, onPause, onDelete)
                "calendar" -> DetailCalendar(habit, index, today, tint)
                else -> DetailNotes(store, habit, tint)
            }
        }
    }
}

@Composable
private fun DetailOverview(
    habit: Habit,
    stats: HabitStats,
    index: Map<String, Map<String, HabitEntry>>,
    today: String,
    tint: Color,
    onEdit: () -> Unit,
    onReminder: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalTf.current
    val todayNum = dayNumberOfKey(today) ?: return

    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HabitStatTile("flame", tr("habits.stats.current_streak"), "${stats.current}", tint, Modifier.weight(1f), unit = tr("habits.unit.days"))
        HabitStatTile("star", tr("habits.stats.longest"), "${stats.longest}", c.gold, Modifier.weight(1f), unit = tr("habits.unit.days"))
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HabitStatTile("target", tr("habits.stats.completion"), "${(stats.rate * 100).roundToInt()}", c.green, Modifier.weight(1f), unit = "%")
        HabitStatTile(
            "calendar", tr("habits.stats.since"),
            if (habit.createdAt > 0) localizedHabitDay(dateKeyOf(dayNumberOfMillis(habit.createdAt)), today) else "—",
            c.cyan, Modifier.weight(1f),
        )
    }

    // Contribution grid, whole weeks so the columns line up and the last one is this week.
    HabitSection(tr("habits.detail.consistency_weeks", HABIT_DETAIL_WEEKS))
    val gridEnd = todayNum + (6 - weekdayIndexOf(todayNum))
    val gridStart = gridEnd - HABIT_DETAIL_WEEKS * 7 + 1
    val gridRolls = remember(habit.id, today, index) {
        HabitMath.rollDays(listOf(habit), index, dateKeyOf(gridStart), dateKeyOf(gridEnd))
    }
    val levels = gridRolls.mapIndexed { i, roll ->
        // A day in the future is drawn as an outline, never as a miss — otherwise the rest
        // of the current week reads as failures the user has not had a chance at yet.
        if (gridStart + i > todayNum) -1 else roll.level
    }
    HabitCardSurface(Modifier.fillMaxWidth()) {
        Heatmap(levels, tint, cell = 12, gap = 3)
        HeatLegend(tint, Modifier.fillMaxWidth().padding(top = 12.dp))
    }

    HabitSection(tr("habits.detail.this_week"))
    val weekStart = todayNum - weekdayIndexOf(todayNum)
    val week = (0 until 7).map { i ->
        val key = dateKeyOf(weekStart + i)
        if (index[habit.id]?.get(key)?.status == HabitStatus.DONE) 1f else 0f
    }
    HabitCardSurface(Modifier.fillMaxWidth()) {
        HabitBars(week, tint, labels = WEEKDAY_INITIAL_KEYS.map { tr(it) }, height = 70, highlightLast = false)
    }

    HabitSection(tr("habits.detail.six_month_trend"))
    val trendRolls = remember(habit.id, today, index) {
        HabitMath.rollDays(listOf(habit), index, dateKeyOf(todayNum - 181), today)
    }
    val (months, monthLabels) = HabitMath.monthlyRates(trendRolls, today)
    HabitCardSurface(Modifier.fillMaxWidth()) { TrendLine(months, tint, labels = monthLabels.map { tr(it) }) }

    HabitSection(tr("habits.detail.milestones"))
    HabitCardSurface(Modifier.fillMaxWidth(), pad = 4) {
        listOf(7, 30, 100, 365).forEachIndexed { i, days ->
            val reached = stats.longest >= days
            Row(
                Modifier.fillMaxWidth()
                    .then(if (i > 0) Modifier.drawTopHairline(c.sep) else Modifier)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(if (reached) tint.soft(0.18f) else c.fg4),
                    contentAlignment = Alignment.Center,
                ) { TfIcon(if (reached) "flame" else "lock", size = 15, color = if (reached) tint else c.fg3) }
                TfText(
                    tr("habits.streak_days", days), color = if (reached) c.fg else c.fg3, size = 14f,
                    modifier = Modifier.weight(1f),
                )
                TfText(
                    if (reached) tr("habits.milestone.reached") else "${stats.longest}/$days",
                    color = if (reached) tint else c.fg4, size = 11f, family = Fonts.mono,
                )
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailAction("edit", tr("habits.action.edit"), Modifier.weight(1f), onPress = onEdit)
        DetailAction("bell", tr("habits.action.reminder"), Modifier.weight(1f), onPress = onReminder)
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailAction(
            if (habit.state == HabitLifecycle.PAUSED) "play" else "pause",
            tr(if (habit.state == HabitLifecycle.PAUSED) "habits.action.resume" else "habits.action.pause"),
            Modifier.weight(1f), onPress = onPause,
        )
        DetailAction("trash", tr("habits.action.delete"), Modifier.weight(1f), danger = true, onPress = onDelete)
    }
}

@Composable
private fun DetailAction(
    icon: String,
    label: String,
    modifier: Modifier,
    danger: Boolean = false,
    onPress: () -> Unit,
) {
    val c = LocalTf.current
    Row(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (danger) c.danger.soft(0.1f) else c.card)
            .border(0.5.dp, if (danger) c.danger.soft(0.4f) else c.sep, RoundedCornerShape(13.dp))
            .then(pressScale(onPress, 0.97f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        TfIcon(icon, size = 17, color = if (danger) c.danger else c.fg2)
        TfText(label, color = if (danger) c.danger else c.fg, size = 14f, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun DetailCalendar(
    habit: Habit,
    index: Map<String, Map<String, HabitEntry>>,
    today: String,
    tint: Color,
) {
    val (year, month, _) = parseDateKey(today) ?: return
    HabitSection(localizedHabitMonth(year, month))
    HabitCardSurface(Modifier.fillMaxWidth()) {
        MonthGrid(year, month, today, tint) { key ->
            val status = index[habit.id]?.get(key)?.status
            when {
                status == HabitStatus.DONE -> 1f
                status == HabitStatus.SKIPPED -> 0.25f
                !habit.scheduledOn(key) -> -1f  // not due: neither a hit nor a miss
                else -> 0f
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    ) {
        LegendSwatch(tr("habits.status.done"), tint)
        LegendSwatch(tr("habits.status.missed"), heatTrack())
        LegendSwatch(tr("habits.status.upcoming"), null)
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color?) {
    val c = LocalTf.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier.size(12.dp).clip(RoundedCornerShape(3.dp))
                .then(
                    if (color != null) Modifier.background(color)
                    else Modifier.border(1.dp, c.line, RoundedCornerShape(3.dp))
                )
        )
        TfText(label, color = c.fg3, size = 12f)
    }
}

@Composable
private fun DetailNotes(store: HabitStore, habit: Habit, tint: Color) {
    val c = LocalTf.current
    var draft by remember { mutableStateOf("") }
    val notes = store.notesFor(habit.id)

    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TfTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = tr("habits.notes.placeholder"),
            modifier = Modifier.weight(1f),
            singleLine = false,
        )
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(tint)
                .then(pressScale({ if (draft.isNotBlank()) { store.addNote(habit.id, draft); draft = "" } }, 0.92f)),
            contentAlignment = Alignment.Center,
        ) { TfIcon("plus", size = 19, color = onAccent(tint)) }
    }

    if (notes.isEmpty()) {
        TfText(
            tr("habits.notes.empty"),
            color = c.fg3, size = 14f, lineHeight = 1.6f, align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        notes.forEach { note ->
            HabitCardSurface(Modifier.fillMaxWidth(), pad = 14) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
                    TfText(
                        localizedHabitDay(note.dateKey, store.today()),
                        color = c.fg3, size = 11f, family = Fonts.mono, modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.then(pressScale({ store.deleteNote(note.id) }, 0.85f)).padding(2.dp)) {
                        TfIcon("x", size = 14, color = c.fg4)
                    }
                }
                TfText(
                    note.text, color = c.fg, size = 16f, family = Fonts.serif, lineHeight = 1.55f,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Statistics
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun HabitStatisticsScreen(store: HabitStore, onBack: () -> Unit) {
    val c = LocalTf.current
    val accent = c.accent
    val revision = store.revision
    val today = store.today()

    val yearRolls = remember(revision) { store.rolls(HABIT_WINDOW_YEAR) }
    val monthRolls = remember(revision) { store.rolls(HABIT_WINDOW_MONTH) }
    val stats = remember(revision) { store.statsAll() }
    val completion = HabitMath.rate(monthRolls)
    val (current, longest) = remember(revision) { HabitMath.accountStreak(yearRolls) }
    val consistency = HabitMath.consistencyScore(completion, current)
    val (weekday, best, worst) = remember(revision) { HabitMath.weekdayRates(yearRolls) }
    val (months, monthLabels) = remember(revision) { HabitMath.monthlyRates(yearRolls, today) }

    var skipped = 0
    var missed = 0
    var doneTotal = 0
    var activeDays = 0
    monthRolls.forEach {
        skipped += it.skipped
        missed += it.scheduled - it.done
        doneTotal += it.done
        if (it.scheduled > 0 || it.skipped > 0) activeDays++
    }
    val avgPerDay = if (activeDays == 0) 0f else (doneTotal.toFloat() / activeDays * 10).roundToInt() / 10f

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("habits.stats.title"), onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 60.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                HabitRing(completion, accent, size = 116, stroke = 10) {
                    CountUp(completion * 100f, c.fg, size = 30f)
                    TfText(tr("habits.stats.complete"), color = c.fg3, size = 9f, family = Fonts.mono, letterSpacing = 0.8f)
                }
                Column(Modifier.weight(1f)) {
                    TfText(
                        tr(statsHeadlineKey(completion, current)), color = c.fg, size = 19f,
                        weight = FontWeight.Bold, family = Fonts.display,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    StatLine(tr("habits.stats.consistency_score"), "$consistency/100", c.green)
                    StatLine(tr("habits.stats.average_daily"), tr("habits.stats.habits_value", avgPerDay), c.cyan)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HabitStatTile("flame", tr("habits.stats.current_streak"), "$current", c.gold, Modifier.weight(1f), unit = tr("habits.unit.days"))
                HabitStatTile("star", tr("habits.stats.longest_streak"), "$longest", c.gold, Modifier.weight(1f), unit = tr("habits.unit.days"))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HabitStatTile("repeat", tr("habits.status.skipped"), "$skipped", c.cyan, Modifier.weight(1f))
                HabitStatTile("x", tr("habits.status.missed"), "$missed", c.danger, Modifier.weight(1f))
            }

            HabitSection(tr("habits.stats.weekday_section"))
            HabitCardSurface(Modifier.fillMaxWidth()) {
                HabitBars(weekday, accent, labels = WEEKDAY_INITIAL_KEYS.map { tr(it) }, height = 74)
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).drawTopHairline(c.sep).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TfText(tr("habits.stats.best_weekday", tr(WEEKDAY_NAME_KEYS[best])), color = c.fg2, size = 12.5f)
                    TfText(tr("habits.stats.toughest_weekday", tr(WEEKDAY_NAME_KEYS[worst])), color = c.fg2, size = 12.5f)
                }
            }

            HabitSection(tr("habits.stats.monthly_trend"))
            HabitCardSurface(Modifier.fillMaxWidth()) { TrendLine(months, c.green, labels = monthLabels.map { tr(it) }) }

            HabitSection(tr("habits.stats.year"))
            HabitCardSurface(Modifier.fillMaxWidth()) {
                Heatmap(yearRolls.map { it.level }, accent, cell = 8, gap = 2)
                HeatLegend(accent, Modifier.fillMaxWidth().padding(top = 12.dp))
            }

            val byCategory = remember(revision) { HabitMath.categoryRates(store.habits, stats) }
            if (byCategory.isNotEmpty()) {
                HabitSection(tr("habits.stats.by_category"))
                HabitCardSurface(Modifier.fillMaxWidth()) {
                    byCategory.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            TfIcon(row.icon, size = 16, color = c.named(row.color))
                            TfText(tr(row.label), color = c.fg2, size = 12.5f, modifier = Modifier.width(82.dp), maxLines = 1)
                            ProgressBar(row.value, c.named(row.color), height = 7, modifier = Modifier.weight(1f))
                            TfText(
                                "${(row.value * 100).roundToInt()}%", color = c.fg2, size = 12f,
                                family = Fonts.mono, align = TextAlign.End, modifier = Modifier.width(38.dp),
                            )
                        }
                    }
                }
            }

            HabitSection(tr("habits.stats.balance"))
            HabitCardSurface(Modifier.fillMaxWidth(), pad = 12) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HabitRadar(remember(revision) { HabitMath.radarValues(store.habits, stats) }, c.purple, size = 200)
                }
            }

            HabitSection(tr("habits.stats.time_of_day"))
            HabitCardSurface(Modifier.fillMaxWidth()) {
                remember(revision) { HabitMath.timeOfDayRates(store.habits, stats) }.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        TfIcon(row.icon, size = 15, color = c.gold)
                        TfText(tr(row.label), color = c.fg2, size = 12.5f, modifier = Modifier.weight(1f))
                        TfText(
                            "${(row.value * 100).roundToInt()}%", color = c.fg, size = 12f,
                            weight = FontWeight.Bold, family = Fonts.mono,
                        )
                    }
                }
            }
        }
    }
}

private fun statsHeadlineKey(rate: Float, streak: Int): String = when {
    rate >= 0.9f -> "habits.stats.headline.outstanding"
    rate >= 0.75f -> "habits.stats.headline.strong"
    rate >= 0.5f -> "habits.stats.headline.steady"
    streak > 0 -> "habits.stats.headline.back"
    else -> "habits.stats.headline.fresh"
}

@Composable
private fun StatLine(label: String, value: String, tint: Color) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TfText(label, color = c.fg2, size = 12.5f)
        TfText(value, color = tint, size = 13f, weight = FontWeight.Bold, family = Fonts.mono)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Calendar
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun HabitCalendarScreen(store: HabitStore, onBack: () -> Unit, onDay: (String) -> Unit) {
    val c = LocalTf.current
    val accent = c.accent
    var view by remember { mutableStateOf("month") }
    val today = store.today()
    val (todayYear, todayMonth, _) = parseDateKey(today) ?: return
    // Month offset from the current month; negative is the past. Paging is capped at 0 so
    // the user cannot walk into empty future months.
    var offset by remember { mutableStateOf(0) }
    val shownYear = ((todayYear * 12 + todayMonth - 1) + offset) / 12
    val shownMonth = ((todayYear * 12 + todayMonth - 1) + offset) % 12 + 1

    val revision = store.revision
    val yearRolls = remember(revision) { store.rolls(HABIT_WINDOW_YEAR) }
    val index = remember(revision) { store.entryIndex() }
    val monthRolls = remember(revision, shownYear, shownMonth) {
        val first = "$shownYear-${pad(shownMonth)}-01"
        val last = dateKeyOf(daysFromCivil(shownYear, shownMonth, 1) + daysInMonth(shownYear, shownMonth) - 1)
        val end = if (last > today) today else last
        HabitMath.rollDays(store.habits, index, first, end)
    }
    val (streak, _) = remember(revision) { HabitMath.accountStreak(yearRolls) }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("habits.nav.calendar"), onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 60.dp),
        ) {
            Segmented(
                listOf("month" to tr("habits.calendar.month"), "year" to tr("habits.calendar.year")),
                view,
                { view = it },
                Modifier.fillMaxWidth(),
            )

            if (view == "month") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.then(pressScale({ offset -= 1 }, 0.85f)).padding(4.dp)) {
                        TfIcon("chevronLeft", size = 20, color = c.fg3)
                    }
                    TfText(
                        localizedHabitMonth(shownYear, shownMonth), color = c.fg, size = 19f,
                        weight = FontWeight.Bold, family = Fonts.display,
                    )
                    Box(
                        Modifier.then(if (offset < 0) pressScale({ offset += 1 }, 0.85f) else Modifier).padding(4.dp)
                    ) { TfIcon("chevronRight", size = 20, color = if (offset < 0) c.fg3 else c.fg4) }
                }
                val byDate = monthRolls.associateBy { it.date }
                HabitCardSurface(Modifier.fillMaxWidth()) {
                    MonthGrid(shownYear, shownMonth, today, accent, onDay = onDay) { key ->
                        val roll = byDate[key] ?: return@MonthGrid -1f
                        if (roll.scheduled == 0 && roll.skipped == 0) -1f
                        else roll.done.toFloat() / maxOf(1, roll.scheduled)
                    }
                }
                HeatLegend(accent, Modifier.fillMaxWidth().padding(top = 14.dp))

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigStat("${(HabitMath.rate(monthRolls) * 100).roundToInt()}%", tr("habits.calendar.this_month"), c.green, Modifier.weight(1f))
                    BigStat("${monthRolls.count { it.perfect }}", tr("habits.calendar.perfect_days"), c.gold, Modifier.weight(1f))
                    BigStat("$streak", tr("habits.calendar.day_streak"), accent, Modifier.weight(1f))
                }
                TfText(
                    tr("habits.calendar.tap_help"),
                    color = c.fg3, size = 12.5f, align = TextAlign.Center, lineHeight = 1.5f,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TfText(tr("habits.calendar.last_year"), color = c.fg, size = 19f, weight = FontWeight.Bold, family = Fonts.display)
                    TfText(
                        tr("habits.calendar.days_active", yearRolls.count { it.done > 0 }),
                        color = accent, size = 12f, family = Fonts.mono,
                    )
                }
                HabitCardSurface(Modifier.fillMaxWidth(), pad = 14) {
                    Heatmap(yearRolls.map { it.level }, accent, cell = 8, gap = 2)
                    HeatLegend(accent, Modifier.fillMaxWidth().padding(top = 12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HabitStatTile(
                        "activity", tr("habits.calendar.active_days"), "${yearRolls.count { it.done > 0 }}",
                        c.green, Modifier.weight(1f), unit = "/ ${yearRolls.size}",
                    )
                    HabitStatTile(
                        "flame", tr("habits.stats.longest_streak"), "${HabitMath.accountStreak(yearRolls).second}",
                        c.gold, Modifier.weight(1f), unit = tr("habits.unit.days"),
                    )
                }
            }
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, tint: Color, modifier: Modifier) {
    val c = LocalTf.current
    Column(
        modifier.clip(RoundedCornerShape(13.dp)).background(c.card)
            .border(0.5.dp, c.sep, RoundedCornerShape(13.dp))
            .padding(horizontal = 8.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TfText(value, color = tint, size = 22f, weight = FontWeight.Bold, family = Fonts.display)
        TfText(
            label, color = c.fg3, size = 9.5f, family = Fonts.mono, letterSpacing = 0.5f,
            align = TextAlign.Center, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A month laid out Monday-first.
 *
 * [intensity] returns 0..1 for a completed fraction, or a negative number for "not
 * applicable" — a day before the habit existed, after today, or one nothing was due on.
 * Those are drawn as a dashed outline rather than an empty cell, so the grid never claims
 * the user missed a day they were never offered.
 */
@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    todayKey: String,
    tint: Color,
    onDay: ((String) -> Unit)? = null,
    intensity: (dateKey: String) -> Float,
) {
    val c = LocalTf.current
    val firstDay = daysFromCivil(year, month, 1)
    val lead = weekdayIndexOf(firstDay)
    val total = daysInMonth(year, month)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            WEEKDAY_INITIAL_KEYS.map { tr(it) }.forEach { d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TfText(d, color = c.fg4, size = 10f, family = Fonts.mono)
                }
            }
        }
        val cells = lead + total
        val rows = (cells + 6) / 7
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(7) { col ->
                    val cellIndex = r * 7 + col
                    val dayOfMonth = cellIndex - lead + 1
                    if (dayOfMonth < 1 || dayOfMonth > total) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                        return@repeat
                    }
                    val key = "$year-${pad(month)}-${pad(dayOfMonth)}"
                    val value = intensity(key)
                    val isToday = key == todayKey
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                when {
                                    value < 0f -> Modifier.border(1.dp, c.line, RoundedCornerShape(10.dp))
                                    else -> Modifier.background(tint.copy(alpha = 0.12f + value * 0.78f))
                                }
                            )
                            .then(if (isToday) Modifier.border(1.5.dp, tint, RoundedCornerShape(10.dp)) else Modifier)
                            .then(if (onDay != null && value >= 0f) pressScale({ onDay(key) }, 0.9f) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        TfText(
                            "$dayOfMonth",
                            color = when {
                                value < 0f -> c.fg4
                                value > 0.5f -> onAccent(tint)
                                else -> c.fg
                            },
                            size = 11.5f, weight = FontWeight.SemiBold, family = Fonts.mono,
                        )
                    }
                }
            }
        }
    }
}

private fun pad(n: Int): String = if (n < 10) "0$n" else "$n"

private fun daysInMonth(year: Int, month: Int): Int {
    val next = if (month == 12) daysFromCivil(year + 1, 1, 1) else daysFromCivil(year, month + 1, 1)
    return (next - daysFromCivil(year, month, 1)).toInt()
}

// ═════════════════════════════════════════════════════════════════════════════
// Achievements
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun HabitAchievementsScreen(
    store: HabitStore,
    onBack: () -> Unit,
    onOpen: (HabitAchievementView) -> Unit,
) {
    val c = LocalTf.current
    val revision = store.revision
    // Evaluated on entry so anything earned since the last visit is persisted (and synced)
    // even if the user never taps a badge.
    val views = remember(revision) { store.evaluateAchievements().first }
    val unlocked = views.count { it.unlocked }
    val bestStreak = remember(revision) { store.statsAll().values.maxOfOrNull { it.longest } ?: 0 }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("habits.achievements.title"), onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 60.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(c.gold.soft(0.2f), c.purple.soft(0.08f))))
                    .border(0.5.dp, c.gold.soft(0.4f), RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HabitRing(
                    unlocked.toFloat() / views.size.coerceAtLeast(1),
                    c.gold, gradientTo = c.rose, size = 92, stroke = 9, glow = false,
                ) {
                    TfText("$unlocked", color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display)
                    TfText(tr("habits.achievements.of", views.size), color = c.fg3, size = 9f, family = Fonts.mono)
                }
                Column(Modifier.weight(1f)) {
                    TfText(
                        tr(if (unlocked == 0) "habits.achievements.first_close" else "habits.achievements.keep_fire"),
                        color = c.fg, size = 20f, weight = FontWeight.Bold, family = Fonts.display,
                    )
                    TfText(
                        boardSubtitle(bestStreak, views),
                        color = c.fg2, size = 13f, lineHeight = 1.5f,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            val recent = views.filter { it.unlocked }.sortedByDescending { it.unlockedAt }.take(4)
            if (recent.isNotEmpty()) {
                HabitSection(tr("habits.achievements.recent"))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    recent.forEach { v ->
                        val tint = c.named(v.achievement.color)
                        Column(
                            Modifier.width(96.dp)
                                .clip(RoundedCornerShape(14.dp)).background(c.card)
                                .border(0.5.dp, tint.soft(0.4f), RoundedCornerShape(14.dp))
                                .then(pressScale({ onOpen(v) }, 0.95f))
                                .padding(horizontal = 10.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                                    .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.5f)))),
                                contentAlignment = Alignment.Center,
                            ) { TfIcon(v.achievement.icon, size = 22, color = Color.White) }
                            TfText(
                                tr(v.achievement.nameKey), color = c.fg, size = 11.5f, weight = FontWeight.SemiBold,
                                align = TextAlign.Center, lineHeight = 1.25f,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            HabitSection(tr("habits.achievements.all"))
            views.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    pair.forEach { v -> AchievementCard(v, { onOpen(v) }, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun boardSubtitle(bestStreak: Int, views: List<HabitAchievementView>): String {
    val near = views.count { !it.unlocked && it.progress >= 0.6f }
    if (bestStreak == 0) return tr("habits.achievements.subtitle.start")
    if (near == 0) return tr("habits.achievements.subtitle.keep", habitDays(bestStreak))
    return tr(
        if (near == 1) "habits.achievements.subtitle.one_near" else "habits.achievements.subtitle.many_near",
        habitDays(bestStreak),
        near,
    )
}
