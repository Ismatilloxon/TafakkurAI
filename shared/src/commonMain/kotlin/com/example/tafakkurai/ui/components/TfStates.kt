package com.example.tafakkurai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.ui.BreathOrb
import com.example.tafakkurai.ui.IconBadge
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.TfText

/** Shared full-screen loading state. The feature supplies localized copy. */
@Composable
fun TfLoadingState(message: String, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BreathOrb(size = 64, color = c.accent, purple = c.purple)
        TfText(message, color = c.fg2, size = 14f, modifier = Modifier.padding(top = 18.dp))
    }
}

/** Shared recoverable error state with explicit localized retry wording. */
@Composable
fun TfErrorState(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val c = LocalTf.current
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge("bookOpen", c.gold, size = 56, iconSize = 26)
        TfText(
            message,
            color = c.fg,
            size = 16f,
            align = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (!detail.isNullOrBlank()) {
            TfText(
                detail,
                color = c.fg3,
                size = 13f,
                align = TextAlign.Center,
                lineHeight = 1.45f,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        TfPrimaryButton(
            label = retryLabel,
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        )
    }
}

/** Shared empty state for libraries, searches and histories. */
@Composable
fun TfEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: String = "bookOpen",
) {
    val c = LocalTf.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconBadge(icon, c.fg3, size = 48, iconSize = 22)
        TfText(title, color = c.fg, size = 15f, align = TextAlign.Center)
        if (!detail.isNullOrBlank()) {
            TfText(detail, color = c.fg3, size = 13f, align = TextAlign.Center, lineHeight = 1.5f)
        }
    }
}
