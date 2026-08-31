package com.hazel.android.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.HistoryEntry
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize

/**
 * Raised when a link about to be read has already been downloaded and the file is still on
 * the device.
 *
 * It shows the copy that exists rather than describing it, because the decision being asked
 * for is whether that copy is the one wanted. A filename cannot answer that; the artwork,
 * the running time, whether it was saved as video or audio and how large it came out can.
 * The alternative to answering well here is a repeat download, which on a slow connection is
 * the most expensive thing the app can do by mistake.
 */
@Composable
fun AlreadyDownloadedDialog(
    entry: HistoryEntry,
    onPlay: () -> Unit,
    onOpenLocation: () -> Unit,
    onDownloadAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // Material caps a dialog at a width meant for a line of text and two buttons. This
        // one carries a card with artwork beside a title, so it takes the screen's width
        // less a margin instead, which is what stops the title wrapping after three words.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        // Darker than the surrounding surfaces rather than lighter. A dialog that lifts off
        // a near-black background with a pale panel glares; one that sits at the darkest
        // tone and is separated by its outline alone stays quiet at night.
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                stringResource(R.string.already_downloaded_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.already_downloaded_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(modifier = Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(width = 96.dp, height = 62.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (entry.thumbnail != null) {
                                AsyncImage(
                                    model = entry.thumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    if (entry.isVideo) Icons.Filled.Videocam
                                    else Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }

                            val duration = formatDuration(entry.durationSeconds)
                            if (duration.isNotBlank()) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        duration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.title.ifBlank { entry.fileName },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.author.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    entry.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // The facts that decide whether the existing copy is the one
                            // wanted: what kind it is, how big it came out, and how old it
                            // is. They wrap rather than share one line, because three tags
                            // beside a thumbnail do not always fit across a narrow screen,
                            // and a Row squeezes the last one to a single character rather
                            // than moving it down.
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DetailTag(if (entry.isVideo) stringResource(R.string.already_downloaded_video) else stringResource(R.string.already_downloaded_audio))
                                formatFileSize(entry.sizeBytes)
                                    .takeIf { it.isNotBlank() }
                                    ?.let { DetailTag(it) }
                                DetailTag(relativeAge(entry.completedAt))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // The location is a place, not a caption, so it behaves like one: tapping
                // it hands the folder to the device's file browser. Someone deciding
                // whether to download again often wants to look at what is already there,
                // and the alternative is reading a path and navigating to it by hand.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                    onClick = onOpenLocation
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            entry.savedPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        // All three sit in one group rather than being split across the dialog's width.
        // Three actions spaced apart read as three unrelated choices; grouped and weighted
        // they read as one decision with an obvious default. Playing what already exists is
        // usually the answer here, so it is present but quiet, and the only action that
        // costs anything is the one carrying the emphasis.
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = COMPACT_PADDING
                ) {
                    Text(stringResource(R.string.already_downloaded_keep), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = onPlay,
                    contentPadding = COMPACT_PADDING
                ) {
                    Text(stringResource(R.string.already_downloaded_play), fontWeight = FontWeight.Medium)
                }
                FilledTonalButton(
                    onClick = onDownloadAgain,
                    contentPadding = COMPACT_PADDING
                ) {
                    Text(stringResource(R.string.already_downloaded_download_again), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}

/**
 * Tighter than a button's default padding, so three actions fit one row on a narrow screen
 * without any of them wrapping or being cut.
 */
private val COMPACT_PADDING = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

/** Small flat label for one fact about the existing copy. */
@Composable
private fun DetailTag(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * How long ago the copy was saved, in the roughest unit that still says something. An exact
 * timestamp would be precision nobody reads; "yesterday" is what decides the question.
 */
@Composable
private fun relativeAge(completedAt: Long): String {
    val elapsed = System.currentTimeMillis() - completedAt
    val minutes = elapsed / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> stringResource(R.string.already_downloaded_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.already_downloaded_minutes, minutes.toInt(), minutes.toInt())
        hours < 24 -> pluralStringResource(R.plurals.already_downloaded_hours, hours.toInt(), hours.toInt())
        days == 1L -> stringResource(R.string.already_downloaded_yesterday)
        days < 30 -> pluralStringResource(R.plurals.already_downloaded_days, days.toInt(), days.toInt())
        else -> pluralStringResource(R.plurals.already_downloaded_months, (days / 30).toInt(), (days / 30).toInt())
    }
}
