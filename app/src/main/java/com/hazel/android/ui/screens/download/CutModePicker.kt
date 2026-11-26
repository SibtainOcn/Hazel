package com.hazel.android.ui.screens.download

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.CutMode

/**
 * The three cut modes as one connected group: one is always chosen, its card rounds out, fills
 * with the accent and spins its shape. Every hint stays visible so the modes can be compared.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CutModePicker(
    selected: CutMode,
    afterAvailable: Boolean,
    onSelect: (CutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Shown at once on a tap rather than after the choice has been saved and read back, so the
    // old row and the new one change together instead of one after the other.
    var picked by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { picked = selected }
    Column(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        CutMode.entries.forEach { mode ->
            val enabled = mode != CutMode.AFTER || afterAvailable
            val spec = when (mode) {
                CutMode.QUICK -> ModeSpec(
                    Icons.Rounded.Speed, MaterialShapes.Cookie6Sided.toShape(),
                    R.string.options_cut_quick, R.string.options_cut_quick_summary
                )
                CutMode.PRECISE -> ModeSpec(
                    Icons.Rounded.Straighten, MaterialShapes.Pentagon.toShape(),
                    R.string.options_cut_precise, R.string.options_cut_precise_summary
                )
                CutMode.AFTER -> ModeSpec(
                    Icons.Rounded.DownloadForOffline, MaterialShapes.Sunny.toShape(),
                    R.string.options_cut_local,
                    if (afterAvailable) R.string.options_cut_local_summary else R.string.options_cut_local_unavailable
                )
            }
            ModeCard(
                spec = spec,
                selected = mode == picked,
                enabled = enabled,
                first = mode == CutMode.entries.first(),
                last = mode == CutMode.entries.last(),
                onClick = {
                    picked = mode
                    onSelect(mode)
                }
            )
        }
    }
}

private class ModeSpec(val icon: ImageVector, val shape: androidx.compose.ui.graphics.Shape, val label: Int, val hint: Int)

@Composable
private fun ModeCard(
    spec: ModeSpec,
    selected: Boolean,
    enabled: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit
) {
    // Settles without overshoot: the change reads as a calm step, not a wobble.
    val settle = spring<androidx.compose.ui.unit.Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val outer = 22.dp
    val inner = 6.dp
    val top by animateDpAsState(if (selected || first) outer else inner, settle, label = "top")
    val bottom by animateDpAsState(if (selected || last) outer else inner, settle, label = "bottom")
    val turn by animateFloatAsState(
        if (selected) MODE_TURN else 0f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "turn"
    )
    val colors = MaterialTheme.colorScheme
    // Every colour moves on one timing, so no part of a row changes ahead of the rest.
    val fade = tween<androidx.compose.ui.graphics.Color>(MODE_FADE_MS)
    val fill by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, fade, label = "fill")
    val ink by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurface, fade, label = "ink")
    val badge by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHighest, fade, label = "badge")
    val glyph by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, fade, label = "glyph")
    val hint by animateColorAsState(
        if (selected) colors.onPrimaryContainer.copy(alpha = 0.85f) else colors.onSurfaceVariant,
        fade,
        label = "hint"
    )
    val alpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .background(fill)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = null,
                // The fill is the feedback; a ripple on top flashed over the change.
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .rotate(turn)
                    .clip(spec.shape)
                    .background(badge)
            )
            Icon(
                spec.icon,
                contentDescription = null,
                tint = glyph.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(spec.label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = ink.copy(alpha = alpha)
            )
            Text(
                stringResource(spec.hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) hint else hint.copy(alpha = alpha),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private const val MODE_TURN = 30f
private const val MODE_FADE_MS = 200
