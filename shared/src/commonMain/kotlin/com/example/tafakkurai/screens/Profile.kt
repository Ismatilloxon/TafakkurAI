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
import com.example.tafakkurai.i18n.Language
import com.example.tafakkurai.i18n.LanguageStore
import com.example.tafakkurai.i18n.tr
import androidx.compose.runtime.remember

/** Profile page — opened from the Today/Hub header avatar. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    val vm: ProfileViewModel = viewModel { ProfileViewModel(getKoin().get(), getKoin().get()) }
    val languages = remember { getKoin().get<LanguageStore>() }
    val pickImage = rememberImagePicker { bytes ->
        val bmp = bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
        if (bmp != null) {
            ProfileImage.set(bytes, bmp)
            bytes?.let { vm.uploadPhoto(it) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("profile.title"), onBack)
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
                    tr("profile.change_photo"), color = c.accent, size = 13f, weight = FontWeight.SemiBold,
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
                    Stat(tr("profile.streak"), "${vm.gamification?.streak ?: 0}")
                    Stat(tr("profile.level"), "${vm.gamification?.level ?: 0}")
                    Stat("XP", "${vm.gamification?.xp ?: 0}/${vm.gamification?.xpNext ?: 0}")
                }
            }

            // Info rows
            Card {
                InfoRow(tr("profile.coach"), tr("profile.coach.name"))
                InfoRow(tr("profile.city"), vm.user?.city ?: "")
            }

            // Interface language — the same choice made on first launch, changeable here.
            // Tapping a row switches the whole app immediately; there is nothing to save.
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Overline(tr("lang.overline"))
                    TfText(
                        tr("profile.language"), color = c.fg, size = 15f,
                        weight = FontWeight.SemiBold, family = Fonts.display,
                    )
                    TfText(tr("profile.language_hint"), color = c.fg3, size = 12f)
                    Language.entries.forEach { language ->
                        val on = language == languages.current
                        Row(
                            Modifier.fillMaxWidth()
                                .then(pressScale({ languages.choose(language) }, 0.985f))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                TfText(
                                    language.nativeName,
                                    color = if (on) c.fg else c.fg2,
                                    size = 14.5f,
                                    weight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                TfText(
                                    language.englishName, color = c.fg3, size = 11.5f,
                                    modifier = Modifier.padding(top = 1.dp),
                                )
                            }
                            if (on) TfIcon("checkCircle", size = 18, color = c.green)
                        }
                    }
                }
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
