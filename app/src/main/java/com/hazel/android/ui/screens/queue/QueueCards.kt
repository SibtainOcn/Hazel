package com.hazel.android.ui.screens.queue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.QueuedDownload
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.components.CardTag
import com.hazel.android.ui.components.formatDateTime
import com.hazel.android.util.UrlExtractor

/** Scrim over artwork, dark at the top for the title and at the bottom for the tags. */
private val ArtworkScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.65f),
    0.45f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.7f)
)

/** The home screen card's scrim, a little darker, as a failed card carries more text. */
private val FailedScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.78f),
    0.45f to Color.Black.copy(alpha = 0.15f),
    0.65f to Color.Black.copy(alpha = 0.15f),
    1f to Color.Black.copy(alpha = 0.8f)
)

/** Three buttons share a row under the log, so each keeps its words rather than its margins. */
private val LogButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)

/** A true red, bright enough to read on dark artwork in either theme. */
private val ErrorOnArtwork = Color(0xFFFF5A52)

/** A link waiting its turn, with the choice it will download as. Tapping it shows its details. */
@Composable
fun QueuedCard(
    item: QueuedDownload,
    onRemove: () -> Unit,
    onOpen: () -> Unit = {}
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
                .clickable(
                    onClickLabel = stringResource(R.string.format_sheet_section_details),
                    onClick = onOpen
                )
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

/**
 * A download that stopped with an error, drawn as the home screen draws a link: artwork
 * with the title over it. The reason sits under the title, and the log and a retry are
 * pills in the corner, so the whole card is the artwork rather than artwork and a form.
 * A tap on the artwork opens the link's sheet again, to download it some other way, and a
 * long press starts picking failures to remove. While picking, [selected] is whether this
 * one is picked, a tap picks it, and the card's own actions step aside.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FailedCard(
    item: FailedDownload,
    onOpen: () -> Unit,
    onViewLog: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onLongPress: () -> Unit = {},
    selected: Boolean? = null,
    /** Its link is being read or downloaded again; the outcome settles the entry. */
    retrying: Boolean = false
) {
    val picking = selected != null
    val reason = remember(item.errorLog) { shortReason(item.errorLog) }
    val host = remember(item.url) { hostOf(item.url) }
    // A link that failed before it was read has a title of its own address and nothing
    // else; its site stands in as the author, and a YouTube link still finds its artwork.
    val title = item.title.takeUnless { it.isBlank() || it == item.url } ?: item.url
    // When it failed rides with the source, so the corner keeps room for the actions.
    val author = listOf(item.author.ifBlank { host }, formatDateTime(item.failedAt))
        .filter { it.isNotBlank() }.joinToString(" · ")
    val thumbnail = item.thumbnail ?: remember(item.url) { youtubeThumbnail(item.url) }

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
                .combinedClickable(
                    onClickLabel = if (picking) null else stringResource(R.string.queue_failed_choose_again),
                    onLongClick = onLongPress,
                    onClick = onOpen
                )
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(FailedScrim))

            // Picked: a frosted wash of the accent over the whole card, rather than a frame
            // around it, so the card reads as lit up and stays the same size.
            if (selected == true) {
                val accent = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.06f))
                        .background(
                            Brush.verticalGradient(
                                0f to accent.copy(alpha = 0.34f),
                                1f to accent.copy(alpha = 0.16f)
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 12.dp, end = 50.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        author,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (reason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = ErrorOnArtwork,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selected != null) {
                SelectMark(selected, modifier = Modifier.align(Alignment.TopEnd))
            } else {
                DismissButton(
                    description = stringResource(R.string.history_failed_dismiss),
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // The state, left; what can be done about it, right.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KindIcon(item.isVideo)
                    if (retrying) CardTag(
                        text = stringResource(R.string.queue_failed_retrying),
                        background = MaterialTheme.colorScheme.primary,
                        foreground = MaterialTheme.colorScheme.onPrimary
                    ) else CardTag(
                        text = stringResource(R.string.download_failed),
                        background = MaterialTheme.colorScheme.error,
                        foreground = MaterialTheme.colorScheme.onError
                    )
                }
                if (!picking) Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CardPill(
                        icon = Icons.Filled.Description,
                        label = stringResource(R.string.history_failed_error_log),
                        onClick = onViewLog
                    )
                    if (!retrying) CardPill(
                        icon = Icons.Filled.Refresh,
                        label = stringResource(R.string.history_failed_retry),
                        onClick = onRetry,
                        background = MaterialTheme.colorScheme.primary,
                        foreground = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * A failure's whole log, on a sheet tall enough to read it: the link it belongs to, the
 * log with its error and warning lines picked out, and copy and retry under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailureLogSheet(
    item: FailedDownload,
    onCopy: () -> Unit,
    onCopyUrl: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = MaterialTheme.colorScheme.tertiary
    val emptyText = stringResource(R.string.history_empty_failed)
    val log = remember(item.errorLog, errorColor, warningColor) {
        buildAnnotatedString {
            val lines = item.errorLog.trim().ifBlank { emptyText }.lines()
            lines.forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                when {
                    trimmed.startsWith("ERROR:") ->
                        withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.SemiBold)) { append(line) }
                    trimmed.startsWith("WARNING:") ->
                        withStyle(SpanStyle(color = warningColor)) { append(line) }
                    else -> append(line)
                }
                if (index < lines.lastIndex) append('\n')
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.history_failed_error_log),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                item.title.takeUnless { it.isBlank() || it == item.url } ?: item.url,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.title.isNotBlank() && item.title != item.url) {
                Text(
                    item.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                formatDateTime(item.failedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            item.stoppedAt?.let { stage ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    stage,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // As tall as the log, up to most of the screen, after which it scrolls. Lines wrap,
            // so the whole of an error reads without scrolling sideways on a phone.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                SelectionContainer {
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(onClick = onCopy, modifier = Modifier.weight(1f), contentPadding = LogButtonPadding) {
                    Text(stringResource(R.string.history_failed_copy_log), maxLines = 1)
                }
                FilledTonalButton(onClick = onCopyUrl, modifier = Modifier.weight(1f), contentPadding = LogButtonPadding) {
                    Text(stringResource(R.string.failed_copy_url), maxLines = 1)
                }
                Button(onClick = onRetry, modifier = Modifier.weight(1f), contentPadding = LogButtonPadding) {
                    Text(stringResource(R.string.history_failed_retry), maxLines = 1)
                }
            }
        }
    }
}

/**
 * The line that says why, without the engine's framing: "ERROR: [youtube] abc123: This
 * video is unavailable" reads "This video is unavailable". Warnings print before the error
 * that stopped the run, so the last error is the one.
 */
private fun shortReason(log: String): String {
    val lines = log.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val line = lines.lastOrNull { it.startsWith("ERROR:") } ?: lines.firstOrNull() ?: return ""
    return line.removePrefix("ERROR:").trim()
        .replace(Regex("""^\[[^\]]+]\s*[^\s:]+:\s*"""), "")
        .replace(Regex("""^\[[^\]]+]\s*"""), "")
        .ifBlank { line }
}

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.").removePrefix("m.") }.getOrDefault("")

private fun youtubeThumbnail(url: String): String? =
    UrlExtractor.extractYouTubeId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

/** A small action on the artwork, in the shape of the tags beside it. */
@Composable
private fun CardPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    background: Color = Color.Black.copy(alpha = 0.6f),
    foreground: Color = Color.White
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = foreground)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1
        )
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

/** Whether a card is picked, where its dismiss button sits otherwise. */
@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f))
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary
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
