package com.hazel.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A heavier download mark than the stock one: a rounded arrow dropping into an open tray,
 * drawn in thick round-capped strokes so it stays legible when filled over busy artwork.
 */
private val DownloadGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "HazelDownload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Shaft and head
            moveTo(12f, 3f)
            lineTo(12f, 14.5f)
            moveTo(7f, 9.8f)
            lineTo(12f, 14.8f)
            lineTo(17f, 9.8f)
            // Tray
            moveTo(4f, 15.5f)
            lineTo(4f, 17.8f)
            quadTo(4f, 20.6f, 6.8f, 20.6f)
            lineTo(17.2f, 20.6f)
            quadTo(20f, 20.6f, 20f, 17.8f)
            lineTo(20f, 15.5f)
        }
    }.build()
}

/**
 * The download glyph, filling from the bottom up as the download does.
 *
 * A faint copy of the glyph is the empty vessel; a cyan-to-blue liquid is drawn over it, clipped
 * below a level that follows [progress]. The liquid keeps its own colours rather than the theme
 * accent so it stays readable on any artwork, only shifting brightness with the theme. While
 * [flowing], two out-of-step waves roll across the surface and the level bobs gently, so a
 * download that is moving reads as moving even between progress lines; a held one sits still,
 * dimmed, at whatever it had reached.
 */
@Composable
fun FillingDownloadIcon(
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    flowing: Boolean = true
) {
    val level by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 220f),
        label = "downloadFill"
    )
    val transition = rememberInfiniteTransition(label = "downloadWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1100, easing = LinearEasing)),
        label = "downloadWavePhase"
    )
    val slowPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1900, easing = LinearEasing)),
        label = "downloadWaveSlowPhase"
    )
    val swell by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing)),
        label = "downloadWaveSwell"
    )
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = if (dark) Color(0xFF7CF3FF) else Color(0xFF4FE3F5)
    val bottom = if (dark) Color(0xFF2E8BFF) else Color(0xFF1565E8)
    val fillAlpha = if (flowing) 1f else 0.6f
    val frontPath = remember { Path() }
    val backPath = remember { Path() }

    Box(modifier = modifier) {
        Icon(
            DownloadGlyph,
            contentDescription = null,
            // Bright enough to read as the outline still to fill on any artwork.
            tint = tint.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxSize()
        )
        Icon(
            DownloadGlyph,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    if (level <= 0f) return@drawWithContent
                    val brush = Brush.verticalGradient(
                        listOf(top.copy(alpha = fillAlpha), bottom.copy(alpha = fillAlpha))
                    )
                    if (level >= 1f) {
                        drawContent()
                        drawRect(brush, blendMode = BlendMode.SrcIn)
                        return@drawWithContent
                    }
                    val bob = if (flowing) 0.8.dp.toPx() * sin(2f * PI.toFloat() * swell) else 0f
                    val surface = size.height * (1f - level) + bob
                    val amplitude = if (flowing) 2.dp.toPx() else 0f
                    fun wave(path: Path, ph: Float, amp: Float, cycles: Float) {
                        path.reset()
                        path.moveTo(0f, size.height)
                        val step = size.width / 24f
                        var x = 0f
                        while (x <= size.width + step) {
                            val y = surface + amp * sin(2f * PI.toFloat() * (x / size.width * cycles + ph))
                            path.lineTo(x, y)
                            x += step
                        }
                        path.lineTo(size.width, size.height)
                        path.close()
                    }
                    // A lighter wave behind, rolling the other way, gives the surface depth.
                    wave(backPath, -slowPhase, amplitude * 0.8f, 1.3f)
                    clipPath(backPath) {
                        this@drawWithContent.drawContent()
                        drawRect(top.copy(alpha = 0.55f * fillAlpha), blendMode = BlendMode.SrcIn)
                    }
                    wave(frontPath, phase, amplitude, 1f)
                    clipPath(frontPath) {
                        this@drawWithContent.drawContent()
                        drawRect(brush, blendMode = BlendMode.SrcIn)
                    }
                }
        )
    }
}
