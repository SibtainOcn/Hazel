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
 * These tokens adapt to light, dark and glass while the update hero states stay
 * independent of the user's accent. Switches and the channel picker follow the accent.
 */
object UpdateTokens {
    val isDark: Boolean
        @Composable get() = LocalDarkTheme.current

    private val glass: Boolean
        @Composable get() = LocalGlass.current

    val Bg: Color
        @Composable get() = if (glass) Color.Transparent else if (isDark) Color(0xFF0A0A0A) else Color(0xFFF7F5F1)
    val BgDeep: Color
        @Composable get() = if (glass) Color.Transparent else if (isDark) Color(0xFF000000) else Color(0xFFEDE9E3)
    val Surface: Color
        @Composable get() = if (glass) MaterialTheme.colorScheme.surface else if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF)
    val Surface1: Color
        @Composable get() = if (glass) MaterialTheme.colorScheme.surfaceVariant else if (isDark) Color(0xFF1A1A1A) else Color(0xFFF5F2EE)
    val Surface2: Color
        @Composable get() = if (glass) MaterialTheme.colorScheme.surfaceContainerHigh else if (isDark) Color(0xFF202020) else Color(0xFFEAE6E0)
    val Surface3: Color
        @Composable get() = if (glass) MaterialTheme.colorScheme.surfaceContainerHighest else if (isDark) Color(0xFF262626) else Color(0xFFE0DDD7)
    val Outline: Color
        @Composable get() = if (isDark) Color(0xFF2C2C2C) else Color(0xFFDEDAD2)
    val OutlineSoft: Color
        @Composable get() = if (isDark) Color(0xFF1F1F1F) else Color(0xFFECE8E1)
    val OnSurface: Color
        @Composable get() = if (isDark) Color(0xFFF2F2F0) else Color(0xFF1F1D1A)
    val OnSurfaceVar: Color
        @Composable get() = if (isDark) Color(0xFFB8B8B4) else Color(0xFF5E5A53)
    val OnSurfaceDim: Color
        @Composable get() = if (isDark) Color(0xFF7A7A77) else Color(0xFF8D8880)

    // Up-to-date hero state, independent emerald green
    val Accent: Color
        @Composable get() = if (isDark) Color(0xFF8FD6B8) else Color(0xFF1B6B4A)
    val AccentStrong: Color
        @Composable get() = if (isDark) Color(0xFFA9E6CC) else Color(0xFF106841)
    val AccentOn: Color
        @Composable get() = if (isDark) Color(0xFF003824) else Color(0xFFFFFFFF)
    val AccentContainer: Color
        @Composable get() = if (isDark) Color(0xFF0E3327) else Color(0xFFE2F4EC)

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
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 4.dp,
                end = 4.dp,
                top = if (isFirst) 0.dp else 24.dp,
                bottom = 8.dp
            )
    )
}

@Composable
fun UpdateListGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(UpdateTokens.Surface)
    ) {
        content()
    }
}

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
    if (showDivider) {
        HorizontalDivider(
            color = UpdateTokens.OutlineSoft,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = UpdateTokens.OnSurfaceVar,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = UpdateTokens.OnSurfaceVar,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = UpdateTokens.OnSurface,
                lineHeight = 22.sp
            )
            if (!secondary.isNullOrBlank()) {
                Text(
                    text = secondary,
                    fontSize = 13.sp,
                    color = UpdateTokens.OnSurfaceDim,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(UpdateTokens.Surface1)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else UpdateTokens.OnSurfaceVar
                )
            }
            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(UpdateTokens.OutlineSoft)
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
