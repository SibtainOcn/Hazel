package com.hazel.android.ui.screens.download

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.components.FormatListShimmer
import com.hazel.android.ui.theme.SizeBadgeContainer
import com.hazel.android.ui.theme.SizeBadgeContent

/**
 * How the format list is ordered. The default is what the probe already sorted for.
 *
 * The name is held as a resource id rather than as text, the same way the fetch mode and
 * the listing source hold theirs: an enum has no Context, so a label written here is a
 * label nothing can translate.
 */
enum class FormatSort(@StringRes val labelRes: Int) {
    QUALITY(R.string.format_sort_quality),
    FILE_SIZE(R.string.format_sort_file_size),
    CONTAINER(R.string.format_sort_container)
}

/**
 * The full format list, opened from the quality row in the download sheet.
 *
 * It opens half way up the screen and can be dragged to full height. Half is where it is
 * useful: the rows are large enough to read at a glance, and the media behind the sheet
 * stays visible while a few of them are already in reach.
 *
 * [audioFirst] carries which tab the download sheet is on. The list holds both kinds
 * either way, since picking an audio stream for a video download is allowed, but the one
 * being chosen leads and the sheet opens scrolled to the current choice.
 *
 * [isLoadingFormats] covers the case where the sheet is opened on a link that came from a
 * listing and has not been read yet. Only the generic entry is there to show at that point,
 * so the sheet says the rest is still coming rather than letting one row look like the
 * whole answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSelectionSheet(
    info: MediaInfo,
    selected: MediaFormat?,
    onConfirm: (MediaFormat) -> Unit,
    onDismiss: () -> Unit,
    audioFirst: Boolean = false,
    isLoadingFormats: Boolean = false
) {
    // Half height on open. The sheet is a list, and a list is readable from the top down,
    // so the whole screen is offered rather than demanded.
    val sheetState = rememberModalBottomSheetState()

    var sort by remember { mutableStateOf(FormatSort.QUALITY) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(selected) }

    // The rows are laid out once per ordering rather than per frame. Each carries the text
    // it draws, so scrolling does no formatting work and a fast fling has nothing to do
    // but draw.
    val rows = remember(info, sort, audioFirst) { buildRows(info, sort, audioFirst) }

    val listState = rememberLazyListState()

    // Opens on what is currently chosen. A list of forty entries that opens at the top of
    // the wrong section hides the one row the user came to confirm. The row above it comes
    // along, so the header saying which kind of stream this is stays in view.
    LaunchedEffect(rows) {
        val index = rows.indexOfFirst { it is FormatListRow.Entry && it.format == draft }
        if (index > 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
    }

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
                        stringResource(R.string.format_selection_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.format_selection_subtitle),
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
                            contentDescription = stringResource(R.string.format_selection_sort),
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
                                text = { Text(stringResource(option.labelRes)) },
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
                            stringResource(R.string.format_selection_confirm),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isLoadingFormats) {
                Text(
                    stringResource(R.string.format_selection_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                // Clears the system's own bar, so the last row can be read and tapped
                // rather than sitting under it.
                contentPadding = WindowInsets.navigationBars
                    .asPaddingValues()
                    .let { PaddingValues(bottom = it.calculateBottomPadding() + 24.dp) }
            ) {
                items(
                    count = rows.size,
                    key = { rows[it].key },
                    contentType = { if (rows[it] is FormatListRow.Header) 0 else 1 }
                ) { index ->
                    when (val row = rows[index]) {
                        is FormatListRow.Header -> SectionHeader(row.title)
                        is FormatListRow.Entry -> FormatRow(
                            format = row.format,
                            selected = row.format == draft,
                            onClick = { draft = row.format }
                        )
                    }
                }

                // Skeletons stand where the rows still being read will land, so a list
                // holding only the generic entry reads as one that is still filling in
                // rather than as the whole answer.
                if (isLoadingFormats) {
                    item(key = "loading", contentType = 2) {
                        FormatListShimmer(
                            rows = 4,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A header bar or one format, in the order the list draws them. */
@Immutable
private sealed interface FormatListRow {
    val key: String

    data class Header(val title: String) : FormatListRow {
        override val key get() = "header_$title"
    }

    data class Entry(val format: MediaFormat, val section: String) : FormatListRow {
        override val key get() = "${section}_${format.formatId}"
    }
}

