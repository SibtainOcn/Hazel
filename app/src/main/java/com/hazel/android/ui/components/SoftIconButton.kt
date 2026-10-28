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

/** Where a segment sits in a connected group, which decides which of its corners are round. */
enum class SegmentPosition { First, Middle, Last }

/**
 * One icon button of a connected group: round where the group ends, tight where it meets
 * its neighbours. Pressed, its tight corners round out and it squeezes.
 */
@Composable
fun SegmentIconButton(
    onClick: () -> Unit,
    position: SegmentPosition,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val outer = size / 2
    val inner by pressCorner(interaction, resting = 8.dp, pressed = outer * 0.75f)
    val start = if (position == SegmentPosition.First) outer else inner
    val end = if (position == SegmentPosition.Last) outer else inner
    Surface(
        onClick = onClick,
        modifier = modifier
            .pressScale(interaction, pressed = 0.92f)
            .size(size),
        shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
