package com.hazel.android.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.theme.HazelSpark
import kotlin.math.hypot
import kotlin.math.max

internal const val SPLASH_TAG = "splash"

private const val BOLT_PATH = "M15.268,18.229C13.289,20.907 12.299,22.246 11.376,21.963C10.453,21.68 10.453,20.038 10.453,16.753L10.453,16.443C10.453,15.259 10.453,14.666 10.074,14.295L10.054,14.275C9.667,13.912 9.051,13.912 7.818,13.912C5.599,13.912 4.489,13.912 4.115,13.239C4.108,13.228 4.102,13.216 4.096,13.205C3.742,12.522 4.385,11.653 5.67,9.914L8.732,5.771C10.711,3.093 11.701,1.754 12.624,2.037C13.547,2.32 13.547,3.962 13.547,7.247V7.557C13.547,8.742 13.547,9.334 13.926,9.705L13.946,9.725C14.333,10.088 14.949,10.088 16.182,10.088C18.401,10.088 19.511,10.088 19.885,10.761C19.892,10.772 19.898,10.784 19.904,10.795C20.258,11.478 19.615,12.347 18.33,14.086"

private val StrokeStyle = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val SparkStroke = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val WarmCover = Color.Black.copy(alpha = 0.98f)
private val SparkBrush = Brush.linearGradient(HazelSpark, start = Offset(4f, 2f), end = Offset(20f, 22f))

/**
 * Bolt Trace on pure black: the bolt draws itself with a spark on the pen tip, the
 * wordmark rises in, then a circle opens from the bolt onto home.
 *
 * The clock steps per drawn frame and each step is capped, so a slow frame pauses the
 * splash instead of skipping part of it. Home is only asked for once the logo has
 * settled, and the circle opens only after home has drawn a frame.
 */
