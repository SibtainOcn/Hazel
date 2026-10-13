package com.hazel.android.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hazel.android.R

/*
 * The pieces the settings screens are built from, so each screen is a list of what it
 * sets rather than a copy of how a row is laid out. They follow the look of the other
 * settings screens: a back arrow and title in the accent, and rows grouped on cards.
 */

/** A settings screen: the header, an optional line under it, and a scrolling body. */
@Composable
internal fun SettingsScreen(
    title: String,
    onBack: () -> Unit,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.settings_back)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        content()
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * A titled group of rows on one card. Rows are separated by thin rules, which the section
 * draws itself, so a row never has to know whether it is first or last.
 */
@Composable
internal fun SettingsSection(title: String, rows: List<@Composable () -> Unit>) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 4.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            row()
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/** An on or off setting. The whole row toggles it, not only the switch. */
@Composable
internal fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true
) {
    SettingRow(
        icon = icon,
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) }
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A setting chosen in a dialog: its name, and what it is set to underneath. */
@Composable
internal fun ValueSettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    SettingRow(icon = icon, title = title, summary = value, enabled = enabled, onClick = onClick, valueStyle = true)
}

/** A row that does something when tapped, with an optional item at its end. */
@Composable
internal fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    SettingRow(icon = icon, title = title, summary = summary, enabled = enabled, onClick = onClick, trailing = trailing)
}

/**
 * A setting with a screen of its own and a quick on or off. The text opens the screen; the
 * switch, set apart by a thin rule, only turns it on or off.
 */
@Composable
internal fun SplitSwitchSettingRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            SettingRow(icon = icon, title = title, summary = summary, enabled = true, onClick = onOpen, valueStyle = true)
        }
        VerticalDivider(
            modifier = Modifier.padding(vertical = 14.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceVariant
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    valueStyle: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                style = LocalTextStyle.current.copy(hyphens = Hyphens.Auto)
            )
            if (!summary.isNullOrBlank()) {
                // A value is cut short, a description never is: a longer translation or a
                // large font must still be able to say the whole thing.
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto),
                    color = if (valueStyle) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f * alpha)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f * alpha),
                    maxLines = if (valueStyle) 2 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * Pick one of [choices], each a stored value and what it is called, with a line under it
 * from [describe] where one helps the choice. Tapping one is the choice: it is saved and the
 * dialog closes, so there is no OK to reach for. Tapping outside leaves things as they were.
 */
@Composable
internal fun <T> SingleChoiceDialog(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    describe: @Composable (T) -> String? = { null }
) {
    ChoiceDialog(title = title, onDismiss = onDismiss) {
        itemsIndexed(choices) { index, (value, label) ->
            ChoiceRow(
                label = label,
                description = describe(value),
                chosen = value == selected,
                shape = groupShape(index, choices.size),
                role = Role.RadioButton,
                onClick = { if (value == selected) onDismiss() else onSelect(value) }
            )
        }
    }
}

/** Tick any number of [choices]; nothing is saved until OK. */
@Composable
internal fun <T> MultiChoiceDialog(
    title: String,
    choices: List<Pair<T, String>>,
    selected: Set<T>,
    onConfirm: (Set<T>) -> Unit,
    onDismiss: () -> Unit
) {
    var ticked by remember { mutableStateOf(selected) }
    ChoiceDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            DialogButton(stringResource(R.string.download_cancel), filled = false, onClick = onDismiss, modifier = Modifier.weight(1f))
            DialogButton(stringResource(R.string.format_sheet_ok), filled = true, onClick = { onConfirm(ticked) }, modifier = Modifier.weight(1f))
        }
    ) {
        itemsIndexed(choices) { index, (value, label) ->
            val isTicked = value in ticked
            ChoiceRow(
                label = label,
                description = null,
                chosen = isTicked,
                shape = groupShape(index, choices.size),
                role = Role.Checkbox,
                onClick = { ticked = if (isTicked) ticked - value else ticked + value }
            )
        }
    }
}

/** The outer corners of a group of choices, and the tighter ones between two of them. */
private val GroupOuter = 20.dp
private val GroupInner = 6.dp

/** The shape of the choice at [index] of [count]: rounded outward at the ends of the group. */
internal fun groupShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) GroupOuter else GroupInner,
    topEnd = if (index == 0) GroupOuter else GroupInner,
    bottomStart = if (index == count - 1) GroupOuter else GroupInner,
    bottomEnd = if (index == count - 1) GroupOuter else GroupInner
)

/**
 * The frame both choice dialogs share: a title, the choices as one grouped block, and, where
 * the choice needs confirming, the buttons under them. Drawn by hand rather than as an
 * AlertDialog, whose fixed padding and gap above the buttons left a short list mostly empty.
 */
@Composable
private fun ChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    choices: LazyListScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 440.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = choices
                )
                if (buttons != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = buttons
                    )
                }
            }
        }
    }
}

/**
 * One choice, as a tonal block in the group. The whole block is the target. The chosen one
 * takes the accent tint and a filled check at its end; the rest carry nothing there, so the
 * eye goes straight to what is set. Shared by every place the app offers a choice, so a
 * pick looks the same in a dialog, a sheet, or a confirmation.
 *
 * [leading] sits before the label, such as a site's mark.
 */
@Composable
internal fun ChoiceRow(
    label: String,
    description: String?,
    chosen: Boolean,
    role: Role,
    onClick: () -> Unit,
    shape: RoundedCornerShape = RoundedCornerShape(GroupOuter),
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(
                if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .clickable(role = role, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            description?.let { line ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (chosen) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** A full pill button for the foot of a dialog: the accent for the action, a tone for Cancel. */
@Composable
internal fun DialogButton(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Asks before something that cannot be taken back. */
@Composable
internal fun ConfirmSettingDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel)) }
        }
    )
}

/**
 * A full-width action at the foot of a settings screen, drawn as a flat surface like the
 * app's other buttons rather than as an outline.
 */
@Composable
internal fun FlatSettingButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, fontWeight = FontWeight.Medium)
        }
    }
}
