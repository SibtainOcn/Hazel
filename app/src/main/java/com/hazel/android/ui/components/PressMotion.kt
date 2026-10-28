package com.hazel.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** The springs every pressed control here shares, quick and with a little bounce. */
private val PressSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
private val CornerSpring = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/**
 * Squeezes a control a little while it is held, the M3 Expressive press, and springs it
 * back on release. Read in the draw phase, so a press never recomposes the content.
 */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, PressSpring, label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A corner radius that tightens while [interaction] is pressed, so the shape morphs too. */
@Composable
fun pressCorner(interaction: InteractionSource, resting: Dp, pressed: Dp): State<Dp> {
    val isPressed by interaction.collectIsPressedAsState()
    return animateDpAsState(if (isPressed) pressed else resting, CornerSpring, label = "corner")
}
