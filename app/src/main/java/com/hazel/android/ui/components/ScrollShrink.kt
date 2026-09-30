package com.hazel.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far a card pulls in at the fastest scroll. */
private const val SHRUNK_SCALE = 0.9f

/** How far a card dims at the fastest scroll. */
private const val SHRUNK_ALPHA = 0.6f

/** Scroll speed below which the cards stay at full size: a slow read-along drag. */
private const val REST_SPEED_DP = 250f

/** Scroll speed at which the cards are fully pulled in: a hard flick. */
private const val FULL_SPEED_DP = 3000f

/** How long the list may go without moving before the cards spring back. */
private const val IDLE_MS = 60L

/** Pulling in follows the finger closely; a stiff spring with no bounce. */
private val shrinkSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1200f)

/** Coming back is quick with a hint of give, so the list settles rather than snaps. */
private val releaseSpec = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)

/**
 * A list's cards pull in and dim as it scrolls, in step with how fast it is moving: a slow
 * drag barely touches them, a flick pulls them in, and they ease back out as the fling slows
 * and spring to full size the moment it stops.
 *
 * It listens to the scroll the list itself dispatches, so a list scrolled by the app (jumping
 * to an item) does not pulse, and a list pinned at its top or bottom does not shrink.
 *
 * Attach it to the list with [Modifier.nestedScroll] and to each item with [scrollShrink].
 * One per list, shared by all of its items, so every card moves together.
 */
@Stable
class ScrollShrink internal constructor(
    private val scope: CoroutineScope,
    private val pxPerDp: Float
) : NestedScrollConnection {
    private val scale = Animatable(1f)
    private var lastEventNanos = 0L
    private var speed = 0f
    private var release: Job? = null

    /** The current scale, 1 at rest; read it while drawing so it never recomposes. */
    val value: Float get() = scale.value

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val moved = abs(consumed.y)
        if (moved > 0f) track(moved)
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        settle()
        return Velocity.Zero
    }

    private fun track(movedPx: Float) {
        val now = System.nanoTime()
        // The gap since the last step, bounded so the first step of a gesture and a dropped
        // frame read as one ordinary frame rather than as a standstill or a jump.
        val elapsed = if (lastEventNanos == 0L) FRAME_NANOS
        else (now - lastEventNanos).coerceIn(MIN_STEP_NANOS, MAX_STEP_NANOS)
        lastEventNanos = now

        val instant = movedPx / pxPerDp / (elapsed / 1_000_000_000f)
        // Smoothed over a few frames: touch deltas arrive unevenly, and following them raw
        // makes the cards shiver.
        speed += (instant - speed) * 0.35f

        val pull = ((speed - REST_SPEED_DP) / (FULL_SPEED_DP - REST_SPEED_DP)).coerceIn(0f, 1f)
        val target = 1f - (1f - SHRUNK_SCALE) * pull
        scope.launch { scale.animateTo(target, shrinkSpec) }

        release?.cancel()
        release = scope.launch {
            delay(IDLE_MS)
            release = null
            settle()
        }
    }

    private fun settle() {
        release?.cancel()
        release = null
        lastEventNanos = 0L
        speed = 0f
        scope.launch { scale.animateTo(1f, releaseSpec) }
    }

    internal companion object {
        const val FRAME_NANOS = 16_666_667L
        const val MIN_STEP_NANOS = 4_000_000L
        const val MAX_STEP_NANOS = 50_000_000L
        fun alphaFor(scale: Float): Float =
            SHRUNK_ALPHA + (1f - SHRUNK_ALPHA) * ((scale - SHRUNK_SCALE) / (1f - SHRUNK_SCALE)).coerceIn(0f, 1f)
    }
}

@Composable
fun rememberScrollShrink(): ScrollShrink {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    return remember(scope, density) { ScrollShrink(scope, density) }
}

/**
 * Applies [shrink] from [rememberScrollShrink] to one item. The value is read while the
 * item is drawn, not while it is composed, so the animation redraws the cards without
 * rebuilding them.
 */
fun Modifier.scrollShrink(shrink: ScrollShrink): Modifier = graphicsLayer {
    val scale = shrink.value
    scaleX = scale
    scaleY = scale
    alpha = ScrollShrink.alphaFor(scale)
}
