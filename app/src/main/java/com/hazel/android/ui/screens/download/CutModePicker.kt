package com.hazel.android.ui.screens.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DownloadDone
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
 * with the accent and spins its shape, and only it shows its hint.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CutModePicker(
    selected: CutMode,
    afterAvailable: Boolean,
    onSelect: (CutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        CutMode.entries.forEach { mode ->
            val enabled = mode != CutMode.AFTER || afterAvailable
            val spec = when (mode) {
                CutMode.QUICK -> ModeSpec(
                    Icons.Filled.Bolt, MaterialShapes.Cookie6Sided.toShape(),
                    R.string.options_cut_quick, R.string.options_cut_quick_summary
                )
                CutMode.PRECISE -> ModeSpec(
                    Icons.Filled.CenterFocusStrong, MaterialShapes.Pentagon.toShape(),
                    R.string.options_cut_precise, R.string.options_cut_precise_summary
                )
                CutMode.AFTER -> ModeSpec(
                    Icons.Filled.DownloadDone, MaterialShapes.Clover4Leaf.toShape(),
                    R.string.options_cut_local,
                    if (afterAvailable) R.string.options_cut_local_summary else R.string.options_cut_local_unavailable
                )
            }
            ModeCard(
                spec = spec,
                selected = mode == selected,
                enabled = enabled,
                first = mode == CutMode.entries.first(),
                last = mode == CutMode.entries.last(),
                onClick = { onSelect(mode) }
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
    val bouncy = spring<androidx.compose.ui.unit.Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val outer = 22.dp
    val inner = 6.dp
    val top by animateDpAsState(if (selected || first) outer else inner, bouncy, label = "top")
    val bottom by animateDpAsState(if (selected || last) outer else inner, bouncy, label = "bottom")
    val turn by animateFloatAsState(
        if (selected) 60f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "turn"
    )
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, label = "fill")
    val ink = if (selected) colors.onPrimaryContainer else colors.onSurface
    val alpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .background(fill)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .rotate(turn)
                    .clip(spec.shape)
                    .background(if (selected) colors.primary else colors.surfaceContainerHighest)
            )
            Icon(
                spec.icon,
                contentDescription = null,
                tint = (if (selected) colors.onPrimary else colors.onSurfaceVariant).copy(alpha = alpha),
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
            // The chosen mode says what it does; a mode that cannot be chosen says why.
            AnimatedVisibility(
                visible = selected || !enabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    stringResource(spec.hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = (if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
                        .copy(alpha = if (selected) 0.8f else alpha),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
