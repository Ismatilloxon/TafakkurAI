package com.example.tafakkurai.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.vm.ProfileViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.mp.KoinPlatform.getKoin

/** Profile page — opened from the Today/Hub header avatar. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    val vm: ProfileViewModel = viewModel { ProfileViewModel(getKoin().get(), getKoin().get()) }
    val pickImage = rememberImagePicker { bytes ->
        val bmp = bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
        if (bmp != null) {
            ProfileImage.set(bytes, bmp)
            bytes?.let { vm.uploadPhoto(it) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        BackBar("Profil", onBack)
        ScrollScreen {
            // Header: big avatar + name + handle
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(
                    size = 88,
                    letter = (vm.user?.displayName ?: "?").take(1).uppercase(),
                    onClick = pickImage,
                )
                TfText(
                    "Change photo", color = c.accent, size = 13f, weight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp).then(pressScale(pickImage, 0.96f)),
                )
                TfText(
                    vm.user?.displayName ?: "", color = c.fg, size = 22f, weight = FontWeight.Bold,
                    family = Fonts.display, modifier = Modifier.padding(top = 12.dp),
                )
                TfText("${vm.user?.handle ?: ""} · ${vm.user?.city ?: ""}", color = c.fg2, size = 13f,
                    modifier = Modifier.padding(top = 2.dp))
            }

            // Stats row
            Card {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Stat("Streak", "${vm.gamification?.streak ?: 0}")
                    Stat("Level", "${vm.gamification?.level ?: 0}")
                    Stat("XP", "${vm.gamification?.xp ?: 0}/${vm.gamification?.xpNext ?: 0}")
                }
            }

            // Info rows
            Card {
                InfoRow("Coach", "Murabbiy")
                InfoRow("Language", vm.user?.languageTag ?: "")
                InfoRow("City", vm.user?.city ?: "")
            }
        }
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String) {
    val c = LocalTf.current
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        TfText(value, color = c.fg, size = 20f, weight = FontWeight.Bold, family = Fonts.display)
        TfText(label, color = c.fg3, size = 12f, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TfText(key, color = c.fg2, size = 14f)
        TfText(value, color = c.fg, size = 14f, weight = FontWeight.SemiBold)
    }
}
