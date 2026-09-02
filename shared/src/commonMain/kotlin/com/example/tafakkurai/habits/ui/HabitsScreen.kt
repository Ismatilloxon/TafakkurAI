package com.example.tafakkurai.habits.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.tafakkurai.habits.*
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.PlatformNestedBackHandler
import com.example.tafakkurai.ui.components.TfSearchField
import org.koin.mp.KoinPlatform.getKoin
import kotlin.math.roundToInt

/**
 * The Habits module.
 *
 * One entry point with its own navigation stack, the same shape the prototype's
 * `HabitsScreen` router has, so `App.kt` only needs a single "habits" route. Detail,
 * statistics, calendar, achievements and create/edit push onto this stack; leaving the
 * last of them exits the module.
 *
 * Every screen reads [HabitStore] directly. That is deliberate rather than lazy: the store
 * is already the Compose-observable single instance the Today block reads, so completing a
 * habit here updates the dashboard card without a bus, a callback chain or a refresh.
 */
@Composable
fun HabitsScreen(onBack: () -> Unit) {
    val store = remember { getKoin().get<HabitStore>() }
    val stack = remember { mutableStateListOf("home") }
    var activeId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var sheet by remember { mutableStateOf<HabitSheetState?>(null) }
    var pendingDelete by remember { mutableStateOf<Habit?>(null) }
    var snack by remember { mutableStateOf<SnackData?>(null) }
    var snackToken by remember { mutableStateOf(0) }

    val route = stack.last()
    val active = activeId?.let { id -> store.habits.firstOrNull { it.id == id } }

    fun push(next: String) { stack.add(next) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onBack() }

    // The app shell owns the one iOS edge recognizer. Route it here only while a nested
    // Habits destination is visible; at home the shell's own pop remains the fallback.
    PlatformNestedBackHandler(enabled = stack.size > 1, onBack = ::pop)

    fun notify(text: String, icon: String, color: Color, undo: (() -> Unit)? = null) {
        snackToken++
        snack = SnackData(snackToken, text, icon, color, undo)
    }

    LaunchedEffect(route) { store.recordScreen("habits_${route}_viewed", activeId) }

    val c = LocalTf.current
    val strings = LocalStrings.current
    val haptics = LocalHapticFeedback.current

    /** One completion tap, with its undo already captured. */
    fun toggle(habit: Habit) {
        val before = store.snapshotEntry(habit.id)
        val day = store.today()
        val outcome = store.toggle(habit.id)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val tint = c.named(habit.colorKey)
        when (outcome) {
            HabitStore.ToggleOutcome.COMPLETED -> {
                store.evaluateAchievements()
                notify(strings.format("habits.snack.completed", habit.name), "check", tint) {
                    store.restoreEntry(habit.id, day, before)
                }
            }
            HabitStore.ToggleOutcome.PROGRESSED -> {
                val now = store.entry(habit.id, day)?.value ?: 0
                notify(strings.format("habits.snack.progress", habit.name, now, habit.goalTarget), "plus", tint) {
                    store.restoreEntry(habit.id, day, before)
                }
            }
            HabitStore.ToggleOutcome.UNDONE ->
                notify(strings.format("habits.snack.not_done", habit.name), "x", c.fg2) {
                    store.restoreEntry(habit.id, day, before)
                }
        }
    }

    fun skip(habit: Habit, reason: String) {
        val before = store.snapshotEntry(habit.id)
        val day = store.today()
        store.skip(habit.id, reason)
        notify(
            if (reason.isBlank()) {
                strings.format("habits.snack.skipped", habit.name)
            } else {
                strings.format("habits.snack.skipped_reason", habit.name, strings[reason])
            },
            "repeat", c.cyan,
        ) { store.restoreEntry(habit.id, day, before) }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            route == "home" && store.habits.isEmpty() -> HabitsEmpty(
                onCreate = { editing = null; push("create") },
                onTemplate = { t, localizedName ->
                    val created = store.createFromTemplate(t, localizedName)
                    store.evaluateAchievements()
                    notify(strings.format("habits.snack.created", created.name), "check", c.named(created.colorKey))
                },
                onExit = onBack,
            )

            route == "home" -> HabitsHome(
                store = store,
                onExit = onBack,
                onOpen = { activeId = it.id; push("detail") },
                onToggle = ::toggle,
                onLongPress = { sheet = HabitSheetState.Menu(it) },
                onSkip = { sheet = HabitSheetState.Skip(it) },
                onCreate = { editing = null; push("create") },
                onNav = { push(it) },
            )

            route == "detail" && active != null -> HabitDetailScreen(
                store = store,
                habit = active,
                onBack = ::pop,
                onToggle = { toggle(active) },
                onEdit = { editing = active; push("create") },
                onMenu = { sheet = HabitSheetState.Menu(active) },
                onReminder = { sheet = HabitSheetState.Reminder(active) },
                onPause = { sheet = HabitSheetState.Pause(active) },
                onDelete = { pendingDelete = active },
            )

            route == "stats" -> HabitStatisticsScreen(store, ::pop)
            route == "calendar" -> HabitCalendarScreen(store, ::pop) { sheet = HabitSheetState.Day(it) }
            route == "achievements" -> HabitAchievementsScreen(store, ::pop) { sheet = HabitSheetState.Award(it) }

            route == "create" -> HabitCreateScreen(
                editing = editing,
                onCancel = { editing = null; pop() },
                onSave = { draft ->
                    val saved = draft.commit(store, editing)
                    editing = null
                    pop()
                    store.evaluateAchievements()
                    notify(
                        strings.format(if (draft.isEdit) "habits.snack.updated" else "habits.snack.created", saved.name),
                        "check", c.named(saved.colorKey),
                    )
                },
            )

            // The active habit was deleted from underneath this route (undo of a create,
            // or a delete synced from another device). Fall back rather than showing a
            // blank screen.
            else -> {
                LaunchedEffect(route) { pop() }
            }
        }

        // ── overlays ─────────────────────────────────────────────────────────
        HabitContextMenu(
            open = sheet is HabitSheetState.Menu,
            onClose = { sheet = null },
            header = {
                (sheet as? HabitSheetState.Menu)?.habit?.let { h -> ContextMenuHeader(h, store.stats(h.id)) }
            },
            items = (sheet as? HabitSheetState.Menu)?.habit?.let { h ->
                menuActions(
                    habit = h,
                    doneToday = store.entry(h.id, store.today())?.status == HabitStatus.DONE,
                    onToggle = { toggle(h) },
                    onSkip = { sheet = HabitSheetState.Skip(h) },
                    onEdit = { editing = h; push("create") },
                    onReminder = { sheet = HabitSheetState.Reminder(h) },
                    onPauseOrResume = {
                        if (h.state == HabitLifecycle.PAUSED) {
                            store.resume(h.id)
                            notify(strings.format("habits.snack.resumed", h.name), "play", c.green)
                        } else {
                            sheet = HabitSheetState.Pause(h)
                        }
                    },
                    onDelete = { pendingDelete = h },
                )
            } ?: emptyList(),
        )

        HabitSheet(
            open = sheet != null && sheet !is HabitSheetState.Menu,
            onClose = { sheet = null },
        ) {
            when (val s = sheet) {
                is HabitSheetState.Skip -> SkipSheet(s.habit) { reason ->
                    skip(s.habit, reason); sheet = null
                }
                is HabitSheetState.Pause -> PauseSheet(s.habit) { days ->
                    store.pause(s.habit.id, days)
                    sheet = null
                    notify(strings.format("habits.snack.paused", s.habit.name), "pause", c.warn) { store.resume(s.habit.id) }
                }
                is HabitSheetState.Reminder -> ReminderSheet(s.habit) { enabled, time, smart ->
                    store.setReminder(s.habit.id, enabled, time, smart)
                    sheet = null
                    notify(
                        strings.format(
                            if (enabled) "habits.snack.reminder_set" else "habits.snack.reminder_off",
                            time,
                            s.habit.name,
                        ),
                        "bell", c.named(s.habit.colorKey),
                    )
                }
                is HabitSheetState.Day -> DaySheet(store, s.dateKey)
                is HabitSheetState.Award -> AwardSheet(s.view)
                else -> Unit
            }
        }

        DeleteHabitDialog(
            habit = pendingDelete,
            onCancel = { pendingDelete = null },
            onConfirm = {
                val h = pendingDelete ?: return@DeleteHabitDialog
                pendingDelete = null
                if (route == "detail") pop()
                store.delete(h.id)
                notify(strings.format("habits.snack.deleted", h.name), "trash", c.danger) { store.restore(h.id) }
            },
        )

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp).zIndex(65f)) {
            HabitSnackbar(snack, onDismiss = { snack = null })
        }
    }
}

