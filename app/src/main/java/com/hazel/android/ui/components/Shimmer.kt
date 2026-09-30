package com.hazel.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Placeholder skeletons with a single highlight travelling across them.
 *
 * The sweep belongs to the whole skeleton, not to each block: one continuous diagonal band
 * crosses the host container at a 20° tilt, so all cards and elements read as one surface
 * catching the light simultaneously. Empty space between elements remains unaffected
 * via offscreen layer compositing.
 */
private const val SWEEP_DURATION_MS = 1000

/** Width of the moving highlight, as a fraction of the host's width. */
private const val BAND_WIDTH = 0.50f

/** The processing sweep is narrower than the skeleton one, so it reads as a glint. */
private const val SHARP_BAND_WIDTH = 0.17f

/** How long the band takes to cross, and how long the whole cycle runs including its rest. */
private const val SWEEP_TRAVEL_MS = 1000
private const val SWEEP_CYCLE_MS = 1800

/** Lean of the band, in degrees off vertical. */
private const val SWEEP_TILT_DEGREES = 20.0

/** Placeholder fill on the media card skeleton, which is dark in either theme. */
private val MEDIA_CARD_BLOCK = Color(0xFF3A3A44)

/**
 * Drives a unified diagonal shimmer sweep across all placeholder content inside it.
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

    Box(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()

                val cosTheta = 0.9396926f // cos(20°)
                val sinTheta = 0.34202014f // sin(20°)

                val bandWidth = size.width * BAND_WIDTH
                val reach = size.width * cosTheta + size.height * sinTheta
                val travel = reach + bandWidth * 2f
                val d = -bandWidth + travel * progress

                val startDist = d - bandWidth / 2f
                val endDist = d + bandWidth / 2f

                val coreHighlight = Color.White.copy(alpha = 0.40f)
                val warmShoulder = Color(0xFFE8DDD0).copy(alpha = 0.10f)

                drawRect(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f    to Color.Transparent,
                            0.20f to warmShoulder,
                            0.40f to coreHighlight.copy(alpha = coreHighlight.alpha * 0.35f),
                            0.50f to coreHighlight,
                            0.60f to coreHighlight.copy(alpha = coreHighlight.alpha * 0.35f),
                            0.80f to warmShoulder,
                            1f    to Color.Transparent
                        ),
                        start = Offset(startDist * cosTheta, startDist * sinTheta),
                        end = Offset(endDist * cosTheta, endDist * sinTheta)
                    ),
                    blendMode = BlendMode.SrcAtop
                )
            }
    ) {
        content()
    }
}

/**
 * Paints the receiver as a placeholder block that a [ShimmerHost] sweep passes over.
 *
 * The fill follows the theme, so a skeleton reads as a faint shape on a light sheet as well
 * as on a dark one. [color] overrides it for a skeleton drawn on a fixed dark surface, such
 * as the media card, which stands in for artwork under a dark scrim in either theme.
 */
fun Modifier.shimmerBlock(
    shape: Shape = RoundedCornerShape(6.dp),
    color: Color = Color.Unspecified
): Modifier = composed {
    val fill = color.takeOrElse { MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) }
    clip(shape).background(fill)
}

/** Card surface for skeleton cards, following the theme. */
fun Modifier.shimmerCard(shape: Shape = RoundedCornerShape(20.dp)): Modifier = composed {
    clip(shape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
}

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
        val axisX = kotlin.math.cos(radians)
        val axisY = kotlin.math.sin(radians)

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
 * Skeleton placeholder matching [MediaCard]'s 16:9 layout: overlaid title, author,
 * duration badge at bottom-start, and state tag at bottom-end.
 */
@Composable
fun MediaCardShimmer(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1A1E)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF262630),
                            0.35f to Color(0xFF1E1E26),
                            0.65f to Color(0xFF1A1A22),
                            1f to Color(0xFF141418)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0f to Color(0x0AFFFFFF),
                            0.5f to Color.Transparent,
                            1f to Color(0x08FFFFFF),
                            start = Offset.Zero,
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.52f),
                            0.35f to Color.Transparent,
                            0.70f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.58f)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Transparent)
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                0f to Color(0x18FFFFFF),
                                0.5f to Color(0x08FFFFFF),
                                1f to Color(0x14FFFFFF),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height)
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 12.dp, end = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(13.dp)
                        .shimmerBlock(RoundedCornerShape(4.dp), MEDIA_CARD_BLOCK)
                )
                Spacer(modifier = Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.52f)
                        .height(13.dp)
                        .shimmerBlock(RoundedCornerShape(4.dp), MEDIA_CARD_BLOCK)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(10.dp)
                        .shimmerBlock(RoundedCornerShape(3.dp), MEDIA_CARD_BLOCK)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(18.dp)
                        .shimmerBlock(RoundedCornerShape(4.dp), MEDIA_CARD_BLOCK)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(18.dp)
                        .shimmerBlock(RoundedCornerShape(4.dp), MEDIA_CARD_BLOCK)
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

    // The sweep crosses, then waits. A band that runs on a loop with no gap reads as a
    // spinner and stops being noticed; one that passes and leaves the artwork alone for a
    // moment reads as light moving across a surface, and the pause is what gives the next
    // pass something to arrive against.
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = SWEEP_CYCLE_MS
                0f at 0 using FastOutSlowInEasing
                1f at SWEEP_TRAVEL_MS
                1f at SWEEP_CYCLE_MS
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
            val axisX = kotlin.math.cos(radians)
            val axisY = kotlin.math.sin(radians)

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
