package com.hazel.android.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                    Icons.AutoMirrored.Filled.ArrowBack,
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valueStyle) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f * alpha)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f * alpha),
                    maxLines = 2,
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
 * from [describe] where one helps the choice.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(choices) { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label)
                            describe(value)?.let { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel)) }
        }
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(choices) { (value, label) ->
                    val isTicked = value in ticked
                    val toggle = { ticked = if (isTicked) ticked - value else ticked + value }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = toggle)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isTicked, onCheckedChange = { toggle() })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ticked) }) { Text(stringResource(R.string.format_sheet_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel)) }
        }
    )
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
