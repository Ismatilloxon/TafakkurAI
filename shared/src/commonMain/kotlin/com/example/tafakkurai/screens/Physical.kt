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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*
import kotlin.math.roundToInt

// ── Sleep & rest ─────────────────────────────────────────────────────────────
@Composable
fun SleepScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    val s = TSLEEP
    Column(Modifier.fillMaxSize()) {
        BackBar("Sleep", onBack)
        ScrollBody {
            InsetCard(pad = 18) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    RingGauge(s.score / 100f, c.named("purple"), c.card3, strokeWidth = 8f, size = 96) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            TfText("${s.score}", color = c.fg, size = 28f, weight = FontWeight.Bold, family = Fonts.mono)
                            TfText("score", color = c.fg3, size = 10f)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TfText("${s.asleep} asleep", color = c.fg, size = 18f, weight = FontWeight.Bold, family = Fonts.display)
                        SleepMeta("Bedtime", s.bedtime); SleepMeta("Wake", s.wake); SleepMeta("In bed", s.inBed)
                    }
                }
            }
            // Hypnogram
            Overline("LAST NIGHT", color = c.fg3)
            InsetCard {
                Hypnogram(s.stages)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("awake" to "Awake", "rem" to "REM", "core" to "Core", "deep" to "Deep").forEach { (k, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(SLEEP_COL.getValue(k)))
                            TfText(label, color = c.fg3, size = 11f)
                        }
                    }
                }
            }
            // Stage breakdown
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                s.breakdown.forEach { b ->
                    Card(Modifier.fillMaxWidth(), pad = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(b.color))
                            TfText(b.label, color = c.fg, size = 14f, weight = FontWeight.SemiBold)
                            TfText(b.sub, color = c.fg3, size = 11.5f, modifier = Modifier.weight(1f))
                            TfText("${b.min / 60}h ${b.min % 60}m", color = c.fg2, size = 13f, family = Fonts.mono)
                        }
                        ProgressBar(b.pct, b.color, modifier = Modifier.fillMaxWidth().padding(top = 9.dp))
                    }
                }
            }
            // metric tiles
            Row(Modifier.fillMaxWidth().padding(horizontal = HPAD.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.metrics.forEach { m -> StatTile("${m.value}${if (m.unit.isNotEmpty()) " " + m.unit else ""}", m.label, m.color) }
            }
            // weekly bars
            Overline("THIS WEEK · GOAL ${s.goalH}h", color = c.fg3)
            InsetCard {
                MiniBars(s.week, c.named("purple"), c.card3, modifier = Modifier.fillMaxWidth().height(80.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { TfText(it, color = c.fg3, size = 10f, family = Fonts.mono) }
                }
            }
            ProactiveCard("You averaged ${s.week.average().let { (it * 10).roundToInt() / 10.0 }}h this week. Going to bed 30 minutes earlier would make Fajr noticeably easier.", modifier = Modifier.padding(horizontal = HPAD.dp))
        }
    }
}

@Composable
private fun SleepMeta(label: String, value: String) {
    val c = LocalTf.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TfText(label, color = c.fg3, size = 12f, modifier = Modifier.width(58.dp))
        TfText(value, color = c.fg2, size = 12f, family = Fonts.mono)
    }
}

@Composable
private fun Hypnogram(stages: List<SleepStage>) {
    val laneOf = mapOf("awake" to 0, "rem" to 1, "core" to 2, "deep" to 3)
    val total = stages.sumOf { it.m }.toFloat().coerceAtLeast(1f)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val laneH = size.height / 4f
        var x = 0f
        stages.forEach { st ->
            val w = (st.m / total) * size.width
            val lane = laneOf[st.s] ?: 2
            val col = SLEEP_COL[st.s] ?: Color.Gray
            drawRoundRect(
                color = col,
                topLeft = Offset(x, lane * laneH + laneH * 0.18f),
                size = Size(w.coerceAtLeast(1f), laneH * 0.64f),
                cornerRadius = CornerRadius(3f, 3f),
            )
            x += w
        }
    }
}

// ── Sport & Gym ──────────────────────────────────────────────────────────────
@Composable
fun SportGymScreen(onBack: () -> Unit) {
    var setup by remember { mutableStateOf(true) }
    var weight by remember { mutableStateOf(72) }
    var height by remember { mutableStateOf(176) }
    var age by remember { mutableStateOf(24) }
    var goal by remember { mutableStateOf(1) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Sport & Gym", onBack)
        if (setup) SportSetup(weight, { weight = it }, height, { height = it }, age, { age = it }, goal, { goal = it }) { setup = false }
        else SportDashboard(weight, goal)
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
            TfText("Let's set you up", color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display)
            TfText("A few numbers so I can tailor training and track balance.", color = c.fg2, size = 13.5f, lineHeight = 1.5f)
        }
        InsetCard {
            Stepper("Height", "$height cm", { onHeight(height - 1) }, { onHeight(height + 1) })
            Spacer(Modifier.height(10.dp)); Stepper("Weight", "$weight kg", { onWeight(weight - 1) }, { onWeight(weight + 1) })
            Spacer(Modifier.height(10.dp)); Stepper("Age", "$age", { onAge(age - 1) }, { onAge(age + 1) })
        }
        InsetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("LIVE BMI")
                    TfText(((bmi * 10).roundToInt() / 10.0).toString(), color = c.fg, size = 30f, weight = FontWeight.Bold, family = Fonts.mono)
                }
                val (label, col) = when {
                    bmi < 18.5 -> "Underweight" to c.warn
                    bmi < 25 -> "Healthy" to c.green
                    bmi < 30 -> "Overweight" to c.warn
                    else -> "High" to c.danger
                }
                Pill(label, col, col.soft(0.16f))
            }
        }
        Overline("GOAL")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TSPORT_GOALS.forEachIndexed { i, g ->
                val on = goal == i
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(if (on) c.accent.soft(0.16f) else c.card)
                        .border(if (on) 1.dp else 0.5.dp, if (on) c.accent else c.sep, RoundedCornerShape(13.dp))
                        .then(pressScale({ onGoal(i) }, 0.99f)).padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TfText(g, color = c.fg, size = 15f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    if (on) TfIcon("checkCircle", size = 18, color = c.accent)
                }
            }
        }
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) { PrimaryButton("Continue", onClick = onDone) }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = LocalTf.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TfText(label, color = c.fg, size = 15f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepBtn("x", onMinus, true)  // minus rendered as small bar via icon fallback
            TfText(value, color = c.fg, size = 15f, family = Fonts.mono, modifier = Modifier.widthIn(min = 64.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
            StepBtn("plus", onPlus, false)
        }
    }
}

