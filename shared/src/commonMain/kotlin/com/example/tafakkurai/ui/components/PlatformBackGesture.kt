package com.example.tafakkurai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Platform behavior for a left-edge hierarchical back gesture. */
@Composable
expect fun Modifier.platformEdgeBackGesture(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier

/**
 * Routes the app-level edge gesture to the deepest visible Compose-owned back stack.
 *
 * Most destinations live directly in App.kt's stack. Gratitude and Habits deliberately own
 * nested stacks, though, so letting the shell always pop itself would skip their detail/home
 * relationship. The visible nested owner registers while it has something local to pop; the
 * shell remains the fallback.
 */
@Stable
class PlatformBackDispatcher {
    private var fallback: () -> Unit = {}
    private var nestedOwner: Any? = null
    private var nestedHandler: (() -> Unit)? = null

    fun updateFallback(onBack: () -> Unit) {
        fallback = onBack
    }

    fun registerNested(owner: Any, onBack: () -> Unit) {
        nestedOwner = owner
        nestedHandler = onBack
    }

    fun unregisterNested(owner: Any) {
        if (nestedOwner === owner) {
            nestedOwner = null
            nestedHandler = null
        }
    }

    fun goBack() {
        (nestedHandler ?: fallback).invoke()
    }
}

val LocalPlatformBackDispatcher = staticCompositionLocalOf<PlatformBackDispatcher?> { null }

/** Registers a local route pop without installing a second competing edge recognizer. */
@Composable
fun PlatformNestedBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val dispatcher = LocalPlatformBackDispatcher.current
    val latestOnBack by rememberUpdatedState(onBack)
    val owner = remember { Any() }

    DisposableEffect(dispatcher, enabled) {
        if (enabled) dispatcher?.registerNested(owner) { latestOnBack() }
        onDispose { dispatcher?.unregisterNested(owner) }
    }
}
