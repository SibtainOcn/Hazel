package com.hazel.android.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An icon on a soft tonal tile, so it reads as a button without an outline. Pressed, it
 * squeezes and its corners tighten, then springs back.
 */
@Composable
fun SoftIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 14.dp,
    size: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val radius by pressCorner(interaction, resting = corner, pressed = corner * 0.5f)
    Surface(
        onClick = onClick,
        modifier = modifier
            .pressScale(interaction, pressed = 0.9f)
            .size(size),
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
