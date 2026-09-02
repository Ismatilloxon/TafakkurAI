package com.example.tafakkurai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.pressScale

/** Shared full-width action with a 48dp target and explicit disabled semantics. */
@Composable
fun TfActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalTf.current
    val interaction = if (enabled) Modifier.then(pressScale(onClick)) else Modifier.semantics { disabled() }
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) c.accent else c.card3)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        TfText(
            text = label,
            color = if (enabled) Color.White else c.fg3,
            size = 14.5f,
            weight = FontWeight.SemiBold,
        )
    }
}