@Composable
fun SplashScreen(
    ready: Boolean,
    homeShown: Boolean,
    onWantHome: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val word = stringResource(R.string.app_name)
    val bolt = remember { PathParser().parsePathString(BOLT_PATH).toPath() }
    val measure = remember(bolt) { PathMeasure().apply { setPath(bolt, false) } }
    val length = remember(measure) { measure.length }
    val segment = remember { Path() }
    val clock = remember { mutableFloatStateOf(0f) }
    val centre = remember { floatArrayOf(Float.NaN, Float.NaN) }

    val canReveal by rememberUpdatedState(ready && homeShown)
    val wantHome by rememberUpdatedState(onWantHome)
    val finished by rememberUpdatedState(onFinished)
    val rise = with(LocalDensity.current) { 22.dp.toPx() }

    val header = MaterialTheme.typography.titleLarge
    val wordStyle = remember(header) {
        val grow = 38f / header.fontSize.value
        header.copy(fontSize = header.fontSize * grow, lineHeight = header.lineHeight * grow, fontWeight = FontWeight.Bold, color = Color.White)
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        var last = withFrameNanos { it }
        var askedHome = false
        while (clock.floatValue < SplashTimeline.TOTAL_MS) {
            val now = withFrameNanos { it }
            val step = ((now - last) / 1_000_000f).coerceIn(0f, SplashTimeline.MAX_STEP_MS)
            last = now
            var t = clock.floatValue + step
            if (!askedHome && t >= SplashTimeline.HOME_AT_MS) {
                askedHome = true
                wantHome()
            }
            if (!canReveal) t = t.coerceAtMost(SplashTimeline.REVEAL_AT_MS)
            clock.floatValue = t.coerceAtMost(SplashTimeline.TOTAL_MS)
        }
        finished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(SPLASH_TAG)
            .preferredFrameRate(FrameRateCategory.High)
            .drawWithContent {
                val open = SplashTimeline.reveal(clock.floatValue)
                // Once home exists the cover is a hair short of opaque, so the GPU actually draws
                // home behind it and is warmed up by the time the circle opens.
                val cover = if (clock.floatValue >= SplashTimeline.HOME_AT_MS) WarmCover else Color.Black
                if (open <= 0f) {
                    drawRect(cover)
                    drawContent()
                    return@drawWithContent
                }
                // A black ring around the opening, not a clip: a stroked circle is cheap to
                // draw, where a cut-out clip forces a full-screen mask on every frame.
                val c = Offset(
                    if (centre[0].isNaN()) size.width / 2f else centre[0],
                    if (centre[1].isNaN()) size.height / 2f else centre[1]
                )
                val far = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y)) + 2f
                val hole = far * open
                val ring = far - hole
                if (ring > 0f) drawCircle(cover, radius = hole + ring / 2f, center = c, style = Stroke(ring))
                drawContent()
            }
            .pointerInput(Unit) {
                detectTapGestures { clock.floatValue = max(clock.floatValue, SplashTimeline.REVEAL_AT_MS) }
            }
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val t = clock.floatValue
                val s = SplashTimeline.exitScale(t)
                scaleX = s
                scaleY = s
                alpha = SplashTimeline.exitAlpha(t)
            }
        ) {
            Canvas(
                modifier = Modifier
                    .size(96.dp)
                    .onGloballyPositioned {
                        val b = it.boundsInRoot()
                        centre[0] = b.center.x
                        centre[1] = b.center.y
                    }
            ) {
                val t = clock.floatValue
                val p = SplashTimeline.trace(t)
                if (p <= 0f) return@Canvas
                scale(size.minDimension / 24f, pivot = Offset.Zero) {
                    segment.reset()
                    measure.getSegment(0f, length * p, segment, true)
                    drawPath(segment, Color.White, style = StrokeStyle)
                    val glow = SplashTimeline.spark(t)
                    if (glow > 0f) {
                        segment.reset()
                        measure.getSegment((p - SplashTimeline.SPARK_SPAN).coerceAtLeast(0f) * length, length * p, segment, true)
                        drawPath(segment, SparkBrush, alpha = glow, style = SparkStroke)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row {
                word.forEachIndexed { i, ch ->
                    BasicText(
                        text = ch.toString(),
                        style = wordStyle,
                        modifier = Modifier.graphicsLayer {
                            val v = SplashTimeline.letter(i, clock.floatValue)
                            alpha = v
                            translationY = (1f - v) * rise
                        }
                    )
                }
            }
        }
    }
}

/** Every pose of the splash as a function of its clock in milliseconds. */
internal object SplashTimeline {
    const val TOTAL_MS = 2150f
    const val MAX_STEP_MS = 24f
    const val HOME_AT_MS = 1300f
    const val REVEAL_AT_MS = 1650f
    const val SPARK_SPAN = 0.16f
    private const val TRACE_MS = 1000f
    private const val SPARK_FADE_MS = 240f
    private const val LETTER_AT_MS = 560f
    private const val LETTER_STAGGER_MS = 50f
    private const val LETTER_MS = 520f
    private const val REVEAL_MS = TOTAL_MS - REVEAL_AT_MS

    private val Draw = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
    private val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    private fun clamp(x: Float) = x.coerceIn(0f, 1f)
    private fun smooth(x: Float) = x * x * (3f - 2f * x)
    private fun revealP(t: Float) = clamp((t - REVEAL_AT_MS) / REVEAL_MS)

    fun trace(t: Float) = Draw.transform(clamp(t / TRACE_MS))
    fun spark(t: Float) = 1f - clamp((t - TRACE_MS) / SPARK_FADE_MS)
    fun letter(i: Int, t: Float) = Decelerate.transform(clamp((t - LETTER_AT_MS - i * LETTER_STAGGER_MS) / LETTER_MS))

    /** How far the circle onto home has opened, 0 to 1 of the screen. */
    fun reveal(t: Float) = Accelerate.transform(revealP(t))
    fun exitScale(t: Float) = 1f + 0.12f * smooth(revealP(t))
    fun exitAlpha(t: Float) = 1f - smooth(clamp(revealP(t) * 1.6f))
}
