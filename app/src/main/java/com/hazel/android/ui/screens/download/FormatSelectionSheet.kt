package com.hazel.android.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo

/** How the format list is ordered. The default is what the probe already sorted for. */
enum class FormatSort(val label: String) {
    QUALITY("Quality"),
    FILE_SIZE("File size"),
    CONTAINER("Container")
}

/**
 * The full format list, opened from the quality row in the download sheet.
 *
 * This is a sheet of its own rather than an expanding section inside the download sheet, so
 * the list gets the whole height it needs: it starts part-way up the screen, can be dragged
 * to full height, and scrolls independently. Video and audio are listed under their own
 * headers, and picking an audio entry from a video download is allowed; the caller decides
 * what that means for the rest of the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSelectionSheet(
    info: MediaInfo,
    selected: MediaFormat?,
    onConfirm: (MediaFormat) -> Unit,
    onDismiss: () -> Unit
) {
    // Full height on open, to match the sheet it is opened from.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sort by remember { mutableStateOf(FormatSort.QUALITY) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(selected) }

    val video = remember(info, sort) { info.videoFormats.sortedBy(sort) }
    val audio = remember(info, sort) { info.audioFormats.sortedBy(sort) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header: stays put while the list scrolls under it ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Format",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Select format",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                            .clickable { sortMenuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort formats",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        FormatSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sort = option
                                    sortMenuOpen = false
                                },
                                trailingIcon = if (option == sort) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    onClick = { draft?.let(onConfirm) },
                    enabled = draft != null,
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "OK",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (video.isNotEmpty()) {
                    item(key = "header_video") { SectionHeader("Video") }
                    items(video, key = { "v_${it.formatId}" }) { format ->
                        FormatRow(
                            format = format,
                            selected = format == draft,
                            onClick = { draft = format }
                        )
                    }
                }
                if (audio.isNotEmpty()) {
                    item(key = "header_audio") { SectionHeader("Audio") }
                    items(audio, key = { "a_${it.formatId}" }) { format ->
                        FormatRow(
                            format = format,
                            selected = format == draft,
                            onClick = { draft = format }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reorders a tab's formats. The generic "best" row is pinned to the top under every
 * ordering, since it is the recommendation rather than one of the measured entries.
 */
private fun List<MediaFormat>.sortedBy(sort: FormatSort): List<MediaFormat> {
    val (generic, real) = partition { it.isGeneric }
    val ordered = when (sort) {
        // The probe already sorted by quality; keep that order untouched.
        FormatSort.QUALITY -> real
        FormatSort.FILE_SIZE -> real.sortedByDescending { it.fileSizeBytes }
        FormatSort.CONTAINER -> real.sortedWith(
            compareBy<MediaFormat> { it.ext }.thenByDescending { it.height }
        )
    }
    return generic + ordered
}

@Composable
private fun SectionHeader(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * One format, with the badges the source actually reported.
 *
 * Shared with the download sheet, which shows a single instance of this row for whatever is
 * currently selected. [showChevron] marks that instance as the thing you tap to open the
 * full list.
 */
@Composable
fun FormatRow(
    format: MediaFormat,
    selected: Boolean,
    onClick: () -> Unit,
    showChevron: Boolean = false,
    mergeAudioId: String? = null
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (format.ext.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        format.ext.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    format.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )

                // Badges are omitted entirely when the extractor did not report them,
                // which is common outside YouTube.
                val badges = listOfNotNull(
                    format.codecLabel.takeIf { it.isNotBlank() },
                    format.sizeLabel.takeIf { it.isNotBlank() },
                    format.bitrateLabel.takeIf { it.isNotBlank() }
                )
                if (badges.isNotEmpty() || mergeAudioId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Badges wrap onto another line rather than running past the row's
                    // edge. A plain Row cannot wrap, so a long set of badges would
                    // overflow the column and stretch the whole row out of shape.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (mergeAudioId != null) {
                            MetaBadge(
                                text = "id: $mergeAudioId",
                                icon = Icons.Filled.MusicNote,
                                emphasised = true
                            )
                        }
                        badges.forEach { MetaBadge(it) }
                    }
                }
            }

            if (!format.isGeneric) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "id: ${format.formatId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }

            if (showChevron) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.UnfoldMore,
                    contentDescription = "Change format",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Small pill under a format's headline. [emphasised] marks the audio track that will be
 * muxed in, which is a different kind of fact from the measured badges beside it.
 */
@Composable
fun MetaBadge(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emphasised: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (emphasised) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = if (emphasised) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (emphasised) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}
