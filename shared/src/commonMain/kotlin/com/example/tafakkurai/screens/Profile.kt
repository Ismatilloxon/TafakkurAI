package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.*
import com.example.tafakkurai.onboarding.Spinner
import com.example.tafakkurai.onboarding.UzPhoneTransformation
import com.example.tafakkurai.onboarding.onAccent
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.vm.ContactStep
import com.example.tafakkurai.vm.ProfileViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.mp.KoinPlatform.getKoin
import com.example.tafakkurai.i18n.Language
import com.example.tafakkurai.i18n.LanguageStore
import com.example.tafakkurai.i18n.tr
import androidx.compose.runtime.remember
import com.example.tafakkurai.ui.components.TfErrorState
import com.example.tafakkurai.ui.components.TfLoadingState

/** Profile page — opened from the Today/Hub header avatar. */
@Composable
fun ProfileScreen(sessionKey: Int, onBack: () -> Unit) {
    val c = LocalTf.current
    val vm: ProfileViewModel = viewModel(key = "profile:$sessionKey") {
        ProfileViewModel(getKoin().get(), getKoin().get(), getKoin().get(), getKoin().get(), getKoin().get())
    }
    val languages = remember { getKoin().get<LanguageStore>() }
    val pickImage = rememberImagePicker { bytes ->
        if (bytes != null) vm.selectPhoto(bytes)
    }
    val openImagePicker = { if (!vm.photoUploading) pickImage() }
    Column(Modifier.fillMaxSize()) {
        BackBar(tr("profile.title"), onBack)
        when {
            vm.loading && vm.user == null -> TfLoadingState(tr("profile.loading"), Modifier.weight(1f))
            vm.errorKey != null && vm.user == null -> TfErrorState(
                message = tr(vm.errorKey!!),
                retryLabel = tr("common.retry"),
                onRetry = vm::load,
                modifier = Modifier.weight(1f),
            )
            else -> ScrollScreen {
            vm.errorKey?.let { errorKey ->
                Card {
                    TfText(tr(errorKey), color = c.danger, size = 13f, lineHeight = 1.4f)
                }
            }
            // Header: big avatar + name + handle
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UserAvatar(
                    size = 88,
                    letter = (vm.user?.displayName ?: "?").take(1).uppercase(),
                    onClick = openImagePicker.takeUnless { vm.photoUploading },
                )
                TfText(
                    tr(if (vm.photoUploading) "common.loading" else "profile.change_photo"),
                    color = if (vm.photoUploading) c.fg3 else c.accent,
                    size = 13f,
                    weight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp).then(
                        if (vm.photoUploading) Modifier else pressScale(openImagePicker, 0.96f),
                    ),
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
                    Stat(tr("profile.xp"), "${vm.gamification?.xp ?: 0}/${vm.gamification?.xpNext ?: 0}")
                }
            }

            // The one thing sign-in could not supply. Google and Apple hand over an
            // address but never a number; a phone or Telegram sign-in is the reverse.
            // Which is missing comes from the server, so the card disappears on its own
            // once the account is complete.
            vm.user?.let { u ->
                if (u.needsPhone) ContactCard(vm, asking = ContactAsk.PHONE)
                else if (u.needsEmail) ContactCard(vm, asking = ContactAsk.EMAIL)
            }

            // Info rows
            Card {
                InfoRow(tr("profile.coach"), tr("profile.coach.name"))
                InfoRow(tr("profile.city"), vm.user?.city ?: "")
                vm.user?.phone?.let { InfoRow(tr("profile.phone"), it) }
                vm.user?.email?.let { InfoRow(tr("profile.email"), it) }
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
                                    tr(language.detailKey), color = c.fg3, size = 11.5f,
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
}

/** Which half of the contact pair the account is missing. */
enum class ContactAsk { PHONE, EMAIL }

/**
 * Asks for the one contact detail sign-in could not supply, and nothing else.
 *
 * The phone half is two steps because a number is only worth storing once an SMS has
 * proved it: the server sends a code to it and attaches it on the way back. The email
 * half is one step — there is no mail sender wired up, so the address is contact detail
 * rather than a verified identity, and pretending otherwise would be theatre.
 */
@Composable
private fun ContactCard(vm: ProfileViewModel, asking: ContactAsk) {
    val c = LocalTf.current
    var digits by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Card {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline(tr("profile.contact.overline"), color = c.accent)
            TfText(
                tr(if (asking == ContactAsk.PHONE) "profile.contact.phone_title" else "profile.contact.email_title"),
                color = c.fg, size = 15f, weight = FontWeight.SemiBold, family = Fonts.display,
            )
            TfText(
                tr(
                    when {
                        asking == ContactAsk.EMAIL -> "profile.contact.email_hint"
                        vm.contactStep == ContactStep.CODE_SENT -> "profile.contact.code_hint"
                        else -> "profile.contact.phone_hint"
                    },
                ),
                color = c.fg3, size = 12f, lineHeight = 1.45f,
            )

            when {
                asking == ContactAsk.EMAIL -> {
                    ContactField(
                        value = email,
                        onValue = { email = it.trim() },
                        placeholder = tr("profile.contact.email_placeholder"),
                        keyboard = KeyboardType.Email,
                        mono = false,
                    )
                    ContactButton(tr("profile.contact.save"), enabled = email.contains('@'), loading = vm.contactBusy) {
                        vm.saveEmail(email)
                    }
                }

                vm.contactStep == ContactStep.CODE_SENT -> {
                    ContactField(
                        value = code,
                        onValue = { code = it.filter { ch -> ch.isDigit() }.take(6) },
                        placeholder = "······",
                        keyboard = KeyboardType.Number,
                        mono = true,
                    )
                    ContactButton(tr("profile.contact.confirm"), enabled = code.length == 6, loading = vm.contactBusy) {
                        vm.verifyPhoneCode(code)
                    }
                    TfText(
                        tr("profile.contact.change_number"), color = c.fg3, size = 12f,
                        modifier = Modifier.padding(top = 2.dp).then(pressScale({ code = ""; vm.resetContact() }, 0.97f)),
                    )
                }

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(c.card3).padding(horizontal = 10.dp, vertical = 12.dp)) {
                            TfText("+998", color = c.fg, size = 15f, weight = FontWeight.SemiBold, family = Fonts.mono)
                        }
                        Box(Modifier.weight(1f)) {
                            ContactField(
                                value = digits,
                                onValue = { digits = it.filter { ch -> ch.isDigit() }.take(9) },
                                placeholder = "90 123 45 67",
                                keyboard = KeyboardType.Number,
                                mono = true,
                                transformation = UzPhoneTransformation,
                            )
                        }
                    }
                    ContactButton(tr("profile.contact.send_code"), enabled = digits.length == 9, loading = vm.contactBusy) {
                        vm.sendPhoneCode("+998$digits")
                    }
                }
            }

            vm.contactError?.let { key ->
                // Use-case errors arrive already localised by NetworkExecutor; the card's
                // own validation passes a key, so try the catalogue and fall back to the text.
                TfText(tr(key).takeIf { it != key } ?: key, color = c.danger, size = 12f)
            }
        }
    }
}

@Composable
private fun ContactField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType,
    mono: Boolean,
    transformation: VisualTransformation = VisualTransformation.None,
) {
    val c = LocalTf.current
    val family = if (mono) Fonts.mono else Fonts.sans
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card3)
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) TfText(placeholder, color = c.fg3, size = 15f, family = family)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = c.fg, fontSize = 15.sp, fontFamily = family),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = transformation,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ContactButton(label: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val c = LocalTf.current
    val on = enabled && !loading
    Box(
        Modifier.fillMaxWidth()
            .then(if (on) pressScale(onClick, 0.96f) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) c.accent else c.card3)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) Spinner(onAccent(c.accent), size = 16)
        else TfText(label, color = if (on) onAccent(c.accent) else c.fg3, size = 14.5f, weight = FontWeight.SemiBold)
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
