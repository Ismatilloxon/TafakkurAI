package com.example.tafakkurai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compose-owned routes cannot participate in UINavigationController's interactive pop, so
 * preserve the familiar iOS left-edge gesture in the shared host. The current route follows
 * the finger, completes from distance or forward velocity, and settles back when cancelled.
 *
 * It is important to inspect the initial down position ourselves. Compose's high-level drag
 * detector reports `onDragStart` only after touch slop has been crossed; by then a legitimate
 * fast edge swipe can already be outside a narrow edge band and used to be rejected.
 */
@Composable
actual fun Modifier.platformEdgeBackGesture(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 32.dp.toPx() }
    val minimumTravelPx = with(density) { 28.dp.toPx() }
    val minimumFlingVelocityPx = with(density) { 620.dp.toPx() }
    var routeOffset by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latestOnBack by rememberUpdatedState(onBack)

    fun settle(target: Float, durationMillis: Int, complete: Boolean) {
        settleJob?.cancel()
        val start = routeOffset
        settleJob = scope.launch {
            animate(
                initialValue = start,
                targetValue = target,
                animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
            ) { value, _ -> routeOffset = value }
            if (complete) {
                // Reset and mutate the route stack in one main-thread turn. App.kt uses a
                // non-animated pop for this callback because this animation already owns
                // the transition; Back buttons still use the normal reverse push.
                routeOffset = 0f
                latestOnBack()
            }
            settleJob = null
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            settleJob?.cancel()
            settleJob = null
            routeOffset = 0f
        }
    }
    if (!enabled) return this

    return graphicsLayer {
        // The route itself tracks the drag instead of waiting for release. The app shell's
        // own theme background is revealed behind it, so no fake screenshot/state copy is
        // needed and the previous screen is not recomposed just to service a gesture.
        translationX = routeOffset.coerceIn(0f, size.width)
        shadowElevation = if (routeOffset > 0f) 12f else 0f
    }.pointerInput(enabled, edgeWidthPx, minimumTravelPx, minimumFlingVelocityPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

            // UIScreenEdgePanGestureRecognizer only owns touches born at the leading edge.
            // Drain non-edge gestures without consuming them so scrolling, carousels and the
            // Mushaf's own horizontal paging keep their existing behavior.
            if (down.position.x > edgeWidthPx) {
                var anyPressed = true
                while (anyPressed) {
                    anyPressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                }
                return@awaitEachGesture
            }

            settleJob?.cancel()
            settleJob = null
            routeOffset = 0f
            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }
            val touchSlop = viewConfiguration.touchSlop
            var horizontalTravel = 0f
            var verticalTravel = 0f
            var claimed = false
            var rejected = false
            var pressed = true

            while (pressed && !rejected) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChange()
                horizontalTravel += delta.x
                verticalTravel += delta.y
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (!claimed) {
                    val verticalWins = abs(verticalTravel) > touchSlop &&
                        abs(verticalTravel) > abs(horizontalTravel)
                    val movedLeft = horizontalTravel < -touchSlop
                    rejected = verticalWins || movedLeft
                    claimed = !rejected && horizontalTravel > touchSlop &&
                        horizontalTravel > abs(verticalTravel) * 1.1f
                }

                if (claimed) {
                    routeOffset = horizontalTravel.coerceIn(0f, size.width.toFloat())
                    change.consume()
                }
                pressed = change.pressed
            }

            if (claimed) {
                val width = size.width.toFloat().coerceAtLeast(1f)
                val velocityX = velocityTracker.calculateVelocity().x
                val shouldComplete = routeOffset >= width * 0.33f ||
                    (routeOffset >= minimumTravelPx && velocityX >= minimumFlingVelocityPx)

                if (shouldComplete) {
                    val remaining = (1f - routeOffset / width).coerceIn(0f, 1f)
                    settle(
                        target = width,
                        durationMillis = (190 * remaining).roundToInt().coerceIn(70, 190),
                        complete = true,
                    )
                } else {
                    settle(target = 0f, durationMillis = 170, complete = false)
                }
            } else if (routeOffset != 0f) {
                settle(target = 0f, durationMillis = 120, complete = false)
            }
        }
    }
}
