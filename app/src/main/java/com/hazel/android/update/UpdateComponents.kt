package com.hazel.android.update

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
 * These tokens are strictly independent of user-configured app accent colors.
 */
object UpdateTokens {
    val Bg = Color(0xFF0A0A0A)
    val BgDeep = Color(0xFF000000)
    val Surface = Color(0xFF141414)
    val Surface1 = Color(0xFF1A1A1A)
    val Surface2 = Color(0xFF202020)
    val Surface3 = Color(0xFF262626)
    val Outline = Color(0xFF2C2C2C)
    val OutlineSoft = Color(0xFF1F1F1F)
    val OnSurface = Color(0xFFF2F2F0)
    val OnSurfaceVar = Color(0xFFB8B8B4)
    val OnSurfaceDim = Color(0xFF7A7A77)

    // Up-to-date hero state (Emerald Green)
    val Accent = Color(0xFF8FD6B8)
    val AccentStrong = Color(0xFFA9E6CC)
    val AccentOn = Color(0xFF003824)
    val AccentContainer = Color(0xFF0E3327)

    // Update available hero state (Warm Amber / Gold)
    val Update = Color(0xFFFFCB80)
    val UpdateStrong = Color(0xFFFFDCA6)
    val UpdateOn = Color(0xFF402D00)
    val UpdateContainer = Color(0xFF3A2C0C)

    // Downloading / Installing hero state (Soft Blue)
    val Run = Color(0xFFA8CDFF)
    val RunStrong = Color(0xFFA8CDFF)
    val RunOn = Color(0xFF00315F)
    val RunContainer = Color(0xFF0C2847)

    val Danger = Color(0xFFFFB4A9)
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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = UpdateTokens.OnSurface
                )
            }
        },
        actions = { actions() },
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
        color = UpdateTokens.AccentStrong,
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

/**
 * Material 3 Switch with animated thumb and custom track styling.
 */
@Composable
fun UpdateSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = tween(durationMillis = 180),
        label = "switchThumbOffset"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (checked) 24.dp else 16.dp,
        animationSpec = tween(durationMillis = 140),
        label = "switchThumbSize"
    )

    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) UpdateTokens.Accent else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (checked) UpdateTokens.Accent else UpdateTokens.Outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (checked) UpdateTokens.AccentOn else UpdateTokens.OnSurfaceDim)
        )
    }
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
            .border(1.dp, UpdateTokens.Outline, RoundedCornerShape(20.dp))
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) UpdateTokens.AccentContainer else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = UpdateTokens.AccentStrong,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) UpdateTokens.AccentStrong else UpdateTokens.OnSurfaceVar
                )
            }
            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(UpdateTokens.Outline)
                )
            }
        }
    }
}
