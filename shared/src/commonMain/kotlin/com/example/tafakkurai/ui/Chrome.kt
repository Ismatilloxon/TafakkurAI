package com.example.tafakkurai.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.ChatMsg
import com.example.tafakkurai.TCHAT
import com.example.tafakkurai.TCHAT_SUGGEST
import com.example.tafakkurai.aiReply
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Avatar (colored initials) ────────────────────────────────────────────────
private val AVATAR_COLORS = listOf(
    Color(0xFF3D7BFF), Color(0xFF2BB673), Color(0xFFC9A24E), Color(0xFF7C5CFF),
    Color(0xFFE07B3B), Color(0xFF4CC2FF), Color(0xFFE5486B),
)

@Composable
fun Avatar(name: String, seed: Int = 0, size: Int = 32) {
    val c = AVATAR_COLORS[((if (seed != 0) seed else name.firstOrNull()?.code ?: 0) % AVATAR_COLORS.size).coerceAtLeast(0)]
    val initials = name.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2).uppercase()
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c),
        contentAlignment = Alignment.Center,
    ) { TfText(initials, color = Color.White, size = size * 0.4f, weight = FontWeight.SemiBold) }
}

/** Gradient initial avatar for the user ("A"). */
@Composable
fun UserAvatar(size: Int = 34, letter: String = "A", onClick: (() -> Unit)? = null) {
    val c = LocalTf.current
    val photo = com.example.tafakkurai.ProfileImage.bitmap
    val base = Modifier.size(size.dp).clip(CircleShape)
        .background(Brush.linearGradient(listOf(c.gold, c.rose)))
    Box(
        if (onClick != null) base.then(pressScale(onClick, 0.9f)) else base,
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            androidx.compose.foundation.Image(
                bitmap = photo,
                contentDescription = "Profile photo",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            TfText(letter, color = Color.White, size = size * 0.41f, weight = FontWeight.SemiBold)
        }
    }
}

// ── iOS chrome ───────────────────────────────────────────────────────────────
@Composable
fun TStatusBar(time: String = "9:41") {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().height(54.dp).padding(start = 32.dp, end = 32.dp, top = 21.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        TfText(time, color = c.fg, size = 16f, weight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // signal bars
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(4, 6, 8, 11).forEach { h ->
                    Box(Modifier.size(width = 3.dp, height = h.dp).clip(RoundedCornerShape(1.dp)).background(c.fg))
                }
            }
            // wifi (drawn — no bundled glyph)
            androidx.compose.foundation.Canvas(Modifier.size(15.dp)) {
                val cx = size.width / 2f
                for (i in 0..2) {
                    val rad = size.width * (0.22f + i * 0.16f)
                    drawArc(
                        color = c.fg, startAngle = 220f, sweepAngle = 100f, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(cx - rad, size.height * 0.78f - rad),
                        size = androidx.compose.ui.geometry.Size(rad * 2, rad * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()),
                    )
                }
                drawCircle(c.fg, 1.1.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, size.height * 0.78f))
            }
            // battery
            Box(
                Modifier.size(width = 24.dp, height = 12.dp).clip(RoundedCornerShape(3.dp))
                    .border(1.dp, c.fg.copy(alpha = 0.4f), RoundedCornerShape(3.dp)).padding(2.dp),
            ) { Box(Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)).background(c.fg)) }
        }
    }
}

@Composable
fun Notch() {
    Box(
        Modifier.padding(top = 11.dp).size(width = 124.dp, height = 36.dp)
            .clip(RoundedCornerShape(24.dp)).background(Color.Black),
    )
}

@Composable
fun HomeIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier.padding(bottom = 8.dp).size(width = 134.dp, height = 5.dp)
            .clip(RoundedCornerShape(100.dp)).background(Color.White.copy(alpha = 0.32f)),
    )
}

// ── Tab bar ──────────────────────────────────────────────────────────────────
val TABS = listOf(
    Triple("today", "sparkles", "Today"),
    Triple("learn", "bookOpen", "Learn"),
    Triple("do", "checkSquare", "Do"),
    Triple("thoughts", "messageCircle", "Thoughts"),
    Triple("more", "layoutGrid", "More"),
)

