package com.hazel.android.ui.screens.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.util.MediaOpener
import com.hazel.android.data.HistoryFilter
import com.hazel.android.data.HistorySort
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.components.rememberPresence
import com.hazel.android.util.MediaPresence
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything that has finished downloading.
 *
 * The list is driven by a flow, so a download that finishes while this screen is open
 * appears without anything being refreshed. Each row also checks whether its file is still
 * on the device, because a download can be deleted from a file manager long after it was
 * made and a row that silently does nothing when tapped is worse than one that says the
 * file is gone.
 */
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val history by DownloadHistoryRepository.getHistory(context)
        .collectAsState(initial = emptyList())

    // Big artwork or a tight list. Remembered between launches rather than only across
    // configuration changes: it is how somebody reads this screen, not a choice they make
    // again every time they open it.
    val compact by SettingsRepository.getHistoryCompact(context).collectAsState(initial = false)

    var sort by remember { mutableStateOf(HistorySort.NEWEST) }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var properties by remember { mutableStateOf<HistoryEntry?>(null) }

    // Asked once per entry and then asked again on every return to the foreground, since
    // deleting a download happens in another app and that is when the answer held here
    // stops being true.
    val presence = rememberPresence(history)

    val visible = remember(history, sort, filter, query) {
        history
            .filter { entry ->
                when (filter) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.AUDIO -> !entry.isVideo
                    HistoryFilter.VIDEO -> entry.isVideo
                }
            }
            .filter { entry ->
                query.isBlank() ||
                        entry.title.contains(query, ignoreCase = true) ||
                        entry.author.contains(query, ignoreCase = true)
            }
            .let { entries ->
                when (sort) {
                    HistorySort.NEWEST -> entries.sortedByDescending { it.completedAt }
                    HistorySort.TITLE -> entries.sortedBy { it.title.lowercase() }
                    HistorySort.SIZE -> entries.sortedByDescending { it.sizeBytes }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Always offered, not only once the list is long. A short list still reads
            // differently in the two layouts, and a control that comes and goes with the
            // item count is one nobody learns is there.
            if (visible.isNotEmpty()) {
                IconButton(
                    onClick = {
                        scope.launch {
                            SettingsRepository.setHistoryCompact(context, !compact)
                        }
                    }
                ) {
                    Icon(
                        if (compact) Icons.Filled.GridView
                        else Icons.AutoMirrored.Filled.List,
                        contentDescription =
                            if (compact) stringResource(R.string.history_layout_grid) else stringResource(R.string.history_layout_list)
                    )
                }
            }

            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.history_search_action))
            }

            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.history_sort_action))
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false }
                ) {
                    HistorySort.entries.forEach { option ->
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

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.history_menu_action))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_menu_clear)) } ,
                        onClick = {
                            menuOpen = false
                            confirmClear = true
                        }
                    )
                }
            }
        }

        // Shaped like the field on the home screen rather than as a boxed input: the two
        // are the same act on two screens, and a square outlined box next to a pill reads
        // as a control borrowed from somewhere else.
        //
        // Drawn from a bare text field rather than from Material's, whose own padding is
        // sized for a floating label this has no room for and left the text sitting low
        // and off-centre inside the pill.
        if (searchOpen) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.history_search_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.history_search_clear),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HistoryFilter.entries) { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    if (history.isEmpty()) stringResource(R.string.history_empty_initial)
                    else stringResource(R.string.history_empty_filtered),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                items(visible, key = { it.id }) { entry ->
                    val present = presence[entry.id] ?: true

                    // Asked again at the moment of the tap rather than trusted from the
                    // last sweep. A file deleted since then handed the address to a player,
                    // which opened on nothing and came straight back, leaving the row still
                    // claiming the file was there. The row is corrected here instead.
                    val open = {
                        scope.launch {
                            if (MediaPresence.refresh(context, entry.fileUri)) {
                                openEntry(context, entry)
                            } else {
                                presence[entry.id] = false
                                Toast.makeText(
                                    context,
                                    "This file is no longer on your device",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        Unit
                    }
                    val remove = {
                        scope.launch { DownloadHistoryRepository.remove(context, entry.id) }
                        Unit
                    }

                    if (compact) {
                        HistoryRow(
                            entry = entry,
                            present = present,
                            onOpen = open,
                            onRemove = remove,
                            onDeleteFile = { pendingDelete = entry },
                            onProperties = { properties = entry }
                        )
                    } else {
                        HistoryCard(
                            entry = entry,
                            present = present,
                            onOpen = open,
                            onRemove = remove,
                            onDeleteFile = { pendingDelete = entry },
                            onProperties = { properties = entry }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_dialog_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.history_clear_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { DownloadHistoryRepository.clear(context) }
                    confirmClear = false
                }) { Text(stringResource(R.string.history_clear_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.history_clear_dialog_cancel)) }
            }
        )
    }

    properties?.let { entry ->
        DownloadPropertiesSheet(
            entry = entry,
            present = presence[entry.id] ?: true,
            onDismiss = { properties = null }
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.history_delete_dialog_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.history_delete_dialog_body, entry.fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val deleted = DownloadHistoryRepository.deleteFile(context, entry)
                        Toast.makeText(
                            context,
                            if (deleted) context.getString(R.string.history_toast_file_deleted) else context.getString(R.string.history_toast_file_delete_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    pendingDelete = null
                }) { Text(stringResource(R.string.history_delete_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.history_delete_dialog_cancel)) }
            }
        )
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    present: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onDeleteFile: () -> Unit,
    onProperties: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                // Holding an entry down asks what it is. A tap plays it, which is what the
                // list is for, so the details cannot have the tap; a long press is where a
                // thing's properties live everywhere else on the platform. It answers for a
                // deleted entry too, where it is the only thing left that can.
                .combinedClickable(
                    onClick = { if (present) onOpen() },
                    onLongClick = onProperties
                )
        ) {
            if (entry.thumbnail != null) {
                // A row whose file has been deleted elsewhere keeps its place in the list,
                // but the artwork is drained of colour and dimmed so the difference is
                // visible at a glance rather than only on the tag.
                AsyncImage(
                    model = entry.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (present) 1f else 0.7f,
                    colorFilter = if (present) null else ColorFilter.colorMatrix(
                        ColorMatrix().apply { setToSaturation(0f) }
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // The artwork is arbitrary, so the text over it gets its own scrim rather than
            // relying on the image being dark where the words fall.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.65f),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.7f)
                        )
                    )
            )

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.author.isNotBlank()) {
                    Text(
                        entry.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Type and actions sit top right, away from the title.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.history_card_options),
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_card_properties)) },
                            onClick = {
                                menuOpen = false
                                onProperties()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_card_remove)) },
                            onClick = {
                                menuOpen = false
                                onRemove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_card_delete)) },
                            enabled = present,
                            onClick = {
                                menuOpen = false
                                onDeleteFile()
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (entry.isVideo) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                val duration = formatDuration(entry.durationSeconds)
                if (duration.isNotBlank()) Tag(duration)
                if (entry.sizeBytes > 0) Tag(formatFileSize(entry.sizeBytes))
                if (!present) {
                    // "Deleted" rather than "File missing". The file is not mislaid, it is
                    // gone, and almost always because the user removed it themselves.
                    Tag(
                        stringResource(R.string.history_tag_deleted),
                        background = MaterialTheme.colorScheme.error,
                        foreground = MaterialTheme.colorScheme.onError
                    )
                }
            }

            Text(
                formatDate(entry.completedAt),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            )
        }
    }
}


