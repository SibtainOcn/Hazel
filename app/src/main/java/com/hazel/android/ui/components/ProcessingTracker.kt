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

    // Drawn in the theme's own colours, so the track is light on the light theme and dark
    // on the dark one: a shade of the surface over the artwork, the surface's text colour
    // for the lines and names, and the accent for what is done and in progress.
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val shade = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .clipToBounds()
            .drawBehind {
                // A shade over the artwork, so the track reads on any picture.
                drawRect(shade.copy(alpha = 0.72f))

                val slot = size.width / shown
                val y = size.height * 0.44f
                fun xOf(index: Float) = (index - start + 0.5f) * slot

                // The glow behind the current stage, drifting with it as it moves.
                val glow = size.height * 0.7f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(xOf(position), y),
                        radius = glow
                    ),
                    radius = glow,
                    center = Offset(xOf(position), y)
                )

                val lineWidth = 3.dp.toPx()
                val dotDone = 6.5.dp.toPx()
                val dotNext = 5.dp.toPx()
                val gap = 12.dp.toPx()

                // Lines between stages: solid behind, a shimmering fill from the current one,
                // a plain track ahead.
                for (i in 0 until steps.size - 1) {
                    val from = Offset(xOf(i.toFloat()) + gap, y)
                    val to = Offset(xOf(i + 1f) - gap, y)
                    if (to.x < 0f || from.x > size.width) continue
                    drawLine(ink.copy(alpha = 0.18f), from, to, lineWidth, StrokeCap.Round)
                    when {
                        i < at -> drawLine(accent, from, to, lineWidth, StrokeCap.Round)
                        i == at -> {
                            drawLine(accent.copy(alpha = 0.45f), from, to, lineWidth, StrokeCap.Round)
                            // A bright band running along the line, over and over, so the
                            // stage reads as working rather than waiting.
                            val length = to.x - from.x
                            val band = length * 0.45f
                            val head = from.x - band + (length + band) * flow
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    0.5f to accent,
                                    0.6f to ink.copy(alpha = 0.9f),
                                    1f to Color.Transparent,
                                    startX = head,
                                    endX = head + band
                                ),
                                start = from,
                                end = to,
                                strokeWidth = lineWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                steps.indices.forEach { i ->
                    val x = xOf(i.toFloat())
                    if (x < -slot || x > size.width + slot) return@forEach
                    val center = Offset(x, y)
                    when {
                        i < at -> drawCircle(accent, dotDone, center)
                        i == at -> {
                            drawCircle(
                                accent.copy(alpha = 0.5f * (1f - pulse)),
                                radius = dotDone + 8.dp.toPx() * pulse,
                                center = center,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                            drawCircle(accent, dotDone + 1.5.dp.toPx(), center)
                            drawCircle(shade, dotDone * 0.45f, center)
                        }
                        else -> drawCircle(ink.copy(alpha = 0.30f), dotNext, center)
                    }

                    val text = measurer.measure(labels[i], labelStyle)
                    drawText(
                        textLayoutResult = text,
                        color = ink.copy(alpha = if (i <= at) 1f else 0.5f),
                        topLeft = Offset(
                            x - text.size.width / 2f,
                            y + 14.dp.toPx()
                        )
                    )
                }
            }
    )
}
