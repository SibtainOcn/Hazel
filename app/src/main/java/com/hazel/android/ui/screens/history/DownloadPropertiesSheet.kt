package com.hazel.android.ui.screens.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.HistoryEntry
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.keepFlingInSheet
import com.hazel.android.ui.screens.download.FEEDBACK_MS
import com.hazel.android.ui.screens.download.FeedbackToast
import com.hazel.android.util.MediaFacts
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaProbeFacts
import kotlinx.coroutines.delay

/**
 * Everything known about one finished download, and what can be done with it.
 *
 * Opens at half height. The title leads, the picture and who posted it sit under it, then
 * the quick figures, the actions on the file, the facts in groups, and the ways to get it
 * again. Actions that need the file are dimmed once it has gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPropertiesSheet(
    entry: HistoryEntry,
    present: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDownloadAgain: (() -> Unit)? = null,
    onFetchAgain: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    // One header read per opening, and none at all for a file that is not there.
    var facts by remember(entry.id) { mutableStateOf(MediaFacts()) }
    LaunchedEffect(entry.id, present) {
        if (present) facts = runCatching { MediaProbeFacts.read(context, entry.fileUri) }.getOrDefault(MediaFacts())
    }

    // A new object per message, so the same one twice in a row still restarts its time.
    var feedback by remember { mutableStateOf<PropertiesFeedback?>(null) }
    var shownFeedback by remember { mutableStateOf("") }
    LaunchedEffect(feedback) {
        val current = feedback ?: return@LaunchedEffect
        shownFeedback = current.message
        delay(FEEDBACK_MS)
        feedback = null
    }
    val copied = stringResource(R.string.properties_copied)
    val copy: (String) -> Unit = remember(copied) {
        { text -> if (copyText(context, text)) feedback = PropertiesFeedback(copied) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Box {
            Column(
                modifier = Modifier
                    .keepFlingInSheet()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Header(entry, present, facts)
                StatTiles(entry, facts)
                FileActions(entry, present)
                Details(entry, facts, onCopy = copy)
                if (entry.url.isNotBlank() && (onDownloadAgain != null || onFetchAgain != null)) {
                    AgainGroup(onDownloadAgain, onFetchAgain)
                }
                ListActions(present, onRemove, onDelete)
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
            ) {
                FeedbackToast(shownFeedback)
            }
        }
    }
}

private class PropertiesFeedback(val message: String)

@Composable
private fun Header(entry: HistoryEntry, present: Boolean, facts: MediaFacts) {
    Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            entry.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 136.dp, height = 77.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                com.hazel.android.ui.components.ArtworkPlaceholder()
                if (entry.thumbnail != null) {
                    AsyncImage(
                        model = entry.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = if (present) 1f else 0.55f,
                        colorFilter = if (present) null else GreyFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                val length = formatDuration(entry.durationSeconds.takeIf { it > 0 } ?: facts.durationSeconds)
                if (length.isNotBlank()) {
                    Text(
                        length,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.author.isNotBlank()) {
                    Text(
                        entry.author,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val line = listOf(entry.site, entry.formatLabel).filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotBlank()) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTiles(entry: HistoryEntry, facts: MediaFacts) {
    val bitrate = facts.bitrateKbps.takeIf { it > 0 } ?: entry.bitrateKbps.takeIf { it > 0 }?.toInt()
    val tiles = buildList {
        if (entry.sizeBytes > 0) add(formatFileSize(entry.sizeBytes) to stringResource(R.string.properties_size))
        entry.container.ifBlank { null }?.let { add(it to stringResource(R.string.properties_container)) }
        bitrate?.let { add(stringResource(R.string.properties_bitrate_value, it) to stringResource(R.string.properties_bitrate)) }
        facts.resolution.ifBlank { null }?.let { add(it.replace('x', '×') to stringResource(R.string.properties_resolution)) }
    }
    if (tiles.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.forEachIndexed { index, (value, label) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(rowShape(index, tiles.size, horizontal = true))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileActions(entry: HistoryEntry, present: Boolean) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        CookieAction(Icons.Filled.PlayArrow, R.string.format_sheet_play, present, primary, MaterialTheme.colorScheme.onPrimary) {
            MediaOpener.play(context, entry.fileUri, entry.isVideo)
        }
        CookieAction(Icons.AutoMirrored.Filled.OpenInNew, R.string.media_open_chooser, present, neutral, MaterialTheme.colorScheme.onSurface) {
            MediaOpener.openWith(context, entry.fileUri, entry.isVideo)
        }
        CookieAction(Icons.Filled.FolderOpen, R.string.properties_show_file, true, neutral, MaterialTheme.colorScheme.onSurface) {
            MediaOpener.showFile(context, entry.fileUri, entry.isVideo)
        }
        CookieAction(Icons.Filled.Share, R.string.properties_share, present, neutral, MaterialTheme.colorScheme.onSurface) {
            MediaOpener.share(context, entry.fileUri, entry.isVideo)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieAction(
    icon: ImageVector,
    @StringRes label: Int,
    enabled: Boolean,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(MaterialShapes.Cookie7Sided.toShape())
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = content)
        }
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(entry: HistoryEntry, facts: MediaFacts, onCopy: (String) -> Unit) {
    val context = LocalContext.current
    val media = buildList<@Composable () -> Unit> {
        entry.formatLabel.ifBlank { null }?.let { add { FactRow(stringResource(R.string.properties_quality), it) } }
        formatDuration(entry.durationSeconds.takeIf { it > 0 } ?: facts.durationSeconds).ifBlank { null }
            ?.let { add { FactRow(stringResource(R.string.properties_length), it) } }
        entry.codec.ifBlank { null }?.let { add { FactRow(stringResource(R.string.properties_codec), it) } }
        facts.mimeType.ifBlank { null }?.let { add { FactRow(stringResource(R.string.properties_type), it) } }
        val embedded = entry.embedded.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (embedded.isNotEmpty()) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.properties_embedded))
                Spacer(modifier = Modifier.width(12.dp))
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    embedded.forEach { Chip(it) }
                }
            }
        }
    }
    val file = buildList<@Composable () -> Unit> {
        entry.fileName.ifBlank { null }?.let { name ->
            add { StackedRow(stringResource(R.string.properties_name), name, Icons.Filled.ContentCopy, R.string.sheet_link_copy) { onCopy(name) } }
        }
        entry.savedPath.ifBlank { null }?.let { path ->
            add {
                StackedRow(stringResource(R.string.properties_saved_to), path, Icons.Filled.FolderOpen, R.string.properties_show_file) {
                    MediaOpener.showFile(context, entry.fileUri, entry.isVideo)
                }
            }
        }
    }
    val source = buildList<@Composable () -> Unit> {
        add { FactRow(stringResource(R.string.properties_downloaded), formatStamp(entry.completedAt)) }
        if (entry.url.isNotBlank()) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.url.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                SquareIcon(Icons.Filled.ContentCopy, R.string.sheet_link_copy) { onCopy(entry.url) }
                Spacer(modifier = Modifier.width(6.dp))
                SquareIcon(Icons.Filled.Public, R.string.docs_open_in_browser) { openInBrowser(context, entry.url) }
            }
        }
    }
    Group(R.string.properties_section_media, media)
    Group(R.string.properties_file, file)
    Group(R.string.properties_source, source)
}

/** Rows joined into one rounded block, with tight corners between them. */
@Composable
private fun Group(@StringRes title: Int, rows: List<@Composable () -> Unit>) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(title)
        rows.forEachIndexed { index, row ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(rowShape(index, rows.size))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) { row() }
        }
    }
}

