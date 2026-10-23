package com.hazel.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.theme.LocalDarkTheme
import com.hazel.android.ui.theme.LocalGlass
import com.hazel.android.ui.theme.LocalGlassPalette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A quiet grid of faint dots for an empty screen. Dots near a finger lift and brighten, then
 * settle back when it lifts. Touches pass straight through, so scrolling and taps still work.
 *
 * The dots take the glass palette from top to bottom, so they sit inside the glow behind them.
 * Without glass they use the accent; in light theme they turn to a darker ink. A faint glow
 * drifts across on its own, and stays still when the system has animations turned off.
 */
@Composable
fun AmbientDotGrid(
    modifier: Modifier = Modifier,
    /** A faint glow that wanders the grid on its own; off when the system removes animations. */
    ambient: Boolean = rememberAnimationsOn(),
    /** True while something covers the grid, so the glow stops instead of drifting unseen. */
    paused: Boolean = false
) {
    val dark = LocalDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val palette = if (LocalGlass.current) LocalGlassPalette.current else listOf(accent)
    val tones = remember(palette, dark) { if (dark) palette else palette.map { lerp(it, Color.Black, LIGHT_INK) } }
    val glowTo = if (dark) lerp(accent, Color.White, 0.5f) else accent
    val restAlpha = if (dark) REST_ALPHA_DARK else REST_ALPHA_LIGHT

    var touch by remember { mutableStateOf(Offset.Unspecified) }
    val lift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val wander = if (ambient && !paused) {
        rememberInfiniteTransition(label = "dots").animateFloat(
            initialValue = 0f,
            targetValue = TAU,
            animationSpec = infiniteRepeatable(tween(WANDER_MS, easing = LinearEasing)),
            label = "wander"
        )
    } else null

    Spacer(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            touch = change.position
                            if (lift.targetValue != 1f) scope.launch { lift.animateTo(1f, RISE) }
                        } else if (lift.targetValue != 0f) {
                            scope.launch { lift.animateTo(0f, SETTLE) }
                        }
                    }
                }
            }
            .drawWithCache {
                val step = SPACING.toPx()
                val reach = REACH.toPx()
                val baseRadius = DOT_RADIUS.toPx()
                val grow = DOT_GROW.toPx()
                val rise = DOT_RISE.toPx()
                val cols = (size.width / step).toInt().coerceAtLeast(1)
                val rows = (size.height / step).toInt().coerceAtLeast(1)
                val left = (size.width - (cols - 1) * step) / 2f
                val top = (size.height - (rows - 1) * step) / 2f
                val rowColor = Array(rows) { r ->
                    val f = if (rows > 1) r / (rows - 1f) else 0f
                    toneAt(tones, f)
                }
                val rowFade = FloatArray(rows) { r ->
                    val f = if (rows > 1) r / (rows - 1f) else 0.5f
                    edgeFade(f)
                }
                val wanderReach = reach * WANDER_REACH
                onDrawBehind {
                    val strength = lift.value
                    val at = touch
                    val phase = wander?.value
                    val idle = if (phase != null) IDLE_STRENGTH * (1f - strength) else 0f
                    val spot = if (phase != null) Offset(
                        size.width * (0.5f + 0.34f * sin(phase)),
                        size.height * (0.5f + 0.32f * sin(2f * phase + 1f))
                    ) else Offset.Zero
                    for (r in 0 until rows) {
                        val y = top + r * step
                        val fade = rowFade[r]
                        if (fade <= 0f) continue
                        val base = rowColor[r]
                        for (c in 0 until cols) {
                            val x = left + c * step
                            val touched = if (strength > 0f && at != Offset.Unspecified) {
                                ((1f - hypot(x - at.x, y - at.y) / reach).coerceIn(0f, 1f)) * strength
                            } else 0f
                            val drifted = if (idle > 0f) {
                                ((1f - hypot(x - spot.x, y - spot.y) / wanderReach).coerceIn(0f, 1f)) * idle
                            } else 0f
                            val near = maxOf(touched, drifted)
                            val color = if (near > 0f) lerp(base, glowTo, near * 0.5f) else base
                            drawCircle(
                                color = color,
                                radius = baseRadius + grow * near,
                                center = Offset(x, y - rise * near),
                                alpha = (restAlpha + near * TOUCH_ALPHA) * fade
                            )
                        }
                    }
                }
            }
    )
}

/** Blends the palette from its first colour at the top to its last at the bottom. */
private fun toneAt(tones: List<Color>, f: Float): Color {
    if (tones.size == 1) return tones[0]
    val pos = f * (tones.size - 1)
    val i = pos.toInt().coerceAtMost(tones.size - 2)
    return lerp(tones[i], tones[i + 1], pos - i)
}

/** Fades the rows nearest the top and bottom edges, so the grid has no hard border. */
private fun edgeFade(f: Float): Float {
    val d = minOf(f, 1f - f) / EDGE
    val t = d.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** False when the system's animator duration scale is 0 (Remove animations is on). */
@Composable
private fun rememberAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) > 0f
    }
}

private const val TAU = (2 * PI).toFloat()
private const val WANDER_MS = 24_000
private const val WANDER_REACH = 1.7f
private const val IDLE_STRENGTH = 0.38f
private val SPACING = 22.dp
private val REACH = 76.dp
private val DOT_RADIUS = 1.1.dp
private val DOT_GROW = 1.6.dp
private val DOT_RISE = 3.5.dp
private const val REST_ALPHA_DARK = 0.10f
private const val REST_ALPHA_LIGHT = 0.16f
private const val TOUCH_ALPHA = 0.6f
private const val LIGHT_INK = 0.4f
private const val EDGE = 0.18f
private val RISE = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val SETTLE = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
