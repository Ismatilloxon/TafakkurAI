package com.example.tafakkurai.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.i18n.weekdayIndex
import com.example.tafakkurai.prayer.deviceDate
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.sync.RecordedActivity
import com.example.tafakkurai.sync.activityUuidV4
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.ui.components.TfSecondaryButton
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.mp.KoinPlatform.getKoin
import uz.tafakkur.domain.usecase.CreateTrackerEntryUseCase
import kotlin.math.roundToInt

// ── Sleep & rest ─────────────────────────────────────────────────────────────
//
// Sleep lives in `com.example.tafakkurai.sleep`; `SleepScreen` scores real sessions from
// `SleepStore`. No static sleep sample remains in the production source set.

// ── Sport & Gym ──────────────────────────────────────────────────────────────
private data class RecordedSportActivity(
    val id: String,
    val type: SportType,
    val minutes: Int,
    val kcal: Int,
    val localDate: String,
    val occurredAt: Long,
)

private fun projectSportActivities(events: List<RecordedActivity>): List<RecordedSportActivity> {
    val removed = events.asSequence()
        .filter { it.feature in SPORT_EVENT_FEATURES && it.type == "sport_activity_removed" }
        .map { it.entityId }
        .toSet()
    return events.asSequence()
        .filter { it.feature in SPORT_EVENT_FEATURES && it.type == "sport_activity_logged" }
        .filter { it.entityId !in removed }
        .distinctBy { it.entityId }
        .mapNotNull { event ->
            val key = event.properties["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val type = TSPORT_TYPES.firstOrNull { it.key == key } ?: return@mapNotNull null
            val minutes = event.properties["minutes"]?.jsonPrimitive?.content?.toIntOrNull()
                ?.coerceIn(1, MAX_SPORT_MINUTES) ?: return@mapNotNull null
            val kcal = event.properties["kcal"]?.jsonPrimitive?.content?.toIntOrNull()
                ?.coerceAtLeast(0) ?: 0
            RecordedSportActivity(event.entityId, type, minutes, kcal, event.localDate, event.occurredAt)
        }
        .sortedByDescending { it.occurredAt }
        .toList()
}

private fun sportDateKey(offsetDays: Int): String {
    val date = deviceDate(offsetDays)
    fun pad(value: Int): String = value.toString().padStart(2, '0')
    return "${date.year}-${pad(date.month)}-${pad(date.day)}"
}

private const val MAX_SPORT_MINUTES = 12 * 60
private val SPORT_EVENT_FEATURES = setOf("sport", "physical")

@Composable
fun SportGymScreen(onBack: () -> Unit) {
    val activity = remember { getKoin().get<ActivityLog>() }
    val events by activity.events.collectAsState()
    val profile = events.firstOrNull {
        it.feature in SPORT_EVENT_FEATURES && it.type == "sport_profile_configured"
    }
    var setup by remember(profile?.id) { mutableStateOf(profile == null) }
    var weight by remember(profile?.id) {
        mutableStateOf(profile?.properties?.get("weight_kg")?.jsonPrimitive?.content?.toIntOrNull() ?: 72)
    }
    var height by remember(profile?.id) {
        mutableStateOf(profile?.properties?.get("height_cm")?.jsonPrimitive?.content?.toIntOrNull() ?: 176)
    }
    var age by remember(profile?.id) {
        mutableStateOf(profile?.properties?.get("age")?.jsonPrimitive?.content?.toIntOrNull() ?: 24)
    }
    var goal by remember(profile?.id) {
        mutableStateOf(profile?.properties?.get("goal_index")?.jsonPrimitive?.content?.toIntOrNull() ?: 1)
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("sport.title"), onBack)
        if (setup) SportSetup(weight, { weight = it }, height, { height = it }, age, { age = it }, goal, { goal = it }) {
            val saved = activity.record(
                "sport",
                "sport_profile_configured",
                buildJsonObject {
                    put("weight_kg", weight)
                    put("height_cm", height)
                    put("age", age)
                    put("goal_index", goal)
                },
            )
            if (saved) setup = false
        }
        else SportDashboard(weight, goal, onEditProfile = { setup = true })
    }
}

