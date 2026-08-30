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
    // SCALE
    // ============================================================================

    /** Initial scale for entering screens (92% — subtle but noticeable) */
    private const val ENTER_SCALE = 0.92f

    // ============================================================================
    // SCREEN TRANSITIONS — fade through
    // ============================================================================
    //
    // The screens behind the navigation bar are peers: nothing goes forward or back
    // between Home, Downloads and More, so nothing should look as though it does. They
    // used to slide vertically past each other by a twelfth of the height, and because a
    // screen is drawn inside the bars rather than under them, that read as a short panel
    // sliding about in the middle instead of one screen replacing another.
    //
    // A fade through is what Material uses when there is no relationship to express: the
    // outgoing screen fades quickly, and once it is gone the incoming one fades up while
    // growing the last of the way to full size. Nothing translates, so nothing looks like
    // it is arriving from somewhere.

    /** How long the outgoing screen takes to clear. */
    private const val FADE_OUT_DURATION = 90

    /** How long the incoming screen takes, once the outgoing one has cleared. */
    private const val FADE_IN_DURATION = 210

    private fun fadeThroughEnter(): EnterTransition {
        return fadeIn(
            animationSpec = tween(
                durationMillis = FADE_IN_DURATION,
                delayMillis = FADE_OUT_DURATION,
                easing = EmphasizedDecelerate
            )
        ) + scaleIn(
            initialScale = ENTER_SCALE,
            animationSpec = tween(
                durationMillis = FADE_IN_DURATION,
                delayMillis = FADE_OUT_DURATION,
                easing = EmphasizedDecelerate
            )
        )
    }

    private fun fadeThroughExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = FADE_OUT_DURATION,
                easing = EmphasizedAccelerate
            )
        )
    }

    fun forwardEnter(): EnterTransition = fadeThroughEnter()

    fun forwardExit(): ExitTransition = fadeThroughExit()

    fun backEnter(): EnterTransition = fadeThroughEnter()

    fun backExit(): ExitTransition = fadeThroughExit()

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