/** Which overlay is open. A sealed type so the router cannot show two at once. */
sealed interface HabitSheetState {
    data class Menu(val habit: Habit) : HabitSheetState
    data class Skip(val habit: Habit) : HabitSheetState
    data class Pause(val habit: Habit) : HabitSheetState
    data class Reminder(val habit: Habit) : HabitSheetState
    data class Day(val dateKey: String) : HabitSheetState
    data class Award(val view: HabitAchievementView) : HabitSheetState
}

@Composable
private fun menuActions(
    habit: Habit,
    doneToday: Boolean,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    onReminder: () -> Unit,
    onPauseOrResume: () -> Unit,
    onDelete: () -> Unit,
): List<ContextAction> = listOf(
    ContextAction(tr(if (doneToday) "habits.action.mark_not_done" else "habits.action.complete"), "check", onPress = onToggle),
    ContextAction(tr("habits.action.skip_today"), "repeat", onPress = onSkip),
    ContextAction(tr("habits.action.edit"), "edit", onPress = onEdit),
    ContextAction(tr("habits.action.reminder"), "bell", onPress = onReminder),
    ContextAction(
        tr(if (habit.state == HabitLifecycle.PAUSED) "habits.action.resume" else "habits.action.pause"),
        if (habit.state == HabitLifecycle.PAUSED) "play" else "pause",
        onPress = onPauseOrResume,
    ),
    ContextAction(tr("habits.action.delete"), "trash", danger = true, onPress = onDelete),
)

