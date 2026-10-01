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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.theme.SizeBadgeContainer
import kotlin.math.cos
import kotlin.math.max
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
 * The quick pass, for skeletons that stand in for a list the user is waiting on with the
 * sheet already open: a shorter sweep and a narrow, hard-edged band, so it reads as a
 * crisp glint moving fast rather than a slow wash.
 */
private const val FAST_SWEEP_DURATION_MS = 650
private const val SHARP_FADE_START = 0.42f
private const val SHARP_FADE_END = 0.58f

/**
 * The fill of a placeholder block. A mid grey rather than a theme colour: at rest it is
 * faint on dark and light surfaces alike, and at full strength under the band it stands
 * clear of both.
 */
private val BLOCK_GREY = Color(0xFFA6A6A6)

/**
 * The size badge's blue, lifted. The badge itself is dark enough to carry pale text; at the
 * skeleton's resting opacity that would all but vanish, so the block takes a lighter step
 * of the same hue.
 */
private val SIZE_BLOCK_BLUE = lerp(SizeBadgeContainer, Color(0xFF8FB8F0), 0.55f)

/**
 * Drives one sweep for everything inside it. Place it around a whole skeleton.
 *
 * [fast] swaps the slow soft wash for the quick, sharp pass.
 */
@Composable
fun ShimmerHost(
    modifier: Modifier = Modifier,
    fast: Boolean = false,
    content: @Composable () -> Unit
) {
    val fadeStart = if (fast) SHARP_FADE_START else BAND_FADE_START
    val fadeEnd = if (fast) SHARP_FADE_END else BAND_FADE_END
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (fast) FAST_SWEEP_DURATION_MS else SWEEP_DURATION_MS,
                easing = LinearEasing
            ),
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
                            fadeStart to resting,
                            0.5f to Color.Black,
                            fadeEnd to resting,
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

/**
 * Paints the receiver as a placeholder block that a [ShimmerHost] sweep passes over.
 *
 * [color] lets a skeleton echo the colours of what it stands in for. The host's sweep works
 * on opacity, so a coloured block rests faint and lights up in its own colour.
 */
fun Modifier.shimmerBlock(
    shape: Shape = RoundedCornerShape(6.dp),
    color: Color = BLOCK_GREY
): Modifier = this
    .clip(shape)
    .background(color)

/** How long the glint takes to run across a [GlintHost], and how bright it is at its core. */
private const val GLINT_SWEEP_MS = 900
private const val GLINT_ALPHA = 0.14f

/**
 * The sideways glint, for a single control rather than a column of them: quicker, and a
 * little brighter, since it crosses a short wide shape in one pass and has to read at once.
 */
private const val GLINT_ACROSS_SWEEP_MS = 850
private const val GLINT_ACROSS_ALPHA = 0.22f

/**
 * Real content that is still filling in: everything inside is drawn as it is, at full
 * strength and still tappable, and a narrow glint runs across it on a loop for as long as
 * [active] holds.
 *
 * By default the glint runs top to bottom, the way a rail or a list is read. [across] turns
 * it into a slanted band running left to right, for one wide control such as the quality
 * row, where it stands in for a progress line under it.
 *
 * The glint is laid only over what the content painted (its blocks, not the gaps between
 * them), and in the theme's own text colour, so it lightens a dark surface and darkens a
 * light one. Its position is read while drawing, so the loop never recomposes the content,
 * and once [active] is false there is no animation running at all.
 */
@Composable
fun GlintHost(
    active: Boolean,
    modifier: Modifier = Modifier,
    across: Boolean = false,
    content: @Composable () -> Unit
) {
    if (!active) {
        Box(modifier = modifier) { content() }
        return
    }

    val transition = rememberInfiniteTransition(label = "glint")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (across) GLINT_ACROSS_SWEEP_MS else GLINT_SWEEP_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "glintSweep"
    )
    val glint = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (across) GLINT_ACROSS_ALPHA else GLINT_ALPHA
    )

    Box(
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()

                val stops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to glint,
                    1f to Color.Transparent
                )

                // Each pass starts and ends wholly outside the content, so it leaves a
                // moment of rest before the next.
                val brush = if (across) {
                    val band = max(size.height * 1.2f, size.width * 0.28f)
                    val lean = size.height * 0.45f
                    val travel = size.width + band * 2f
                    val centre = -band + travel * progress.value
                    Brush.linearGradient(
                        colorStops = stops,
                        start = Offset(centre - band / 2f, 0f),
                        end = Offset(centre + band / 2f, lean)
                    )
                } else {
                    val band = size.width * 1.4f
                    val travel = size.height + band * 2f
                    val centre = -band + travel * progress.value
                    Brush.linearGradient(
                        colorStops = stops,
                        start = Offset(0f, centre - band / 2f),
                        end = Offset(size.width * 0.35f, centre + band / 2f)
                    )
                }
                drawRect(brush = brush, blendMode = BlendMode.SrcAtop)
            }
    ) {
        content()
    }
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
 *
 * Coloured the way a real row is, so the skeleton reads as rows arriving rather than as
 * grey bars: the container block in the accent, the codec badge neutral, and the size badge
 * in its own fixed blue.
 */
@Composable
fun FormatRowShimmer(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 52.dp)
                .shimmerBlock(RoundedCornerShape(10.dp), color = accent)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(16.dp).shimmerBlock())
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Box(modifier = Modifier.width(48.dp).height(18.dp).shimmerBlock())
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(18.dp)
                        .shimmerBlock(color = SIZE_BLOCK_BLUE)
                )
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

/** A stack of [FormatRowShimmer]s sharing one quick, sharp sweep. */
@Composable
fun FormatListShimmer(rows: Int = 5, modifier: Modifier = Modifier) {
    ShimmerHost(modifier = modifier.fillMaxWidth(), fast = true) {
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(rows) { FormatRowShimmer() }
        }
    }
}

/**
 * How long the band takes to cross a [ShimmerLabel], and how wide it is. Quick and narrow,
 * so a heading that is waiting reads as busy rather than idle.
 */
private const val TEXT_SWEEP_MS = 1000
private val TEXT_BAND_WIDTH = 90.dp

/**
 * Words with light running across them: the letters rest dim and a bright band sweeps
 * through them from left to right, over and over. For a heading that is waiting on
 * something, so it says what is happening ("Fetching") instead of standing in as a blank
 * block.
 *
 * The band is painted into the letters only (the text is drawn, then the gradient is kept
 * where the text is), so nothing shows between or around them, and it is read while
 * drawing, so the sweep never recomposes the text.
 */
@Composable
fun ShimmerLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null
) {
    val transition = rememberInfiniteTransition(label = "shimmerText")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TEXT_SWEEP_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTextSweep"
    )
    val resting = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val lit = MaterialTheme.colorScheme.onSurface

    Text(
        text,
        style = style,
        fontWeight = fontWeight,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val band = TEXT_BAND_WIDTH.toPx()
                // Starts wholly before the first letter and ends wholly past the last, so
                // each pass leaves the word resting for a moment before the next.
                val centre = -band + (size.width + 2f * band) * progress
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to resting,
                        0.5f to lit,
                        1f to resting,
                        startX = centre - band / 2f,
                        endX = centre + band / 2f
                    ),
                    blendMode = BlendMode.SrcIn
                )
            }
    )
}

/** Single placeholder line, for a field whose value has not arrived yet. */
@Composable
fun LineShimmer(width: Dp, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(width).height(height).shimmerBlock())
}
