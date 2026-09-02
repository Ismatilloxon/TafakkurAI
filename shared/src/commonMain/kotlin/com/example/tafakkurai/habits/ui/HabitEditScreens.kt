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
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfTextField

// ═════════════════════════════════════════════════════════════════════════════
// Empty state
// ═════════════════════════════════════════════════════════════════════════════

/**
 * What a new user sees.
 *
 * Templates rather than a bare "create" button: the hardest part of starting is not the
 * form, it is deciding what the habit should be. One tap on a chip creates a fully
 * configured habit — category, icon, goal, reminder — and the user is already in the
 * module instead of staring at an empty field.
 */
@Composable
fun HabitsEmpty(
    onCreate: () -> Unit,
    onTemplate: (HabitTemplate, String) -> Unit,
    onExit: () -> Unit,
) {
    val c = LocalTf.current
    val accent = c.accent
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.then(pressScale(onExit, 0.9f)).padding(6.dp)) {
                TfIcon("chevronLeft", size = 22, color = accent)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp).padding(top = 30.dp, bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BreathOrb(size = 104, color = accent, purple = c.purple)
            TfText(
                tr("habits.empty.title"), color = c.fg, size = 27f, weight = FontWeight.Bold,
                family = Fonts.display, align = TextAlign.Center,
                modifier = Modifier.padding(top = 34.dp, bottom = 10.dp),
            )
            TfText(
                tr("habits.empty.detail"),
                color = c.fg2, size = 15f, lineHeight = 1.6f, align = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp).padding(bottom = 26.dp),
            )
            Row(
                Modifier.clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(accent, c.purple)))
                    .then(pressScale(onCreate, 0.96f))
                    .padding(horizontal = 26.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TfIcon("plus", size = 19, color = Color.White)
                TfText(tr("habits.empty.create"), color = Color.White, size = 15.5f, weight = FontWeight.Bold)
            }
            Overline(tr("habits.empty.templates"), modifier = Modifier.padding(top = 36.dp, bottom = 14.dp))
            // Two rows of three rather than a wrap: the chips are similar widths and a
            // FlowRow is not in the foundation this project builds against.
            HABIT_TEMPLATES.chunked(2).forEach { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    pair.forEach { t ->
                        val cat = habitCategory(t.categoryId)
                        val templateName = tr(t.nameKey)
                        Row(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(c.card)
                                .border(1.dp, c.sep, RoundedCornerShape(999.dp))
                                .then(pressScale({ onTemplate(t, templateName) }, 0.96f))
                                .padding(horizontal = 13.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            TfIcon(cat.icon, size = 15, color = c.named(cat.color))
                            TfText(templateName, color = c.fg, size = 13f, maxLines = 1, modifier = Modifier.weight(1f))
                            TfIcon("plus", size = 14, color = c.fg3)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Create / Edit
// ═════════════════════════════════════════════════════════════════════════════

/**
 * The Create/Edit form's state, hoisted out of the composable so the router can hand the
 * finished thing back to the store in one call.
 *
 * [commit] is the only place that decides create-vs-update, which is why editing a habit
 * cannot accidentally clone it: the id comes from [editing] when there is one.
 */
@Stable
class HabitDraft(editing: Habit?) {
    val isEdit = editing != null

    var name by mutableStateOf(editing?.name ?: "")
    var categoryId by mutableStateOf(editing?.categoryId ?: "islam")
    var icon by mutableStateOf(editing?.icon ?: habitCategory("islam").icon)
    var colorKey by mutableStateOf(editing?.colorKey ?: habitCategory("islam").color)
    var slot by mutableStateOf(editing?.slot ?: HabitSlot.MORNING)
    var timeLocal by mutableStateOf(editing?.timeLocal?.ifBlank { "07:00" } ?: "07:00")
    var reminder by mutableStateOf(editing?.reminderEnabled ?: true)
    var smartTiming by mutableStateOf(editing?.smartTiming ?: false)
    var frequency by mutableStateOf(editing?.frequency ?: HabitFrequency.DAILY)
    var weekdays = mutableStateListOf<Int>().apply {
        addAll(editing?.weekdays?.takeIf { it.isNotEmpty() } ?: listOf(0, 1, 2, 3, 4, 5, 6))
    }
    var difficulty by mutableStateOf(editing?.difficulty ?: HabitDifficulty.MEDIUM)
    var goalType by mutableStateOf(editing?.goalType ?: HabitGoalType.CHECK)
    var goalTarget by mutableStateOf(editing?.goalTarget ?: 1)
    var motivation by mutableStateOf(editing?.motivation ?: "")

    val valid: Boolean get() = name.trim().isNotEmpty()

    /** Category changes carry the icon and colour with them, unless the user overrode them. */
    fun chooseCategory(id: String) {
        val previous = habitCategory(categoryId)
        categoryId = id
        val next = habitCategory(id)
        if (icon == previous.icon) icon = next.icon
        if (colorKey == previous.color) colorKey = next.color
    }

    /** Slot follows the clock time so the home groups stay honest without a second picker. */
    fun chooseTime(value: String) {
        timeLocal = value
        slot = when {
            value < "12:00" -> HabitSlot.MORNING
            value < "17:00" -> HabitSlot.AFTERNOON
            else -> HabitSlot.EVENING
        }
    }

    fun commit(store: HabitStore, editing: Habit?): Habit {
        val unit = when (goalType) {
            HabitGoalType.DURATION -> "min"
            HabitGoalType.COUNT -> "x"
            HabitGoalType.CHECK -> ""
        }
        val days = if (frequency == HabitFrequency.CUSTOM) weekdays.sorted() else emptyList()
        if (editing != null) {
            val updated = editing.copy(
                name = name.trim(), categoryId = categoryId, icon = icon, colorKey = colorKey,
                slot = slot, timeLocal = timeLocal,
                reminderEnabled = reminder, reminderTime = if (reminder) timeLocal else "",
                smartTiming = smartTiming, frequency = frequency, weekdays = days,
                difficulty = difficulty, goalType = goalType,
                goalTarget = if (goalType == HabitGoalType.CHECK) 1 else goalTarget,
                goalUnit = unit, motivation = motivation,
            )
            store.update(updated)
            return updated
        }
        return store.create(
            name = name.trim(), categoryId = categoryId, icon = icon, colorKey = colorKey,
            slot = slot, timeLocal = timeLocal,
            reminderEnabled = reminder, reminderTime = timeLocal, smartTiming = smartTiming,
            frequency = frequency, weekdays = days, difficulty = difficulty,
            goalType = goalType, goalTarget = goalTarget, goalUnit = unit, motivation = motivation,
        )
    }
}

@Composable
fun HabitCreateScreen(editing: Habit?, onCancel: () -> Unit, onSave: (HabitDraft) -> Unit) {
    val c = LocalTf.current
    val draft = remember(editing?.id) { HabitDraft(editing) }
    val tint = c.named(draft.colorKey)
    var showMore by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // toolbar
        Row(
            Modifier.fillMaxWidth().height(52.dp).background(c.glass)
                .drawBottomHairline(c.sep)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TfText(tr("common.cancel"), color = c.fg2, size = 16f, modifier = Modifier.then(pressScale(onCancel, 0.92f)))
            TfText(
                tr(if (draft.isEdit) "habits.form.edit_title" else "habits.form.new_title"),
                color = c.fg, size = 16f, weight = FontWeight.SemiBold,
            )
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (draft.valid) tint else c.card2)
                    .then(if (draft.valid) pressScale({ onSave(draft) }, 0.94f) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TfText(
                    tr("common.save"),
                    color = if (draft.valid) onAccent(tint) else c.fg3,
                    size = 14f, weight = FontWeight.Bold,
                )
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 60.dp),
        ) {
            // Live preview — the single most useful thing on this screen. Choosing an icon
            // and a colour blind, then discovering the result on the home list, is how
            // people end up with six identical-looking habits.
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(tint.soft(0.2f), c.card)))
                    .border(0.5.dp, tint.soft(0.4f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.5f)))),
                    contentAlignment = Alignment.Center,
                ) { TfIcon(draft.icon, size = 26, color = Color.White) }
                Column(Modifier.weight(1f)) {
                    TfText(
                        draft.name.ifBlank { tr("habits.form.new_title") }, color = c.fg, size = 18f,
                        weight = FontWeight.Bold, family = Fonts.display, maxLines = 1,
                    )
                    Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TfText(tr(habitCategory(draft.categoryId).labelKey), color = tint, size = 11f, family = Fonts.mono)
                        Box(Modifier.size(3.dp).clip(CircleShape).background(c.fg4))
                        TfText(draft.timeLocal, color = c.fg3, size = 11f, family = Fonts.mono)
                    }
                }
                CompletionButton(false, tint, {}, size = 28)
            }

            // name
            TfTextField(
                value = draft.name,
                onValueChange = { draft.name = it },
                placeholder = tr("habits.form.name_placeholder"),
                modifier = Modifier.padding(top = 20.dp),
            )

            FieldLabel(tr("habits.form.category"))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HABIT_CATEGORIES.forEach { cat ->
                    CategoryChip(cat, draft.categoryId == cat.id, small = true) { draft.chooseCategory(cat.id) }
                }
            }

            FieldLabel(tr("habits.form.icon"))
            HABIT_ICONS.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { name ->
                        val on = draft.icon == name
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) tint.soft(0.2f) else c.card)
                                .border(1.dp, if (on) tint else c.sep, RoundedCornerShape(12.dp))
                                .then(pressScale({ draft.icon = name }, 0.92f)),
                            contentAlignment = Alignment.Center,
                        ) { TfIcon(name, size = 20, color = if (on) tint else c.fg2) }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            FieldLabel(tr("habits.form.color"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HABIT_COLORS.forEach { key ->
                    val swatch = c.named(key)
                    val on = draft.colorKey == key
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(swatch)
                            .then(if (on) Modifier.border(2.dp, c.fg, CircleShape) else Modifier)
                            .then(pressScale({ draft.colorKey = key }, 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) TfIcon("check", size = 18, color = onAccent(swatch)) }
                }
            }

            FieldLabel(tr("habits.form.frequency"))
            Segmented(
                HabitFrequency.entries.map { it.id to tr(it.labelKey) },
                draft.frequency.id,
                { draft.frequency = HabitFrequency.from(it) },
                Modifier.fillMaxWidth(),
            )
            if (draft.frequency == HabitFrequency.CUSTOM) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    WEEKDAY_INITIAL_KEYS.map { tr(it) }.forEachIndexed { i, initial ->
                        val on = draft.weekdays.contains(i)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                .background(if (on) tint else c.card)
                                .border(1.dp, if (on) tint else c.sep, CircleShape)
                                .then(pressScale({ if (on) draft.weekdays.remove(i) else draft.weekdays.add(i) }, 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            TfText(
                                initial, color = if (on) onAccent(tint) else c.fg2,
                                size = 13f, weight = FontWeight.Bold, family = Fonts.mono,
                            )
                        }
                    }
                }
            }

            // time + reminder
            Column(
                Modifier.fillMaxWidth().padding(top = 18.dp)
                    .clip(RoundedCornerShape(14.dp)).background(c.card)
                    .border(0.5.dp, c.sep, RoundedCornerShape(14.dp)),
            ) {
                Column(Modifier.fillMaxWidth().drawBottomHairline(c.sep).padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        TfIcon("clock", size = 16, color = c.fg2)
                        TfText(tr("habits.form.time"), color = c.fg, size = 14.5f)
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        HABIT_TIME_CHOICES.forEach { t -> TimeChip(t, draft.timeLocal == t, tint) { draft.chooseTime(t) } }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    TfIcon("bell", size = 16, color = c.fg2)
                    TfText(tr("habits.action.reminder"), color = c.fg, size = 14.5f, modifier = Modifier.weight(1f))
                    TfSwitch(draft.reminder, tint) { draft.reminder = it }
                }
            }

            FieldLabel(tr("habits.form.difficulty"))
            Segmented(
                HabitDifficulty.entries.map { it.id to tr(it.labelKey) },
                draft.difficulty.id,
                { draft.difficulty = HabitDifficulty.from(it) },
                Modifier.fillMaxWidth(),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp).then(pressScale({ showMore = !showMore }, 0.97f)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TfText(
                    tr(if (showMore) "habits.form.fewer_options" else "habits.form.more_options"),
                    color = c.accent, size = 14f, weight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(7.dp))
                TfIcon(if (showMore) "chevronUp" else "chevronDown", size = 16, color = c.accent)
            }

            if (showMore) {
                FieldLabel(tr("habits.form.goal"))
                Segmented(
                    HabitGoalType.entries.map { it.id to tr(it.labelKey) },
                    draft.goalType.id,
                    { draft.goalType = HabitGoalType.from(it) },
                    Modifier.fillMaxWidth(),
                )
                if (draft.goalType != HabitGoalType.CHECK) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp)).background(c.card)
                            .border(0.5.dp, c.sep, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TfText(
                            tr(if (draft.goalType == HabitGoalType.DURATION) "habits.form.target_minutes" else "habits.form.target_daily"),
                            color = c.fg, size = 14.5f, modifier = Modifier.weight(1f),
                        )
                        StepButton("−", c.card2, c.fg, c.line) {
                            draft.goalTarget = (draft.goalTarget - 1).coerceAtLeast(1)
                        }
                        TfText(
                            "${draft.goalTarget}", color = c.fg, size = 20f, weight = FontWeight.Bold,
                            family = Fonts.display, align = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 34.dp),
                        )
                        StepButton("+", tint, onAccent(tint), tint) { draft.goalTarget += 1 }
                    }
                }

                FieldLabel(tr("habits.form.motivation"))
                TfTextField(
                    value = draft.motivation,
                    onValueChange = { draft.motivation = it },
                    placeholder = tr("habits.form.motivation_placeholder"),
                    singleLine = false,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Overline(text, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 2.dp))
}

@Composable
private fun TimeChip(value: String, on: Boolean, tint: Color, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (on) tint.soft(0.18f) else c.card2)
            .border(1.dp, if (on) tint else c.sep, RoundedCornerShape(9.dp))
            .then(pressScale(onClick, 0.94f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        TfText(
            value, color = if (on) tint else c.fg2, size = 13f,
            weight = if (on) FontWeight.Bold else FontWeight.Medium, family = Fonts.mono,
        )
    }
}

@Composable
private fun StepButton(glyph: String, background: Color, foreground: Color, border: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(background)
            .border(1.dp, border, RoundedCornerShape(9.dp))
            .then(pressScale(onClick, 0.88f)),
        contentAlignment = Alignment.Center,
    ) { TfText(glyph, color = foreground, size = 18f, weight = FontWeight.Bold, family = Fonts.mono) }
}