@Composable
fun TTabBar(active: String, onChange: (String) -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().background(c.glass).drawTopHairline(c.sep)
            .padding(top = 9.dp, bottom = 22.dp),
    ) {
        TABS.forEach { (k, ic, label) ->
            val on = active == k
            val indScale by animateFloatAsState(if (on) 1f else 0.55f, tween(200))
            Column(
                Modifier.weight(1f).then(pressScale({ onChange(k) }, 0.92f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                    if (on) {
                        Box(
                            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 1.dp)
                                .scale(indScale)
                                .clip(RoundedCornerShape(11.dp)).background(c.accent.soft(0.18f)),
                        )
                    }
                    TfIcon(ic, size = 22, color = if (on) c.accent else c.fg3)
                }
                TfText(label, color = if (on) c.accent else c.fg3, size = 10f, weight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

// ── Companion FAB ────────────────────────────────────────────────────────────
@Composable
fun BoxScope.CompanionFab(onClick: () -> Unit, raised: Boolean) {
    val c = LocalTf.current
    val bottom by animateDpAsState(if (raised) 92.dp else 30.dp, tween(220))
    Box(
        Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = bottom)
            .size(58.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(c.accent, c.purple)))
            .then(pressScale(onClick, 0.9f)),
        contentAlignment = Alignment.Center,
    ) { TfIcon("sparkles", size = 26, color = Color.White) }
}

// ── Chat ─────────────────────────────────────────────────────────────────────
@Composable
fun ChatBubble(m: ChatMsg) {
    val c = LocalTf.current
    val me = m.from == "me"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (me) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.fillMaxWidth(0.82f).wrapContentWidth(if (me) Alignment.End else Alignment.Start)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomEnd = if (me) 5.dp else 16.dp, bottomStart = if (me) 16.dp else 5.dp,
                    )
                )
                .background(if (me) c.accent else c.card)
                .then(if (me) Modifier else Modifier.border(0.5.dp, c.sep, RoundedCornerShape(16.dp)))
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) { TfText(m.text, color = if (me) Color.White else c.fg, size = 14f, lineHeight = 1.5f) }
    }
}

@Composable
fun ChatInput(draft: String, onDraft: (String) -> Unit, onSend: () -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().drawTopHairline(c.sep).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).background(c.card)
                .border(0.5.dp, c.line, RoundedCornerShape(999.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) TfText("Ask Murabbiy…", color = c.fg3, size = 15f)
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    singleLine = true,
                    textStyle = TextStyle(color = c.fg, fontSize = 15.sp, fontFamily = Fonts.sans),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TfIcon("mic", size = 18, color = c.fg3)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.accent).then(pressScale(onSend, 0.9f)),
            contentAlignment = Alignment.Center,
        ) { TfIcon("send", size = 18, color = Color.White) }
    }
}

// ── Suggestion chips row ─────────────────────────────────────────────────────
@Composable
fun SuggestRow(onPick: (String) -> Unit) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TCHAT_SUGGEST.forEach { s ->
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(c.card2)
                    .border(0.5.dp, c.sep, RoundedCornerShape(999.dp))
                    .then(pressScale({ onPick(s) }, 0.95f)).padding(horizontal = 12.dp, vertical = 7.dp),
            ) { TfText(s, color = c.fg2, size = 12.5f) }
        }
    }
}

/** Shared chat state + send logic (companion + coach). */
@Stable
class ChatState {
    var messages by mutableStateOf(TCHAT)
        private set
    var draft by mutableStateOf("")

    suspend fun send(text: String? = null, scrollToEnd: suspend () -> Unit = {}) {
        val t = (text ?: draft).trim()
        if (t.isEmpty()) return
        messages = messages + ChatMsg("me", t, "now")
        draft = ""
        scrollToEnd()
        delay(650)
        messages = messages + ChatMsg("ai", aiReply(t), "now")
        scrollToEnd()
    }
}

