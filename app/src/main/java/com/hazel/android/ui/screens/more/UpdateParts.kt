package com.hazel.android.ui.screens.more

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.screens.history.CookieAction
import com.hazel.android.ui.screens.history.SectionLabel
import com.hazel.android.ui.screens.history.StackedRow
import com.hazel.android.ui.screens.history.rowShape
import com.hazel.android.update.DebugInfo

/** One button in an update screen's action row. */
internal class UpdateAction(
    val icon: ImageVector,
    @param:StringRes val label: Int,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * A row of shaped actions, the first in the accent and the rest tonal. Each takes its own
 * shape, the way the bottom bar gives each tab one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateActionRow(actions: List<UpdateAction>) {
    val shapes = listOf(
        MaterialShapes.Cookie6Sided.toShape(),
        MaterialShapes.Pentagon.toShape(),
        MaterialShapes.Clover4Leaf.toShape(),
        MaterialShapes.Square.toShape()
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        actions.forEachIndexed { index, action ->
            val lead = index == 0
            CookieAction(
                icon = action.icon,
                label = action.label,
                enabled = action.enabled,
                container = if (lead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                content = if (lead) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = shapes[index % shapes.size],
                onClick = action.onClick
            )
        }
    }
}

/** A component that opens its own screen: shaped icon, name, a line under it, and a mark at the end. */
internal class ComponentLink(
    val icon: ImageVector,
    val title: String,
    val line: String,
    val fresh: Boolean,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ComponentGroup(@StringRes title: Int, links: List<ComponentLink>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(title)
        links.forEachIndexed { index, link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(rowShape(index, links.size))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button, onClick = link.onClick)
                    .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShapedIcon(link.icon, MaterialShapes.Cookie9Sided.toShape())
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(link.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        link.line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (link.fresh) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.upd_chip_new),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialShapes.Pill.toShape())
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** An icon on a tonal accent shape. */
@Composable
internal fun ShapedIcon(icon: ImageVector, shape: Shape, size: androidx.compose.ui.unit.Dp = 42.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** Label-over-value rows, each with a copy button. */
@Composable
internal fun CopyGroup(@StringRes title: Int, rows: List<Pair<String, String>>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    com.hazel.android.ui.screens.history.Group(
        title,
        rows.map { (label, value) ->
            @Composable {
                StackedRow(label, value, Icons.Filled.ContentCopy, R.string.sheet_link_copy) { copyAndTell(context, value) }
            }
        }
    )
}

internal fun copyAndTell(context: android.content.Context, text: String, @StringRes done: Int = R.string.properties_copied) {
    val ok = DebugInfo.copy(context, text)
    Toast.makeText(context, if (ok) done else R.string.upd_copy_failed, Toast.LENGTH_SHORT).show()
}
