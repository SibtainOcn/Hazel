package com.hazel.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.hazel.android.download.ProcessingStep

/** How many stages are in view at once; the rest slide in as the download reaches them. */
private const val VISIBLE_STEPS = 3

/**
 * The stages a finished download goes through, laid across its artwork as a track of dots.
 *
 * Three are in view at a time. Stages done are solid and joined by a full line, the one in
 * progress pulses and the line from it fills towards the next, and those still to come are
 * faint. When the download moves on, the track slides along so the current stage stays in
 * the middle, until the last three are in view. A glow in the accent colour sits behind the
 * current stage and travels with it.
 *
 * Everything is drawn rather than laid out, so the slide and the fill redraw the artwork
 * without rebuilding the card.
 */
@Composable
fun ProcessingTracker(
    steps: List<ProcessingStep>,
    current: Int,
    modifier: Modifier = Modifier
) {
    val labels = steps.map { stringResource(it.label) }
    val shown = minOf(VISIBLE_STEPS, steps.size).coerceAtLeast(1)
    val last = (steps.size - 1).coerceAtLeast(0)
    val at = current.coerceIn(0, last)

    // The window keeps the current stage in the middle, and stops when the end is in view.
    val windowStart = (at - shown / 2).coerceIn(0, (steps.size - shown).coerceAtLeast(0))
    val start by animateFloatAsState(
        targetValue = windowStart.toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "trackerWindow"
    )
    val position by animateFloatAsState(
        targetValue = at.toFloat(),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "trackerPosition"
    )

    val transition = rememberInfiniteTransition(label = "tracker")
    val flow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "trackerFlow"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trackerPulse"
    )

    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .clipToBounds()
            .drawBehind {
                // A shade over the artwork, so the track reads on any picture.
                drawRect(Color.Black.copy(alpha = 0.5f))

                val slot = size.width / shown
                val y = size.height * 0.46f
                fun xOf(index: Float) = (index - start + 0.5f) * slot

                // The glow behind the current stage, drifting with it as it moves.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(xOf(position), y),
                        radius = size.height * 0.62f
                    ),
                    radius = size.height * 0.62f,
                    center = Offset(xOf(position), y)
                )

                val lineWidth = 2.5.dp.toPx()
                val dotDone = 5.5.dp.toPx()
                val dotNext = 4.5.dp.toPx()
                val gap = 9.dp.toPx()

                // Lines between stages: full behind, filling from the current one, faint ahead.
                for (i in 0 until steps.size - 1) {
                    val from = Offset(xOf(i.toFloat()) + gap, y)
                    val to = Offset(xOf(i + 1f) - gap, y)
                    if (to.x < 0f || from.x > size.width) continue
                    drawLine(Color.White.copy(alpha = 0.28f), from, to, lineWidth, StrokeCap.Round)
                    val filled = when {
                        i < at -> 1f
                        i == at -> flow
                        else -> 0f
                    }
                    if (filled > 0f) {
                        val end = Offset(from.x + (to.x - from.x) * filled, y)
                        drawLine(
                            Color.White.copy(alpha = if (i < at) 1f else 1f - 0.5f * flow),
                            from, end, lineWidth, StrokeCap.Round
                        )
                    }
                }

                steps.indices.forEach { i ->
                    val x = xOf(i.toFloat())
                    if (x < -slot || x > size.width + slot) return@forEach
                    val center = Offset(x, y)
                    when {
                        i < at -> drawCircle(Color.White, dotDone, center)
                        i == at -> {
                            drawCircle(
                                Color.White.copy(alpha = 0.55f * (1f - pulse)),
                                radius = dotDone + 7.dp.toPx() * pulse,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(Color.White, dotDone + 1.dp.toPx(), center)
                        }
                        else -> drawCircle(Color.White.copy(alpha = 0.38f), dotNext, center)
                    }

                    val text = measurer.measure(labels[i], labelStyle)
                    drawText(
                        textLayoutResult = text,
                        color = Color.White.copy(alpha = if (i <= at) 1f else 0.6f),
                        topLeft = Offset(
                            x - text.size.width / 2f,
                            y + 12.dp.toPx()
                        )
                    )
                }
            }
    )
}
