package com.hazel.android.ui.screens.more

import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.outlined.AutoAwesome
import com.hazel.android.ui.components.glassStyleLabel
import com.hazel.android.ui.components.GlassStyleSwatch
import com.hazel.android.ui.components.GlassStylePickerDialog
import com.hazel.android.ui.theme.GlassStyle
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.components.AccentPickerDialog
import com.hazel.android.ui.theme.AccentColors

/**
 * Theme and accent color controls, moved off the top bar into their own screen.
 */
@Composable
fun AppearanceScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    accentName: String,
    onAccentChanged: (String) -> Unit,
    glassEnabled: Boolean,
    onToggleGlass: () -> Unit,
    glassStyle: GlassStyle,
    onGlassStyleChanged: (GlassStyle) -> Unit,
    onBack: () -> Unit
) {
    var accentPickerVisible by remember { mutableStateOf(false) }
    var glassPickerVisible by remember { mutableStateOf(false) }
    var resetVisible by remember { mutableStateOf(false) }
    val currentAccent = AccentColors.find { it.name == accentName } ?: AccentColors.first()

    SettingsScreen(title = stringResource(R.string.appearance_title), onBack = onBack) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // Dark theme toggle
            ListItem(
                headlineContent = { Text(stringResource(R.string.appearance_dark_theme)) },
                supportingContent = {
                    Text(stringResource(if (isDarkTheme) R.string.appearance_on else R.string.appearance_off))
                },
                leadingContent = {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onToggleTheme() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.appearance_glass)) },
                supportingContent = {
                    Text(stringResource(if (glassEnabled) R.string.appearance_on else R.string.appearance_off))
                },
                leadingContent = {
                    Icon(Icons.Outlined.BlurOn, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Switch(
                        checked = glassEnabled,
                        onCheckedChange = { onToggleGlass() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onToggleGlass() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.glass_style_title)) },
                supportingContent = { Text(glassStyleLabel(glassStyle)) },
                leadingContent = {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { GlassStyleSwatch(glassStyle, accentName) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clickable(enabled = glassEnabled) { glassPickerVisible = true }
                    .alpha(if (glassEnabled) 1f else 0.45f)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Accent Color row — opens centered dialog
            ListItem(
                headlineContent = { Text(stringResource(R.string.appearance_accent_color)) },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(currentAccent.dark)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(accentName)
                    }
                },
                leadingContent = {
                    Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { accentPickerVisible = true }
            )
        }

        FlatSettingButton(
            icon = Icons.Filled.RestartAlt,
            text = stringResource(R.string.appearance_reset),
            onClick = { resetVisible = true }
        )
    }

    if (resetVisible) {
        ConfirmSettingDialog(
            title = stringResource(R.string.appearance_reset_title),
            body = stringResource(R.string.appearance_reset_body),
            confirm = stringResource(R.string.format_sheet_reset),
            onConfirm = {
                if (!isDarkTheme) onToggleTheme()
                if (accentName != SettingsRepository.DEFAULT_ACCENT) onAccentChanged(SettingsRepository.DEFAULT_ACCENT)
                if (!glassEnabled) onToggleGlass()
                if (glassStyle != GlassStyle.DEFAULT) onGlassStyleChanged(GlassStyle.DEFAULT)
                resetVisible = false
            },
            onDismiss = { resetVisible = false }
        )
    }

    if (glassPickerVisible) {
        GlassStylePickerDialog(
            current = glassStyle,
            accentName = accentName,
            onPick = onGlassStyleChanged,
            onDismiss = { glassPickerVisible = false }
        )
    }

    if (accentPickerVisible) {
        AccentPickerDialog(
            accentName = accentName,
            onAccentChanged = onAccentChanged,
            onDismiss = { accentPickerVisible = false }
        )
    }
}
