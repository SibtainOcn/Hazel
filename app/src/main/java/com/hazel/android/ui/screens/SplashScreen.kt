package com.hazel.android.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R

/**
 * What is on screen for the moment before the app is ready.
 *
 * It continues the window background the system draws on a cold start, so the launch reads
 * as one step rather than a black frame followed by a different screen. The mark is static,
 * and a highlight travels through the wordmark beneath it, which is the only thing that
 * separates this from a frozen frame when the wait runs long.
 */
private const val SWEEP_DURATION_MS = 1400

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Tinted from the theme rather than taken as drawn. The asset is stroked in
            // white for the system's own launch window, which is always dark, and on the
            // light theme's warm ground that white mark disappears entirely.
            Image(
                painter = painterResource(R.drawable.splash_icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.size(112.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            ShimmerText(
                text = "Hazel",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                )
            )
        }
    }
}

/**
 * Text with a bright band travelling through it.
 *
 * The gradient is applied to the glyphs themselves rather than drawn over them, so the
 * highlight follows the letterforms instead of sweeping across a rectangle around them.
 * The base colour is dimmed so the band has something to stand out against; at full
 * brightness there would be nothing to see moving.
 */
@Composable
fun ShimmerText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    durationMillis: Int = SWEEP_DURATION_MS
) {
    val base = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
    val highlight = MaterialTheme.colorScheme.onBackground

    val transition = rememberInfiniteTransition(label = "textShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "textShimmerSweep"
    )

    // The gradient is sized in pixels, so it is expressed as a multiple of the text size
    // and swept from fully before the text to fully past it.
    val span = with(androidx.compose.ui.platform.LocalDensity.current) {
        style.fontSize.toPx() * text.length
    }
    val band = span * 0.45f
    val start = -band + (span + band * 2f) * progress

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to base,
                    0.5f to highlight,
                    1f to base
                ),
                start = Offset(start, 0f),
                end = Offset(start + band, 0f)
            )
        )
    )
}
