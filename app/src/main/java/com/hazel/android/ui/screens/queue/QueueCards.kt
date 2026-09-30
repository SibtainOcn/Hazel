package com.hazel.android.ui.screens.queue

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.QueuedDownload
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.components.CardTag
import com.hazel.android.ui.components.formatDateTime

/** Scrim over artwork, dark at the top for the title and at the bottom for the tags. */
private val ArtworkScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.65f),
    0.45f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.7f)
)

/** A link waiting its turn, with the choice it will download as. */
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
            Artwork(item.thumbnail, item.hasVideo)
            Box(modifier = Modifier.fillMaxSize().background(ArtworkScrim))

            TitleBlock(
                title = item.title,
                author = item.author,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 48.dp)
            )

            DismissButton(
                description = stringResource(R.string.download_remove_link),
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KindIcon(item.hasVideo)
                formatDuration(item.durationSeconds).takeIf { it.isNotBlank() }?.let { CardTag(it) }
                item.formatLabel.takeIf { it.isNotBlank() }?.let { CardTag(it) }
                if (item.fileSizeBytes > 0) CardTag(formatFileSize(item.fileSizeBytes))
            }
        }
    }
}

/** A download that stopped with an error, with its log and a way to try again. */
@Composable
fun FailedCard(
    item: FailedDownload,
    onViewLog: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
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
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize().background(ArtworkScrim))

                CardTag(
                    text = stringResource(R.string.history_tab_failed),
                    background = MaterialTheme.colorScheme.error,
                    foreground = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )

                DismissButton(
                    description = stringResource(R.string.history_failed_dismiss),
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd)
                )

                TitleBlock(
                    title = item.title.ifBlank { item.url },
                    author = item.author,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            ) {
                Text(
                    text = item.errorLog.lines().firstOrNull { it.isNotBlank() }
                        ?: stringResource(R.string.history_empty_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(item.failedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onViewLog) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.history_failed_error_log),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    FilledTonalButton(
                        onClick = onRetry,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.history_failed_retry),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Artwork(thumbnail: String?, hasVideo: Boolean) {
    if (thumbnail != null) {
        AsyncImage(
            model = thumbnail,
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
                if (hasVideo) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
private fun TitleBlock(title: String, author: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (author.isNotBlank()) {
            Text(
                author,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DismissButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun KindIcon(hasVideo: Boolean) {
    Icon(
        if (hasVideo) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = Color.White
    )
}
