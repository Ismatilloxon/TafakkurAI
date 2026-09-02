package com.example.tafakkurai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.Tweaks
import com.example.tafakkurai.i18n.Language
import com.example.tafakkurai.i18n.LanguageStore
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.settings.domain.SettingsSyncStatus
import com.example.tafakkurai.settings.presentation.SettingsViewModel
import com.example.tafakkurai.ui.BackBar
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.Overline
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.components.TfConfirmationDialog
import com.example.tafakkurai.ui.components.TfPrimaryButton
import com.example.tafakkurai.ui.components.TfSecondaryButton
import com.example.tafakkurai.ui.components.TfSettingsChoiceRow
import com.example.tafakkurai.ui.components.TfSettingsLinkRow
import com.example.tafakkurai.ui.components.TfSettingsToggleRow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    languages: LanguageStore,
    tweaks: Tweaks,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val c = LocalTf.current
    val state = viewModel.state
    val sync by viewModel.syncStatus.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("settings.title"), onBack)
        ScrollBody {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TfText(
                    tr("settings.heading"),
                    color = c.fg,
                    size = 26f,
                    weight = FontWeight.Bold,
                    family = Fonts.display,
                )
                TfText(tr("settings.subtitle"), color = c.fg2, size = 13.5f, lineHeight = 1.5f)
            }

            InsetCard {
                Overline(tr("settings.ai.section"), c.accent)
                TfSettingsToggleRow(
                    title = tr("settings.ai.cloud_title"),
                    detail = tr("settings.ai.cloud_detail"),
                    checked = state.cloudConversationEnabled,
                    onCheckedChange = viewModel::setCloudConversationEnabled,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TfText(
                    tr("settings.ai.local_note"),
                    color = c.fg3,
                    size = 11.5f,
                    lineHeight = 1.45f,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            InsetCard {
                Overline(tr("settings.interface.section"))
                TfSettingsLinkRow(
                    icon = "sliders",
                    title = tr("settings.appearance"),
                    detail = tr("settings.appearance_detail"),
                    value = tr(if (tweaks.mode == "light") "appearance.light" else "appearance.dark"),
                    onClick = onOpenAppearance,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TfText(
                    tr("settings.language"),
                    color = c.fg,
                    size = 14.5f,
                    weight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                Language.entries.forEach { language ->
                    TfSettingsChoiceRow(
                        title = language.nativeName,
                        detail = tr(language.detailKey),
                        selected = language == languages.current,
                        onClick = { languages.choose(language) },
                    )
                }
            }

            InsetCard {
                Overline(tr("settings.sync.section"), c.teal)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        TfText(tr("settings.sync.title"), color = c.fg, size = 14.5f, weight = FontWeight.Medium)
                        TfText(syncStatusText(sync), color = syncStatusColor(sync), size = 11.5f, lineHeight = 1.4f)
                    }
                    if (sync is SettingsSyncStatus.Syncing) {
                        TfPrimaryButton(
                            label = tr("settings.sync.syncing"),
                            onClick = viewModel::syncNow,
                            enabled = false,
                        )
                    } else {
                        TfSecondaryButton(
                            label = tr("settings.sync.now"),
                            onClick = viewModel::syncNow,
                        )
                    }
                }
                TfText(
                    tr("settings.sync.detail"),
                    color = c.fg3,
                    size = 11.5f,
                    lineHeight = 1.45f,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            if (state.signOutFailed) {
                InsetCard {
                    TfText(tr("settings.signout.failed"), color = c.danger, size = 13f, lineHeight = 1.45f)
                    TfSecondaryButton(
                        label = tr("common.close"),
                        onClick = viewModel::dismissSignOutError,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }

            InsetCard {
                Overline(tr("settings.account.section"))
                if (state.signingOut) {
                    TfPrimaryButton(
                        label = tr("settings.signout.progress"),
                        onClick = { viewModel.signOut(onLoggedOut) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = false,
                        destructive = true,
                    )
                } else {
                    TfSettingsLinkRow(
                        icon = "user",
                        title = tr("settings.signout.title"),
                        detail = tr("settings.signout.detail"),
                        onClick = { confirmSignOut = true },
                        modifier = Modifier.padding(top = 4.dp),
                        destructive = true,
                    )
                }
            }

            PrivacyFooter(tr("settings.privacy.footer"))
        }
    }

    if (confirmSignOut) {
        TfConfirmationDialog(
            title = tr("settings.signout.confirm_title"),
            message = tr("settings.signout.confirm_message"),
            confirmLabel = tr("settings.signout.confirm"),
            cancelLabel = tr("common.cancel"),
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut(onLoggedOut)
            },
            onCancel = { confirmSignOut = false },
            destructive = true,
        )
    }
}

@Composable
private fun syncStatusText(status: SettingsSyncStatus): String {
    val strings = LocalStrings.current
    return when (status) {
        is SettingsSyncStatus.Idle -> if (status.pending == 0L) {
            strings["settings.sync.ready"]
        } else {
            strings.format("settings.sync.pending", status.pending)
        }
        is SettingsSyncStatus.Syncing -> strings.format("settings.sync.in_progress", status.pending)
        is SettingsSyncStatus.Synced -> if (status.pending == 0L) {
            strings.format("settings.sync.complete", status.pushed, status.pulled)
        } else {
            strings.format("settings.sync.complete_pending", status.pushed, status.pulled, status.pending)
        }
        is SettingsSyncStatus.Failed -> strings.format("settings.sync.failed", status.pending)
        SettingsSyncStatus.SignedOut -> strings["settings.sync.signed_out"]
    }
}

@Composable
private fun syncStatusColor(status: SettingsSyncStatus) = when (status) {
    is SettingsSyncStatus.Failed -> LocalTf.current.warn
    is SettingsSyncStatus.Synced -> LocalTf.current.green
    else -> LocalTf.current.fg3
}
