package com.hazel.android.update

import com.hazel.android.ui.theme.LocalGlass
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import com.hazel.android.ui.theme.LocalDarkTheme
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * These tokens adapt to light, dark and glass. The up-to-date state follows the user's accent;
 * update-available and downloading keep their own amber and blue.
 */
object UpdateTokens {
    val isDark: Boolean
        @Composable get() = LocalDarkTheme.current

    private val glass: Boolean
        @Composable get() = LocalGlass.current

    // Neutrals come from the theme, so light, dark, glass and the accent's tint all carry through.
    val Bg: Color
        @Composable get() = if (glass) Color.Transparent else MaterialTheme.colorScheme.background
    val BgDeep: Color
        @Composable get() = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest
    val Surface: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val Surface1: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val Surface2: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
    val Surface3: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
    val Outline: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val OutlineSoft: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val OnSurface: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface
    val OnSurfaceVar: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val OnSurfaceDim: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    // Up-to-date state, in the user's accent
    val Accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val AccentStrong: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val AccentOn: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimary
    val AccentContainer: Color
        @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.12f)

    // Update available hero state (Warm Amber / Gold)
    val Update: Color
        @Composable get() = if (isDark) Color(0xFFFFCB80) else Color(0xFFC77700)
    val UpdateStrong: Color
        @Composable get() = if (isDark) Color(0xFFFFDCA6) else Color(0xFF8F4D00)
    val UpdateOn: Color
        @Composable get() = if (isDark) Color(0xFF402D00) else Color(0xFFFFFFFF)
    val UpdateContainer: Color
        @Composable get() = if (isDark) Color(0xFF3A2C0C) else Color(0xFFFFF0D6)

    // Downloading / Installing hero state (Soft Blue)
    val Run: Color
        @Composable get() = if (isDark) Color(0xFFA8CDFF) else Color(0xFF005FB8)
    val RunStrong: Color
        @Composable get() = if (isDark) Color(0xFFA8CDFF) else Color(0xFF005FB8)
    val RunOn: Color
        @Composable get() = if (isDark) Color(0xFF00315F) else Color(0xFFFFFFFF)
    val RunContainer: Color
        @Composable get() = if (isDark) Color(0xFF0C2847) else Color(0xFFE1F0FF)

    val Danger: Color
        @Composable get() = if (isDark) Color(0xFFFFB4A9) else Color(0xFFBA1A1A)
}

typealias UpdaterTokens = UpdateTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = UpdateTokens.OnSurface
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Back",
                    tint = UpdateTokens.OnSurface
                )
            }
        },
        actions = { actions() },
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = UpdateTokens.Bg
        )
    )
}

@Composable
fun UpdateSectionLabel(text: String, isFirst: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = if (isFirst) 0.dp else 24.dp, bottom = 8.dp)
    )
}

/**
 * Rows joined into one rounded block: the group rounds the outer corners and the rows sit
 * 2dp apart with tight corners between them, the way the properties sheet groups its rows.
 */
@Composable
fun UpdateListGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
}

/** One row of a [UpdateListGroup]; its icon sits on a tonal accent shape. [showDivider] is kept for callers and ignored. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun UpdateListItem(
    icon: ImageVector? = null,
    painter: Painter? = null,
    primary: String,
    secondary: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null || painter != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                val tint = MaterialTheme.colorScheme.primary
                if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                else if (painter != null) Icon(painter, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = UpdateTokens.OnSurface
            )
            if (!secondary.isNullOrBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = UpdateTokens.OnSurfaceVar
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) = UpdateTopBar(title = title, onBack = onBack, actions = actions)

@Composable
fun UpdaterSectionLabel(text: String, isFirst: Boolean = false) =
    UpdateSectionLabel(text = text, isFirst = isFirst)

@Composable
fun UpdaterListGroup(content: @Composable ColumnScope.() -> Unit) =
    UpdateListGroup(content = content)

@Composable
fun UpdaterListItem(
    icon: ImageVector? = null,
    painter: Painter? = null,
    primary: String,
    secondary: String? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) = UpdateListItem(
    icon = icon,
    painter = painter,
    primary = primary,
    secondary = secondary,
    showDivider = showDivider,
    onClick = onClick,
    trailing = trailing
)

@Composable
fun UpdateSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}

/**
 * Material 3 Segmented Button row for channel selection.
 */
@Composable
fun UpdateSegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    // Connected segments: round at the ends, tight between, the chosen one in the solid accent.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val start = if (index == 0) 24.dp else 8.dp
            val end = if (index == options.lastIndex) 24.dp else 8.dp
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .selectable(selected = isSelected, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else UpdateTokens.OnSurfaceVar
                )
            }
        }
    }
}

/** Calm wavy progress for update downloads; [progress] null (size unknown or done) shows full. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateWavyProgress(progress: Float?, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(
        targetValue = progress ?: 1f,
        animationSpec = tween(durationMillis = 600),
        label = "updateProgress"
    )
    LinearWavyProgressIndicator(
        progress = { shown },
        modifier = modifier.fillMaxWidth(),
        color = UpdateTokens.RunStrong,
        trackColor = if (UpdateTokens.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
        amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) * 0.6f },
        wavelength = 48.dp,
        waveSpeed = 12.dp
    )
}
