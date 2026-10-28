package com.hazel.android.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.hazel.android.ui.components.rememberAnimationsOn
import kotlin.math.PI
import kotlin.math.sin

/**
 * How wavy the played line is, from 0 (flat) to 1, and how far its wave has travelled. The
 * wave flows while [flowing] and eases flat when it stops, such as under a finger or on pause.
 * With system animations off it stays flat.
 */
@Composable
internal fun rememberSeekWave(flowing: Boolean): Pair<State<Float>, State<Float>> {
    val motion = rememberAnimationsOn()
    val amount = animateFloatAsState(
        if (flowing && motion) 1f else 0f,
        animationSpec = spring(stiffness = 300f),
        label = "seekWaveAmount"
    )
    val phase = rememberLoop(running = flowing && motion, periodMs = WAVE_PERIOD_MS, span = 1f)
    return amount to phase
}

/**
 * A value that climbs through [span] every [periodMs] while [running] and holds still
 * otherwise, so nothing asks for frames while a video is paused or off screen.
 */
@Composable
private fun rememberLoop(running: Boolean, periodMs: Int, span: Float): State<Float> {
    val value = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val perNano = span / (periodMs * 1_000_000f)
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                value.floatValue = (value.floatValue + (now - last) * perNano) % span
                last = now
            }
        }
    }
    return value
}

/**
 * The played part of the bar: a wave of [amount] height whose crests travel with [phase],
 * flattened toward both ends so it meets the start and the thumb cleanly.
 */
internal fun DrawScope.drawWavyLine(
    y: Float,
    stroke: Float,
    fraction: Float,
    color: Color,
    amount: Float,
    phase: Float
) {
    val end = size.width * fraction.coerceIn(0f, 1f)
    if (end <= 0f) return
    if (amount <= 0.01f) {
        drawLine(color, Offset(0f, y), Offset(end, y), strokeWidth = stroke, cap = StrokeCap.Round)
        return
    }
    val wavelength = WAVELENGTH.toPx()
    val height = WAVE_HEIGHT.toPx() * amount
    val taper = wavelength / 2f
    val path = Path()
    var x = 0f
    val step = 2f
    while (x <= end) {
        val ease = minOf(1f, x / taper, (end - x) / taper).coerceAtLeast(0f)
        val dy = sin((x / wavelength - phase) * 2f * PI.toFloat()) * height * ease
        if (x == 0f) path.moveTo(x, y + dy) else path.lineTo(x, y + dy)
        x += step
    }
    path.lineTo(end, y)
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
}

/** The bar's thumb: an upright pill, as on Material 3 Expressive sliders, taller while dragged. */
internal fun DrawScope.drawPillThumb(x: Float, y: Float, color: Color, height: Float, width: Float) {
    drawLine(
        color,
        Offset(x, y - height / 2f),
        Offset(x, y + height / 2f),
        strokeWidth = width,
        cap = StrokeCap.Round
    )
}

/** The play button's resting shape, shared by the player and the play buttons on result cards. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun playButtonShape(): Shape = MaterialShapes.Pentagon.toShape()

/** A [Morph] drawn at [progress], filling its bounds. */
internal class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * The play and pause button: a still pentagon while paused that turns into a slowly turning
 * six-sided cookie while the video plays, so its edge moves with the seek bar's wave. Only
 * the shape turns; the icon stays upright, and the turn is drawn without recomposing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MorphingPlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    content: @Composable () -> Unit
) {
    val motion = rememberAnimationsOn()
    val morph = remember { Morph(MaterialShapes.Pentagon, MaterialShapes.Cookie6Sided) }
    val progress = animateFloatAsState(
        if (playing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "playMorph"
    )
    val spin = rememberLoop(running = playing && motion, periodMs = SPIN_PERIOD_MS, span = 360f)
    val shape by remember { derivedStateOf { MorphShape(morph, progress.value.coerceIn(0f, 1f)) } }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = if (motion) spin.value * progress.value else 0f
                    this.shape = shape
                    clip = true
                }
                .background(color)
        )
        content()
    }
}

/** Low and long, so it reads as a ripple rather than a zigzag. */
private val WAVELENGTH = 40.dp
private val WAVE_HEIGHT = 2.dp
private const val WAVE_PERIOD_MS = 2200
private const val SPIN_PERIOD_MS = 9000