@Composable
private fun ContextMenuHeader(habit: Habit, stats: HabitStats) {
    val c = LocalTf.current
    val tint = c.named(habit.colorKey)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card2)
            .border(0.5.dp, c.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        IconBadge(habit.icon, tint, size = 38, iconSize = 19, soft = tint.soft(0.18f))
        Column {
            TfText(habit.name, color = c.fg, size = 15f, weight = FontWeight.SemiBold)
            TfText(tr("habits.streak_days", stats.current), color = tint, size = 11f, family = Fonts.mono)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Home
// ═════════════════════════════════════════════════════════════════════════════

private val HOME_FILTERS = listOf(
    "all" to "habits.filter.all",
    HabitSlot.MORNING.id to HabitSlot.MORNING.labelKey,
    HabitSlot.ANYTIME.id to HabitSlot.ANYTIME.labelKey,
    HabitSlot.EVENING.id to HabitSlot.EVENING.labelKey,
    "done" to "habits.filter.done",
)

@Composable
private fun HabitsHome(
    store: HabitStore,
    onExit: () -> Unit,
    onOpen: (Habit) -> Unit,
    onToggle: (Habit) -> Unit,
    onLongPress: (Habit) -> Unit,
    onSkip: (Habit) -> Unit,
    onCreate: () -> Unit,
    onNav: (String) -> Unit,
) {
    val c = LocalTf.current
    val accent = c.accent
    var filter by remember { mutableStateOf("all") }
    var reordering by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf<String?>(null) }

    val today = store.today()
    // Derived once per store mutation rather than once per recomposition: folding a year of
    // rolls on every frame of a scroll is the difference between smooth and not.
    val revision = store.revision
    val stats = remember(revision) { store.statsAll() }
    val monthRolls = remember(revision) { store.rolls(HABIT_WINDOW_MONTH) }
    val streaks = remember(revision) { HabitMath.accountStreak(store.rolls(HABIT_WINDOW_YEAR)) }
    val consistency = remember(revision) {
        HabitMath.consistencyScore(HabitMath.rate(monthRolls), streaks.first)
    }

    val dueToday = store.active.filter { it.scheduledOn(today) }
    val doneToday = dueToday.count { stats[it.id]?.isDoneToday == true }
    val percent = if (dueToday.isEmpty()) 0f else doneToday.toFloat() / dueToday.size

    val query = search.orEmpty().trim().lowercase()
    val shown = store.active.filter { h ->
        if (query.isNotEmpty() && !h.name.lowercase().contains(query)) return@filter false
        when (filter) {
            "all" -> true
            "done" -> stats[h.id]?.isDoneToday == true
            else -> h.slot.id == filter
        }
    }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.then(pressScale(onExit, 0.9f)).padding(6.dp)) {
                TfIcon("chevronLeft", size = 22, color = accent)
            }
            Spacer(Modifier.weight(1f))
            HeaderToggle("search", search != null, accent) { search = if (search == null) "" else null }
            HeaderToggle(if (reordering) "check" else "sliders", reordering, accent) { reordering = !reordering }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(bottom = 130.dp),
        ) {
            // ── hero ─────────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth()) {
                HeroGlow(accent, Modifier.fillMaxWidth().height(260.dp).align(Alignment.TopCenter))
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Overline(tr("habits.home.overline", localizedHabitDay(today, today)), c.gold)
                    TfText(
                        tr("habits.title"), color = c.fg, size = 32f, weight = FontWeight.Bold,
                        family = Fonts.display, letterSpacing = 0.3f,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        HabitRing(percent, accent, size = 132, stroke = 11) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                CountUp(doneToday.toFloat(), c.fg, size = 34f)
                                TfText(
                                    "/${dueToday.size}", color = c.fg3, size = 19f,
                                    weight = FontWeight.SemiBold, family = Fonts.display,
                                )
                            }
                            TfText(
                                tr("habits.home.percent_today", (percent * 100).roundToInt()), color = accent, size = 10f,
                                family = Fonts.mono, letterSpacing = 0.8f,
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeroStat("flame", tr("habits.stats.current"), habitDays(streaks.first), c.gold)
                            HeroStat("star", tr("habits.stats.longest"), habitDays(streaks.second), c.gold)
                            HeroStat("target", tr("habits.stats.consistency"), "$consistency/100", c.green)
                        }
                    }
                    val remaining = dueToday.size - doneToday
                    TfText(
                        when {
                            dueToday.isEmpty() -> tr("habits.home.none_due")
                            remaining == 0 -> tr("habits.home.perfect")
                            streaks.first > 1 -> tr("habits.home.remaining_streak", remaining, streaks.first)
                            else -> tr("habits.home.remaining_restart", remaining)
                        },
                        color = c.fg2, size = 13.5f, lineHeight = 1.5f, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }

            // ── quick nav ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                QuickNav("lineChart", tr("habits.nav.stats"), c.cyan, Modifier.weight(1f)) { onNav("stats") }
                QuickNav("calendar", tr("habits.nav.calendar"), c.gold, Modifier.weight(1f)) { onNav("calendar") }
                QuickNav("star", tr("habits.nav.awards"), c.purple, Modifier.weight(1f)) { onNav("achievements") }
            }

            if (reordering) {
                TfText(
                    tr("habits.reorder.help"),
                    color = c.fg3, size = 13f, lineHeight = 1.5f, align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                )
                ReorderList(store.active) { store.reorder(it) }
                return@Column
            }

            if (search != null) {
                TfSearchField(
                    query = search!!,
                    onQuery = { search = it },
                    placeholder = tr("habits.search.hint"),
                    searchContentDescription = tr("habits.search.a11y"),
                    clearContentDescription = tr("habits.search.clear_a11y"),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HOME_FILTERS.forEach { (key, labelKey) ->
                    FilterPill(tr(labelKey), filter == key, accent) { filter = key }
                }
            }

            HomeNudge(stats, dueToday, doneToday, streaks.first) { habit ->
                onLongPress(habit)
            }

            when {
                shown.isEmpty() -> TfText(
                    tr("habits.filter.empty"),
                    color = c.fg3, size = 14f, align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 48.dp),
                )

                filter == "all" && query.isEmpty() -> {
                    // Grouped by time of day, and only groups that have something in them:
                    // an empty "Afternoon 0/0" heading is pure noise.
                    HabitSlot.entries.forEach { slot ->
                        val items = shown.filter { it.slot == slot }
                        if (items.isEmpty()) return@forEach
                        val slotDone = items.count { stats[it.id]?.isDoneToday == true }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TfIcon(slot.icon, size = 15, color = c.fg3)
                            TfText(
                                tr(slot.labelKey).uppercase(), color = c.fg3, size = 11f,
                                weight = FontWeight.SemiBold, family = Fonts.mono, letterSpacing = 0.8f,
                            )
                            TfText("$slotDone/${items.size}", color = c.fg4, size = 11f, family = Fonts.mono)
                            if (slotDone == items.size) TfIcon("checkCircle", size = 14, color = c.green)
                        }
                        Column(
                            Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items.forEach { h ->
                                HabitRow(h, stats[h.id] ?: HabitStats(h.id), onToggle, onOpen, onLongPress, onSkip)
                            }
                        }
                    }
                }

                else -> Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    shown.forEach { h ->
                        HabitRow(h, stats[h.id] ?: HabitStats(h.id), onToggle, onOpen, onLongPress, onSkip)
                    }
                }
            }

            if (store.paused.isNotEmpty()) {
                HabitSection(tr("habits.section.paused"), modifier = Modifier.padding(horizontal = 20.dp))
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    store.paused.forEach { h ->
                        PausedRow(h, stats[h.id] ?: HabitStats(h.id)) { store.resume(h.id) }
                    }
                }
            }
        }
    }

    if (!reordering) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Row(
                Modifier.padding(bottom = 30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(listOf(accent, c.purple)))
                    .then(pressScale(onCreate, 0.95f))
                    .padding(horizontal = 22.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TfIcon("plus", size = 20, color = Color.White)
                TfText(tr("habits.form.new_title"), color = Color.White, size = 15f, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeaderToggle(icon: String, on: Boolean, accent: Color, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.size(38.dp).clip(CircleShape)
            .background(if (on) accent.soft(0.16f) else Color.Transparent)
            .then(pressScale(onClick, 0.9f)),
        contentAlignment = Alignment.Center,
    ) { TfIcon(icon, size = 20, color = if (on) accent else c.fg2) }
}

@Composable
private fun HeroStat(icon: String, label: String, value: String, tint: Color) {
    val c = LocalTf.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tint.soft(0.14f)),
            contentAlignment = Alignment.Center,
        ) { TfIcon(icon, size = 15, color = tint) }
        Column {
            TfText(label.uppercase(), color = c.fg3, size = 9f, family = Fonts.mono, letterSpacing = 0.6f)
            TfText(value, color = c.fg, size = 13.5f, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickNav(icon: String, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalTf.current
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(c.card)
            .border(0.5.dp, c.sep, RoundedCornerShape(15.dp))
            .then(pressScale(onClick, 0.97f))
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TfIcon(icon, size = 20, color = tint)
        TfText(label, color = c.fg2, size = 12f, weight = FontWeight.SemiBold)
    }
}

/**
 * The one proactive line on the home screen.
 *
 * Derived from the user's own numbers rather than generated, exactly like the backend's
 * `buildNudge` — so it costs nothing, works with no connection, and cannot invent a habit
 * that does not exist. Ordered by how actionable each case is.
 */
@Composable
private fun HomeNudge(
    stats: Map<String, HabitStats>,
    dueToday: List<Habit>,
    doneToday: Int,
    streak: Int,
    onAct: (Habit) -> Unit,
) {
    if (dueToday.isEmpty() || doneToday == dueToday.size) return
    val open = dueToday.filter {
        val s = stats[it.id]
        s?.isDoneToday != true && s?.isSkippedToday != true
    }
    val atRisk = open.maxByOrNull { stats[it.id]?.current ?: 0 } ?: return
    val risk = stats[atRisk.id]?.current ?: 0
    val eveningOpen = open.filter { it.slot == HabitSlot.EVENING }

    val text = when {
        risk >= 3 ->
            tr("habits.nudge.streak_risk", atRisk.name, risk)
        eveningOpen.size == 1 && !eveningOpen.first().reminderEnabled -> {
            val h = eveningOpen.first()
            tr("habits.nudge.evening", h.name, h.timeLocal.ifBlank { "18:00" })
        }
        else -> {
            val remaining = dueToday.size - doneToday
            tr(if (streak > 1) "habits.nudge.remaining_streak" else "habits.nudge.remaining_restart", remaining, streak)
        }
    }
    val target = if (risk >= 3) atRisk else eveningOpen.firstOrNull() ?: atRisk
    ProactiveCard(
        text,
        action = tr(if (risk >= 3) "habits.action.open" else "habits.action.set_reminder"),
        onAction = { onAct(target) },
        compact = true,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Habit row
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun HabitRow(
    habit: Habit,
    stats: HabitStats,
    onToggle: (Habit) -> Unit,
    onOpen: (Habit) -> Unit,
    onLongPress: (Habit) -> Unit,
    onSkip: (Habit) -> Unit,
) {
    val c = LocalTf.current
    val tint = c.named(habit.colorKey)
    val done = stats.isDoneToday
    val skipped = stats.isSkippedToday
    val partial = if (habit.goalType != HabitGoalType.CHECK && !done && stats.todayValue > 0) stats.todayValue else null

    // Fires the burst on the transition into "done", never on the first composition —
    // otherwise every scroll back to a completed habit re-confettis it.
    var burst by remember(habit.id) { mutableStateOf(0) }
    var wasDone by remember(habit.id) { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !wasDone) burst++
        wasDone = done
    }

    SwipeRow(
        actions = listOf(
            SwipeAction(tr("habits.action.skip"), "repeat", c.cyan) { onSkip(habit) },
            SwipeAction(tr("habits.action.more"), "more", c.card3) { onLongPress(habit) },
        ),
        onLongPress = { onLongPress(habit) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(c.card2, c.card)))
                .border(0.5.dp, if (done) tint.soft(0.45f) else c.hair, RoundedCornerShape(18.dp))
                .then(pressScale({ onOpen(habit) }, 0.99f))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CompletionButton(done, tint, { onToggle(habit) }, partial = partial)
                Burst(burst, tint)
            }
            IconBadge(habit.icon, tint, size = 40, iconSize = 20, soft = tint.soft(0.16f))
            Column(Modifier.weight(1f)) {
                TfText(
                    habit.name,
                    color = if (done) c.fg2 else c.fg,
                    size = 15f, weight = FontWeight.SemiBold, maxLines = 1,
                )
                Row(
                    Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TfText(
                        habit.timeLocal.ifBlank { tr("habits.time.all_day") },
                        color = c.fg3, size = 11f, family = Fonts.mono,
                    )
                    Dot(c.fg4)
                    StreakBadge(stats.current, tint)
                    Dot(c.fg4)
                    DifficultyDots(habit.difficulty, tint)
                }
            }
            when {
                skipped -> Pill(tr("habits.status.skipped"), c.cyan, c.cyan.soft(0.14f))
                partial != null -> TfText(
                    "$partial/${habit.goalTarget}", color = tint, size = 12f,
                    weight = FontWeight.Bold, family = Fonts.mono,
                )
                else -> TfIcon("chevronRight", size = 18, color = c.fg4)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(3.dp).clip(CircleShape).background(color))
}

@Composable
private fun PausedRow(habit: Habit, stats: HabitStats, onResume: () -> Unit) {
    val c = LocalTf.current
    val tint = c.named(habit.colorKey)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .border(0.5.dp, c.sep, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(habit.icon, c.fg3, size = 34, iconSize = 17, soft = c.fg4)
        Column(Modifier.weight(1f)) {
            TfText(habit.name, color = c.fg2, size = 14.5f, weight = FontWeight.Medium)
            TfText(
                tr("habits.paused.streak_held", stats.current),
                color = c.fg3, size = 11f, family = Fonts.mono,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(Modifier.then(pressScale(onResume, 0.92f))) {
            Pill(tr("habits.action.resume"), tint, tint.soft(0.16f))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Reorder
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Drag-to-reorder without a third-party list library.
 *
 * The rows are a fixed height, so the target index is `dragY / stride` — no measurement
 * pass, no layout coordinates, and the same arithmetic the prototype uses. Reordering is
 * committed on release rather than on every crossing, so one drag is one sync event
 * instead of a dozen.
 */
@Composable
private fun ReorderList(habits: List<Habit>, onCommit: (List<String>) -> Unit) {
    val c = LocalTf.current
    val order = remember(habits.map { it.id }) { mutableStateListOf<String>().apply { addAll(habits.map { it.id }) } }
    val byId = habits.associateBy { it.id }
    var dragging by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        order.forEach { id ->
            val h = byId[id] ?: return@forEach
            val tint = c.named(h.colorKey)
            val lifted = dragging == id
            Row(
                Modifier.fillMaxWidth().height(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (lifted) c.card3 else c.card)
                    .border(0.5.dp, if (lifted) tint.soft(0.5f) else c.sep, RoundedCornerShape(14.dp))
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(h.icon, tint, size = 34, iconSize = 17, soft = tint.soft(0.16f))
                TfText(h.name, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TfText(h.timeLocal.ifBlank { tr("habits.time.all_day") }, color = c.fg3, size = 11f, family = Fonts.mono)
                DragHandle(
                    onStart = { dragging = id },
                    onMove = { deltaRows ->
                        val from = order.indexOf(id)
                        val to = (from + deltaRows).coerceIn(0, order.lastIndex)
                        if (to != from) {
                            order.removeAt(from)
                            order.add(to, id)
                        }
                    },
                    onEnd = { dragging = null; onCommit(order.toList()) },
                )
            }
        }
    }
}

@Composable
private fun DragHandle(onStart: () -> Unit, onMove: (Int) -> Unit, onEnd: () -> Unit) {
    val c = LocalTf.current
    val haptics = LocalHapticFeedback.current
    var accumulated by remember { mutableStateOf(0f) }
    val strideDp = 66f
    Box(
        Modifier
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .verticalDrag(
                onStart = {
                    accumulated = 0f
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStart()
                },
                onDelta = { dyDp ->
                    accumulated += dyDp
                    val rows = (accumulated / strideDp).toInt()
                    if (rows != 0) {
                        accumulated -= rows * strideDp
                        onMove(rows)
                    }
                },
                onEnd = onEnd,
            ),
    ) { TfIcon("list", size = 20, color = c.fg3) }
}
