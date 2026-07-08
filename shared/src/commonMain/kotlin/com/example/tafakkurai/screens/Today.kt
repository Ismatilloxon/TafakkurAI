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
import com.example.tafakkurai.prayer.SalahProgressHero
import com.example.tafakkurai.ui.*

private val ASPECT = mapOf(
    "spiritual" to "gold", "physical" to "green", "educational" to "purple",
    "social" to "cyan", "financial" to "gold",
)

private data class BlockDef(val key: String, val title: String, val aspect: String, val color: String)

private val ALL_BLOCKS = listOf(
    BlockDef("prayer", "Prayer times", "Spiritual", "gold"),
    BlockDef("ai", "AI guidance", "Educational", "purple"),
    BlockDef("plan", "Today's plan", "Productivity", "teal"),
    BlockDef("balance", "Balance", "Spiritual", "cyan"),
    BlockDef("habits", "Habits", "Physical", "green"),
    BlockDef("gratitude", "Gratitude", "Spiritual", "gold"),
    BlockDef("continue", "Continue learning", "Educational", "purple"),
    BlockDef("circle", "From your circle", "Social", "cyan"),
    BlockDef("portfolio", "Portfolio", "Financial", "gold"),
    BlockDef("sleep", "Sleep", "Physical", "green"),
)
private val DEFAULT_ORDER = listOf("prayer", "ai", "plan", "balance", "habits")

@Composable
fun TodayScreen(onOpenModule: (String) -> Unit, onCoach: () -> Unit, onProfile: () -> Unit = {}) {
    val c = LocalTf.current
    var customize by remember { mutableStateOf(false) }
    val order = remember { mutableStateListOf<String>().apply { addAll(DEFAULT_ORDER) } }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Overline("BISMILLAH.")
                TfText(
                    "${TUSER.greetingUz}, ${TUSER.name}",
                    color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display,
                    modifier = Modifier.padding(top = 3.dp),
                )
                TfText("$TCITY · ${TUSER.today}", color = c.fg2, size = 13f, modifier = Modifier.padding(top = 2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.then(pressScale({ customize = !customize }, 0.95f))) {
                    Pill(if (customize) "Done" else "Customize", c.accent, c.accent.soft(0.16f))
                }
                UserAvatar(onClick = onProfile)
            }
        }

        if (customize) {
            CustomizeBoard(order)
        } else {
            ScrollScreen {
                // Hero ring — live, fills clockwise toward the next prayer
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    SalahProgressHero()
                }
                TfText(
                    "Your day starts at Maghrib — plan mindful of Salah.",
                    color = c.fg3, size = 12.5f, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                order.forEach { key -> TodayBlock(key, onOpenModule, onCoach) }
            }
        }
    }
}

