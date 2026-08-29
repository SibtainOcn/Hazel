package com.hazel.android.ui.screens.download.batch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.download.AUDIO_CONTAINERS
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.VIDEO_CONTAINERS
import com.hazel.android.ui.screens.download.ChaptersDialog
import com.hazel.android.ui.screens.download.FilenameTemplateDialog
import com.hazel.android.ui.screens.download.SponsorBlockDialog
import com.hazel.android.ui.screens.download.SubtitlesDialog

/**
 * The heights the action bar offers, paired with what each is called.
 *
 * A height is a ceiling rather than a request for that exact resolution: a set of links
 * does not all offer the same ladder, and asking every one of them for a size only some
 * publish would leave the rest with nothing. Each link resolves the ceiling against its
 * own formats, which is why the rows can end up showing different numbers from each other.
 */
val BATCH_QUALITY_STEPS: List<Pair<Int, String>> = listOf(
    0 to "Best quality",
    2160 to "~ 2160p",
    1440 to "~ 1440p",
    1080 to "~ 1080p",
    720 to "~ 720p",
    480 to "~ 480p",
    360 to "~ 360p"
)

/** The action bar's label for the current ceiling. */
fun qualityLabelFor(maxHeight: Int): String =
    BATCH_QUALITY_STEPS.firstOrNull { it.first == maxHeight }?.second ?: "Best quality"

/** Audio or video, for the whole set or for one link. */
@Composable
fun BatchDownloadTypeSheet(
    isVideo: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ChoiceSheet(
        title = "Preferred download type",
        subtitle = "What these links download as",
        onDismiss = onDismiss
    ) {
        ChoiceRow(
            label = "Audio",
            icon = Icons.Filled.MusicNote,
            selected = !isVideo,
            onClick = { onSelect(false) }
        )
        ChoiceRow(
            label = "Video",
            icon = Icons.Filled.Videocam,
            selected = isVideo,
            onClick = { onSelect(true) }
        )
    }
}

/** The height ceiling every link resolves against. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchQualitySheet(
    maxHeight: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader("Format", "Select format")
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BATCH_QUALITY_STEPS, key = { it.first }) { (height, label) ->
                    ChoiceRow(
                        label = label,
                        selected = height == maxHeight,
                        onClick = { onSelect(height) }
                    )
                }
            }
        }
    }
}

/** Output container, which is the same choice the single download sheet offers. */
@Composable
fun BatchContainerSheet(
    isVideo: Boolean,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val choices = if (isVideo) VIDEO_CONTAINERS else AUDIO_CONTAINERS

    ChoiceSheet(
        title = "Container",
        subtitle = "The file these links are saved as",
        onDismiss = onDismiss
    ) {
        choices.forEach { choice ->
            ChoiceRow(
                label = choice,
                selected = choice == current.ifBlank { "Default" },
                // "Default" is stored as blank: it means "add no container option".
                onClick = { onSelect(if (choice == "Default") "" else choice) }
            )
        }
    }
}

/** Where the batch is written, with the folder it is going to now. */
@Composable
fun BatchSaveDirSheet(
    saveDirLabel: String,
    onOpen: () -> Unit,
    onPick: () -> Unit,
    onDismiss: () -> Unit
) {
    ChoiceSheet(
        title = "Save location",
        subtitle = saveDirLabel,
        onDismiss = onDismiss
    ) {
        ChoiceRow(
            label = "Open this folder",
            icon = Icons.Filled.Folder,
            selected = false,
            onClick = onOpen
        )
        ChoiceRow(
            label = "Change folder",
            icon = Icons.Filled.Edit,
            selected = false,
            onClick = onPick
        )
    }
}

/**
 * The rest of the settings, which apply to every link the action bar covers.
 *
 * These are one step further in than the action bar because they are the ones a download
 * rarely needs changed. Each chip opens the same dialog the single download sheet uses, so
 * there is one place where each of these settings is explained.
 */
@Composable
fun BatchMoreSheet(
    options: DownloadOptions,
    isVideo: Boolean,
    onOptionsChange: (DownloadOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var openDialog by remember { mutableStateOf(BatchDialog.NONE) }

    ChoiceSheet(
        title = if (isVideo) "Adjust video" else "Adjust audio",
        subtitle = "Applies to every link this covers",
        onDismiss = onDismiss
    ) {
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
                badge = options.chapterBadge(isVideo),
                onClick = { openDialog = BatchDialog.CHAPTERS }
            )
            if (isVideo) {
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
            isVideo = isVideo,
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
 * The shell every one of these sheets shares: a title, a line saying what it covers, and
 * whatever the sheet is actually offering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(title, subtitle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
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
