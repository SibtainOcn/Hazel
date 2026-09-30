package com.hazel.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** How far a card pulls in while its list is moving. */
private const val SHRUNK_SCALE = 0.88f

/**
 * A list's cards pull in and dim while it is being scrolled, and spring back to full size
 * when it stops. It makes a flick read as the list moving under the finger rather than as a
 * wall of cards being redrawn, and it settles the moment the list does.
 *
 * One value per list, shared by all of its items, so every card moves together.
 */
@Composable
fun rememberScrollShrink(state: ScrollableState): State<Float> =
    animateFloatAsState(
        targetValue = if (state.isScrollInProgress) SHRUNK_SCALE else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scrollShrink"
    )

/**
 * Applies [shrink] from [rememberScrollShrink] to one item. The value is read while the
 * item is drawn, not while it is composed, so the animation redraws the cards without
 * rebuilding them.
 */
fun Modifier.scrollShrink(shrink: State<Float>): Modifier = graphicsLayer {
    val scale = shrink.value
    scaleX = scale
    scaleY = scale
    alpha = 0.5f + 0.5f * (scale - SHRUNK_SCALE) / (1f - SHRUNK_SCALE)
}
