package com.hazel.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import kotlinx.coroutines.flow.collectLatest

/** One pass of every loading placeholder inside a sheet. */
const val LOADING_CYCLE_MS = 1800L

/**
 * The one loop a sheet's loading placeholders move by, so they pass together and at one calm
 * pace. It runs only while at least one placeholder is shown, and stops when the last goes.
 */
@Stable
class LoadingClock internal constructor() {
    internal val phase = mutableFloatStateOf(0f)
    internal var users by mutableIntStateOf(0)
}

private val LocalLoadingClock = staticCompositionLocalOf<LoadingClock?> { null }

/**
 * Gives [content] a shared [LoadingClock]. Nested inside another provider (a sheet opened
 * from a sheet), the outer clock is kept, so both sheets move as one.
 */
@Composable
fun ProvideLoadingClock(content: @Composable () -> Unit) {
    if (LocalLoadingClock.current != null) {
        content()
        return
    }
    val clock = remember { LoadingClock() }
    LaunchedEffect(clock) {
        snapshotFlow { clock.users > 0 }.collectLatest { running ->
            if (running) while (true) {
                withInfiniteAnimationFrameMillis { clock.phase.floatValue = phaseAt(it) }
            }
        }
    }
    CompositionLocalProvider(LocalLoadingClock provides clock, content = content)
}

/**
 * The loading phase, 0 to 1, from the shared [LoadingClock] where one is provided. Without
 * one the caller runs its own loop on the same time base, so it still moves in step. Read it
 * while drawing, so the loop repaints and never recomposes.
 */
@Composable
fun loadingPhase(): State<Float> {
    val clock = LocalLoadingClock.current
    if (clock != null) {
        DisposableEffect(clock) {
            clock.users++
            onDispose { clock.users-- }
        }
        return clock.phase
    }
    val own = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(own) {
        while (true) withInfiniteAnimationFrameMillis { own.floatValue = phaseAt(it) }
    }
    return own
}

/** The phase at a frame time. Frames share one time base, so separate loops stay in step. */
internal fun phaseAt(frameMillis: Long): Float =
    (frameMillis % LOADING_CYCLE_MS) / LOADING_CYCLE_MS.toFloat()
