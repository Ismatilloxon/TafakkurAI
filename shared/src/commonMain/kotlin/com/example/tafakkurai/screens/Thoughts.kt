package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*

@Composable
fun ThoughtsScreen(posts: List<SocialPost>, onCompose: () -> Unit, onLike: (String) -> Unit = {}) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        LargeTitle("Thoughts", right = {
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.accent.soft(0.16f)).then(pressScale(onCompose, 0.9f)), contentAlignment = Alignment.Center) {
                TfIcon("plus", size = 20, color = c.accent)
            }
        })
        ScrollBody {
            // compose stub
            Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp).clip(RoundedCornerShape(14.dp)).background(c.card)
                    .border(0.5.dp, c.sep, RoundedCornerShape(14.dp)).then(pressScale(onCompose, 0.985f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                UserAvatar(size = 32)
                TfText("Share a thought…", color = c.fg3, size = 14.5f, modifier = Modifier.weight(1f))
                TfIcon("penLine", size = 18, color = c.fg3)
            }
            // circles strip
            Overline("YOUR CIRCLES", color = c.fg3)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                TGROUPS.forEach { g ->
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(c.card2).border(0.5.dp, c.sep, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        TfIcon(g.icon, size = 15, color = if (g.active) c.green else c.fg3)
                        TfText(g.name, color = c.fg, size = 13f, weight = FontWeight.Medium)
                    }
                }
            }
            // feed
            Column(Modifier.padding(horizontal = HPAD.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                posts.forEach { p -> PostCard(p, onLike) }
            }
        }
    }
}

@Composable
private fun PostCard(p: SocialPost, onLike: (String) -> Unit = {}) {
    val c = LocalTf.current
    var liked by remember { mutableStateOf(false) }
    var bookmarked by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(p.author, p.avatarSeed, size = 38)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TfText(p.author, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold)
                    TfText(p.handle, color = c.fg3, size = 12.5f)
                    TfText("· ${p.time}", color = c.fg3, size = 12.5f)
                }
            }
            Pill(p.tag, c.named("cyan"), c.named("cyan").soft(0.16f))
        }
        TfText(p.text, color = c.fg, size = 14.5f, lineHeight = 1.6f, modifier = Modifier.padding(top = 11.dp))
        Row(Modifier.fillMaxWidth().padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionStat("heart", if (liked) p.likes + 1 else p.likes, if (liked) c.rose else c.fg3) { liked = !liked; onLike(p.id) }
            ActionStat("messageCircle", p.comments, c.fg3) {}
            Spacer(Modifier.weight(1f))
            Box(Modifier.then(pressScale({ bookmarked = !bookmarked }, 0.9f))) {
                TfIcon("bookmark", size = 17, color = if (bookmarked) c.gold else c.fg3)
            }
        }
    }
}

@Composable
private fun ActionStat(icon: String, count: Int, color: Color, onClick: () -> Unit) {
    val c = LocalTf.current
    Row(Modifier.then(pressScale(onClick, 0.9f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TfIcon(icon, size = 17, color = color)
        TfText("$count", color = c.fg3, size = 12.5f, family = Fonts.mono)
    }
}

// ── Compose a thought ────────────────────────────────────────────────────────
private val THOUGHT_TYPES = listOf("Thought" to "Mindset", "Experience" to "Reflection", "Achievement" to "Wins")
private val MOODS = listOf("Grateful", "Hopeful", "Tested", "Calm")

@Composable
fun ComposeThoughtScreen(onBack: () -> Unit, onPublish: (SocialPost) -> Unit) {
    val c = LocalTf.current
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(0) }
    var mood by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        BackBar("New thought", onBack, right = {
            val enabled = text.isNotBlank()
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(if (enabled) c.accent else c.card3)
                    .then(pressScale({ if (enabled) onPublish(SocialPost("u${text.hashCode()}", TUSER.name, TUSER.handle, 0, "now", text.trim(), 0, 0, THOUGHT_TYPES[type].second)) }, 0.92f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) { TfText("Publish", color = if (enabled) Color.White else c.fg3, size = 14f, weight = FontWeight.SemiBold) }
        })
        ScrollBody {
            // type selector
            Box(Modifier.padding(horizontal = HPAD.dp)) {
                Segmented(THOUGHT_TYPES.mapIndexed { i, t -> i.toString() to t.first }, type.toString(), { type = it.toInt() })
            }
            // editor
            InsetCard(pad = 16) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    UserAvatar(size = 34)
                    TfText("@${TUSER.handle.removePrefix("@")} · to your circle", color = c.fg3, size = 12.5f)
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
                    if (text.isEmpty()) TfText("Share a thought, experience, or achievement…", color = c.fg3, size = 16f, lineHeight = 1.5f)
                    BasicTextField(
                        value = text, onValueChange = { text = it },
                        textStyle = TextStyle(color = c.fg, fontSize = 16.sp, fontFamily = Fonts.sans, lineHeight = 24.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // topic
            InsetCard(pad = 14) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    TfIcon("hash", size = 17, color = c.fg3)
                    Box(Modifier.weight(1f)) {
                        if (topic.isEmpty()) TfText("Add a topic (optional)", color = c.fg3, size = 14.5f)
                        BasicTextField(
                            value = topic, onValueChange = { topic = it }, singleLine = true,
                            textStyle = TextStyle(color = c.fg, fontSize = 14.5.sp, fontFamily = Fonts.sans),
                            cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            // mood
            Overline("MOOD")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MOODS.forEach { m ->
                    val on = mood == m
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) c.accent.soft(0.18f) else c.card2)
                            .border(0.5.dp, if (on) c.accent else c.sep, RoundedCornerShape(999.dp))
                            .then(pressScale({ mood = if (on) "" else m }, 0.95f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { TfText(m, color = if (on) c.accent else c.fg2, size = 13.5f, weight = FontWeight.Medium) }
                }
            }
            PrivacyFooter("Shared only with circles you choose. You can delete anytime.")
        }
    }
}

// ── Circles & Mentors ────────────────────────────────────────────────────────
@Composable
fun SocialScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    var tab by remember { mutableStateOf("circles") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Community", onBack)
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 10.dp)) {
            Segmented(listOf("circles" to "Circles", "mentors" to "Mentors"), tab, { tab = it })
        }
        ScrollBody {
            if (tab == "circles") {
                Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TGROUPS.forEach { g ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBadge(g.icon, c.named("cyan"), size = 44)
                                Column(Modifier.weight(1f)) {
                                    TfText(g.name, color = c.fg, size = 15.5f, weight = FontWeight.SemiBold)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                                        if (g.active) Box(Modifier.size(6.dp).clip(CircleShape).background(c.green))
                                        TfText("${g.members} members${if (g.active) " · active now" else ""}", color = c.fg2, size = 12.5f)
                                    }
                                }
                                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(c.accent.soft(0.16f)).then(pressScale({}, 0.95f)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                                    TfText("Join", color = c.accent, size = 13f, weight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                ProactiveCard(
                    "Madina is two steps ahead on your backend path and open to mentoring. Want a warm intro?",
                    action = "Request intro", modifier = Modifier.padding(horizontal = HPAD.dp),
                )
                Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TSOCIAL.forEach { p ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(p.author, p.avatarSeed, size = 46)
                                Column(Modifier.weight(1f)) {
                                    TfText(p.author, color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                                    TfText("A few steps ahead · ${p.tag}", color = c.fg2, size = 12.5f)
                                }
                                TfIcon("chevronRight", size = 18, color = c.fg3)
                            }
                        }
                    }
                }
                PrivacyFooter("Mentorship happens privately, on your terms.")
            }
        }
    }
}
