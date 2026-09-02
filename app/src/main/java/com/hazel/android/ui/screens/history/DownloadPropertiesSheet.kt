package com.hazel.android.ui.screens.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.HistoryEntry
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.screens.download.FEEDBACK_MS
import com.hazel.android.ui.screens.download.copySheetLink
import com.hazel.android.ui.screens.download.openSheetLink
import com.hazel.android.util.MediaFacts
import com.hazel.android.util.MediaProbeFacts
import kotlinx.coroutines.delay

/**
 * Everything the app knows about one finished download.
 *
 * The list says as much as a row has space for: a title, a size and a date. The rest was
 * either recorded and never shown, or sits in the file itself. This is where it lives,
 * reached from the row's own menu and from holding the row down.
 *
 * Two sources, neither of them expensive. What was asked for comes from the record, which
 * is already in memory. What arrived is read once from the file's header, on a background
 * thread, and only for a file that is still there. Anything either source has nothing to
 * say about is left out rather than shown as blank or zero, so the sheet is exactly as long
 * as there are facts to fill it.
 *
 * The address closes the sheet the way it closes a download sheet, and behaves the same:
 * tapping it copies it and opens it, in the site's own app where that is installed. It is
 * the one line here worth acting on rather than only reading, and for a download whose file
 * has gone it is the way back to the thing itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPropertiesSheet(
    entry: HistoryEntry,
    present: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // One header read per opening, and none at all for a file that is not there. Keyed on
    // the entry, so reopening the same sheet does not read again while it stays composed.
    var facts by remember(entry.id) { mutableStateOf(MediaFacts()) }
    LaunchedEffect(entry.id, present) {
        if (present) facts = MediaProbeFacts.read(context, entry.fileUri)
    }

    // What the last tap on the address did. It clears itself, so nothing has to be
    // dismissed and an old answer does not sit under the details being read.
    var feedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(FEEDBACK_MS)
            feedback = null
        }
    }

    // The screen's own ground rather than the raised surface a sheet takes by default. A
    // sheet is the whole foreground while it is up, and the raised tone read as a grey
    // panel floating on the black behind it. Taken from the theme, so it is the near black
    // in the dark scheme and the warm off white in the light one, and it follows the accent
    // the user picked rather than pinning a colour of its own.
    val sheetColor = MaterialTheme.colorScheme.background
    val panelColor = MaterialTheme.colorScheme.surfaceContainerHigh

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetColor
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                stringResource(R.string.properties_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            // The download itself, so the details underneath are read against the thing
            // they describe rather than against a filename.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(panelColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (entry.thumbnail != null) {
                        AsyncImage(
                            model = entry.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = if (present) 1f else 0.55f,
                            colorFilter = if (present) null else ColorFilter.colorMatrix(
                                ColorMatrix().apply { setToSaturation(0f) }
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            if (entry.isVideo) Icons.Filled.PlayArrow
                            else Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            entry.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Gathered first, then drawn, so a section with nothing in it does not leave a
            // heading and an empty panel behind.
            val media = buildList {
                add(
                    stringResource(R.string.properties_status) to stringResource(
                        if (present) R.string.properties_status_present
                        else R.string.properties_status_deleted
                    )
                )
                add(
                    stringResource(R.string.properties_kind) to stringResource(
                        if (entry.isVideo) R.string.properties_kind_video
                        else R.string.properties_kind_audio
                    )
                )
                entry.formatLabel.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_quality) to it) }
                facts.resolution.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_resolution) to it) }
                val length = formatDuration(
                    entry.durationSeconds.takeIf { it > 0 } ?: facts.durationSeconds
                )
                length.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_length) to it) }
                entry.codec.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_codec) to it) }

                // The file's own figure first: it describes what is on disk, where the
                // recorded one describes the stream that was asked for.
                val bitrate = facts.bitrateKbps.takeIf { it > 0 }
                    ?: entry.bitrateKbps.takeIf { it > 0 }?.toInt()
                bitrate?.let {
                    add(
                        stringResource(R.string.properties_bitrate) to
                                stringResource(R.string.properties_bitrate_value, it)
                    )
                }
            }

            val file = buildList {
                if (entry.sizeBytes > 0) {
                    add(
                        stringResource(R.string.properties_size) to
                                formatFileSize(entry.sizeBytes)
                    )
                }
                entry.container.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_container) to it) }
                facts.mimeType.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_type) to it) }
                entry.embedded.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_embedded) to it) }
                entry.fileName.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_file) to it) }
                entry.savedPath.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_saved_to) to it) }
            }

            val origin = buildList {
                add(
                    stringResource(R.string.properties_downloaded) to
                            formatStamp(entry.completedAt)
                )
                entry.site.ifBlank { null }
                    ?.let { add(stringResource(R.string.properties_source) to it) }
            }

            DetailPanel(media, panelColor, missing = !present)
            Spacer(modifier = Modifier.height(12.dp))
            DetailPanel(file, panelColor)
            Spacer(modifier = Modifier.height(12.dp))
            DetailPanel(origin, panelColor)

            Spacer(modifier = Modifier.height(22.dp))

            // The line that closes the sheet, as the download sheet closes with the same
            // thing. Copied because the address is often wanted somewhere else, opened
            // because the other reason to look at a link is to go and see what is behind it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    entry.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = entry.url.isNotBlank()) {
                            copySheetLink(context, entry.url)
                            feedback = context.getString(
                                if (openSheetLink(context, entry.url)) {
                                    R.string.properties_link_copied_opened
                                } else {
                                    R.string.properties_link_copied
                                }
                            )
                        }
                )
            }

            AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.inverseSurface
                    ) {
                        Text(
                            feedback.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * A group of facts, hairline separated. Draws nothing at all when the group is empty, which
 * is what keeps a sheet about an old entry from being mostly headings.
 */
@Composable
private fun DetailPanel(
    rows: List<Pair<String, String>>,
    color: Color,
    missing: Boolean = false
) {
    if (rows.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = color
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    )
                }
                DetailRow(
                    label = label,
                    value = value,
                    // The status leads the panel, so the first row is the one that says
                    // the file has gone. Asked by position rather than by matching the
                    // label against a word, which is a comparison that stops holding the
                    // moment the label is translated.
                    emphasis = missing && index == 0
                )
            }
        }
    }
}

/**
 * One fact, named on the left and given on the right.
 *
 * The value is the part that gives way when the row runs out of width: a label is one word
 * and a filename is forty, so weighting the label would shorten the wrong half.
 */
@Composable
private fun DetailRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(94.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (emphasis) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** The date and the time, since two downloads of the same thing differ only by the hour. */
private fun formatStamp(millis: Long): String = runCatching {
    java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}.getOrDefault("")
