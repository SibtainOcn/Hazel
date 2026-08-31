package com.hazel.android.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.AUDIO_CONTAINERS
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.VIDEO_CONTAINERS
import com.hazel.android.ui.components.FormatListShimmer

/**
 * Everything you can adjust before a download starts.
 *
 * The sheet opens part way up the screen and can be dragged the rest of the way, so the
 * media stays visible behind it while the first few controls are already in reach.
 *
 * Only the currently chosen quality is shown here, as a single row. The full format list
 * lives in [FormatSelectionSheet], which gets a sheet of its own rather than growing this
 * one, because a source with a hundred formats would bury every control underneath them.
 *
 * The set-of-links sheet opens this same sheet for one of its links, so that adjusting one
 * link of a batch and adjusting a single download are the same screen with the same
 * controls. [initialFormat] is what that link is currently set to, and [confirmAsApply]
 * turns the download action into one that hands the choice back instead, since there the
 * download does not start until the whole set is sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSheet(
    info: MediaInfo,
    options: DownloadOptions,
    onOptionsChange: (DownloadOptions) -> Unit,
    saveDirLabel: String,
    isCustomSaveDir: Boolean,
    isLoadingFormats: Boolean = false,
    initialFormat: MediaFormat? = null,
    confirmAsApply: Boolean = false,
    /**
     * Set when this media is already downloaded and still on the device, in which case the
     * header offers to play it beside the action that would fetch it again.
     */
    onPlay: (() -> Unit)? = null,
    onOpenSaveDir: () -> Unit,
    onPickSaveDir: () -> Unit,
    onResetSaveDir: () -> Unit,
    onDownload: (format: MediaFormat, title: String, author: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Opened at full height. The sheet's own content is a full screen of format rows and
    // fields, so a half-height first stop showed only the header and made an extra drag a
    // condition of using it. The set-of-links sheet already opens this way.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Open on whichever tab the source actually has formats for.
    var videoTab by remember(info.url) {
        mutableStateOf(initialFormat?.hasVideo ?: info.videoFormats.isNotEmpty())
    }

    // The best concrete format is preselected, so the row shows what will actually be
    // downloaded rather than a placeholder. The selection is keyed on the link rather than
    // the tab, so a format picked from the full list survives the tab switch that picking
    // it may have caused.
    //
    // It is keyed on whether formats have arrived as well, because a card that came from a
    // listing opens this sheet before they have. Without that, the sheet would keep the
    // stand-in it was opened with and never show the real one.
    var selected by remember(info.url, info.hasResolvedFormats) {
        mutableStateOf(
            initialFormat
                ?: if (info.videoFormats.isNotEmpty()) info.bestVideo else info.bestAudio
        )
    }

    // Title and author are editable: they name the saved file and, where the value is
    // unambiguous, the metadata written into it.
    var title by remember(info.url) { mutableStateOf(info.title) }
    var author by remember(info.url) { mutableStateOf(info.uploader) }

    var openDialog by remember { mutableStateOf(SheetDialog.NONE) }
    var formatSheetVisible by remember { mutableStateOf(false) }

    val container = if (videoTab) options.videoContainer else options.audioContainer
    val containerChoices = if (videoTab) VIDEO_CONTAINERS else AUDIO_CONTAINERS

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            // ── Header: title block on the left, download action on the right ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.format_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.format_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                // Left of the download action, and quieter than it. Playing what is already
                // there is the smaller of the two things to do here, and it should not be
                // possible to take it by aiming for the other.
                if (onPlay != null) {
                    Surface(
                        onClick = onPlay,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.format_sheet_play),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Surface(
                    onClick = {
                        selected?.let { onDownload(it, title.trim(), author.trim()) }
                    },
                    enabled = selected != null,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (selected != null) 0.15f else 0.06f
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (confirmAsApply) Icons.Filled.Check else Icons.Filled.Download,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (confirmAsApply) stringResource(R.string.format_sheet_ok) else stringResource(R.string.format_sheet_download),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Audio / Video tabs ──
            val tabIndex = if (videoTab) 1 else 0
            SecondaryTabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = !videoTab,
                    onClick = {
                        videoTab = false
                        selected = info.bestAudio
                    },
                    enabled = info.audioFormats.isNotEmpty(),
                    text = { Text(stringResource(R.string.format_sheet_tab_audio), fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = videoTab,
                    onClick = {
                        videoTab = true
                        selected = info.bestVideo
                    },
                    enabled = info.videoFormats.isNotEmpty(),
                    text = { Text(stringResource(R.string.format_sheet_tab_video), fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            EditableField(label = stringResource(R.string.format_sheet_label_title), value = title, onValueChange = { title = it })

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditableField(
                    label = stringResource(R.string.format_sheet_label_author),
                    value = author,
                    onValueChange = { author = it },
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = stringResource(R.string.format_sheet_label_container),
                    value = container.ifBlank { "Default" },
                    choices = containerChoices,
                    onSelect = { choice ->
                        // "Default" is stored as blank: it means "add no container option".
                        val stored = if (choice == "Default") "" else choice
                        onOptionsChange(
                            if (videoTab) options.copy(videoContainer = stored)
                            else options.copy(audioContainer = stored)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                if (videoTab) stringResource(R.string.format_sheet_video_quality) else stringResource(R.string.format_sheet_audio_quality),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            val current = selected
            when {
                // The sheet can open before the source has reported its formats, on the
                // path that goes ahead without metadata. A skeleton stands in until the
                // real row is known.
                isLoadingFormats -> FormatRowShimmerCard()

                current == null -> Text(
                    if (videoTab) stringResource(R.string.format_sheet_no_video_stream)
                    else stringResource(R.string.format_sheet_no_audio_stream),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                else -> {
                    // One row, showing what will be downloaded. Tapping it opens the list.
                    FormatRow(
                        format = current,
                        selected = true,
                        onClick = { formatSheetVisible = true },
                        showChevron = true,
                        // A video-only stream is muxed with an audio track, so the track
                        // that will be used is named alongside it.
                        mergeAudioId = info.mergeAudio
                            ?.formatId
                            ?.takeIf { videoTab && current.hasVideo && !current.hasAudio }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SaveDirField(label = saveDirLabel, onClick = { openDialog = SheetDialog.SAVE_DIR })

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                if (videoTab) stringResource(R.string.format_sheet_adjust_video)
                else stringResource(R.string.format_sheet_adjust_audio),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Option chips ──
            //
            // Chapters and subtitles only apply to a video download; the audio tab shows
            // the split-by-chapters half of the chapters dialog and no subtitles at all,
            // which is what yt-dlp can actually act on for an audio-only extraction.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionChip(
                        label = stringResource(R.string.format_sheet_thumbnail),
                        icon = Icons.Filled.Image,
                        selected = options.embedThumbnail,
                        onClick = {
                            onOptionsChange(options.copy(embedThumbnail = !options.embedThumbnail))
                        }
                    )
                    OptionChip(
                        label = stringResource(R.string.format_sheet_chapters),
                        icon = Icons.Filled.Book,
                        badge = options.chapterBadge(videoTab),
                        onClick = { openDialog = SheetDialog.CHAPTERS }
                    )
                    if (videoTab) {
                        OptionChip(
                            label = stringResource(R.string.format_sheet_subtitles),
                            icon = Icons.Filled.ClosedCaption,
                            badge = options.subtitleBadge,
                            onClick = { openDialog = SheetDialog.SUBTITLES }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionChip(
                        label = stringResource(R.string.format_sheet_sponsorblock),
                        icon = Icons.Filled.Paid,
                        badge = options.sponsorBlockFilters.size,
                        onClick = { openDialog = SheetDialog.SPONSORBLOCK }
                    )
                    OptionChip(
                        label = stringResource(R.string.format_sheet_filename_template),
                        icon = Icons.Filled.Edit,
                        onClick = { openDialog = SheetDialog.FILENAME }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (formatSheetVisible) {
        FormatSelectionSheet(
            info = info,
            selected = selected,
            onConfirm = { format ->
                selected = format
                // Picking an audio stream from the video tab, or the other way round,
                // moves the sheet to the tab that entry belongs to.
                videoTab = format.hasVideo
                formatSheetVisible = false
            },
            onDismiss = { formatSheetVisible = false }
        )
    }

    when (openDialog) {
        SheetDialog.NONE -> Unit

        SheetDialog.SPONSORBLOCK -> SponsorBlockDialog(
            options = options,
            onConfirm = {
                onOptionsChange(it)
                openDialog = SheetDialog.NONE
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.CHAPTERS -> ChaptersDialog(
            options = options,
            isVideo = videoTab,
            onChange = onOptionsChange,
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.SUBTITLES -> SubtitlesDialog(
            options = options,
            onChange = onOptionsChange,
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.FILENAME -> FilenameTemplateDialog(
            template = options.filenameTemplate,
            onConfirm = {
                onOptionsChange(options.copy(filenameTemplate = it))
                openDialog = SheetDialog.NONE
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.SAVE_DIR -> SaveDirDialog(
            label = saveDirLabel,
            isCustom = isCustomSaveDir,
            onOpen = {
                openDialog = SheetDialog.NONE
                onOpenSaveDir()
            },
            onPick = {
                openDialog = SheetDialog.NONE
                onPickSaveDir()
            },
            onReset = {
                openDialog = SheetDialog.NONE
                onResetSaveDir()
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )
    }
}

/** Which of the sheet's dialogs is open. Only one can be at a time. */
private enum class SheetDialog { NONE, SPONSORBLOCK, CHAPTERS, SUBTITLES, FILENAME, SAVE_DIR }

/** Labelled box whose value the user can type into, matching the read-only fields' look. */
@Composable
private fun EditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current
                    .merge(MaterialTheme.typography.bodyMedium)
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Labelled box that opens a menu of fixed choices, used for the container picker. */
@Composable
private fun DropdownField(
    label: String,
    value: String,
    choices: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.format_sheet_change_field, label),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice) },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    },
                    trailingIcon = if (choice == value) {
                        { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
    }
}

/** The destination row. Tapping it leads to opening or changing the folder. */
@Composable
private fun SaveDirField(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.format_sheet_save_dir),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.Folder,
                contentDescription = stringResource(R.string.format_sheet_save_location),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SaveDirDialog(
    label: String,
    isCustom: Boolean,
    onOpen: () -> Unit,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Folder, null) },
        title = { Text(stringResource(R.string.format_sheet_save_location_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.format_sheet_save_location_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.format_sheet_open_folder)) } },
        dismissButton = {
            Row {
                if (isCustom) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.format_sheet_reset)) }
                }
                TextButton(onClick = onPick) { Text(stringResource(R.string.format_sheet_change)) }
            }
        }
    )
}

/** Filter chip with an optional count badge, used for the adjust-download options. */
@Composable
private fun OptionChip(
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
        BadgedBox(badge = { Badge { Text(badge.toString()) } }) { chip() }
    } else {
        chip()
    }
}

/** Skeleton shown in place of the quality row while the format list is still resolving. */
@Composable
private fun FormatRowShimmerCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        FormatListShimmer(rows = 1, modifier = Modifier.padding(horizontal = 12.dp))
    }
}