@Composable
private fun StepBtn(icon: String, onClick: () -> Unit, minus: Boolean) {
    val c = LocalTf.current
    Box(Modifier.size(34.dp).clip(CircleShape).background(c.card3).then(pressScale(onClick, 0.9f)), contentAlignment = Alignment.Center) {
        if (minus) Box(Modifier.size(width = 12.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(c.fg2))
        else TfIcon("plus", size = 16, color = c.fg2)
    }
}

@Composable
private fun ColumnScope.SportDashboard(weight: Int, goal: Int) {
    val c = LocalTf.current
    val activities = remember { mutableStateListOf<SportActivity>().apply { addAll(TSPORT_DEFAULT.activities) } }
    val week = remember { mutableStateListOf<Int>().apply { addAll(TSPORT_DEFAULT.weekMin) } }
    var adding by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(0) }
    var dur by remember { mutableStateOf(30) }

    val kcalGoal = 500; val minGoal = 60
    val kcal = activities.sumOf { it.kcal }
    val mins = activities.sumOf { it.min }

    ScrollBody {
        // dual rings
        InsetCard(pad = 18) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    RingGauge(kcal.toFloat() / kcalGoal, c.named("rose"), c.card3, strokeWidth = 8f, size = 120)
                    RingGauge(mins.toFloat() / minGoal, c.green, c.card3, strokeWidth = 8f, size = 88)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TfText("$kcal", color = c.fg, size = 22f, weight = FontWeight.Bold, family = Fonts.mono)
                        TfText("kcal", color = c.fg3, size = 10f)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(c.named("rose")))
                        TfText("$kcal / $kcalGoal kcal", color = c.fg2, size = 13f, family = Fonts.mono)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(c.green))
                        TfText("$mins / $minGoal min", color = c.fg2, size = 13f, family = Fonts.mono)
                    }
                    Pill("${activities.size} sessions · +6 Physical", c.green, c.green.soft(0.16f))
                }
            }
        }
        // add activity
        if (!adding) {
            Box(Modifier.padding(horizontal = HPAD.dp)) { PrimaryButton("Add activity") { adding = true } }
        } else {
            val t = TSPORT_TYPES[pick]
            val computed = (t.met * weight * dur / 60f).roundToInt()
            InsetCard {
                Overline("PICK A SPORT")
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
                                        TfText(sp.name, color = c.fg2, size = 11f)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Overline("DURATION")
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TSPORT_DURATIONS.forEach { d ->
                        val on = dur == d
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) c.accent.soft(0.18f) else c.card3)
                                .then(pressScale({ dur = d }, 0.95f)).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { TfText("${d}m", color = if (on) c.accent else c.fg2, size = 12.5f, family = Fonts.mono) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overline("ESTIMATED")
                        TfText("$computed kcal", color = c.fg, size = 20f, weight = FontWeight.Bold, family = Fonts.mono)
                    }
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(c.accent).then(pressScale({
                        activities.add(SportActivity("a${activities.size + 1}", t.key, t.name, t.icon, t.color, dur, computed, "now"))
                        week[6] = week[6] + dur
                        adding = false
                    }, 0.96f)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                        TfText("Log", color = Color.White, size = 14.5f, weight = FontWeight.SemiBold)
                    }
                }
            }
        }
        // logged today
        if (activities.isEmpty()) EmptyNote("No sessions yet today. Even a short walk counts.")
        else {
            SectionTitle("Logged today")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activities.forEach { a ->
                    Card(Modifier.fillMaxWidth(), pad = 13) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(a.icon, a.color, size = 40)
                            Column(Modifier.weight(1f)) {
                                TfText(a.name, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold)
                                TfText("${a.min} min · ${a.kcal} kcal · ${a.time}", color = c.fg3, size = 12f, family = Fonts.mono)
                            }
                            Box(Modifier.then(pressScale({ activities.remove(a) }, 0.9f)).padding(4.dp)) { TfIcon("x", size = 16, color = c.fg3) }
                        }
                    }
                }
            }
        }
        // weekly bars
        Overline("ACTIVE MINUTES · THIS WEEK", color = c.fg3)
        InsetCard {
            MiniBars(week.map { it.toFloat() }, c.green, c.card3, modifier = Modifier.fillMaxWidth().height(72.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { TfText(it, color = c.fg3, size = 10f, family = Fonts.mono) }
            }
        }
        ProactiveCard("That's $mins active minutes today — over halfway to your goal. Strength sessions are lifting your Physical balance the most.", modifier = Modifier.padding(horizontal = HPAD.dp))
    }
}
