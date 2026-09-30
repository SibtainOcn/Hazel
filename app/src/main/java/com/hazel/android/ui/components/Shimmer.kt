package com.hazel.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Placeholder skeletons with one highlight travelling across them.
 *
 * The skeleton is drawn as it is, then masked: everything sits at [RESTING_ALPHA], and a
 * soft band as wide as the skeleton sweeps across it at full strength. Because the band
 * changes opacity rather than adding light, it shows the same in either theme and on any
 * colour of block, and the gaps between blocks stay empty since there is nothing there to
 * reveal. The sweep belongs to the whole skeleton rather than to each block, so a list of
 * placeholders reads as one surface catching the light instead of rows flickering apart.
 *
 * A skeleton is wrapped in [ShimmerHost], and its placeholders use [shimmerBlock].
 */
private const val SWEEP_DURATION_MS = 1000

/** How visible the skeleton is between passes of the band. */
private const val RESTING_ALPHA = 0.3f

/** Lean of the band, in degrees off vertical. */
private const val SWEEP_TILT_DEGREES = 20.0

/**
 * Where the band's edges fall across the host's width: its full-strength core sits at the
 * centre and it fades out over the quarter on either side.
 */
private const val BAND_FADE_START = 0.25f
private const val BAND_FADE_END = 0.75f

/**
 * The fill of a placeholder block. A mid grey rather than a theme colour: at rest it is
 * faint on dark and light surfaces alike, and at full strength under the band it stands
 * clear of both.
 */
private val BLOCK_GREY = Color(0xFFA6A6A6)

/** The processing sweep is narrower than the skeleton one, so it reads as a glint. */
private const val SHARP_BAND_WIDTH = 0.17f

/** How long the processing glint takes to cross, and its whole cycle including the rest. */
private const val GLINT_TRAVEL_MS = 1150
private const val GLINT_CYCLE_MS = 2050

/** Drives one sweep for everything inside it. Place it around a whole skeleton. */
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

    Box(
        modifier = modifier
            // The mask needs a layer of its own: it keeps the skeleton's alpha, and only
            // what this host drew, rather than punching through to whatever is behind it.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()

                val radians = Math.toRadians(SWEEP_TILT_DEGREES)
                val axisX = cos(radians).toFloat()
                val axisY = sin(radians).toFloat()

                // The band starts fully before the left edge and ends fully past the right,
                // counting the extra ground the tilt adds over the host's height.
                val span = size.width
                val travel = span + tan(radians).toFloat() * size.height
                val offset = -travel + 2f * travel * progress

                val start = Offset(offset, 0f)
                val end = Offset(offset + span * axisX, span * axisY)

                val resting = Color.Black.copy(alpha = RESTING_ALPHA)
                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to resting,
                            BAND_FADE_START to resting,
                            0.5f to Color.Black,
                            BAND_FADE_END to resting,
                            1f to resting
                        ),
                        start = start,
                        end = end
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        content()
    }
}

/** Paints the receiver as a placeholder block that a [ShimmerHost] sweep passes over. */
fun Modifier.shimmerBlock(shape: Shape = RoundedCornerShape(6.dp)): Modifier = this
    .clip(shape)
    .background(BLOCK_GREY)

/**
 * A one-shot luminous shine sweep that passes across an element (such as the searchbar)
 * to indicate refresh or newly arrived results.
 *
 * @param progress 0f to 1f animation progress.
 * @param shape Pill or rounded rectangle shape of the container.
 */