/**
 * One download as a single line: artwork, then what it is, then what it cost.
 *
 * The card form leads with the artwork, which is right for browsing but wasteful once the
 * list is long, since four entries fill a screen. This form fits several times as many by
 * moving the text out from over the image onto its own column, where it also stops
 * competing with the picture for contrast.
 *
 * The three facts under the title are laid out as a line that gives way and a marker that
 * does not. They used to share a row where neither was allowed to yield, so the text took
 * the whole width and the marker beside it was measured into nothing: on a deleted download
 * it collapsed to a red thread down the side of the row and its label wrapped a letter at a
 * time, dragging the row to several times its height. The line is what shortens now, and
 * the marker keeps the width its own words need.
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    present: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onDeleteFile: () -> Unit,
    onProperties: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // As on the card: a tap plays it, holding it down says what it is.
                .combinedClickable(
                    onClick = { if (present) onOpen() },
                    onLongClick = onProperties
                )
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 128.dp, height = 78.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        if (entry.isVideo) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }

                val duration = formatDuration(entry.durationSeconds)
                if (duration.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Text(
                            duration,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (present) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
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

                Spacer(modifier = Modifier.height(8.dp))

                // No type glyph in front of the figures. The artwork beside them already
                // says what this is, and on a narrow row the glyph was spending width the
                // date needed to finish.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        buildString {
                            if (entry.sizeBytes > 0) {
                                append(formatFileSize(entry.sizeBytes))
                                append("  \u00b7  ")
                            }
                            append(formatDate(entry.completedAt))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Weighted, so this is the part that gives when the row is narrow.
                        // Anything unweighted beside it is measured first and in full.
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!present) {
                        // The same word the card uses. The file is not mislaid, it is gone,
                        // and almost always because the user removed it themselves.
                        Tag(
                            stringResource(R.string.history_tag_deleted),
                            background = MaterialTheme.colorScheme.error,
                            foreground = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.history_row_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_row_properties)) },
                        onClick = {
                            menuOpen = false
                            onProperties()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_row_remove)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_row_delete)) },
                        enabled = present,
                        onClick = {
                            menuOpen = false
                            onDeleteFile()
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun Tag(
    text: String,
    background: Color = Color.Black.copy(alpha = 0.6f),
    foreground: Color = Color.White
) {
    Surface(shape = RoundedCornerShape(4.dp), color = background) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Opens a finished download in whatever app the device uses for that media type. */
private fun openEntry(context: Context, entry: HistoryEntry) =
    MediaOpener.play(context, entry.fileUri, entry.isVideo)

private fun formatDate(millis: Long): String =
    runCatching {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("")
