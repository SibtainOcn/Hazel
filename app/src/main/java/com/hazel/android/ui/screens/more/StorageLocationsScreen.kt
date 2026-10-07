package com.hazel.android.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.screens.download.SaveDirDialog
import com.hazel.android.ui.screens.download.rememberSaveDirPicker
import com.hazel.android.util.MediaOpener
import kotlinx.coroutines.launch

/**
 * Shows all Hazel storage locations with an option to open each in the system file manager.
 */
@Composable
fun StorageLocationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val wifiOnly by remember(context) { SettingsRepository.getWifiOnly(context) }.collectAsState(initial = false)
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }.collectAsState(initial = SaveDirs())
    // The kind whose folder is being looked at or chosen, or null when neither is.
    var dirDialogFor by remember { mutableStateOf<Boolean?>(null) }

    // The same picker the download sheet uses: any folder or an SD card, saved for the
    // kind it was made for until it is changed or reset.
    val pickSaveDir = rememberSaveDirPicker(saveDirs)
    val speedLimit by remember(context) { SettingsRepository.getSpeedLimit(context) }.collectAsState(initial = "")
    val concurrentFragments by remember(context) { SettingsRepository.getConcurrentFragments(context) }.collectAsState(initial = SettingsRepository.CONCURRENT_FRAGMENTS.last())
    val throttledRate by remember(context) { SettingsRepository.getThrottledRate(context) }.collectAsState(initial = "")

    SettingsScreen(
        title = stringResource(R.string.storage_locations_title),
        onBack = onBack,
        description = stringResource(R.string.storage_locations_subtitle)
    ) {
        // Locations card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // One row per kind: each is saved on its own, the built-in folder until a
            // folder is picked for it.
            StorageLocationItem(
                icon = Icons.Filled.MusicNote,
                title = stringResource(R.string.format_sheet_tab_audio),
                path = saveDirs.labelOf(isVideo = false),
                onClick = { dirDialogFor = false }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
            StorageLocationItem(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.format_sheet_tab_video),
                path = saveDirs.labelOf(isVideo = true),
                onClick = { dirDialogFor = true }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val noLimit = stringResource(R.string.storage_locations_no_limit)
        val speeds = listOf("" to noLimit) + SettingsRepository.SPEED_LIMITS
        val rateOff = stringResource(R.string.advanced_sleep_off)
        val rates = listOf("" to rateOff) + SettingsRepository.THROTTLED_RATES

        SettingsSection(
            title = stringResource(R.string.storage_locations_limits),
            rows = listOf(
                {
                    // Checked as a download starts, not while one runs: stopping a transfer
                    // partway because the phone changed networks wastes the data it has
                    // already spent.
                    SwitchSettingRow(
                        icon = Icons.Filled.Wifi,
                        title = stringResource(R.string.storage_locations_wifi_only),
                        summary = stringResource(R.string.storage_locations_wifi_only_description),
                        checked = wifiOnly,
                        onCheckedChange = { enabled ->
                            scope.launch { SettingsRepository.setWifiOnly(context, enabled) }
                        }
                    )
                },
                {
                    LimitDropdownRow(
                        icon = Icons.Filled.Speed,
                        title = stringResource(R.string.storage_locations_speed_limit),
                        description = stringResource(R.string.storage_locations_speed_limit_description),
                        choices = speeds,
                        selected = speedLimit,
                        selectedLabel = speeds.firstOrNull { it.first == speedLimit }?.second
                            ?: speedLimit.ifBlank { noLimit },
                        onSelect = { scope.launch { SettingsRepository.setSpeedLimit(context, it) } }
                    )
                },
                {
                    LimitDropdownRow(
                        icon = Icons.Filled.Layers,
                        title = stringResource(R.string.storage_locations_fragments),
                        description = stringResource(R.string.storage_locations_fragments_description),
                        choices = SettingsRepository.CONCURRENT_FRAGMENTS.map { it to it.toString() },
                        selected = concurrentFragments,
                        selectedLabel = concurrentFragments.toString(),
                        onSelect = { scope.launch { SettingsRepository.setConcurrentFragments(context, it) } }
                    )
                },
                {
                    LimitDropdownRow(
                        icon = Icons.Filled.Autorenew,
                        title = stringResource(R.string.storage_locations_throttled),
                        description = stringResource(R.string.storage_locations_throttled_description),
                        choices = rates,
                        selected = throttledRate,
                        selectedLabel = rates.firstOrNull { it.first == throttledRate }?.second
                            ?: throttledRate.ifBlank { rateOff },
                        onSelect = { scope.launch { SettingsRepository.setThrottledRate(context, it) } }
                    )
                }
            )
        )

        // Hint
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.storage_locations_internal_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 16.sp
                )
            }
        }
    }

    dirDialogFor?.let { isVideo ->
        SaveDirDialog(
            isVideo = isVideo,
            saveDir = saveDirs.of(isVideo),
            label = saveDirs.labelOf(isVideo),
            onOpen = {
                dirDialogFor = null
                MediaOpener.openLocation(context, saveDirs.of(isVideo).uri, isVideo)
            },
            onPick = { card ->
                dirDialogFor = null
                pickSaveDir(isVideo, card)
            },
            onReset = {
                dirDialogFor = null
                scope.launch { SettingsRepository.resetSaveDir(context, isVideo) }
            },
            onDismiss = { dirDialogFor = null }
        )
    }
}

