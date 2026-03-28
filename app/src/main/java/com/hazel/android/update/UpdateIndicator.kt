package com.hazel.android.update

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hazel.android.R

/**
 * Animated update indicator button shown in the top bar next to the Hazel logo.
 * Uses install_ic.xml with an accent-color "liquid fill" animation that sweeps down.
 *
 * - During download: accent color fills from top to bottom in sync with progress
 * - When available/ready: continuous sweep animation
 * - On click: navigates to the dedicated UpdateScreen
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
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

    // Infinite sweep animation for non-downloading states (available / ready)
    val infiniteTransition = rememberInfiniteTransition(label = "update_sweep")
    val sweepFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // For downloading: smooth animated fill level following actual progress
    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress,
        animationSpec = tween(durationMillis = 300),
        label = "dl_progress"
    )

    // Determine the fill fraction
    val fillFraction = when {
        isDownloading -> animatedProgress
        isReady -> 1f  // fully filled when ready
        else -> sweepFraction  // continuous sweep when available
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Dim base icon (always full, faded)
        Icon(
            painter = painterResource(R.drawable.install_ic),
            contentDescription = "Update",
            modifier = Modifier.size(22.dp),
            tint = dimColor
        )

        // Layer 2: Accent-colored icon masked by a fill rectangle from top
        // This creates the "liquid fill going down" effect
        Box(
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
            contentAlignment = Alignment.Center
        ) {
            // Draw the accent icon
            Icon(
                painter = painterResource(R.drawable.install_ic),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = accentColor
            )

            // Mask: clear everything below the fill line
            // fillFraction 0 = nothing shown, 1 = fully shown
            Canvas(modifier = Modifier.size(22.dp)) {
                val fillHeight = size.height * fillFraction
                // Clear the area below the fill line to hide the accent icon there
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(0f, fillHeight),
                    size = Size(size.width, size.height - fillHeight),
                    blendMode = BlendMode.Clear
                )
            }
        }
    }
}
