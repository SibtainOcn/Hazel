package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.screens.more.SingleChoiceDialog
import com.hazel.android.ui.theme.AccentColors
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.glassPreview

@Composable
fun glassStyleLabel(style: GlassStyle): String = stringResource(
    when (style) {
        GlassStyle.AURORA -> R.string.glass_style_aurora
        GlassStyle.ACCENT -> R.string.glass_style_accent
        GlassStyle.WALLPAPER -> R.string.glass_style_wallpaper
        GlassStyle.SUNSET -> R.string.glass_style_sunset
        GlassStyle.NEBULA -> R.string.glass_style_nebula
        GlassStyle.FOREST -> R.string.glass_style_forest
        GlassStyle.OCEAN -> R.string.glass_style_ocean
        GlassStyle.MONO -> R.string.glass_style_mono
    }
)

@Composable
fun GlassStyleSwatch(style: GlassStyle, accentName: String, modifier: Modifier = Modifier) {
    val colors = when (style) {
        GlassStyle.ACCENT -> {
            val accent = (AccentColors.find { it.name == accentName } ?: AccentColors.first()).dark
            listOf(accent, accent.copy(alpha = 0.55f), accent.copy(alpha = 0.3f))
        }
        GlassStyle.WALLPAPER -> listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
        )
        else -> glassPreview(style)
    }
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors))
    )
}

@Composable
fun GlassStylePickerDialog(
    current: GlassStyle,
    accentName: String,
    onPick: (GlassStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    SingleChoiceDialog(
        title = stringResource(R.string.glass_style_title),
        choices = GlassStyle.available.map { it to glassStyleLabel(it) },
        selected = current,
        onSelect = {
            onPick(it)
            onDismiss()
        },
        onDismiss = onDismiss,
        leading = { GlassStyleSwatch(it, accentName) }
    )
}
