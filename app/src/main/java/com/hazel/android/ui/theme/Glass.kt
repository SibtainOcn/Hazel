package com.hazel.android.ui.theme

import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

val LocalGlass = staticCompositionLocalOf { false }
val LocalDarkTheme = staticCompositionLocalOf { true }

private data class Glow(val color: Color, val x: Float, val y: Float, val radius: Float, val strength: Float)

private val DarkGlows = listOf(
    Glow(Color(0xFF16B8E6), 0.10f, 0.06f, 0.78f, 1f),
    Glow(Color(0xFF3D5BFF), 1.00f, 0.36f, 0.66f, 0.8f),
    Glow(Color(0xFFFF4FA8), 0.50f, 1.02f, 0.62f, 0.7f),
    Glow(Color(0xFFFFAE2C), 0.92f, 0.90f, 0.38f, 0.45f),
)

private val LightGlows = listOf(
    Glow(Color(0xFF7FDCFF), 0.10f, 0.06f, 0.78f, 1f),
    Glow(Color(0xFF9DB0FF), 1.00f, 0.36f, 0.66f, 0.8f),
    Glow(Color(0xFFFFB3D6), 0.50f, 1.02f, 0.62f, 0.7f),
    Glow(Color(0xFFFFD59A), 0.92f, 0.90f, 0.38f, 0.45f),
)

val GlassDarkBase = Color(0xFF000000)
val GlassLightBase = Color(0xFFF6F7FB)

private class AuroraSpot(val brush: Brush, val center: Offset, val radius: Float)

private fun auroraSpots(dark: Boolean, scale: Float, area: Size, top: Float = 0f): List<AuroraSpot> {
    val opacity = (if (dark) 0.42f else 0.55f) * scale
    return (if (dark) DarkGlows else LightGlows).map { g ->
        val a = opacity * g.strength
        val center = Offset(area.width * g.x, top + area.height * g.y)
        val r = area.width * g.radius
        AuroraSpot(
            Brush.radialGradient(
                0f to g.color.copy(alpha = a),
                0.45f to g.color.copy(alpha = a * 0.55f),
                1f to Color.Transparent,
                center = center,
                radius = r,
            ),
            center,
            r,
        )
    }
}

private fun DrawScope.drawSpots(spots: List<AuroraSpot>) {
    for (s in spots) drawCircle(brush = s.brush, radius = s.radius, center = s.center)
}

fun Modifier.auroraBackground(dark: Boolean): Modifier = drawWithCache {
    val spots = auroraSpots(dark, 1f, size)
    val base = if (dark) GlassDarkBase else GlassLightBase
    onDrawBehind {
        drawRect(base)
        drawSpots(spots)
    }
}

@Composable
fun Modifier.glassScreen(): Modifier =
    if (LocalGlass.current) auroraBackground(LocalDarkTheme.current) else this

private val SheetBleed = 64.dp

private fun overlayScale(dark: Boolean) = if (dark) 0.6f else 0.7f

private fun overlayBase(dark: Boolean): Color =
    if (dark) Color(0xFF07080C).copy(alpha = 0.94f) else GlassLightBase.copy(alpha = 0.95f)

@Composable
fun opaqueSurface(): Color {
    val surface = MaterialTheme.colorScheme.surface
    if (!LocalGlass.current) return surface
    return surface.compositeOver(if (LocalDarkTheme.current) GlassDarkBase else GlassLightBase)
}

@Composable
fun glassSheetColor(containerColor: Color): Color {
    if (!LocalGlass.current) return containerColor
    return containerColor.compositeOver(overlayBase(LocalDarkTheme.current))
}

@Composable
fun Modifier.glassSheetGlow(): Modifier {
    if (!LocalGlass.current) return this
    val dark = LocalDarkTheme.current
    return drawWithCache {
        val bleed = SheetBleed.toPx()
        val spots = auroraSpots(dark, overlayScale(dark), Size(size.width, size.height + bleed * 2), -bleed)
        onDrawBehind { drawSpots(spots) }
    }
}

@Composable
fun Modifier.glassBackdrop(shape: Shape): Modifier {
    if (!LocalGlass.current) return this
    val dark = LocalDarkTheme.current
    val base = overlayBase(dark)
    val edge = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.9f)
    return this
        .clip(shape)
        .drawWithCache {
            val spots = auroraSpots(dark, overlayScale(dark), size)
            onDrawBehind {
                drawRect(base)
                drawSpots(spots)
            }
        }
        .border(1.dp, edge, shape)
}

fun ColorScheme.toGlass(dark: Boolean): ColorScheme = if (dark) {
    copy(
        background = Color.Transparent,
        surface = Color.White.copy(alpha = 0.05f),
        surfaceVariant = Color.White.copy(alpha = 0.07f),
        surfaceContainerLowest = Color.White.copy(alpha = 0.02f),
        surfaceContainerLow = Color.White.copy(alpha = 0.035f),
        surfaceContainer = Color(0xFF111318),
        surfaceContainerHigh = Color.White.copy(alpha = 0.085f),
        surfaceContainerHighest = Color.White.copy(alpha = 0.11f),
        outlineVariant = Color.White.copy(alpha = 0.09f),
    )
} else {
    copy(
        background = Color.Transparent,
        surface = Color.White.copy(alpha = 0.6f),
        surfaceVariant = Color(0xFF0F172A).copy(alpha = 0.05f),
        surfaceContainerLowest = Color.White.copy(alpha = 0.7f),
        surfaceContainerLow = Color.White.copy(alpha = 0.5f),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color.White.copy(alpha = 0.75f),
        surfaceContainerHighest = Color.White.copy(alpha = 0.85f),
        outlineVariant = Color(0xFF0F172A).copy(alpha = 0.08f),
    )
}
