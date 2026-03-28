package com.hazel.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium 4-dot rotating + squish loader inspired by CSS l37.
 * Dots alternate between spread and compact positions while rotating.
 * Color defaults to accent / onPrimary.
 */
@Composable
fun HazelDotLoader(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val transition = rememberInfiniteTransition(label = "dotLoader")

    // Squish animation — dots move between spread (30px) and compact (14px)
    val spread by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spread"
    )

    // Rotation — full 180° every 1.5s
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(24.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxSpread = size.width * 0.35f
        val dotRadius = size.width * 0.06f
        val bigDotRadius = size.width * 0.1f

        val angleRad = Math.toRadians(rotation.toDouble())

        // 4 dots at 0°, 90°, 180°, 270° offsets — rotated
        val offsets = listOf(0.0, 90.0, 180.0, 270.0)
        offsets.forEachIndexed { i, angleDeg ->
            val a = angleRad + Math.toRadians(angleDeg)
            val dist = maxSpread * spread
            val x = cx + (dist * cos(a)).toFloat()
            val y = cy + (dist * sin(a)).toFloat()
            // Outer dots are bigger when spread, inner dots bigger when compact
            val r = if (spread > 0.7f) {
                if (i % 2 == 0) bigDotRadius else dotRadius
            } else {
                if (i % 2 == 0) dotRadius else bigDotRadius
            }
            drawCircle(color = color, radius = r, center = Offset(x, y))
        }
    }
}