@Composable
private fun StorageLocationItem(
    icon: ImageVector,
    title: String,
    path: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * A limit picked from a short list: its name and what it does, and the current choice on a
 * flat chip that opens the list. A list rather than a field to type in, because each value
 * has a shape the engine expects and a typo would only show when a download misbehaves.
 *
 * The chip sits beside the title while every word of the title and description still fits
 * whole, and moves under it when one would not, as with a long translation or a large font
 * on a narrow phone. The chip's label wraps rather than being cut where even a row's width
 * is not enough.
 */
@Composable
private fun <T> LimitDropdownRow(
    icon: ImageVector,
    title: String,
    description: String,
    choices: List<Pair<T, String>>,
    selected: T,
    selectedLabel: String,
    onSelect: (T) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        TextBesideOrAboveChip(modifier = Modifier.weight(1f)) {
            Column {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    style = LocalTextStyle.current.copy(hyphens = Hyphens.Auto)
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Box {
                Surface(
                    onClick = { menuOpen = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            selectedLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    choices.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                menuOpen = false
                                onSelect(value)
                            },
                            trailingIcon = if (value == selected) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

/** The narrowest the text beside a chip may get, however short its words. */
private val MinTextBesideChip = 80.dp
private val ChipGap = 12.dp
private val ChipGapBelow = 8.dp

/**
 * Two children, text then chip. The chip is measured at its own width first; if the room
 * left still holds the text's longest word (its min intrinsic width) and [MinTextBesideChip],
 * the two sit side by side, centred on each other, with the chip at the end. Otherwise the
 * text takes the full width and the chip goes under it, at the end, so no word is broken to
 * make room for the chip.
 */
@Composable
private fun TextBesideOrAboveChip(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier, measurePolicy = TextBesideOrAboveChipPolicy)
}

/**
 * One policy for every row, created once: it reads no state, so a recomposition of the row
 * hands Compose the same policy and leaves the layout as it was. Each child is measured once,
 * and the text's longest word comes from its min intrinsic width, which reuses the text's own
 * cached measurement.
 */
private val TextBesideOrAboveChipPolicy = MeasurePolicy { measurables, constraints ->
    val (textMeasurable, chipMeasurable) = measurables
    val width = constraints.maxWidth
    val chip = chipMeasurable.measure(Constraints(maxWidth = width))
    val gap = ChipGap.roundToPx()
    val besideWidth = width - chip.width - gap
    val longestWord = textMeasurable.minIntrinsicWidth(Constraints.Infinity)

    if (besideWidth >= maxOf(longestWord, MinTextBesideChip.roundToPx())) {
        val text = textMeasurable.measure(Constraints(maxWidth = besideWidth))
        val height = maxOf(text.height, chip.height)
        layout(width, height) {
            text.placeRelative(0, (height - text.height) / 2)
            chip.placeRelative(width - chip.width, (height - chip.height) / 2)
        }
    } else {
        val text = textMeasurable.measure(Constraints(maxWidth = width))
        val below = ChipGapBelow.roundToPx()
        layout(width, text.height + below + chip.height) {
            text.placeRelative(0, 0)
            chip.placeRelative(width - chip.width, text.height + below)
        }
    }
}
