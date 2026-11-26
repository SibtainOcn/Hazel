package com.hazel.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.hazel.android.ui.motion.M3Motion

/**
 * A result card that arrives rather than appears: it fades up from slightly below where it
 * belongs, once, the first time it is composed, and shrinks with the list's scroll. A long list
 * scrolls past as cards settling into place instead of a wall that redraws under the finger.
 */
@Composable
fun ArrivingCard(key: Any, shrink: ScrollShrink, content: @Composable () -> Unit) {
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { shown = true }
    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = M3Motion.emphasized(320),
        label = "cardEntrance"
    )
    Box(
        modifier = Modifier
            .scrollShrink(shrink)
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 28f
            }
    ) {
        content()
    }
}