fun Modifier.refreshShine(progress: Float, shape: Shape = RoundedCornerShape(26.dp)): Modifier = this
    .clip(shape)
    .drawWithContent {
        drawContent()

        if (progress <= 0f || progress >= 1f) return@drawWithContent

        val radians = Math.toRadians(SWEEP_TILT_DEGREES).toFloat()
        val axisX = cos(radians)
        val axisY = sin(radians)

        val bandWidth = size.width * 0.35f
        val reach = size.width + kotlin.math.abs(axisY) * size.height + bandWidth * 2f
        val centreX = -bandWidth + reach * progress
        val centreY = size.height / 2f

        fun axis(width: Float) = Offset(
            centreX - axisX * width / 2f,
            centreY - axisY * width / 2f
        ) to Offset(
            centreX + axisX * width / 2f,
            centreY + axisY * width / 2f
        )

        // Soft halo
        val (haloStart, haloEnd) = axis(bandWidth * 2.2f)
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.12f),
                    1f to Color.Transparent
                ),
                start = haloStart,
                end = haloEnd
            )
        )

        // Core bright sheen
        val (coreStart, coreEnd) = axis(bandWidth)
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.35f to Color.White.copy(alpha = 0.05f),
                    0.5f to Color.White.copy(alpha = 0.60f),
                    0.65f to Color.White.copy(alpha = 0.05f),
                    1f to Color.Transparent
                ),
                start = coreStart,
                end = coreEnd
            )
        )
    }

/**
 * Stands in for the media card while a link is being read, in the card's own 16:9 shape:
 * title and author along the top, duration and state badges along the bottom, so nothing
 * moves when the real card takes its place.
 */
@Composable
fun MediaCardShimmer(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(10.dp)
                    .shimmerBlock(RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(10.dp)
                    .shimmerBlock(RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(10.dp)
                    .shimmerBlock(RoundedCornerShape(5.dp))
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .width(46.dp)
                .height(18.dp)
                .shimmerBlock(RoundedCornerShape(5.dp))
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .width(56.dp)
                .height(18.dp)
                .shimmerBlock(RoundedCornerShape(5.dp))
        )
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

    // The sweep crosses, then waits. A band that runs on a loop with no gap reads as a
    // spinner and stops being noticed; one that passes and leaves the artwork alone for a
    // moment reads as light moving across a surface, and the pause is what gives the next
    // pass something to arrive against.
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = GLINT_CYCLE_MS
                0f at 0 using FastOutSlowInEasing
                1f at GLINT_TRAVEL_MS
                1f at GLINT_CYCLE_MS
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "processingSweep"
    )

    Box(
        modifier = modifier.drawBehind {
            val bandWidth = size.width * SHARP_BAND_WIDTH

            // The band leans rather than standing upright. A vertical wipe reads as a
            // progress bar lying on its side; a raked one reads as a reflection, which is
            // the difference between the surface looking busy and looking lit.
            val radians = Math.toRadians(SWEEP_TILT_DEGREES).toFloat()
            val axisX = cos(radians)
            val axisY = sin(radians)

            // Travel is measured along the tilt, and overshoots at both ends so the band is
            // fully clear of the artwork before the cycle restarts.
            val reach = size.width + kotlin.math.abs(axisY) * size.height + bandWidth * 2f
            val centreX = -bandWidth + reach * progress
            val centreY = size.height / 2f

            fun axis(width: Float) = Offset(
                centreX - axisX * width / 2f,
                centreY - axisY * width / 2f
            ) to Offset(
                centreX + axisX * width / 2f,
                centreY + axisY * width / 2f
            )

            // Two passes make the sheen. A broad halo lifts the whole area the band is
            // crossing, and a narrow core sits inside it as the highlight proper. One band
            // alone is either soft and muddy or hard and cheap; the pair reads as depth.
            val (haloStart, haloEnd) = axis(bandWidth * 2.6f)
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.20f),
                        0.5f to Color.White.copy(alpha = 0.14f),
                        0.65f to Color.Black.copy(alpha = 0.20f),
                        1f to Color.Transparent
                    ),
                    start = haloStart,
                    end = haloEnd
                )
            )

            val (coreStart, coreEnd) = axis(bandWidth)
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.42f to Color.White.copy(alpha = 0.08f),
                        0.48f to Color.White.copy(alpha = 0.72f),
                        0.52f to Color.White.copy(alpha = 0.72f),
                        0.58f to Color.White.copy(alpha = 0.08f),
                        1f to Color.Transparent
                    ),
                    start = coreStart,
                    end = coreEnd
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
