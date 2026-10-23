package com.hazel.android.ui.theme

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.remember
import androidx.compose.material3.dynamicDarkColorScheme
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

val LocalGlass = staticCompositionLocalOf { false }
val LocalDarkTheme = staticCompositionLocalOf { true }

/** Glass over another app: an opaque base with only a faint tint, so nothing behind shows through. */
val LocalGlassSolid = staticCompositionLocalOf { false }

enum class GlassStyle(val key: String) {
    AURORA("aurora"), OCEAN("ocean"), ACCENT("accent"), WALLPAPER("wallpaper"),
    SUNSET("sunset"), NEBULA("nebula"), FOREST("forest"), MONO("mono");

    companion object {
        val DEFAULT = FOREST

        fun fromKey(key: String?): GlassStyle = entries.firstOrNull { it.key == key } ?: DEFAULT

        val available: List<GlassStyle>
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) entries else entries - WALLPAPER
    }
}

private class GlowSpot(val x: Float, val y: Float, val radius: Float, val strength: Float)

private val GlowSpots = listOf(
    GlowSpot(0.10f, 0.06f, 0.78f, 1f),
    GlowSpot(1.00f, 0.36f, 0.66f, 0.8f),
    GlowSpot(0.50f, 1.02f, 0.62f, 0.7f),
    GlowSpot(0.92f, 0.90f, 0.38f, 0.45f),
)

private val AuroraDark = listOf(Color(0xFF16B8E6), Color(0xFF3D5BFF), Color(0xFFFF4FA8), Color(0xFFFFAE2C))
private val AuroraLight = listOf(Color(0xFF7FDCFF), Color(0xFF9DB0FF), Color(0xFFFFB3D6), Color(0xFFFFD59A))
private val SunsetDark = listOf(Color(0xFFFF6B35), Color(0xFFFF2E63), Color(0xFFA239CA), Color(0xFFFFC93C))
private val OceanDark = listOf(Color(0xFF00C9A7), Color(0xFF0077FF), Color(0xFF00B4D8), Color(0xFF7AE7C7))
private val NebulaDark = listOf(Color(0xFF8B5CF6), Color(0xFF4F46E5), Color(0xFFE040FB), Color(0xFF38BDF8))
private val ForestDark = listOf(Color(0xFF10B981), Color(0xFF15803D), Color(0xFF84CC16), Color(0xFF2DD4BF))
private val MonoDark = listOf(Color(0xFF9AA0AA), Color(0xFF6B7280), Color(0xFFC9CED6), Color(0xFF4B5563))

private fun fixedPalette(style: GlassStyle): List<Color>? = when (style) {
    GlassStyle.SUNSET -> SunsetDark
    GlassStyle.OCEAN -> OceanDark
    GlassStyle.NEBULA -> NebulaDark
    GlassStyle.FOREST -> ForestDark
    GlassStyle.MONO -> MonoDark
    GlassStyle.AURORA, GlassStyle.ACCENT, GlassStyle.WALLPAPER -> null
}

val LocalGlassPalette = staticCompositionLocalOf { AuroraDark }

private fun shiftHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun List<Color>.pastel() = map { lerp(it, Color.White, 0.45f) }

@Composable
fun glassPalette(style: GlassStyle, dark: Boolean, accent: Color): List<Color> {
    val context = LocalContext.current
    return remember(style, dark, accent, context) {
        val base = when (style) {
            GlassStyle.AURORA -> return@remember if (dark) AuroraDark else AuroraLight
            GlassStyle.ACCENT -> listOf(accent, shiftHue(accent, 30f), shiftHue(accent, -35f), shiftHue(accent, 160f))
            GlassStyle.SUNSET, GlassStyle.OCEAN, GlassStyle.NEBULA, GlassStyle.FOREST, GlassStyle.MONO ->
                fixedPalette(style) ?: AuroraDark
            GlassStyle.WALLPAPER -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val s = dynamicDarkColorScheme(context)
                listOf(s.primary, s.tertiary, s.secondary, s.inversePrimary)
            } else {
                return@remember if (dark) AuroraDark else AuroraLight
            }
        }
        if (dark) base else base.pastel()
    }
}

fun glassPreview(style: GlassStyle): List<Color> = fixedPalette(style) ?: AuroraDark

val GlassDarkBase = Color(0xFF000000)
val GlassLightBase = Color(0xFFF6F7FB)

// A bell-shaped fade that reaches zero with no slope, so a glow has no visible rim.
private val FalloffStops = floatArrayOf(0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f, 1f)
private val FalloffLevels = floatArrayOf(1f, 0.94f, 0.79f, 0.58f, 0.33f, 0.13f, 0.02f, 0f)
private const val GlowReach = 1.15f
private const val MaxStretch = 1.5f

private class AuroraSpot(val shader: Shader, val x: Float, val y: Float, val radius: Float, val stretch: Float)

private class AuroraLayer(val spots: List<AuroraSpot>, val wash: Shader?) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
}

