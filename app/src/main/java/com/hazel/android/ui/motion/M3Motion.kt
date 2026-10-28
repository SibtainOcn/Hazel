package com.hazel.android.ui.motion

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween


object M3Motion {

    // ============================================================================
    // DURATION — Fast and snappy
    // ============================================================================

    private const val ENTER_DURATION = 250
    private const val EXIT_DURATION = 200

    // ============================================================================
    // M3 EASING CURVES
    // ============================================================================

    /** Emphasized Decelerate — Fast start, smooth landing (for enter) */
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Emphasized Accelerate — Smooth start, fast exit (for exit) */
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    // ============================================================================
    // SCREEN TRANSITIONS
    // ============================================================================
    //
    // The old and new screens always overlap, so no frame shows an empty screen, and
    // nothing scales, which keeps the glass behind them from being redrawn at every size.
    //
    // Opening a screen is a shared axis: the new one moves in a short way from the end
    // while it fades up and the old one drifts the other way as it fades. Back plays the
    // same motion in reverse. Tabs are peers, so switching them is a plain crossfade.

    private const val AXIS_DURATION = 300
    private const val AXIS_FADE_IN = 220
    private const val AXIS_FADE_IN_DELAY = 50
    private const val AXIS_FADE_OUT = 140
    private const val TAB_FADE_IN = 200
    private const val TAB_FADE_IN_DELAY = 40
    private const val TAB_FADE_OUT = 110

    /** How far a screen travels, as a fraction of its width. */
    private const val AXIS_SHIFT = 10

    private val LinearOutSlowIn = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    private val FastOutLinearIn = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    private fun axisEnter(from: Int): EnterTransition =
        slideInHorizontally(tween(AXIS_DURATION, easing = EmphasizedDecelerate)) { from * it / AXIS_SHIFT } +
            fadeIn(tween(AXIS_FADE_IN, delayMillis = AXIS_FADE_IN_DELAY, easing = LinearOutSlowIn))

    private fun axisExit(to: Int): ExitTransition =
        slideOutHorizontally(tween(AXIS_DURATION, easing = EmphasizedDecelerate)) { to * it / AXIS_SHIFT } +
            fadeOut(tween(AXIS_FADE_OUT, easing = FastOutLinearIn))

    fun forwardEnter(): EnterTransition = axisEnter(1)

    fun forwardExit(): ExitTransition = axisExit(-1)

    fun backEnter(): EnterTransition = axisEnter(-1)

    fun backExit(): ExitTransition = axisExit(1)

    fun tabEnter(): EnterTransition =
        fadeIn(tween(TAB_FADE_IN, delayMillis = TAB_FADE_IN_DELAY, easing = LinearOutSlowIn))

    fun tabExit(): ExitTransition = fadeOut(tween(TAB_FADE_OUT, easing = FastOutLinearIn))

    // ============================================================================
    // CONTENT TRANSITIONS (elements appearing inside a screen)
    // ============================================================================

    /** Emphasized decelerate fade and rise, for content arriving in place. */
    fun contentEnter(): EnterTransition {
        return fadeIn(
            animationSpec = tween(ENTER_DURATION, easing = EmphasizedDecelerate)
        ) + slideInVertically(
            initialOffsetY = { it / 8 },
            animationSpec = tween(ENTER_DURATION, easing = EmphasizedDecelerate)
        )
    }

    /** Emphasized accelerate fade, for content leaving in place. */
    fun contentExit(): ExitTransition {
        return fadeOut(animationSpec = tween(EXIT_DURATION, easing = EmphasizedAccelerate))
    }

    /** Spec for value animations that should share the screen transitions' feel. */
    fun <T> emphasized(durationMillis: Int = ENTER_DURATION) =
        tween<T>(durationMillis = durationMillis, easing = EmphasizedDecelerate)
}
