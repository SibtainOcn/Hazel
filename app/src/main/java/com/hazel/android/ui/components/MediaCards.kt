package com.hazel.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.download.ProcessingStep
import com.hazel.android.download.BatchItem
import com.hazel.android.download.BatchState
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.motion.M3Motion

/** Dark pill drawn over the thumbnail. */
@Composable
fun OverlayChip(text: String, bold: Boolean = false) {
    Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.6f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Flat tag in the thumbnail's corner, for the duration and the state marker. */
@Composable
fun CornerTag(
    text: String,
    background: Color = Color.Black.copy(alpha = 0.7f),
    foreground: Color = Color.White
) {
    Surface(shape = RoundedCornerShape(4.dp), color = background) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * A download in the queue: its artwork, title and author, and where it has got to.
 *
 * From the first byte to the saved file the artwork carries the stage track. While the
 * streams are fetched, the line from Fetch fills with the transfer, the percentage sits beside
 * Fetch, and how much of how much has arrived and how long is left sit under the
 * track; after that the track moves through the stages yt-dlp runs. Pausing, resuming and cancelling are in the corner menu.
 *
 * A paused card resumes when tapped, its pause sign included, and its pill says how much is
 * already in hand. [onDetails], where given, adds Details to the corner menu.
 */
@Composable
fun MediaCard(
    info: MediaInfo,
    isDownloading: Boolean,
    isProcessing: Boolean = false,
    processingSteps: List<ProcessingStep> = emptyList(),
    processingStep: Int = 0,
    progress: Float = 0f,
    totalBytes: Long = 0L,
    eta: String = "",
    isComplete: Boolean = false,
    batchItem: BatchItem? = null,
    waitingForWifi: Boolean = false,
    alreadyDownloaded: Boolean = false,
    onOpenSheet: () -> Unit = {},
    onCancel: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onDetails: (() -> Unit)? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = M3Motion.emphasized(300),
        label = "cardProgress"
    )

    var menuOpen by remember { mutableStateOf(false) }
    val isPaused = batchItem?.state == BatchState.PAUSED
    val isQueued = batchItem?.state == BatchState.QUEUED
    val inHand = isDownloading || isPaused || isQueued
    val transferring = isDownloading && !isProcessing

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    when {
                        isDownloading -> Modifier
                        isPaused -> Modifier.clickable(
                            onClickLabel = stringResource(R.string.download_resume),
                            onClick = onResume
                        )
                        else -> Modifier.clickable(onClick = onOpenSheet)
                    }
                )
        ) {
            // Sources without artwork simply show the placeholder glyph.
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
            }

            // Dark gradient overlay from top and bottom so text is always readable over thumbnail
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            0.4f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
            )

            if (isDownloading || isPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }

            // A paused link says so in the middle of its artwork, where the moving stage
            // track would otherwise be, and nothing on it moves.
            if (isPaused && !isDownloading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.download_paused),
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }
            }

            // The stage track goes under the title and the readouts, so its shade dims the
            // artwork and not the words laid over it. Fetch is the stage until the transfer is
            // over, and its line fills with it.
            if (isDownloading && processingSteps.isNotEmpty()) {
                ProcessingTracker(
                    steps = processingSteps,
                    current = if (isProcessing) processingStep else 0,
                    fill = if (isProcessing) null else animatedProgress,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Title and author directly overlaid on top of the thumbnail
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        start = 14.dp,
                        top = 12.dp,
                        end = if (inHand) 48.dp else 14.dp
                    )
            ) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (info.uploader.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        info.uploader,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (inHand) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .zIndex(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.download_options),
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        if (isPaused) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_resume)) },
                                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onResume()
                                }
                            )
                        } else if (isDownloading) {
                            // Nothing is left to hold once the file is being worked on.
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_pause)) },
                                leadingIcon = { Icon(Icons.Filled.Pause, contentDescription = null) },
                                enabled = !isProcessing,
                                onClick = {
                                    menuOpen = false
                                    onPause()
                                }
                            )
                        }
                        onDetails?.let { open ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.format_sheet_section_details)) },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    open()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_cancel)) },
                            leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onCancel()
                            }
                        )
                    }
                }
            }

            // The figures sit under the track, in one pill on one line, for as long as bytes
            // are moving, or held there while paused. How far along is on the track itself.
            if (transferring || isPaused) {
                val figures = buildList {
                    if (isPaused) {
                        add(stringResource(R.string.download_paused))
                        // From the figure itself rather than the animation towards it, and
                        // only once something has arrived: "0% downloaded" says nothing.
                        val percent = (progress * 100f).toInt().coerceIn(0, 99)
                        if (percent >= 1) add(stringResource(R.string.download_percent_downloaded, percent))
                    }
                    if (totalBytes > 0) {
                        // A paused figure stands still, so it is shown as it is rather than
                        // counted up to each time the card is drawn again.
                        val shown = if (isPaused) progress else animatedProgress
                        val done = (totalBytes * shown).toLong().coerceAtLeast(1L)
                        add("${formatFileSize(done)} / ${formatFileSize(totalBytes)}")
                    }
                    if (transferring && eta.isNotBlank()) add(stringResource(R.string.queue_header_eta, eta))
                }
                if (figures.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            figures.forEachIndexed { index, figure ->
                                if (index > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.6f))
                                    )
                                }
                                Text(
                                    figure,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isPaused && index == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            if (waitingForWifi && !isDownloading && !isPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )

                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = Color.White
                        )
                        Text(
                            stringResource(R.string.download_waiting_wifi),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom left corner carries the duration, and bottom right corner carries
            // whatever this link's state is: queued, failed, saved, or downloaded.
            if (!isDownloading && !isPaused) {
                val duration = formatDuration(info.durationSeconds)
                if (duration.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        CornerTag(text = duration)
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        batchItem?.state == BatchState.FAILED -> CornerTag(
                            text = batchItem.error ?: stringResource(R.string.download_failed),
                            background = MaterialTheme.colorScheme.error,
                            foreground = MaterialTheme.colorScheme.onError
                        )
                        isComplete -> CornerTag(
                            text = stringResource(R.string.download_saved),
                            background = MaterialTheme.colorScheme.primary,
                            foreground = MaterialTheme.colorScheme.onPrimary
                        )
                        batchItem?.state == BatchState.QUEUED -> CornerTag(text = stringResource(R.string.download_queued))
                        alreadyDownloaded -> CornerTag(text = stringResource(R.string.download_downloaded))
                    }
                }
            }
        }
    }
}
