package com.hazel.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * The accent as a soft, opaque tint for tonal fills. The container tones are near black on
 * the dark themes for every accent, so the fill is made from the accent itself, mixed into a
 * card tone so the glass glows behind it can't change how its content reads.
 */
fun accentTint(primary: Color, dark: Boolean): Color =
    primary.copy(alpha = if (dark) 0.22f else 0.14f)
        .compositeOver(if (dark) DarkSurfaceContainerHigh else LightSurfaceContainerLow)

/** What sits on [accentTint]: the accent, lifted on the dark themes and deepened on the light ones so every accent reads. */
fun accentInk(primary: Color, dark: Boolean): Color =
    if (dark) lerp(primary, Color.White, 0.25f) else lerp(primary, Color.Black, 0.3f)

@Composable
@ReadOnlyComposable
fun accentTint(): Color = accentTint(MaterialTheme.colorScheme.primary, LocalDarkTheme.current)

@Composable
@ReadOnlyComposable
fun accentInk(): Color = accentInk(MaterialTheme.colorScheme.primary, LocalDarkTheme.current)
