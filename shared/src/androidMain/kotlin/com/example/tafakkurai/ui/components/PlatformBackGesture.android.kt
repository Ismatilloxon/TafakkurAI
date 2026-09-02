package com.example.tafakkurai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android keeps system predictive-back ownership; this modifier is intentionally inert. */
@Composable
actual fun Modifier.platformEdgeBackGesture(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier = this
