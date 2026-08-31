package com.hazel.android.ui.screens.download.batch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.AUDIO_CONTAINERS
import com.hazel.android.download.VIDEO_CONTAINERS

/**
 * The heights the action bar offers, paired with what each is called.
 *
 * A height is a ceiling rather than a request for that exact resolution: a set of links
 * does not all offer the same ladder, and asking every one of them for a size only some
 * publish would leave the rest with nothing. Each link resolves the ceiling against its
 * own formats, which is why the rows can end up showing different numbers from each other.
 */
val BATCH_QUALITY_STEPS: List<Pair<Int, Int>> = listOf(
    0 to R.string.batch_quality_best,
    2160 to R.string.batch_quality_2160,
    1440 to R.string.batch_quality_1440,
    1080 to R.string.batch_quality_1080,
    720 to R.string.batch_quality_720,
    480 to R.string.batch_quality_480,
    360 to R.string.batch_quality_360
)

/**
 * The action bar's label for the current ceiling, as a resource id rather than as text.
 * This list is a top level value, built before there is a context to read strings with, so
 * the name is resolved where it is shown instead.
 */
@StringRes
fun qualityLabelFor(maxHeight: Int): Int =
    BATCH_QUALITY_STEPS.firstOrNull { it.first == maxHeight }?.second
        ?: R.string.batch_quality_best

/** Audio or video, for the whole set or for one link. */
@Composable
fun BatchDownloadTypeSheet(
    isVideo: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ChoiceSheet(
        title = stringResource(R.string.batch_type_sheet_title),
        subtitle = stringResource(R.string.batch_type_sheet_subtitle),
        onDismiss = onDismiss
    ) {
        ChoiceRow(
            label = stringResource(R.string.batch_type_audio),
            icon = Icons.Filled.MusicNote,
            selected = !isVideo,
            onClick = { onSelect(false) }
        )
        ChoiceRow(
            label = stringResource(R.string.batch_type_video),
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
        containerColor = batchSheetColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(
                stringResource(R.string.batch_format_title),
                stringResource(R.string.batch_format_subtitle)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BATCH_QUALITY_STEPS, key = { it.first }) { (height, labelRes) ->
                    ChoiceRow(
                        label = stringResource(labelRes),
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
        title = stringResource(R.string.batch_container_title),
        subtitle = stringResource(R.string.batch_container_subtitle),
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
        title = stringResource(R.string.batch_save_dir_title),
        subtitle = saveDirLabel,
        onDismiss = onDismiss
    ) {
        ChoiceRow(
            label = stringResource(R.string.batch_open_folder),
            icon = Icons.Filled.Folder,
            selected = false,
            onClick = onOpen
        )
        ChoiceRow(
            label = stringResource(R.string.batch_change_folder),
            icon = Icons.Filled.Edit,
            selected = false,
            onClick = onPick
        )
    }
}

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
        containerColor = batchSheetColor
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
            style = MaterialTheme.typography.bodyMedium,
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
        else batchRaisedColor
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
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
