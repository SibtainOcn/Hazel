package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.glass_style_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.pickerListHeight(54.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                GlassStyle.available.forEach { style ->
                    val selected = style == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .selectable(selected = selected, role = Role.RadioButton) {
                                onPick(style)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassStyleSwatch(style, accentName)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = glassStyleLabel(style),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.accent_picker_close)) }
        }
    )
}
