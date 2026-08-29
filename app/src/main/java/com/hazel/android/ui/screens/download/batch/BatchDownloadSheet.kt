package com.hazel.android.ui.screens.download.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadPlan
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.screens.download.FormatSheet

/**
 * Settings for a whole set of links, whether they came from several pasted urls or from
 * one playlist.
 *
 * The sheet is a list with a bar under it. Tapping a link opens that link's own format
 * list, so a set can be part 1080p and part audio and still go out in one download; the bar
 * along the bottom is where a change is made to all of them at once, each of its buttons
 * opening a sheet of its own so the list keeps the height of the screen.
 *
 * Ticking is a separate mode, reached by holding a link or from the list menu, and it
 * narrows what the bar acts on. It never decides what gets downloaded: the download always
 * covers every link in the list, which is why a set collected on purpose does not have to
 * be ticked again before it will go.
 *
 * The choices themselves live in [BatchDownloadState], which is what keeps a change from
 * one row and a change from the bar from treading on each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDownloadSheet(
    results: List<MediaInfo>,
    options: DownloadOptions,
    onOptionsChange: (DownloadOptions) -> Unit,
    saveDirLabel: String,
    isCustomSaveDir: Boolean,
    onOpenSaveDir: () -> Unit,
    onPickSaveDir: () -> Unit,
    onResetSaveDir: () -> Unit,
    onResolveFormats: (MediaInfo) -> Unit,
    incognito: Boolean,
    onToggleIncognito: () -> Unit,
    onRemove: (MediaInfo) -> Unit,
    onDownload: (List<DownloadPlan>) -> Unit,
    onDismiss: () -> Unit
) {
    // Opened at full height: the list of links and the bar under it do not fit in a half
    // sheet, and a partly open sheet would hide the download action.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val state = rememberBatchDownloadState(results)
    var openSheet by remember { mutableStateOf(BatchSheet.NONE) }
    var listMenuOpen by remember { mutableStateOf(false) }

    // The link whose own sheet is open, held by url so the row keeps up with the formats
    // arriving for it rather than showing whatever it had when it was tapped.
    var focusedUrl by remember { mutableStateOf<String?>(null) }
    val focused = results.firstOrNull { it.url == focusedUrl }

    val plans = state.plans()
    val totalBytes = state.totalBytes

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Download",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Adjust download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = { onDownload(plans) },
                    enabled = plans.isNotEmpty(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (plans.isNotEmpty()) 0.15f else 0.06f
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Download, null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Download",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── What the list holds, and the controls that act on the list itself ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (state.selectionMode) "${state.selected.size} selected"
                    else "${results.size} links",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (totalBytes > 0) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "~ ${formatFileSize(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = {
                        if (state.selectionMode) state.endSelection() else state.startSelection()
                    }
                ) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = if (state.selectionMode) "Stop selecting"
                        else "Select links",
                        tint = if (state.selectionMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { listMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "List options")
                    }
                    DropdownMenu(
                        expanded = listMenuOpen,
                        onDismissRequest = { listMenuOpen = false }
                    ) {
                        if (!state.selectionMode) {
                            DropdownMenuItem(
                                text = { Text("Select links") },
                                onClick = {
                                    listMenuOpen = false
                                    state.startSelection()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Select all") },
                                onClick = {
                                    listMenuOpen = false
                                    state.selectAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Invert selection") },
                                onClick = {
                                    listMenuOpen = false
                                    state.invertSelection()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove selected") },
                                enabled = state.selected.isNotEmpty(),
                                onClick = {
                                    listMenuOpen = false
                                    results.filter { it.url in state.selected }.forEach(onRemove)
                                    state.endSelection()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Done") },
                                onClick = {
                                    listMenuOpen = false
                                    state.endSelection()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(results, key = { _, info -> info.url }) { index, info ->
                    val format = state.formatOf(info)
                    BatchDownloadCard(
                        info = info,
                        position = index + 1,
                        formatLabel = format?.label.orEmpty(),
                        sizeLabel = format?.sizeLabel.orEmpty(),
                        isVideo = state.isVideo(info),
                        isAdjusted = state.isAdjusted(info),
                        selectionMode = state.selectionMode,
                        checked = info.url in state.selected,
                        onClick = {
                            if (state.selectionMode) {
                                state.toggle(info)
                            } else {
                                // A link that came from a listing has no formats yet, so
                                // the read is started as the sheet opens and the list
                                // fills in under it.
                                onResolveFormats(info)
                                focusedUrl = info.url
                            }
                        },
                        onLongClick = {
                            if (state.selectionMode) state.toggle(info)
                            else state.startSelection(info)
                        },
                        onTypeClick = {
                            focusedUrl = info.url
                            openSheet = BatchSheet.ITEM_TYPE
                        },
                        onRemove = { onRemove(info) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            BatchActionBar(
                isVideo = state.videoTab,
                qualityLabel = qualityLabelFor(state.maxHeight),
                containerLabel = containerLabelFor(options, state.videoTab),
                incognito = incognito,
                onDownloadType = { openSheet = BatchSheet.TYPE },
                onQuality = { openSheet = BatchSheet.QUALITY },
                onSaveDir = { openSheet = BatchSheet.SAVE_DIR },
                onContainer = { openSheet = BatchSheet.CONTAINER },
                onIncognito = onToggleIncognito,
                onMore = { openSheet = BatchSheet.MORE }
            )

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }

    // ── The link's own sheet, which is what tapping a row opens ──
    //
    // It is the single download sheet, not a second version of it: the same tabs, fields
    // and quality row, with its action handing the choice back to the set instead of
    // starting a download of its own.
    if (focused != null && openSheet == BatchSheet.NONE) {
        FormatSheet(
            info = focused,
            options = options,
            onOptionsChange = onOptionsChange,
            saveDirLabel = saveDirLabel,
            isCustomSaveDir = isCustomSaveDir,
            isLoadingFormats = !focused.hasResolvedFormats,
            initialFormat = state.formatOf(focused),
            confirmAsApply = true,
            onOpenSaveDir = onOpenSaveDir,
            onPickSaveDir = onPickSaveDir,
            onResetSaveDir = onResetSaveDir,
            onDownload = { format, title, author ->
                state.setChoice(focused, format, title, author)
                focusedUrl = null
            },
            onDismiss = { focusedUrl = null }
        )
    }

    when (openSheet) {
        BatchSheet.NONE -> Unit

        BatchSheet.TYPE -> BatchDownloadTypeSheet(
            isVideo = state.videoTab,
            onSelect = {
                state.setDownloadType(it)
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        // The same choice as above, aimed at one link instead of the set.
        BatchSheet.ITEM_TYPE -> {
            val info = focused
            if (info == null) {
                openSheet = BatchSheet.NONE
            } else {
                BatchDownloadTypeSheet(
                    isVideo = state.isVideo(info),
                    onSelect = { isVideo ->
                        info.autoPick(isVideo, state.maxHeight)?.let {
                            state.setFormat(info, it)
                        }
                        openSheet = BatchSheet.NONE
                        focusedUrl = null
                    },
                    onDismiss = {
                        openSheet = BatchSheet.NONE
                        focusedUrl = null
                    }
                )
            }
        }

        BatchSheet.QUALITY -> BatchQualitySheet(
            maxHeight = state.maxHeight,
            onSelect = {
                state.setQualityCeiling(it)
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.CONTAINER -> BatchContainerSheet(
            isVideo = state.videoTab,
            current = if (state.videoTab) options.videoContainer else options.audioContainer,
            onSelect = { choice ->
                onOptionsChange(
                    if (state.videoTab) options.copy(videoContainer = choice)
                    else options.copy(audioContainer = choice)
                )
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.SAVE_DIR -> BatchSaveDirSheet(
            saveDirLabel = saveDirLabel,
            onOpen = {
                openSheet = BatchSheet.NONE
                onOpenSaveDir()
            },
            onPick = {
                openSheet = BatchSheet.NONE
                onPickSaveDir()
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.MORE -> BatchMoreSheet(
            options = options,
            isVideo = state.videoTab,
            onOptionsChange = onOptionsChange,
            onDismiss = { openSheet = BatchSheet.NONE }
        )
    }
}

/** Which sheet the action bar or a row's type button has opened, if any. */
private enum class BatchSheet { NONE, TYPE, ITEM_TYPE, QUALITY, CONTAINER, SAVE_DIR, MORE }

/** The action bar's container label, which is the extension it will write. */
private fun containerLabelFor(options: DownloadOptions, isVideo: Boolean): String {
    val container = if (isVideo) options.videoContainer else options.audioContainer
    return if (container.isBlank()) ".EXT" else ".${container.uppercase()}"
}