/**
 * Flattens the two tabs into the single list the sheet scrolls.
 *
 * The kind being chosen leads, and a section with nothing in it is left out rather than
 * shown as a header with no rows under it.
 */
private fun buildRows(
    info: MediaInfo,
    sort: FormatSort,
    audioFirst: Boolean
): List<FormatListRow> {
    val video = info.videoFormats.sortedBy(sort)
    val audio = info.audioFormats.sortedBy(sort)

    fun section(title: String, formats: List<MediaFormat>): List<FormatListRow> =
        if (formats.isEmpty()) emptyList()
        else buildList {
            add(FormatListRow.Header(title))
            formats.forEach { add(FormatListRow.Entry(it, title)) }
        }

    val videoRows = section("Video", video)
    val audioRows = section("Audio", audio)
    return if (audioFirst) audioRows + videoRows else videoRows + audioRows
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

/** Full width bar naming the kind of stream the rows under it hold. */
@Composable
private fun SectionHeader(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

/**
 * One format, with the badges the source actually reported.
 *
 * The container leads as a block the eye can pick out of a long list, the quality is the
 * headline, and everything measured sits under it as pills that scroll sideways rather
 * than wrapping the row into different heights. A list whose rows are all the same height
 * is what keeps a fast fling smooth.
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
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContainerBadge(text = format.ext.ifBlank { "DEFAULT" })

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    val headline = format.label.uppercase()
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleLarge,
                        // A resolution is short and reads well large. The notes a source
                        // puts on an audio stream are a sentence, and at the same size they
                        // take two lines and shout over the rest of the row.
                        fontSize = if (headline.length > LONG_HEADLINE) 16.sp else 20.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "id: ${format.formatId}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1
                    )
                }

                // Badges are omitted entirely when the extractor did not report them,
                // which is common outside the largest sites.
                val codec = format.codecLabel
                val size = format.sizeLabel
                // The bitrate says something the resolution does not only for audio; on a
                // video row it repeats what the size already showed.
                val bitrate = format.bitrateLabel.takeIf { !format.hasVideo }.orEmpty()

                if (mergeAudioId != null || codec.isNotBlank() ||
                    size.isNotBlank() || bitrate.isNotBlank()
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (mergeAudioId != null) {
                            MetaBadge(
                                text = "id: $mergeAudioId",
                                icon = Icons.Filled.MusicNote,
                                tone = BadgeTone.SOLID
                            )
                        }
                        if (codec.isNotBlank()) MetaBadge(codec)
                        if (size.isNotBlank()) MetaBadge(size, tone = BadgeTone.SIZE)
                        if (bitrate.isNotBlank()) MetaBadge(bitrate, tone = BadgeTone.ACCENT)
                    }
                }
            }

            if (showChevron) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.UnfoldMore,
                    contentDescription = stringResource(R.string.format_selection_change),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** Past this many characters a headline is a sentence rather than a label. */
private const val LONG_HEADLINE = 22

/** The container block a row leads with, sized the same whatever the word inside it is. */
@Composable
private fun ContainerBadge(text: String) {
    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            // A container name is three or four letters; "DEFAULT" is the one that is not,
            // and it is shrunk to fit rather than allowed to widen the block.
            fontSize = if (text.length > 5) 11.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/** How loud a badge is, from a plain measurement to the point of the row. */
enum class BadgeTone {
    /** Codecs and other facts that are read only when something else has been decided. */
    NEUTRAL,

    /** The file size, which has a colour of its own because it is what lists are scanned for. */
    SIZE,

    /** The bitrate, tinted rather than filled so it sits between the two. */
    ACCENT,

    /** The audio track that will be muxed in, which is the one thing here that is a choice. */
    SOLID
}

/** Small pill under a format's headline. */
@Composable
fun MetaBadge(
    text: String,
    icon: ImageVector? = null,
    tone: BadgeTone = BadgeTone.NEUTRAL
) {
    val container = when (tone) {
        BadgeTone.SOLID -> MaterialTheme.colorScheme.primary
        BadgeTone.SIZE -> SizeBadgeContainer
        BadgeTone.ACCENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    }
    val content = when (tone) {
        BadgeTone.SOLID -> MaterialTheme.colorScheme.onPrimary
        BadgeTone.SIZE -> SizeBadgeContent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = content
            )
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = content
        )
    }
}
