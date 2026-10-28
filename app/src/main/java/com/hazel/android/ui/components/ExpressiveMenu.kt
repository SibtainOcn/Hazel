package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.theme.LocalDarkTheme
import com.hazel.android.ui.theme.LocalGlass
import com.hazel.android.ui.theme.glassBackdrop
import com.hazel.android.ui.theme.tonalFill
import androidx.compose.material3.DropdownMenu as M3DropdownMenu

private val MenuShape = RoundedCornerShape(24.dp)
private val ItemShape = RoundedCornerShape(16.dp)

/**
 * Hazel's menu: a flat rounded panel on the app's glass, or on the theme's raised surface
 * without glass, holding its items as separate rounded slabs. Takes the same arguments as
 * the Material menu it stands in for.
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlass.current
    M3DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        shape = MenuShape,
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        modifier = modifier.glassBackdrop(MenuShape, enabled = glass)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 196.dp, max = 300.dp)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

/**
 * What a menu item does, which picks its badge's shape. Every badge is in the accent, so it
 * follows the chosen accent, dark or light and glass.
 */
enum class MenuTone { Accent, Info, Neutral, Danger }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuTone.shape(): Shape = when (this) {
    MenuTone.Accent -> MaterialShapes.Cookie6Sided.toShape()
    MenuTone.Info -> MaterialShapes.Pentagon.toShape()
    MenuTone.Neutral -> MaterialShapes.Cookie4Sided.toShape()
    MenuTone.Danger -> MaterialShapes.Cookie9Sided.toShape()
}

/** [icon] on its [tone]'s coloured shape, as menus and option dialogs lead their rows with. */
@Composable
fun ToneBadge(
    icon: ImageVector,
    tone: MenuTone,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 38.dp
) {
    val color = MaterialTheme.colorScheme.primary
    val shape = tone.shape()
    val dark = LocalDarkTheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(color.copy(alpha = if (!enabled) 0.08f else if (dark) 0.22f else 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) color else color.copy(alpha = 0.38f),
            modifier = Modifier.size(size * 0.53f)
        )
    }
}

/**
 * One item of [DropdownMenu]: a rounded slab with its [icon] on a [tone] coloured shape. A
 * [selected] item, the current choice in a list of options, is tinted in the accent. It grows
 * with the font rather than clipping, and wraps a long label.
 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: MenuTone = MenuTone.Accent,
    selected: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    /** A smaller row, for a menu over a small surface such as the player. */
    compact: Boolean = false
) {
    val dark = LocalDarkTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val dim = { c: Color -> if (enabled) c else c.copy(alpha = 0.38f) }
    val ink = dim(if (selected) accent else MaterialTheme.colorScheme.onSurface)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 44.dp else 52.dp)
            .clip(ItemShape)
            .background(if (selected) accent.copy(alpha = if (dark) 0.2f else 0.14f) else tonalFill(0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            ToneBadge(icon, tone, enabled = enabled, size = if (compact) 30.dp else 38.dp)
            Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
        } else {
            Spacer(Modifier.width(6.dp))
        }
        Box(modifier = Modifier.weight(1f, fill = false)) {
            CompositionLocalProvider(LocalContentColor provides ink) {
                ProvideTextStyle(
                    (if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge).copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                ) { text() }
            }
        }
        if (trailingIcon != null) {
            Spacer(Modifier.weight(1f, fill = true).widthIn(min = 12.dp))
            CompositionLocalProvider(LocalContentColor provides dim(accent)) { trailingIcon() }
        }
    }
}
