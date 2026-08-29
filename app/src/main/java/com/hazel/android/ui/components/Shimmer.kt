package com.hazel.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder skeletons with a single highlight travelling across them.
 *
 * The sweep belongs to the whole skeleton, not to each block: one band crosses the group
 * from left to right, so the blocks read as one surface catching the light. Animating each
 * block on its own timeline is what makes a shimmer look wrong, because every block starts
 * its own sweep and the group flickers instead of gleaming.
 *
 * A skeleton is therefore wrapped in [ShimmerHost], and every placeholder inside it uses
 * [shimmerBlock]. Blocks measure where they sit inside the host and draw the part of the
 * band that falls across them.
 *
 * This is for skeletons only. Waits with no shape to stand in for, such as an update check,
 * keep [HazelLoadingIndicator].
 */
private const val SWEEP_DURATION_MS = 1200

/** Width of the moving highlight, as a fraction of the host's width. */
private const val BAND_WIDTH = 0.45f

/** The processing sweep is narrower and quicker, so it reads as a glint, not a wash. */
private const val SHARP_BAND_WIDTH = 0.20f
private const val SHARP_SWEEP_DURATION_MS = 900

/** Position of the band and the geometry it is measured against. */
private data class ShimmerSweep(
    val progress: Float,
    val hostLeft: Float,
    val hostWidth: Float
)

private val LocalShimmerSweep = staticCompositionLocalOf<ShimmerSweep?> { null }

/**
 * Drives one sweep for everything inside it. Place this around a whole skeleton rather than
 * around each placeholder.
 */
@Composable
fun ShimmerHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SWEEP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )

    var hostLeft by remember { mutableStateOf(0f) }
    var hostWidth by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            hostLeft = coordinates.positionInRoot().x
            hostWidth = coordinates.size.width.toFloat()
        }
    ) {
        CompositionLocalProvider(
            LocalShimmerSweep provides ShimmerSweep(progress, hostLeft, hostWidth)
        ) {
            content()
        }
    }
}

/**
 * Paints the receiver as a placeholder block that the host's sweep passes over.
 *
 * Outside a [ShimmerHost] the block still renders, as a plain resting fill, so a skeleton
 * used on its own never disappears.
 */
fun Modifier.shimmerBlock(shape: Shape = RoundedCornerShape(6.dp)): Modifier = composed {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)

    val sweep = LocalShimmerSweep.current
    var blockLeft by remember { mutableStateOf(0f) }

    this
        .clip(shape)
        .background(base)
        .onGloballyPositioned { blockLeft = it.positionInRoot().x }
        .drawWithContent {
            drawContent()

            if (sweep == null || sweep.hostWidth <= 0f) return@drawWithContent

            val bandWidth = sweep.hostWidth * BAND_WIDTH
            // The band starts fully off the host's left edge and finishes fully off its
            // right edge, which is what keeps the sweep continuous across the group.
            val travel = sweep.hostWidth + bandWidth * 2f
            val bandLeftInRoot = sweep.hostLeft - bandWidth + travel * sweep.progress

            // Translate into this block's own coordinates so the band lines up across
            // blocks that start at different offsets.
            val start = bandLeftInRoot - blockLeft

            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.5f to highlight,
                        1f to Color.Transparent
                    ),
                    start = Offset(start, 0f),
                    end = Offset(start + bandWidth, size.height)
                )
            )
        }
}

/**
 * Stands in for the media card while a link is being read. It matches the real card's
 * layout, so nothing moves when the metadata arrives.
 */
@Composable
fun MediaCardShimmer(modifier: Modifier = Modifier) {
    ShimmerHost(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .shimmerBlock(RoundedCornerShape(16.dp))
            )
            Column(modifier = Modifier.padding(top = 14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .shimmerBlock()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(12.dp)
                        .shimmerBlock()
                )
            }
        }
    }
}

/**
 * Stands in for a format row: container badge, headline, and the meta badges beneath it.
 * Used inside the download sheet while the format list is still being resolved.
 */
@Composable
fun FormatRowShimmer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 52.dp)
                .shimmerBlock(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Box(modifier = Modifier.width(170.dp).height(16.dp).shimmerBlock())
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Box(modifier = Modifier.width(64.dp).height(14.dp).shimmerBlock())
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.width(52.dp).height(14.dp).shimmerBlock())
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(10.dp)
                .shimmerBlock()
        )
    }
}

/** A stack of [FormatRowShimmer]s sharing one sweep. */
@Composable
fun FormatListShimmer(rows: Int = 5, modifier: Modifier = Modifier) {
    ShimmerHost(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(rows) { FormatRowShimmer() }
        }
    }
}

/**
 * A single bright band sweeping across whatever this is laid over.
 *
 * Unlike the skeleton blocks this paints no resting fill, so the artwork underneath stays
 * visible and only the band moves over it. The band is narrow and its highlight rises and
 * falls sharply, which reads as a surface catching the light rather than as a placeholder
 * waiting to be filled — the download is finished at this point, and what is left is the
 * work the app is doing to the file.
 *
 * The bright core is flanked by a darker shoulder on both sides. A single white band
 * disappears over pale artwork, and a single dark one disappears over dark artwork; the
 * pair always leaves one half of it standing out. That is also what makes this readable in
 * either theme, since what the band crosses is the artwork rather than any app surface.
 */
@Composable
fun ProcessingShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "processing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHARP_SWEEP_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "processingSweep"
    )

    Box(
        modifier = modifier.drawBehind {
            val bandWidth = size.width * SHARP_BAND_WIDTH
            val travel = size.width + bandWidth * 2f
            val start = -bandWidth + travel * progress

            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.30f to Color.Black.copy(alpha = 0.28f),
                        0.44f to Color.White.copy(alpha = 0.10f),
                        0.50f to Color.White.copy(alpha = 0.60f),
                        0.56f to Color.White.copy(alpha = 0.10f),
                        0.70f to Color.Black.copy(alpha = 0.28f),
                        1f to Color.Transparent
                    ),
                    start = Offset(start, 0f),
                    end = Offset(start + bandWidth, size.height)
                )
            )
        }
    )
}

/** Single placeholder line, for a field whose value has not arrived yet. */
@Composable
fun LineShimmer(width: Dp, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(width).height(height).shimmerBlock())
}
