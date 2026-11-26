package com.hazel.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The accent as a soft tint for tonal fills. The container tones are near black on the dark
 * themes for every accent, so the fill is made from the accent itself. On the solid themes it
 * is mixed into a card tone; on glass it stays see-through like every other glass surface.
 * A bright accent (White, pastels) tints less, so it reads as a tone rather than a grey block.
 */
fun accentTint(primary: Color, dark: Boolean, glass: Boolean = false): Color {
    val strength = if (dark) 0.12f + 0.08f * (1f - primary.luminance()) else 0.14f
    val base = when {
        glass && dark -> Color.White.copy(alpha = 0.05f)
        glass -> Color.White.copy(alpha = 0.45f)
        dark -> DarkSurfaceContainerHigh
        else -> LightSurfaceContainerLow
    }
    return primary.copy(alpha = strength).compositeOver(base)
}

/** What sits on [accentTint]: the accent, lifted on the dark themes and deepened on the light ones so every accent reads. */
fun accentInk(primary: Color, dark: Boolean): Color =
    if (dark) lerp(primary, Color.White, 0.25f) else lerp(primary, Color.Black, 0.3f)

@Composable
@ReadOnlyComposable
fun accentTint(): Color = accentTint(
    MaterialTheme.colorScheme.primary,
    LocalDarkTheme.current,
    glass = LocalGlass.current && !LocalGlassSolid.current
)

/** [accentTint] that never lets anything show through, for marks drawn over a picture. */
@Composable
@ReadOnlyComposable
fun accentTintSolid(): Color = accentTint(MaterialTheme.colorScheme.primary, LocalDarkTheme.current)

@Composable
@ReadOnlyComposable
fun accentInk(): Color = accentInk(MaterialTheme.colorScheme.primary, LocalDarkTheme.current)
