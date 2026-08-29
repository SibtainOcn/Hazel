package com.hazel.android.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadPlan
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize

/**
 * Settings for a whole set of links, applied to every one of them at once.
 *
 * Adjusting each link on its own defeats the point of collecting them, so this sheet carries
 * one set of options and one audio or video choice for the batch. Each link still resolves
 * that choice against its own formats, which is why the rows show what each will actually
 * download rather than repeating the same label.
 *
 * Links are ticked or unticked to decide what the download covers, and the header menu acts
 * on the whole list at once. Removing a link takes it out of the results entirely, which is
 * a different thing from unticking it and is why the two are separate actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDownloadSheet(
    results: List<MediaInfo>,
    options: DownloadOptions,
    onOptionsChange: (DownloadOptions) -> Unit,
    saveDirLabel: String,
    onOpenSaveDir: () -> Unit,
    onPickSaveDir: () -> Unit,
    onRemove: (MediaInfo) -> Unit,
    onDownload: (List<DownloadPlan>) -> Unit,
    onDismiss: () -> Unit
) {
    // Opened at full height: the list of links and the options that apply to them all do
    // not fit in a half sheet, and a partly open sheet would hide the download action.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var videoTab by remember { mutableStateOf(true) }
    var openDialog by remember { mutableStateOf(BatchDialog.NONE) }
    var headerMenuOpen by remember { mutableStateOf(false) }

    // Collecting the links is already the decision to download them, so every link is
    // included and nothing is ticked. Selection exists only for acting on part of the set,
    // and turns itself on when the user asks for it or taps a row.
    var selectionMode by remember(results) { mutableStateOf(false) }
    var selected by remember(results) { mutableStateOf(emptySet<String>()) }

    // Outside selection the download covers everything; inside it, only what is ticked.
    val targets = remember(results, selectionMode, selected) {
        if (selectionMode && selected.isNotEmpty()) results.filter { it.url in selected }
        else results
    }

    // What each link will download under the current choice, worked out per item because
    // one source's best video is not another's.
    val plans = remember(targets, videoTab) {
        targets.mapNotNull { info ->
            val format = if (videoTab) info.bestVideo else info.bestAudio
            format?.let { DownloadPlan(info, it, info.title, info.uploader) }
        }
    }

    val totalBytes = plans.sumOf { it.format.fileSizeBytes }

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
                    .padding(start = 8.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Actions that apply to the list rather than to one row. They lead the
                // header because they change what the rest of it is describing.
                Box {
                    IconButton(onClick = { headerMenuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "List options")
                    }
                    DropdownMenu(
                        expanded = headerMenuOpen,
                        onDismissRequest = { headerMenuOpen = false }
                    ) {
                        if (!selectionMode) {
                            DropdownMenuItem(
                                text = { Text("Select links") },
                                onClick = {
                                    headerMenuOpen = false
                                    selectionMode = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Select all") },
                                onClick = {
                                    headerMenuOpen = false
                                    selected = results.map { it.url }.toSet()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Invert selection") },
                                onClick = {
                                    headerMenuOpen = false
                                    selected = results.map { it.url }.toSet() - selected
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove selected") },
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    headerMenuOpen = false
                                    results.filter { it.url in selected }.forEach(onRemove)
                                    selected = emptySet()
                                    selectionMode = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Done") },
                                onClick = {
                                    headerMenuOpen = false
                                    selected = emptySet()
                                    selectionMode = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

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

            SecondaryTabRow(
                selectedTabIndex = if (videoTab) 1 else 0,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = !videoTab,
                    onClick = { videoTab = false },
                    text = { Text("Audio", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = videoTab,
                    onClick = { videoTab = true },
                    text = { Text("Video", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selectionMode) "${selected.size} of ${results.size} selected"
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.url }) { info ->
                    val format = if (videoTab) info.bestVideo else info.bestAudio
                    BatchRow(
                        info = info,
                        formatLabel = format?.label.orEmpty(),
                        sizeLabel = format?.sizeLabel.orEmpty(),
                        isVideo = videoTab,
                        selectionMode = selectionMode,
                        checked = info.url in selected,
                        onToggle = {
                            // Tapping a row while nothing is being selected starts
                            // selection on that row, which is how the mode is reached
                            // without going to the menu first.
                            if (!selectionMode) {
                                selectionMode = true
                                selected = setOf(info.url)
                            } else {
                                selected = if (info.url in selected) selected - info.url
                                else selected + info.url
                            }
                        },
                        onRemove = { onRemove(info) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onOpenSaveDir)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Save dir",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                saveDirLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onPickSaveDir) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = "Change save location",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Applies to every selected link",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BatchChip(
                        label = "Thumbnail",
                        icon = Icons.Filled.Image,
                        selected = options.embedThumbnail,
                        onClick = {
                            onOptionsChange(options.copy(embedThumbnail = !options.embedThumbnail))
                        }
                    )
                    BatchChip(
                        label = "Chapters",
                        icon = Icons.Filled.Book,
                        badge = options.chapterBadge(videoTab),
                        onClick = { openDialog = BatchDialog.CHAPTERS }
                    )
                    if (videoTab) {
                        BatchChip(
                            label = "Subtitles",
                            icon = Icons.Filled.ClosedCaption,
                            badge = options.subtitleBadge,
                            onClick = { openDialog = BatchDialog.SUBTITLES }
                        )
                    }
                    BatchChip(
                        label = "SponsorBlock",
                        icon = Icons.Filled.Paid,
                        badge = options.sponsorBlockFilters.size,
                        onClick = { openDialog = BatchDialog.SPONSORBLOCK }
                    )
                    BatchChip(
                        label = "Filename template",
                        icon = Icons.Filled.Edit,
                        onClick = { openDialog = BatchDialog.FILENAME }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    when (openDialog) {
        BatchDialog.NONE -> Unit

        BatchDialog.SPONSORBLOCK -> SponsorBlockDialog(
            options = options,
            onConfirm = {
                onOptionsChange(it)
                openDialog = BatchDialog.NONE
            },
            onDismiss = { openDialog = BatchDialog.NONE }
        )

        BatchDialog.CHAPTERS -> ChaptersDialog(
            options = options,
            isVideo = videoTab,
            onChange = onOptionsChange,
            onDismiss = { openDialog = BatchDialog.NONE }
        )

        BatchDialog.SUBTITLES -> SubtitlesDialog(
            options = options,
            onChange = onOptionsChange,
            onDismiss = { openDialog = BatchDialog.NONE }
        )

        BatchDialog.FILENAME -> FilenameTemplateDialog(
            template = options.filenameTemplate,
            onConfirm = {
                onOptionsChange(options.copy(filenameTemplate = it))
                openDialog = BatchDialog.NONE
            },
            onDismiss = { openDialog = BatchDialog.NONE }
        )
    }
}

private enum class BatchDialog { NONE, SPONSORBLOCK, CHAPTERS, SUBTITLES, FILENAME }

/**
 * One link in the set: artwork, title, and what it will download under the current choice.
 *
 * A tick appears only once the user is selecting, since outside selection every link is
 * already included and a row of ticked boxes would say nothing. Swiping takes the link out
 * of the results entirely, which is a different thing from unticking it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchRow(
    info: MediaInfo,
    formatLabel: String,
    sizeLabel: String,
    isVideo: Boolean,
    selectionMode: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Fills the row exactly, so nothing of it shows until the row is actually
            // dragged aside.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            // Opaque: a translucent row would let the swipe background show through it
            // while the row is sitting still.
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggle)
                    .padding(start = if (selectionMode) 0.dp else 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle() })
                }

                Box(
                    modifier = Modifier
                        .width(78.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (info.thumbnail != null) {
                        AsyncImage(
                            model = info.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    val duration = formatDuration(info.durationSeconds)
                    if (duration.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp),
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        info.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (formatLabel.isNotBlank()) MetaBadge(formatLabel)
                        if (sizeLabel.isNotBlank()) MetaBadge(sizeLabel)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    if (isVideo) Icons.Filled.Videocam else Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BatchChip(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
    badge: Int = 0,
    onClick: () -> Unit
) {
    val chip = @Composable {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    if (selected) Icons.Filled.Check else icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
            )
        )
    }

    if (badge > 0) {
        BadgedBox(badge = { Badge { Text("$badge") } }) { chip() }
    } else {
        chip()
    }
}
