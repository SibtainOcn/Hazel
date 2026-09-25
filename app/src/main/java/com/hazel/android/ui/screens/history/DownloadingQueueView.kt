package com.hazel.android.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.QueuedDownload
import com.hazel.android.download.DownloadState
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.components.MediaCard

/**
 * Unified Downloading & Queue view displaying active running downloads
 * alongside pending queued items in a single cohesive list.
 */
@Composable
fun DownloadingQueueView(
    state: DownloadState,
    queueItems: List<QueuedDownload>,
    isDownloadingActive: Boolean,
    activeDownloadMatches: Boolean,
    query: String,
    isFiltered: Boolean,
    onCancelActive: () -> Unit,
    onPauseActive: () -> Unit,
    onResumeActive: () -> Unit,
    onRemoveQueued: (QueuedDownload) -> Unit,
    modifier: Modifier = Modifier
) {
    val showActive = isDownloadingActive && activeDownloadMatches
    val hasItems = showActive || queueItems.isNotEmpty()

    if (!hasItems) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                if (isFiltered) Icons.Filled.Download else Icons.Filled.HourglassEmpty,
                contentDescription = null,
                modifier = Modifier.size(if (isFiltered) 48.dp else 56.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(if (isFiltered) 12.dp else 16.dp))
            Text(
                if (isFiltered) stringResource(R.string.history_empty_filtered)
                else stringResource(R.string.history_empty_downloading_queue),
                style = if (isFiltered) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (isFiltered) FontWeight.Normal else FontWeight.SemiBold,
                color = if (isFiltered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (!isFiltered) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.history_empty_downloading_queue_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showActive) {
                item(key = "active_download") {
                    val info = state.info ?: MediaInfo(
                        url = state.url,
                        title = state.fileName.ifBlank { stringResource(R.string.history_tab_downloading) },
                        uploader = "",
                        thumbnail = null,
                        durationSeconds = 0,
                        videoFormats = emptyList<MediaFormat>(),
                        audioFormats = emptyList<MediaFormat>()
                    )
                    MediaCard(
                        info = info,
                        isDownloading = true,
                        isProcessing = state.isProcessing,
                        progress = state.progress,
                        totalBytes = state.totalBytes,
                        waitingForWifi = state.waitingForWifi,
                        onCancel = onCancelActive,
                        onPause = onPauseActive,
                        onResume = onResumeActive
                    )
                }
            }

            if (showActive && queueItems.isNotEmpty()) {
                item(key = "queued_section_header") {
                    Text(
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.download_links,
                            queueItems.size,
                            queueItems.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                    )
                }
            }

            items(queueItems, key = { it.url }) { item ->
                QueuedCard(item = item, onRemove = { onRemoveQueued(item) })
            }
        }
    }
}

@Composable
fun QueuedCard(
    item: QueuedDownload,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            if (item.thumbnail != null) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (item.hasVideo) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                    )
                }
            }

            // Dual gradient scrim for title & bottom chips
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.65f),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.7f)
                        )
                    )
            )

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.author.isNotBlank()) {
                    Text(
                        item.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.download_remove_link),
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (item.hasVideo) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                val duration = formatDuration(item.durationSeconds)
                if (duration.isNotBlank()) QueueTag(duration)
                if (item.formatLabel.isNotBlank()) QueueTag(item.formatLabel)
                if (item.fileSizeBytes > 0) QueueTag(formatFileSize(item.fileSizeBytes))
            }
        }
    }
}


@Composable
private fun QueueTag(
    text: String,
    background: Color = Color.Black.copy(alpha = 0.6f),
    foreground: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(4.dp), color = background, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