@Composable
private fun ColumnScope.SportSetup(
    weight: Int, onWeight: (Int) -> Unit, height: Int, onHeight: (Int) -> Unit,
    age: Int, onAge: (Int) -> Unit, goal: Int, onGoal: (Int) -> Unit, onDone: () -> Unit,
) {
    val c = LocalTf.current
    val bmi = weight / ((height / 100f) * (height / 100f))
    ScrollBody {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TfText(tr("sport.setup.title"), color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display)
            TfText(tr("sport.setup.detail"), color = c.fg2, size = 13.5f, lineHeight = 1.5f)
        }
        InsetCard {
            Stepper(
                tr("sport.setup.height"), tr("sport.unit.centimeters", height),
                { onHeight((height - 1).coerceAtLeast(100)) }, { onHeight((height + 1).coerceAtMost(240)) },
            )
            Spacer(Modifier.height(10.dp))
            Stepper(
                tr("sport.setup.weight"), tr("sport.unit.kilograms", weight),
                { onWeight((weight - 1).coerceAtLeast(30)) }, { onWeight((weight + 1).coerceAtMost(300)) },
            )
            Spacer(Modifier.height(10.dp))
            Stepper(
                tr("sport.setup.age"), "$age",
                { onAge((age - 1).coerceAtLeast(13)) }, { onAge((age + 1).coerceAtMost(100)) },
            )
        }
        InsetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline(tr("sport.setup.bmi"))
                    TfText(((bmi * 10).roundToInt() / 10.0).toString(), color = c.fg, size = 30f, weight = FontWeight.Bold, family = Fonts.mono)
                }
                val (label, col) = when {
                    bmi < 18.5 -> tr("sport.bmi.underweight") to c.warn
                    bmi < 25 -> tr("sport.bmi.healthy") to c.green
                    bmi < 30 -> tr("sport.bmi.overweight") to c.warn
                    else -> tr("sport.bmi.high") to c.danger
                }
                Pill(label, col, col.soft(0.16f))
            }
        }
        Overline(tr("sport.setup.goal"))
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TSPORT_GOALS.forEachIndexed { i, g ->
                val on = goal == i
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (on) c.accent.soft(0.16f) else c.card)
                        .border(if (on) 1.dp else 0.5.dp, if (on) c.accent else c.sep, RoundedCornerShape(13.dp))
                        .then(pressScale({ onGoal(i) }, 0.99f)).padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TfText(tr("sport.goal.$g"), color = c.fg, size = 15f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (on) TfIcon("checkCircle", size = 18, color = c.accent)
                }
            }
        }
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
            TfPrimaryButton(tr("common.continue"), onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = LocalTf.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TfText(label, color = c.fg, size = 15f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepBtn(onMinus, true, tr("sport.action.decrease", label))
            TfText(value, color = c.fg, size = 15f, family = Fonts.mono, modifier = Modifier.widthIn(min = 64.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
            StepBtn(onPlus, false, tr("sport.action.increase", label))
        }
    }
}

@Composable
private fun StepBtn(onClick: () -> Unit, minus: Boolean, description: String) {
    val c = LocalTf.current
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(c.card3)
            .semantics { contentDescription = description }
            .then(pressScale(onClick, 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        if (minus) Box(Modifier.size(width = 12.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(c.fg2))
        else TfIcon("plus", size = 16, color = c.fg2)
    }
}

@Composable
private fun ColumnScope.SportDashboard(weight: Int, goal: Int, onEditProfile: () -> Unit) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val activity = remember { getKoin().get<ActivityLog>() }
    val createEntry = remember { getKoin().get<CreateTrackerEntryUseCase>() }
    val events by activity.events.collectAsState()
    val projected = remember(events) { projectSportActivities(events) }
    val todayKey = sportDateKey(0)
    val activities = remember(projected, todayKey) { projected.filter { it.localDate == todayKey } }
    val weekOffsets = remember(todayKey) { (-6..0).toList() }
    val weekKeys = remember(weekOffsets) { weekOffsets.map(::sportDateKey) }
    val weekDayIds = remember(weekOffsets) {
        val ids = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        weekOffsets.map { offset -> ids[weekdayIndex(deviceDate(offset))] }
    }
    val week = remember(projected, weekKeys) {
        weekKeys.map { key -> projected.filter { it.localDate == key }.sumOf { it.minutes } }
    }
    var adding by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(0) }
    var dur by remember { mutableStateOf(30) }

    val kcalGoal = 500; val minGoal = 60
    val kcal = activities.sumOf { it.kcal }
    val mins = activities.sumOf { it.minutes }

    ScrollBody {
        Box(Modifier.padding(horizontal = HPAD.dp)) {
            TfSecondaryButton(
                tr("sport.profile.edit"),
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // dual rings
        InsetCard(pad = 18) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    RingGauge(kcal.toFloat() / kcalGoal, c.named("rose"), c.card3, strokeWidth = 8f, size = 120)
                    RingGauge(mins.toFloat() / minGoal, c.green, c.card3, strokeWidth = 8f, size = 88)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TfText("$kcal", color = c.fg, size = 22f, weight = FontWeight.Bold, family = Fonts.mono)
                        TfText(tr("sport.unit.kcal"), color = c.fg3, size = 10f)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(c.named("rose")))
                        TfText(tr("sport.summary.kcal", kcal, kcalGoal), color = c.fg2, size = 13f, family = Fonts.mono)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(c.green))
                        TfText(tr("sport.summary.minutes", mins, minGoal), color = c.fg2, size = 13f, family = Fonts.mono)
                    }
                    Pill(tr("sport.summary.sessions", activities.size), c.green, c.green.soft(0.16f))
                }
            }
        }
        // add activity
        if (!adding) {
            Box(Modifier.padding(horizontal = HPAD.dp)) {
                TfPrimaryButton(tr("sport.activity.add"), onClick = { adding = true }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            val t = TSPORT_TYPES[pick]
            val computed = (t.met * weight * dur / 60f).roundToInt()
            InsetCard {
                Overline(tr("sport.activity.pick"))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // wrap types in two rows
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TSPORT_TYPES.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { sp ->
                                    val on = TSPORT_TYPES.indexOf(sp) == pick
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                            .background(if (on) sp.color.soft(0.18f) else c.card3)
                                            .border(0.5.dp, if (on) sp.color else c.sep, RoundedCornerShape(12.dp))
                                            .then(pressScale({ pick = TSPORT_TYPES.indexOf(sp) }, 0.96f)).padding(vertical = 11.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        TfIcon(sp.icon, size = 19, color = sp.color)
                                        TfText(tr("sport.type.${sp.key}"), color = c.fg2, size = 11f)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Overline(tr("sport.activity.duration"))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TSPORT_DURATIONS.forEach { d ->
                        val on = dur == d
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) c.accent.soft(0.18f) else c.card3)
                                .then(pressScale({ dur = d }, 0.95f)).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { TfText(tr("sport.unit.minutes_short", d), color = if (on) c.accent else c.fg2, size = 12.5f, family = Fonts.mono) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overline(tr("sport.activity.estimated"))
                        TfText(tr("sport.unit.kcal_value", computed), color = c.fg, size = 20f, weight = FontWeight.Bold, family = Fonts.mono)
                    }
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.accent).then(pressScale({
                        val activityId = activityUuidV4()
                        val saved = activity.record(
                            "sport",
                            "sport_activity_logged",
                            buildJsonObject {
                                put("type", t.key)
                                put("minutes", dur)
                                put("kcal", computed)
                            },
                            entityId = activityId,
                        )
                        if (saved) {
                            adding = false
                            scope.launch {
                                runCatching {
                                    createEntry(
                                        "sport",
                                        buildJsonObject {
                                            put("type", t.key)
                                            put("minutes", dur)
                                            put("kcal", computed)
                                        }.toString(),
                                    )
                                }
                            }
                        }
                    }, 0.96f)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                        TfText(tr("sport.activity.log"), color = Color.White, size = 14.5f, weight = FontWeight.SemiBold)
                    }
                }
            }
        }
        // logged today
        if (activities.isEmpty()) EmptyNote(tr("sport.activity.empty"))
        else {
            SectionTitle(tr("sport.activity.logged_today"))
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activities.forEach { a ->
                    val activityLabel = tr("sport.type.${a.type.key}")
                    val removeDescription = tr("sport.activity.remove_a11y", activityLabel)
                    Card(Modifier.fillMaxWidth(), pad = 13) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(a.type.icon, a.type.color, size = 40)
                            Column(Modifier.weight(1f)) {
                                TfText(activityLabel, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold)
                                TfText(
                                    tr("sport.activity.meta", a.minutes, a.kcal),
                                    color = c.fg3, size = 12f, family = Fonts.mono,
                                )
                            }
                            Box(Modifier.semantics {
                                contentDescription = removeDescription
                            }.then(pressScale({
                                activity.record(
                                    "sport",
                                    "sport_activity_removed",
                                    buildJsonObject { put("type", a.type.key) },
                                    entityId = a.id,
                                )
                            }, 0.9f)).padding(4.dp)) { TfIcon("x", size = 16, color = c.fg3) }
                        }
                    }
                }
            }
        }
        // weekly bars
        Overline(tr("sport.week.overline"), color = c.fg3)
        InsetCard {
            MiniBars(week.map { it.toFloat() }, c.green, c.card3, modifier = Modifier.fillMaxWidth().height(72.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                weekDayIds.forEach {
                    TfText(tr("weekday.short.$it"), color = c.fg3, size = 10f, family = Fonts.mono)
                }
            }
        }
        ProactiveCard(
            if (mins == 0) tr("sport.insight.empty")
            else tr("sport.insight.today", mins, tr("sport.goal.${TSPORT_GOALS[goal.coerceIn(TSPORT_GOALS.indices)]}")),
            modifier = Modifier.padding(horizontal = HPAD.dp),
        )
    }
}
