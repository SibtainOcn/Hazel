package com.hazel.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun HazelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentName: String = "Cyan",
    content: @Composable () -> Unit
) {
    val accent = AccentColors.find { it.name == accentName } ?: AccentColors.first()

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent.dark,
            onPrimary = DarkBackground,
            primaryContainer = accent.containerDark,
            onPrimaryContainer = accent.dark,
            secondary = accent.dark.copy(alpha = 0.8f),
            tertiary = InfoBlue,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            error = ErrorRed,
            onError = DarkBackground,
        )
    } else {
        lightColorScheme(
            primary = accent.light,
            onPrimary = Color.White,
            primaryContainer = accent.containerLight,
            onPrimaryContainer = accent.light,
            secondary = accent.light.copy(alpha = 0.8f),
            tertiary = InfoBlue,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            // Named one by one rather than left to Material's defaults, which derive their
            // light container tones from a violet neutral and pulled the search field and
            // the navigation bar away from the rest of the screen.
            surfaceContainerLowest = LightSurfaceContainerLowest,
            surfaceContainerLow = LightSurfaceContainerLow,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHighest,
            surfaceBright = LightSurfaceBright,
            surfaceDim = LightSurfaceDim,
            inverseSurface = LightInverseSurface,
            inverseOnSurface = LightInverseOnSurface,
            error = ErrorRedLight,
            onError = Color.White,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HazelTypography,
        content = content
    )
}
