package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.theme.sheetButtonColor

/**
 * An icon button on a filled M3 Expressive shape, a six-sided cookie by default. [active]
 * turns the shape and the icon to the accent, for a mode that is on; [filled] makes it the
 * solid accent, for the main action.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    filled: Boolean = false,
    size: Dp = 40.dp,
    shape: Shape = MaterialShapes.Cookie6Sided.toShape(),
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        filled -> scheme.primary
        active -> scheme.primary.copy(alpha = 0.22f)
        else -> sheetButtonColor
    }
    val ink = when {
        filled -> scheme.onPrimary
        active -> scheme.primary
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) { content() }
    }
}
