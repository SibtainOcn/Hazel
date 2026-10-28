package com.hazel.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import kotlin.random.Random

/**
 * Faint stars for an empty screen. They twinkle and drift slowly upward, fading out towards
 * every edge. Stars near a finger brighten, then settle when it lifts. Touches pass straight
 * through, so scrolling and taps still work.
 *
 * Colours come from the glass palette, or the accent without glass, nudged towards the
 * screen's text colour wherever they would be too faint to see on that theme.
 */
@Composable
fun AmbientStarField(
    modifier: Modifier = Modifier,
    /** Drift and twinkle; off when the system removes animations. */
    ambient: Boolean = rememberAnimationsOn(),
    /** True while something covers the field, so it stops instead of moving unseen. */
    paused: Boolean = false
) {
    val dark = LocalDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onBackground
    val palette = if (LocalGlass.current) LocalGlassPalette.current else listOf(accent)
    val tones = remember(palette, dark, ink) { palette.map { readable(it, dark, ink) } }
    val glow = remember(accent, dark, ink) {
        val base = readable(accent, dark, ink)
        if (dark) lerp(base, Color.White, 0.5f) else base
    }
    val restAlpha = if (dark) REST_ALPHA_DARK else REST_ALPHA_LIGHT

    var touch by remember { mutableStateOf(Offset.Unspecified) }
    val lift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val clock = remember { mutableFloatStateOf(0f) }
    if (ambient && !paused) {
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    clock.floatValue += (now - last) / 1_000_000_000f
                    last = now
                }
            }
        }
    }

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
                val w = size.width
                val h = size.height
                val area = STAR_AREA.toPx()
                val count = (w * h / (area * area)).toInt().coerceIn(MIN_STARS, MAX_STARS)
                val random = Random(SEED)
                val minRadius = RADIUS_MIN.toPx()
                val radiusSpan = RADIUS_SPAN.toPx()
                val stars = Array(count) {
                    Star(
                        x = random.nextFloat() * w,
                        y = random.nextFloat() * h,
                        radius = minRadius + random.nextFloat() * radiusSpan,
                        phase = random.nextFloat() * TAU,
                        speed = TWINKLE_MIN + random.nextFloat() * TWINKLE_SPAN
                    )
                }
                val drift = DRIFT.toPx()
                val sway = SWAY.toPx()
                val reach = REACH.toPx()
                val grow = GROW.toPx()
                val cx = w / 2f
                val cy = h * CENTRE_Y
                val rx = (w * OVAL_X).coerceAtLeast(1f)
                val ry = (h * OVAL_Y).coerceAtLeast(1f)
                onDrawBehind {
                    if (h <= 0f) return@onDrawBehind
                    val t = clock.floatValue
                    val strength = lift.value
                    val at = touch
                    for (s in stars) {
                        val y = ((s.y - t * drift) % h + h) % h
                        val x = s.x + sin(t * SWAY_SPEED + s.phase) * sway
                        val fade = 1f - smoothstep(FADE_START, 1f, hypot((x - cx) / rx, (y - cy) / ry))
                        if (fade <= 0f) continue
                        val near = if (strength > 0f && at.isSpecified) {
                            (1f - hypot(x - at.x, y - at.y) / reach).coerceIn(0f, 1f) * strength
                        } else 0f
                        val twinkle = 0.5f + 0.5f * sin(t * s.speed + s.phase)
                        drawCircle(
                            color = lerp(toneAt(tones, y / h), glow, GLOW_MIX + near * 0.5f),
                            radius = s.radius + grow * near,
                            center = Offset(x, y),
                            alpha = ((restAlpha + TWINKLE_ALPHA * twinkle + TOUCH_ALPHA * near) * fade).coerceIn(0f, 1f)
                        )
                    }
                }
            }
    )
}

private class Star(val x: Float, val y: Float, val radius: Float, val phase: Float, val speed: Float)

/** Pushes a colour towards the theme's text colour when it would be too faint on that background. */
private fun readable(c: Color, dark: Boolean, ink: Color): Color {
    val l = c.luminance()
    return if (dark) {
        if (l < MIN_LUMINANCE_DARK) lerp(c, ink, ((MIN_LUMINANCE_DARK - l) / MIN_LUMINANCE_DARK).coerceIn(0f, 0.7f)) else c
    } else {
        lerp(c, ink, if (l > MAX_LUMINANCE_LIGHT) LIGHT_INK_STRONG else LIGHT_INK)
    }
}

/** Blends the palette from its first colour at the top to its last at the bottom. */
private fun toneAt(tones: List<Color>, f: Float): Color {
    if (tones.size == 1) return tones[0]
    val pos = f.coerceIn(0f, 1f) * (tones.size - 1)
    val i = pos.toInt().coerceAtMost(tones.size - 2)
    return lerp(tones[i], tones[i + 1], pos - i)
}

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** False when the system's animator duration scale is 0 (Remove animations is on). */
@Composable
internal fun rememberAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) > 0f
    }
}

private const val TAU = (2 * PI).toFloat()
private const val SEED = 7
private val STAR_AREA = 55.dp
private const val MIN_STARS = 24
private const val MAX_STARS = 260
private val RADIUS_MIN = 0.5.dp
private val RADIUS_SPAN = 1.3.dp
private val DRIFT = 6.dp
private val SWAY = 3.dp
private const val SWAY_SPEED = 0.2f
private const val TWINKLE_MIN = 0.35f
private const val TWINKLE_SPAN = 0.9f
private val REACH = 110.dp
private val GROW = 1.2.dp
private const val CENTRE_Y = 0.46f
private const val OVAL_X = 0.62f
private const val OVAL_Y = 0.44f
private const val FADE_START = 0.3f
private const val REST_ALPHA_DARK = 0.08f
private const val REST_ALPHA_LIGHT = 0.14f
private const val TWINKLE_ALPHA = 0.24f
private const val TOUCH_ALPHA = 0.5f
private const val GLOW_MIX = 0.3f
private const val MIN_LUMINANCE_DARK = 0.18f
private const val MAX_LUMINANCE_LIGHT = 0.5f
private const val LIGHT_INK = 0.4f
private const val LIGHT_INK_STRONG = 0.6f
private val RISE = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val SETTLE = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
