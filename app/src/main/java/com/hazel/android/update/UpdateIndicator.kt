package com.hazel.android.update

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Animated update indicator button shown in the top bar next to the Hazel logo.
 * Pure Compose Canvas — fully theme-adaptive and accent-color aware.
 *
 * States:
 * - Downloading: Animated pulsing download arrow with bouncing motion
 * - Available (not downloading, not ready): Static download arrow
 * - Ready to Install: Static checkmark
 *
 * Optimization: `rememberInfiniteTransition` is only created when `isDownloading`
 * is true. When static (Available/Ready), no animation runs — zero recomposition
 * overhead. Safe for millions of devices.
 */
@Composable
fun UpdateIndicator(
    isDownloading: Boolean,
    downloadProgress: Float,
    isReady: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    // Animation values — only allocated when actually downloading
    val bounceOffset: Float
    val pulseAlpha: Float

    if (isDownloading) {
        val infiniteTransition = rememberInfiniteTransition(label = "updateIndicator")
        val bounce by infiniteTransition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounce"
        )
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        bounceOffset = bounce
        pulseAlpha = pulse
    } else {
        bounceOffset = 0f
        pulseAlpha = 1f
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val strokeWidth = w * 0.14f

            when {
                isReady -> drawCheckmark(accentColor, w, h, strokeWidth)
                isDownloading -> drawDownloadArrow(
                    color = accentColor.copy(alpha = pulseAlpha),
                    w = w, h = h,
                    strokeWidth = strokeWidth,
                    verticalOffset = bounceOffset
                )
                else -> drawDownloadArrow(
                    color = accentColor,
                    w = w, h = h,
                    strokeWidth = strokeWidth,
                    verticalOffset = 0f
                )
            }
        }
    }
}

/**
 * Draws a download arrow icon: vertical line with arrowhead + horizontal base line.
 */
private fun DrawScope.drawDownloadArrow(
    color: Color,
    w: Float,
    h: Float,
    strokeWidth: Float,
    verticalOffset: Float
) {
    val cx = w / 2f
    val arrowTop = h * 0.1f + verticalOffset
    val arrowBottom = h * 0.65f + verticalOffset
    val arrowWing = w * 0.22f
    val baseY = h * 0.85f

    val stroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )

    // Vertical shaft
    drawLine(
        color = color,
        start = Offset(cx, arrowTop),
        end = Offset(cx, arrowBottom),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // Arrowhead
    val arrowPath = Path().apply {
        moveTo(cx - arrowWing, arrowBottom - arrowWing)
        lineTo(cx, arrowBottom)
        lineTo(cx + arrowWing, arrowBottom - arrowWing)
    }
    drawPath(arrowPath, color = color, style = stroke)

    // Base line (tray)
    drawLine(
        color = color,
        start = Offset(w * 0.2f, baseY),
        end = Offset(w * 0.8f, baseY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

/**
 * Draws a checkmark icon for "ready to install" state.
 */
private fun DrawScope.drawCheckmark(
    color: Color,
    w: Float,
    h: Float,
    strokeWidth: Float
) {
    val stroke = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )

    val checkPath = Path().apply {
        moveTo(w * 0.18f, h * 0.50f)
        lineTo(w * 0.40f, h * 0.72f)
        lineTo(w * 0.82f, h * 0.28f)
    }
    drawPath(checkPath, color = color, style = stroke)
}