@Composable
private fun SectionLabel(@StringRes title: Int) {
    Text(
        stringResource(title).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(modifier = Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StackedRow(label: String, value: String, icon: ImageVector, @StringRes action: Int, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        SquareIcon(icon, action, onAction)
    }
}

@Composable
private fun SquareIcon(icon: ImageVector, @StringRes description: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClickLabel = stringResource(description), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AgainGroup(onDownloadAgain: (() -> Unit)?, onFetchAgain: (() -> Unit)?) {
    val rows = buildList {
        onDownloadAgain?.let { add(Triple(Icons.Filled.Download, R.string.properties_download_again to R.string.properties_download_again_hint, it)) }
        onFetchAgain?.let { add(Triple(Icons.Filled.Refresh, R.string.properties_fetch_again to R.string.properties_fetch_again_hint, it)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(R.string.properties_section_again)
        rows.forEachIndexed { index, (icon, text, onClick) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowShape(index, rows.size))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(start = 12.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(text.first), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(text.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ListActions(present: Boolean, onRemove: () -> Unit, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SplitButton(R.string.history_card_remove, splitShape(start = true), MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), onRemove)
        if (present) {
            SplitButton(R.string.history_card_delete, splitShape(start = false), MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer, Modifier.weight(1f), onDelete)
        }
    }
}

@Composable
private fun SplitButton(
    @StringRes label: Int,
    shape: Shape,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = shape, color = container, contentColor = content, modifier = modifier.heightIn(min = 52.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
        }
    }
}

private val Outer: Dp = 24.dp
private val Inner: Dp = 6.dp

/** The corners of one row in a joined group: round on the outside, tight between rows. */
private fun rowShape(index: Int, count: Int, horizontal: Boolean = false): Shape {
    val first = index == 0
    val last = index == count - 1
    val a = if (first) Outer else Inner
    val b = if (last) Outer else Inner
    return if (horizontal) RoundedCornerShape(topStart = a, bottomStart = a, topEnd = b, bottomEnd = b)
    else RoundedCornerShape(topStart = a, topEnd = a, bottomStart = b, bottomEnd = b)
}

private fun splitShape(start: Boolean): Shape =
    if (start) RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 8.dp, bottomEnd = 8.dp)
    else RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 26.dp, bottomEnd = 26.dp)

private val GreyFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private fun copyText(context: Context, text: String): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
}.isSuccess

private fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(intent) }.isSuccess ||
        runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!opened) android.widget.Toast.makeText(context, context.getString(R.string.media_open_no_app), android.widget.Toast.LENGTH_SHORT).show()
}

/** The date and the time, since two downloads of the same thing differ only by the hour. */
private fun formatStamp(millis: Long): String = runCatching {
    java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
}.getOrDefault("")