private fun auroraLayer(
    palette: List<Color>,
    dark: Boolean,
    scale: Float,
    area: Size,
    top: Float = 0f,
    wash: Boolean = false,
): AuroraLayer {
    val opacity = (if (dark) 0.4f else 0.52f) * scale
    // Tall areas get oval glows, so a phone screen is not dotted with round discs.
    val stretch = (area.height / area.width).coerceIn(1f, MaxStretch)
    val spots = GlowSpots.zip(palette) { g, color ->
        val a = opacity * g.strength
        val x = area.width * g.x
        val y = top + area.height * g.y
        val r = area.width * g.radius * GlowReach
        val colors = IntArray(FalloffLevels.size) { color.copy(alpha = a * FalloffLevels[it]).toArgb() }
        AuroraSpot(RadialGradient(x, y, r, colors, FalloffStops, Shader.TileMode.CLAMP), x, y, r, stretch)
    }
    val washShader = if (wash && palette.size >= 3) {
        val w = opacity * 0.18f
        LinearGradient(
            0f, top, 0f, top + area.height,
            intArrayOf(
                palette[0].copy(alpha = w).toArgb(),
                Color.Transparent.toArgb(),
                palette[2].copy(alpha = w * 0.8f).toArgb(),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    } else null
    return AuroraLayer(spots, washShader)
}

private fun DrawScope.drawAurora(layer: AuroraLayer) = drawIntoCanvas { canvas ->
    val native = canvas.nativeCanvas
    val paint = layer.paint
    layer.wash?.let {
        paint.shader = it
        native.drawRect(0f, 0f, size.width, size.height, paint)
    }
    for (s in layer.spots) {
        paint.shader = s.shader
        native.save()
        native.scale(1f, s.stretch, s.x, s.y)
        native.drawCircle(s.x, s.y, s.radius, paint)
        native.restore()
    }
    paint.shader = null
}

fun Modifier.auroraBackground(dark: Boolean, palette: List<Color>): Modifier = drawWithCache {
    val layer = auroraLayer(palette, dark, 1f, size, wash = true)
    val base = if (dark) GlassDarkBase else GlassLightBase
    onDrawBehind {
        drawRect(base)
        drawAurora(layer)
    }
}

@Composable
fun Modifier.glassScreen(): Modifier =
    if (LocalGlass.current) auroraBackground(LocalDarkTheme.current, LocalGlassPalette.current) else this

private val SheetBleed = 64.dp

private fun overlayScale(dark: Boolean, solid: Boolean) = when {
    solid -> 0.22f
    dark -> 0.6f
    else -> 0.7f
}

private fun overlayBase(dark: Boolean, solid: Boolean): Color = when {
    solid -> if (dark) GlassDarkBase else GlassLightBase
    dark -> Color(0xFF07080C).copy(alpha = 0.94f)
    else -> GlassLightBase.copy(alpha = 0.95f)
}

@Composable
fun tonalFill(alpha: Float): Color {
    if (!LocalGlass.current) return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    val ink = if (LocalDarkTheme.current) Color.White else Color(0xFF0F172A)
    return ink.copy(alpha = 0.06f + alpha * 0.1f)
}

@Composable
fun opaqueSurface(): Color {
    val surface = MaterialTheme.colorScheme.surface
    if (!LocalGlass.current) return surface
    return surface.compositeOver(if (LocalDarkTheme.current) GlassDarkBase else GlassLightBase)
}

@Composable
fun glassSheetColor(containerColor: Color): Color {
    if (!LocalGlass.current) return containerColor
    return containerColor.compositeOver(overlayBase(LocalDarkTheme.current, LocalGlassSolid.current))
}

@Composable
fun Modifier.glassSheetGlow(): Modifier {
    if (!LocalGlass.current) return this
    val dark = LocalDarkTheme.current
    val scale = overlayScale(dark, LocalGlassSolid.current)
    val palette = LocalGlassPalette.current
    return drawWithCache {
        val bleed = SheetBleed.toPx()
        val layer = auroraLayer(palette, dark, scale, Size(size.width, size.height + bleed * 2), -bleed)
        onDrawBehind { drawAurora(layer) }
    }
}

@Composable
fun Modifier.glassBackdrop(shape: Shape, solid: Boolean = false, enabled: Boolean = LocalGlass.current): Modifier {
    if (!enabled) return this
    val dark = LocalDarkTheme.current
    val solidGlass = LocalGlassSolid.current
    val base = if (solid) (if (dark) GlassDarkBase else GlassLightBase) else overlayBase(dark, solidGlass)
    val scale = overlayScale(dark, solidGlass)
    val palette = LocalGlassPalette.current
    val edge = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.9f)
    return this
        .clip(shape)
        .drawWithCache {
            val layer = auroraLayer(palette, dark, scale, size)
            onDrawBehind {
                drawRect(base)
                drawAurora(layer)
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
        outline = Color(0xFF8E9099),
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
