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
            error = ErrorRedLight,
            onError = LightBackground,
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
