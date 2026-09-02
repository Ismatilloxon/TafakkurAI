package com.example.tafakkurai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.TfIcon
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.pressScale

/** Shared accessible primary action. Labels are supplied by the localized caller. */
@Composable
fun TfPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: String? = null,
    destructive: Boolean = false,
) {
    val c = LocalTf.current
    val fill = if (destructive) c.danger else c.accent
    val interaction = if (enabled) Modifier.then(pressScale(onClick, 0.97f)) else Modifier
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) fill else c.fg4)
            .then(interaction)
            .semantics {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            TfIcon(icon, size = 16, color = Color.White, modifier = Modifier.padding(end = 7.dp))
        }
        TfText(label, color = Color.White, size = 15f, weight = FontWeight.SemiBold)
    }
}

/** Shared low-emphasis action used for cancel/edit affordances. */
@Composable
fun TfSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    Box(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.card3)
            .border(0.5.dp, c.line, RoundedCornerShape(12.dp))
            .then(pressScale(onClick, 0.97f))
            .semantics { role = Role.Button; contentDescription = label }
            .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        TfText(label, color = c.fg2, size = 14f, weight = FontWeight.SemiBold)
    }
}

/** Shared full-width add/create affordance. */
@Composable
fun TfAddButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .then(pressScale(onClick, 0.98f))
            .semantics { role = Role.Button; contentDescription = label }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TfIcon("plus", size = 16, color = c.fg2, modifier = Modifier.padding(end = 7.dp))
        TfText(label, color = c.fg2, size = 14f, weight = FontWeight.Medium)
    }
}

/** 44pt circular icon action for edit/delete/clear controls. */
@Composable
fun TfIconButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val c = LocalTf.current
    val tint = if (destructive) c.danger else c.fg2
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (destructive) c.danger.copy(alpha = 0.12f) else c.card3)
            .then(pressScale(onClick, 0.9f))
            .semantics { role = Role.Button; this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        TfIcon(icon, size = 17, color = tint)
    }
}
