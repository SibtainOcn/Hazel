package com.hazel.android.ui.screens.history

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.DownloadQueueRepository
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.FailedDownloadRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.HistoryFilter
import com.hazel.android.data.HistorySort
import com.hazel.android.data.QueuedDownload
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.DownloadViewModel
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.components.MediaCard
import com.hazel.android.ui.components.MediaRow
import com.hazel.android.ui.components.rememberPresence
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaPresence
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Finished downloads and active workflow queues.
 *
 * Shows running downloads, finished history, queued links, and failed tasks with full visual parity,
 * support for large thumbnail and compact single-row layouts across all categories, and an interactive
 * top title dropdown menu.
 */
@Composable
fun HistoryScreen(
    downloadViewModel: DownloadViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val history by DownloadHistoryRepository.getHistory(context)
        .collectAsState(initial = emptyList())
    val downloadState by downloadViewModel.state.collectAsState()
    val queueList by DownloadQueueRepository.getQueue(context).collectAsState(initial = emptyList())
    val failedList by FailedDownloadRepository.getFailed(context).collectAsState(initial = emptyList())

    // Big artwork or a tight list, remembered across launches.
    val compact by SettingsRepository.getHistoryCompact(context).collectAsState(initial = false)

    var sort by remember { mutableStateOf(HistorySort.NEWEST) }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterDropdownOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var properties by remember { mutableStateOf<HistoryEntry?>(null) }
    var viewFailedLog by remember { mutableStateOf<FailedDownload?>(null) }

    val presence = rememberPresence(history)

    val isDownloadingActive by remember(downloadState.isDownloading, downloadState.isProcessing) {
        derivedStateOf { downloadState.isDownloading || downloadState.isProcessing }
    }

    val visible = remember(history, sort, filter, query) {
        history
            .filter { entry ->
                when (filter) {
                    HistoryFilter.AUDIO -> !entry.isVideo
                    HistoryFilter.VIDEO -> entry.isVideo
                    else -> true
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

    val visibleQueue = remember(queueList, query, sort) {
        queueList
            .filter { item ->
                query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.author.contains(query, ignoreCase = true) ||
                        item.url.contains(query, ignoreCase = true)
            }
            .let { items ->
                when (sort) {
                    HistorySort.NEWEST -> items
                    HistorySort.TITLE -> items.sortedBy { it.title.lowercase() }
                    HistorySort.SIZE -> items.sortedByDescending { it.fileSizeBytes }
                }
            }
    }

    val visibleFailed = remember(failedList, query, sort) {
        failedList
            .filter { item ->
                query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.author.contains(query, ignoreCase = true) ||
                        item.url.contains(query, ignoreCase = true) ||
                        item.errorLog.contains(query, ignoreCase = true)
            }
            .let { items ->
                when (sort) {
                    HistorySort.NEWEST -> items.sortedByDescending { it.failedAt }
                    HistorySort.TITLE -> items.sortedBy { it.title.lowercase() }
                    HistorySort.SIZE -> items.sortedBy { it.title.lowercase() }
                }
            }
    }

    val activeDownloadMatches = remember(query, downloadState.fileName, downloadState.url, downloadState.info) {
        query.isBlank() ||
                downloadState.fileName.contains(query, ignoreCase = true) ||
                downloadState.url.contains(query, ignoreCase = true) ||
                (downloadState.info?.title?.contains(query, ignoreCase = true) == true) ||
                (downloadState.info?.uploader?.contains(query, ignoreCase = true) == true)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Top Header with Title Dropdown & Actions ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { filterDropdownOpen = true }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = when (filter) {
                            HistoryFilter.ALL -> stringResource(R.string.history_title)
                            HistoryFilter.DOWNLOADING -> stringResource(R.string.history_tab_downloading)
                            HistoryFilter.QUEUED -> stringResource(R.string.history_tab_queued)
                            HistoryFilter.FAILED -> stringResource(R.string.history_tab_failed)
                            HistoryFilter.AUDIO -> stringResource(R.string.properties_kind_audio)
                            HistoryFilter.VIDEO -> stringResource(R.string.properties_kind_video)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = filterDropdownOpen,
                    onDismissRequest = { filterDropdownOpen = false }
                ) {
                    HistoryFilter.entries.forEach { option ->
                        val label = when (option) {
                            HistoryFilter.ALL -> stringResource(R.string.history_title)
                            HistoryFilter.DOWNLOADING -> {
                                val base = stringResource(R.string.history_tab_downloading)
                                if (isDownloadingActive) "$base (1)" else base
                            }
                            HistoryFilter.QUEUED -> {
                                val base = stringResource(R.string.history_tab_queued)
                                if (queueList.isNotEmpty()) "$base (${queueList.size})" else base
                            }
                            HistoryFilter.FAILED -> {
                                val base = stringResource(R.string.history_tab_failed)
                                if (failedList.isNotEmpty()) "$base (${failedList.size})" else base
                            }
                            HistoryFilter.AUDIO -> stringResource(R.string.properties_kind_audio)
                            HistoryFilter.VIDEO -> stringResource(R.string.properties_kind_video)
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    fontWeight = if (option == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                filter = option
                                filterDropdownOpen = false
                            },
                            trailingIcon = if (option == filter) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            }

            // ── 4 Action Buttons (ALWAYS visible across all tabs) ──
            // 1. Layout switcher
            IconButton(
                onClick = {
                    scope.launch {
                        SettingsRepository.setHistoryCompact(context, !compact)
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    if (compact) Icons.Filled.GridView else Icons.AutoMirrored.Filled.List,
                    contentDescription =
                        if (compact) stringResource(R.string.history_layout_grid)
                        else stringResource(R.string.history_layout_list),
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Search
            IconButton(
                onClick = { searchOpen = !searchOpen },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.history_search_action),
                    modifier = Modifier.size(22.dp),
                    tint = if (searchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // 3. Sort
            Box {
                IconButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.history_sort_action),
                        modifier = Modifier.size(22.dp)
                    )
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

            // 4. More (3-dots)
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.history_menu_action),
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    when (filter) {
                        HistoryFilter.ALL, HistoryFilter.AUDIO, HistoryFilter.VIDEO -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_menu_clear)) },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                },
                                enabled = history.isNotEmpty()
                            )
                        }
                        HistoryFilter.QUEUED -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_queue_clear_all)) },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                },
                                enabled = queueList.isNotEmpty()
                            )
                        }
                        HistoryFilter.FAILED -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_failed_clear_all)) },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                },
                                enabled = failedList.isNotEmpty()
                            )
                        }
                        HistoryFilter.DOWNLOADING -> {
                            if (isDownloadingActive) {
                                if (downloadState.isDownloading) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.notification_action_pause)) },
                                        onClick = {
                                            menuOpen = false
                                            downloadViewModel.pauseDownload()
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.notification_action_resume)) },
                                        onClick = {
                                            menuOpen = false
                                            downloadViewModel.resumeDownload()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.download_cancel_action)) },
                                    onClick = {
                                        menuOpen = false
                                        downloadViewModel.cancelDownload()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.history_empty_downloading)) },
                                    onClick = { menuOpen = false },
                                    enabled = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search bar — animates smoothly into its own row; tapping outside dismisses it.
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

        AnimatedVisibility(
            visible = searchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
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
                    } else {
                        // X button to close the bar entirely when nothing is typed
                        IconButton(onClick = {
                            keyboardController?.hide()
                            searchOpen = false
                        }) {
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

        Spacer(modifier = Modifier.height(4.dp))

        // ── Screen Content by Selected Filter ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (searchOpen) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            keyboardController?.hide()
                            searchOpen = false
                        }
                    } else Modifier
                )
        ) {
            when (filter) {
            HistoryFilter.DOWNLOADING -> {
                HistoryDownloadingView(
                    state = downloadState,
                    compact = compact,
                    isFiltered = isDownloadingActive && !activeDownloadMatches && query.isNotBlank(),
                    onCancel = { downloadViewModel.cancelDownload() },
                    onPause = { downloadViewModel.pauseDownload() },
                    onResume = { downloadViewModel.resumeDownload() }
                )
            }
            HistoryFilter.QUEUED -> {
                HistoryQueuedView(
                    items = visibleQueue,
                    compact = compact,
                    isFiltered = query.isNotBlank() && queueList.isNotEmpty() && visibleQueue.isEmpty(),
                    onRemove = { item -> downloadViewModel.removeQueued(context, item.url) }
                )
            }
            HistoryFilter.FAILED -> {
                HistoryFailedView(
                    items = visibleFailed,
                    compact = compact,
                    isFiltered = query.isNotBlank() && failedList.isNotEmpty() && visibleFailed.isEmpty(),
                    onViewLog = { item -> viewFailedLog = item },
                    onRetry = { item ->
                        Toast.makeText(context, context.getString(R.string.history_retrying_toast), Toast.LENGTH_SHORT).show()
                        downloadViewModel.retryFailed(context, item)
                    },
                    onDismiss = { item ->
                        scope.launch { FailedDownloadRepository.remove(context, item.id) }
                    },
                    onClearAll = {
                        scope.launch { FailedDownloadRepository.clear(context) }
                    }
                )
            }
            HistoryFilter.ALL, HistoryFilter.AUDIO, HistoryFilter.VIDEO -> {
                val showActiveDownload = isDownloadingActive && activeDownloadMatches && when (filter) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.AUDIO -> downloadState.info?.videoFormats.isNullOrEmpty()
                    HistoryFilter.VIDEO -> downloadState.info?.videoFormats?.isNotEmpty() == true
                    else -> false
                }

                if (visible.isEmpty() && !showActiveDownload) {
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
                        if (showActiveDownload) {
                            item(key = "active_download") {
                                val info = downloadState.info ?: MediaInfo(
                                    url = downloadState.url,
                                    title = downloadState.fileName.ifBlank { stringResource(R.string.history_tab_downloading) },
                                    uploader = "",
                                    thumbnail = null,
                                    durationSeconds = 0,
                                    videoFormats = emptyList(),
                                    audioFormats = emptyList()
                                )
                                if (compact) {
                                    MediaRow(
                                        info = info,
                                        isDownloading = true,
                                        isProcessing = downloadState.isProcessing,
                                        progress = downloadState.progress,
                                        totalBytes = downloadState.totalBytes,
                                        waitingForWifi = downloadState.waitingForWifi,
                                        onCancel = { downloadViewModel.cancelDownload() },
                                        onPause = { downloadViewModel.pauseDownload() },
                                        onResume = { downloadViewModel.resumeDownload() }
                                    )
                                } else {
                                    MediaCard(
                                        info = info,
                                        isDownloading = true,
                                        isProcessing = downloadState.isProcessing,
                                        progress = downloadState.progress,
                                        totalBytes = downloadState.totalBytes,
                                        waitingForWifi = downloadState.waitingForWifi,
                                        onCancel = { downloadViewModel.cancelDownload() },
                                        onPause = { downloadViewModel.pauseDownload() },
                                        onResume = { downloadViewModel.resumeDownload() }
                                    )
                                }
                            }
                        }

                        items(visible, key = { it.id }) { entry ->
                            val present = presence[entry.id] ?: true

                            val open = {
                                scope.launch {
                                    if (MediaPresence.refresh(context, entry.fileUri)) {
                                        openEntry(context, entry)
                                    } else {
                                        presence[entry.id] = false
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.history_toast_file_gone),
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
        }
    }
    }

    viewFailedLog?.let { failed ->
        AlertDialog(
            onDismissRequest = { viewFailedLog = null },
            title = {
                Text(
                    stringResource(R.string.history_failed_error_log),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    Text(
                        failed.title.ifBlank { failed.url },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = failed.errorLog.ifBlank { stringResource(R.string.history_empty_failed) },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(failed.errorLog))
                        Toast.makeText(
                            context,
                            context.getString(R.string.history_failed_log_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.history_failed_copy_log))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewFailedLog = null }) {
                    Text(stringResource(R.string.history_clear_dialog_cancel))
                }
            }
        )
    }

    if (confirmClear) {
        val title = when (filter) {
            HistoryFilter.QUEUED -> stringResource(R.string.history_queue_clear_all)
            HistoryFilter.FAILED -> stringResource(R.string.history_failed_clear_all)
            else -> stringResource(R.string.history_clear_dialog_title)
        }
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.history_clear_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (filter) {
                            HistoryFilter.QUEUED -> DownloadQueueRepository.clear(context)
                            HistoryFilter.FAILED -> FailedDownloadRepository.clear(context)
                            else -> DownloadHistoryRepository.clear(context)
                        }
                    }
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
private fun HistoryDownloadingView(
    state: com.hazel.android.download.DownloadState,
    compact: Boolean,
    isFiltered: Boolean = false,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    if (isFiltered) {
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
                stringResource(R.string.history_empty_filtered),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (!state.isDownloading && !state.isProcessing) {
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
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.history_empty_downloading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.history_empty_downloading_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val info = state.info ?: MediaInfo(
            url = state.url,
            title = state.fileName.ifBlank { stringResource(R.string.history_tab_downloading) },
            uploader = "",
            thumbnail = null,
            durationSeconds = 0,
            videoFormats = emptyList(),
            audioFormats = emptyList()
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            item(key = "active_download") {
                if (compact) {
                    MediaRow(
                        info = info,
                        isDownloading = true,
                        isProcessing = state.isProcessing,
                        progress = state.progress,
                        totalBytes = state.totalBytes,
                        waitingForWifi = state.waitingForWifi,
                        onCancel = onCancel,
                        onPause = onPause,
                        onResume = onResume
                    )
                } else {
                    MediaCard(
                        info = info,
                        isDownloading = true,
                        isProcessing = state.isProcessing,
                        progress = state.progress,
                        totalBytes = state.totalBytes,
                        waitingForWifi = state.waitingForWifi,
                        onCancel = onCancel,
                        onPause = onPause,
                        onResume = onResume
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryQueuedView(
    items: List<QueuedDownload>,
    compact: Boolean,
    isFiltered: Boolean = false,
    onRemove: (QueuedDownload) -> Unit
) {
    if (items.isEmpty()) {
        Column(
            modifier = Modifier
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
                else stringResource(R.string.history_empty_queued),
                style = if (isFiltered) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (isFiltered) FontWeight.Normal else FontWeight.SemiBold,
                color = if (isFiltered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (!isFiltered) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.history_empty_queued_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            items(items, key = { it.url }) { item ->
                if (compact) {
                    QueuedRow(item = item, onRemove = { onRemove(item) })
                } else {
                    QueuedCard(item = item, onRemove = { onRemove(item) })
                }
            }
        }
    }
}

@Composable
private fun QueuedCard(
    item: QueuedDownload,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
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
                if (duration.isNotBlank()) Tag(duration)
                if (item.formatLabel.isNotBlank()) Tag(item.formatLabel)
                if (item.fileSizeBytes > 0) Tag(formatFileSize(item.fileSizeBytes))
            }
        }
    }
}

@Composable
private fun QueuedRow(
    item: QueuedDownload,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 128.dp, height = 78.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.thumbnail != null) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        if (item.hasVideo) Icons.Filled.PlayArrow else Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }

                val duration = formatDuration(item.durationSeconds)
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
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        item.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.formatLabel.isNotBlank()) {
                        Tag(
                            item.formatLabel,
                            background = MaterialTheme.colorScheme.surfaceContainerHighest,
                            foreground = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.fileSizeBytes > 0) {
                        Text(
                            formatFileSize(item.fileSizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.download_remove_link),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HistoryFailedView(
    items: List<FailedDownload>,
    compact: Boolean,
    isFiltered: Boolean = false,
    onViewLog: (FailedDownload) -> Unit,
    onRetry: (FailedDownload) -> Unit,
    onDismiss: (FailedDownload) -> Unit,
    onClearAll: () -> Unit
) {
    if (items.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                if (isFiltered) Icons.Filled.Download else Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(if (isFiltered) 48.dp else 56.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
            Spacer(modifier = Modifier.height(if (isFiltered) 12.dp else 16.dp))
            Text(
                if (isFiltered) stringResource(R.string.history_empty_filtered)
                else stringResource(R.string.history_empty_failed),
                style = if (isFiltered) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (isFiltered) FontWeight.Normal else FontWeight.SemiBold,
                color = if (isFiltered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (!isFiltered) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.history_empty_failed_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            item(key = "failed_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onClearAll) {
                        Text(
                            stringResource(R.string.history_failed_clear_all),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            items(items, key = { it.id }) { item ->
                if (compact) {
                    FailedRow(
                        item = item,
                        onViewLog = { onViewLog(item) },
                        onRetry = { onRetry(item) },
                        onDismiss = { onDismiss(item) }
                    )
                } else {
                    FailedCard(
                        item = item,
                        onViewLog = { onViewLog(item) },
                        onRetry = { onRetry(item) },
                        onDismiss = { onDismiss(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedCard(
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

                Tag(
                    text = stringResource(R.string.history_tab_failed),
                    background = MaterialTheme.colorScheme.error,
                    foreground = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.history_failed_dismiss),
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = item.title.ifBlank { item.url },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.author.isNotBlank()) {
                        Text(
                            text = item.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            ) {
                val snippet = item.errorLog.lines().firstOrNull { it.isNotBlank() }
                    ?: stringResource(R.string.history_empty_failed)
                Text(
                    text = snippet,
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
                    text = formatDate(item.failedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onViewLog) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
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
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
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
private fun FailedRow(
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
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.thumbnail != null) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 96.dp, height = 58.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Surface(
                        modifier = Modifier.size(width = 96.dp, height = 58.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifBlank { item.url },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.author.isNotBlank()) {
                        Text(
                            text = item.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatDate(item.failedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.history_failed_dismiss),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            ) {
                val snippet = item.errorLog.lines().firstOrNull { it.isNotBlank() }
                    ?: stringResource(R.string.history_empty_failed)
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onViewLog) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.history_failed_error_log),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                FilledTonalButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
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
                .combinedClickable(
                    onClick = { if (present) onOpen() },
                    onLongClick = onProperties
                )
        ) {
            if (entry.thumbnail != null) {
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
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!present) {
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

private fun openEntry(context: Context, entry: HistoryEntry) =
    MediaOpener.play(context, entry.fileUri, entry.isVideo)

private fun formatDate(millis: Long): String =
    runCatching {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("")
