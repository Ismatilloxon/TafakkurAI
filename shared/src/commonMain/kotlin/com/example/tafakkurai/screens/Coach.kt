package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.vm.CoachViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.mp.KoinPlatform.getKoin
import kotlinx.coroutines.launch

@Composable
fun CoachScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    var tab by remember { mutableStateOf("chat") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Murabbiy", onBack)
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            BreathOrb(size = 52, color = c.accent, purple = c.purple)
            Column(Modifier.weight(1f)) {
                TfText("Murabbiy", color = c.fg, size = 19f, weight = FontWeight.Bold, family = Fonts.display)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(c.green))
                    TfText("On-device · your data stays private", color = c.green, size = 12f)
                }
            }
        }
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
            Segmented(listOf("chat" to "Chat", "memory" to "What I remember"), tab, { tab = it })
        }
        if (tab == "chat") ChatTab() else MemoryTab()
    }
}

@Composable
private fun ColumnScope.ChatTab() {
    val vm: CoachViewModel = viewModel { CoachViewModel(getKoin().get()) }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()?.text) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = HPAD.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { items(vm.messages) { ChatBubble(it) } }
    SuggestRow { s -> vm.send(s) }
    ChatInput(vm.draft, { vm.draft = it }, { vm.send() })
}

@Composable
private fun ColumnScope.MemoryTab() {
    val c = LocalTf.current
    ScrollBody {
        Box(Modifier.padding(horizontal = 20.dp)) {
            TfText("Here's what I keep about you. Edit or forget anything — it only lives on this device.", color = c.fg2, size = 14f, lineHeight = 1.55f)
        }
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TMEMORY.forEach { m ->
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.accent.soft(0.14f)), contentAlignment = Alignment.Center) {
                            TfIcon(m.icon, size = 18, color = c.accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Overline(m.label)
                            TfText(m.text, color = c.fg, size = 14.5f, modifier = Modifier.padding(top = 3.dp))
                        }
                        Box(Modifier.then(pressScale({}, 0.9f)).padding(4.dp)) { TfIcon("x", size = 16, color = c.fg3) }
                    }
                }
            }
        }
        PrivacyFooter("Memory is summarized and decayed nightly, on-device. Never uploaded.")
    }
}
