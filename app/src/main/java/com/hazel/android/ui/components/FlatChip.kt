package com.hazel.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ChipShape = RoundedCornerShape(12.dp)

/** The resting fill of a flat control: one tone up from the surface, no outline. */
@Composable
private fun restingFill(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

/**
 * A setting that is on or off, or opens its own choices: an icon and a name on a flat
 * rounded surface. There is no outline; the surface alone says it can be tapped, and it
 * takes the accent colour while it is on.
 *
 * [leading] replaces the icon where the icon itself carries state, such as a sort
 * direction arrow.
 */
@Composable
fun FlatChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    badge: Int = 0,
    leading: (@Composable () -> Unit)? = null
) {
    val content = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val chip = @Composable {
        Surface(
            onClick = onClick,
            shape = ChipShape,
            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else restingFill(),
            contentColor = content,
            modifier = modifier
                .height(36.dp)
                .semantics {
                    this.selected = selected
                    role = Role.Button
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    leading != null -> leading()
                    icon != null -> Icon(
                        if (selected) Icons.Filled.Check else icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }

    if (badge > 0) {
        BadgedBox(badge = { Badge { Text(badge.toString()) } }) { chip() }
    } else {
        chip()
    }
}

/** A square icon control drawn the same way as [FlatChip]. */
@Composable
fun FlatIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ChipShape,
        color = restingFill(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
        }
    }
}