// ── Companion bottom sheet ───────────────────────────────────────────────────
@Composable
fun BoxScope.CompanionSheet(open: Boolean, onClose: () -> Unit, onFull: () -> Unit) {
    val c = LocalTf.current
    val scope = rememberCoroutineScope()
    val chat = remember { ChatState() }
    val listState = rememberLazyListState()
    val scrim by animateFloatAsState(if (open) 1f else 0f, tween(280))
    val offsetFrac by animateFloatAsState(if (open) 0f else 1f, tween(340))

    LaunchedEffect(open, chat.messages.size) {
        if (open && chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.size - 1)
    }
    if (scrim <= 0f && !open) return

    Box(
        Modifier.matchParentSize().background(Color(0xFF07090D).copy(alpha = 0.55f * scrim))
            .then(pressScale(onClose, 1f)),
    )
    BoxWithConstraints(Modifier.align(Alignment.BottomCenter)) {
        val sheetH = maxHeight * 0.74f
        Column(
            Modifier.fillMaxWidth().height(sheetH)
                .offset(y = sheetH * offsetFrac)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(c.bg1).drawTopHairline(c.line),
        ) {
            Row(
                Modifier.fillMaxWidth().drawBottomHairline(c.sep).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.accent, c.purple))),
                    contentAlignment = Alignment.Center,
                ) { TfIcon("sparkles", size = 19, color = Color.White) }
                Column(Modifier.weight(1f)) {
                    TfText("Murabbiy", color = c.fg, size = 16f, weight = FontWeight.SemiBold, family = Fonts.display)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c.green))
                        TfText("On-device · private", color = c.green, size = 12f)
                    }
                }
                Box(Modifier.then(pressScale(onFull, 0.9f)).padding(6.dp)) { TfIcon("arrowUpRight", size = 20, color = c.accent) }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(c.card2).then(pressScale(onClose, 0.9f)),
                    contentAlignment = Alignment.Center,
                ) { TfIcon("x", size = 16, color = c.fg2) }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { items(chat.messages) { ChatBubble(it) } }
            SuggestRow { s -> scope.launch { chat.send(s) { if (chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.size - 1) } } }
            ChatInput(chat.draft, { chat.draft = it }, { scope.launch { chat.send() { if (chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.size - 1) } } })
        }
    }
}

// ── Hub launcher (hub nav mode) ──────────────────────────────────────────────
private data class HubModule(val k: String, val name: String, val icon: String, val color: String, val sub: String)

private val HUB_MODULES = listOf(
    HubModule("today", "Today", "sparkles", "teal", "Your AI brief"),
    HubModule("coach", "Coach", "brain", "purple", "Murabbiy"),
    HubModule("learn", "Learn", "bookOpen", "cyan", "4 subjects"),
    HubModule("do", "Productivity", "checkSquare", "teal", "Tasks · focus"),
    HubModule("thoughts", "Thoughts", "messageCircle", "rose", "From friends"),
    HubModule("career", "Career", "briefcase", "green", "CV · interviews"),
    HubModule("finance", "Finance", "wallet", "gold", "Portfolio"),
)

@Composable
fun HubLauncher(onOpen: (String) -> Unit) {
    val c = LocalTf.current
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HUB_MODULES.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { m ->
                    val col = c.named(m.color)
                    Column(
                        Modifier.weight(1f).heightIn(min = 118.dp).clip(RoundedCornerShape(18.dp))
                            .background(c.card).border(0.5.dp, c.sep, RoundedCornerShape(18.dp))
                            .then(pressScale({ onOpen(m.k) }, 0.97f)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(col.soft()),
                            contentAlignment = Alignment.Center,
                        ) { TfIcon(m.icon, size = 24, color = col) }
                        Column {
                            TfText(m.name, color = c.fg, size = 17f, weight = FontWeight.SemiBold, family = Fonts.display)
                            TfText(m.sub, color = c.fg2, size = 12.5f, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Ambient glow positioned at top of a screen ───────────────────────────────
@Composable
fun BoxScope.TopGlow(color: Color, opacity: Float = 0.16f) {
    Box(
        Modifier.align(Alignment.TopCenter).offset(y = (-40).dp).size(width = 460.dp, height = 360.dp)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = opacity), Color.Transparent),
                        radius = size.minDimension / 2f,
                    ),
                    radius = size.minDimension / 2f,
                )
            },
    )
}