@Composable
private fun TodayBlock(key: String, onOpenModule: (String) -> Unit, onCoach: () -> Unit) {
    val def = ALL_BLOCKS.first { it.key == key }
    val c = LocalTf.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectHeading(def.title, c.named(def.color))
        when (key) {
            "prayer" -> InsetCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TPRAYER_TODAY.forEach { p ->
                        val on = p.state != "upcoming"
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (on) c.green.soft(0.18f) else c.card3),
                                contentAlignment = Alignment.Center,
                            ) { TfIcon(if (on) "checkCircle" else "circle", size = 17, color = if (on) c.green else c.fg3) }
                            TfText(p.name, color = if (on) c.fg else c.fg3, size = 11f, weight = FontWeight.Medium)
                            TfText(p.time, color = c.fg3, size = 10f, family = Fonts.mono)
                        }
                    }
                }
            }
            "ai" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = HPAD.dp)) {
                ProactiveCard(TBRIEF.line)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
                        .border(0.5.dp, c.sep, RoundedCornerShape(14.dp))
                        .then(pressScale(onCoach, 0.985f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent.soft(0.16f)), contentAlignment = Alignment.Center) {
                        TfIcon("sparkles", size = 18, color = c.accent)
                    }
                    TfText("Ask Murabbiy anything", color = c.fg, size = 14.5f, modifier = Modifier.weight(1f))
                    TfIcon("chevronRight", size = 18, color = c.fg3)
                }
            }
            "plan" -> InsetCard {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TDAYPLAN.take(6).forEach { item -> PlanRow(item) { item.cat?.let { } } }
                }
            }
            "balance" -> InsetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        RadarChart(TBALANCE.map { it.value }, TBALANCE.map { it.color }, c.sep, size = 150)
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TBALANCE.sortedBy { it.value }.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(d.color))
                                TfText(d.label, color = c.fg2, size = 12.5f, modifier = Modifier.weight(1f))
                                TfText("${(d.value * 100).toInt()}", color = c.fg, size = 12.5f, family = Fonts.mono, weight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            "habits" -> Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                THABITS.take(4).forEach { h ->
                    Card(Modifier.weight(1f), pad = 12) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            RingGauge(
                                if (h.done) 1f else 0.6f, c.named(if (h.accent == "blue") "cyan" else h.accent), c.card3,
                                strokeWidth = 4f, size = 40,
                            ) { TfIcon(h.icon, size = 16, color = c.named(if (h.accent == "blue") "cyan" else h.accent)) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                TfIcon("flame", size = 11, color = c.warn)
                                TfText("${h.streak}", color = c.fg2, size = 11f, family = Fonts.mono)
                            }
                        }
                    }
                }
            }
            "gratitude" -> InsetCard {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    TGRATITUDE_TODAY.forEach { g ->
                        Pill(g, c.gold, c.gold.soft(0.16f), leading = { TfIcon("leaf", size = 11, color = c.gold) })
                    }
                }
            }
            "continue" -> InsetCard(onClick = { onOpenModule("Learn") }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge("terminal", c.named("cyan"), size = 42)
                    Column(Modifier.weight(1f)) {
                        TfText(TSUBJECTS[1].next, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold)
                        TfText(TSUBJECTS[1].name, color = c.fg2, size = 12.5f)
                        ProgressBar(TSUBJECTS[1].progress, c.named("cyan"), modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    TfIcon("play", size = 18, color = c.accent)
                }
            }
            "circle" -> InsetCard {
                val p = TSOCIAL.first()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(p.author, p.avatarSeed)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TfText(p.author, color = c.fg, size = 13.5f, weight = FontWeight.SemiBold)
                            TfText("· ${p.time}", color = c.fg3, size = 12f)
                        }
                        TfText(p.text, color = c.fg2, size = 13f, lineHeight = 1.5f, maxLines = 3, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            "portfolio" -> InsetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Overline("NET WORTH")
                        TfText("$${TFINANCE.net}", color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.mono)
                    }
                    Pill("+${TFINANCE.change}%", c.green, c.green.soft(0.16f), leading = { TfIcon("trendingUp", size = 11, color = c.green) })
                }
            }
            "sleep" -> InsetCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RingGauge(TSLEEP.score / 100f, c.named("purple"), c.card3, strokeWidth = 5f, size = 48) {
                        TfText("${TSLEEP.score}", color = c.fg, size = 15f, weight = FontWeight.Bold, family = Fonts.mono)
                    }
                    Column(Modifier.weight(1f)) {
                        TfText("${TSLEEP.asleep} asleep", color = c.fg, size = 14.5f, weight = FontWeight.SemiBold)
                        TfText("Bed ${TSLEEP.bedtime} · Wake ${TSLEEP.wake}", color = c.fg2, size = 12.5f)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(item: DayPlanItem, onTap: () -> Unit) {
    val c = LocalTf.current
    val salah = item.type == "salah"
    val tint = when (item.cat) {
        "physical" -> c.green; "social" -> c.cyan; "financial" -> c.gold
        "educational" -> c.purple; "spiritual" -> c.gold; else -> c.accent
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        TfText(item.time, color = if (item.current) c.accent else c.fg3, size = 12f, family = Fonts.mono, modifier = Modifier.width(42.dp))
        Box(
            Modifier.size(if (salah) 26.dp else 22.dp).clip(CircleShape)
                .background(if (salah) c.gold.soft(0.16f) else tint.soft(0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) TfIcon("check", size = 13, color = c.green)
            else TfIcon(if (salah) "moonStars" else "circle", size = 13, color = if (salah) c.gold else tint)
        }
        Column(Modifier.weight(1f)) {
            TfText(
                item.name ?: item.title ?: "", color = if (item.done) c.fg3 else c.fg, size = 14f,
                weight = if (salah) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (!salah && item.dur != null) TfText(item.dur, color = c.fg3, size = 11.5f)
        }
        if (item.current) Pill("now", c.accent, c.accent.soft(0.16f))
        else if (salah && item.state == "next") Pill("next", c.gold, c.gold.soft(0.16f))
    }
}

// ── Customize board ──────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.CustomizeBoard(order: MutableList<String>) {
    val c = LocalTf.current
    ScrollScreen {
        Overline("YOUR BLOCKS")
        Box(Modifier.padding(horizontal = 20.dp).offset(y = (-8).dp)) {
            TfText("Reorder, remove, or add blocks. Your layout is saved on this device.", color = c.fg2, size = 13f, lineHeight = 1.5f)
        }
        order.toList().forEachIndexed { i, key ->
            val def = ALL_BLOCKS.first { it.key == key }
            InsetCard(pad = 12) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.named(def.color)))
                    TfText(def.title, color = c.fg, size = 15f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    IconButton("chevronUp", c.fg2) { if (i > 0) { val t = order[i]; order[i] = order[i - 1]; order[i - 1] = t } }
                    IconButton("chevronDown", c.fg2) { if (i < order.size - 1) { val t = order[i]; order[i] = order[i + 1]; order[i + 1] = t } }
                    IconButton("x", c.danger) { order.remove(key) }
                }
            }
        }
        SectionTitle("Add blocks")
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ALL_BLOCKS.filter { it.key !in order }.forEach { def ->
                Card(Modifier.fillMaxWidth(), pad = 12, onClick = { order.add(def.key) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c.named(def.color)))
                        TfText(def.title, color = c.fg, size = 15f, modifier = Modifier.weight(1f))
                        TfIcon("plus", size = 18, color = c.accent)
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(0.5.dp, c.line, RoundedCornerShape(12.dp))
                    .then(pressScale({ order.clear(); order.addAll(DEFAULT_ORDER) }, 0.98f)).padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) { TfText("Reset to default layout", color = c.fg2, size = 14f, weight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun IconButton(icon: String, color: Color, onClick: () -> Unit) {
    val c = LocalTf.current
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(c.card3).then(pressScale(onClick, 0.9f)),
        contentAlignment = Alignment.Center,
    ) { TfIcon(icon, size = 16, color = color) }
}

// ── Hub launcher screen (hub nav mode root) ──────────────────────────────────
@Composable
fun HubScreen(onOpen: (String) -> Unit, onProfile: () -> Unit = {}) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("BISMILLAH.")
                TfText("${TUSER.greetingUz}, ${TUSER.name}", color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display, modifier = Modifier.padding(top = 3.dp))
            }
            UserAvatar(onClick = onProfile)
        }
        ScrollScreen {
            ProactiveCard(TBRIEF.line, modifier = Modifier.padding(horizontal = HPAD.dp))
            HubLauncher(onOpen)
        }
    }
}
