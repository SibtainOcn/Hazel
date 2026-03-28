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
    // FORWARD NAVIGATION 
    // ============================================================================

    fun forwardEnter(): EnterTransition {
        return fadeIn(
            animationSpec = tween(
                durationMillis = ENTER_DURATION,
                easing = EmphasizedDecelerate
            )
        ) + scaleIn(
            initialScale = ENTER_SCALE,
            animationSpec = tween(
                durationMillis = ENTER_DURATION,
                easing = EmphasizedDecelerate
            )
        ) + slideInVertically(
            initialOffsetY = { fullHeight -> -fullHeight / 12 },
            animationSpec = tween(
                durationMillis = ENTER_DURATION,
                easing = EmphasizedDecelerate
            )
        )
    }

    fun forwardExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = EXIT_DURATION,
                easing = EmphasizedAccelerate
            )
        )
    }

    
    fun backEnter(): EnterTransition {
        return fadeIn(
            animationSpec = tween(
                durationMillis = ENTER_DURATION,
                easing = EmphasizedDecelerate
            )
        )
    }

    fun backExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = EXIT_DURATION,
                easing = EmphasizedAccelerate
            )
        ) + slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight / 12 },
            animationSpec = tween(
                durationMillis = EXIT_DURATION,
                easing = EmphasizedAccelerate
            )
        )
    }

    // ============================================================================
    // MODAL TRANSITIONS (Fullscreen overlays)
    // ============================================================================

    fun modalEnter(): EnterTransition = forwardEnter()
    fun modalExit(): ExitTransition = backExit()
}
