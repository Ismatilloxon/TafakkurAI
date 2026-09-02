package com.example.tafakkurai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.tafakkurai.ui.Card
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.TfText

/** Reusable confirmation surface for destructive actions. */
@Composable
fun TfConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    destructive: Boolean = false,
) {
    val c = LocalTf.current
    Dialog(onDismissRequest = onCancel) {
        Card(Modifier.fillMaxWidth().widthIn(max = 360.dp), pad = 22, radius = 20) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TfText(
                    title,
                    color = c.fg,
                    size = 20f,
                    weight = FontWeight.Bold,
                    family = Fonts.display,
                    align = TextAlign.Center,
                )
                TfText(
                    message,
                    color = c.fg2,
                    size = 13.5f,
                    lineHeight = 1.5f,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TfSecondaryButton(cancelLabel, onCancel, Modifier.weight(1f))
                    TfPrimaryButton(
                        label = confirmLabel,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        destructive = destructive,
                    )
                }
            }
        }
    }
}
