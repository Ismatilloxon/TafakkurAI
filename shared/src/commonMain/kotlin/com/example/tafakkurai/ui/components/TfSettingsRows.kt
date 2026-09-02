package com.example.tafakkurai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.ui.IconBadge
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.TfIcon
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.pressScale

/** Shared accessible switch used by Settings and feature preference screens. */
@Composable
fun TfSettingsToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $detail" }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TfText(title, color = LocalTf.current.fg, size = 14.5f, weight = FontWeight.Medium)
            TfText(detail, color = LocalTf.current.fg3, size = 11.5f, lineHeight = 1.4f)
        }
        TfSwitchIndicator(checked = checked)
    }
}

/** Visual part only; the owning row provides the switch semantics and interaction. */
@Composable
private fun TfSwitchIndicator(checked: Boolean) {
    val c = LocalTf.current
    val shift by animateFloatAsState(if (checked) 18f else 0f, tween(180), label = "settings-switch")
    Box(
        Modifier.size(width = 46.dp, height = 28.dp)
            .background(if (checked) c.accent else c.card3, RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = 3.dp).offset(x = shift.dp).size(22.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** Shared navigation/action row for settings-like lists. */
@Composable
fun TfSettingsLinkRow(
    icon: String,
    title: String,
    detail: String? = null,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val c = LocalTf.current
    val tint = if (destructive) c.danger else c.accent
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(pressScale(onClick, 0.985f))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = listOfNotNull(title, detail, value).joinToString(". ")
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, tint, size = 38, iconSize = 18)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TfText(title, color = if (destructive) c.danger else c.fg, size = 14.5f, weight = FontWeight.Medium)
            detail?.let { TfText(it, color = c.fg3, size = 11.5f, lineHeight = 1.35f) }
        }
        value?.let { TfText(it, color = c.fg2, size = 12.5f) }
        if (!destructive) TfIcon("chevronRight", size = 17, color = c.fg3)
    }
}

/** Shared radio-like choice row. */
@Composable
fun TfSettingsChoiceRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalTf.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
            .then(pressScale(onClick, 0.985f))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title. $detail"
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            TfText(title, color = if (selected) c.fg else c.fg2, size = 14.5f, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            TfText(detail, color = c.fg3, size = 11.5f, modifier = Modifier.padding(top = 1.dp))
        }
        if (selected) TfIcon("checkCircle", size = 18, color = c.green)
    }
}
